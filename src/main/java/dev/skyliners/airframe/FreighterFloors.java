package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LedPanelBlockEntity;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's floors, each room's its own, so no two of her rooms look alike underfoot: her cargo hold a steel
 * deck with painted loading lines, her entry hall cream tiles, her lounge warm wooden boards, engineering steel grating
 * glowing amber down in its slots, her club the rocket club's glossy black dance floor, and her crew quarters the
 * soft blue-grey carpet the rest of her deck has, as the open floor about her core keeps. Each is one of the mod's own
 * blocks, as light as the carpet it takes the place of, so she weighs and flies as before.
 *
 * <p>Laid over her deck's floor, the row of her carpet, in each room, a wedge round her axis between two of her room
 * walls, past the open floor about her core, wherever it is her carpet, before her ramp's well is cut, whose hatch
 * takes up a room's floor as it does her carpet, and before her rooms are furnished. Laid out nose north, as the rest
 * of her; none with the server config's {@code freighter.finishes} off.
 */
final class FreighterFloors {
    /** How wide each room is, round her axis, in degrees: one of six. */
    private static final int ROOM_TURN = 60;
    /**
     * Her rooms, each from the bearing of the room wall on its anticlockwise side, in degrees clockwise from her bow,
     * and its floor; her crew quarters keep her carpet.
     */
    private static final List<Floor> FLOORS = List.of(
        new Floor(330, SkylinersRegistry.FLIGHT_DECK::get, true),
        new Floor(30, SkylinersRegistry.POOL_DECK_TILE::get, false),
        new Floor(90, SkylinersRegistry.GYM_FLOOR::get, false),
        new Floor(150, SkylinersRegistry.ENGINE_GRATING::get, false),
        new Floor(270, SkylinersRegistry.DANCE_FLOOR::get, false));
    /**
     * How far apart a lined floor's loading lines are, in blocks across her, one down her middle line and the rest
     * either side of it, each running along her length.
     */
    private static final int LINES_APART = 4;

    /**
     * A room's floor: the bearing of its anticlockwise wall, in degrees, the block it is laid in, and whether loading
     * lines are painted along it.
     */
    private record Floor(int from, Supplier<? extends Block> block, boolean lined) {
    }

    private FreighterFloors() {
    }

    /** Lays the rooms' floors in a freighter's layout whose deck's floor is the row given, reaching so far out. */
    static void build(HullLayout layout, int deck, int reach) {
        if (!SkylinersConfig.FREIGHTER_FINISHES.get()) {
            return;
        }
        int across = 2 * reach + 1;
        for (Floor floor : FLOORS) {
            LedPanelBlockEntity.Wedge room = new LedPanelBlockEntity.Wedge(BlockPos.ZERO, floor.from(), floor.from() + ROOM_TURN);
            Block plain = floor.block().get();
            for (int block = 0; block < across * across; block++) {
                lay(layout, room, new BlockPos(block / across - reach, deck, block % across - reach), plain, floor.lined());
            }
        }
    }

    /** Whether a block is one of her rooms' floors, which her ramp's hatch may be cut from as from her carpet. */
    static boolean isRoomFloor(BlockState state) {
        return state.is(SkylinersRegistry.FLIGHT_DECK_STRIPE.get())
            || FLOORS.stream().anyMatch(floor -> state.is(floor.block().get()));
    }

    /**
     * Lays a room's floor at a block of her deck's floor, if it is in the room and her carpet: in her cargo hold its
     * painted loading lines where they run.
     */
    private static void lay(HullLayout layout, LedPanelBlockEntity.Wedge room, BlockPos at, Block floor, boolean lined) {
        boolean inRoom = Math.hypot(at.getX(), at.getZ()) >= FreighterLayout.CORRIDOR && room.contains(at);
        if (!inRoom || !layout.stateAt(at.getX(), at.getY(), at.getZ()).is(SkylinersRegistry.CABIN_FLOOR.get())) {
            return;
        }
        boolean line = lined && Math.floorMod(at.getX(), LINES_APART) == 0;
        Block laid = line ? SkylinersRegistry.FLIGHT_DECK_STRIPE.get() : floor;
        layout.set(at.getX(), at.getY(), at.getZ(), laid.defaultBlockState());
    }
}
