package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BallastValveBlockEntity;
import dev.aerolarge.block.BallastValveBlockEntity.Station;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;

/**
 * "Up ship!": the water ballast valve hung under the gangway at its forward end, its pipe's outlet in the belly under
 * it, and the stations of the ground handlers who hold the zeppelin on her lines as she takes off from the ground (see
 * {@link BallastValveBlockEntity}): the ground officer on her centre line ahead of the control car, facing it, and on
 * each side two handlers in a file on a line from under her bow, two at the car's side, one under her flank amidships
 * and one under her stern. The valve hangs from the gangway face to face, so it is assembled with the zeppelin; the
 * handlers stand on the ground, drawn by the clients, and are no blocks at all.
 */
final class ZeppelinUpShip implements ZeppelinPiece {
    /** How far aft of the gangway's forward end the valve hangs, in rows: clear of the post at its first ring. */
    private static final int VALVE_AFT = 2;
    /** The row the handlers stand in, on the ground the zeppelin's wheels stand on. */
    private static final int GROUND = 0;
    /** Where the bow lines hang from, in blocks: how far aft of the nose's tip, and how far either side. */
    private static final int BOW_LINE_Z = 24;
    private static final int BOW_LINE_X = 3;
    /**
     * The bow lines' files: how many handlers each, how far out and forward of the line's foot the first stands, and
     * how much further each next one does, in blocks.
     */
    private static final int BOW_FILE = 2;
    private static final int BOW_OUT = 6;
    private static final int FILE_STEP = 3;
    /**
     * The handlers at the control car: how far in from its ends they stand and how far out from its sides, in blocks.
     */
    private static final int CAR_IN = 4;
    private static final int CAR_OUT = 3;
    /**
     * The flank lines: how far either side of the middle they hang from, and how far out the handlers stand, in blocks.
     */
    private static final int FLANK_X = 24;
    private static final int FLANK_OUT = 8;
    /**
     * The stern lines: how far aft of the nose's tip, how far either side, and how far out and aft the handlers stand,
     * in blocks.
     */
    private static final int TAIL_Z = 400;
    private static final int TAIL_X = 3;
    private static final int TAIL_OUT = 6;
    /**
     * How far ahead of the control car's front the ground officer stands, in blocks: well clear of the water ballast
     * pouring from the outlet ahead of the car.
     */
    private static final int OFFICER_AHEAD = 14;
    private static final int[] SIDES = {-1, 1};

    private final ZeppelinLayout layout;

    ZeppelinUpShip(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.UP_SHIP.get();
    }

    @Override
    public void build() {
        int z = (int) Math.floor(ringAt(1) * layout.scale()) + VALVE_AFT;
        BlockPos valve = new BlockPos(0, layout.gangwayRow() - 1, z);
        Integer belly = underside(0, z);
        if (belly == null || !layout.isOpen(valve.getX(), valve.getY(), valve.getZ())) {
            return;
        }
        layout.set(valve.getX(), valve.getY(), valve.getZ(), AerolargeRegistry.BALLAST_VALVE.get().defaultBlockState());
        BlockPos outlet = new BlockPos(0, belly, z);
        List<Station> stations = stations();
        layout.fit(valve, (blockEntity, rotation) -> {
            if (blockEntity instanceof BallastValveBlockEntity rigged) {
                rigged.rig(turned(stations, valve, rotation), outlet.subtract(valve).rotate(rotation));
            }
        });
    }

    /** Every handler's station, the officer's first, in the layout. */
    private List<Station> stations() {
        List<Station> stations = new ArrayList<>();
        ZeppelinLayout.Car car = layout.car();
        stations.add(new Station(new BlockPos(0, GROUND, car.front() - OFFICER_AHEAD), new BlockPos(0, car.floor() + 1, car.front()), null));
        int middle = (int) Math.round(LENGTH * layout.scale() / 2);
        for (int side : SIDES) {
            for (int man = 0; man < BOW_FILE; man++) {
                int out = BOW_OUT + man * FILE_STEP;
                hullLine(stations, side * BOW_LINE_X, BOW_LINE_Z, side * out, -out);
            }
            for (int z : new int[]{car.front() + CAR_IN, car.back() - CAR_IN}) {
                BlockPos line = new BlockPos(side * car.half(), car.floor() + 1, z);
                stations.add(new Station(new BlockPos(side * (car.half() + CAR_OUT), GROUND, z), line, line));
            }
            hullLine(stations, side * FLANK_X, middle, side * FLANK_OUT, 0);
            hullLine(stations, side * TAIL_X, TAIL_Z, side * TAIL_OUT, TAIL_OUT);
        }
        return stations;
    }

    /**
     * A handler on a line from the hull's underside at a point across and along it, standing so far out and aft of
     * the line's foot; none where the hull does not reach that far across.
     */
    private void hullLine(List<Station> stations, int x, int z, int out, int aft) {
        Integer y = underside(x, z);
        if (y != null) {
            BlockPos line = new BlockPos(x, y, z);
            stations.add(new Station(new BlockPos(x + out, GROUND, z + aft), line, line));
        }
    }

    /** The row just under the hull's cover at a point across and along it; null where the hull does not reach. */
    @Nullable
    private Integer underside(int x, int z) {
        double radius = layout.radiusAt(z);
        if (radius <= Math.abs(x) + 1) {
            return null;
        }
        return layout.axis() - (int) Math.ceil(Math.sqrt(radius * radius - x * x)) - 1;
    }

    /** The stations as offsets from the valve, turned as the zeppelin was placed. */
    private static List<Station> turned(List<Station> stations, BlockPos valve, Rotation rotation) {
        List<Station> turned = new ArrayList<>();
        for (Station station : stations) {
            BlockPos line = station.line() == null ? null : station.line().subtract(valve).rotate(rotation);
            turned.add(new Station(station.stand().subtract(valve).rotate(rotation), station.faces().subtract(valve).rotate(rotation), line));
        }
        return turned;
    }
}
