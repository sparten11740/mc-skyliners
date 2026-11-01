package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.ClimbingRopeBlock;
import dev.skyliners.block.EngineRepeaterBlock;
import dev.skyliners.block.EngineRepeaterBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Each engine car fitted out for its engineer: a grating along the bottom of the pod, a lamp in its roof, and at its
 * forward end an engine repeater (see {@link EngineRepeaterBlock}), facing aft along the car, standing by the car's
 * engine, so its orders from the control car are answered there; and a climbing rope from a hatch in its floor down
 * to a little over the ground, to climb up into it while the zeppelin rests on the ground and to ride in it in flight.
 * Everything stands on the pod face to face, so it is assembled with the zeppelin. A smaller zeppelin's smaller pod
 * holds a smaller room, laid out by its own numbers (see {@link Room}).
 */
final class ZeppelinEngineRooms implements ZeppelinPiece {
    /** How high over the ground the rope ends, in blocks. */
    private static final int ROPE_FOOT = 2;
    /** The room in a full-size zeppelin's pod, four rows of headroom over a grating thirteen rows long. */
    private static final Room FULL_ROOM = new Room(2, 3, 6, 3);
    /** The room in a half-size zeppelin's smaller pod, two rows of headroom over a grating nine rows long. */
    private static final Room HALF_ROOM = new Room(1, 2, 4, 2);

    /**
     * An engine car's room, in blocks: how far below the pod's axis its floor is and how far above it its lamp, how far
     * fore and aft of the car's middle the grating runs and so how far forward the repeater stands, and how far aft of
     * the middle the hatch is.
     */
    private record Room(int floorBelow, int lampAbove, int floorReach, int hatchAft) {
    }

    private final ZeppelinLayout layout;

    ZeppelinEngineRooms(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return SkylinersConfig.ENGINE_REPEATERS.get();
    }

    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        List<BlockPos> cars = layout.engineCars();
        for (int engine = 0; engine < cars.size(); engine++) {
            fitOut(engine, cars.get(engine));
        }
    }

    /** The room in a pod of a zeppelin of this size. */
    private static Room room(ZeppelinSize size) {
        return switch (size) {
            case HALF -> HALF_ROOM;
            case FULL -> FULL_ROOM;
        };
    }

    /** One car: its floor, its lamp, its repeater and its rope. */
    private void fitOut(int engine, BlockPos middle) {
        Room room = room(layout.hullSize());
        int floor = middle.getY() - room.floorBelow();
        BlockState grating = SkylinersRegistry.ZEPPELIN_CATWALK.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(middle.getX() - 1, floor, middle.getZ() - room.floorReach(), middle.getX() + 1, floor,
            middle.getZ() + room.floorReach())) {
            inside(at, grating);
        }
        BlockPos lamp = middle.above(room.lampAbove());
        if (layout.stateAt(lamp.getX(), lamp.getY(), lamp.getZ()).is(SkylinersRegistry.ZEPPELIN_FABRIC.get())) {
            layout.set(lamp.getX(), lamp.getY(), lamp.getZ(), SkylinersRegistry.CABIN_LIGHT.get().defaultBlockState());
        }
        BlockPos repeater = new BlockPos(middle.getX(), floor + 1, middle.getZ() - room.floorReach());
        if (inside(repeater, SkylinersRegistry.ENGINE_REPEATER.get().defaultBlockState().setValue(EngineRepeaterBlock.FACING, Direction.NORTH))) {
            layout.fit(repeater, (blockEntity, rotation) -> {
                if (blockEntity instanceof EngineRepeaterBlockEntity standing) {
                    standing.standBy(engine);
                }
            });
        }
        rope(middle.getX(), floor, middle.getZ() + room.hatchAft());
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
            layout.set(x, y, z, SkylinersRegistry.CLIMBING_ROPE.get().defaultBlockState().setValue(ClimbingRopeBlock.UP, y < top)
                .setValue(ClimbingRopeBlock.DOWN, y > foot));
        }
    }
}
