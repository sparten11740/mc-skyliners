package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.CrossbowRackBlock;
import dev.skyliners.block.LedPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The freighter's entry hall, the room through the doorway at her starboard bow, where her ramp comes up: the laser
 * crossbow's rack (see {@link CrossbowRackBlock}) on the wall just in from the corridor, at eye height, the crossbow on
 * it, for whoever comes aboard to take down. Laid on the first padded wall with room there, nearest a point just in
 * through the doorway; none with the server config's {@code freighter.laserCrossbow} off. Laid out nose north, as the
 * rest of her.
 */
final class FreighterEntryHall {
    /** The entry hall's walls, at these bearings, in degrees clockwise from her bow. */
    private static final int FROM = 30;
    private static final int TO = 90;
    /**
     * Where the rack is looked for: so many rows over her deck, at eye height, from so far out from her axis to so far,
     * in blocks, and nearest to the point at this bearing and so far out, just in through the doorway beside it.
     */
    private static final int RACK_UP = 2;
    private static final double RACK_FROM = FreighterLayout.CORRIDOR;
    private static final double RACK_TO = FreighterLayout.CORRIDOR + 5;
    private static final int RACK_BEARING = 40;
    private static final double RACK_NEAR = FreighterLayout.CORRIDOR + 2;
    /** How many blocks before the rack must be open, to stand in and see it from. */
    private static final int ROOM_BEFORE = 3;

    private FreighterEntryHall() {
    }

    /** Lays the entry hall's fittings in a freighter's layout whose deck's floor is the row given. */
    static void build(HullLayout layout, int deck) {
        if (!SkylinersConfig.FREIGHTER_LASER_CROSSBOW.get()) {
            return;
        }
        double turn = Math.toRadians(RACK_BEARING);
        LedPanelBlockEntity.Wedge room = new LedPanelBlockEntity.Wedge(BlockPos.ZERO, FROM, TO);
        double towardX = Math.sin(turn) * RACK_NEAR;
        double towardZ = -Math.cos(turn) * RACK_NEAR;
        BlockPos rack = FreighterWalls.nearest(layout, deck + RACK_UP, room, RACK_FROM, RACK_TO, towardX, towardZ, ROOM_BEFORE);
        Direction behind = rack == null ? null : FreighterWalls.wallBehind(layout, rack);
        if (behind != null) {
            layout.set(rack.getX(), rack.getY(), rack.getZ(),
                SkylinersRegistry.CROSSBOW_RACK.get().defaultBlockState().setValue(CrossbowRackBlock.FACING, behind.getOpposite()));
        }
    }
}
