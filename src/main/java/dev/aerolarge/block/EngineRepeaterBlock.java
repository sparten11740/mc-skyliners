package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An engine repeater in a zeppelin's engine car: an engine telegraph's brass pedestal and dial, as in the control car,
 * whose handle swings to each new order for its engine with the double ring of its bell (see
 * {@link EngineRepeaterBlockEntity}), and a lamp on its head, red while the order waits to be answered, green once it
 * is. Using it answers the order, and the engine does as it says; left unanswered, it rings on now and then until the
 * engine crew answer it. ORDER is the order on its dial, ANSWERED whether it is answered; FACING the way whoever
 * answers it looks.
 */
public class EngineRepeaterBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<EngineRepeaterBlock> CODEC = simpleCodec(EngineRepeaterBlock::new);
    public static final EnumProperty<TelegraphBlock.Order> ORDER = TelegraphBlock.ORDER;
    public static final BooleanProperty ANSWERED = BooleanProperty.create("answered");
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    public EngineRepeaterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ORDER, TelegraphBlock.Order.STOP).setValue(ANSWERED, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ORDER, ANSWERED);
    }

    /** Put down by hand, its dial faces whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Answers the order on its dial, if it waits to be. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(ANSWERED)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            answer(level, pos, state);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The order answered: the lamp turns green, with the clunk of the handle and a single ring back. */
    static void answer(Level level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(ANSWERED, true), Block.UPDATE_ALL);
        level.playSound(null, pos, AerolargeRegistry.TELEGRAPH_ANSWER.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EngineRepeaterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == AerolargeRegistry.ENGINE_REPEATER_ENTITY.get()
            ? (tickLevel, pos, tickState, repeater) -> ((EngineRepeaterBlockEntity) repeater).serverTick((ServerLevel) tickLevel)
            : null;
    }
}
