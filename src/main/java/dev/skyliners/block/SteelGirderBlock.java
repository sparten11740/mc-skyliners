package dev.skyliners.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rocket's steel girder, and anyone's steel frame: a gunmetal I-beam that stands up as a column or lies along
 * whichever way it is put down, up the corners of her lift shaft and under her rooms' ceilings. It joins what is
 * beside it each way as a real steel frame does (see {@link Joint}). Girders end to end run on as one, so a stack of
 * them is one column with its flanges running up. Where a girder's end meets another's side, or one beam lies across
 * another, they are bolted together, and the girder met is stiffened there: a beam into a column, a corner, a tee and
 * a cross of beams each make a clean joint. A column on the ground or on a block stands on a concrete footing under a
 * bolted base plate, so it reads as a pillar, and an end against a wall or a ceiling is bolted to it by a fixing plate.
 * Its joints are set as it is put down and kept up as what is beside it changes; a girder saved before it had any
 * starts unjoined and joins up as its neighbours change.
 */
public class SteelGirderBlock extends RotatedPillarBlock {
    private static final MapCodec<SteelGirderBlock> CODEC = simpleCodec(SteelGirderBlock::new);
    public static final EnumProperty<Joint> NORTH = EnumProperty.create("north", Joint.class);
    public static final EnumProperty<Joint> EAST = EnumProperty.create("east", Joint.class);
    public static final EnumProperty<Joint> SOUTH = EnumProperty.create("south", Joint.class);
    public static final EnumProperty<Joint> WEST = EnumProperty.create("west", Joint.class);
    public static final EnumProperty<Joint> UP = EnumProperty.create("up", Joint.class);
    public static final EnumProperty<Joint> DOWN = EnumProperty.create("down", Joint.class);
    /** How it joins each way, by the way. */
    public static final Map<Direction, EnumProperty<Joint>> JOINTS = Maps.immutableEnumMap(Map.of(
        Direction.NORTH, NORTH,
        Direction.EAST, EAST,
        Direction.SOUTH, SOUTH,
        Direction.WEST, WEST,
        Direction.UP, UP,
        Direction.DOWN, DOWN));
    /** How far in from its block's sides the I-beam's flanges stop, in pixels, as its model draws them. */
    private static final double INSET = 1;
    /** How high a column's footing stands, its concrete and base plate together, in pixels. */
    private static final double FOOTING = 4.5;

    private final Map<BlockState, VoxelShape> shapes;

