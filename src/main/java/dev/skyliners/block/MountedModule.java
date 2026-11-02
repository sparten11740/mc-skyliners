package dev.skyliners.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A console module a player puts up where they like (see {@link ConsoleModule}): as a button goes, against the block
 * clicked, standing on a floor or a desk, fixed to a wall looking out from it, or hung face down under a ceiling (see
 * {@link Mount}). Its model is drawn standing on the floor, its face looking up and whoever works it at its north side,
 * and its blockstate tips it over onto the wall or under the ceiling before turning it to its facing; its shape is
 * tipped and turned the same way (see {@link #shapes}). FACING is the way toward whoever put it up: on a wall the way
 * its face looks out, so that the near edge of a module standing on the floor is its lower edge on the wall; on the
 * floor the way its near edge is, and under a ceiling the way the top of what is drawn on it is, so that it reads the
 * right way up to them looking down or up at it. The blueprints put their modules on the floor of a console, as all
 * were.
 */
public final class MountedModule {
    /** How a module is put up; one saved before it had a mount stood on a floor or a desk, as all did then. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.CEILING, Mount.WALL, Mount.FLOOR);

    private MountedModule() {
    }

    /**
     * A module put up by a player: against the block clicked, as a button goes (see {@link Mounting}), on top of it, on
     * its side or under it, facing whoever puts it there. Where nothing would hold it, it stands where it is put as it
     * always could, on another console module or a desk whose top is not whole.
     */
    public static BlockState place(BlockPlaceContext context, BlockState base) {
        Direction placer = context.getHorizontalDirection().getOpposite();
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return base.setValue(MOUNT, Mount.FLOOR).setValue(HorizontalDirectionalBlock.FACING, placer);
        }
        Mount mount = Mount.against(toward);
        return base.setValue(MOUNT, mount).setValue(HorizontalDirectionalBlock.FACING, mount == Mount.WALL ? toward.getOpposite() : placer);
    }

    /** How a module is put up: on the floor for one whose block has no mount of its own, as every module once was. */
    public static Mount of(BlockState state) {
        return state.hasProperty(MOUNT) ? state.getValue(MOUNT) : Mount.FLOOR;
    }

    /**
     * How far a module's model is tipped over about the east-west line, in degrees, as its blockstate tips it before
     * turning it to its facing: onto a wall or under a ceiling (see tools/generate_resources.mjs).
     */
    public static int tilt(Mount mount) {
        return switch (mount) {
            case FLOOR -> 0;
            case WALL -> 90;
            case CEILING -> 180;
        };
    }

    /**
     * A module's shape for every mount and facing, from its shape standing on the floor drawn facing north: tipped over
     * as its model is, then turned (see {@link TurnedShapes}).
     */
    static Map<Mount, Map<Direction, VoxelShape>> shapes(VoxelShape north) {
        Map<Mount, Map<Direction, VoxelShape>> shapes = new EnumMap<>(Mount.class);
        for (Mount mount : Mount.values()) {
            shapes.put(mount, TurnedShapes.of(tipped(north, mount)));
        }
        return shapes;
    }

    /** A module's shape as it is put up and turned, among its shapes (see {@link #shapes}). */
    static VoxelShape shape(Map<Mount, Map<Direction, VoxelShape>> shapes, BlockState state) {
        return shapes.get(of(state)).get(state.getValue(HorizontalDirectionalBlock.FACING));
    }

    /**
     * A shape drawn facing north, tipped as a blockstate's x rotation tips a model: by a quarter turn onto a wall, its
     * floor against the south side of its block and its north side down; by a half turn under a ceiling, upside down
     * and its north and south sides swapped.
     */
    private static VoxelShape tipped(VoxelShape north, Mount mount) {
        if (mount == Mount.FLOOR) {
            return north;
        }
        VoxelShape[] shape = {Shapes.empty()};
        north.forAllBoxes((x0, y0, z0, x1, y1, z1) -> shape[0] = Shapes.or(shape[0], mount == Mount.WALL
            ? Shapes.box(x0, z0, 1 - y1, x1, z1, 1 - y0)
            : Shapes.box(x0, 1 - y1, 1 - z1, x1, 1 - y0, 1 - z0)));
        return shape[0];
    }
}
