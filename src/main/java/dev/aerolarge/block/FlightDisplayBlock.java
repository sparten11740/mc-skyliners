package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A primary flight display in the instrument panel, in front of each pilot: attitude, airspeed, altitude, vertical
 * speed and heading of the assembled airliner, drawn live by its renderer. FACING is the way the screen faces, toward
 * the pilot; the nose is the other way.
 */
public class FlightDisplayBlock extends InstrumentPanelBlock implements EntityBlock {
    private static final MapCodec<FlightDisplayBlock> CODEC = simpleCodec(FlightDisplayBlock::new);

    public FlightDisplayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlightDisplayBlockEntity(pos, state);
    }
}
