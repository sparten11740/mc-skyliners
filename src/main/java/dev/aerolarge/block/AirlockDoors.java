package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A rocket's front door as sliding airlock doors (see {@link AirlockDoorBlock}): two leaves meeting in the middle of
 * her doorway, each half its width and all its height, sliding apart into her skin either side. Her stairs button
 * keeps them with her airstairs (see {@link RocketStairsBlockEntity}): they open as the stairs come out and shut as the
 * stairs fold away, once the flight is off the sill, and her airlock panel works them while she stands with her stairs
 * out (see {@link AirlockPanelBlock}), cycling first, its latches clunking and air hissing out of its vents, and
 * opening after it with its chime; shut, they seal with a clunk. They never shut on anyone in the doorway: they wait
 * open, with a chime, until it is clear, and slide back open if someone steps in as they close. The panels round the
 * doorway show how they stand. The server runs them and tells every client where they are going, each client running
 * the same clock to draw them in between.
 */
public final class AirlockDoors {
    /** How long the leaves take to slide open or shut, in ticks. */
    public static final int SLIDE_TICKS = 30;
    /** How long the panel's cycle runs before the leaves slide open, in ticks: its hiss's. */
    private static final int CYCLE_TICKS = 60;
    /** While someone stands in the doorway, how often shutting looks again whether they have stepped out, in ticks. */
    private static final int CLEAR_CHECK_TICKS = 10;
    /**
     * How far from the doorway's middle at its sill its airlock panels are looked for, in blocks: across and along, and
     * up and down, as a panel stands on the floor inside it.
     */
    private static final int PANEL_REACH = 8;
    private static final int PANEL_RISE = 2;
    /** How many puffs of air leave the doorway's foot as it unlatches, how far they spread and how fast. */
    private static final int PUFFS = 10;
    private static final double PUFF_SPREAD = 0.6;
    private static final double PUFF_SPEED = 0.03;
    private static final float HISS_VOLUME = 0.8F;
    private static final float SEAL_VOLUME = 0.8F;
    private static final float CHIME_VOLUME = 1.0F;

    private final RocketStairsBlockEntity keeper;
    /**
     * The doorway's frame, as its stairs': from the keeper, its bottom block on the left seen from outside, whose
     * bottom is the sill; the way out, and the way to the right seen from outside; and how many blocks wide and tall
     * it is.
     */
    private final BlockPos corner;
    private final Direction out;
    private final Direction right;
    private final int width;
    private final int rows;
    /** Whether the leaves are open or opening; how far open, in ticks, now and a tick ago. */
    private boolean open;
    private int ticks;
    private int lastTicks;
    /** How many ticks are left of the panel's cycle before the leaves slide open; none while it is not cycling. */
    private int cycling;
    /** Shutting is held up by someone in the doorway; the chime has sounded. */
    private boolean held;
    private int cooldown;

    AirlockDoors(RocketStairsBlockEntity keeper, BlockPos corner, Direction out, Direction right, int width, int rows) {
        this.keeper = keeper;
        this.corner = corner;
        this.out = out;
        this.right = right;
        this.width = width;
        this.rows = rows;
    }

    /** The doorway's bottom left block seen from outside, where it is. */
    public BlockPos corner() {
        return keeper.getBlockPos().offset(corner);
    }

    public Direction out() {
        return out;
    }

    public Direction right() {
        return right;
    }

    public int width() {
        return width;
    }

    public int rows() {
        return rows;
    }

    /** How far open the leaves stand at this partial tick, from 0 shut to 1 open, eased off and in. */
    public float openness(float partialTick) {
        float done = Mth.clamp(Mth.lerp(partialTick, lastTicks, ticks) / SLIDE_TICKS, 0, 1);
        return done * done * (3 - 2 * done);
    }

    /** Whether the leaves are shut and staying that way: sealed. */
    boolean shut() {
        return !open && ticks == 0 && cycling == 0;
    }

    /** Whether the leaves are going shut, or shut. */
    boolean shutting() {
        return !open && cycling == 0;
    }

    /** Whether the leaves stand all the way open, and staying that way. */
    private boolean clear() {
        return open && ticks == SLIDE_TICKS;
    }

