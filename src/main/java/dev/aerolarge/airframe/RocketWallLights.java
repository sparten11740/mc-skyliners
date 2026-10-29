package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.WallLightBlock;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Wall washers round her rooms (see {@link WallLightBlock}): on every story with a wall of her cover to throw light
 * up, eight of them round it over head height, between her windows and the way to her lift, each fixed to her skin
 * where it stands at the room's edge; warm white, a cool blue in the bunk room and pink in the club. Not on the
 * observation deck, whose wall over its band of glass is all starlight, nor in the cockpit, glass all round. Laid
 * once the rooms have their furniture and before the LED strips, in cells along the skin the rooms keep clear.
 */
final class RocketWallLights implements RocketPiece {
    /** How far round from her front each of a story's wall washers is, in degrees: clear of the four ways. */
    private static final double[] ROUNDS = {30, 60, 120, 150, 210, 240, 300, 330};
    /** How many rows over its floor a story's wall washers are fixed: over the head of anyone walking past. */
    private static final int UP = 3;
    /** How far in from her body's radius the search for the wall begins, and how far past it it ends, in blocks. */
    private static final double SEARCH_IN = 3;
    private static final double SEARCH_OUT = 1;
    /** How finely the search steps out toward the wall, in blocks. */
    private static final double STEP = 0.5;
    /** The stories with no wall of cover to light: the observation deck. */
    private static final List<Integer> UNLIT = List.of(RocketObservationDeck.STORY);

    private final RocketLayout layout;

    RocketWallLights(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return layout.rooms();
    }

    @Override
    public void build() {
        List<Integer> floors = layout.floors();
        for (int story = 0; story < floors.size(); story++) {
            if (!UNLIT.contains(story)) {
                story(story, floors.get(story));
            }
        }
    }

    /** A story's wall washers, each where it can be fixed to her skin. */
    private void story(int story, int floor) {
        WallLightBlock.Tone tone = story == RocketBunks.STORY ? WallLightBlock.Tone.COOL
            : story == RocketClub.STORY ? WallLightBlock.Tone.PINK : WallLightBlock.Tone.WARM;
        int y = floor + UP;
        for (double round : ROUNDS) {
            Mount mount = againstSkin(round, y);
            if (mount != null) {
                layout.set(mount.at().getX(), y, mount.at().getZ(), AerolargeRegistry.WALL_LIGHT.get().shining(mount.facing(), tone));
            }
        }
    }

    /** Where a wall washer is fixed, and the way it shines, away from the skin it is fixed to. */
    private record Mount(BlockPos at, Direction facing) {
    }

    /**
     * The block out along a way round from her front, at a row, that stands open at the room's edge with her cover
     * beside it on a side facing out from her axis, and room over it for its light: the outermost of the blocks the
     * way passes through and those beside them further out, since where her wall steps the way can pass from open air
     * into her cover corner to corner; null where there is none.
     */
    @Nullable
    private Mount againstSkin(double round, int y) {
        Mount found = null;
        for (double out = layout.radius() - SEARCH_IN; out <= layout.radius() + SEARCH_OUT; out += STEP) {
            BlockPos on = new BlockPos(RocketRoom.across(out, round), y, RocketRoom.along(out, round));
            for (BlockPos at : List.of(on, on.relative(outward(on, true)), on.relative(outward(on, false)))) {
                Mount mount = mount(at);
                if (mount != null && (found == null || farther(at, found.at()))) {
                    found = mount;
                }
            }
        }
        return found;
    }

    /** The way out from her axis along one of the two axes, across (x) or along (z), at a block. */
    private static Direction outward(BlockPos at, boolean across) {
        if (across) {
            return at.getX() < 0 ? Direction.WEST : Direction.EAST;
        }
        return at.getZ() < 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /** A wall washer fixed at a block, open with room over it and her cover beside it; null where it cannot be. */
    @Nullable
    private Mount mount(BlockPos at) {
        Direction back = skinBeside(at.getX(), at.getY(), at.getZ());
        boolean room = layout.isOpen(at.getX(), at.getY(), at.getZ()) && layout.isOpen(at.getX(), at.getY() + 1, at.getZ());
        return back != null && room ? new Mount(at, back.getOpposite()) : null;
    }

    /** Whether a block lies further out from her axis than another. */
    private static boolean farther(BlockPos at, BlockPos than) {
        return at.getX() * at.getX() + at.getZ() * at.getZ() > than.getX() * than.getX() + than.getZ() * than.getZ();
    }

    /** The side of a block, facing out from her axis, that her cover stands on; null for none. */
    @Nullable
    private Direction skinBeside(int x, int y, int z) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean outward = side.getStepX() * x + side.getStepZ() * z > 0;
            if (outward && layout.stateAt(x + side.getStepX(), y, z + side.getStepZ()).is(AerolargeRegistry.ZEPPELIN_COVER.get())) {
                return side;
            }
        }
        return null;
    }
}
