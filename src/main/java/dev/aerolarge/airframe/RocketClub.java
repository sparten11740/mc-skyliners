package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BarCounterBlock;
import dev.aerolarge.block.CocktailTableBlock;
import dev.aerolarge.block.DisplayShelfBlock;
import dev.aerolarge.block.DrinkTapBlock;
import dev.aerolarge.block.FogMachineBlock;
import dev.aerolarge.block.NeonSignBlock;
import dev.aerolarge.block.OvalWindowBlock;
import dev.aerolarge.block.RobotBartenderBlock;
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
 * The techno club: round the shaft the dance floor's black glass tiles, lit in the beat while the music plays, and past
 * them a plum club carpet out to her skin; speaker stacks two high at the four diagonals facing in, a disco ball over
 * each quarter of the floor, and behind the shaft at her back the DJ deck that plays the room (see
 * {@link TechnoDeckBlock}), facing the floor, the fog machine beside it (see {@link FogMachineBlock}). Along her left
 * side a bar curves round the window there: its counter parting the bar from the floor, two drink taps on it (see
 * {@link DrinkTapBlock}) and stools before it; behind its middle the robot bartender, who sells mocktails (see
 * {@link RobotBartenderBlock}), his back to the window; and behind him either side of the window the back bar, cabinets
 * under two tiers of lit shelves of bottles. At her front right a booth, armchairs round a low table; at her back right
 * two high tables with stools (see {@link CocktailTableBlock}); at her front left, by the bar's end, a coat corner of
 * lockers; and round her wall, over head height, neon signs, a picture each (see {@link NeonSignBlock}). Its windows
 * are built at their darkest, so daylight does not wash out the dance floor.
 */
final class RocketClub extends RocketRoom {
    /** Her techno club's story, from the entry story up. */
    static final int STORY = 6;
    /** How far in from the skin's inside the speakers stand, in blocks. */
    private static final double SPEAKERS_IN = 2.5;
    /** How far out from her axis the DJ deck and the disco balls are, in blocks. */
    private static final double DECK_OUT = 4.5;
    private static final double BALLS_OUT = 5;
    /**
     * How far in from the skin's inside the middle of the bar's straight counter stands, in blocks, how far round from
     * her front it is, in degrees (the window at her left), and how far round either side of it the counter reaches.
     */
    private static final double BAR_IN = 4.4;
    private static final double BAR_MIDDLE = 270;
    private static final double BAR_HALF = 34;
    /** How many tiers of lit shelves of bottles stand on the back bar's cabinets. */
    private static final int BACK_BAR_SHELVES = 2;
    /** How far in from the skin's inside the dance floor's tiles reach round her shaft, in blocks: to the counter. */
    private static final double DANCE_IN = 4;
    /**
     * The neon signs on her wall, each far round from her front, in degrees, and so many rows over the floor, with its
     * picture: a martini glass over the back bar, music notes over the window behind the DJ deck, a lightning bolt over
     * the bar's end, lips over the booth, a planet over the high tables, a rocket over the coat corner; between the
     * wall washers, over the back bar's top shelves and the speakers.
     */
    private static final double[] SIGN_ROUNDS = {255, 180, 225, 45, 135, 315};
    private static final int[] SIGN_UPS = {4, 5, 4, 4, 4, 4};
    private static final NeonSignBlock.Design[] SIGNS = {
        NeonSignBlock.Design.COCKTAIL, NeonSignBlock.Design.NOTE, NeonSignBlock.Design.BOLT, NeonSignBlock.Design.LIPS,
        NeonSignBlock.Design.PLANET, NeonSignBlock.Design.ROCKET,
    };
    /**
     * The high tables at her back right, each far round from her front, in degrees, so far in from the skin's inside,
     * in blocks, and how many stools stand at each.
     */
    private static final double[] TABLE_ROUNDS = {115, 140};
    private static final double TABLE_IN = 3;
    private static final int TABLE_STOOLS = 2;
    /** From and to how far round from her front the coat corner's lockers run, in degrees, past the bar's end. */
    private static final double COATS_FROM = 308;
    private static final double COATS_TO = 340;
    /**
     * How far to her right of the DJ deck the fog machine stands, in blocks, its nozzle out along the floor behind the
     * shaft.
     */
    private static final int FOG_ACROSS = 2;
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
        highTables(y);
        signs();
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