    /**
     * Opens or shuts the leaves as the stairs come out or fold away: opening at once, unlatching with the hiss of the
     * airlock; shutting once the doorway is clear.
     */
    void set(ServerLevel level, boolean toOpen) {
        if (toOpen && !open) {
            cycling = 0;
            startOpening(level);
        } else if (!toOpen && (open || cycling > 0)) {
            startShutting(level);
        }
    }

    /**
     * Its airlock panel used: shut, the airlock cycles and then the leaves open; open, they shut; on their way, only a
     * click.
     */
    void cycle(ServerLevel level, BlockPos panel) {
        if (shut()) {
            cycling = CYCLE_TICKS;
            AirlockPanelBlock.cycleEffects(level, panel);
            show(level, AirlockPanelBlock.Cycle.CYCLING);
            keeper.sync();
        } else if (clear()) {
            startShutting(level);
        } else {
            level.playSound(null, panel, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, AirlockPanelBlock.BUSY_VOLUME, 1.0F);
        }
    }

    private void startOpening(ServerLevel level) {
        open = true;
        held = false;
        if (ticks == 0) {
            sound(level, AerolargeRegistry.AIRLOCK_CYCLE.get(), HISS_VOLUME);
            Vec3 foot = Vec3.atBottomCenterOf(middle());
            level.sendParticles(ParticleTypes.CLOUD, foot.x, foot.y, foot.z, PUFFS, PUFF_SPREAD, 0, PUFF_SPREAD, PUFF_SPEED);
        }
        leaves(level, true);
        show(level, AirlockPanelBlock.Cycle.CYCLING);
        keeper.sync();
    }

    /**
     * Sets the leaves going shut: at once, or once nobody is in the doorway; still shut, as with the panel's cycle
     * not yet run, the cycle is called off.
     */
    private void startShutting(ServerLevel level) {
        open = false;
        cycling = 0;
        if (ticks == 0) {
            show(level, AirlockPanelBlock.Cycle.SEALED);
        } else if (anyoneInDoorway(level)) {
            hold(level);
        } else {
            show(level, AirlockPanelBlock.Cycle.CYCLING);
        }
        keeper.sync();
    }

