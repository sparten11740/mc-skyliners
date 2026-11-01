package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.CabinSeatBlock;
import dev.skyliners.block.CrewLockerBlock;
import dev.skyliners.block.CrewLockerBlockEntity;
import dev.skyliners.block.HaloLightBlock;
import dev.skyliners.block.LightTone;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * One of the rocket's stories furnished as its room (see {@link RocketPiece}): a round room round her glass lift shaft
 * in the middle, from its floor to the next. Its furniture keeps clear of what every story keeps clear: the shaft and a
 * walkway round it, the way out from the lift's door to the front, the fronts of her four oval windows, and a ring
 * along the skin left to walk round and for the light strips along it. Furniture is placed by how far out from her
 * axis it stands and how far round from her front, clockwise seen from above, so a room comes out round at every
 * scale, or in one of the room's quarters (see {@link RocketQuarter}), as a corner of its own; and anything that does
 * not fit is left out.
 */
abstract class RocketRoom implements RocketPiece {
    /** How far in from the skin's surface furniture keeps, in blocks: the ring along it is left clear. */
    private static final double FROM_SKIN = 2.5;
    /** How far out from the shaft's walls the walkway round it reaches, in blocks. */
    private static final int WALKWAY = 1;
    /** The rows of a story's windows over its floor, from and to, and how far either side of a window's middle. */
    private static final int WINDOW_FROM = 1;
    private static final int WINDOW_TO = 4;
    private static final int WINDOW_HALF = 2;
    /** How far in from the skin's surface a window's front is kept clear, in blocks. */
    private static final double WINDOW_CLEAR = 4;
    /** How far either side of her front-to-back line the way out from the lift's door is kept clear, in blocks. */
    private static final int WAY_OUT_HALF = 1;
    /** How many rows over the floor a pendant lamp hangs, on its cable down from the ceiling: well over a head. */
    private static final int LAMP_UP = 5;
    /** How far out from her axis the lights hanging at the four diagonals round her shaft are, in blocks. */
    private static final double LAMPS_OUT = 5.5;
    /** Her halo lights (see {@link HaloLightBlock}), warm white and a cool blue night light, and a length of cable. */
    protected static final BlockState WARM_LIGHT = SkylinersRegistry.HALO_LIGHT.get().defaultBlockState();
    protected static final BlockState NIGHT_LIGHT = SkylinersRegistry.HALO_LIGHT.get().defaultBlockState().setValue(HaloLightBlock.TONE, LightTone.COOL);
    private static final BlockState CABLE = SkylinersRegistry.LIGHT_CABLE.get().defaultBlockState();
    /** What a room's random sequence starts from, its story added, so each room comes out the same every build. */
    private static final long SCATTER_SEED = 0x526f6f6d73L;

    protected final RocketLayout layout;
    private final int story;

    RocketRoom(RocketLayout layout, int story) {
        this.layout = layout;
        this.story = story;
    }

    @Override
    public boolean wanted() {
        return layout.rooms() && layout.floors().size() > story;
    }

    /**
     * A random sequence of the room's own, to set things about a little unevenly, as a lived-in room is: the same each
     * time she is built, so a rocket comes out as the last one did. Each call starts the sequence over: take it once
     * a build.
     */
    protected RandomSource scatter() {
        return RandomSource.create(SCATTER_SEED + story);
    }

    /** The room's floor row. */
    protected int floor() {
        return layout.floors().get(story);
    }

    /** The room's highest row, under the next floor. */
    protected int top() {
        return layout.storyTop(story);
    }

    /** How far out from her axis the skin's inside is, in blocks. */
    protected double wall() {
        return layout.radius() - 1;
    }

    /** Whether a block is free for furniture: open, well in from the skin, and clear of everything kept clear. */
    protected boolean free(int x, int y, int z) {
        return free(x, y, z, 0);
    }

    /**
     * Whether a block is free for furniture so many blocks further in from the ring along the skin than furniture
     * keeps, or nearer it for a negative distance, and clear of everything else kept clear.
     */
    protected boolean free(int x, int y, int z, double further) {
        if (!layout.isOpen(x, y, z) || !inRoom(x, y, z, further)) {
            return false;
        }
        return offTheWays(x, z, 0) && !beforeWindow(x, y, z);
    }

    /** Whether a block lies in from the ring kept clear along the skin, and so many blocks further in. */
    protected boolean inRoom(int x, int y, int z, double further) {
        return layout.inFrom(x, y, z, FROM_SKIN + further);
    }

