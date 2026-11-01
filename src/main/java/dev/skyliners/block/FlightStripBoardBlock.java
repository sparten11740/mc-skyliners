package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The control tower's flight strip board: a wide screen (see {@link WideScreenBlock}) listing every transponder within
 * the radar's range as a controller's flight strip, as a real tower keeps one for each aircraft it works: its colour
 * and picture, its callsign, how high and far off it is, which way it goes and what it is doing (parked, taxiing,
 * climbing, cruising, descending or inbound), paging through them when they are more than it holds, drawn by
 * {@code FlightStripBoardRenderer}. In the tower it hangs tilted from the cab's ceiling over the front consoles.
 */
public class FlightStripBoardBlock extends WideScreenBlock {
    private static final MapCodec<FlightStripBoardBlock> CODEC = simpleCodec(FlightStripBoardBlock::new);

    public FlightStripBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected BlockEntity newScreen(BlockPos pos, BlockState state) {
        return new FlightStripBoardBlockEntity(pos, state);
    }
}