    public SteelGirderBlock(Properties properties) {
        super(properties);
        BlockState unjoined = defaultBlockState();
        for (EnumProperty<Joint> joint : JOINTS.values()) {
            unjoined = unjoined.setValue(joint, Joint.NONE);
        }
        registerDefaultState(unjoined);
        shapes = getShapeForEachState(SteelGirderBlock::shapeOf);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /**
     * How a girder meets what is beside it one way. At an end, it runs on into another girder or meets one's side
     * ({@code GIRDER}), is bolted to a block whose face there is firm ({@code FIXED}: a column's footing on the ground,
     * a fixing plate under a ceiling or against a wall), or stands free ({@code NONE}). At a side, it is met by another
     * girder's end or crossed by a beam over or under it ({@code GIRDER}), and stiffened there; a girder lying
     * alongside it is not joined.
     */
    public enum Joint implements StringRepresentable {
        NONE("none"),
        GIRDER("girder"),
        FIXED("fixed");

        private final String name;

        Joint(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Put down, it joins what is already beside it each way. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = super.getStateForPlacement(context);
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return placed == null ? null : joined(placed, way -> level.getBlockState(pos.relative(way)), level, pos);
    }

    /** What is beside it changing, it joins that again: a girder put down or taken away beside it, a wall built. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return withJoint(state, direction, neighbour, level, neighbourPos);
    }

    /**
     * A girder as a blueprint lays it, joined to what the blueprint puts beside it each way, as {@code beside} gives
     * it: with no level standing yet, a block's face counts as firm by its shape alone.
     */
    public static BlockState joined(BlockState state, Function<Direction, BlockState> beside) {
        return joined(state, beside, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** A girder at a place in a level joined to what is beside it each way, as {@code beside} gives it. */
    private static BlockState joined(BlockState state, Function<Direction, BlockState> beside, BlockGetter level,
                                     BlockPos pos) {
        BlockState joined = state;
        for (Direction way : Direction.values()) {
            joined = withJoint(joined, way, beside.apply(way), level, pos.relative(way));
        }
        return joined;
    }

    private static BlockState withJoint(BlockState state, Direction way, BlockState beside, BlockGetter level,
                                        BlockPos besidePos) {
        boolean firm = beside.isFaceSturdy(level, besidePos, way.getOpposite());
        return state.setValue(JOINTS.get(way), joint(state.getValue(AXIS), way, beside, firm));
    }

    /** How a girder lying along an axis meets a block beside it one way, whose face toward it is firm or not. */
    private static Joint joint(Direction.Axis axis, Direction way, BlockState beside, boolean firm) {
        boolean along = way.getAxis() == axis;
        if (beside.getBlock() instanceof SteelGirderBlock) {
            Direction.Axis besideAxis = beside.getValue(AXIS);
            boolean meets = along || besideAxis == way.getAxis() || crossing(axis, way, besideAxis);
            return meets ? Joint.GIRDER : Joint.NONE;
        }
        return along && firm ? Joint.FIXED : Joint.NONE;
    }

    /** Whether two beams one over the other cross, flange on flange, rather than lying one along the other. */
    private static boolean crossing(Direction.Axis axis, Direction way, Direction.Axis besideAxis) {
        return way.getAxis().isVertical() && axis.isHorizontal() && besideAxis.isHorizontal() && besideAxis != axis;
    }

    /** Whether a girder stands on its footing: a column whose foot is on the ground or on a block. */
    private static boolean footed(BlockState state) {
        return state.getValue(AXIS) == Direction.Axis.Y && state.getValue(DOWN) == Joint.FIXED;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    /**
     * Every face of its block holds what is fixed to it, as it did when it was a whole block: a ladder, a button, a
     * torch or a girder decoration goes on a girder as on a wall, though its shape is a pixel slimmer.
     */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    /**
     * Its shape: the I-beam's outline along its length, out to its block's side where another girder meets it, and its
     * footing across its block under a column that stands on one.
     */
    private static VoxelShape shapeOf(BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        VoxelShape shape = outline(axis, null);
        for (Direction way : Direction.values()) {
            if (way.getAxis() != axis && state.getValue(JOINTS.get(way)) == Joint.GIRDER) {
                shape = Shapes.or(shape, outline(axis, way));
            }
        }
        return footed(state) ? Shapes.or(shape, Block.box(0, 0, 0, 16, FOOTING, 16)) : shape;
    }

    /** The I-beam's outline along an axis, or the strip of it between one of its sides and its block's side. */
    private static VoxelShape outline(Direction.Axis axis, @Nullable Direction reaching) {
        double[] from = {INSET, INSET, INSET};
        double[] to = {16 - INSET, 16 - INSET, 16 - INSET};
        from[axis.ordinal()] = 0;
        to[axis.ordinal()] = 16;
        if (reaching != null) {
            int across = reaching.getAxis().ordinal();
            boolean positive = reaching.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            from[across] = positive ? 16 - INSET : 0;
            to[across] = positive ? 16 : INSET;
        }
        return Block.box(from[0], from[1], from[2], to[0], to[1], to[2]);
    }

    /**
     * An end is not drawn where it runs on into another girder, where it meets one's side, under that one's joint, or
     * where it is bolted to a block, under its plate. A girder saved before it had joints, until it joins up, still
     * hides its end against another running the same way, as it always did.
     */
    @Override
    protected boolean skipRendering(BlockState state, BlockState beside, Direction side) {
        Direction.Axis axis = state.getValue(AXIS);
        boolean end = side.getAxis() == axis;
        boolean endToEnd = beside.getBlock() instanceof SteelGirderBlock && beside.getValue(AXIS) == axis;
        boolean covered = end && (state.getValue(JOINTS.get(side)) != Joint.NONE || endToEnd);
        return covered || super.skipRendering(state, beside, side);
    }

    /** Its joints turn with it, as when the rocket is built facing another way. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = super.rotate(state, rotation);
        for (Direction way : Direction.Plane.HORIZONTAL) {
            turned = turned.setValue(JOINTS.get(rotation.rotate(way)), state.getValue(JOINTS.get(way)));
        }
        return turned;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = state;
        for (Direction way : Direction.Plane.HORIZONTAL) {
            turned = turned.setValue(JOINTS.get(mirror.mirror(way)), state.getValue(JOINTS.get(way)));
        }
        return turned;
    }
}
