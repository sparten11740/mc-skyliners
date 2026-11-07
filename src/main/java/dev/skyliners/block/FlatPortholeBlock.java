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
 * The porthole lying flat in a floor or a roof, as the Porthole sets it there by hand (see {@link FlatWindowBlock}): a
 * round skylight or a round window in a floor, a block across; it gives back a Porthole.
 */
public class FlatPortholeBlock extends FlatWindowBlock {
    private static final MapCodec<FlatPortholeBlock> CODEC = simpleCodec(FlatPortholeBlock::new);

    public FlatPortholeBlock(Properties properties) {
        super(properties, Size.PORTHOLE);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ALONG, Direction.NORTH).setValue(DIM, 0));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ALONG, DIM);
    }

    /** A porthole is a block wide, so it keeps no column. */
    @Nullable
    @Override
    protected IntegerProperty columnProperty() {
        return null;
    }

    /** A porthole is a block long, so it keeps no row. */
    @Nullable
    @Override
    protected IntegerProperty rowProperty() {
        return null;
    }

    @Override
    protected StandingWindowBlock standing() {
        return SkylinersRegistry.PORTHOLE.get();
    }
}
