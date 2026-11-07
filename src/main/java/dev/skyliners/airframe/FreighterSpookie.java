package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.spookie.SpookieHammockBlock;
import dev.skyliners.spookie.SpookieHammockBlockEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The freighter's Spookie's hammock and rounds (see {@link SpookieHammockBlock}): its hammock in the open floor of her
 * crew quarters, its length east and west, and the rounds its keeper hands the Spookie: from home beside the hammock
 * out through the quarters' doorway to her ring corridor, round the ring, looking into her club, her cargo hold, her
 * lounge and engineering on the way, and back home, resting at each room and longest at home. Her entry hall is left
 * out: her ramp's well opens in its floor.
 *
 * <p>Every stretch of the rounds is walked over her blocks as she is laid out: the Spookie's footprint all along it
 * must stand on her deck's floor with three rows open over it, so it never meets a wall or furniture. A room it cannot
 * walk into is left out of its rounds; a ring it cannot walk round, or a hammock with no room, leaves the Spookie out.
 * Laid out nose north, as the rest of her; none with the server config's {@code freighter.spookie} off.
 */
final class FreighterSpookie {
    /**
     * Where its hammock may hang, each a bearing in degrees clockwise from her bow and a distance out from her axis, in
     * blocks, the first with room taken: the middle of her crew quarters and round it.
     */
    private static final double[][] HAMMOCK_SPOTS = {{240, 13}, {240, 12}, {235, 14}, {245, 12}, {240, 15}, {230, 13}};
    /** The bearing of her crew quarters' doorway, in degrees. */
    private static final int QUARTERS = 240;
    /** The bearings of the rooms it looks into on its rounds, in the order it comes to them going round clockwise. */
    private static final int[] ROOMS = {300, 0, 120, 180};
    /**
     * How far out from her axis its rounds go round the open floor about her core, in blocks, the first it can walk
     * taken: clear of the core and of her room walls' ends.
     */
    private static final double[] RING_RADII = {4.5, 5, 4};
    /** How many degrees apart its stops round the ring are. */
    private static final int RING_STEP = 15;
    /**
     * How far out from her axis a room's stop is, in blocks: so far past the ring corridor's outer wall, through its
     * doorway.
     */
    private static final double INTO_ROOM = FreighterLayout.CORRIDOR + 2.5;
    /** How long it rests at home and in each room it looks into, in ticks. */
    private static final int REST_HOME = 600;
    private static final int REST_ROOM = 160;
    /** How finely a stretch of its rounds is walked over, in blocks, and how far its footprint reaches from its middle. */
    private static final double SAMPLE = 0.25;
    private static final double REACH = 0.45;
    private static final double[][] CORNERS = {{-REACH, -REACH}, {-REACH, REACH}, {REACH, -REACH}, {REACH, REACH}};

    private final HullLayout layout;
    private final int deck;

    private FreighterSpookie(HullLayout layout, int deck) {
        this.layout = layout;
        this.deck = deck;
    }

    /** Hangs the hammock and lays out the rounds in a freighter's layout whose deck's floor is the row given. */
    static void build(HullLayout layout, int deck) {
        if (SkylinersConfig.FREIGHTER_SPOOKIE.get()) {
            new FreighterSpookie(layout, deck).lay();
        }
    }

    private void lay() {
        BlockPos hammock = hammockSpot();
        if (hammock == null) {
            return;
        }
        List<BlockPos> stops = new ArrayList<>();
        List<Integer> rests = new ArrayList<>();
        BlockPos home = hammock.north();
        stops.add(home);
        rests.add(REST_HOME);
        if (!roundTheRing(home, stops, rests)) {
            return;
        }
        layout.set(hammock.getX(), hammock.getY(), hammock.getZ(),
            SkylinersRegistry.SPOOKIE_HAMMOCK.get().defaultBlockState().setValue(SpookieHammockBlock.FACING, Direction.NORTH));
        List<BlockPos> offsets = stops.stream().map(stop -> stop.subtract(hammock)).toList();
        layout.fit(hammock, (blockEntity, rotation) -> keeper(blockEntity, rotation, offsets, rests));
    }

    private static void keeper(BlockEntity blockEntity, Rotation rotation, List<BlockPos> offsets, List<Integer> rests) {
        if (blockEntity instanceof SpookieHammockBlockEntity keeper) {
            keeper.configure(offsets, rests, rotation);
        }
    }

