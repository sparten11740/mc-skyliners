package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.entity.LiftCar;
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
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
 * step out. The car moves only with every door shut and no one in a doorway, and lets no one off between floors.
 *
 * <p>Whoever is in the car stands on its floor and walks about freely, at a floor and on a ride, as in any lift: the
 * machine fills the shaft's opening with the car's floor (see {@link LiftFloorBlock}), solid wherever the car's floor
 * is now, so it rides with the car (see {@link LiftCar}). The lift runs whatever the ship does, flying or not.
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
     * How close to the shaft's middle a player stands to be in the car, across and up, in blocks: anywhere on its
     * floor, out to its very edge, so whoever is in it is taken along to the floor picked.
     */
    private static final double ABOARD_ACROSS = 1.6;
    private static final double ABOARD_UP = 2.0;
    /** How far out from the shaft's middle the car's floor reaches each way, in blocks: the opening in its glass. */
    private static final int CAR_HALF = 1;
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
        RIDING;

        /**
         * A phase by its saved name; standing for none or one not known. A lift saved parked, as it stood with its
         * doors open while its ship flew before it ran in flight, stands open.
         */
        static Phase named(String name) {
            if (name.equals("PARKED")) {
                return BOARDING;
            }
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
    /**
     * Whether the shaft has been looked over for the car's floor since the machine was loaded: a ship built before the
     * floor filled it had it laid only where the car stood.
     */
    private boolean shaftFilled;

    public LiftMachineBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LIFT_MACHINE_ENTITY.get(), pos, state);
    }

    /**
     * Set as the ship is built: her landings, bottom first, each turned as she was placed; the car starts at the
     * lowest.
     */
    public void configure(List<Landing> built, Rotation turn) {
        landings.clear();
        built.forEach(landing -> landings.add(landing.turned(turn)));
        shaftFilled = false;
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
     * with another ride, it comes once that is done.
     */
    public boolean call(ServerLevel level, int from) {
        if (from < 0 || from >= landings.size()) {
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
     * or picked again); never while it is on its way.
     */
    public boolean ask(ServerLevel level, int from, int to) {
        if (from < 0 || from >= landings.size() || to < 0 || to >= landings.size()) {
            return false;
        }
        if (phase == Phase.IDLE) {
            target = to;
            if (at == from) {
                enter(someoneInCar(level) ? Phase.CLOSING : Phase.OPENING);
            } else {
                LiftCar running = car(level);
                goingUp = landings.get(from).depth() < landings.get(at).depth();
                at = from;
                enter(Phase.FETCHING);
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
        if (!lift.shaftFilled) {
            lift.shaftFilled = true;
            lift.fillShaft(level);
        }
        lift.ticks++;
        lift.doorsSlid(level);
        lift.step(level, running);
        running.hold(level, pos);
    }

    /** One tick of the lift's work in its phase. */
    private void step(ServerLevel level, LiftCar running) {
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
        }
    }

    /** Its doors sliding open, and once they are, standing open. */
    private void opening(ServerLevel level, LiftCar running) {
        if (ticks >= DOOR_TICKS) {
            enter(Phase.BOARDING);
        }
    }

    /** On its way: once the car stands at its floor, a chime, and the doors open. */
    private void arriving(ServerLevel level, LiftCar running) {
        if (!running.arrived()) {
            return;
        }
        if (phase == Phase.RIDING) {
            at = target;
            target = -1;
        }
        sound(level, goingUp ? SkylinersRegistry.LIFT_CHIME_UP.get() : SkylinersRegistry.LIFT_CHIME_DOWN.get(), CHIME_PITCH);
        enter(Phase.OPENING);
        changed();
    }

    /**
     * Standing open, whoever is in it free to walk about and out: once it has stood long enough, with a floor picked or
     * no one in it, and no one in the way, the doors close. With another landing calling, it stands open only a moment
     * empty, and no longer than it would empty for someone in it who has picked no floor: it goes to answer the call,
     * taking them along.
     */
    private void boarding(ServerLevel level, LiftCar running) {
        boolean picked = target >= 0 && target != at;
        boolean waited = !calls.isEmpty();
        boolean someone = someoneInCar(level);
        int stays = waited && !someone ? Math.min(lasts, PICKED_TICKS) : lasts;
        if (ticks < stays || !picked && someone && !waited || doorwayTaken(level)) {
            return;
        }
        enter(Phase.CLOSING);
        doors(level, at, false);
        sound(level, SkylinersRegistry.LIFT_DOOR_SOUND.get(), 1.0F);
    }

    /** The doors shut: off with whoever is in it to their floor, or standing shut. */
    private void closed(ServerLevel level, LiftCar running) {
        if (target >= 0 && target != at && someoneInCar(level)) {
            goingUp = landings.get(target).depth() < landings.get(at).depth();
            enter(Phase.RIDING);
            running.ride(level, landings.get(target).depth());
            sound(level, SkylinersRegistry.LIFT_START.get(), 1.0F);
        } else {
            target = -1;
            enter(Phase.IDLE);
            answerCalls(level);
        }
        changed();
    }

    /** Whether anyone stands in the car where it is now. */
    private boolean someoneInCar(ServerLevel level) {
        return !inCar(level).isEmpty();
    }

    /** The players standing in the car, on its floor at the landing it is at, spectators left out. */
    private List<ServerPlayer> inCar(ServerLevel level) {
        Vec3 floor = Vec3.atBottomCenterOf(worldPosition.below(landings.get(at).depth()));
        List<ServerPlayer> found = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            Vec3 off = SableFlight.toPlot(level, worldPosition, player.position(), false).subtract(floor);
            boolean onFloor = Math.abs(off.x) < ABOARD_ACROSS && Math.abs(off.z) < ABOARD_ACROSS && off.y > -0.5 && off.y < ABOARD_UP;
            if (onFloor && !player.isSpectator() && player.getVehicle() == null) {
                found.add(player);
            }
        }
        return found;
    }

    /**
     * Fills the shaft's opening with the car's floor (see {@link LiftFloorBlock}) wherever it is open, from the lowest
     * landing's floor up to the highest's: every block the floor's top passes through on a ride.
     */
    private void fillShaft(ServerLevel level) {
        for (BlockPos cell : shaftCells()) {
            if (level.getBlockState(cell).isAir()) {
                level.setBlock(cell, SkylinersRegistry.LIFT_FLOOR.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Takes the car's floor out of the shaft, as the machine is taken down. */
    public void clearShaft(ServerLevel level) {
        for (BlockPos cell : shaftCells()) {
            if (level.getBlockState(cell).is(SkylinersRegistry.LIFT_FLOOR.get())) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * The blocks the car's floor may lie in: the shaft's opening, from the row under the lowest landing's feet up to
     * the row under the highest's.
     */
    private List<BlockPos> shaftCells() {
        List<BlockPos> cells = new ArrayList<>();
        if (landings.isEmpty()) {
            return cells;
        }
        BlockPos bottom = worldPosition.below(landings.getFirst().depth() + 1).offset(-CAR_HALF, 0, -CAR_HALF);
        BlockPos top = worldPosition.below(landings.getLast().depth() + 1).offset(CAR_HALF, 0, CAR_HALF);
        for (BlockPos cell : BlockPos.betweenClosed(bottom, top)) {
            cells.add(cell.immutable());
        }
        return cells;
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

    /** A sound at the car's landing door, where whoever waits hears it. */
    private void sound(ServerLevel level, SoundEvent sound, float pitch) {
        BlockPos door = worldPosition.offset(landings.get(at).door());
        level.playSound(null, door, sound, SoundSource.BLOCKS, 1.0F, pitch);
    }

    private void enter(Phase next) {
        if (next == Phase.OPENING && level instanceof ServerLevel server) {
            calls.remove(at);
            doors(server, at, true);
            sound(server, SkylinersRegistry.LIFT_DOOR_SOUND.get(), 1.0F);
        }
        phase = next;
        ticks = 0;
        lasts = OPEN_TICKS;
        changed();
    }

    /**
     * Its car, or a new one standing at the floor it was at, once the old one has been missing a while; a ride cut short
     * so ends there.
     */
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
        LiftCar fresh = new LiftCar(SkylinersRegistry.LIFT_CAR.get(), level);
        fresh.hold(level, worldPosition);
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
