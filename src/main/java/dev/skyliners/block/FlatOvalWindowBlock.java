package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The oval window lying flat in a floor or a roof, as the Oval Window sets it there by hand (see {@link
 * FlatWindowBlock}), three blocks across and four along, its pieces by the same column and row as the rocket's window
 * standing in her skin ({@link OvalWindowBlock}), or smaller where it has no room for that, as a block of its own for
 * those (see {@link FlatSmallOvalWindowBlock}); it gives back an Oval Window.
 */
public class FlatOvalWindowBlock extends FlatWindowBlock {
    private static final MapCodec<FlatOvalWindowBlock> CODEC = simpleCodec(FlatOvalWindowBlock::new);

    public FlatOvalWindowBlock(Properties properties) {
        super(properties, Size.ROCKET);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ALONG, Direction.NORTH).setValue(OvalWindowBlock.COLUMN, 0)
            .setValue(OvalWindowBlock.ROW, 0).setValue(DIM, 0));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ALONG, OvalWindowBlock.COLUMN, OvalWindowBlock.ROW, DIM);
    }

    /** Set by hand, at her size first and then at each smaller one (see {@link FlatSmallOvalWindowBlock}). */
    @Override
    protected List<BlockState> sizes(BlockState piece) {
        return Stream.concat(Stream.of(piece), SkylinersRegistry.FLAT_SMALL_OVAL_WINDOW.get().spans(piece.getValue(FACING), piece.getValue(ALONG)))
            .toList();
    }

    @Override
    protected IntegerProperty columnProperty() {
        return OvalWindowBlock.COLUMN;
    }

    @Override
    protected IntegerProperty rowProperty() {
        return OvalWindowBlock.ROW;
    }

    @Override
    protected StandingWindowBlock standing() {
        return SkylinersRegistry.OVAL_WINDOW.get();
    }
}
