package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A searchlight's handle in a zeppelin's control car: a brass column with a cross grip, worked by whoever takes it
 * (see {@link SearchlightHandleBlockEntity}). Used, it is taken, its searchlight under the car switched on and swung
 * wherever its operator looks; used again it is let go, the light left shining where it points; used sneaking, the
 * light is switched off. FACING is the way its operator faces as they stand at it.
 */
public class SearchlightHandleBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<SearchlightHandleBlock> CODEC = simpleCodec(SearchlightHandleBlock::new);
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 15, 11);

    public SearchlightHandleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Put down by hand, it faces whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Used, on the server: taken, let go, or with sneaking its light switched off. Nothing with the server's switch
     * off.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!SkylinersConfig.SEARCHLIGHTS.get()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer worker && level.getBlockEntity(pos) instanceof SearchlightHandleBlockEntity handle) {
            handle.use(worker);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SearchlightHandleBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == SkylinersRegistry.SEARCHLIGHT_HANDLE_ENTITY.get()
            ? (tickLevel, pos, tickState, handle) -> ((SearchlightHandleBlockEntity) handle).serverTick((ServerLevel) tickLevel)
            : null;
    }
}
