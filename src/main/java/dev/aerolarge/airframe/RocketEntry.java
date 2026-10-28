package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CrewLockerBlock;
import dev.aerolarge.block.ParachuteRackBlock;
import net.minecraft.world.item.DyeColor;

/**
 * The entry story, where the front door lets the crew in: a parachute rack either side of the way in from the door,
 * for anyone who would rather leave her in flight; a run of crew lockers along her sides, to change into a flight suit;
 * and a pair of armchairs either side at her back, to wait in for the lift.
 */
final class RocketEntry extends RocketRoom {
    /** Her entry story, from the bottom up. */
    static final int STORY = 0;
    /** How far in from the skin's inside the racks, the lockers and the armchairs stand, in blocks. */
    private static final double ALONG_SKIN = 2.5;
    /** How far round from her front the parachute racks are, in degrees, either side of the way in. */
    private static final double RACKS = 24;
    /** The runs of lockers along her sides, from and to how far round from her front, in degrees. */
    private static final double[][] LOCKERS = {{48, 72}, {288, 312}};
    /** How far round from her front the armchairs at her back are, in degrees. */
    private static final double[] SEATS = {140, 150, 210, 220};

    RocketEntry(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        double out = wall() - ALONG_SKIN;
        for (double round : new double[]{RACKS, 360 - RACKS}) {
            put(across(out, round), y, along(out, round),
                AerolargeRegistry.PARACHUTE_RACK.get().defaultBlockState().setValue(ParachuteRackBlock.FACING, inward(round)));
        }
        for (double[] lockers : LOCKERS) {
            run(y, out, lockers[0], lockers[1]).forEach(at -> put(at.getX(), y, at.getZ(), AerolargeRegistry.CREW_LOCKER.get().defaultBlockState()
                .setValue(CrewLockerBlock.FACING, inward(roundOf(at.getX(), at.getZ())))));
        }
        for (double round : SEATS) {
            seat(across(out, round), y, along(out, round), inward(round), DyeColor.GRAY);
        }
    }
}