    /**
     * The dance floor's tiles in place of the room's floor round her shaft, out to the bar's counter; past them the
     * floor is left to be finished as the club's carpet.
     */
    private void danceFloor() {
        double out = wall() - DANCE_IN;
        int reach = (int) Math.ceil(out);
        BlockState tile = AerolargeRegistry.DANCE_FLOOR.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor(), -reach, reach, floor(), reach)) {
            boolean floor = layout.stateAt(at.getX(), at.getY(), at.getZ()).is(AerolargeRegistry.CABIN_FLOOR.get());
            if (floor && Math.hypot(at.getX(), at.getZ()) <= out) {
                layout.set(at.getX(), at.getY(), at.getZ(), tile);
            }
        }
    }

    /**
     * The bar, straight across the window at her left: its counter facing in, a stool before every other block of it
     * and a drink tap on it halfway out to either end (see {@link DrinkTapBlock}); behind its middle the robot
     * bartender; and behind the rest, either side of the window, the back bar, its cabinets under two tiers of lit
     * shelves of bottles.
     */
    private void bar(int y) {
        double out = wall() - BAR_IN;
        BlockPos middle = new BlockPos(across(out, BAR_MIDDLE), y, along(out, BAR_MIDDLE));
        int reach = (int) Math.round(out * Math.tan(Math.toRadians(BAR_HALF)));
        Direction facing = inward(BAR_MIDDLE);
        Direction along = facing.getClockWise();
        BlockState counter = AerolargeRegistry.BAR_COUNTER.get().defaultBlockState().setValue(BarCounterBlock.FACING, facing);
        for (int step = -reach; step <= reach; step++) {
            BlockPos at = middle.relative(along, step);
            if (put(at.getX(), y, at.getZ(), counter) && (step + reach) % 2 == 0) {
                put(at.getX() + facing.getStepX(), y, at.getZ() + facing.getStepZ(), AerolargeRegistry.BAR_STOOL.get().defaultBlockState());
            }
        }
        BlockState tap = AerolargeRegistry.DRINK_TAP.get().defaultBlockState().setValue(DrinkTapBlock.FACING, facing);
        for (int side : new int[]{-1, 1}) {
            BlockPos at = middle.relative(along, side * (reach + 1) / 2);
            if (layout.stateAt(at.getX(), y, at.getZ()).is(AerolargeRegistry.BAR_COUNTER.get())) {
                put(at.getX(), y + 1, at.getZ(), tap);
            }
        }
        BlockPos behind = middle.relative(facing.getOpposite());
        bartender(behind, facing);
        backBar(behind, along, reach + 1, facing);
    }

    /**
     * The back bar along the row behind the counter, either side of the bartender out to so far, wherever the room
     * leaves the floor free: not before the window.
     */
    private void backBar(BlockPos behind, Direction along, int reach, Direction facing) {
        BlockState shelf = AerolargeRegistry.BACK_BAR.get().defaultBlockState().setValue(DisplayShelfBlock.FACING, facing);
        for (int step = -reach; step <= reach; step++) {
            BlockPos at = behind.relative(along, step);
            if (step != 0 && put(at.getX(), at.getY(), at.getZ(), shelf.setValue(DisplayShelfBlock.TIER, DisplayShelfBlock.Tier.CABINET))) {
                stack(at.getX(), at.getY() + 1, at.getZ(), shelf.setValue(DisplayShelfBlock.TIER, DisplayShelfBlock.Tier.SHELVES), BACK_BAR_SHELVES);
            }
        }
    }

    /**
     * The high tables at her back right, round her floor's edge, a stool or two beside each where there is room (see
     * {@link CocktailTableBlock}).
     */
    private void highTables(int y) {
        double out = wall() - TABLE_IN;
        for (double round : TABLE_ROUNDS) {
            int x = across(out, round);
            int z = along(out, round);
            if (put(x, y, z, AerolargeRegistry.COCKTAIL_TABLE.get().defaultBlockState())) {
                stools(x, y, z);
            }
        }
    }

    /** Stools beside a high table, as many as stand at one, on the first sides of it with room. */
    private void stools(int x, int y, int z) {
        int stood = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (stood < TABLE_STOOLS && put(x + side.getStepX(), y, z + side.getStepZ(), AerolargeRegistry.BAR_STOOL.get().defaultBlockState())) {
                stood++;
            }
        }
    }

    /**
     * The neon signs, each fixed to her skin over head height where it can be (see {@link NeonSignBlock} and
     * {@link SkinMount}), lit; none in the row of the LED strip along the ceiling, or over it.
     */
    private void signs() {
        for (int sign = 0; sign < SIGN_ROUNDS.length; sign++) {
            int y = floor() + SIGN_UPS[sign];
            SkinMount mount = y < top() ? SkinMount.find(layout, SIGN_ROUNDS[sign], y, 1, inward(SIGN_ROUNDS[sign])) : null;
            if (mount != null) {
                layout.set(mount.at().getX(), y, mount.at().getZ(), AerolargeRegistry.NEON_SIGN.get().defaultBlockState()
                    .setValue(NeonSignBlock.FACING, mount.facing()).setValue(NeonSignBlock.DESIGN, SIGNS[sign]));
            }
        }
    }

    /**
     * The robot bartender behind the counter's middle, two blocks tall, facing across it (see
     * {@link RobotBartenderBlock}): with his back to the window there, where the room keeps the floor clear before a
     * window, since behind the counter there is nowhere else for him, the stars out of the window behind him.
     */
    private void bartender(BlockPos at, Direction facing) {
        int x = at.getX();
        int y = at.getY();
        int z = at.getZ();
        boolean room = inRoom(x, y, z, 0) && layout.isOpen(x, y, z) && layout.isOpen(x, y + 1, z);
        if (room && offTheWays(x, z, 0)) {
            BlockState lower = RobotBartenderBlock.lower(AerolargeRegistry.ROBOT_BARTENDER.get(), facing);
            layout.set(x, y, z, lower);
            layout.set(x, y + 1, z, RobotBartenderBlock.upper(lower));
        }
    }

    /**
     * The booth in a quarter's corner by the walkway: a low table with an armchair on each side of it that has room,
     * facing it.
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
