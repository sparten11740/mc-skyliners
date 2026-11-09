package dev.skyliners.spaceweather;

import dev.skyliners.airframe.SurfaceCut;
import dev.skyliners.block.ShellBlock;
import dev.skyliners.block.ShellGeometry;
import dev.skyliners.block.SpaceRockBlock;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * The blocks of a rock of space weather, worked out from how big it is and its seed: its shape (see {@link RockShape}),
 * roughened, cut block by block at its skin by the same cutter as a hull's (see {@link SurfaceCut}), and solid stone
 * all the way through, so a shot that breaks one block finds another behind it. Plain working out that can take a
 * second or more for a giant, so it is done away from the server's own thread.
 */
final class RockCells {
    /**
     * How far a rock's blocks may reach from its middle, as a share of its half width: its lumps and stretches at their
     * most.
     */
    private static final double MOST_REACH = 2.4;

    private RockCells() {
    }

    /** A rock's blocks by where they lie round its middle, in its own frame. */
    static Map<BlockPos, BlockState> of(double size, long seed) {
        RockShape.Look look = RockShape.look(seed);
        Skin skin = new Skin(size, seed, look);
        BlockState stone = SpaceRockBlock.of(look);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        int reach = skin.reach;
        for (int x = -reach; x < reach; x++) {
            for (int y = -reach; y < reach; y++) {
                addRow(blocks, skin, stone, x, y);
            }
        }
        return blocks;
    }

    /** One row of blocks along z: each cut to its skin, or whole if under it. */
    private static void addRow(Map<BlockPos, BlockState> blocks, Skin skin, BlockState stone, int x, int y) {
        for (int z = -skin.reach; z < skin.reach; z++) {
            int shape = SurfaceCut.shape(skin, x, y, z);
            if (shape != ShellGeometry.NONE) {
                blocks.put(new BlockPos(x, y, z), ShellBlock.withShape(stone, shape));
            } else if (skin.depth(x + 0.5, y + 0.5, z + 0.5) < 0) {
                blocks.put(new BlockPos(x, y, z), stone);
            }
        }
    }

    /** A rock's skin, how far a point lies out of it kept for the corners of blocks, each asked for by up to eight. */
    private static final class Skin implements SurfaceCut.Surface {
        final int reach;
        private final double size;
        private final long seed;
        private final double radius;
        private final RockShape shape;
        private final Vector3f stretch;
        private final float[] corners;
        private final int side;

        Skin(double size, long seed, RockShape.Look look) {
            this.size = size;
            this.seed = seed;
            radius = size / 2;
            shape = RockShape.of(look.shape());
            stretch = new Vector3f(shape.stretch()).mul(look.stretch());
            reach = (int) Math.ceil(radius * MOST_REACH) + 1;
            side = 2 * reach + 1;
            corners = new float[side * side * side];
            Arrays.fill(corners, Float.NaN);
        }

        @Override
        public double depth(double x, double y, double z) {
            if (x != Math.rint(x) || y != Math.rint(y) || z != Math.rint(z)) {
                return worked(x, y, z);
            }
            int index = (((int) x + reach) * side + (int) y + reach) * side + (int) z + reach;
            if (Float.isNaN(corners[index])) {
                corners[index] = (float) worked(x, y, z);
            }
            return corners[index];
        }

        private double worked(double x, double y, double z) {
            Vector3f way = new Vector3f((float) (x / radius / stretch.x), (float) (y / radius / stretch.y), (float) (z / radius / stretch.z));
            float out = way.length();
            if (out < 1e-5F) {
                return -radius;
            }
            way.div(out);
            return (out - shape.radius(way)) * radius + RockShape.rough(x, y, z, size, seed);
        }

        @Override
        public double[] outward(double x, double y, double z) {
            double length = Math.max(1e-6, Math.sqrt(x * x + y * y + z * z));
            return new double[] {x / length, y / length, z / length};
        }
    }
}
