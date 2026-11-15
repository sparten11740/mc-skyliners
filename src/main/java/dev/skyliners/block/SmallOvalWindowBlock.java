package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The oval window set into a wall by hand at one of its smaller sizes, where the opening has no room for the rocket's
 * (see {@link OvalWindowBlock.Span}): anything from three blocks wide and three tall down to one wide and two tall,
 * looking out away from whoever set it and looking like the wall round it, as the Oval Window does at her size (see
 * {@link StandingWindowBlock}). It is only ever set by hand, so it has no wall of hers to keep, and each of its states
 * is a piece one of its windows really has: PIECE says which, of every piece of every smaller size (see {@link
 * #PIECES}), so its sizes cost no more block states than they have pieces. The Oval Window's item sets it, and it
 * gives that back.
 */
public class SmallOvalWindowBlock extends StandingWindowBlock {
    private static final MapCodec<SmallOvalWindowBlock> CODEC = simpleCodec(SmallOvalWindowBlock::new);
    /**
     * Every piece of every smaller size, in the order its state numbers them: the sizes as they are tried, and in each
     * its pieces column by column from the left as seen from outside, and down each column from the top. The lying-flat
     * window numbers them alike (see {@link FlatSmallOvalWindowBlock}), and so do the models and loot written for both.
     */
    static final List<Piece> PIECES = pieces();
    /** Where each piece stands in {@link #PIECES}. */
    private static final Map<Piece, Integer> NUMBERS = numbers();
    /** Which piece of which smaller size a block is, by its place in {@link #PIECES}. */
    public static final IntegerProperty PIECE = IntegerProperty.create("piece", 0, PIECES.size() - 1);

    /** A piece of a smaller oval window: its size, its column from the left as seen from outside and its row from the top. */
    record Piece(OvalWindowBlock.Span span, int column, int row) {
    }

    /** Its size is its piece's (see {@link #size}): the size it is made with only stands in for that. */
    public SmallOvalWindowBlock(Properties properties) {
        super(properties, PIECES.getFirst().span().size());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DIM, 0).setValue(PIECE, 0));
    }

    private static List<Piece> pieces() {
        List<Piece> pieces = new ArrayList<>();
        Arrays.stream(OvalWindowBlock.Span.values()).skip(1).forEach(span -> addPieces(pieces, span));
        return List.copyOf(pieces);
    }

    private static void addPieces(List<Piece> pieces, OvalWindowBlock.Span span) {
        for (int column = 0; column < span.size().columns(); column++) {
            for (int row = 0; row < span.size().rows(); row++) {
                pieces.add(new Piece(span, column, row));
            }
        }
    }

    private static Map<Piece, Integer> numbers() {
        Map<Piece, Integer> numbers = new HashMap<>();
        for (int i = 0; i < PIECES.size(); i++) {
            numbers.put(PIECES.get(i), i);
        }
        return Map.copyOf(numbers);
    }

    /** The piece a state of a smaller oval window is, standing or lying flat. */
    static Piece piece(BlockState state) {
        return PIECES.get(state.getValue(PIECE));
    }

    /** The same window's piece at a column and a row, standing or lying flat. */
    static BlockState pieceAt(BlockState state, int column, int row) {
        return state.setValue(PIECE, NUMBERS.get(new Piece(piece(state).span(), column, row)));
    }

    /** A first piece of a window at each smaller size, the largest first, from a piece looking the way wanted. */
    static Stream<BlockState> atEachSpan(BlockState looking) {
        return Arrays.stream(OvalWindowBlock.Span.values()).skip(1).map(span -> looking.setValue(PIECE, NUMBERS.get(new Piece(span, 0, 0))));
    }

    /** This window at each of its sizes, the largest first, looking out a way: as the Oval Window tries them in turn. */
    Stream<BlockState> spans(Direction facing) {
        return atEachSpan(defaultBlockState().setValue(FACING, facing));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DIM, PIECE);
    }

    @Override
    public Size size(BlockState piece) {
        return piece(piece).span().size();
    }

    @Override
    public int column(BlockState piece) {
        return piece(piece).column();
    }

    @Override
    public int row(BlockState piece) {
        return piece(piece).row();
    }

    @Override
    public BlockState at(BlockState piece, int column, int row) {
        return pieceAt(piece, column, row);
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

    @Override
    protected FlatWindowBlock lyingFlat() {
        return SkylinersRegistry.FLAT_SMALL_OVAL_WINDOW.get();
    }

    @Override
    protected BlockState handSet(BlockState piece) {
        return piece;
    }

    @Override
    public boolean setByHand(BlockState piece) {
        return true;
    }

    /** Picked in creative mode, it gives the Oval Window, which sets it. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(SkylinersRegistry.OVAL_WINDOW_ITEM.get());
    }
}
