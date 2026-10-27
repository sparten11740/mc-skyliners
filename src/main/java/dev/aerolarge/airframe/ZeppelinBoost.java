package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BoostButtonBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * The speed boost (see {@link BoostButtonBlockEntity}): its button on the inside of the control car's front wall,
 * under the windows beside the rudder wheel, where whoever stands at the wheel or the control head sees it; and its
 * boosters, rings of thrusters round her tail between the fins, each standing on the tail's cover just aft of it with
 * its relay set into the cover in front of it, so all of them push along her length, through the hull's drag. Everything stands on the car or the
 * cover face to face, so it is assembled with her. The {@code speedBoost} switch leaves it off a zeppelin built from
 * then on.
 */
final class ZeppelinBoost implements ZeppelinPiece {
    /** How far to starboard of the middle the button is, beside the rudder wheel, and how high over the floor, in blocks. */
    private static final int ACROSS = 1;
    private static final int UP = 1;
    /** The boosters at half size: a ring of them, its middle 7 blocks over her axis. */
    private static final Boosters HALF_BOOSTERS = new Boosters(new int[] {2}, 7);
    /**
     * The boosters at full size: two rings, as her three times the weight needs twice the boosters to gather as much
     * speed, their middle twice as far over her axis, as her fins are twice the size.
     */
    private static final Boosters FULL_BOOSTERS = new Boosters(new int[] {3, 6}, 10);
    /** How many boosters stand in a ring, between the fins: two between each pair. */
    private static final int RING_BOOSTERS = 8;

    private final ZeppelinLayout layout;

    /**
     * Where a size's boosters stand: how far out from her axis each ring of them is, and how far over her axis their
     * middle, in blocks. The hull's drag centres over her axis, her upper fin the larger as her lower stops short of
     * the ground: boosters pushing under it would lift her nose, and her with it, as she gathers speed. Found in
     * play-testing.
     */
    private record Boosters(int[] rings, int rise) {
    }

    ZeppelinBoost(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.SPEED_BOOST.get();
    }

    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        ZeppelinLayout.Car car = layout.car();
        BlockPos button = new BlockPos(ACROSS, car.floor() + UP, car.front() + 1);
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (!layout.stateAt(button.getX(), button.getY(), button.getZ()).isAir() || layout.isOpen(button.getX(), button.getY(), button.getZ() - 1)) {
            return;
        }
        List<BlockPos> relays = new ArrayList<>();
        Boosters boosters = boosters(layout.hullSize());
        int middle = layout.axis() + boosters.rise();
        for (int ring : boosters.rings()) {
            for (int booster = 0; booster < RING_BOOSTERS; booster++) {
                double round = Math.PI / RING_BOOSTERS + 2 * Math.PI * booster / RING_BOOSTERS;
                booster((int) Math.round(ring * Math.cos(round)), middle + (int) Math.round(ring * Math.sin(round)), relays);
            }
        }
        if (relays.isEmpty()) {
            return;
        }
        layout.set(button.getX(), button.getY(), button.getZ(), AerolargeRegistry.BOOST_BUTTON.get().defaultBlockState()
            .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.SOUTH));
        List<BlockPos> offsets = relays.stream().map(relay -> relay.subtract(button)).toList();
        layout.fit(button, (blockEntity, rotation) -> {
            if (blockEntity instanceof BoostButtonBlockEntity rigged) {
                rigged.rig(offsets.stream().map(offset -> offset.rotate(rotation)).toList());
            }
        });
    }

    private static Boosters boosters(ZeppelinSize size) {
        return switch (size) {
            case HALF -> HALF_BOOSTERS;
            case FULL -> FULL_BOOSTERS;
        };
    }

    /**
     * A booster across and up from the stern's axis: on the tail's cover where it ends, looked for from aft of the tail
     * forward, pushing ahead, its relay in the cover in front of it.
     */
    private void booster(int x, int y, List<BlockPos> relays) {
        for (int z = layout.length() + 1; z > layout.length() / 2; z--) {
            if (!layout.isOpen(x, y, z - 1)) {
                if (cover(layout.stateAt(x, y, z - 1)) && layout.booster(x, y, z, Direction.NORTH)) {
                    relays.add(layout.relay(x, y, z - 1));
                }
                return;
            }
        }
    }

    private static boolean cover(BlockState state) {
        return state.is(AerolargeRegistry.ZEPPELIN_COVER.get()) || state.is(AerolargeRegistry.ZEPPELIN_FABRIC.get());
    }
}
