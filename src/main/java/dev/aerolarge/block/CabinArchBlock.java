package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
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
 */
public class CabinArchBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<CabinArchBlock> CODEC = simpleCodec(CabinArchBlock::new);
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** How far out from the opening's edge the white panelling reaches, in pixels, its moulding the first two. */
    public static final int BAND = 6;
    /** The piece put down when nothing says which: the crown of a narrow archway, the most of an arch one piece shows. */
    private static final Part DEFAULT = Part.NARROW_0_2;
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

    public CabinArchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.EAST).setValue(PART, DEFAULT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
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
     * Put down, it is the right-hand side of an archway as its builder faces it (sneaking, the left-hand side), the
     * piece it was taken down as or picked as (see {@link #getCloneItemStack}).
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction look = context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, context.isSecondaryUseActive() ? look.getCounterClockWise() : look.getClockWise());
    }

    /**
     * Itself, as the piece it is: the piece rides along as the block state to put it down as (the loot table keeps it
     * the same way), left off for the piece put down when nothing says which, so those stack with new ones.
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (state.getValue(PART) != DEFAULT) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(PART, state.getValue(PART)));
        }
        return stack;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(PART)).get(state.getValue(FACING));
    }
}
