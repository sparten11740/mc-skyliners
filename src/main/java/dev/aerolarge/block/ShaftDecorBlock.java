package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of the decoration on the outside of the rocket's glass lift shaft, each of her rooms' its own (see
 * {@code RocketShaftDecor}), with no words in any of it: KIND says whose room it is. Each block is a piece of it,
 * ALONG where across the shaft's face it is, from the corner girder on the viewer's left over the glass to the one on
 * the right, and ROW which row of it, from the bottom up, so a piece several blocks wide or tall reads as one. MOUNT
 * says how it is put up. On a wall, as the shaft has it and as every piece saved before it had a mount loads, its
 * model is thin, against the wall side of its block. On a floor or under a ceiling, where a piece of a picture several
 * blocks wide would make no sense, it is its room's own small decoration, whole in one block whatever its ALONG and
 * ROW, which it keeps so that broken it gives back the piece it was: the departures board on a stand or hung on rods,
 * the menu as a sandwich board or hung on chains, strands of fairy lights hung from wires, ivy in a pot or trailing
 * from the ceiling, the life ring on a post, neon tubes standing or hung on wires, and a mobile whiteboard (see
 * {@link #STANDING} and {@link #HUNG}). Nothing collides with any of it, so the walkway round the shaft stays walkable.
 * FACING is the way it faces: out from the wall, or toward whoever put it up. The bunk room's fairy lights and the
 * club's neon glow, and the entry's departures board is lit; the rest give no light. Broken, a piece gives itself
 * back as it was.
 */
public class ShaftDecorBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<ShaftDecorBlock> CODEC = simpleCodec(ShaftDecorBlock::new);
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final EnumProperty<Along> ALONG = EnumProperty.create("along", Along.class);
    public static final EnumProperty<Row> ROW = EnumProperty.create("row", Row.class);
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.CEILING, Mount.WALL, Mount.FLOOR);
    /** How far out from the glass its pieces stand at most, in pixels: what is aimed at to break one. */
    private static final double DEPTH = 5;
    /** Its shape on a wall facing each way: a slab against the wall behind it. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Block.box(0, 0, 16 - DEPTH, 16, 16, 16));
    /**
     * The rooms whose decoration stands on a floor as a small one of its own, and its shape facing each way, drawn to
     * its model: the departures board's screen on its pole and foot, the sandwich board's A-frame in steps, the ivy's
     * pot and the leaves over it, the life ring before its post on its foot plate, the two neon tubes on their stands,
     * and the mobile whiteboard on its feet. The cargo bay's corner guards stand only round a corner and the
     * observation deck's curtains need a window to frame, so those go only on a wall; fairy lights on a floor would lie
     * in a heap, so the bunk room's go on a wall or under a ceiling.
     */
    private static final Map<Kind, Map<Direction, VoxelShape>> STANDING = Map.of(
        Kind.ENTRY, TurnedShapes.of(Shapes.or(
            Block.box(4, 0, 5, 12, 1.5, 11), Block.box(7, 1.5, 7, 9, 6, 9), Block.box(1, 6, 6.25, 15, 16, 9))),
        Kind.MESS, TurnedShapes.of(Shapes.or(Block.box(2, 0, 0.5, 14, 5, 15.5), Block.box(2, 5, 2.5, 14, 10, 13.5),
            Block.box(2, 10, 4.5, 14, 15.5, 11.5))),
        Kind.GREENHOUSE, TurnedShapes.of(Block.box(3.5, 0, 3.5, 12.5, 13, 12.5)),
        Kind.POOL, TurnedShapes.of(Shapes.or(Block.box(5, 0, 8, 11, 1, 14), Block.box(6.75, 1, 9.75, 9.25, 16, 12.25),
            Block.box(2.75, 3.75, 7.75, 13.25, 14.25, 10))),
        Kind.CLUB, TurnedShapes.of(Shapes.or(
            Block.box(2.5, 0, 3.5, 7.5, 15.5, 8.5), Block.box(8.5, 0, 8, 13.5, 12, 13))),
        Kind.LAB, TurnedShapes.of(Shapes.or(
            Block.box(0.25, 0, 3, 15.75, 1.75, 13), Block.box(0.25, 1.75, 5.75, 15.75, 15, 9))));
    /**
     * The rooms whose decoration hangs under a ceiling as a small one of its own, and its shape facing each way, drawn
     * to its model: the departures board's screen on its rods, the menu board on its chains, the fairy lights' strands
     * from their wires, the ivy's mat and its garlands, and the two neon tubes on their wires. A life ring or a
     * whiteboard is not hung from a ceiling, and neither are the cargo bay's guards or the curtains, so those go up
     * only where else they make sense.
     */
    private static final Map<Kind, Map<Direction, VoxelShape>> HUNG = Map.of(
        Kind.ENTRY, TurnedShapes.of(Shapes.or(
            Block.box(1, 3, 6.25, 15, 13, 9.75), Block.box(3, 13, 7, 5, 16, 9), Block.box(11, 13, 7, 13, 16, 9))),
        Kind.MESS, TurnedShapes.of(Shapes.or(
            Block.box(1, 2, 7, 15, 13, 9), Block.box(3, 13, 7, 5, 16, 9), Block.box(11, 13, 7, 13, 16, 9))),
        Kind.BUNKS, TurnedShapes.of(Block.box(1, 0.5, 4.5, 15, 16, 11.5)),
        Kind.GREENHOUSE, TurnedShapes.of(Block.box(1, 3, 1, 15, 16, 15)),
        Kind.CLUB, TurnedShapes.of(Shapes.or(
            Block.box(1, 7.75, 3.75, 15, 16, 6.25), Block.box(1, 3.75, 9.75, 15, 16, 12.25))));

    /**
     * Whose room a piece's decoration is, and so how it looks, glows and sounds: the entry's departures board, its
     * destinations as pictures; the cargo bay's hazard-striped rubber guards round the shaft's corners and its load
     * chart; the mess's chalkboard menu of food drawn in chalk; the bunk room's warm fairy lights; the greenhouse's
     * ivy; the pool's life ring on its hook; the club's pink and cyan neon tubes; the lab's whiteboard of a molecule
     * and a graph; and the observation deck's deep blue velvet curtains, tied back with gold, under their valance.
     */
    public enum Kind implements StringRepresentable {
        ENTRY(3, SoundType.METAL),
        CARGO(0, SoundType.WOOL),
        MESS(0, SoundType.WOOD),
        BUNKS(6, SoundType.GLASS),
        GREENHOUSE(0, SoundType.VINE),
        POOL(0, SoundType.WOOL),
        CLUB(8, SoundType.GLASS),
        LAB(0, SoundType.METAL),
        OBSERVATION(0, SoundType.WOOL);

        /** How brightly a piece of it lights what is round it, as a block's light level. */
        private final int glow;
        private final SoundType sound;

        Kind(int glow, SoundType sound) {
            this.glow = glow;
            this.sound = sound;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Where across a face of the shaft a piece is, as seen from in front: before the corner girder on the left, over
     * the glass on the left, in the middle or on the right, or before the girder on the right.
     */
    public enum Along implements StringRepresentable {
        OUTER_LEFT,
        LEFT,
        MIDDLE,
        RIGHT,
        OUTER_RIGHT;

        /** The place so many blocks to the viewer's right of the face's middle, the girders' two out. */
        public static Along of(int right) {
            return values()[MIDDLE.ordinal() + right];
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Which row of a piece's decoration it is, from the bottom up: a board's lower and upper rows are its bottom and
     * top; a curtain's are its hem, its fall under its tieback, its tieback, its fall over it and its valance; the
     * fairy lights' string is the top and the strands hanging from it the row under it; a column's are its foot, its
     * middle rows and its top.
     */
    public enum Row implements StringRepresentable {
        BOTTOM,
        LOWER,
        MIDDLE,
        UPPER,
        TOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public ShaftDecorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, Kind.OBSERVATION)
            .setValue(ALONG, Along.OUTER_LEFT).setValue(ROW, Row.MIDDLE).setValue(MOUNT, Mount.WALL));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND, ALONG, ROW, MOUNT);
    }

    /** A piece of a room's decoration on a wall facing one way, at a place across the shaft's face and a row. */
    public BlockState piece(Kind kind, Direction facing, Along along, Row row) {
        return defaultBlockState().setValue(KIND, kind).setValue(FACING, facing).setValue(ALONG, along).setValue(ROW, row);
    }

    /** The viewer's right, looking at a piece facing this way: the way ALONG runs. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** How brightly a piece lights what is round it: its kind's glow. */
    public static int glow(BlockState state) {
        return state.getValue(KIND).glow;
    }

    /** Each kind sounds as what it is made of: velvet, rubber or a life ring soft, the boards hard, lights glass. */
    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return state.getValue(KIND).sound;
    }

    /**
     * Put up against what holds it where the player puts it (see {@link Mounting}): fixed to a wall, facing out from
     * it, or, for a room whose decoration has a small one of its own, standing on a floor or hung under a ceiling,
     * facing whoever puts it up; not where nothing holds it. Which piece it is comes from the item, as it was when it
     * was broken, and is set only once the block is placed, so its room is read from the item here to know how it may
     * go up.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, mounts(kindOf(context.getItemInHand())));
        if (toward == null) {
            return null;
        }
        Mount mount = Mount.against(toward);
        Direction facing = mount == Mount.WALL ? toward.getOpposite() : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(MOUNT, mount).setValue(FACING, facing);
    }

    /** The room whose piece an item puts up, kept on it when it was broken; the block's own piece's if it has none. */
    private Kind kindOf(ItemStack stack) {
        Kind kind = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(KIND);
        return kind == null ? defaultBlockState().getValue(KIND) : kind;
    }

    /** How a room's piece may go up: on a wall, and on a floor or under a ceiling where it has a small one for it. */
    private static Set<Mount> mounts(Kind kind) {
        Set<Mount> mounts = EnumSet.of(Mount.WALL);
        if (STANDING.containsKey(kind)) {
            mounts.add(Mount.FLOOR);
        }
        if (HUNG.containsKey(kind)) {
            mounts.add(Mount.CEILING);
        }
        return mounts;
    }

    /** A piece's shape on a wall, or its room's small one's on a floor or under a ceiling; a wall's if it has none. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Kind, Map<Direction, VoxelShape>> small = switch (state.getValue(MOUNT)) {
            case FLOOR -> STANDING;
            case CEILING -> HUNG;
            case WALL -> Map.of();
        };
        return small.getOrDefault(state.getValue(KIND), SHAPES).get(state.getValue(FACING));
    }

    /**
     * The piece picked, the way one broken comes back, however it is put up; the default piece is left plain, so they
     * stack.
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        BlockState plain = defaultBlockState().setValue(FACING, state.getValue(FACING))
            .setValue(MOUNT, state.getValue(MOUNT));
        if (state != plain) {
            BlockItemStateProperties piece = BlockItemStateProperties.EMPTY.with(KIND, state.getValue(KIND))
                .with(ALONG, state.getValue(ALONG)).with(ROW, state.getValue(ROW));
            stack.set(DataComponents.BLOCK_STATE, piece);
        }
        return stack;
    }
}
