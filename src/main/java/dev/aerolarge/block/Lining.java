package dev.aerolarge.block;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The hull's lining: which faces of a block of the airframe's skin look into the airliner, and how each of them looks.
 * Outside the skin is white plating; inside, wherever it is seen from the cabin, the hold or the flight deck, it is
 * lined like an airliner's cabin. The blueprints work out which faces look in as they build (see the layout's
 * {@code HullLining}) and keep it in the blocks, so it turns with them and flies with them: a whole panel, and the
 * relay set into the wall beside a lamp, keep a property for each of their six faces; a block cut to the hull's curve
 * that the inside sees is a {@link LinedShellBlock}, lined behind its cut (the skin's inside) or on its cut (round the
 * flight deck, where the inside is cut to the curve too). Every block placed by hand, or built before the lining, is
 * plated all round.
 * <p>
 * How a lined face looks follows from the way it faces: the sidewall, the ceiling, the floor (where the skin is the
 * hold's floor, or a ledge along the edge of a deck). A wall's face also takes in what is round it in the world, as
 * its model is built for drawing ({@link #context}): the dado where a floor runs along its foot, and the deep reveal
 * round a cabin window beside it, down its sides, over it and under it; inside a window's opening the lining turns in
 * to the window.
 */
public final class Lining {
    /** Whether each face of a whole panel is lined, by {@link Direction#get3DDataValue()}. */
    private static final BooleanProperty[] SIDES = new BooleanProperty[Direction.values().length];
    /** How many bits of a context each of the six sides takes: its {@link Look}, or 0 for the one its facing gives. */
    private static final int CONTEXT_BITS = 4;
    /** Which faces of each shape are lined, as bits by the face's place in {@link ShellGeometry#faces}, for each side lined. */
    private static final int[][] LINED = new int[LinedShellBlock.Side.values().length][ShellGeometry.COUNT];
    /** A cut this nearly level, facing up, is a floor; facing down this much, a ceiling. */
    private static final float FLOOR_CUT = 0.95F;
    private static final float CEILING_CUT = -0.5F;
    /** How square a side must be to a cut to count as behind it or in front of it, rather than along it. */
    private static final double ALONG = 1e-3;

    static {
        for (Direction side : Direction.values()) {
            SIDES[side.get3DDataValue()] = BooleanProperty.create("lined_" + side.getSerializedName());
        }
        for (int shape = 0; shape < ShellGeometry.COUNT; shape++) {
            double[] out = ShellGeometry.outward(shape);
            List<ShellGeometry.Face> faces = ShellGeometry.faces(shape);
            for (int i = 0; i < faces.size(); i++) {
                Direction side = faces.get(i).side();
                double way = side == null ? 1 : side.getStepX() * out[0] + side.getStepY() * out[1] + side.getStepZ() * out[2];
                // A side along the cut runs with the skin's surface: where the hull bulges out past it, or the skin
                // ends beside a window, it is seen from outside as often as from in, and stays plated behind an outer
                // cut. Along an inner cut it is lined only where the cut ends, open to the cabin (CUT_ALONG).
                if (way < -ALONG) {
                    LINED[LinedShellBlock.Side.BACK.ordinal()][shape] |= 1 << i;
                }
                if (way > ALONG) {
                    LINED[LinedShellBlock.Side.CUT.ordinal()][shape] |= 1 << i;
                }
                if (way >= -ALONG) {
                    LINED[LinedShellBlock.Side.CUT_ALONG.ordinal()][shape] |= 1 << i;
                }
            }
        }
    }

    private Lining() {
    }

    /**
     * How a face looks: the hull's plating, or one of the lining's panels. A wall's own face is the sidewall unless
     * what is round it makes it the dado, part of a window's reveal (down its right or left side as the face is seen,
     * down both sides of a pillar between two windows, the head over a window or the sill under it, on the dado where
     * the dado runs under it) or the inside of a window's opening.
     */
    public enum Look {
        SKIN, SIDEWALL, CEILING, FLOOR, DADO, REVEAL, REVEAL_LEFT, REVEAL_PAIR, REVEAL_HEAD, REVEAL_SILL, DADO_SILL
    }

    private static final Look[] LOOKS = Look.values();

    /** The property saying whether the face on this side of a whole panel is lined. */
    public static BooleanProperty side(Direction side) {
        return SIDES[side.get3DDataValue()];
    }

    /** Adds a property for each face to a block that keeps them (a whole panel). */
    public static void define(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDES);
    }

    /** The state with every face plated: the state a block keeping its faces starts in, placed by hand or built before. */
    public static BlockState plated(BlockState state) {
        for (BooleanProperty side : SIDES) {
            state = state.setValue(side, false);
        }
        return state;
    }

    /** Whether the block keeps which of its six faces are lined. */
    public static boolean keepsSides(BlockState state) {
        return state.hasProperty(SIDES[0]);
    }

    /** A block keeping its faces turned: each face's lining goes round with it. */
    public static BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            turned = turned.setValue(side(rotation.rotate(side)), state.getValue(side(side)));
        }
        return turned;
    }

    /** A block keeping its faces mirrored. */
    public static BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            mirrored = mirrored.setValue(side(mirror.mirror(side)), state.getValue(side(side)));
        }
        return mirrored;
    }

    /**
     * Which faces of a block's shape are lined, as bits by the face's place in {@link ShellGeometry#faces}: a whole
     * panel's as it keeps them; a lined shell's behind its cut (its sides that face away from the cut) or on it (the
     * cut and the sides facing its way, and those along it where it ends); the rocket's lined cover's behind its cut;
     * none for anything else.
     */
    public static int faces(BlockState state) {
        if (state.getBlock() instanceof LinedShellBlock) {
            return faces(ShellBlock.shape(state), state.getValue(LinedShellBlock.LINED));
        }
        if (state.getBlock() instanceof RocketLinedCoverBlock) {
            return faces(ShellBlock.shape(state), LinedShellBlock.Side.BACK);
        }
        if (!keepsSides(state)) {
            return 0;
        }
        int lined = 0;
        List<ShellGeometry.Face> faces = ShellGeometry.faces(ShellGeometry.WHOLE);
        for (int i = 0; i < faces.size(); i++) {
            if (state.getValue(side(faces.get(i).side()))) {
                lined |= 1 << i;
            }
        }
        return lined;
    }

    /** Which faces of a shape a lined shell lines, lined on this side of its cut. */
    public static int faces(int shape, LinedShellBlock.Side side) {
        return LINED[side.ordinal()][shape];
    }

    /**
     * What is round a block that the look of its lined walls depends on, for its model: for each of its six sides, the
     * {@link Look} its face there takes from its surroundings, or none to look as its facing gives. Read from the world
     * as the block's model is built for drawing, so it turns and flies with the airliner and follows a window put in
     * or taken out beside it.
     */
    public static int context(BlockGetter level, BlockPos pos, BlockState state) {
        int lined = faces(state);
        if (lined == 0) {
            return 0;
        }
        int context = 0;
        List<ShellGeometry.Face> faces = ShellGeometry.faces(ShellBlock.shape(state));
        for (int i = 0; i < faces.size(); i++) {
            Direction side = faces.get(i).side();
            if ((lined & 1 << i) != 0 && side != null) {
                Look look = around(level, pos, side);
                if (look != null) {
                    context |= look.ordinal() << CONTEXT_BITS * side.get3DDataValue();
                }
            }
        }
        return context;
    }

    /** The look of a face of a block, lined or not, in the context {@link #context} gave. */
    public static Look look(ShellGeometry.Face face, boolean lined, int context) {
        if (!lined) {
            return Look.SKIN;
        }
        Direction side = face.side();
        if (side == null) {
            return face.ny() >= FLOOR_CUT ? Look.FLOOR : face.ny() <= CEILING_CUT ? Look.CEILING : Look.SIDEWALL;
        }
        int around = context >> CONTEXT_BITS * side.get3DDataValue() & (1 << CONTEXT_BITS) - 1;
        if (around != 0) {
            return LOOKS[around];
        }
        return side == Direction.UP ? Look.FLOOR : side == Direction.DOWN ? Look.CEILING : Look.SIDEWALL;
    }

    /**
     * The look a lined face on this side of a block takes from what is round it, or null for the one its facing gives:
     * the inside of a window's opening, where the face looks onto the window's pane; for a wall, the reveal round a
     * window beside it in the wall (right and left as the face is seen from the cabin), over it or under it; and the
     * dado where a floor runs along the wall's foot: a solid block under the space in front of it, not the hull's own
     * skin, which steps in where the hold's walls curve down to its floor.
     */
    private static Look around(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos front = pos.relative(side);
        if (CabinWindowBlock.isPane(level.getBlockState(front))) {
            return Look.SIDEWALL;
        }
        if (side.getAxis().isVertical()) {
            return null;
        }
        Direction right = side.getCounterClockWise();
        boolean onRight = CabinWindowBlock.isPane(level.getBlockState(pos.relative(right)));
        boolean onLeft = CabinWindowBlock.isPane(level.getBlockState(pos.relative(right.getOpposite())));
        if (onRight || onLeft) {
            return onRight && onLeft ? Look.REVEAL_PAIR : onRight ? Look.REVEAL : Look.REVEAL_LEFT;
        }
        if (CabinWindowBlock.isPane(level.getBlockState(pos.below()))) {
            return Look.REVEAL_HEAD;
        }
        BlockPos floor = front.below();
        BlockState under = level.getBlockState(floor);
        boolean dado = under.isSolidRender(level, floor) && !keepsSides(under) && !(under.getBlock() instanceof ShellBlock);
        if (CabinWindowBlock.isPane(level.getBlockState(pos.above()))) {
            return dado ? Look.DADO_SILL : Look.REVEAL_SILL;
        }
        return dado ? Look.DADO : null;
    }
}
