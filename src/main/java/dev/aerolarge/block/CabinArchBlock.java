package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of a white archway in a cabin bulkhead: a round arch, a half circle springing from the top of the opening's
 * sides, over an opening three blocks wide (narrow) or five (wide), and the sides themselves, its jambs. It is smooth
 * white panelling round the opening, a band of it six pixels wide, with a moulding along the opening's edge: a bead
 * flush with the wall and a groove behind it. Beyond the band each piece is the bulkhead's own cream panelling, so the
 * arch reads as one moulded piece set into the wall; the wall's two faces look the same.
 *
 * <p>PART is which block of the archway it is (see {@link Part}), and FACING points along the wall from the archway's
 * middle out to the side the piece is on; the middle column's look the same either way. Its model draws the curve a
 * pixel at a time, and its outline and what bumps into it follow the curve the same way. It sits in the cabin,
 * between rooms already open to each other through the archway under it, so it keeps no light out, and the curved
 * faces inside its block are lit as the cabin round it is.
 *
 * <p>Put down by hand, a whole archway goes up at once, like the airliner's at its default scale (see
 * {@link #getStateForPlacement}); WHOLE marks its pieces, which stand and fall together, and taken down it gives back
 * the one Cabin Arch it went up from. The airliner's archways are built a piece at a time, and their pieces, like one
 * picked from them, stand alone and come down as themselves.
 */
public class CabinArchBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<CabinArchBlock> CODEC = simpleCodec(CabinArchBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /**
     * Whether the piece went up by hand with the rest of a whole archway, which stands and falls as one: false for the
     * airliner's archways, for a piece put down alone, and for every piece saved before archways went up whole.
     */
    public static final BooleanProperty WHOLE = BooleanProperty.create("whole");
    /** How far out from the opening's edge the white panelling reaches, in pixels, its moulding the first two. */
    public static final int BAND = 6;
    /**
     * How many rows tall the jambs of an archway put up by hand are: as the airliner's are at its default scale, a
     * head's height clear right across the opening.
     */
    private static final int JAMB_ROWS = 3;
    /** How many blocks either side of its middle the opening of an archway put up by hand reaches: narrow or wide. */
    private static final int NARROW = 1;
    private static final int WIDE = 2;
    private static final Map<Part, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(Part.class);

    static {
        for (Part part : Part.values()) {
            SHAPES.put(part, TurnedShapes.of(part.shapeFacingNorth()));
        }
    }

    /**
     * The blocks of the archways that the curve or its band passes through: the jambs either side of the opening, the
     * same for both sizes, and for each size the arch's blocks on one side of the middle and up the middle, named for
     * how many columns out from the middle they are and how many rows over the opening. The rest of the archway's
     * blocks are open (inside the curve) or plain bulkhead (beyond the band). The models are generated for these same
     * blocks, from the same curve (tools/generate_resources.mjs).
     */
    public enum Part implements StringRepresentable {
        JAMB(0, 0, 0),
        NARROW_1_1(1, 1, 1), NARROW_2_1(1, 2, 1), NARROW_0_2(1, 0, 2), NARROW_1_2(1, 1, 2), NARROW_2_2(1, 2, 2),
        WIDE_2_1(2, 2, 1), WIDE_3_1(2, 3, 1), WIDE_2_2(2, 2, 2), WIDE_3_2(2, 3, 2), WIDE_0_3(2, 0, 3), WIDE_1_3(2, 1, 3),
        WIDE_2_3(2, 2, 3);

        /** How many blocks either side of the middle the opening reaches, columns out from the middle and rows over it. */
        private final int span;
        private final int out;
        private final int over;

        Part(int span, int out, int over) {
            this.span = span;
            this.out = out;
            this.over = over;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The top of the middle column, the arch's crown: of an archway put up whole, the piece it comes down as. */
        boolean crown() {
            return this != JAMB && out == 0;
        }

        /** The piece the arch springs from, over the top of a jamb. */
        boolean springer() {
            return this != JAMB && out == span + 1 && over == 1;
        }

        /**
         * The outline drawn facing north (out to the north, the wall's faces east and west), a pixel at a time across
         * the wall's face: a pixel is in it where its middle is out of the opening, just as the model draws it, so the
         * outline steps round the curve with the arch and never over the open archway. The jamb is the whole block.
         */
        private VoxelShape shapeFacingNorth() {
            if (this == JAMB) {
                return Shapes.block();
            }
            VoxelShape shape = Shapes.empty();
            for (int across = 0; across < 16; across++) {
                for (int up = 0; up < 16; up++) {
                    if (fromMiddle(16 * out - 8 + across + 0.5, 16 * (over - 1) + up + 0.5) >= radius(span)) {
                        shape = Shapes.joinUnoptimized(shape, Block.box(0, up, 15 - across, 16, up + 1, 16 - across), BooleanOp.OR);
                    }
                }
            }
            return shape.optimize();
        }
    }

    /**
     * An archway put up whole: the block at the foot of the middle of its opening, the way along the wall that is its
     * right as seen by whoever put it up, and how many blocks either side of the middle its opening reaches. Its jambs
     * are {@link #JAMB_ROWS} tall.
     */
    private record Archway(BlockPos sill, Direction right, int span) {
        /** The block so many columns right of the middle (left, below 0) and rows over the tops of the jambs. */
        BlockPos at(int x, int over) {
            return sill.relative(right, x).above(JAMB_ROWS - 1 + over);
        }

        /**
         * Every block it puts up, by where it goes: its pieces, each facing out to its own side (the middle column's to
         * the right), and over a wide archway's jambs the partition's panels the airliner's bulkheads have there,
         * beyond the band. Nothing in its opening.
         */
        Map<BlockPos, BlockState> blocks(BlockState piece) {
            Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
            for (int x = -span - 1; x <= span + 1; x++) {
                column(blocks, piece, x);
            }
            return blocks;
        }

        /** Adds the blocks it puts up in one column, so many right of the middle, from the floor up. */
        private void column(Map<BlockPos, BlockState> blocks, BlockState piece, int x) {
            for (int over = 1 - JAMB_ROWS; over <= rows(span); over++) {
                BlockState block = blockAt(piece, x, over);
                if (block != null) {
                    blocks.put(at(x, over), block);
                }
            }
        }

        /** The block it puts up so many columns right of the middle and rows over the jambs; null where it is open. */
        @Nullable
        private BlockState blockAt(BlockState piece, int x, int over) {
            Part part = pieceAt(span, Math.abs(x), over);
            if (part != null) {
                return piece.setValue(PART, part).setValue(FACING, x < 0 ? right.getOpposite() : right).setValue(WHOLE, true);
            }
            boolean open = over <= 0 || openAt(span, Math.abs(x), over);
            return open ? null : AerolargeRegistry.CABIN_PARTITION.get().defaultBlockState();
        }
    }

    public CabinArchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST).setValue(PART, Part.NARROW_0_2).setValue(WHOLE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, WHOLE);
    }

    /** How far either side of the opening's middle its edge is, in pixels: the arch's radius. */
    public static int radius(int span) {
        return 16 * span + 8;
    }

    /** How many rows of blocks the arch and its band take over the opening. */
    public static int rows(int span) {
        return (radius(span) + BAND + 15) / 16;
    }

    /**
     * How far a point of the wall is from the edge's middle line, in pixels across the wall from the opening's middle
     * and up from the top of its sides: round the arch's middle over them, straight out to the side below them.
     */
    private static double fromMiddle(double u, double v) {
        return v >= 0 ? Math.hypot(u, v) : Math.abs(u);
    }

    /**
     * The piece of an archway reaching so many blocks either side of its middle at the block so many columns out from
     * its middle and rows over its opening (0 and below: beside it); null where it has none.
     */
    @Nullable
    public static Part pieceAt(int span, int out, int over) {
        if (over <= 0) {
            return out == span + 1 ? Part.JAMB : null;
        }
        for (Part part : Part.values()) {
            if (part.span == span && part.out == out && part.over == over) {
                return part;
            }
        }
        return null;
    }

    /** Whether an archway is open at that block over its opening: all of the block inside the curve. */
    public static boolean openAt(int span, int out, int over) {
        return over >= 1 && fromMiddle(16 * out + 7.5, 16 * over - 0.5) < radius(span);
    }

    /**
     * Put down by hand, a whole archway goes up across the way whoever puts it down faces: the foot of its left-hand
     * jamb where it is put, reaching to their right and up as a {@link WideScreenBlock} does, narrow, or wide when they
     * sneak. Only this jamb goes down here; the rest goes up round it (see {@link #setPlacedBy}), and none of it where
     * anything stands in the way of any of it. A piece picked from an archway (see {@link #getCloneItemStack}) goes
     * down alone, the right-hand side of an archway as its builder faces it (sneaking, the left-hand side), as the
     * piece it is.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction look = context.getHorizontalDirection();
        if (isPiece(context.getItemInHand())) {
            return defaultBlockState().setValue(FACING, context.isSecondaryUseActive() ? look.getCounterClockWise() : look.getClockWise());
        }
        Archway archway = putUp(context.getClickedPos(), look.getClockWise(), context.isSecondaryUseActive());
        Map<BlockPos, BlockState> blocks = archway.blocks(defaultBlockState());
        return roomFor(blocks, context) ? blocks.get(context.getClickedPos()) : null;
    }

    /** Whether the item puts down one piece, picked from an archway as the piece it is, rather than a whole archway. */
    private static boolean isPiece(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).isEmpty();
    }

    /** The archway put up whole with the foot of its left-hand jamb here, its right that way, narrow or wide. */
    private static Archway putUp(BlockPos foot, Direction right, boolean wide) {
        int span = wide ? WIDE : NARROW;
        return new Archway(foot.relative(right, span + 1), right, span);
    }

    /**
     * Whether every block of an archway can go up: inside the world, where nothing stands but what gives way to a block
     * put down (air, grass, snow), and where it would shut nobody in.
     */
    private static boolean roomFor(Map<BlockPos, BlockState> blocks, BlockPlaceContext context) {
        Level level = context.getLevel();
        CollisionContext nobody = context.getPlayer() == null ? CollisionContext.empty() : CollisionContext.of(context.getPlayer());
        for (Map.Entry<BlockPos, BlockState> block : blocks.entrySet()) {
            BlockPos at = block.getKey();
            boolean inWorld = !level.isOutsideBuildHeight(at) && level.getWorldBorder().isWithinBounds(at);
            if (!inWorld || !level.getBlockState(at).canBeReplaced(context) || !level.isUnobstructed(block.getValue(), at, nobody)) {
                return false;
            }
        }
        return true;
    }

    /** The rest of an archway put up whole goes up round the foot of the jamb put down. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !state.getValue(WHOLE) || state.getValue(PART) != Part.JAMB) {
            return;
        }
        boolean wide = placer instanceof Player player && player.isSecondaryUseActive();
        Archway archway = putUp(pos, state.getValue(FACING).getOpposite(), wide);
        for (Map.Entry<BlockPos, BlockState> block : archway.blocks(defaultBlockState()).entrySet()) {
            if (!block.getKey().equals(pos)) {
                level.setBlock(block.getKey(), block.getValue(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * The archway put up whole that a piece of one belongs to, found from the piece: every piece but a jamb says where
     * it is in its archway, and a jamb finds the arch over it up its column. Null for a jamb with no arch over it.
     */
    @Nullable
    private Archway archwayOf(BlockGetter level, BlockPos pos, BlockState state) {
        Part part = state.getValue(PART);
        Direction side = state.getValue(FACING);
        if (part != Part.JAMB) {
            return new Archway(pos.relative(side, -part.out).below(part.over + JAMB_ROWS - 1), side, part.span);
        }
        for (int up = 1; up <= JAMB_ROWS; up++) {
            BlockState above = level.getBlockState(pos.above(up));
            if (!carriesOn(above, side)) {
                return null;
            }
            Part springer = above.getValue(PART);
            if (springer.springer()) {
                return new Archway(pos.relative(side, -(springer.span + 1)).above(up - JAMB_ROWS), side, springer.span);
            }
        }
        return null;
    }

    /**
     * Whether the block over a jamb of an archway put up whole carries its column on up: another of its jambs, or the
     * piece the arch springs from, facing the same way.
     */
    private boolean carriesOn(BlockState above, Direction side) {
        if (!above.is(this) || !above.getValue(WHOLE) || above.getValue(FACING) != side) {
            return false;
        }
        return above.getValue(PART) == Part.JAMB || above.getValue(PART).springer();
    }

    /**
     * A piece of an archway put up whole goes when a piece of the same archway beside it does, so the archway is only
     * ever whole: taking any of it down takes all of it, and only its crown gives back the Cabin Arch it went up from.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WHOLE) && !standsWith(state, direction, neighbour, level, pos, neighbourPos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /**
     * Whether a piece of an archway put up whole still stands with its neighbour as it now is. A jamb stands under
     * whatever carries its column on (see {@link #carriesOn}); any piece stands beside the piece of its archway that
     * belongs there, where one does. Only the neighbour is looked at, as for a panel (see {@link TiledPanelBlock}), so
     * an archway can also go up a block at a time, as a schematic puts one up: a jamb whose column does not reach the
     * arch yet takes what is beside and under it as it is.
     */
    private boolean standsWith(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(PART) == Part.JAMB && direction == Direction.UP) {
            return carriesOn(neighbour, state.getValue(FACING));
        }
        Archway archway = archwayOf(level, pos, state);
        BlockState expected = archway == null ? null : archway.blocks(defaultBlockState()).get(neighbourPos);
        return expected == null || !expected.is(this) || samePiece(neighbour, expected);
    }

    /** Whether a block is the piece of an archway put up whole that is expected there; the crown faces either way. */
    private boolean samePiece(BlockState block, BlockState expected) {
        if (!block.is(this) || !block.getValue(WHOLE) || block.getValue(PART) != expected.getValue(PART)) {
            return false;
        }
        Direction facing = block.getValue(FACING);
        return expected.getValue(PART).crown() ? facing.getAxis() == expected.getValue(FACING).getAxis() : facing == expected.getValue(FACING);
    }

    /**
     * Broken in creative mode, a piece of an archway put up whole takes its crown down first without a drop, and the
     * rest follows it (see {@link #updateShape}), so nothing drops.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        Archway archway = state.getValue(WHOLE) ? archwayOf(level, pos, state) : null;
        if (!level.isClientSide && player.isCreative() && archway != null) {
            BlockPos crown = archway.at(0, rows(archway.span()));
            if (!crown.equals(pos) && samePiece(level.getBlockState(crown), archway.blocks(defaultBlockState()).get(crown))) {
                level.setBlock(crown, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * The crown of an archway put up whole takes the panels that went up with it over a wide one's jambs down with it,
     * so taking the archway down frees all the room it took to put it up.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.getValue(WHOLE) && state.getValue(PART).crown() && !newState.is(this)) {
            takeDownPanels(level, archwayOf(level, pos, state));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Takes the panels of an archway put up whole down where they still stand, dropping nothing. */
    private void takeDownPanels(Level level, Archway archway) {
        for (Map.Entry<BlockPos, BlockState> block : archway.blocks(defaultBlockState()).entrySet()) {
            if (!block.getValue().is(this) && level.getBlockState(block.getKey()).is(block.getValue().getBlock())) {
                level.removeBlock(block.getKey(), false);
            }
        }
    }

    /**
     * Itself, as it went up: from an archway put up whole, a whole archway; otherwise the piece it is, riding along as
     * the block state to put it down as (the loot table keeps it the same way).
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (!state.getValue(WHOLE)) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(PART, state.getValue(PART)));
        }
        return stack;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(PART)).get(state.getValue(FACING));
    }
}