    /**
     * The first spot with room for the hammock, its block and one either side along its length open two rows over her
     * deck's floor, and home before it, on its north side, clear for the Spookie to stand; null for none.
     */
    @Nullable
    private BlockPos hammockSpot() {
        for (double[] spot : HAMMOCK_SPOTS) {
            BlockPos at = out(spot[0], spot[1]);
            boolean hangs = clear(at.west(), 2) && clear(at, 2) && clear(at.east(), 2);
            if (hangs && clear(at.north(), 3)) {
                return at;
            }
        }
        return null;
    }

    /** A stop on the ring: the bearing it is at, in degrees, and the block it falls in. */
    private record RingStop(int bearing, BlockPos at) {
    }

    /**
     * Its rounds from home round the ring and back, added to the stops and rests: out through the quarters' doorway,
     * round the ring clockwise, into each room it can walk into and back out, and home; whether it can walk them.
     */
    private boolean roundTheRing(BlockPos home, List<BlockPos> stops, List<Integer> rests) {
        BlockPos door = out(QUARTERS, INTO_ROOM);
        for (double radius : RING_RADII) {
            List<RingStop> ring = ring(radius);
            if (ring != null && walkable(home, door) && walkable(door, ring.get(0).at())) {
                addRing(ring, door, stops, rests);
                return true;
            }
        }
        return false;
    }

    /** The ring, in its order, as it walks it from the quarters' doorway and back; added with the rooms it looks into. */
    private void addRing(List<RingStop> ring, BlockPos door, List<BlockPos> stops, List<Integer> rests) {
        stops.add(door);
        rests.add(0);
        for (RingStop stop : ring) {
            stops.add(stop.at());
            rests.add(0);
            lookIn(stop, stops, rests);
        }
        stops.add(ring.get(0).at());
        rests.add(0);
        stops.add(door);
        rests.add(0);
    }

    /** A look into the room whose doorway a stop on the ring is at, there and back, where it can walk it. */
    private void lookIn(RingStop stop, List<BlockPos> stops, List<Integer> rests) {
        boolean room = Arrays.stream(ROOMS).anyMatch(bearing -> bearing == Math.floorMod(stop.bearing(), 360));
        BlockPos inside = out(stop.bearing(), INTO_ROOM);
        if (room && walkable(stop.at(), inside)) {
            stops.add(inside);
            rests.add(REST_ROOM);
            stops.add(stop.at());
            rests.add(0);
        }
    }

    /**
     * The ring's stops at a radius, every few degrees clockwise from the quarters' doorway all the way round; null if
     * any stretch of it cannot be walked.
     */
    @Nullable
    private List<RingStop> ring(double radius) {
        List<RingStop> ring = new ArrayList<>();
        for (int turned = 0; turned < 360; turned += RING_STEP) {
            RingStop stop = new RingStop(QUARTERS + turned, out(QUARTERS + turned, radius));
            if (ring.isEmpty() || !ring.get(ring.size() - 1).at().equals(stop.at())) {
                ring.add(stop);
            }
        }
        for (int stop = 0; stop < ring.size(); stop++) {
            if (!walkable(ring.get(stop).at(), ring.get((stop + 1) % ring.size()).at())) {
                return null;
            }
        }
        return ring;
    }

    /**
     * The block over her deck's floor at a bearing, in degrees, and a distance out from her axis, in blocks, rounded
     * onto her blocks as her walls are.
     */
    private BlockPos out(double bearing, double distance) {
        double turn = Math.toRadians(bearing);
        return new BlockPos((int) Math.round(Math.sin(turn) * distance), deck + 1, (int) Math.round(-Math.cos(turn) * distance));
    }

    /** Whether the Spookie can walk straight from one stop to another, its footprint on her floor all the way. */
    private boolean walkable(BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atBottomCenterOf(from);
        Vec3 end = Vec3.atBottomCenterOf(to);
        int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) / SAMPLE));
        for (int step = 0; step <= steps; step++) {
            if (!standsAt(start.lerp(end, step / (double) steps))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the Spookie's footprint at a point stands on her floor with room over it. */
    private boolean standsAt(Vec3 at) {
        for (double[] corner : CORNERS) {
            if (!clear(BlockPos.containing(at.x + corner[0], deck + 1, at.z + corner[1]), 3)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a block over her deck's floor has the floor under it and so many rows open from it up. */
    private boolean clear(BlockPos at, int rows) {
        if (layout.isOpen(at.getX(), deck, at.getZ())) {
            return false;
        }
        for (int row = 1; row <= rows; row++) {
            if (!layout.isOpen(at.getX(), deck + row, at.getZ())) {
                return false;
            }
        }
        return true;
    }
}
