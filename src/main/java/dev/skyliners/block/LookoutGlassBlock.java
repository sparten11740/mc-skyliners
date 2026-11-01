package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersConfig;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Lookout's Glass in a zeppelin's control car: a long brass glass with a sunshade, on a swivel yoke atop a teak
 * post. Used, the player looks through it, the land far below brought near (see the client's {@code LookoutView}), and
 * marks a spot on it to fly to, which drops a flag with their face on the autopilot's map (see {@link
 * LookoutGlassBlockEntity}). FACING is the way the glass points.
 */
public class LookoutGlassBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<LookoutGlassBlock> CODEC = simpleCodec(LookoutGlassBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 23, 14);

    public LookoutGlassBlock(Properties properties) {
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

    /** Put down by hand, it points the way whoever puts it down looks. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Drawn by its block entity, so whoever looks through it does not see it (see the client's LookoutGlassRenderer).
     */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /**
     * Used, the player looks through it, on their own client; nothing with the server's {@code lookout} switched off.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!SkylinersConfig.LOOKOUT.get()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide && level.getBlockEntity(pos) instanceof LookoutGlassBlockEntity glass) {
            LookoutGlassBlockEntity.clientUse.accept(glass);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LookoutGlassBlockEntity(pos, state);
    }
}
