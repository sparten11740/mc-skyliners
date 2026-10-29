package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BarCounterBlock;
import dev.aerolarge.block.DisplayShelfBlock;
import dev.aerolarge.block.FogMachineBlock;
import dev.aerolarge.block.OvalWindowBlock;
import dev.aerolarge.block.ShelfUnitBlock;
import dev.aerolarge.block.SpeakerBlock;
import dev.aerolarge.block.TechnoDeckBlock;
import dev.aerolarge.block.TechnoDeckBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The techno club: its floor laid all over with the dance floor's black glass tiles round the shaft, speaker stacks two
 * high at the four diagonals facing in, a disco ball over each quarter of the floor, and behind the shaft at her back
 * the DJ deck that plays the room (see {@link TechnoDeckBlock}), facing the floor, the fog machine beside it (see
 * {@link FogMachineBlock}). Along her left side a bar curves round the window there, its counter parting the bar from
 * the floor, stools before it and the back bar's lit shelves of bottles behind it either side of the window. At her
 * front right a booth: armchairs round a low table, walled off from the way in by shelves of records; at her front
 * left, by the bar's end, a coat corner of lockers. Its windows are built at their darkest, so daylight does not wash
 * out the dance floor.
 */
final class RocketClub extends RocketRoom {
    /** Her techno club's story, from the entry story up. */
    static final int STORY = 6;
    /** How far in from the skin's inside the speakers stand, in blocks. */
    private static final double SPEAKERS_IN = 2.5;
    /** How far out from her axis the DJ deck and the disco balls are, in blocks. */
    private static final double DECK_OUT = 4.5;
    private static final double BALLS_OUT = 5;
    /** How far in from the skin's inside the bar's counter runs, and from and to how far round from her front. */
    private static final double BAR_IN = 3.5;
    private static final double BAR_FROM = 236;
    private static final double BAR_TO = 304;
    /** How far in from the skin's inside the back bar stands behind the counter, in blocks. */
    private static final double BACK_BAR_IN = 2.5;
    /** From and to how far round from her front the coat corner's lockers run, in degrees, past the bar's end. */
    private static final double COATS_FROM = 308;
    private static final double COATS_TO = 340;
    /**
     * How far to her right of the DJ deck the fog machine stands, in blocks, its nozzle out along the floor behind the
     * shaft.
     */
    private static final int FOG_ACROSS = 2;
    /** How far from the booth's table toward the way in its wall of records stands, in blocks, and how high. */
    private static final int BOOTH_WALL = 2;
    private static final int RECORDS_HIGH = 2;
    /** The booth's armchairs' colours, a chair's picked at random. */
    private static final DyeColor[] BOOTH = {DyeColor.PURPLE, DyeColor.MAGENTA};

    RocketClub(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        RandomSource random = scatter();
        danceFloor();
        double out = wall() - SPEAKERS_IN;
        BlockState speaker = AerolargeRegistry.SPEAKER.get().defaultBlockState();
        for (double round = 45; round < 360; round += 90) {
            put(across(out, round), y, along(out, round), speaker.setValue(SpeakerBlock.FACING, inward(round)));
            put(across(out, round), y + 1, along(out, round), speaker.setValue(SpeakerBlock.FACING, inward(round)));
            put(across(BALLS_OUT, round), top(), along(BALLS_OUT, round), AerolargeRegistry.DISCO_BALL.get().defaultBlockState());
        }
        bar(y);
        deck(new BlockPos(0, y, (int) Math.round(DECK_OUT)));
        put(FOG_ACROSS, y, (int) Math.round(DECK_OUT),
            AerolargeRegistry.FOG_MACHINE.get().defaultBlockState().setValue(FogMachineBlock.FACING, Direction.EAST));
        booth(RocketQuarter.FRONT_RIGHT, y, random);
        coats(y);
        darken();
    }

