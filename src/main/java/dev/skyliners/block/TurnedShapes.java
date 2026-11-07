package dev.skyliners.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A block's shape, or a point of it, drawn facing north, turned to face each way, as its model is turned by the
 * blockstate.
 */
public final class TurnedShapes {
    private TurnedShapes() {
    }

    /** The shape for each horizontal facing, turned a quarter at a time clockwise, seen from above, from north. */
    public static Map<Direction, VoxelShape> of(VoxelShape north) {
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

    /** A point given in pixels across and along the model drawn facing north, turned the given way: in blocks. */
    static Vec3 point(Direction facing, double x, double z) {
        return switch (facing) {
            case SOUTH -> new Vec3(16 - x, 0, 16 - z).scale(1.0 / 16);
            case EAST -> new Vec3(16 - z, 0, x).scale(1.0 / 16);
            case WEST -> new Vec3(z, 0, 16 - x).scale(1.0 / 16);
            default -> new Vec3(x, 0, z).scale(1.0 / 16);
        };
    }
}
