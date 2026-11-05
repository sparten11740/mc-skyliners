package dev.skyliners.airframe;

import dev.skyliners.block.AutopilotScreenBlock;
import dev.skyliners.block.LedConsoleBlock;
import dev.skyliners.block.LiverySelectorBlock;
import dev.skyliners.block.PilotSeatBlock;
import dev.skyliners.block.TailCameraDisplayBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where everything on the rocket's flight deck stands, worked out before any of it is laid, so that a seated pilot
 * reaches every instrument: the autopilot's control head facing her front, the commander's seat on its left and the
 * pilot's on its right; a row of legroom ahead of them, then the panel, a flight display dead ahead of each seat and
 * the autopilot screen between them, its blank casing running on ahead to her glass as a glareshield; the LED console
 * at the commander's left hand and the livery selector under the camera display at the pilot's right, the two wing
 * consoles, facing in; jump seats behind the pilots and launch seats at her back glass looking out, where they fit.
 * It only answers where: the layout and its pieces lay the blocks (see {@link RocketLayout}).
 *
 * <p>It is found by search rather than by scale, so it suits every scale and the fractional ones between: the
 * roomiest kind of deck that fits (see {@link Kind}) is laid as far forward as it fits, the head's row walked back
 * from the front of her glass toward the lift until every block it claims is open floor inside her and every seat's
 * back is inside her skin. Nothing it claims is in the lift's booth, in its doorway's way out or at its call button,
 * and the five blocks round the head, either side of it and the three in front, are left open for her controls
 * there. Heights are in the layout's rows, the lowest of everything on the row over the cockpit's floor.
 *
 * @param head where the control head stands; null where not even it fits
 * @param pilots the commander's seat and the pilot's, in that order; none where they do not fit
 * @param flightDisplays the two flight displays in the panel, facing the seats; none without a panel
 * @param screen where the autopilot screen's first part stands, on the left of its bottom row as the seats see it,
 *     facing them; null without a panel
 * @param casing every block of blank panel casing: the panel's ends past the flight displays and the glareshield
 * @param ledConsole where the left wing's bottom row begins at its front end, for the LED console facing her right;
 *     null without one
 * @param cameraWing where the right wing's bottom row begins at its front end, for the livery selector facing her
 *     left with the camera display over it; null without one
 * @param jumpSeats the jump seats behind the pilots, facing her front
 * @param launchSeats the launch seats at her back glass, facing it
 */
