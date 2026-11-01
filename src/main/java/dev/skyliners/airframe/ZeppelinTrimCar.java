package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.BubbleLevelBlock;
import dev.skyliners.block.HelmBlock;
import dev.skyliners.block.TrimWinchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The trim car (see {@link TrimWinchBlockEntity}): a rail of girders hung under the gangway's starboard edge either
 * side of the middle of the gas cells, the winch hung from its forward end and the heavy car from its middle, hauled
 * fore and aft along it; and in the control car, by its starboard windows, the trim wheel that says where the car goes
 * and the bubble level that shows how the ship pitches. Each hangs from or stands on what is next to it, face to face,
 * so it is assembled with the zeppelin; nothing hangs from the car.
 */
final class ZeppelinTrimCar implements ZeppelinPiece {
    /**
     * Where the rail runs: how far to starboard and how long it is, in blocks, the same at every size; and where its
     * middle is, how far aft of the nose's tip in metres along the hull: the middle of the gas cells at every size.
     */
    private static final int RAIL_ACROSS = 1;
    private static final int RAIL_LENGTH = 120;
    private static final double RAIL_MIDDLE = 95.5;
    /**
     * How far aft of the car's front the trim wheel and the bubble level stand, in blocks, by its starboard windows.
     */
    private static final int WHEEL_BACK = 5;
    private static final int LEVEL_BACK = 4;

    private final ZeppelinLayout layout;

    ZeppelinTrimCar(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return SkylinersConfig.TRIM_CAR.get();
    }

    /** Its middle at the middle of the gas cells at every size, the rail itself as long. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        int rail = layout.gangwayRow() - 1;
        int front = (int) Math.round(RAIL_MIDDLE * layout.scale()) - RAIL_LENGTH / 2;
        if (!clear(rail, front) || !clear(rail - 1, front)) {
            return;
        }
        for (int z = front; z <= front + RAIL_LENGTH; z++) {
            layout.set(RAIL_ACROSS, rail, z, SkylinersRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
        BlockPos winch = new BlockPos(RAIL_ACROSS, rail - 1, front);
        layout.set(winch.getX(), winch.getY(), winch.getZ(), SkylinersRegistry.TRIM_WINCH.get().defaultBlockState());
        int middle = RAIL_LENGTH / 2;
        layout.set(winch.getX(), winch.getY(), winch.getZ() + middle, SkylinersRegistry.TRIM_CAR.get().defaultBlockState());
        BlockPos wheel = controlCar();
        layout.fit(winch, (blockEntity, rotation) -> {
            if (blockEntity instanceof TrimWinchBlockEntity rigged) {
                rigged.rig(rotation.rotate(Direction.SOUTH), RAIL_LENGTH, middle, wheel.subtract(winch).rotate(rotation));
            }
        });
    }

    /** Whether nothing stands along the rail's row yet, under the gangway. */
    private boolean clear(int y, int front) {
        for (int z = front; z <= front + RAIL_LENGTH; z++) {
            if (!layout.isOpen(RAIL_ACROSS, y, z)) {
                return false;
            }
        }
        return true;
    }

    /** The trim wheel and the bubble level by the control car's starboard windows, facing out of them; the wheel. */
    private BlockPos controlCar() {
        ZeppelinLayout.Car car = layout.car();
        int x = car.half() - 1;
        int y = car.floor() + 1;
        BlockPos wheel = new BlockPos(x, y, car.front() + WHEEL_BACK);
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (layout.stateAt(x, y, wheel.getZ()).isAir()) {
            layout.set(x, y, wheel.getZ(), SkylinersRegistry.TRIM_WHEEL.get().defaultBlockState().setValue(HelmBlock.FACING, Direction.EAST));
        }
        if (layout.stateAt(x, y, car.front() + LEVEL_BACK).isAir()) {
            layout.set(x, y, car.front() + LEVEL_BACK, SkylinersRegistry.BUBBLE_LEVEL.get().defaultBlockState().setValue(BubbleLevelBlock.FACING, Direction.EAST));
        }
        return wheel;
    }
}
