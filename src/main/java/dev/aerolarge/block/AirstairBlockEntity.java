package dev.aerolarge.block;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The forward door's integral airstairs, their motor and what they know, kept in the controller in the jamb aft of
 * the door; its renderer draws them (see {@link AirstairFlight} for how they are built and move). Stowed, they lie
 * folded in a bay under the cabin floor, behind a hatch in the hull below the door: the hatch is the hull's own skin
 * there, which the controller takes away as the hatch opens and puts back as it shuts, so shut it is the hull, cut to
 * its curve like the rest. Coming out, the door opens, the hatch drops, the flight slides out of its bay, rises to the
 * sill, swings down and runs out to the ground; once it is down, the controller lays the steps it is walked on (see
 * {@link AirstairStepBlock}). How far down it reaches is settled as it sets out: the first block of the way the
 * ground, or anything else, takes ends it there. Going away it waits, with a chime, until nobody is on it, takes the
 * steps up and runs it all back, shutting the door once the flight is off the sill. It only comes out with the airliner
 * standing still, and goes away of its own accord once the airliner moves. The server runs it and tells every client
 * where it is going; each client runs the same clock to draw it in between.
 *
 * <p>An airliner built before has its old stairs of blocks out, perhaps, and no bay: the old stairs fold away the old
 * way the first time (see {@link AirstairControllerBlockEntity}), and from then on the new ones come out, their hatch
 * found in the hull below the door, the flight sliding into the hold behind it. One built before the plug doors keeps
 * its old stairs.
 */
public class AirstairBlockEntity extends AirstairControllerBlockEntity implements Airstairs {
    /** While someone stands on the stairs, how often folding looks again whether they have stepped off. */
    private static final int CLEAR_CHECK_TICKS = 10;
    /** How often stairs that are out look whether the airliner has started to move, in ticks. */
    private static final int WATCH_TICKS = 10;
    /** Faster than this, in blocks a second, the airliner is moving: the stairs will not come out, and fold if out. */
    private static final double MOVING = 1.0;
    /** For an airliner built before the bay: how far in from the door's face the skin below it lies, near enough. */
    private static final float OLD_HATCH_INSET = 0.5F;
    /** In an airliner built before: how far in from the door's face the hull below it is searched for the hold. */
    private static final int OLD_MOUTH_SEARCH = 4;
    private static final float MOTOR_VOLUME = 0.9F;
    private static final float LATCH_VOLUME = 1.0F;
    /** The latch clunks higher coming free than going home. */
    private static final float UNLATCH_PITCH = 1.25F;

    /** How many blocks the flight comes down from the sill to the wheels' ground, and in how many sections. */
    private int rise;
    private int sections = AirstairFlight.FEWEST_SECTIONS;
    /** How far in from the door's face the hull's skin over the bay's mouth lies, in blocks. */
    private float hatchInset = OLD_HATCH_INSET;
    /** The skin over the bay's mouth, shut: where each block of it is from the controller, and what it is. */
    private List<Step> hatch = List.of();
    /** Out or coming out; and how far through coming out, in ticks, now and a tick ago. */
    private boolean deploying;
    private int ticks;
    private int lastTicks;
    /** How many blocks down the stairs reach this time. */
    private int length;
    /** Folding is held up by someone on the stairs; the chime has sounded. */
    private boolean held;
    private int cooldown;
    /** Whether the steps are laid, and the hatch's skin is off. */
    private boolean laid;
    /** Whether a foot was laid with them, below the bottom step (see {@link AirstairSteps#lay}). */
    private boolean foot;
    private boolean hatchOpen;
    /**
     * Going away, the door is still to be shut: once the flight has swung up off the sill and dropped to the bay's
     * mouth, out of the way of the panel swinging back past the top of the stairs.
     */
    private boolean doorToShut;
    /** Whether its links have been looked at since it was loaded, to mend them (see {@link Turning#mend}). */
    private boolean linksChecked;

