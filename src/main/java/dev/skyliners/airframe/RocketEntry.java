package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.AirlockPanelBlock;
import dev.skyliners.block.CrewLockerBlock;
import dev.skyliners.block.GymBenchBlock;
import dev.skyliners.block.ParachuteRackBlock;
import dev.skyliners.block.PlanterBlock;
import dev.skyliners.block.ShelfUnitBlock;
import dev.skyliners.block.SuitcaseBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The entry story, where the front door lets the crew in, in two halves. At her front, either side of the way in, a
 * kit-up room: a parachute rack by the door, for anyone who would rather leave her in flight; crew lockers round the
 * corner, a space helmet in each and one left ajar; a mudroom bench across the room facing a low wall of boot shelves,
 * which parts the kit-up rooms from the back of the story, a model rocket left on top; and somebody's suitcase dropped
 * nearby. By the door on her left stands the airlock panel, to cycle the airlock with. At her back, either side, a
 * waiting lounge for the lift: armchairs round a low table on a rug, one of them turned away, and bamboo in a planter.
 * Halo lights hang round the shaft.
 */
final class RocketEntry extends RocketRoom {
    /** Her entry story, from the bottom up. */
    static final int STORY = 0;
    /** How far in from the skin's inside the racks and the lockers stand, in blocks. */
    private static final double ALONG_SKIN = 2.5;
    /** How far round from her front the parachute racks are, in degrees, either side of the way in. */
    private static final double RACKS = 24;
    /** How far round from her front her right's lockers run from and to, in degrees; her left's are mirrored. */
    private static final double LOCKERS_FROM = 36;
    private static final double LOCKERS_TO = 80;
    /** How far along toward her front the mudroom bench stands, in blocks, facing the boot shelves along her side. */
    private static final int BENCH_ALONG = 2;
    /** The suitcases' colours. */
    private static final DyeColor[] CASES = {DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.PURPLE};

    RocketEntry(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        RandomSource random = scatter();
        double out = wall() - ALONG_SKIN;
        int arm = reach(0);
        int corner = corner();
        for (double round : new double[]{RACKS, 360 - RACKS}) {
            put(across(out, round), y, along(out, round),
                SkylinersRegistry.PARACHUTE_RACK.get().defaultBlockState().setValue(ParachuteRackBlock.FACING, inward(round)));
        }
        for (RocketQuarter quarter : new RocketQuarter[]{RocketQuarter.FRONT_RIGHT, RocketQuarter.FRONT_LEFT}) {
            lockers(quarter, y, out, random);
            bootShelves(quarter, y, arm, random);
            bench(quarter, y, arm, random);
        }
        lounge(RocketQuarter.BACK_RIGHT, y, corner, DyeColor.RED, Blocks.CYAN_CARPET.defaultBlockState(), random);
        lounge(RocketQuarter.BACK_LEFT, y, corner, DyeColor.ORANGE, Blocks.LIGHT_GRAY_CARPET.defaultBlockState(), random);
        airlockPanel(RocketQuarter.FRONT_LEFT, y);
        lightsRoundShaft();
    }

    /**
     * The airlock panel beside the way in from the door, as near the door as the room has space for, its screen facing
     * the way in (see {@link AirlockPanelBlock}).
     */
    private void airlockPanel(RocketQuarter quarter, int y) {
        int across = RocketQuarter.INNER - 2;
        int along = RocketQuarter.INNER;
        while (free(quarter.x(across), y, quarter.z(along + 1))) {
            along++;
        }
        put(quarter.x(across), y, quarter.z(along), SkylinersRegistry.AIRLOCK_PANEL.get().defaultBlockState()
            .setValue(AirlockPanelBlock.FACING, quarter.side().getOpposite()));
    }

    /** A quarter's lockers round its corner, facing in, one of them left ajar. */
    private void lockers(RocketQuarter quarter, int y, double out, RandomSource random) {
        boolean right = quarter == RocketQuarter.FRONT_RIGHT;
        List<BlockPos> run = run(y, out, right ? LOCKERS_FROM : 360 - LOCKERS_TO, right ? LOCKERS_TO : 360 - LOCKERS_FROM);
        int ajar = run.isEmpty() ? -1 : random.nextInt(run.size());
        for (int locker = 0; locker < run.size(); locker++) {
            BlockPos at = run.get(locker);
            crewLocker(at.getX(), y, at.getZ(), facingOff(run, at));
            if (locker == ajar && layout.stateAt(at.getX(), y, at.getZ()).is(SkylinersRegistry.CREW_LOCKER.get())) {
                layout.set(at.getX(), y, at.getZ(), layout.stateAt(at.getX(), y, at.getZ()).setValue(CrewLockerBlock.OPEN, true));
            }
        }
    }

