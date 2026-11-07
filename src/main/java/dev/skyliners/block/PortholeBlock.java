package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The porthole: a small round window a block across, the rocket's oval window made small (see {@link
 * OvalWindowPieceBlock.Size#PORTHOLE}), its glass domed a little out of its steel collar and its steel-lined recess
 * running back through the block behind a steel lip. It is always as one set by hand (see {@link StandingWindowBlock}):
 * standing in a wall, looking out toward whoever sets it, or laid by the same item in a floor or a roof (see {@link
 * FlatPortholeBlock}), it fills its block, flush on both sides, and round its glass, outside and in, it looks like the
 * wall beside it. It dims as the rocket's windows do, and broken it gives itself back. The freighter has them down her
 * long passages (see {@code airframe.FreighterPortholes}).
 */
public class PortholeBlock extends StandingWindowBlock {
    private static final MapCodec<PortholeBlock> CODEC = simpleCodec(PortholeBlock::new);

    public PortholeBlock(Properties properties) {
        super(properties, Size.PORTHOLE);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DIM, 0));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DIM);
    }

    /** A porthole is a block wide, so it keeps no column. */
    @Nullable
    @Override
    protected IntegerProperty columnProperty() {
        return null;
    }

    /** A porthole is a block tall, so it keeps no row. */
    @Nullable
    @Override
    protected IntegerProperty rowProperty() {
        return null;
    }

    @Override
    protected FlatWindowBlock lyingFlat() {
        return SkylinersRegistry.FLAT_PORTHOLE.get();
    }

    @Override
    protected BlockState handSet(BlockState piece) {
        return piece;
    }

    @Override
    public boolean setByHand(BlockState piece) {
        return true;
    }
}
