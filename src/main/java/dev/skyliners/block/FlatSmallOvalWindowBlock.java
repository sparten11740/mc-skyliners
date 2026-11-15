package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The oval window lying flat in a floor or a roof at one of its smaller sizes, where the hole has no room for the
 * rocket's (see {@link FlatWindowBlock}), each state a piece one of its windows really has, numbered as the window
 * standing in a wall numbers them (see {@link SmallOvalWindowBlock#PIECES}). The Oval Window's item lays it, and it
 * gives that back.
 */
public class FlatSmallOvalWindowBlock extends FlatWindowBlock {
    private static final MapCodec<FlatSmallOvalWindowBlock> CODEC = simpleCodec(FlatSmallOvalWindowBlock::new);

    /** Its size is its piece's (see {@link #size}): the size it is made with only stands in for that. */
    public FlatSmallOvalWindowBlock(Properties properties) {
        super(properties, SmallOvalWindowBlock.PIECES.getFirst().span().size());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ALONG, Direction.NORTH).setValue(DIM, 0)
            .setValue(SmallOvalWindowBlock.PIECE, 0));
    }

    /** This window at each of its sizes, the largest first, looking out a way with its top along another. */
    Stream<BlockState> spans(Direction facing, Direction along) {
        return SmallOvalWindowBlock.atEachSpan(defaultBlockState().setValue(FACING, facing).setValue(ALONG, along));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ALONG, DIM, SmallOvalWindowBlock.PIECE);
    }

    @Override
    public Size size(BlockState piece) {
        return SmallOvalWindowBlock.piece(piece).span().size();
    }

    @Override
    public int column(BlockState piece) {
        return SmallOvalWindowBlock.piece(piece).column();
    }

    @Override
    public int row(BlockState piece) {
        return SmallOvalWindowBlock.piece(piece).row();
    }

    @Override
    public BlockState at(BlockState piece, int column, int row) {
        return SmallOvalWindowBlock.pieceAt(piece, column, row);
    }

    /** Its column and row are its piece's, so it keeps no property for either. */
    @Nullable
    @Override
    protected IntegerProperty columnProperty() {
        return null;
    }

    @Nullable
    @Override
    protected IntegerProperty rowProperty() {
        return null;
    }

    /** The Oval Window, whose item lays it and which it gives back. */
    @Override
    protected StandingWindowBlock standing() {
        return SkylinersRegistry.OVAL_WINDOW.get();
    }
}
