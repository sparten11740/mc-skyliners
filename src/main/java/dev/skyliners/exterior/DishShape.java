package dev.skyliners.exterior;

import net.minecraft.world.phys.shapes.ArrayVoxelShape;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * What of a satellite dish's dish stands over its mount, to collide with (see {@link SatelliteDishBlock}): its bowl,
 * its hub and the feed horn on its boom, turned as they are drawn. It is the quarter-block cells round its block whose
 * middles the dish takes in, each part at least half a cell thick so none slips between them, made straight into one
 * shape rather than joined box by box, which for as many boxes costs milliseconds: one is made in about a quarter of
 * a millisecond, and each is kept once made (see {@link SatelliteDishBlock#getCollisionShape}). A player walks into the
 * bowl as it is drawn and stands on it, however it is turned.
 */
final class DishShape {
    /** How big a cell of the dish's shape is, in blocks. */
    private static final double CELL = 0.25;
    /** How many cells there are to a block. */
    private static final int PER_BLOCK = 4;
    /** How many cells the grid round the dish's block spans each way: its block and as far as a dish reaches either side. */
    private static final int CELLS = (2 * SatelliteDishBlockEntity.REACH_BLOCKS + 1) * PER_BLOCK;
    /** Where the grid starts, in blocks from the block's corner, the same each way. */
    private static final double GRID_FROM = -SatelliteDishBlockEntity.REACH_BLOCKS;
    /** How far either way of its middle line or plane each part reaches at least, in blocks: a little over half a cell. */
    private static final double LEAST_HALF = 0.13;
    /** Where the dish's model has its middle line, across and up, in its own pixels (see tools/generate_resources.mjs). */
    private static final double MIDDLE = 8;
    /**
     * Its parts, each round its middle line, as wide as given either way of it and from so far along it to so far, in
     * its model's pixels, its face toward low values: the bowl, the hub at its back, the boom out of its middle and the
     * feed horn at the boom's end.
     */
    private static final double[][] PARTS = {{9, 7.5, 10.1}, {1.7, 10, 12}, {0.6, 1, 9}, {1.5, -1.6, 1}};

    private DishShape() {
    }

    /**
     * The dish's shape where it is held as the pose given, from its model's pixels over sixteen to its block's own
     * blocks, drawn as many times its model's size as given.
     */
    static VoxelShape of(Matrix4f pose, float scale) {
        Matrix4f toModel = new Matrix4f(pose).invert();
        double least = LEAST_HALF * 16 / scale;
        boolean[] taken = new boolean[CELLS * CELLS * CELLS];
        int[] low = {CELLS, CELLS, CELLS};
        int[] high = {-1, -1, -1};
        Vector3f at = new Vector3f();
        for (int cell = 0; cell < taken.length; cell++) {
            int[] index = {cell % CELLS, cell / CELLS % CELLS, cell / (CELLS * CELLS)};
            at.set(middleOf(index[0]), middleOf(index[1]), middleOf(index[2]));
            toModel.transformPosition(at).mul(16);
            taken[cell] = inDish(at, least);
            for (int axis = 0; taken[cell] && axis < 3; axis++) {
                low[axis] = Math.min(low[axis], index[axis]);
                high[axis] = Math.max(high[axis], index[axis]);
            }
        }
        return high[0] < 0 ? Shapes.empty() : grid(taken, low, high);
    }

    /** The shape of the cells taken, cut down to the box round them, given by its lowest and highest cells. */
    private static VoxelShape grid(boolean[] taken, int[] low, int[] high) {
        BitSetDiscreteVoxelShape cells = new BitSetDiscreteVoxelShape(high[0] - low[0] + 1, high[1] - low[1] + 1, high[2] - low[2] + 1);
        for (int cell = 0; cell < taken.length; cell++) {
            if (taken[cell]) {
                cells.fill(cell % CELLS - low[0], cell / CELLS % CELLS - low[1], cell / (CELLS * CELLS) - low[2]);
            }
        }
        return new ArrayVoxelShape(cells, lines(low[0], high[0]), lines(low[1], high[1]), lines(low[2], high[2])) {
        };
    }

    /** Where the cells from the lowest to the highest given start and end along an axis, in blocks. */
    private static double[] lines(int low, int high) {
        double[] lines = new double[high - low + 2];
        for (int line = 0; line < lines.length; line++) {
            lines[line] = GRID_FROM + (low + line) * CELL;
        }
        return lines;
    }

    /** The middle of a cell along an axis, in blocks from the block's corner. */
    private static float middleOf(int index) {
        return (float) (GRID_FROM + (index + 0.5) * CELL);
    }

    /** Whether a point in the dish's model's pixels is in one of its parts, each at least so many pixels thick. */
    private static boolean inDish(Vector3f at, double least) {
        double across = Math.hypot(at.x - MIDDLE, at.y - MIDDLE);
        for (double[] part : PARTS) {
            double middle = (part[1] + part[2]) / 2;
            boolean along = Math.abs(at.z - middle) <= Math.max((part[2] - part[1]) / 2, least);
            if (along && across <= Math.max(part[0], least)) {
                return true;
            }
        }
        return false;
    }
}
