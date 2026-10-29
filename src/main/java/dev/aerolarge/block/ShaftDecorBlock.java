package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of the decoration on the outside of the rocket's glass lift shaft, each of her rooms' its own (see
 * {@code RocketShaftDecor}), with no words in any of it: KIND says whose room it is. Each block is a piece of it,
 * ALONG where across the shaft's face it is, from the corner girder on the viewer's left over the glass to the one on
 * the right, and ROW which row of it, from the bottom up, so a piece several blocks wide or tall reads as one. Its
 * model is thin, against the glass side of its block, and nothing collides with it, so the walkway round the shaft
 * stays walkable. FACING is the way it faces, out from the glass. The bunk room's fairy lights and the club's neon
 * glow, and the entry's departures board is lit; the rest give no light. Broken, a piece gives itself back as it was.
 */
public class ShaftDecorBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<ShaftDecorBlock> CODEC = simpleCodec(ShaftDecorBlock::new);
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final EnumProperty<Along> ALONG = EnumProperty.create("along", Along.class);
    public static final EnumProperty<Row> ROW = EnumProperty.create("row", Row.class);
    /** How far out from the glass its pieces stand at most, in pixels: what is aimed at to break one. */
    private static final double DEPTH = 5;
    /** Its shape facing each way: a slab against the glass behind it. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Block.box(0, 0, 16 - DEPTH, 16, 16, 16));

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
            .setValue(ALONG, Along.OUTER_LEFT).setValue(ROW, Row.MIDDLE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND, ALONG, ROW);
    }

    /** A piece of a room's decoration facing one way, at a place across the shaft's face and a row. */
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
     * Fixed to the side of the block clicked, facing out from it; not on a floor or a ceiling, where it would stand
     * against nothing. Which piece it is comes from the item, as it was when it was broken.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = Hanging.onWall(context);
        return facing == null ? null : defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** The piece picked, the way one broken comes back; the default piece is left plain, so they stack. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        BlockState plain = defaultBlockState().setValue(FACING, state.getValue(FACING));
        if (state != plain) {
            BlockItemStateProperties piece = BlockItemStateProperties.EMPTY.with(KIND, state.getValue(KIND))
                .with(ALONG, state.getValue(ALONG)).with(ROW, state.getValue(ROW));
            stack.set(DataComponents.BLOCK_STATE, piece);
        }
        return stack;
    }
}
