package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BeanBagBlock;
import dev.aerolarge.block.Brightness;
import dev.aerolarge.block.LookoutGlassBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The observation deck, her highest story under the cockpit: in place of its oval windows, a band of the cockpit's
 * smoked glass all the way round her at eye height, so the whole horizon is seen from it; out to her sides and her back
 * a lookout glass on its stand looking out, a bean bag either side of it to lie back in and watch the land fall away
 * or the stars come out, and between them a pair of bean bags at each diagonal, facing out too; and overhead, as the
 * airliner's lounge has, a starlight ceiling of the airliner's starlight panels, dimmed so the stars outside outshine
 * it.
 */
final class RocketObservationDeck extends RocketRoom {
    /** Her observation deck's story, from the entry story up. */
    static final int STORY = 8;
    /** How far in from the skin's inside the glasses and the bean bags at the windows stand, in blocks. */
    private static final double AT_WINDOW = 3.5;
    /** How far round either side of a window the bean bags beside its glass are, in degrees. */
    private static final double BESIDE = 16;
    /** The band of glass's lowest and highest rows over the floor. */
    private static final int BAND_FROM = 1;
    private static final int BAND_TO = 5;
    /** How far in from the skin's inside the bean bags at the diagonals stand, and how far round apart a pair is. */
    private static final double LOUNGE_IN = 2.5;
    private static final double LOUNGE_APART = 9;

    RocketObservationDeck(RocketLayout layout) {
        super(layout, STORY);
    }

    /** Its band of glass takes the windows' place. */
    @Override
    protected boolean windowed() {
        return false;
    }

    @Override
    public void build() {
        band();
        starlight();
        int y = floor() + 1;
        double out = wall() - AT_WINDOW;
        for (double round : new double[]{90, 180, 270}) {
            Direction facing = inward(round).getOpposite();
            put(across(out, round), y, along(out, round), AerolargeRegistry.LOOKOUT_GLASS.get().defaultBlockState().setValue(LookoutGlassBlock.FACING, facing));
            for (int side = -1; side <= 1; side += 2) {
                double at = round + side * BESIDE;
                put(across(out, at), y, along(out, at), beanBag(facing, DyeColor.GRAY));
            }
        }
        double lounge = wall() - LOUNGE_IN;
        for (double round = 45; round < 360; round += 90) {
            for (int side = -1; side <= 1; side += 2) {
                double at = round + side * LOUNGE_APART / 2;
                put(across(lounge, at), y, along(lounge, at), beanBag(inward(at).getOpposite(), DyeColor.BLACK));
            }
        }
    }

    /** The band of smoked glass: every block of her skin in its rows, cover or oval window, turned to glass. */
    private void band() {
        int reach = layout.reach();
        BlockState glass = AerolargeRegistry.SMOKED_GLASS.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor() + BAND_FROM, -reach, reach, floor() + BAND_TO, reach)) {
            BlockState skin = layout.stateAt(at.getX(), at.getY(), at.getZ());
            if (skin.is(AerolargeRegistry.ZEPPELIN_COVER.get()) || skin.is(AerolargeRegistry.OVAL_WINDOW.get())) {
                layout.set(at.getX(), at.getY(), at.getZ(), glass);
            }
        }
    }

    /** The starlight ceiling: the airliner's starlight panels, dimmed, in every free block of the top row. */
    private void starlight() {
        int reach = (int) Math.ceil(wall());
        BlockState panel = AerolargeRegistry.STARLIGHT_PANEL.get().defaultBlockState().setValue(Brightness.PROPERTY, Brightness.DIM);
        for (BlockPos at : BlockPos.betweenClosed(-reach, top(), -reach, reach, top(), reach)) {
            put(at.getX(), at.getY(), at.getZ(), panel);
        }
    }

    private static BlockState beanBag(Direction facing, DyeColor colour) {
        return AerolargeRegistry.BEAN_BAG.get().defaultBlockState().setValue(BeanBagBlock.FACING, facing).setValue(BeanBagBlock.COLOR, colour);
    }
}
