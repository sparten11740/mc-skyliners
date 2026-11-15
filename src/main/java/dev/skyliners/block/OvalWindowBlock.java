package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A big oval window in the rocket's skin, three blocks wide and four tall, each block a piece of it (see
 * {@link OvalWindowPieceBlock}), its glass bulging a little out of the skin. Each piece is the inner half of its block,
 * where the skin's cut runs through the block's middle out to the four ways round a round body, so it sits flush with
 * the skin beside it. It turns with the ship as she is built facing any way. Inside, the wall round its oval is her
 * cover's weave in her livery, or, in a room finished its own way, that room's wall (see {@link RocketFinish}), so the
 * oval sits in the wall as the room has it. Broken, a piece of hers gives back glass.
 *
 * <p>It is also set into any wall by hand, from its item or picked from one in creative mode, and then looks out away
 * from whoever sets it, its inside toward them ({@link Wall#MATCHED}); the same item lays it in a floor or a roof (see
 * {@link FlatOvalWindowBlock}). Set by hand, each piece fills its whole block, flush with the wall on both sides, its
 * glass standing out of the wall's face in its collar as hers stands out of her skin (see {@link StandingWindowBlock}).
 * Where an opening has no room for her size, it goes in as the largest of its smaller sizes that fits, its {@link
 * Span}, down to one block wide and two tall, as a block of its own for those (see {@link SmallOvalWindowBlock}).
 */
public class OvalWindowBlock extends StandingWindowBlock {
    private static final MapCodec<OvalWindowBlock> CODEC = simpleCodec(OvalWindowBlock::new);
    /** How many blocks wide one window is. */
    public static final int COLUMNS = Size.ROCKET.columns();
    /** How many blocks tall one window is. */
    public static final int ROWS = Size.ROCKET.rows();
    /**
     * A piece's column from the left as seen from outside, standing here or lying flat ({@link FlatOvalWindowBlock}
     * keeps the same property, so both keep the names their pieces were saved with).
     */
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, COLUMNS - 1);
    /** A piece's row from the top, kept as its column is. */
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, ROWS - 1);
    /** What the wall round its oval is inside: her cover, or a room's finish. */
    public static final EnumProperty<Wall> WALL = EnumProperty.create("wall", Wall.class);

    /**
     * The sizes an oval window takes set by hand, in the order they are tried, the largest first, so it fills as much
     * of the opening it is set into as fits: the rocket's own, three blocks wide and four tall, down to one block wide
     * and two tall. One a block across is the Porthole. Each is measured as hers is (see {@link Size#ofBlocks}) and
     * named by its blocks, "2x4", as its models and its textures name it.
     */
    public enum Span {
        THREE_BY_FOUR(3, 4), THREE_BY_THREE(3, 3), TWO_BY_FOUR(2, 4), TWO_BY_THREE(2, 3), THREE_BY_TWO(3, 2),
        ONE_BY_FOUR(1, 4), TWO_BY_TWO(2, 2), ONE_BY_THREE(1, 3), THREE_BY_ONE(3, 1), ONE_BY_TWO(1, 2), TWO_BY_ONE(2, 1);

        private final Size size;

        Span(int columns, int rows) {
            this.size = Size.ofBlocks(columns, rows);
        }

        /** How big a window of this span is, and how its glass and steel are measured round its oval. */
        public Size size() {
            return size;
        }
    }

    /**
     * The wall round a window's oval inside: her cover's weave, painted by her livery, or one of the rooms' finishes.
     */
    public enum Wall implements StringRepresentable {
        COVER, ENTRY, CARGO, MESS, BUNKS, GREENHOUSE, POOL, CLUB, LAB, LOUNGE, COCKPIT,
        /** Set into a wall by hand: round its oval, outside and in, it looks like the wall beside it. */
        MATCHED;

        /** The wall of a room finished its own way; the cover's for a finish no window is set in, as the dome's. */
        public static Wall of(RocketFinish finish) {
            for (Wall wall : values()) {
                if (wall.name().equals(finish.name())) {
                    return wall;
                }
            }
            return COVER;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public OvalWindowBlock(Properties properties) {
        super(properties, Size.ROCKET);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 0).setValue(ROW, 0)
            .setValue(DIM, 0).setValue(WALL, Wall.COVER));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW, DIM, WALL);
    }

    /** Set by hand, at her size first and then at each smaller one (see {@link SmallOvalWindowBlock}). */
    @Override
    protected List<BlockState> sizes(BlockState piece) {
        return Stream.concat(Stream.of(piece), SkylinersRegistry.SMALL_OVAL_WINDOW.get().spans(piece.getValue(FACING))).toList();
    }

    @Override
    protected IntegerProperty columnProperty() {
        return COLUMN;
    }

    @Override
    protected IntegerProperty rowProperty() {
        return ROW;
    }

    @Override
    protected FlatWindowBlock lyingFlat() {
        return SkylinersRegistry.FLAT_OVAL_WINDOW.get();
    }

    @Override
    protected BlockState handSet(BlockState piece) {
        return piece.setValue(WALL, Wall.MATCHED);
    }

    @Override
    public boolean setByHand(BlockState piece) {
        return piece.getValue(WALL) == Wall.MATCHED;
    }
}