    /** Held open by someone in the doorway, with a chime, the panels showing it clear. */
    private void hold(ServerLevel level) {
        held = true;
        cooldown = CLEAR_CHECK_TICKS;
        level.playSound(null, middle(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, CHIME_VOLUME, 0.7F);
        show(level, AirlockPanelBlock.Cycle.CLEAR);
        keeper.sync();
    }

    /** Once a tick, on the server: the cycle run on, the leaves a tick on, or a look whether the doorway is clear. */
    void tick(ServerLevel level) {
        lastTicks = ticks;
        if (cycling > 0) {
            if (--cycling == 0) {
                startOpening(level);
            }
            return;
        }
        if (held) {
            waitForClear(level);
            return;
        }
        if (open && ticks < SLIDE_TICKS) {
            ticks++;
            if (ticks == SLIDE_TICKS) {
                level.playSound(null, middle(), AerolargeRegistry.AIRLOCK_CLEAR.get(), SoundSource.BLOCKS, CHIME_VOLUME, 1.0F);
                show(level, AirlockPanelBlock.Cycle.CLEAR);
            }
        } else if (!open && ticks > 0) {
            closeIn(level);
        }
    }

    /** The leaves a tick nearer shut, sliding back open if someone has stepped into the doorway; sealed once home. */
    private void closeIn(ServerLevel level) {
        if (anyoneInDoorway(level)) {
            hold(level);
            return;
        }
        ticks--;
        if (ticks == 0) {
            leaves(level, false);
            sound(level, SoundEvents.IRON_TRAPDOOR_CLOSE, SEAL_VOLUME);
            show(level, AirlockPanelBlock.Cycle.SEALED);
            keeper.sync();
        }
    }

    /** Held open: now and then a look whether the doorway is clear, and then shut. */
    private void waitForClear(ServerLevel level) {
        if (ticks < SLIDE_TICKS) {
            ticks++;
            return;
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = CLEAR_CHECK_TICKS;
        if (!anyoneInDoorway(level)) {
            held = false;
            show(level, AirlockPanelBlock.Cycle.CYCLING);
            keeper.sync();
        }
    }

    /** On a client, each tick: the same clock the server runs, toward where the leaves are going. */
    void clientTick() {
        lastTicks = ticks;
        if (cycling > 0) {
            cycling--;
            return;
        }
        boolean opening = open || held;
        ticks = Math.clamp(ticks + (opening ? 1 : -1), 0, SLIDE_TICKS);
    }

    /** Sets the doorway's blocks open, nothing to bump into, or shut. */
    private void leaves(ServerLevel level, boolean toOpen) {
        for (int column = 0; column < width; column++) {
            for (int row = 0; row < rows; row++) {
                BlockPos at = block(column, row);
                BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof AirlockDoorBlock && state.getValue(AirlockDoorBlock.OPEN) != toOpen) {
                    level.setBlock(at, state.setValue(AirlockDoorBlock.OPEN, toOpen), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** A block of the doorway, so many columns across from its left seen from outside and rows up from its sill. */
    private BlockPos block(int column, int row) {
        return corner().relative(right, column).above(row);
    }

    /** The doorway's middle block at the sill, where its sounds come from. */
    private BlockPos middle() {
        return block(width / 2, 0);
    }

    /** Whether anyone stands in the doorway, where the leaves close. */
    private boolean anyoneInDoorway(ServerLevel level) {
        AABB doorway = AABB.encapsulatingFullBlocks(block(0, 0), block(width - 1, rows - 1));
        return AirstairControllerBlockEntity.anyoneIn(level, keeper.getBlockPos(), List.of(doorway));
    }

    /** Shows how the airlock stands on every airlock panel round the doorway. */
    private void show(ServerLevel level, AirlockPanelBlock.Cycle cycle) {
        BlockPos middle = middle();
        for (BlockPos at : BlockPos.betweenClosed(middle.offset(-PANEL_REACH, -PANEL_RISE, -PANEL_REACH), middle.offset(PANEL_REACH, PANEL_RISE, PANEL_REACH))) {
            AirlockPanelBlock.show(level, at, cycle);
        }
    }

    /** Whether a block lies within reach of the doorway's middle, as its airlock panels do. */
    boolean near(BlockPos pos) {
        BlockPos middle = middle();
        return Math.abs(pos.getX() - middle.getX()) <= PANEL_REACH && Math.abs(pos.getY() - middle.getY()) <= PANEL_RISE
            && Math.abs(pos.getZ() - middle.getZ()) <= PANEL_REACH;
    }

    private void sound(ServerLevel level, SoundEvent sound, float volume) {
        level.playSound(null, middle(), sound, SoundSource.BLOCKS, volume, 1.0F);
    }

    void save(CompoundTag tag) {
        tag.put("Corner", NbtUtils.writeBlockPos(corner));
        tag.putString("Out", out.getSerializedName());
        tag.putString("Right", right.getSerializedName());
        tag.putInt("Width", width);
        tag.putInt("Rows", rows);
        tag.putBoolean("Open", open);
        tag.putInt("Ticks", ticks);
        tag.putInt("Cycling", cycling);
        tag.putBoolean("Held", held);
    }

    /** The doors as saved, turned by the quarter turn their keeper has turned since (see {@link Turning}). */
    static AirlockDoors load(RocketStairsBlockEntity keeper, CompoundTag tag, Rotation turn) {
        Direction out = Direction.byName(tag.getString("Out"));
        Direction right = Direction.byName(tag.getString("Right"));
        AirlockDoors doors = new AirlockDoors(keeper, Turning.turn(NbtUtils.readBlockPos(tag, "Corner").orElse(BlockPos.ZERO), turn),
            turn.rotate(out == null ? Direction.NORTH : out), turn.rotate(right == null ? Direction.WEST : right),
            Math.max(1, tag.getInt("Width")), Math.max(1, tag.getInt("Rows")));
        doors.open = tag.getBoolean("Open");
        doors.ticks = Math.clamp(tag.getInt("Ticks"), 0, SLIDE_TICKS);
        doors.lastTicks = doors.ticks;
        doors.cycling = Math.clamp(tag.getInt("Cycling"), 0, CYCLE_TICKS);
        doors.held = tag.getBoolean("Held");
        return doors;
    }
}
