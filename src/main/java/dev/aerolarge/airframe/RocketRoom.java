package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CabinSeatBlock;
import dev.aerolarge.block.CrewLockerBlock;
import dev.aerolarge.block.CrewLockerBlockEntity;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
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
 * scale, and anything that does not fit is left out.
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
    /** How many rows over the floor a pendant lamp hangs, on its chain down from the ceiling: well over a head. */
    private static final int LAMP_UP = 5;
    /** How far out from her axis the lanterns hanging at the four diagonals round her shaft are, in blocks. */
    private static final double LAMPS_OUT = 5.5;
    /** A lantern and a soul lantern hanging from a chain, and a link of the chain. */
    protected static final BlockState LANTERN = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    protected static final BlockState SOUL_LANTERN = Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
    private static final BlockState CHAIN = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);

    protected final RocketLayout layout;
    private final int story;

    RocketRoom(RocketLayout layout, int story) {
        this.layout = layout;
        this.story = story;
    }

    @Override
    public boolean wanted() {
        return AerolargeConfig.ROCKET_ROOMS.get() && layout.floors().size() > story;
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
        if (!layout.isOpen(x, y, z) || !inRoom(x, y, z, 0)) {
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
    private boolean beforeWindow(int x, int y, int z) {
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
     * chain down from the ceiling, where every block of them is free, breaking up a tall room's bare height with a
     * line and a pool of warm light.
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
            layout.set(x, y, z, CHAIN);
        }
    }

    /** Lanterns hanging at the four diagonals round her shaft, just out from the walkway's corners. */
    protected void lanternsRoundShaft() {
        for (double round = 45; round < 360; round += 90) {
            pendant(across(LAMPS_OUT, round), along(LAMPS_OUT, round), LANTERN);
        }
    }

    /** An armchair facing the way given, its back over it, where both are free. */
    protected void seat(int x, int y, int z, Direction facing, DyeColor colour) {
        if (!free(x, y, z) || !free(x, y + 1, z)) {
            return;
        }
        BlockState seat = CabinSeatBlock.seat(AerolargeRegistry.BUSINESS_SEAT.get(), colour, facing);
        layout.set(x, y, z, seat);
        layout.set(x, y + 1, z, seat.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * A crew locker facing the way given, where it is free, stocked once it stands with a space helmet for whoever it
     * belongs to.
     */
    protected void crewLocker(int x, int y, int z, Direction facing) {
        if (put(x, y, z, AerolargeRegistry.CREW_LOCKER.get().defaultBlockState().setValue(CrewLockerBlock.FACING, facing))) {
            layout.fit(new BlockPos(x, y, z), RocketRoom::stockLocker);
        }
    }

    /** A crew locker's space helmet, put in its first slot. */
    private static void stockLocker(BlockEntity blockEntity, Rotation rotation) {
        if (blockEntity instanceof CrewLockerBlockEntity locker) {
            locker.setItem(0, new ItemStack(AerolargeRegistry.SPACE_HELMET.get()));
        }
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
