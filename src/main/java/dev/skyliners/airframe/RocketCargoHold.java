package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.CargoCrateBlock;
import dev.skyliners.block.PalletRackBlock;
import dev.skyliners.block.ParcelScannerBlock;
import dev.skyliners.block.ShelfUnitBlock;
import dev.skyliners.block.UldContainerBlock;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Her cargo bay, its back left to her rover's cradle and its way out (see {@link RocketRoverBay}), which nothing stands
 * in. At her front, a pair of the airliner's baggage containers either side of the way in from the lift, their doors
 * facing in, and care-package crates stacked unevenly beside them, ready to be packed and dropped. Out to either side a
 * wall of pallet racks three bays high, loaded with boxes, drums and crates, parts the front of the bay from its back,
 * a gap left in it where it is long. By the way in on her left, the parcel scanner, an empty crate on its belt. At her
 * back right, a workbench corner: a crafting table and a grindstone by a stool under shelves of tool cases; at her back
 * left, crates piled where they were set down. Halo lights hang round the shaft.
 * Laid after the rover's bay, so nothing of it stands where the bay has put anything.
 */
final class RocketCargoHold extends RocketRoom {
    /** Her cargo bay's story, from the entry story up. */
    static final int STORY = 1;
    /** How far in from the skin's inside the containers, and the crates and the tool shelves, stand, in blocks. */
    private static final double CONTAINERS_IN = 3.5;
    private static final double CRATES_IN = 2.5;
    /** How far round from her front the containers and the crates' stacks stand, in degrees. */
    private static final double[] CONTAINERS = {42, 318};
    private static final double[] CRATES = {56, 64, 296, 304};
    /** How far either side of her front-to-back line her rover's cradle and its way out to her back are kept clear. */
    private static final int ROVER_LANE = 2;
    /** How many crates a stack is at most, and how many bays tall the racks and the tool shelves are. */
    private static final int STACKED = 3;
    private static final int RACK_BAYS = 3;
    private static final int TOOL_SHELVES = 2;
    /** How far round from her front the tool shelves over the workbench run from and to, in degrees. */
    private static final double TOOLS_FROM = 100;
    private static final double TOOLS_TO = 132;
    /** One in how many spots of the piled corner is left without a stack. */
    private static final int PILE_GAPS = 3;
    /** The racks' loads, a bay's picked at random: boxes and crates most often, now and then a bay left empty. */
    private static final PalletRackBlock.Load[] LOADS = {PalletRackBlock.Load.BOXES, PalletRackBlock.Load.BOXES, PalletRackBlock.Load.CRATES,
        PalletRackBlock.Load.DRUMS, PalletRackBlock.Load.CRATES, PalletRackBlock.Load.BOXES, PalletRackBlock.Load.EMPTY};

    RocketCargoHold(RocketLayout layout) {
        super(layout, STORY);
    }

    /** Free for furniture as any room's blocks are, and off the lane her rover drives along to her back. */
    @Override
    protected boolean free(int x, int y, int z) {
        return super.free(x, y, z) && !(Math.abs(x) <= ROVER_LANE && z > 0);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        RandomSource random = scatter();
        int arm = reach(0);
        int corner = corner();
        double out = wall() - CONTAINERS_IN;
        for (int k = 0; k < CONTAINERS.length; k++) {
            double round = CONTAINERS[k];
            container(new BlockPos(across(out, round), y, along(out, round)), inward(round), k);
        }
        scanner(RocketQuarter.FRONT_LEFT, y, random);
        lightsRoundShaft();
        double crates = wall() - CRATES_IN;
        for (double round : CRATES) {
            stack(across(crates, round), y, along(crates, round), crate(random), 1 + random.nextInt(STACKED));
        }
        for (RocketQuarter quarter : new RocketQuarter[]{RocketQuarter.FRONT_RIGHT, RocketQuarter.FRONT_LEFT}) {
            racks(quarter, y, arm, random);
        }
        workbench(RocketQuarter.BACK_RIGHT, y, corner);
        pile(RocketQuarter.BACK_LEFT, y, corner, random);
    }

    /**
     * The parcel scanner beside the way in from the lift, its screen facing the way in, an empty crate waiting on its
     * belt to be packed (see {@link ParcelScannerBlock}).
     */
    private void scanner(RocketQuarter quarter, int y, RandomSource random) {
        int across = RocketQuarter.INNER - 2;
        if (put(quarter.x(across), y, quarter.z(RocketQuarter.INNER), SkylinersRegistry.PARCEL_SCANNER.get().defaultBlockState()
            .setValue(ParcelScannerBlock.FACING, quarter.side().getOpposite()))) {
            standing(quarter.x(across), y + 1, quarter.z(RocketQuarter.INNER), crate(random));
        }
    }

