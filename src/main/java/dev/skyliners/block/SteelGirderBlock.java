package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rocket's steel girder: a gunmetal I-beam that stands up or lies along whichever way it is put down, up the
 * corners of her lift shaft and under her rooms' ceilings. Girders end to end along the way they run hide the ends
 * where they meet, so a column of them up the shaft is drawn as one girder, without a face at every joint.
 */
public class SteelGirderBlock extends RotatedPillarBlock {
    private static final MapCodec<SteelGirderBlock> CODEC = simpleCodec(SteelGirderBlock::new);

    public SteelGirderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends RotatedPillarBlock> codec() {
        return CODEC;
    }

    /** Its end against another girder running the same way, end to end, is not drawn. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState beside, Direction side) {
        Direction.Axis axis = state.getValue(AXIS);
        boolean endToEnd = beside.is(this) && beside.getValue(AXIS) == axis && side.getAxis() == axis;
        return endToEnd || super.skipRendering(state, beside, side);
    }
}
