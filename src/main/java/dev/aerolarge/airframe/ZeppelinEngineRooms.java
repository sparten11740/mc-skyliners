package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.ClimbingRopeBlock;
import dev.aerolarge.block.EngineRepeaterBlock;
import dev.aerolarge.block.EngineRepeaterBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Each engine car fitted out for its engineer: a grating along the bottom of the pod, a lamp in its roof, and at its
 * forward end an engine repeater (see {@link EngineRepeaterBlock}), facing aft along the car, standing by the car's
 * engine, so its orders from the control car are answered there; and a climbing rope from a hatch in its floor down
 * to a little over the ground, to climb up into it while the zeppelin rests on the ground and to ride in it in flight.
 * Everything stands on the pod face to face, so it is assembled with the zeppelin.
 */
final class ZeppelinEngineRooms implements ZeppelinPiece {
    /** How far below the pod's axis its floor is, and how far above it its lamp, in blocks. */
    private static final int FLOOR_BELOW = 2;
    private static final int LAMP_ABOVE = 3;
    /**
     * How far fore and aft of the car's middle the grating runs, and how far forward the repeater stands, in blocks.
     */
    private static final int FLOOR_REACH = 6;
    /** How far aft of the car's middle the hatch is, and how high over the ground the rope ends, in blocks. */
    private static final int HATCH_AFT = 3;
    private static final int ROPE_FOOT = 2;

    private final ZeppelinLayout layout;

    ZeppelinEngineRooms(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.ENGINE_REPEATERS.get();
    }

    @Override
    public void build() {
        List<BlockPos> cars = layout.engineCars();
        for (int engine = 0; engine < cars.size(); engine++) {
            fitOut(engine, cars.get(engine));
        }
    }

    /** One car: its floor, its lamp, its repeater and its rope. */
    private void fitOut(int engine, BlockPos middle) {
        int floor = middle.getY() - FLOOR_BELOW;
        BlockState grating = AerolargeRegistry.ZEPPELIN_CATWALK.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(middle.getX() - 1, floor, middle.getZ() - FLOOR_REACH, middle.getX() + 1, floor, middle.getZ() + FLOOR_REACH)) {
            inside(at, grating);
        }
        BlockPos lamp = middle.above(LAMP_ABOVE);
        if (layout.stateAt(lamp.getX(), lamp.getY(), lamp.getZ()).is(AerolargeRegistry.ZEPPELIN_FABRIC.get())) {
            layout.set(lamp.getX(), lamp.getY(), lamp.getZ(), AerolargeRegistry.CABIN_LIGHT.get().defaultBlockState());
        }
        BlockPos repeater = new BlockPos(middle.getX(), floor + 1, middle.getZ() - FLOOR_REACH);
        if (inside(repeater, AerolargeRegistry.ENGINE_REPEATER.get().defaultBlockState().setValue(EngineRepeaterBlock.FACING, Direction.NORTH))) {
            layout.fit(repeater, (blockEntity, rotation) -> {
                if (blockEntity instanceof EngineRepeaterBlockEntity standing) {
                    standing.standBy(engine);
                }
            });
        }
        rope(middle.getX(), floor, middle.getZ() + HATCH_AFT);
    }

    /** Something inside the pod, where its inside is open; whether it stands there now. */
    private boolean inside(BlockPos at, BlockState state) {
        // The pod's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (!layout.stateAt(at.getX(), at.getY(), at.getZ()).isAir() || layout.isOpen(at.getX(), at.getY(), at.getZ())) {
            return false;
        }
        layout.set(at.getX(), at.getY(), at.getZ(), state);
        return true;
    }

    /**
     * The climbing rope down from a hatch in the floor: up through the floor and the skin under it, and down through
     * the open air under the car as far as it is clear, to a little over the ground.
     */
    private void rope(int x, int top, int z) {
        int foot = top - 1;
        while (foot > ROPE_FOOT && layout.isOpen(x, foot - 1, z)) {
            foot--;
        }
        for (int y = foot; y <= top; y++) {
            layout.set(x, y, z, AerolargeRegistry.CLIMBING_ROPE.get().defaultBlockState().setValue(ClimbingRopeBlock.UP, y < top)
                .setValue(ClimbingRopeBlock.DOWN, y > foot));
        }
    }
}
