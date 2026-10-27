package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CabinSeatBlock;
import dev.aerolarge.block.PassengerScreenBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * A zeppelin's passenger screens, looking through her outside cameras (see {@link ZeppelinCamera}): on her promenades,
 * one at the foot of the windows before each armchair facing out of them, turned to whoever sits in it; and one on the
 * control car's back wall beside the Ship's Plaque, facing forward. Each is fixed to the sill or the wall behind it,
 * face to face, so it is assembled with the zeppelin; without the promenades' armchairs, those screens are left out.
 */
final class ZeppelinScreens implements ZeppelinPiece {
    /** How far in from the promenade windows an armchair is looked for, at most, in blocks. */
    private static final int CHAIR_REACH = 4;
    /**
     * Where the control car's screen hangs on its back wall, beside the Ship's Plaque: how far to port of the middle
     * and how high over the floor, in blocks.
     */
    private static final int CAR_SCREEN_OUT = 4;
    private static final int CAR_SCREEN_UP = 2;

    private final ZeppelinLayout layout;

    ZeppelinScreens(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.ZEPPELIN_SCREENS.get();
    }

    @Override
    public void build() {
        int y = layout.upperDeckRow() + 1;
        for (int z = (int) Math.ceil(DECKS_FRONT * SCALE); z <= (int) Math.floor(DECKS_BACK * SCALE); z++) {
            beforeChair(y, z, 1);
            beforeChair(y, z, -1);
        }
        ZeppelinLayout.Car car = layout.car();
        int x = -CAR_SCREEN_OUT;
        int up = car.floor() + CAR_SCREEN_UP;
        int z = car.back() - 1;
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (layout.stateAt(x, up, z).isAir()) {
            layout.set(x, up, z, screen(Direction.NORTH));
        }
    }

    /**
     * A screen between the window and the armchair facing out of it on one side of a promenade at a row, if one stands
     * there: at its foot, turned to the chair.
     */
    private void beforeChair(int y, int z, int side) {
        Direction out = side > 0 ? Direction.EAST : Direction.WEST;
        int wall = layout.wallX(y, z);
        for (int in = CHAIR_REACH; in >= 1; in--) {
            int x = side * (wall - in);
            if (facingOut(layout.stateAt(x, y, z), out) && layout.isOpen(x + side, y, z)) {
                layout.set(x + side, y, z, screen(out.getOpposite()));
                return;
            }
        }
    }

    /** Whether a block is an armchair's seat facing out of a window. */
    private static boolean facingOut(BlockState state, Direction out) {
        return state.getBlock() instanceof CabinSeatBlock && state.getValue(CabinSeatBlock.FACING) == out
            && state.getValue(CabinSeatBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static BlockState screen(Direction facing) {
        return AerolargeRegistry.PASSENGER_SCREEN.get().defaultBlockState().setValue(PassengerScreenBlock.FACING, facing);
    }
}
