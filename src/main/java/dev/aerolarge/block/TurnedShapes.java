package dev.aerolarge.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A block's shape drawn facing north, turned to face each way, as its model is turned by the blockstate. */
final class TurnedShapes {
    private TurnedShapes() {
    }

    /** The shape for each horizontal facing, turned a quarter at a time clockwise, seen from above, from north. */
    static Map<Direction, VoxelShape> of(VoxelShape north) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int turns = (facing.get2DDataValue() + 2) % 4;
            VoxelShape[] shape = {Shapes.empty()};
            north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                double[] from = {x0, z0};
                double[] to = {x1, z1};
                for (int k = 0; k < turns; k++) {
                    from = new double[]{1 - from[1], from[0]};
                    to = new double[]{1 - to[1], to[0]};
                }
                shape[0] = Shapes.or(shape[0], Shapes.box(
                    Math.min(from[0], to[0]), y0, Math.min(from[1], to[1]), Math.max(from[0], to[0]), y1, Math.max(from[1], to[1])));
            });
            shapes.put(facing, shape[0]);
        }
        return shapes;
    }
}