record RocketFlightDeck(@Nullable BlockPos head, List<Seat> pilots, List<BlockPos> flightDisplays, @Nullable BlockPos screen,
                        List<BlockPos> casing, @Nullable BlockPos ledConsole, @Nullable BlockPos cameraWing, List<Seat> jumpSeats,
                        List<Seat> launchSeats) {
    /** How far either side of her axis the commander and the pilot sit, beside the control head, in blocks. */
    private static final int PILOTS_OUT = 2;
    /** How far ahead of the pilots' row the panel's row stands, in blocks: a row of legroom between them. */
    private static final int PANEL_AHEAD = 2;
    /** How far either side of her axis the panel's casing ends stand, in blocks: just past the flight displays. */
    private static final int CASING_OUT = PILOTS_OUT + 1;
    /** How far either side of her axis the wing consoles stand, in blocks: a block of floor out from the pilots. */
    private static final int WING_OUT = 4;
    /**
     * How long each wing console is along her, in blocks, and how many rows tall: the right one's camera display
     * stands over its livery selector.
     */
    private static final int LEFT_WING_LENGTH = LedConsoleBlock.CONSOLE_COLUMNS;
    private static final int LEFT_WING_ROWS = LedConsoleBlock.CONSOLE_ROWS;
    private static final int RIGHT_WING_LENGTH = Math.max(LiverySelectorBlock.SELECTOR_COLUMNS, TailCameraDisplayBlock.LARGE_COLUMNS);
    private static final int RIGHT_WING_ROWS = 1 + TailCameraDisplayBlock.LARGE_ROWS;
    /** How far either side of her axis the jump seats stand, and how far behind the pilots' row, in blocks. */
    private static final int JUMP_OUT = 3;
    private static final int JUMP_BEHIND = 2;
    /**
     * Where the launch seats stand across her back, in blocks from her axis, a block of floor between each two: four,
     * or the middle two where the four do not fit.
     */
    private static final int[][] LAUNCH_ROWS = {{-3, -1, 1, 3}, {-1, 1}};
    /** How many rows of walkway behind the lift's booth the launch seats leave clear at least. */
    private static final int WALKWAY = 2;
    /** The walkway round the lift's booth, so many blocks out from her axis: kept clear of jump and launch seats. */
    private static final int RING = RocketLayout.SHAFT + 1;
    /**
     * Where the lift lets out onto the deck, at its doorway, and where its call button is, beside the doorway on its
     * right (see {@link RocketLift}): the row in front of the booth, at these blocks across.
     */
    private static final int LIFT_EXIT_X = 0;
    private static final int CALL_BUTTON_X = 1;
    /**
     * The rearmost row the control head's row may come to: the row in front of the walkway before the lift's doorway.
     * Only the capsule's pilots, a row behind the head, sit on that walkway, either side of the lift's way out: in so
     * small a rocket nothing else stands beside the lift's booth.
     */
    private static final int LAST_ROW = -RING - 1;

    /** A question asked of a block of the cockpit, by its position in the layout. */
    @FunctionalInterface
    interface Cells {
        boolean at(int x, int y, int z);
    }

    /**
     * A seat of the plan, one of the airliner's pilot seats: where its lower half stands, the way whoever sits in it
     * faces, and which armrest has a sidestick (the pilots' outboard ones; none on the others).
     */
    record Seat(BlockPos at, Direction facing, PilotSeatBlock.Stick stick) {
    }

    /**
     * The kinds of flight deck, roomiest first. The panelled ones have the panel, and the wing consoles beginning at
     * its row, or a row further back where her glass curves in too soon for them there; a capsule has no panel, the
     * pilots a row behind the head and the flight displays either side of it, and the wings beside the pilots only
     * where they fit; the last is the head alone.
     */
    private enum Kind {
        /** The pilots beside the head, the panel two rows ahead of them and the wings beginning at its row. */
        ROOMY(true, -PANEL_AHEAD, 0),
        /** As roomy, but the wings beginning a row behind the panel's. */
        COMPACT(true, 1 - PANEL_AHEAD, 0),
        /** The pilots a row behind the head, either side of the lift's way out, and any wings beginning beside them. */
        CAPSULE(false, 1, 1),
        /** The head alone, with no seats and no wings. */
        HEAD(false, 0, -1);

        /** Whether it has the panel, and must have the wings. */
        final boolean panelled;
        /** How many rows behind the head's the wings begin, negative ahead of it. */
        final int wingsBack;
        /** How many rows behind the head's the pilots sit; negative for no seats. */
        final int pilotsBack;

        Kind(boolean panelled, int wingsBack, int pilotsBack) {
            this.panelled = panelled;
            this.wingsBack = wingsBack;
            this.pilotsBack = pilotsBack;
        }
    }

    /**
     * The flight deck on the cockpit's floor at a row, the head's row walked back from the front row given: of the
     * cockpit's blocks, which are open (nothing stands there yet), which lie within her surface, and which lie inside
     * her by a skin's thickness, as a seat's back must.
     */
    static RocketFlightDeck plan(int floor, int front, Cells open, Cells within, Cells inside) {
        Room room = new Room(floor, front, open, within, inside);
        List<Seat> launch = room.launchSeats();
        for (Kind kind : Kind.values()) {
            RocketFlightDeck deck = room.first(kind, launch);
            if (deck != null) {
                return deck;
            }
        }
        return new RocketFlightDeck(null, List.of(), List.of(), null, List.of(), null, null, List.of(), launch);
    }

    /** Every seat of the plan: the pilots', the jump seats and the launch seats. */
    /**
     * Where the self-destruct button stands (see {@link RocketSelfDestruct}): on the floor on the commander's side, in
     * line with the left wing and just behind it; null without that wing.
     */
    @Nullable
    BlockPos selfDestruct() {
        return ledConsole == null ? null : ledConsole.south(LEFT_WING_LENGTH);
    }

    List<Seat> seats() {
        return Stream.of(pilots, jumpSeats, launchSeats).flatMap(List::stream).toList();
    }

    /**
     * What every kind of deck with seats has, before its panel is fitted: the head, the pilots' seats, the blocks each
     * wing would take, front end first, and the jump and launch seats.
     */
    private record Deck(BlockPos head, List<Seat> pilots, List<BlockPos> left, List<BlockPos> right, List<Seat> jump, List<Seat> launch) {
    }

    /**
     * The cockpit the plan is found in: its floor's row, its frontmost row (and as far out either side and back as
     * that is forward of her axis), and what its blocks are.
     */
    private record Room(int floor, int front, Cells open, Cells within, Cells inside) {
        /** The first deck of a kind that fits, walking its row back from the front; null for none. */
        @Nullable
        RocketFlightDeck first(Kind kind, List<Seat> launch) {
            for (int row = front; row <= LAST_ROW; row++) {
                RocketFlightDeck deck = fit(kind, row, launch);
                if (deck != null) {
                    return deck;
                }
            }
            return null;
        }

        /** The deck of a kind with the head on a row across her, if everything it claims fits; null if not. */
        @Nullable
        RocketFlightDeck fit(Kind kind, int row, List<Seat> launch) {
            BlockPos head = at(0, row);
            if (!free(head)) {
                return null;
            }
            if (kind.pilotsBack < 0) {
                return new RocketFlightDeck(head, List.of(), List.of(), null, List.of(), null, null, List.of(), launch);
            }
            int pilotsRow = row + kind.pilotsBack;
            List<Seat> pilots = pilots(pilotsRow);
            if (!pilots.stream().allMatch(this::fits)) {
                return null;
            }
            Deck deck = new Deck(head, pilots, wing(-WING_OUT, row + kind.wingsBack, LEFT_WING_LENGTH, LEFT_WING_ROWS),
                wing(WING_OUT, row + kind.wingsBack, RIGHT_WING_LENGTH, RIGHT_WING_ROWS), jumpSeats(pilotsRow), launch);
            return kind.panelled ? panelled(deck) : capsule(deck);
        }

        /**
         * The panelled deck, if the legroom ahead of the pilots and the head is open and the panel and both wings fit;
         * null if not.
         */
        @Nullable
        private RocketFlightDeck panelled(Deck deck) {
            int panel = deck.head().getZ() - PANEL_AHEAD;
            List<BlockPos> displays = List.of(at(-PILOTS_OUT, panel), at(PILOTS_OUT, panel));
            BlockPos screen = at(-AutopilotScreenBlock.COLUMNS / 2, panel);
            boolean fits = allFree(legroom(deck.head().getZ() - 1)) && allFree(displays) && allFree(screen(screen)) && allFree(deck.left())
                && allFree(deck.right());
            if (!fits) {
                return null;
            }
            return new RocketFlightDeck(deck.head(), deck.pilots(), displays, screen, casing(panel), deck.left().getFirst(), deck.right().getFirst(),
                deck.jump(), deck.launch());
        }

        /**
         * The capsule: the flight displays either side of the head, dead ahead of the pilots, and each wing, where they
         * fit; no panel.
         */
        private RocketFlightDeck capsule(Deck deck) {
            BlockPos head = deck.head();
            List<BlockPos> displays = Stream.of(head.west(PILOTS_OUT), head.east(PILOTS_OUT)).filter(this::free).toList();
            BlockPos left = allFree(deck.left()) ? deck.left().getFirst() : null;
            BlockPos right = allFree(deck.right()) ? deck.right().getFirst() : null;
            return new RocketFlightDeck(head, deck.pilots(), displays, null, List.of(), left, right, deck.jump(), deck.launch());
        }

        /** The commander's seat and the pilot's, either side of her axis on a row, facing her front. */
        private List<Seat> pilots(int row) {
            return List.of(new Seat(at(-PILOTS_OUT, row), Direction.NORTH, PilotSeatBlock.Stick.LEFT),
                new Seat(at(PILOTS_OUT, row), Direction.NORTH, PilotSeatBlock.Stick.RIGHT));
        }

        /** The jump seats behind the pilots' row, facing her front: those that fit, off the walkway round the booth. */
        private List<Seat> jumpSeats(int pilotsRow) {
            int row = pilotsRow + JUMP_BEHIND;
            Stream<Seat> both = Stream.of(-JUMP_OUT, JUMP_OUT).map(x -> new Seat(at(x, row), Direction.NORTH, PilotSeatBlock.Stick.NONE));
            return both.filter(seat -> clearOfRing(seat.at()) && fits(seat) && free(ahead(seat))).toList();
        }

        /**
         * The launch seats along the row furthest back that has room for a row of them, each with its legroom, facing
         * her back glass, behind the walkway round the booth; the middle two where four fit on no row; or none.
         */
        List<Seat> launchSeats() {
            for (int[] across : LAUNCH_ROWS) {
                List<Seat> seats = launchRow(across);
                if (!seats.isEmpty()) {
                    return seats;
                }
            }
            return List.of();
        }

        /** A row of launch seats at these blocks across, on the row furthest back where all of them fit; or none. */
        private List<Seat> launchRow(int[] across) {
            for (int row = -front; row > RocketLayout.SHAFT + WALKWAY; row--) {
                int z = row;
                List<Seat> seats = Arrays.stream(across).mapToObj(x -> new Seat(at(x, z), Direction.SOUTH, PilotSeatBlock.Stick.NONE)).toList();
                if (seats.stream().allMatch(seat -> fits(seat) && free(ahead(seat)))) {
                    return seats;
                }
            }
            return List.of();
        }

        /**
         * The blank casing: the panel's ends past the flight displays, and the glareshield, every block open on the
         * row over the floor ahead of the panel out to her glass.
         */
        private List<BlockPos> casing(int panel) {
            List<BlockPos> casing = new ArrayList<>();
            Stream.of(at(-CASING_OUT, panel), at(CASING_OUT, panel)).filter(this::free).forEach(casing::add);
            int across = 1 - 2 * front;
            for (int block = 0; block < across * (panel - front); block++) {
                BlockPos at = at(block % across + front, panel - 1 - block / across);
                if (free(at)) {
                    casing.add(at);
                }
            }
            return casing;
        }

        /** The legroom ahead of the pilots and the head, on the row over the floor: open in front of all three. */
        private List<BlockPos> legroom(int row) {
            List<BlockPos> legroom = new ArrayList<>();
            for (int x = -PILOTS_OUT; x <= PILOTS_OUT; x++) {
                legroom.add(at(x, row));
            }
            return legroom;
        }

        /** A wing console's blocks, a block across, so long back from its first row and so many rows tall. */
        private List<BlockPos> wing(int x, int first, int length, int rows) {
            List<BlockPos> wing = new ArrayList<>();
            for (int block = 0; block < length * rows; block++) {
                wing.add(new BlockPos(x, floor + 1 + block / length, first + block % length));
            }
            return wing;
        }

        /** The autopilot screen's blocks, from its first part on the left of its bottom row as the seats see it. */
        private static List<BlockPos> screen(BlockPos first) {
            List<BlockPos> blocks = new ArrayList<>();
            for (int part = 0; part < AutopilotScreenBlock.COLUMNS * AutopilotScreenBlock.ROWS; part++) {
                blocks.add(first.offset(part % AutopilotScreenBlock.COLUMNS, part / AutopilotScreenBlock.COLUMNS, 0));
            }
            return blocks;
        }

        /** Whether a seat fits: both its halves free (see {@link #free}), its back inside her skin. */
        private boolean fits(Seat seat) {
            BlockPos back = seat.at().above();
            return free(seat.at()) && free(back) && inside.at(back.getX(), back.getY(), back.getZ());
        }

        /** The block in front of a seat, where its legs go. */
        private static BlockPos ahead(Seat seat) {
            return seat.at().relative(seat.facing());
        }

        private boolean allFree(List<BlockPos> blocks) {
            return blocks.stream().allMatch(this::free);
        }

        /** Whether a block is open, within her surface, and clear of the lift's booth, way out and call button. */
        private boolean free(BlockPos at) {
            return open.at(at.getX(), at.getY(), at.getZ()) && within.at(at.getX(), at.getY(), at.getZ()) && !lift(at.getX(), at.getZ());
        }

        /** Whether a block across and along is the lift's: its booth, or its way out and its call button's place. */
        private static boolean lift(int x, int z) {
            boolean booth = Math.abs(x) <= RocketLayout.SHAFT && Math.abs(z) <= RocketLayout.SHAFT;
            return booth || z == -RING && (x == LIFT_EXIT_X || x == CALL_BUTTON_X);
        }

        /** Whether a block is off the walkway round the lift's booth. */
        private static boolean clearOfRing(BlockPos at) {
            return Math.max(Math.abs(at.getX()), Math.abs(at.getZ())) != RING;
        }

        /** A block on the row over the floor, so far across and along. */
        private BlockPos at(int x, int z) {
            return new BlockPos(x, floor + 1, z);
        }
    }
}
