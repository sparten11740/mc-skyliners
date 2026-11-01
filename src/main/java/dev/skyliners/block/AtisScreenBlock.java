package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The tower's weather screen, its ATIS (automatic terminal information service) as a real tower's is: on the same
 * monitor and desk stand as the radar screen, the airfield's current report, under its letter ("INFORMATION B", see
 * {@link AtisScreenBlockEntity}): the time, the sky with a little picture of it, the wind (see
 * {@link dev.skyliners.airport.Wind}) and how far one sees, drawn by {@code AtisScreenRenderer}. FACING is the way its
 * screen looks, toward whoever placed it.
 */
public class AtisScreenBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<AtisScreenBlock> CODEC = simpleCodec(AtisScreenBlock::new);

    public AtisScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
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

    /** The radar screen's monitor and stand. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return TowerRadarBlock.SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AtisScreenBlockEntity(pos, state);
    }

    /** On the server only: it watches the sky for a new report. */
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != SkylinersRegistry.ATIS_SCREEN_ENTITY.get()) {
            return null;
        }
        BlockEntityTicker<AtisScreenBlockEntity> ticker = AtisScreenBlockEntity::serverTick;
        return (BlockEntityTicker<T>) ticker;
    }
}
