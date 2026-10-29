package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.entity.LiftCar;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The machine of a ship's lift, at the top of its glass shaft: it owns the lift's car (see {@link LiftCar}) and runs it
 * between the landings. A landing's call button (see {@link LiftCallButtonBlock}) calls the car: it comes if it is
 * elsewhere and its doors slide open; whoever steps in picks their floor on the board inside (see
 * {@link LiftBoardBlock}), the doors close, and the car takes them there, where a chime sounds, the doors open and they
 * step out. The car moves only with every
 * door shut and no one in a doorway, and lets no one off between floors.
 *
 * <p>While the ship is not resting on the ground (her autopilot counting down, climbing, flying or landing), the lift
 * stands at a floor with its doors open and takes no rides, whoever is in it free to step out; it runs again once she
 * stands still.
 *
 * <p>Its landings, set as the ship is built (see {@link #configure}), each hold how far under the machine the car's
 * floor stands there, where the landing's door is and where riders step out, as offsets from the machine turned as she
 * was placed, and the colour and the icon its boards show the floor by. They are sent to the clients for the boards.
 */
public class LiftMachineBlockEntity extends BlockEntity {
    /**
     * How long the doors take to slide open or shut, and how long they stand open with nobody aboard, in ticks: long
     * enough for whoever called the car to walk in.
     */
    public static final int DOOR_TICKS = 10;
    private static final int OPEN_TICKS = 160;
    /** How long the doors stay open once a rider has picked a floor from inside the car, in ticks. */
    private static final int PICKED_TICKS = 20;
    /** How long the car may be missing before a new one is set in the shaft, in ticks: its own chunk may load late. */
    private static final int CAR_MISSING = 40;
    /**
     * How close to the shaft's middle a player stands to be taken aboard the car, across and up, in blocks: anywhere in
     * the car, or already in the open doorway, so no one steps out over the gap at the car's edge first.
     */
    private static final double ABOARD_ACROSS = 1.4;
    private static final double ABOARD_UP = 2.0;
    private static final double ABOARD_DOORWAY = 0.6;
    /** The chimes' pitch: one rising tone as the car comes in going up, two falling going down. */
    private static final float CHIME_PITCH = 1.0F;

    /** What the lift is doing. */
    public enum Phase {
        /** Standing at a floor, its doors shut. */
        IDLE,
        /** Going empty to the floor it was called to. */
        FETCHING,
        /** Its doors sliding open at the floor it stands at. */
        OPENING,
        /** Standing with its doors open, taking riders aboard. */
        BOARDING,
        /** Its doors sliding shut. */
        CLOSING,
        /** Taking its riders to their floor. */
        RIDING,
        /** Standing at a floor with its doors open while the ship is not resting, taking no rides. */
        PARKED;

        /** A phase by its saved name; standing for none or one not known. */
        static Phase named(String name) {
            for (Phase phase : values()) {
                if (phase.name().equals(name)) {
                    return phase;
                }
            }
            return IDLE;
        }
    }

    /**
     * A landing: how far under the machine's bottom the car's floor stands at it, in blocks; its door's lower block and
     * the block riders step out onto, as offsets from the machine; and the colour (ARGB) and the icon its floor is
     * shown by.
     */
    public record Landing(int depth, BlockPos door, BlockPos exit, int colour, String icon) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Depth", depth);
            tag.putLong("Door", door.asLong());
            tag.putLong("Exit", exit.asLong());
            tag.putInt("Colour", colour);
            tag.putString("Icon", icon);
            return tag;
        }

        static Landing load(CompoundTag tag) {
            return new Landing(tag.getInt("Depth"), BlockPos.of(tag.getLong("Door")), BlockPos.of(tag.getLong("Exit")), tag.getInt("Colour"),
                tag.getString("Icon"));
        }

        Landing turned(Rotation turn) {
            return new Landing(depth, door.rotate(turn), exit.rotate(turn), colour, icon);
        }
    }

    private final List<Landing> landings = new ArrayList<>();
    /** Its ship's autopilot, as an offset from the machine; null for a lift not built with one. */
    @Nullable
    private BlockPos autopilot;
    @Nullable
    private UUID car;
    private int missing;
    private Phase phase = Phase.IDLE;
    /** The landing the car stands at or last stood at, and the one it is to take its riders to, or -1 for none. */
    private int at;
    private int target = -1;
    /** Ticks spent in the phase so far, and how long it lasts where that is set. */
    private int ticks;
    private int lasts = OPEN_TICKS;
    /** Whether the car's ride now, or its last, goes up: the chime it comes in with. */
    private boolean goingUp;
    /**
     * The landings whose call buttons were pressed while the car was busy, in the order they were, each answered once
     * the car stands free; not saved, as a press is soon made again.
     */
    private final Set<Integer> calls = new LinkedHashSet<>();
    /**
     * The landing whose doors slide now, or -1, and the ticks they have slid; and whether its doors have been looked
     * over since it was loaded, for any left sliding when it was saved.
     */
    private int sliding = -1;
    private int slid;
    private boolean doorsLookedOver;

    public LiftMachineBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LIFT_MACHINE_ENTITY.get(), pos, state);
    }

    /**
     * Set as the ship is built: her landings, bottom first, each turned as she was placed, and her autopilot's offset
     * from the machine; the car starts at the lowest.
     */
    public void configure(List<Landing> built, @Nullable BlockPos autopilotOffset, Rotation turn) {
        landings.clear();
        built.forEach(landing -> landings.add(landing.turned(turn)));
        autopilot = autopilotOffset == null ? null : autopilotOffset.rotate(turn);
        at = 0;
        target = -1;
        phase = Phase.IDLE;
        changed();
    }

    /** Its landings, bottom first. */
    public List<Landing> landings() {
        return List.copyOf(landings);
    }

    /** The landing its car stands at or last stood at. */
    public int at() {
        return at;
    }

    /** The landing it is taking its riders to, or -1 for none. */
    public int target() {
        return target;
    }

    public Phase phase() {
        return phase;
    }

    /** Whether the car's ride now, or its last, goes up. */
    public boolean goingUp() {
        return goingUp;
    }

    /**
     * Calls the car to a landing, as its call button (or an older ship's board outside the doors) was pressed; whether
     * the lift took the call. Standing shut, it comes if it is elsewhere and its doors open there, for whoever is
     * waiting to step in and pick their floor inside; standing open there already, it stays open a while longer; busy
     * with another ride, it comes once that is done. Never while the ship is not resting and the lift parked.
     */
    public boolean call(ServerLevel level, int from) {
        if (from < 0 || from >= landings.size() || phase == Phase.PARKED) {
            return false;
        }
        if (phase == Phase.IDLE) {
            return ask(level, from, from);
        }
        if ((phase == Phase.FETCHING || phase == Phase.OPENING) && at == from) {
            return true;
        }
        if (phase == Phase.BOARDING && at == from) {
            if (target < 0 || target == at) {
                lasts = Math.max(lasts, ticks + OPEN_TICKS);
                changed();
            }
            return true;
        }
        calls.add(from);
        return true;
    }

    /** Whether the car is called to a landing and not there yet: on its way to it, or to come once it is free. */
    public boolean coming(int landing) {
        return phase == Phase.FETCHING && at == landing || calls.contains(landing);
    }

    /** Standing shut and free once more, the car goes to the first landing that called it meanwhile, if one did. */
    private void answerCalls(ServerLevel level) {
        Iterator<Integer> waiting = calls.iterator();
        if (waiting.hasNext()) {
            int next = waiting.next();
            waiting.remove();
            call(level, next);
        }
    }

    /**
     * Whether a block at a landing is inside the shaft, as the board inside the car is, rather than out beyond its
     * doors: nearer the shaft's middle, under the machine, than the landing's doors are.
     */
    public boolean inside(BlockPos at, int landing) {
        if (landing < 0 || landing >= landings.size()) {
            return false;
        }
        return across(at.subtract(worldPosition)) < across(landings.get(landing).door());
    }

    /** How far out from the shaft's middle an offset from the machine is, across, along the farther way. */
    private static int across(BlockPos offset) {
        return Math.max(Math.abs(offset.getX()), Math.abs(offset.getZ()));
    }

    /**
     * Asks for a ride from a landing to another, as the board inside the car was touched; whether the lift took it. A
     * ride is taken while the lift stands shut, or stands open at that landing (the floor picked from inside the car,
     * or picked again); never while it is on its way or parked.
     */
    public boolean ask(ServerLevel level, int from, int to) {
        if (from < 0 || from >= landings.size() || to < 0 || to >= landings.size()) {
            return false;
        }
        if (phase == Phase.IDLE) {
            target = to;
            if (at == from) {
                enter(Phase.OPENING);
            } else {
                goingUp = landings.get(from).depth() < landings.get(at).depth();
                at = from;
                enter(Phase.FETCHING);
                LiftCar running = car(level);
                if (running != null) {
                    running.ride(level, landings.get(from).depth());
                }
            }
            changed();
            return true;
        }
        if ((phase == Phase.BOARDING || phase == Phase.OPENING) && at == from) {
            target = to;
            lasts = Math.min(lasts, ticks + PICKED_TICKS);
            changed();
            return true;
        }
        return false;
    }

    static void tick(ServerLevel level, BlockPos pos, LiftMachineBlockEntity lift) {
        if (lift.landings.isEmpty()) {
            return;
        }
        LiftCar running = lift.carOrNew(level);
        if (running == null) {
            return;
        }
        lift.ticks++;
        lift.doorsSlid(level);
        lift.step(level, running);
        Vec3 letOff = lift.doorsOpen() ? lift.exitAt(level, lift.at) : null;
        running.hold(level, pos, letOff);
    }

    /** One tick of the lift's work in its phase. */
    private void step(ServerLevel level, LiftCar running) {
        boolean resting = shipResting(level);
        if (!resting && phase != Phase.PARKED && phase != Phase.RIDING && phase != Phase.FETCHING) {
            park(level);
            return;
        }
        switch (phase) {
            case IDLE -> {
            }
            case FETCHING, RIDING -> arriving(level, running);
            case OPENING -> opening(level, running);
            case BOARDING -> boarding(level, running);
            case CLOSING -> {
                if (ticks >= DOOR_TICKS) {
                    closed(level, running);
                }
            }
            case PARKED -> {
                letSneakersOff(level, running);
                takeAboard(level, running);
                if (resting) {
                    target = -1;
                    enter(Phase.BOARDING);
                }
            }
        }
    }

    /**
     * Its doors sliding open: once they are, its riders step out; while they slide, whoever steps into the doorway is
     * taken aboard already, the doorway being clear of them from the start.
     */
    private void opening(ServerLevel level, LiftCar running) {
        if (ticks < DOOR_TICKS) {
            if (!running.isVehicle()) {
                takeAboard(level, running);
            }
            return;
        }
        letOff(level, running);
        enter(Phase.BOARDING);
    }

    /** On its way: once the car stands at its floor, a chime, and the doors open there. */
    private void arriving(ServerLevel level, LiftCar running) {
        if (!running.arrived()) {
            return;
        }
        if (phase == Phase.RIDING) {
            at = target;
            target = -1;
        }
        sound(level, goingUp ? AerolargeRegistry.LIFT_CHIME_UP.get() : AerolargeRegistry.LIFT_CHIME_DOWN.get(), CHIME_PITCH);
        enter(Phase.OPENING);
        changed();
    }

    /**
     * Standing open: whoever steps in is taken aboard and whoever sneaks in it is set down outside; once it has stood
     * long enough, with a floor picked or no one aboard, and no one in the way, the doors close. With another landing
     * calling, it stands open only a moment empty, and no longer than it would empty for a rider who has picked no
     * floor: it goes to answer the call, taking them along.
     */
    private void boarding(ServerLevel level, LiftCar running) {
        letSneakersOff(level, running);
        takeAboard(level, running);
        boolean picked = target >= 0 && target != at;
        boolean waited = !calls.isEmpty();
        int stays = waited && !running.isVehicle() ? Math.min(lasts, PICKED_TICKS) : lasts;
        if (ticks < stays || !picked && running.isVehicle() && !waited || doorwayTaken(level)) {
            return;
        }
        enter(Phase.CLOSING);
        doors(level, at, false);
        sound(level, AerolargeRegistry.LIFT_DOOR_SOUND.get(), 1.0F);
    }

    /** The doors shut: off with its riders to their floor, or standing shut. */
    private void closed(ServerLevel level, LiftCar running) {
        if (target >= 0 && target != at && running.isVehicle()) {
            goingUp = landings.get(target).depth() < landings.get(at).depth();
            enter(Phase.RIDING);
            running.ride(level, landings.get(target).depth());
            sound(level, AerolargeRegistry.LIFT_START.get(), 1.0F);
        } else {
            target = -1;
            enter(Phase.IDLE);
            answerCalls(level);
        }
        changed();
    }

    /** Stands at its floor with its doors open, the ship not resting: riders may get out, and no ride is taken. */
    private void park(ServerLevel level) {
        target = -1;
        calls.clear();
        enter(Phase.PARKED);
        doors(level, at, true);
        changed();
    }

    /** Opens the doors at the floor it has come to, letting its riders out there. */
    private void letOff(ServerLevel level, LiftCar running) {
        for (Entity rider : List.copyOf(running.getPassengers())) {
            setDown(level, running, rider);
        }
    }

    /** While its doors stand open at a floor, whoever sneaks in the car is set down outside them. */
    private void letSneakersOff(ServerLevel level, LiftCar running) {
        for (Entity rider : List.copyOf(running.getPassengers())) {
            if (running.askedOff(rider)) {
                setDown(level, running, rider);
            }
        }
    }

    /**
     * A rider off the car and outside the door of the floor it is at. A player is told they are off the car before
     * they are moved, or their game would ride on a moment and put them back in the car.
     */
    private void setDown(ServerLevel level, LiftCar running, Entity rider) {
        Vec3 exit = exitAt(level, at);
        running.release(rider);
        if (rider instanceof ServerPlayer player) {
            player.connection.send(new ClientboundSetPassengersPacket(running));
            player.connection.teleport(exit.x, exit.y, exit.z, player.getYRot(), player.getXRot());
        } else {
            rider.teleportTo(exit.x, exit.y, exit.z);
        }
    }

    /** Takes aboard every player standing in the car, so no one steps into the open shaft. */
    private void takeAboard(ServerLevel level, LiftCar running) {
        Vec3 middle = running.position();
        for (ServerPlayer player : level.players()) {
            if (player.getVehicle() == running || player.isSpectator() || running.getPassengers().size() >= LiftCar.RIDERS) {
                continue;
            }
            Vec3 seen = SableFlight.toPlot(level, worldPosition, player.position(), false);
            Vec3 off = seen.subtract(middle);
            boolean inCar = Math.abs(off.x) < ABOARD_ACROSS && Math.abs(off.z) < ABOARD_ACROSS;
            if ((inCar || inDoorway(seen)) && off.y > -0.5 && off.y < ABOARD_UP) {
                player.startRiding(running, true);
            }
        }
    }

    /** Whether a point among the ship's blocks is in the open doorway of the floor the car is at. */
    private boolean inDoorway(Vec3 seen) {
        Vec3 door = Vec3.atBottomCenterOf(worldPosition.offset(landings.get(at).door()));
        Vec3 off = seen.subtract(door);
        return Math.abs(off.x) < ABOARD_DOORWAY && Math.abs(off.z) < ABOARD_DOORWAY;
    }

    /** Whether someone stands in the doorway of the floor it is at, where the doors would close on them. */
    private boolean doorwayTaken(ServerLevel level) {
        BlockPos door = worldPosition.offset(landings.get(at).door());
        AABB doorway = new AABB(door).expandTowards(0, 1, 0).inflate(0.1);
        for (ServerPlayer player : level.players()) {
            Vec3 seen = SableFlight.toPlot(level, worldPosition, player.position(), false);
            if (player.getVehicle() == null && doorway.inflate(player.getBbWidth() / 2).contains(seen)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the ship stands resting on the ground, her autopilot off: the lift runs only then. */
    private boolean shipResting(ServerLevel level) {
        if (autopilot == null) {
            return true;
        }
        BlockEntity found = level.getBlockEntity(worldPosition.offset(autopilot));
        return !(found instanceof AutopilotBlockEntity head) || head.phase() == AutopilotBlockEntity.Phase.OFF;
    }

    /** Whether its doors stand open at the floor it is at, so riders may step out. */
    private boolean doorsOpen() {
        return phase == Phase.BOARDING || phase == Phase.PARKED;
    }

    /** Opens or shuts a landing's doors, both of its blocks. */
    private void doors(ServerLevel level, int landing, boolean open) {
        BlockPos lower = worldPosition.offset(landings.get(landing).door());
        for (BlockPos door : List.of(lower, lower.above())) {
            BlockState state = level.getBlockState(door);
            if (state.getBlock() instanceof LiftDoorBlock && state.getValue(LiftDoorBlock.OPEN) != open) {
                level.setBlock(door, state.setValue(LiftDoorBlock.OPEN, open).setValue(LiftDoorBlock.SLIDING, true), Block.UPDATE_ALL);
                sliding = landing;
                slid = 0;
            }
        }
    }

    /**
     * Once a landing's doors have slid all the way, they stand still again, drawn as the blocks they are; and the first
     * tick after it is loaded, any doors left sliding when it was saved stand still.
     */
    private void doorsSlid(ServerLevel level) {
        if (!doorsLookedOver) {
            doorsLookedOver = true;
            for (int landing = 0; landing < landings.size(); landing++) {
                standStill(level, landing);
            }
        }
        if (sliding >= 0 && ++slid >= DOOR_TICKS) {
            standStill(level, sliding);
            sliding = -1;
        }
    }

    /** A landing's doors standing still where they are. */
    private void standStill(ServerLevel level, int landing) {
        BlockPos lower = worldPosition.offset(landings.get(landing).door());
        for (BlockPos door : List.of(lower, lower.above())) {
            BlockState state = level.getBlockState(door);
            if (state.getBlock() instanceof LiftDoorBlock && state.getValue(LiftDoorBlock.SLIDING)) {
                level.setBlock(door, state.setValue(LiftDoorBlock.SLIDING, false), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Where riders step out at a landing, in the world: the middle of its exit block's floor, as the ship lies now. */
    private Vec3 exitAt(ServerLevel level, int landing) {
        return SableFlight.toWorld(level, Vec3.atBottomCenterOf(worldPosition.offset(landings.get(landing).exit())));
    }

    /** A sound at the car's landing door, where whoever waits hears it. */
    private void sound(ServerLevel level, SoundEvent sound, float pitch) {
        BlockPos door = worldPosition.offset(landings.get(at).door());
        level.playSound(null, door, sound, SoundSource.BLOCKS, 1.0F, pitch);
    }

    private void enter(Phase next) {
        if (next == Phase.OPENING && level instanceof ServerLevel server) {
            calls.remove(at);
            doors(server, at, true);
            sound(server, AerolargeRegistry.LIFT_DOOR_SOUND.get(), 1.0F);
        }
        phase = next;
        ticks = 0;
        lasts = OPEN_TICKS;
        changed();
    }

    /** Its car, or a new one standing at the floor it was at, once the old one has been missing a while. */
    @Nullable
    private LiftCar carOrNew(ServerLevel level) {
        LiftCar found = car(level);
        if (found != null) {
            missing = 0;
            return found;
        }
        if (car != null && ++missing < CAR_MISSING) {
            return null;
        }
        LiftCar fresh = new LiftCar(AerolargeRegistry.LIFT_CAR.get(), level);
        fresh.hold(level, worldPosition, null);
        fresh.standAt(landings.get(Math.min(at, landings.size() - 1)).depth());
        level.addFreshEntity(fresh);
        car = fresh.getUUID();
        missing = 0;
        if (phase == Phase.FETCHING || phase == Phase.RIDING) {
            enter(Phase.OPENING);
        }
        setChanged();
        return fresh;
    }

    @Nullable
    private LiftCar car(ServerLevel level) {
        Entity found = car == null ? null : level.getEntity(car);
        return found instanceof LiftCar lift && !lift.isRemoved() ? lift : null;
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveShown(tag);
        if (car != null) {
            tag.putUUID("Car", car);
        }
        if (autopilot != null) {
            tag.putLong("Autopilot", autopilot.asLong());
        }
        tag.putString("Phase", phase.name());
        tag.putInt("Target", target);
    }

    /** What the boards show: the landings, the floor the car is at and the one it goes to, the way, the phase. */
    private void saveShown(CompoundTag tag) {
        ListTag saved = new ListTag();
        landings.forEach(landing -> saved.add(landing.save()));
        tag.put("Landings", saved);
        tag.putInt("At", at);
        tag.putInt("Going", target);
        tag.putBoolean("GoingUp", goingUp);
        tag.putString("Shown", phase.name());
    }

    /**
     * Loaded again, a lift stands at the floor it was at or going to, its doors as they were left: a ride cut short by
     * an unload or a restart ends at a floor, never between two.
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        landings.clear();
        for (Tag entry : tag.getList("Landings", Tag.TAG_COMPOUND)) {
            landings.add(Landing.load((CompoundTag) entry));
        }
        at = Math.max(0, Math.min(tag.getInt("At"), landings.size() - 1));
        car = tag.hasUUID("Car") ? tag.getUUID("Car") : car;
        autopilot = tag.contains("Autopilot") ? BlockPos.of(tag.getLong("Autopilot")) : null;
        goingUp = tag.getBoolean("GoingUp");
        if (tag.contains("Phase")) {
            settle(Phase.named(tag.getString("Phase")), tag.getInt("Target"));
        } else {
            phase = Phase.named(tag.getString("Shown"));
            target = tag.contains("Going") ? tag.getInt("Going") : -1;
        }
    }

    /** A phase loaded from the save, carried on from a floor: a ride on its way ends at the floor it was going to. */
    private void settle(Phase saved, int savedTarget) {
        boolean onItsWay = saved == Phase.RIDING || saved == Phase.FETCHING;
        if (onItsWay && savedTarget >= 0 && savedTarget < landings.size() && saved == Phase.RIDING) {
            at = savedTarget;
        }
        phase = onItsWay ? Phase.OPENING : saved;
        target = onItsWay ? -1 : savedTarget;
        ticks = 0;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveShown(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
