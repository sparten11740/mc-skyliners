package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A glazed partition dividing the rocket's lab into its parts, as a lab's clean room is screened off: glass in a slim
 * steel frame through the middle of its block, a mullion at each side, a steel kick plate along its foot (ROW
 * {@code bottom}), a frosted band at eye height (ROW {@code middle}) and a head rail along its top (ROW {@code top}).
 * FACING is the way one of its faces looks; it is the same from both sides. Broken, it gives itself back, row and all.
 */
public class LabPartitionBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<LabPartitionBlock> CODEC = simpleCodec(LabPartitionBlock::new);
    public static final EnumProperty<Row> ROW = EnumProperty.create("row", Row.class);
    /** The panel through the middle of its block, drawn facing north, two pixels thick. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Block.box(0, 0, 7, 16, 16, 9));

    /** Which row of the partition a block is: its foot, between, or its top. */
    public enum Row implements StringRepresentable {
        BOTTOM, MIDDLE, TOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public LabPartitionBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROW, Row.MIDDLE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROW);
    }

    /** A row of the partition, its faces looking the way given and the opposite way. */
    public BlockState of(Row row, Direction facing) {
        return defaultBlockState().setValue(ROW, row).setValue(FACING, facing);
    }

    /** Put down by hand, it faces whoever puts it there. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Itself, with its row, the way one broken comes back; the middle row is left plain, so they stack. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (state.getValue(ROW) != defaultBlockState().getValue(ROW)) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(ROW, state.getValue(ROW)));
        }
        return stack;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}
