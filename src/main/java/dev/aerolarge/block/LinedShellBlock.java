package dev.aerolarge.block;

import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Airframe skin cut to the hull's curve like {@link ShellBlock}, and lined on the side the airliner's inside sees it
 * from (see {@link Lining}): behind its cut, where the cut is the hull's outer skin and the faces behind it look into
 * the cabin, or on its cut, round the flight deck, where the skin's inside is cut to the curve too. Only the blueprints
 * place it, where the inside sees a block of the skin cut to the curve; broken, it gives back the part it is cut from.
 * <p>
 * Its shape is kept in two properties, a high and a low part of about the square root of the shapes each, rather than
 * one of all of them: the game keeps a table of every state's neighbours as large as all of its property values
 * together, which for every shape in one property, as the plain skin has it, is some 75 MB; split, it is a few.
 */
public class LinedShellBlock extends ShellBlock {
    private static final int SPLIT = (int) Math.ceil(Math.sqrt(ShellGeometry.COUNT));
    public static final IntegerProperty SHAPE_HIGH = IntegerProperty.create("shape_high", 0, (ShellGeometry.COUNT - 1) / SPLIT);
    public static final IntegerProperty SHAPE_LOW = IntegerProperty.create("shape_low", 0, SPLIT - 1);
    public static final EnumProperty<Side> LINED = EnumProperty.create("lined", Side.class);

    /** Which side of its cut the block is lined on (see {@link Lining#faces(int, Side)}). */
    public enum Side implements StringRepresentable {
        /** Behind the cut: the skin's inside, its cut the outer skin. */
        BACK,
        /** On the cut: the inside of the skin cut to the curve, as round the flight deck. */
        CUT,
        /** On the cut and along it too, where the inside cut to the curve ends and the cabin sees its sides. */
        CUT_ALONG;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public LinedShellBlock(Properties properties, Supplier<? extends ItemLike> part) {
        super(properties, part);
        registerDefaultState(defaultBlockState().setValue(LINED, Side.BACK));
    }

    /** The block cut to a shape and lined on one side of the cut. */
    public BlockState lined(int shape, Side side) {
        return cut(defaultBlockState(), shape).setValue(LINED, side);
    }

    @Override
    protected int shapeOf(BlockState state) {
        int shape = state.getValue(SHAPE_HIGH) * SPLIT + state.getValue(SHAPE_LOW);
        // The split leaves a few values past the last shape, which no block is ever cut to.
        return shape < ShellGeometry.COUNT ? shape : ShellGeometry.WHOLE;
    }

    @Override
    protected BlockState cut(BlockState state, int shape) {
        return state.setValue(SHAPE_HIGH, shape / SPLIT).setValue(SHAPE_LOW, shape % SPLIT);
    }

    @Override
    protected boolean canTake(int shape) {
        return shape >= 0 && shape < ShellGeometry.COUNT;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE_HIGH, SHAPE_LOW, LINED);
    }
}