    /** Its windows at their darkest, so that daylight does not wash out the dance floor's colours. */
    private void darken() {
        int reach = layout.reach();
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor() + 1, -reach, reach, top(), reach)) {
            BlockState state = layout.stateAt(at.getX(), at.getY(), at.getZ());
            if (state.is(AerolargeRegistry.OVAL_WINDOW.get())) {
                layout.set(at.getX(), at.getY(), at.getZ(), state.setValue(OvalWindowBlock.DIM, OvalWindowBlock.DARKEST));
            }
        }
    }

    /** The dance floor's tiles in place of every block of the room's floor. */
    private void danceFloor() {
        int reach = (int) Math.ceil(wall());
        BlockState tile = AerolargeRegistry.DANCE_FLOOR.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor(), -reach, reach, floor(), reach)) {
            if (layout.stateAt(at.getX(), at.getY(), at.getZ()).is(AerolargeRegistry.CABIN_FLOOR.get())) {
                layout.set(at.getX(), at.getY(), at.getZ(), tile);
            }
        }
    }

    /**
     * The bar: its counter in a curve round her left side, facing in, a stool before every other block of it; and
     * behind it, against the skin where the window leaves room, the back bar, its cabinet under its lit shelves.
     */
    private void bar(int y) {
        double out = wall() - BAR_IN;
        int stool = 0;
        for (BlockPos at : run(y, out, BAR_FROM, BAR_TO)) {
            Direction facing = inward(roundOf(at.getX(), at.getZ()));
            if (put(at.getX(), y, at.getZ(), AerolargeRegistry.BAR_COUNTER.get().defaultBlockState().setValue(BarCounterBlock.FACING, facing))
                && stool++ % 2 == 0) {
                put(at.getX() + facing.getStepX(), y, at.getZ() + facing.getStepZ(), AerolargeRegistry.BAR_STOOL.get().defaultBlockState());
            }
        }
        List<BlockPos> back = run(y, wall() - BACK_BAR_IN, BAR_FROM, BAR_TO);
        for (BlockPos at : back) {
            BlockState shelf = AerolargeRegistry.BACK_BAR.get().defaultBlockState().setValue(DisplayShelfBlock.FACING, facingOff(back, at));
            if (put(at.getX(), y, at.getZ(), shelf.setValue(DisplayShelfBlock.TIER, DisplayShelfBlock.Tier.CABINET))) {
                put(at.getX(), y + 1, at.getZ(), shelf.setValue(DisplayShelfBlock.TIER, DisplayShelfBlock.Tier.SHELVES));
            }
        }
    }

    /**
     * The booth in a quarter's corner by the walkway: a low table with an armchair on each side of it that has room,
     * facing it, and between it and the way in a wall of record shelves, facing in.
     */
    private void booth(RocketQuarter quarter, int y, RandomSource random) {
        int table = RocketQuarter.INNER;
        if (!put(quarter.x(table), y, quarter.z(table), AerolargeRegistry.LOUNGE_TABLE.get().defaultBlockState())) {
            return;
        }
        Direction side = quarter.side();
        Direction end = quarter.end();
        seat(quarter.x(table - 1), y, quarter.z(table), side, BOOTH[random.nextInt(BOOTH.length)]);
        seat(quarter.x(table + 1), y, quarter.z(table), side.getOpposite(), BOOTH[random.nextInt(BOOTH.length)]);
        seat(quarter.x(table), y, quarter.z(table - 1), end, BOOTH[random.nextInt(BOOTH.length)]);
        seat(quarter.x(table), y, quarter.z(table + 1), end.getOpposite(), BOOTH[random.nextInt(BOOTH.length)]);
        BlockState records = ShelfUnitBlock.stocked(AerolargeRegistry.SHELF_UNIT.get(), ShelfUnitBlock.Kind.RECORDS, side);
        for (int along = table; along <= table + 1; along++) {
            stack(quarter.x(table - BOOTH_WALL), y, quarter.z(along), records, RECORDS_HIGH);
        }
    }

    /** The coat corner's lockers round her front left past the bar's end, facing in, a space helmet in each. */
    private void coats(int y) {
        List<BlockPos> run = run(y, wall() - SPEAKERS_IN, COATS_FROM, COATS_TO);
        for (BlockPos at : run) {
            crewLocker(at.getX(), y, at.getZ(), facingOff(run, at));
        }
    }

    /** The DJ deck at a block behind the shaft, facing the front, and the room it plays: the whole story. */
    private void deck(BlockPos deck) {
        if (!put(deck.getX(), deck.getY(), deck.getZ(), AerolargeRegistry.TECHNO_DECK.get().defaultBlockState().setValue(TechnoDeckBlock.FACING, Direction.NORTH))) {
            return;
        }
        int reach = (int) Math.ceil(wall());
        BlockPos from = new BlockPos(-reach, floor() + 1, -reach).subtract(deck);
        BlockPos to = new BlockPos(reach, top(), reach).subtract(deck);
        layout.fit(deck, (blockEntity, rotation) -> {
            if (blockEntity instanceof TechnoDeckBlockEntity techno) {
                techno.room(from.rotate(rotation), to.rotate(rotation));
            }
        });
    }
}