    /**
     * Whether a spot of the floor plan lies clear of the shaft, the walkway round it and the way out from the lift's
     * door to the front, and so many blocks clear of them.
     */
    protected static boolean offTheWays(int x, int z, int further) {
        int shaft = RocketLayout.SHAFT + WALKWAY + further;
        boolean byShaft = Math.abs(x) <= shaft && Math.abs(z) <= shaft;
        return !byShaft && !(Math.abs(x) <= WAY_OUT_HALF + further && z < 0);
    }

    /** Whether the story has the layout's oval windows, whose fronts are kept clear. */
    protected boolean windowed() {
        return true;
    }

    /** Whether a block stands in front of one of the story's windows, out to the four ways, near the skin. */
    protected boolean beforeWindow(int x, int y, int z) {
        if (!windowed()) {
            return false;
        }
        int up = y - floor();
        boolean inWindowRows = up >= WINDOW_FROM && up <= WINDOW_TO;
        boolean onAWay = Math.abs(x) <= WINDOW_HALF || Math.abs(z) <= WINDOW_HALF;
        return inWindowRows && onAWay && !layout.inFrom(x, y, z, WINDOW_CLEAR);
    }

    /** Puts a block where it is free, and says whether it did. */
    protected boolean put(int x, int y, int z, BlockState state) {
        if (!free(x, y, z)) {
            return false;
        }
        layout.set(x, y, z, state);
        return true;
    }

    /**
     * A pendant lamp at a spot of the floor plan: the lamp given hanging {@link #LAMP_UP} rows over the floor from a
     * cable down from the ceiling, where every block of them is free, breaking up a tall room's bare height with a
     * line and a pool of light.
     */
    protected void pendant(int x, int z, BlockState lamp) {
        int lowest = floor() + LAMP_UP;
        for (int y = lowest; y <= top(); y++) {
            if (!free(x, y, z)) {
                return;
            }
        }
        layout.set(x, lowest, z, lamp);
        for (int y = lowest + 1; y <= top(); y++) {
            layout.set(x, y, z, CABLE);
        }
    }

    /** Warm halo lights hanging at the four diagonals round her shaft, just out from the walkway's corners. */
    protected void lightsRoundShaft() {
        for (double round = 45; round < 360; round += 90) {
            pendant(across(LAMPS_OUT, round), along(LAMPS_OUT, round), WARM_LIGHT);
        }
    }

