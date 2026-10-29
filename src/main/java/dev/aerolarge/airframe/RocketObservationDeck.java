package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BeanBagBlock;
import dev.aerolarge.block.Brightness;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The observation deck, her highest story under the cockpit: in place of its oval windows, a band of the cockpit's
 * smoked glass all the way round her at eye height, so the whole horizon is seen from it; out to her sides and her back
 * an armchair facing out of it, a bean bag either side of it to lie back in and watch the land fall away or the stars
 * come out, and between them a pair of bean bags at each diagonal, facing out too; and overhead, as the airliner's
 * lounge has, the airliner's starlight panels, dimmed so the stars outside outshine them, all over the ceiling and up
 * the wall from the band to it, so the room over the band is a starry dome.
 */
final class RocketObservationDeck extends RocketRoom {
    /** Her observation deck's story, from the entry story up. */
    static final int STORY = 8;
    /** How far in from the skin's inside the armchairs and the bean bags out to her sides and back stand, in blocks. */
    private static final double AT_WINDOW = 3.5;
    /** How far round either side of a window the bean bags beside its glass are, in degrees. */
    private static final double BESIDE = 16;
    /**
     * How many rows its room is at most from its floor to its ceiling: on a bigger rocket, whose stories stay a
     * player's size, its story would otherwise rise up into her nose as a tower of bare skin.
     */
    private static final int TALLEST = 16;
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

    /**
     * The row a story's ceiling is over its floor, given the row of the floor over it: that row, or for the observation
     * deck the lower ceiling of its own that it has, furnished as her room, where its story is taller than
     * {@link #TALLEST}. Her LED strips run round under it (see {@link RocketLedStrips}).
     */
    static int ceilingOf(RocketLayout layout, int story, int floor, int over) {
        int lid = floor + TALLEST;
        boolean lidded = story == STORY && layout.rooms() && lid < over;
        return lidded ? lid : over;
    }

    @Override
    public void build() {
        band();
        int ceiling = Math.min(top() + 1, floor() + TALLEST);
        if (ceiling <= top()) {
            lid(ceiling);
        }
        starlight(ceiling - 1);
        starryWall(ceiling - 1);
        int y = floor() + 1;
        double out = wall() - AT_WINDOW;
        for (double round : new double[]{90, 180, 270}) {
            Direction facing = inward(round).getOpposite();
            seat(across(out, round), y, along(out, round), facing, DyeColor.ORANGE);
            for (int side = -1; side <= 1; side += 2) {
                double at = round + side * BESIDE;
                put(across(out, at), y, along(out, at), beanBag(facing, DyeColor.YELLOW));
            }
        }
        double lounge = wall() - LOUNGE_IN;
        for (double round = 45; round < 360; round += 90) {
            for (int side = -1; side <= 1; side += 2) {
                double at = round + side * LOUNGE_APART / 2;
                put(across(lounge, at), y, along(lounge, at), beanBag(inward(at).getOpposite(), DyeColor.LIGHT_BLUE));
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

    /**
     * A ceiling over the room at a row, under the rest of her story, as a floor is laid: out to her skin round her lift
     * shaft, which runs on up through it to her cockpit.
     */
    private void lid(int y) {
        int reach = (int) Math.ceil(wall()) + 1;
        BlockState floor = AerolargeRegistry.CABIN_FLOOR.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-reach, y, -reach, reach, y, reach)) {
            boolean inShaft = Math.abs(at.getX()) < RocketLayout.SHAFT && Math.abs(at.getZ()) < RocketLayout.SHAFT;
            if (!inShaft && layout.isOpen(at.getX(), y, at.getZ()) && layout.inFrom(at.getX(), y, at.getZ(), 0)) {
                layout.set(at.getX(), y, at.getZ(), floor);
            }
        }
    }

    /** The starlight ceiling: the airliner's starlight panels, dimmed, in every free block of the row under it. */
    private void starlight(int y) {
        int reach = (int) Math.ceil(wall());
        BlockState panel = AerolargeRegistry.STARLIGHT_PANEL.get().defaultBlockState().setValue(Brightness.PROPERTY, Brightness.DIM);
        for (BlockPos at : BlockPos.betweenClosed(-reach, y, -reach, reach, y, reach)) {
            put(at.getX(), at.getY(), at.getZ(), panel);
        }
    }

    /**
     * The starlight panels up the wall too, over the band of glass to the ceiling, in every open block from her skin in
     * to the ceiling's panels, so that the whole room over the band is a starry dome, a planetarium; but for the blocks
     * against her skin in the row under the ceiling, where her LED strips run round under it (see
     * {@link RocketLedStrips}).
     */
    private void starryWall(int highest) {
        int reach = (int) Math.ceil(wall()) + 1;
        BlockState panel = AerolargeRegistry.STARLIGHT_PANEL.get().defaultBlockState().setValue(Brightness.PROPERTY, Brightness.DIM);
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor() + BAND_TO + 1, -reach, reach, highest, reach)) {
            int x = at.getX();
            int y = at.getY();
            int z = at.getZ();
            boolean wall = layout.inFrom(x, y, z, 0) && !inRoom(x, y, z, 0) && layout.isOpen(x, y, z);
            if (wall && !(y == highest && AerolargeConfig.LED_STRIPS.get() && againstSkin(x, y, z))) {
                layout.set(x, y, z, panel);
            }
        }
    }

    /** Whether the cover of her skin stands beside a block, further out from her axis, as an LED strip runs along it. */
    private boolean againstSkin(int x, int y, int z) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            boolean outward = side.getStepX() * x + side.getStepZ() * z > 0;
            if (outward && layout.stateAt(x + side.getStepX(), y, z + side.getStepZ()).is(AerolargeRegistry.ZEPPELIN_COVER.get())) {
                return true;
            }
        }
        return false;
    }

    private static BlockState beanBag(Direction facing, DyeColor colour) {
        return AerolargeRegistry.BEAN_BAG.get().defaultBlockState().setValue(BeanBagBlock.FACING, facing).setValue(BeanBagBlock.COLOR, colour);
    }
}
