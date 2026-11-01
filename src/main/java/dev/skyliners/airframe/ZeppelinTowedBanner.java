package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.BannerCleatBlock;
import net.minecraft.core.Direction;

/**
 * The towed banner's cleat (see {@link BannerCleatBlock}) low on the control car's back wall, to starboard of the
 * ladder up into the hull, where the tow rope would run aft along her keel to the tail. It stands against the wall face
 * to face, so it is assembled with the zeppelin, and holds nothing up. The {@code towedBanner} switch leaves it off a
 * zeppelin built from then on.
 */
final class ZeppelinTowedBanner implements ZeppelinPiece {
    /** How far to starboard of the car's middle the cleat is, and how high over its floor, in blocks. */
    private static final int STARBOARD = 2;
    private static final int UP = 1;

    private final ZeppelinLayout layout;

    ZeppelinTowedBanner(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return SkylinersConfig.TOWED_BANNER.get();
    }

    /** In the control car, the same at every size; the banner streams from wherever her tail is. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        ZeppelinLayout.Car car = layout.car();
        int y = car.floor() + UP;
        int z = car.back() - 1;
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (layout.stateAt(STARBOARD, y, z).isAir() && !layout.stateAt(STARBOARD, y, car.back()).isAir()) {
            layout.set(STARBOARD, y, z, SkylinersRegistry.BANNER_CLEAT.get().defaultBlockState().setValue(BannerCleatBlock.FACING, Direction.NORTH));
        }
    }
}