    /** An armchair facing the way given, its back over it, where both are free. */
    protected void seat(int x, int y, int z, Direction facing, DyeColor colour) {
        if (!free(x, y, z) || !free(x, y + 1, z)) {
            return;
        }
        BlockState seat = CabinSeatBlock.seat(SkylinersRegistry.BUSINESS_SEAT.get(), colour, facing);
        layout.set(x, y, z, seat);
        layout.set(x, y + 1, z, seat.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * A crew locker facing the way given, where it is free, stocked once it stands with a space helmet for whoever it
     * belongs to.
     */
    protected void crewLocker(int x, int y, int z, Direction facing) {
        if (put(x, y, z, SkylinersRegistry.CREW_LOCKER.get().defaultBlockState().setValue(CrewLockerBlock.FACING, facing))) {
            layout.fit(new BlockPos(x, y, z), RocketRoom::stockLocker);
        }
    }

    /** A crew locker's space helmet, put in its first slot. */
    private static void stockLocker(BlockEntity blockEntity, Rotation rotation) {
        if (blockEntity instanceof CrewLockerBlockEntity locker) {
            locker.setItem(0, new ItemStack(SkylinersRegistry.SPACE_HELMET.get()));
        }
    }

    /**
     * Puts a block where it is free and stands on something, and says whether it did: the block under it taken, and
     * neither water nor a rug, so nothing stands on the pool or floats over a carpet.
     */
    protected boolean standing(int x, int y, int z, BlockState state) {
        BlockState under = layout.stateAt(x, y - 1, z);
        if (layout.isOpen(x, y - 1, z) || !under.getFluidState().isEmpty() || under.getBlock() instanceof CarpetBlock) {
            return false;
        }
        return put(x, y, z, state);
    }

    /** Stands a block so many high, one on another, as far up as each is free. */
    protected void stack(int x, int y, int z, BlockState state, int high) {
        int up = 0;
        while (up < high && standing(x, y + up, z, state)) {
            up++;
        }
    }

    /** A rug of carpet over every free block of the floor between two corners of the floor plan. */
    protected void rug(int fromX, int fromZ, int toX, int toZ, BlockState carpet) {
        int y = floor() + 1;
        for (BlockPos at : BlockPos.betweenClosed(Math.min(fromX, toX), y, Math.min(fromZ, toZ), Math.max(fromX, toX), y, Math.max(fromZ, toZ))) {
            standing(at.getX(), y, at.getZ(), carpet);
        }
    }

    /**
     * How far out the room's corners reach, the same across and along: the furthest a spot so far out both ways at a
     * diagonal is in the room (see {@link RocketQuarter}), and never less than the first spot clear of the walkway,
     * which may not be in a room too small for any corner; what is put there is only put where it is free.
     */
    protected int corner() {
        int y = floor() + 1;
        int out = RocketQuarter.INNER;
        while (inRoom(out + 1, y, out + 1, 0)) {
            out++;
        }
        return out;
    }

    /**
     * How far out toward her side the room reaches at so far along toward her front or back, the same in any quarter:
     * the last spot free going out from the walkway, found before anything is laid in the room; one short of the first
     * spot out from the walkway where none is free.
     */
    protected int reach(int along) {
        int y = floor() + 1;
        int out = RocketQuarter.INNER - 1;
        while (free(out + 1, y, along)) {
            out++;
        }
        return out;
    }

    /**
     * The way a piece of a curved run of furniture faces, its front toward her axis: the nearest of the four ways in,
     * unless another piece of the run stands there, as where the run steps round, and then the other way in.
     */
    protected static Direction facingOff(List<BlockPos> run, BlockPos at) {
        double round = roundOf(at.getX(), at.getZ());
        Direction in = inward(round);
        if (!run.contains(at.relative(in))) {
            return in;
        }
        double sin = Math.sin(Math.toRadians(round));
        double cos = Math.cos(Math.toRadians(round));
        if (in.getAxis() == Direction.Axis.X) {
            return cos > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return sin > 0 ? Direction.WEST : Direction.EAST;
    }

    /**
     * The blocks of a row so far out from her axis, give or take half a block, between two angles round from her
     * front, in degrees, in order round: a curved run of furniture's places.
     */
    protected static List<BlockPos> run(int y, double out, double from, double to) {
        int reach = (int) Math.ceil(out) + 1;
        return BlockPos.betweenClosedStream(-reach, y, -reach, reach, y, reach)
            .filter(at -> Math.abs(Math.hypot(at.getX(), at.getZ()) - out) < 0.5)
            .filter(at -> roundOf(at.getX(), at.getZ()) >= from && roundOf(at.getX(), at.getZ()) <= to)
            .map(BlockPos::immutable)
            .sorted(Comparator.comparingDouble(at -> roundOf(at.getX(), at.getZ())))
            .toList();
    }

    /** How far round from her front a spot of the floor plan is, clockwise seen from above, in degrees, 0 to 360. */
    protected static double roundOf(int x, int z) {
        double round = Math.toDegrees(Math.atan2(x, -z));
        return round < 0 ? round + 360 : round;
    }

    /** How far to her right of her axis a spot is, so far out and so far round from her front, in blocks. */
    protected static int across(double out, double round) {
        return nearest(out * Math.sin(Math.toRadians(round)));
    }

    /** How far behind her axis a spot is, so far out and so far round from her front, in blocks. */
    protected static int along(double out, double round) {
        return nearest(-out * Math.cos(Math.toRadians(round)));
    }

    /** The nearest block to a distance from her axis, halves rounded away from it, so rooms match either side. */
    private static int nearest(double blocks) {
        return (int) (Math.signum(blocks) * Math.round(Math.abs(blocks)));
    }

    /** The way from a spot so far round from her front toward her axis, the nearest of the four. */
    protected static Direction inward(double round) {
        double sin = Math.sin(Math.toRadians(round));
        double cos = Math.cos(Math.toRadians(round));
        if (Math.abs(sin) > Math.abs(cos)) {
            return sin > 0 ? Direction.WEST : Direction.EAST;
        }
        return cos > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** The way on round her clockwise, seen from above, at a spot so far round from her front: nearest of the four. */
    protected static Direction clockwise(double round) {
        return inward(round).getCounterClockWise();
    }
}