    /** A care-package crate turned a random way, as it was set down. */
    private static BlockState crate(RandomSource random) {
        return SkylinersRegistry.CARGO_CRATE.get().defaultBlockState().setValue(CargoCrateBlock.FACING, Direction.from2DDataValue(random.nextInt(4)));
    }

    /** A baggage container with its door facing the way given, from a corner, where every block of it is free. */
    private void container(BlockPos origin, Direction facing, int number) {
        BlockState state = SkylinersRegistry.ULD_CONTAINER.get().defaultBlockState().setValue(UldContainerBlock.FACING, facing)
            .setValue(UldContainerBlock.NUMBER, number % (UldContainerBlock.NUMBER.getPossibleValues().size()));
        Map<BlockPos, BlockState> corners = UldContainerBlock.corners(origin, state);
        if (corners.keySet().stream().allMatch(at -> free(at.getX(), at.getY(), at.getZ()))) {
            corners.forEach((at, corner) -> layout.set(at.getX(), at.getY(), at.getZ(), corner));
        }
    }

    /**
     * A wall of pallet racks out toward her side from the walkway, each bay loaded at random, a gap left where it is
     * long.
     */
    private void racks(RocketQuarter quarter, int y, int arm, RandomSource random) {
        int gap = RocketQuarter.gap(arm, random);
        for (int across = RocketQuarter.INNER; across <= arm; across++) {
            if (across != gap) {
                rack(quarter.x(across), y, quarter.end(), random);
            }
        }
    }

    /**
     * A rack of bays one on another at a block along her side-to-side line, facing the way given, each loaded at
     * random.
     */
    private void rack(int x, int y, Direction facing, RandomSource random) {
        for (int bay = 0; bay < RACK_BAYS; bay++) {
            PalletRackBlock.Load load = LOADS[random.nextInt(LOADS.length)];
            if (!standing(x, y + bay, 0, PalletRackBlock.loaded(SkylinersRegistry.PALLET_RACK.get(), load, facing))) {
                return;
            }
        }
    }

    /**
     * The workbench corner: shelves of tool cases two high round its corner, facing in, and in the middle of the corner
     * a crafting table, a stool to sit at it and a grindstone beside it, at the first spot round the table that is
     * free: out toward her back, in toward her front, or beside the stool where her skin and the shelves come too close.
     */
    private void workbench(RocketQuarter quarter, int y, int corner) {
        List<BlockPos> run = run(y, wall() - CRATES_IN, TOOLS_FROM, TOOLS_TO);
        for (BlockPos at : run) {
            BlockState tools = ShelfUnitBlock.stocked(SkylinersRegistry.SHELF_UNIT.get(), ShelfUnitBlock.Kind.TOOLS, facingOff(run, at));
            stack(at.getX(), y, at.getZ(), tools, TOOL_SHELVES);
        }
        int middle = RocketQuarter.middle(corner);
        put(quarter.x(middle), y, quarter.z(middle), Blocks.CRAFTING_TABLE.defaultBlockState());
        put(quarter.x(middle - 1), y, quarter.z(middle), SkylinersRegistry.BAR_STOOL.get().defaultBlockState());
        BlockState grindstone = Blocks.GRINDSTONE.defaultBlockState().setValue(GrindstoneBlock.FACE, AttachFace.FLOOR)
            .setValue(GrindstoneBlock.FACING, quarter.side());
        int[][] spots = {{middle, middle + 1}, {middle, middle - 1}, {middle - 1, middle + 1}, {middle - 1, middle - 1}};
        for (int[] spot : spots) {
            if (put(quarter.x(spot[0]), y, quarter.z(spot[1]), grindstone)) {
                return;
            }
        }
    }

    /**
     * Crates piled over a quarter's corner in stacks of one to three, as they were set down, a spot left bare now and
     * then.
     */
    private void pile(RocketQuarter quarter, int y, int corner, RandomSource random) {
        for (BlockPos at : BlockPos.betweenClosed(RocketQuarter.INNER, y, RocketQuarter.INNER, corner, y, corner)) {
            if (random.nextInt(PILE_GAPS) > 0) {
                stack(quarter.x(at.getX()), y, quarter.z(at.getZ()), crate(random), 1 + random.nextInt(STACKED));
            }
        }
    }
}
