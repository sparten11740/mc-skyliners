package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pop-up sprinkler in a raised bed: a brass head on a copper riser in a little box of spruce boards, which in the
 * greenhouse stands in water, so the bed round it stays moist, the water held in by the box (its sides and its bottom
 * let nothing through, so it never runs out over the floor, and broken, it takes its water with it). Turned on from an
 * irrigation valve (see {@link IrrigationValveBlock}), its head pops up and sweeps a jet round over the beds, and what
 * grows in them comes on (see {@link SprinklerBlockEntity}). SPRAYING is whether it is spraying now.
 */
public class SprinklerBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    private static final MapCodec<SprinklerBlock> CODEC = simpleCodec(SprinklerBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty SPRAYING = BooleanProperty.create("spraying");
    /**
     * What of it hides what is beside it, and the water in it from the sides: its box's four boards, a sixteenth thick
     * and a block tall, open on top.
     */
    private static final VoxelShape BOX = Shapes.join(Shapes.block(), Block.box(1, 0, 1, 15, 16, 15), BooleanOp.ONLY_FIRST);

    public SprinklerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATERLOGGED, false).setValue(SPRAYING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, SPRAYING);
    }

    /** Put down into water, it stands in it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
    }

    /** Clicked and bumped into as a whole block: what is solid of it holds its water in on every side. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return BOX;
    }

    /** Broken, it takes its water with it: none is left to run out over the floor where it stood. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, Fluids.EMPTY.defaultFluidState());
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState beside, LevelAccessor level, BlockPos pos, BlockPos besidePos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, side, beside, level, pos, besidePos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SprinklerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AerolargeRegistry.SPRINKLER_ENTITY.get()) {
            return null;
        }
        return level.isClientSide
            ? (tickLevel, pos, tickState, sprinkler) -> ((SprinklerBlockEntity) sprinkler).clientTick(tickLevel, tickState)
            : (tickLevel, pos, tickState, sprinkler) -> ((SprinklerBlockEntity) sprinkler).serverTick((ServerLevel) tickLevel, tickState);
    }
}
