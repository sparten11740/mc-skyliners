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
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Goods on show against a wall, in two tiers, TIER: a cabinet standing on the floor, and lit shelves standing on a
 * cabinet (or on more shelves), the goods on them catching the light. Put down on one of its own kind it is the shelves,
 * anywhere else the cabinet; shelves with nothing of their kind under them come down. It is registered twice: the
 * lounge's back bar, its shelves of bottles, and the duty-free display, its showcase of perfumes and chocolates. FACING
 * is the way its front faces, its back to the wall.
 */
public class DisplayShelfBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<DisplayShelfBlock> CODEC = simpleCodec(DisplayShelfBlock::new);
    public static final EnumProperty<Tier> TIER = EnumProperty.create("tier", Tier.class);

    /** Which tier of the display it is. */
    public enum Tier implements StringRepresentable {
        CABINET,
        SHELVES;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The shelves facing north: their frame and back, against the south side of the block. */
    private static final Map<Direction, VoxelShape> SHELF_SHAPES = TurnedShapes.of(Block.box(0, 0, 7, 16, 16, 16));

    public DisplayShelfBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TIER, Tier.CABINET));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIER);
    }

    /** Its front toward whoever puts it down: shelves on one of its own kind, a cabinet anywhere else. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean onDisplay = context.getLevel().getBlockState(context.getClickedPos().below()).is(this);
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(TIER, onDisplay ? Tier.SHELVES : Tier.CABINET);
    }

    /** Shelves stand on one of their own kind. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(TIER) == Tier.CABINET || level.getBlockState(pos.below()).is(this);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TIER) == Tier.CABINET ? Shapes.block() : SHELF_SHAPES.get(state.getValue(FACING));
    }
}
