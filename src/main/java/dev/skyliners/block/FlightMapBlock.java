package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The flight map, {@link #COLUMNS} by {@link #ROWS} blocks on a wall (see {@link TiledPanelBlock}): the passenger
 * screens' route map as big as the announcement panel, on the flight deck's back wall beside the door across from it,
 * with how high, how fast and how far the airliner has flown under it. Only ever on a wall.
 */
public class FlightMapBlock extends TiledPanelBlock {
    private static final MapCodec<FlightMapBlock> CODEC = simpleCodec(FlightMapBlock::new);

    public FlightMapBlock(Properties properties) {
        super(properties, false);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new FlightMapBlockEntity(pos, state) : null;
    }
}