    public AirstairBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.AIRSTAIR_CONTROLLER_ENTITY.get(), pos, state);
    }

    /**
     * Hands the controller the stairs it was built with: where the door they serve is from it, how far they come
     * down and in how many sections, and the hatch's skin, how far in it lies and where each block of it is, shut. The
     * airliner is built with them down this many blocks, the steps laid and the hatch open; none, and they are built
     * stowed.
     */
    public void configure(BlockPos door, int rise, int length, int sections, float hatchInset, List<Step> hatch) {
        super.configure(List.of(), door);
        this.rise = rise;
        this.sections = sections;
        this.hatchInset = hatchInset;
        this.hatch = List.copyOf(hatch);
        this.length = length;
        deploying = length > 0;
        ticks = deploying ? AirstairFlight.OUT : 0;
        lastTicks = ticks;
        laid = deploying;
        hatchOpen = deploying;
        doorToShut = false;
        linksChecked = true;
        setChanged();
    }

    /** Whether the stairs are the new kind: serving a plug door, with none of the old kind's blocks out. */
    private boolean integral() {
        return !standing() && served() != null;
    }

    /**
     * Whether the stairs are out or coming out. The new ones answer from where they are themselves: the old ones'
     * record of coming out stays as they were built, out, however often the new ones have folded since.
     */
    @Override
    protected boolean unfolding() {
        return integral() ? deploying : super.unfolding();
    }

    /** Whether the stairs are all folded away and staying that way: the new ones in their bay, its hatch shut. */
    @Override
    protected boolean folded() {
        return integral() ? !deploying && ticks == 0 && !hatchOpen : super.folded();
    }

    /** The served door's frame: its bottom left part, the way it faces, and its right as seen from outside. */
    @Nullable
    @Override
    public Frame frame() {
        BlockPos door = served();
        if (door == null || level == null) {
            return null;
        }
        BlockState part = level.getBlockState(door);
        return new Frame(door, part.getValue(PlugDoorBlock.FACING), PlugDoorBlock.right(part));
    }

    /** The served door's bottom left part, where it is; null when the controller serves no plug door. */
    @Nullable
    public BlockPos served() {
        mendLinks();
        BlockPos door = door();
        if (level == null || door == null) {
            return null;
        }
        BlockPos at = worldPosition.offset(door);
        return PlugDoorBlock.isController(level.getBlockState(at)) ? at : null;
    }

    /**
     * Once after loading, as the door is first looked for, mends the links if the one to the door leads nowhere: the
     * door, the hatch and any old stairs turn together.
     */
    private void mendLinks() {
        BlockPos door = door();
        if (!linksChecked && level != null && door != null) {
            linksChecked = true;
            Turning.mend(level, worldPosition, List.of(door), PlugDoorBlock::isController, this::turnLinks);
        }
    }

    /** Turns the hatch's skin with the rest, and tells the clients, which draw the stairs from the door and the hatch. */
    @Override
    protected void turnLinks(Rotation turn) {
        super.turnLinks(turn);
        hatch = hatch.stream().map(part -> part.turned(turn)).toList();
        sync();
    }

    @Override
    public void toggle() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (standing()) {
            // The old stairs fold away the old way; serving a plug door they stay away, and the new ones come out next.
            if (unfolding() || served() == null) {
                super.toggle();
            }
            return;
        }
        if (served() == null) {
            super.toggle();
            return;
        }
        if (deploying) {
            fold(server);
        } else {
            deploy(server);
        }
    }

    /** Sets the stairs coming out, unless the airliner is moving or there is no room below the door. */
    private void deploy(ServerLevel level) {
        if (moving(level)) {
            refuse(level);
            return;
        }
        if (ticks == 0) {
            int reach = reach(level);
            if (rise <= 0 || reach < AirstairFlight.shortest(rise, sections)) {
                refuse(level);
                return;
            }
            length = reach;
            openHatch(level);
            sound(level, AerolargeRegistry.AIRSTAIR_LATCH.get(), LATCH_VOLUME, UNLATCH_PITCH);
        }
        deploying = true;
        held = false;
        doorToShut = false;
        door(level, true);
        if (ticks < AirstairFlight.OUT) {
            sound(level, AerolargeRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME, 1.0F);
        }
        sync();
    }

    /** Sets the stairs going away: at once, or once nobody is on them; the door shuts behind them. */
    private void fold(ServerLevel level) {
        deploying = false;
        doorToShut = true;
        if (laid) {
            if (anyoneOn(level)) {
                held = true;
                cooldown = CLEAR_CHECK_TICKS;
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
            } else {
                takeUp(level);
            }
        }
        if (!held) {
            sound(level, AerolargeRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME, 1.0F);
        }
        sync();
    }

    private void refuse(ServerLevel level) {
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    @Override
    protected void tick(ServerLevel level) {
        if (!integral()) {
            // Old stairs of blocks: they work the old way, and fold as the airliner moves off; once they are all away,
            // serving a plug door, the new ones are the stairs.
            if (standing() && unfolding() && served() != null && level.getGameTime() % WATCH_TICKS == 0 && moving(level)) {
                super.toggle();
            }
            super.tick(level);
            return;
        }
        lastTicks = ticks;
        if (deploying && level.getGameTime() % WATCH_TICKS == 0 && moving(level)) {
            fold(level);
        }
        if (held) {
            if (--cooldown <= 0) {
                cooldown = CLEAR_CHECK_TICKS;
                if (!anyoneOn(level)) {
                    held = false;
                    takeUp(level);
                    sound(level, AerolargeRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME, 1.0F);
                    sync();
                }
            }
            return;
        }
        if (deploying && ticks < AirstairFlight.OUT) {
            ticks++;
            if (ticks == AirstairFlight.HATCH || ticks == AirstairFlight.SLIDE || ticks == AirstairFlight.SWING || ticks == AirstairFlight.EXTEND) {
                sound(level, AerolargeRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME, 1.0F);
            }
            if (ticks == AirstairFlight.OUT) {
                lay(level);
                sound(level, AerolargeRegistry.AIRSTAIR_LATCH.get(), LATCH_VOLUME, 1.0F);
                sync();
            }
            setChanged();
        } else if (!deploying && (ticks > 0 || hatchOpen)) {
            ticks = Math.max(0, ticks - 1);
            if (doorToShut && ticks <= AirstairFlight.SLIDE) {
                door(level, false);
                doorToShut = false;
            }
            if (ticks == AirstairFlight.EXTEND || ticks == AirstairFlight.SWING || ticks == AirstairFlight.SLIDE || ticks == AirstairFlight.HATCH) {
                sound(level, AerolargeRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME, 1.0F);
            }
            if (ticks == 0) {
                closeHatch(level);
                sound(level, AerolargeRegistry.AIRSTAIR_LATCH.get(), LATCH_VOLUME, 1.0F);
                sync();
            }
            setChanged();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, AirstairBlockEntity airstairs) {
        airstairs.lastTicks = airstairs.ticks;
        if (!airstairs.held && airstairs.rise > 0) {
            airstairs.ticks = Math.clamp(airstairs.ticks + (airstairs.deploying ? 1 : -1), 0, AirstairFlight.OUT);
        }
    }

    /**
     * Where the stairs are, between the last tick and this one, to draw them; null while there is nothing to draw:
     * stowed behind the shut hatch, or old stairs of blocks.
     */
    @Nullable
    @Override
    public AirstairFlight.Motion motion(float partialTick) {
        if (rise <= 0 || standing() || ticks == 0 && lastTicks == 0 && !hatchOpen) {
            return null;
        }
        return new AirstairFlight.Motion(Mth.lerp(partialTick, lastTicks, ticks), length, rise, sections, AirstairFlight.WIDTH, hatchInset, mouth(),
            hatchOpen, false);
    }

    /** How far down the stairs could come this time: blocks clear all the way from the door, up to their whole rise. */
    private int reach(ServerLevel level) {
        return AirstairSteps.reach(level, this::step, AirstairFlight.WIDTH, rise, cell -> false);
    }

    /**
     * The step {@code down} blocks below the sill, in one of the flight's two columns (0 the left, seen from outside),
     * as far out from the door as it is down. Null while the door is gone.
     */
    @Nullable
    private BlockPos step(int down, int column) {
        BlockPos door = served();
        if (door == null || level == null) {
            return null;
        }
        BlockState part = level.getBlockState(door);
        Direction out = part.getValue(PlugDoorBlock.FACING);
        return door.relative(PlugDoorBlock.right(part), column).relative(out, down).below(down);
    }

    /** Whether a block is one of the steps this controller has laid. */
    @Override
    public boolean holds(Level level, BlockPos pos) {
        return laid && level == this.level && AirstairSteps.holds(this::step, AirstairFlight.WIDTH, laidDown(), pos);
    }

    /** Lays the steps under the flight, each with a guard over it holding its side's handrail, and the foot below. */
    private void lay(ServerLevel level) {
        BlockPos door = served();
        if (door == null) {
            return;
        }
        Direction up = level.getBlockState(door).getValue(PlugDoorBlock.FACING).getOpposite();
        foot = AirstairSteps.lay(level, this::step, AirstairFlight.WIDTH, length, up);
        laid = true;
    }

    /** How far down the laid steps reach, the foot below the bottom step included. */
    private int laidDown() {
        return length + (foot ? 1 : 0);
    }

    /** Takes the steps up, and the guards with them; only the steps' own blocks, whatever stands in their place. */
    private void takeUp(ServerLevel level) {
        AirstairSteps.takeUp(level, this::step, AirstairFlight.WIDTH, laidDown());
        laid = false;
        foot = false;
    }

    /** Whether anyone is on the steps, or in the way just over them. */
    private boolean anyoneOn(ServerLevel level) {
        return anyoneIn(level, AirstairSteps.spots(this::step, AirstairFlight.WIDTH, length));
    }

    /**
     * Takes the hatch's skin away, as the hatch drops. An airliner built before the bay has its hatch found the first
     * time: the hull's own panels in the row below the sill, from the outside in to the hold.
     */
    private void openHatch(ServerLevel level) {
        if (hatch.isEmpty()) {
            hatch = oldHatch(level);
        }
        for (Step part : hatch) {
            BlockPos at = worldPosition.offset(part.offset());
            if (level.getBlockState(at).is(part.state().getBlock())) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        hatchOpen = true;
    }

    /** Puts the hatch's skin back as it was, where nothing else has taken its place. */
    private void closeHatch(ServerLevel level) {
        for (Step part : hatch) {
            BlockPos at = worldPosition.offset(part.offset());
            BlockState there = level.getBlockState(at);
            if (there.isAir() || there.canBeReplaced()) {
                level.setBlock(at, part.state(), Block.UPDATE_ALL);
            }
        }
        hatchOpen = false;
    }

    /**
     * The hatch of an airliner built before the bay: in each of the flight's columns, the row below the sill, from
     * just outside the door's face in through the hull's panels to the open hold. None where that is not plain hull
     * all the way to open air within reach: the flight then slides in through the skin.
     */
    private List<Step> oldHatch(ServerLevel level) {
        BlockPos door = served();
        if (door == null) {
            return List.of();
        }
        Direction out = level.getBlockState(door).getValue(PlugDoorBlock.FACING);
        List<Step> found = new ArrayList<>();
        for (int column = 0; column < AirstairFlight.WIDTH; column++) {
            BlockPos row = door.relative(PlugDoorBlock.right(level.getBlockState(door)), column).below(2);
            boolean open = false;
            for (int in = -1; in < OLD_MOUTH_SEARCH && !open; in++) {
                BlockPos at = row.relative(out, -in);
                BlockState state = level.getBlockState(at);
                if (state.isAir()) {
                    open = in >= 0;
                } else if (state.is(Aboard.AIRFRAME) && !state.hasBlockEntity()) {
                    found.add(new Step(at.subtract(worldPosition), state));
                } else {
                    return List.of();
                }
            }
            if (!open) {
                return List.of();
            }
        }
        return List.copyOf(found);
    }

    /** How deep the bay's mouth is, in blocks in from the door's face: as deep as the hatch's skin is thick. */
    private int mouth() {
        BlockPos door = door();
        if (door == null || level == null || hatch.isEmpty()) {
            return 1;
        }
        BlockState part = level.getBlockState(worldPosition.offset(door));
        if (!PlugDoorBlock.isDoor(part)) {
            return 1;
        }
        Direction out = part.getValue(PlugDoorBlock.FACING);
        int deepest = 0;
        for (Step skin : hatch) {
            BlockPos from = skin.offset().subtract(door);
            deepest = Math.max(deepest, -(from.getX() * out.getStepX() + from.getZ() * out.getStepZ()));
        }
        return deepest + 1;
    }

    /** Opens or shuts the door the stairs serve. */
    private void door(ServerLevel level, boolean open) {
        BlockPos at = served();
        PlugDoorBlockEntity door = at == null ? null : PlugDoorBlockEntity.of(level, at);
        if (door != null) {
            door.set(open);
        }
    }

    private boolean moving(ServerLevel level) {
        return SableFlight.velocity(level, Vec3.atCenterOf(worldPosition)).length() > MOVING;
    }

    /** A sound from just below the door, where the bay is. */
    private void sound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        BlockPos door = served();
        BlockPos at = door == null ? worldPosition : door.below(2);
        level.playSound(null, at, sound, SoundSource.BLOCKS, volume, pitch);
    }

    /** Takes the stairs away at once, steps, hatch and all, as when the controller itself is broken. */
    public void stowAway() {
        if (!(level instanceof ServerLevel server) || !integral()) {
            foldAway();
            return;
        }
        if (laid) {
            takeUp(server);
        }
        if (hatchOpen) {
            closeHatch(server);
        }
        deploying = false;
        ticks = 0;
        setChanged();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Rise", rise);
        tag.putInt("Sections", sections);
        tag.putFloat("HatchInset", hatchInset);
        ListTag skin = new ListTag();
        for (Step part : hatch) {
            CompoundTag entry = new CompoundTag();
            entry.put("Offset", NbtUtils.writeBlockPos(part.offset()));
            entry.put("State", NbtUtils.writeBlockState(part.state()));
            skin.add(entry);
        }
        tag.put("Hatch", skin);
        tag.putBoolean("Deploying", deploying);
        tag.putInt("Ticks", ticks);
        tag.putInt("Length", length);
        tag.putBoolean("Held", held);
        tag.putBoolean("Laid", laid);
        tag.putBoolean("Foot", foot);
        tag.putBoolean("HatchOpen", hatchOpen);
        tag.putBoolean("DoorToShut", doorToShut);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains("Rise")) {
            // Built before the stairs folded into the hull: as far down as the old stairs came, but for their landing.
            rise = Math.max(0, stageCount() - 1);
            return;
        }
        rise = tag.getInt("Rise");
        sections = Math.clamp(tag.getInt("Sections"), 1, AirstairFlight.MOST_SECTIONS);
        hatchInset = tag.getFloat("HatchInset");
        Rotation turn = Turning.since(tag, getBlockState());
        List<Step> skin = new ArrayList<>();
        for (Tag entry : tag.getList("Hatch", Tag.TAG_COMPOUND)) {
            CompoundTag part = (CompoundTag) entry;
            skin.add(new Step(NbtUtils.readBlockPos(part, "Offset").orElse(BlockPos.ZERO),
                NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), part.getCompound("State"))).turned(turn));
        }
        hatch = List.copyOf(skin);
        deploying = tag.getBoolean("Deploying");
        ticks = Math.clamp(tag.getInt("Ticks"), 0, AirstairFlight.OUT);
        lastTicks = ticks;
        length = Math.clamp(tag.getInt("Length"), 0, rise);
        held = tag.getBoolean("Held");
        laid = tag.getBoolean("Laid");
        foot = tag.getBoolean("Foot");
        hatchOpen = tag.getBoolean("HatchOpen");
        doorToShut = tag.getBoolean("DoorToShut");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
