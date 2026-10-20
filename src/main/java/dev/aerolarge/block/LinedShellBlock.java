package dev.aerolarge.block;

import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Airframe skin cut to the hull's curve like {@link ShellBlock}, and lined on the side the airliner's inside sees it
 * from (see {@link Lining}): behind its cut, where the cut is the hull's outer skin and the faces behind it look into
 * the cabin, or on its cut, round the flight deck, where the skin's inside is cut to the curve too. Only the blueprints
 * place it, where the inside sees a block of the skin cut to the curve; broken, it gives back the part it is cut from.
 */
public class LinedShellBlock extends ShellBlock {
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LINED);
    }
}
