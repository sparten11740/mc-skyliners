package dev.skyliners.spookie;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Spookie's hammock in the freighter's crew quarters (see {@link SpookieHammockBlockEntity}, which keeps the
 * Spookie): a big striped canvas sling slung between two dark posts, three blocks long, its block the middle of it and
 * the posts standing a block out either side, as its model draws them. FACING is the way its long side faces, the side
 * the Spookie climbs in from; its length runs across that. Only the sling in its own block is solid. Set down by hand
 * it keeps a Spookie of its own, which stands beside it on the side it faces, having no rounds to walk.
 */
public class SpookieHammockBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<SpookieHammockBlock> CODEC = simpleCodec(SpookieHammockBlock::new);
    /** The sling at its lowest, in its own block, by the way it faces: its length across that way. */
    private static final Map<Direction, VoxelShape> SLING = new EnumMap<>(Direction.class);

    static {
        VoxelShape across = Block.box(0, 6, 2, 16, 10, 14);
        VoxelShape along = Block.box(2, 6, 0, 14, 10, 16);
        SLING.put(Direction.NORTH, across);
        SLING.put(Direction.SOUTH, across);
        SLING.put(Direction.EAST, along);
        SLING.put(Direction.WEST, along);
    }

    public SpookieHammockBlock(Properties properties) {
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

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Set down by hand, it keeps a Spookie standing beside it (see {@link SpookieHammockBlockEntity#keepBeside}). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpookieHammockBlockEntity keeper) {
            keeper.keepBeside(state.getValue(FACING));
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SLING.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpookieHammockBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != SkylinersRegistry.SPOOKIE_HAMMOCK_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, keeper) -> SpookieHammockBlockEntity.tick((ServerLevel) world, (SpookieHammockBlockEntity) keeper);
    }
}
