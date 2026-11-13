package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A shop's striped awning, in lime and white, on a slim steel frame, in two parts, PART. The canopy is the fabric
 * stretched flat over a steel rib along either side, filling the top of its block, so canopies put side by side and one
 * behind the other make one awning; along its front edge, where no canopy carries it on (VALANCE), a scalloped valance
 * hangs from a steel tube. A rod is a steel hanger rod to hang it by: put up against the underside of a block, or
 * under another rod, the awning is a rod. FACING is the way a canopy's front faces, its valance toward whoever put it
 * up.
 */
public class AwningBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<AwningBlock> CODEC = simpleCodec(AwningBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Whether the canopy's front edge is the awning's, with its valance: no canopy facing the same way in front of it. */
    public static final BooleanProperty VALANCE = BooleanProperty.create("valance");

    /** Which part of the awning it is. */
    public enum Part implements StringRepresentable {
        CANOPY,
        ROD;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The canopy facing north: the fabric and its ribs, and the valance down its front edge. */
    private static final VoxelShape CANOPY = Block.box(0, 14.5, 0, 16, 16, 16);
    private static final Map<Direction, VoxelShape> VALANCE_SHAPES = TurnedShapes.of(Shapes.or(CANOPY, Block.box(0, 9, 0, 16, 14.5, 1)));
    private static final VoxelShape ROD = Block.box(7.5, 0, 7.5, 8.5, 16, 8.5);

    public AwningBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.CANOPY).setValue(VALANCE, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, VALANCE);
    }

    /** Whether this is a rod, which the awning hangs by. */
    public static boolean isRod(BlockState state) {
        return state.getBlock() instanceof AwningBlock && state.getValue(PART) == Part.ROD;
    }

    /**
     * A rod against the underside of a block or under another rod; anywhere else a canopy with its front toward whoever
     * puts it up, its valance there unless a canopy carries it on in front; none set down on top of a block, where the
     * canopy, filling the top of its block, stood in the air.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (context.getClickedFace() == Direction.DOWN || isRod(context.getLevel().getBlockState(pos.above()))) {
            return defaultBlockState().setValue(PART, Part.ROD);
        }
        if (context.getClickedFace() == Direction.UP) {
            return null;
        }
        Direction front = context.getHorizontalDirection().getOpposite();
        BlockState canopy = defaultBlockState().setValue(FACING, front);
        return canopy.setValue(VALANCE, !carriesOn(canopy, context.getLevel().getBlockState(pos.relative(front))));
    }

    /** A canopy keeps its valance only while no canopy facing the same way carries the awning on in front of it. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(PART) == Part.CANOPY && direction == state.getValue(FACING)) {
            return state.setValue(VALANCE, !carriesOn(state, neighbour));
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Whether the neighbour is a canopy facing the same way as this one, carrying the awning on. */
    private static boolean carriesOn(BlockState canopy, BlockState neighbour) {
        return neighbour.getBlock() instanceof AwningBlock && neighbour.getValue(PART) == Part.CANOPY
            && neighbour.getValue(FACING) == canopy.getValue(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(PART) == Part.ROD) {
            return ROD;
        }
        return state.getValue(VALANCE) ? VALANCE_SHAPES.get(state.getValue(FACING)) : CANOPY;
    }
}