    /**
     * The low wall of boot shelves out toward her side from the walkway, between the kit-up room and the lounge behind
     * it, facing the kit-up room; a gap left in it where it is long, and a model rocket on top.
     */
    private void bootShelves(RocketQuarter quarter, int y, int arm, RandomSource random) {
        int gap = RocketQuarter.gap(arm, random);
        BlockState shelf = ShelfUnitBlock.stocked(SkylinersRegistry.SHELF_UNIT.get(), ShelfUnitBlock.Kind.BOOTS, quarter.end());
        for (int across = RocketQuarter.INNER; across <= arm; across++) {
            if (across != gap) {
                standing(quarter.x(across), y, 0, shelf);
            }
        }
        int top = RocketQuarter.INNER + random.nextInt(Math.max(1, arm - RocketQuarter.INNER + 1));
        standing(quarter.x(top), y + 1, 0, SkylinersRegistry.MODEL_ROCKET.get().defaultBlockState()
            .setValue(BlockStateProperties.HORIZONTAL_FACING, quarter.side()));
    }

    /**
     * The mudroom bench across the kit-up room, its sitter facing the boot shelves, centred on the room's reach; and a
     * suitcase dropped by it or by the lockers, wherever the first of a few spots is free and not in front of a locker's
     * door, which it would keep shut.
     */
    private void bench(RocketQuarter quarter, int y, int arm, RandomSource random) {
        int length = RocketQuarter.roomy(arm) ? 3 : 2;
        int first = RocketQuarter.INNER + (arm - RocketQuarter.INNER + 1 - length) / 2;
        BlockState piece = SkylinersRegistry.GYM_BENCH.get().defaultBlockState().setValue(GymBenchBlock.FACING, quarter.end().getOpposite());
        List<BlockPos> bench = new ArrayList<>();
        for (int across = first; across < first + length; across++) {
            if (put(quarter.x(across), y, quarter.z(BENCH_ALONG), piece)) {
                bench.add(new BlockPos(quarter.x(across), y, quarter.z(BENCH_ALONG)));
            }
        }
        for (BlockPos at : bench) {
            layout.set(at.getX(), y, at.getZ(), SkylinersRegistry.GYM_BENCH.get().joined(piece,
                way -> layout.stateAt(at.getX() + way.getStepX(), y, at.getZ() + way.getStepZ())));
        }
        int inner = RocketQuarter.INNER;
        int[][] spots = {{inner, inner}, {inner + 1, inner}, {inner, inner + 1}, {first + length, BENCH_ALONG + 1}, {first + length, BENCH_ALONG},
            {inner + 2, inner}, {inner, inner + 2}};
        int start = random.nextInt(spots.length);
        BlockState suitcase = SkylinersRegistry.SUITCASE.get().defaultBlockState()
            .setValue(SuitcaseBlock.FACING, Direction.from2DDataValue(random.nextInt(4)))
            .setValue(SuitcaseBlock.COLOR, CASES[random.nextInt(CASES.length)]);
        for (int spot = 0; spot < spots.length; spot++) {
            int[] at = spots[(start + spot) % spots.length];
            if (!beforeLocker(quarter.x(at[0]), y, quarter.z(at[1])) && standing(quarter.x(at[0]), y, quarter.z(at[1]), suitcase)) {
                return;
            }
        }
    }

    /** Whether a spot of the floor plan is just in front of a crew locker's door, beside it and faced by it. */
    private boolean beforeLocker(int x, int y, int z) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState beside = layout.stateAt(x + side.getStepX(), y, z + side.getStepZ());
            if (beside.is(SkylinersRegistry.CREW_LOCKER.get()) && beside.getValue(CrewLockerBlock.FACING) == side.getOpposite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A waiting lounge in a quarter: a low table in the middle of its corner, an armchair on each side of it that has
     * room facing it, one turned a quarter away, as left by whoever last got up; bamboo in a planter at the first spot
     * free beside them; and a rug under it all.
     */
    private void lounge(RocketQuarter quarter, int y, int corner, DyeColor colour, BlockState carpet, RandomSource random) {
        int middle = RocketQuarter.middle(corner);
        put(quarter.x(middle), y, quarter.z(middle), SkylinersRegistry.LOUNGE_TABLE.get().defaultBlockState());
        Direction side = quarter.side();
        Direction end = quarter.end();
        int[][] chairs = {{middle - 1, middle}, {middle, middle - 1}, {middle + 1, middle}, {middle, middle + 1}};
        Direction[] facings = {side, end, side.getOpposite(), end.getOpposite()};
        int turned = random.nextInt(chairs.length);
        for (int chair = 0; chair < chairs.length; chair++) {
            Direction facing = chair == turned ? facings[chair].getClockWise() : facings[chair];
            seat(quarter.x(chairs[chair][0]), y, quarter.z(chairs[chair][1]), facing, colour);
        }
        int[][] planters = {{RocketQuarter.INNER, middle + 1}, {middle + 1, RocketQuarter.INNER}, {middle + 2, middle}, {middle, middle + 2}};
        BlockState planter = PlanterBlock.planted(SkylinersRegistry.PLANTER.get(), PlanterBlock.Plant.BAMBOO, end.getOpposite());
        for (int[] at : planters) {
            if (standing(quarter.x(at[0]), y, quarter.z(at[1]), planter)) {
                break;
            }
        }
        rug(quarter.x(middle - 1), quarter.z(middle - 1), quarter.x(middle + 1), quarter.z(middle + 1), carpet);
    }
}
