package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.ZeppelinPilot;
import dev.aerolarge.block.BoardingStairsBlockEntity;
import dev.aerolarge.block.CabinRailBlock;
import dev.aerolarge.block.CabinStairsBlock;
import dev.aerolarge.block.PropellerBlock;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.ShellGeometry;
import dev.aerolarge.block.ThrustRelayBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The zeppelin cut into blocks before it is put anywhere, drawn nose north as the airliner's layout is (see
 * {@link Layout}), its hull at its size's blocks a metre (see {@link ZeppelinSize}) and everything a player uses at
 * a player's size. Only its blocks are kept: the air inside its hull, over a million blocks at full size, is worked
 * out from its shape as it is handed over, and never held.
 *
 * <p>Its hull is a silver fabric cover over seventeen rings of blue girders, cut to its curve block by block in the
 * snapped style ({@link ShellGeometry#snapped}), from where each block's corners lie against the surface. Under the
 * crown of each of the sixteen bays between the rings hangs a gas cell, a balloon of Create Aeronautics' envelope in a
 * colour of its own, its hot air burners standing in it on posts; the zeppelin floats on their hot air (see the
 * README's How it flies). Below the cells an axial gangway runs nearly the whole length of the hull, the passenger
 * decks lie in its belly with promenade windows down both sides, the control car hangs under the bow (see
 * {@link ZeppelinControlCar}), the four engine cars stand out on struts on the flanks, and four fins in a cross carry
 * the tail. Once the hull stands, the features laid into it, each a piece of its own (see {@link ZeppelinPiece}):
 * a rope from the gangway to a deck on the crown (see {@link ZeppelinSkyDeck}), another into the middle cell's hot air
 * (see {@link ZeppelinCellGallery}), and on a furnished zeppelin the furniture (see {@link ZeppelinDecks}).
 *
 * <p>Sable flies the zeppelin as one body only as far as its blocks touch, face to face: anything standing free
 * inside it would fly off on its own. So the cells hang from the crown, the burners stand on posts down to the cells'
 * floors, the gangway stands on posts down to the rings, and the decks' floors run out to the cover.
 */
public final class ZeppelinLayout implements Layout {
    /**
     * The gas cells' colours, bow to stern: the rainbow, then the pale colours and the dark ones, so the cells are
     * told apart by their colour alone, as the way to a cell is told ("the green balloon").
     */
    static final String[] CELL_COLOURS = {
        "red", "orange", "yellow", "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink", "white", "light_gray",
        "gray", "brown", "black"
    };
    /**
     * How many burners stand in the biggest gas cell, in two rows across its floor; smaller cells, toward either end,
     * have fewer, by their floor's area, and at least {@link #LEAST_BURNERS}. Each burner lifts as much as its
     * relay asks, up to 750 kpg at sea level. Twelve in the biggest give the zeppelin twice the lift that floats it
     * at sea level, enough to fly high over the hills of an ordinary world and still be far from full; with six it
     * could barely climb 60 blocks over the sea, at full burn.
     */
    private static final int MOST_BURNERS = 12;
    private static final int LEAST_BURNERS = 2;
    /**
     * How far apart the burners stand across the cell, in blocks: a relay between neighbours and open gas round each.
     */
    private static final int BURNER_SPACING = 3;
    /** How far fore and aft of the cell's middle its two rows of burners stand, in blocks. */
    private static final int BURNER_STAGGER = 2;
    /** How many rows of the cell's floor the burners stand over its lowest point, so they stand in a broad layer. */
    private static final int BURNER_RISE = 2;
    /**
     * The signal the burners are built at, out of 15: enough to keep their pilot flames lit, so each balloon is found.
     */
    private static final int PILOT_FLAME = 1;
    /** How far apart along a cell the girders it hangs from stand, in blocks. */
    private static final int HANGER_SPACING = 6;
    /**
     * How far across, in blocks, a block may lie outside the hull's radius there, or inside it, and still be looked at
     * for the cover: past either, it is wholly outside or inside.
     */
    private static final double COVER_BAND_OUT = 2.5;
    private static final double COVER_BAND_IN = 3.5;
    /** How far in from the cover the rings stand, in blocks, and how thick they are. */
    private static final double RING_INSET = 1;
    private static final double RING_DEPTH = 2;
    /** Rows between the lamps along the gangway and the decks. */
    private static final int LAMP_SPACING = 6;
    /** How far either side of the middle the gangway's catwalk reaches, in blocks. */
    private static final int GANGWAY_HALF_WIDTH = 1;
    /**
     * How far in from the cover's surface the decks' floors end, in blocks: right up against the cover, so the decks
     * are held to it; and how tall a room is, in blocks.
     */
    private static final double DECK_INSET = 0.5;
    private static final int ROOM_HEIGHT = 5;
    /** How many blocks of headroom a flight of stairs keeps over each step. */
    private static final int HEADROOM = 3;
    /**
     * The stairs from the lower deck to the upper: how far to starboard they stand, and how many rows ahead of the
     * decks' middle they start, climbing forward; the flight on up to the gangway, how many rows aft of the middle it
     * starts, climbing aft, beside the gangway. Both are two blocks wide.
     */
    private static final int LOWER_FLIGHT_ACROSS = 2;
    private static final int LOWER_FLIGHT_AHEAD = 4;
    private static final int UPPER_FLIGHT_AFT = 2;
    private static final int FLIGHT_WIDTH = 2;
    /**
     * The boarding stairs up through the belly: how far either side of the middle, and how many rows into the decks
     * they start.
     */
    private static final int BOARDING_ACROSS = 3;
    private static final int BOARDING_AFT = 6;
    /**
     * How square to the side the cover has to face for a promenade window there: the sideways share of its outward
     * direction.
     */
    private static final double WINDOW_FACING = 0.6;
    /** How many rows over the control car's floor its windows start. */
    private static final int CAR_SILL = 2;
    /**
     * The boarding stairs' call button, on the outside of the control car's port wall, within reach from the ground:
     * how many rows aft of the car's front, and how many over its floor.
     */
    private static final int CALL_BUTTON_AFT = 4;
    private static final int CALL_BUTTON_UP = 1;
    /**
     * The control car, at every size: how many rows long it is from its front wall to its back wall, how far either
     * side of the middle its walls stand, its floor's row over the ground, how many rows tall it is inside, and the row
     * of its roof, which the hull is raised to clear (see {@link ZeppelinSize#axis}).
     */
    static final int CAR_ROWS = 28;
    private static final int CAR_HALF = 5;
    private static final int CAR_FLOOR_ROW = 1;
    private static final int CAR_ROOM = 6;
    static final int CAR_ROOF = CAR_FLOOR_ROW + CAR_ROOM + 1;
    /** How far either side of the middle the control car's outer rows of ceiling lights run, in blocks. */
    private static final int CAR_LAMP_OUT = 3;
    /**
     * The passenger decks, at every size: how many rows long they are at most, and the lower and upper decks' floors,
     * in rows over the hull's lowest row along them; a hull too shallow for two has one, its floor so many rows up. They
     * begin a couple of rows aft of the control car at least, and end where the hull has narrowed to a share of its
     * full radius, at the latest.
     */
    private static final int DECK_ROWS = 80;
    private static final int LOWER_DECK_ROWS = 2;
    private static final int UPPER_DECK_ROWS = 8;
    private static final int SINGLE_DECK_ROWS = 3;
    private static final int DECKS_AFT_OF_CAR = 2;
    private static final double DECKS_NARROWEST = 0.95;
    /** The most rows the fairing over the control car climbs to meet the cover, a bound on its search. */
    private static final int FAIRING_HIGHEST = 16;
    /** An engine car's pod: how many rows its nose rounds over, and how thick its skin is, in blocks. */
    private static final double POD_NOSE = 4;
    private static final double POD_SKIN = 1.2;
    /**
     * An engine car's pod, at every size: its radius, how many rows it reaches fore and aft of its station, and how
     * far out from the hull's side it stands, in blocks.
     */
    private static final double POD_RADIUS = 3.2;
    private static final int POD_HALF_LENGTH = 9;
    private static final double POD_STANDOFF = 8;
    /**
     * The engine cars' thrusters: those pushing ahead stand round the propeller's hub at the pod's tail, as offsets
     * across and up from its middle; those pushing astern stand in its nose.
     */
    private static final int[][] AHEAD_THRUSTERS = {{-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1}};
    private static final int[][] ASTERN_THRUSTERS = {{-1, 0}, {1, 0}};

    /**
     * The features laid once the hull stands, in this order, each where the zeppelin's options want it (see
     * {@link ZeppelinPiece}): taking one out of the zeppelin is taking out its line here, and its class.
     */
    private static final List<Function<ZeppelinLayout, ZeppelinPiece>> PIECES = List.of(
        ZeppelinSkyDeck::new,
        ZeppelinSkyDeckLaunch::new,
        ZeppelinCellGallery::new,
        ZeppelinDecks::new,
        ZeppelinLights::new,
        ZeppelinHornCord::new,
        ZeppelinLiveryBoard::new,
        ZeppelinFestive::new,
        ZeppelinColours::new,
        ZeppelinChristening::new,
        ZeppelinFlankScreen::new,
        ZeppelinEngineRooms::new,
        ZeppelinTrimCar::new,
        ZeppelinTechnoRoom::new,
        ZeppelinLookout::new,
        ZeppelinUpShip::new,
        ZeppelinScreens::new,
        ZeppelinSearchlights::new,
        ZeppelinFloodlights::new,
        ZeppelinSkywriting::new);

    private final AirframePalette.Balloon palette;
    /** What it is built with: furnished or not, and the livery it is painted in, and its cells coloured by. */
    private final ZeppelinOptions options;
    /** Every block of the zeppelin that is not the air inside its hull, by position relative to the nose's tip. */
    private final Map<BlockPos, BlockState> cells = new HashMap<>();
    private final Map<BlockPos, Fitting> fittings = new HashMap<>();
    /** Each gas cell's balloon, bow first, worked out once: the layout looks into them millions of times. */
    private final List<ZeppelinShape.Cell> balloons = new ArrayList<>();
    /** Each gas cell's burners' relays, bow first, and the most hot air they give together, at full signal, in m³. */
    private final List<List<BlockPos>> burnerRelays = new ArrayList<>();
    private final List<Double> cellGas = new ArrayList<>();
    /**
     * Each engine car's relays, ahead and astern, by its engine's number less one (1 and 2 to port, 3 and 4 to
     * starboard).
     */
    private final Map<Integer, List<BlockPos>> aheadRelays = new HashMap<>();
    private final Map<Integer, List<BlockPos>> asternRelays = new HashMap<>();
    /** Each engine car's pod's middle, on its axis, by its engine's number less one. */
    private final List<BlockPos> engineCars = new ArrayList<>();
    /** The hull's blocks a metre, as its size has it. */
    private final double scale;
    private final int length;
    private final int axis;
    /** How high the hull's axis stands over the ground, in metres, as the shape measures it. */
    private final double axisHeight;
    /**
     * The rows the passenger decks begin and end at along the hull, the hull's lowest row along them, and whether it
     * is deep enough there for two decks, worked out once: the cover asks after them for every block it lays.
     */
    private final int deckFront;
    private final int deckBack;
    private final int deckBase;
    private final boolean twoDecks;
    /** The row the axial gangway's catwalk is laid in. */
    private final int gangway;
    /** The hull's radius at each row along it, in blocks, from the row before the nose to the one after the stern. */
    private final double[] radii;
    /** Where the stairs up from the decks reach the gangway, a row along it, once they are laid. */
    private int stairsTop;
    /** The control car's box, once it is laid. */
    private Car car;
    /** How many blocks of air {@link #inOrder} hands over, found once everything else stands. */
    private int air = -1;

    private ZeppelinLayout(AirframePalette.Balloon palette, ZeppelinOptions options) {
        this.palette = palette;
        this.options = options;
        this.scale = options.size().scale();
        this.length = (int) Math.ceil(LENGTH * scale);
        this.axis = options.size().axis();
        this.axisHeight = axis / scale;
        this.radii = new double[length + 4];
        for (int z = -2; z <= length + 1; z++) {
            radii[z + 2] = radius((z + 0.5) / scale) * scale;
        }
        int carBack = (int) Math.round(CAR_FRONT * scale) + CAR_ROWS;
        this.deckFront = Math.max((int) Math.ceil(DECKS_FRONT * scale), carBack + DECKS_AFT_OF_CAR);
        int back = deckFront;
        while (back < deckFront + DECK_ROWS && radiusAt(back + 1) >= DECKS_NARROWEST * RADIUS * scale) {
            back++;
        }
        this.deckBack = back;
        this.deckBase = (int) Math.round(ZeppelinShape.belly((deckFront + deckBack) / 2.0 / scale, axisHeight) * scale);
        this.twoDecks = deckBase + UPPER_DECK_ROWS + ROOM_HEIGHT + 1 < axis - 1;
        this.gangway = twoDecks ? axis - 1 : deckBase + SINGLE_DECK_ROWS + ROOM_HEIGHT + 2;
    }

    /**
     * The finished layout, built as the options ask: its lowest blocks (the control car's wheel and the lower fin's)
     * at y 0, its nose at z 0. The hull first, then its pieces.
     */
    public static ZeppelinLayout create(ZeppelinOptions options) {
        ZeppelinLayout layout = new ZeppelinLayout(AirframePalette.balloon(), options);
        layout.cover();
        layout.rings();
        layout.gasCells();
        layout.controlCar();
        layout.decks();
        layout.gangway();
        for (int engine = 0; engine < 4; engine++) {
            layout.engineCar(engine);
        }
        layout.fins();
        for (Function<ZeppelinLayout, ZeppelinPiece> kind : PIECES) {
            ZeppelinPiece piece = kind.apply(layout);
            if (piece.fits(options.size()) && piece.wanted(options)) {
                piece.build();
            }
        }
        layout.countAir();
        return layout;
    }

    /** Whether a block's middle lies inside the hull. */
    boolean inHull(int x, int y, int z) {
        return ZeppelinShape.inHull(x / scale, (y + 0.5) / scale, (z + 0.5) / scale, axisHeight);
    }

    /** How far a block's middle lies outside the hull's surface, in blocks, negative inside. */
    double depthAt(int x, int y, int z) {
        return ZeppelinShape.depth(x / scale, (y + 0.5) / scale, (z + 0.5) / scale, axisHeight) * scale;
    }

    void set(int x, int y, int z, BlockState state) {
        cells.put(new BlockPos(x, y, z), state);
    }

    BlockState stateAt(int x, int y, int z) {
        return cells.getOrDefault(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
    }

    /** Whether nothing of the zeppelin stands at the block yet: inside the hull, its open air. */
    boolean isOpen(int x, int y, int z) {
        return !cells.containsKey(new BlockPos(x, y, z));
    }

    /**
     * How far out to starboard the outermost open block on a deck is, at a row and a station along the hull: inside
     * the hull, with the floor under it; 0 where there is none. The hull is the same to port.
     */
    int wallX(int y, int z) {
        for (int x = reach(); x > 0; x--) {
            if (isOpen(x, y, z) && inHull(x, y, z) && !isOpen(x, y - 1, z)) {
                return x;
            }
        }
        return 0;
    }

    void clear(int x, int y, int z) {
        cells.remove(new BlockPos(x, y, z));
    }

    /** The hull's radius at a row along it, in blocks; 0 past either end. */
    double radiusAt(int z) {
        return z < -2 || z > length + 1 ? 0 : radii[z + 2];
    }

    @FunctionalInterface
    private interface Cell {
        void at(int x, int y, int z);
    }

    /** Every block of a box of the layout, from one corner to the other, both included. */
    private static void box(int x0, int y0, int z0, int x1, int y1, int z1, Cell cell) {
        for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            cell.at(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /** How far out from the axis the hull reaches at its widest, in blocks, and a block more. */
    private int reach() {
        return (int) Math.ceil(RADIUS * scale) + 1;
    }

    /**
     * The cover: every block the hull's surface passes through, cut by it in the snapped style, so two blocks sharing
     * a side meet without a step. A block keeps what lies within the surface of it; where its corners make a pattern
     * no flat surface makes, it is cut by the surface's plane through its middle instead. Along the upper deck's sides,
     * where the cover faces out sideways, it is glass: the promenade windows.
     */
    private void cover() {
        int reach = reach() + 1;
        box(-reach, axis - reach, -1, reach, axis + reach, length, this::coverAt);
    }

    /**
     * The cover's block at a place, if the surface passes through it. Only blocks near the hull's radius at their row
     * or the rows either side are looked at: at the blunt nose the surface faces forward, so the tip's first row is
     * cut by it right on the axis, though the hull there is already some four blocks across.
     */
    private void coverAt(int x, int y, int z) {
        double across = Math.hypot(x, y + 0.5 - axis);
        double widest = Math.max(radiusAt(z - 1), Math.max(radiusAt(z), radiusAt(z + 1)));
        double narrowest = Math.min(radiusAt(z - 1), Math.min(radiusAt(z), radiusAt(z + 1)));
        if (across > widest + COVER_BAND_OUT || across < narrowest - COVER_BAND_IN) {
            return;
        }
        int shape = coverShape(x, y, z);
        if (shape != ShellGeometry.NONE) {
            BlockState skin = promenade(x, y, z) ? AerolargeRegistry.HULL_GLASS.get().defaultBlockState()
                : AerolargeRegistry.ZEPPELIN_COVER.get().defaultBlockState();
            set(x, y, z, ShellBlock.withShape(skin, shape));
        }
    }

    /**
     * The shape the cover cuts a block to, or {@link ShellGeometry#NONE} for a block wholly inside or outside it. Its
     * corners (corner {@code x + 2y + 4z} of the block, as {@link ShellGeometry} counts them) lie inside, on or
     * outside the surface; a block with all of them inside is the hull's open space.
     */
    private int coverShape(int x, int y, int z) {
        int[] corners = new int[8];
        boolean within = false;
        boolean inside = true;
        for (int corner = 0; corner < 8; corner++) {
            corners[corner] = corner(x - 0.5 + (corner & 1), y + (corner >> 1 & 1), z + (corner >> 2 & 1));
            within |= corners[corner] != ShellGeometry.OUTSIDE;
            inside &= corners[corner] == ShellGeometry.INSIDE;
        }
        if (!within || inside) {
            return ShellGeometry.NONE;
        }
        int shape = ShellGeometry.snapped(corners);
        if (shape != ShellGeometry.UNMATCHED) {
            return shape;
        }
        double[] out = outward(x / scale, (y + 0.5) / scale, (z + 0.5) / scale, axisHeight);
        double depth = depthAt(x, y, z);
        return ShellGeometry.cut(out[0], out[1], out[2], 0.5 - out[0] * depth, 0.5 - out[1] * depth, 0.5 - out[2] * depth);
    }

    /** Where a block's corner, in blocks, lies against the hull's surface. */
    private int corner(double x, double y, double z) {
        double depth = ZeppelinShape.depth(x / scale, y / scale, z / scale, axisHeight) * scale;
        if (depth < -ShellGeometry.ON_SURFACE) {
            return ShellGeometry.INSIDE;
        }
        return depth > ShellGeometry.ON_SURFACE ? ShellGeometry.OUTSIDE : ShellGeometry.ON;
    }

    /** Whether the cover is a promenade window here: along the upper deck, where the cover faces out to the side. */
    private boolean promenade(int x, int y, int z) {
        int floor = upperDeckRow();
        if (y <= floor || y > floor + ROOM_HEIGHT - 1 || z < deckFront() || z > deckBack()) {
            return false;
        }
        double[] out = outward(x / scale, (y + 0.5) / scale, (z + 0.5) / scale, axisHeight);
        return Math.abs(out[0]) >= WINDOW_FACING;
    }

    /**
     * The main rings: a band of blue girders just inside the cover at each ring's station, the frames the cells hang
     * between.
     */
    private void rings() {
        for (int ring = 0; ring <= CELLS; ring++) {
            int z = (int) Math.floor(ringAt(ring) * scale);
            int reach = (int) Math.ceil(radiusAt(z)) + 1;
            box(-reach, axis - reach, z, reach, axis + reach, z, this::ringBlockAt);
        }
    }

    private void ringBlockAt(int x, int y, int z) {
        double depth = depthAt(x, y, z);
        if (depth < -RING_INSET && depth >= -RING_INSET - RING_DEPTH && isOpen(x, y, z)) {
            set(x, y, z, AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
    }

    /**
     * The gas cells: each a balloon of envelope in its own colour, every block of the ellipsoid that touches its
     * outside (see {@link #onCellWall}), hollow within, hung from the crown (see {@link #hangers}); on its floor, a
     * little over the lowest point, its burners in two rows across the middle, each with a relay beside it giving it
     * the signal it burns at (see {@link ThrustRelayBlock}), its pilot flame lit to start with. Nothing else stands
     * within a cell but the gallery in one of them, which never changes, its rope up through an open mouth in that
     * cell's floor, over which the hot air stays put (see {@link ZeppelinCellGallery}); and nothing that changes
     * ever stands in a cell's walls, so Aeronautics finds each balloon once and never again.
     */
    private void gasCells() {
        for (int cell = 0; cell < CELLS; cell++) {
            balloons.add(ZeppelinShape.Cell.of(cell, axisHeight, options.size().cells()));
        }
        for (int cell = 0; cell < CELLS; cell++) {
            burnerRelays.add(gasCell(cell));
        }
    }

    /**
     * The box round a gas cell, in blocks: its first and last rows along the hull, how far across it reaches, and its
     * lowest and highest rows.
     */
    private record CellBox(int front, int back, int across, int low, int high) {
    }

    private CellBox cellBox(int cell) {
        ZeppelinShape.Cell balloon = balloons.get(cell);
        return new CellBox((int) Math.floor((balloon.middle() - balloon.halfLength()) * scale) - 1,
            (int) Math.ceil((balloon.middle() + balloon.halfLength()) * scale) + 1, (int) Math.ceil(balloon.halfWidth() * scale) + 1,
            (int) Math.floor((balloon.centreY() - balloon.halfHeight()) * scale) - 1, (int) Math.ceil((balloon.centreY() + balloon.halfHeight()) * scale) + 1);
    }

    private List<BlockPos> gasCell(int cell) {
        String[] colours = options.livery().cellColours();
        BlockState envelope = palette.envelope(colours[cell % colours.length]);
        CellBox around = cellBox(cell);
        box(-around.across(), around.low(), around.front(), around.across(), around.high(), around.back(), (x, y, z) -> envelopeAt(cell, envelope, x, y, z));
        hangers(cell);
        int middle = (int) Math.floor(balloons.get(cell).middle() * scale);
        int bottom = around.low();
        while (bottom < around.high() && !inCell(cell, 0, bottom, middle)) {
            bottom++;
        }
        return burners(cell, bottom + BURNER_RISE);
    }

    /** How much hot air a cell holds, in m³: its open gas, inside its envelope. */
    private int gasHeld(int cell) {
        CellBox around = cellBox(cell);
        int[] held = {0};
        box(-around.across(), around.low(), around.front(), around.across(), around.high(), around.back(), (x, y, z) -> held[0] += inGas(cell, x, y, z) ? 1 : 0);
        return held[0];
    }

    private void envelopeAt(int cell, BlockState envelope, int x, int y, int z) {
        if (inCell(cell, x, y, z) && onCellWall(cell, x, y, z)) {
            set(x, y, z, envelope);
        }
    }

    boolean inCell(int cell, int x, int y, int z) {
        return balloons.get(cell).contains(x / scale, (y + 0.5) / scale, (z + 0.5) / scale);
    }

    /**
     * Whether a block of a cell touches its outside on a face, along an edge or at a corner: its envelope. Only the
     * blocks touching it on a face would meet the cell's floor and roof edge to edge where the envelope steps in, and
     * assembling the zeppelin takes only blocks touching face to face, leaving the floor, and its burners, behind.
     */
    private boolean onCellWall(int cell, int x, int y, int z) {
        for (BlockPos beside : BlockPos.betweenClosed(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1)) {
            if (!inCell(cell, beside.getX(), beside.getY(), beside.getZ())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a block is inside a cell and clear of its envelope: open gas, where a burner or its relay can stand. */
    boolean inGas(int cell, int x, int y, int z) {
        return inCell(cell, x, y, z) && !onCellWall(cell, x, y, z);
    }

    /**
     * The girders a cell hangs from: posts from the top of its envelope up to the crown of the hull, along its middle.
     * They hold it to the zeppelin; outside the cell, they take nothing from its gas.
     */
    private void hangers(int cell) {
        int middle = (int) Math.floor(balloons.get(cell).middle() * scale);
        for (int z = middle - HANGER_SPACING; z <= middle + HANGER_SPACING; z += HANGER_SPACING) {
            hanger(cell, z);
        }
    }

    private void hanger(int cell, int z) {
        // Down from the crown to the first open block under the cover, then on down to the top of the cell.
        int y = axis + reach();
        while (y > axis && !(isOpen(0, y, z) && inHull(0, y, z))) {
            y--;
        }
        int top = y;
        while (y > axis && isOpen(0, y, z)) {
            y--;
        }
        if (y <= axis || !inCell(cell, 0, y, z)) {
            return;
        }
        for (int at = y + 1; at <= top; at++) {
            set(0, at, z, AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
    }

    /**
     * A cell's burners, in two rows across the middle of its floor at a row, spaced so each stands with open gas all
     * round it, each with its relay on its east side and a post from under the relay down to the envelope: as many as
     * the floor's breadth takes, but no more than fill the gas the cell holds, and in a cell too small for them a
     * single one wherever there is room for it. Where together they would give more than the cell holds, each is
     * turned down to its share. Their relays.
     */
    private List<BlockPos> burners(int cell, int row) {
        List<BlockPos> relays = new ArrayList<>();
        if (palette.burner().isEmpty()) {
            cellGas.add(0.0);
            return relays;
        }
        int held = gasHeld(cell);
        double size = cellScale(cell);
        int pattern = Math.max(LEAST_BURNERS, (int) Math.round(MOST_BURNERS * size * size));
        int count = Math.max(1, Math.min(pattern, (int) Math.ceil(held / ZeppelinPilot.BURNER_GAS)));
        int middle = (int) Math.floor(balloons.get(cell).middle() * scale);
        int pairs = (count + 1) / 2;
        for (int i = 0; i < count; i++) {
            int across = (i / 2) * BURNER_SPACING - (pairs - 1) * BURNER_SPACING / 2;
            int z = middle + (i % 2 == 0 ? -BURNER_STAGGER : BURNER_STAGGER);
            if (inGas(cell, across, row, z) && inGas(cell, across + 1, row, z)) {
                relays.add(burner(across, row, z));
            }
        }
        BlockPos room = relays.isEmpty() ? roomForBurner(cell, row, middle) : null;
        if (room != null) {
            relays.add(burner(room.getX(), room.getY(), room.getZ()));
        }
        turnDown(relays, held);
        return relays;
    }

    /** Where in a cell a burner and its relay find open gas, from a row up, nearest its middle; null for nowhere. */
    @Nullable
    private BlockPos roomForBurner(int cell, int from, int middle) {
        CellBox around = cellBox(cell);
        for (int y = from; y < around.high(); y++) {
            BlockPos room = roomInRow(cell, y, middle, around);
            if (room != null) {
                return room;
            }
        }
        return null;
    }

    @Nullable
    private BlockPos roomInRow(int cell, int y, int middle, CellBox around) {
        for (int k = 0; k <= around.back() - around.front(); k++) {
            int z = middle + (k % 2 == 0 ? k / 2 : -(k + 1) / 2);
            for (int x = -around.across(); x < around.across(); x++) {
                if (inGas(cell, x, y, z) && inGas(cell, x + 1, y, z)) {
                    return new BlockPos(x, y, z);
                }
            }
        }
        return null;
    }

    /**
     * Turns a cell's burners down, by their relays, where together they would give more hot air than the cell holds,
     * each to its share of it; and notes what they give together.
     */
    private void turnDown(List<BlockPos> relays, int held) {
        double each = relays.isEmpty() ? 0 : Math.min(ZeppelinPilot.BURNER_GAS, (double) held / relays.size());
        if (each < ZeppelinPilot.BURNER_GAS) {
            for (BlockPos relay : relays) {
                fittings.put(relay.west(), Burners.turnedDown(Math.max(1, (int) each)));
            }
        }
        cellGas.add(each * relays.size());
    }

    /** A burner with its relay beside it, standing on its post; the relay. */
    private BlockPos burner(int x, int y, int z) {
        set(x, y, z, palette.burner().orElseThrow());
        set(x + 1, y, z, AerolargeRegistry.THRUST_RELAY.get().defaultBlockState().setValue(ThrustRelayBlock.POWER, PILOT_FLAME));
        // Below the burner, it takes nothing from its hot air, which rises from it.
        for (int below = y - 1; below > 0 && isOpen(x + 1, below, z); below--) {
            set(x + 1, below, z, AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
        return new BlockPos(x + 1, y, z);
    }

    /**
     * The row the axial gangway's catwalk is laid in: its walkway just over the hull's axis; in a hull with one deck,
     * just over that deck's roof, so the gas cells over it have the room a small hull has for them.
     */
    int gangwayRow() {
        return gangway;
    }

    /**
     * The axial gangway: a catwalk along the middle of the hull from the second ring to the second last, just under
     * the gas cells, with glass rails along both edges and lamps set into its edges between them; at each ring a girder
     * post from under it down to the ring at the bottom of the hull, where nothing is laid below it, holding it up;
     * and where the stairs up from the decks reach it, a landing across to them.
     */
    private void gangway() {
        int from = (int) Math.floor(ringAt(1) * scale);
        int to = (int) Math.ceil(ringAt(CELLS - 1) * scale);
        for (int z = from; z <= to; z++) {
            gangwayRow(z);
        }
        for (int ring = 1; ring < CELLS; ring++) {
            post((int) Math.floor(ringAt(ring) * scale));
        }
        gangwayLanding(stairsTop);
    }

    private void gangwayRow(int z) {
        int row = gangwayRow();
        BlockState catwalk = AerolargeRegistry.ZEPPELIN_CATWALK.get().defaultBlockState();
        BlockState rail = AerolargeRegistry.CABIN_RAIL.get().defaultBlockState();
        for (int x = -GANGWAY_HALF_WIDTH; x <= GANGWAY_HALF_WIDTH; x++) {
            boolean lamp = Math.abs(x) == GANGWAY_HALF_WIDTH && Math.floorMod(z, LAMP_SPACING) == 0;
            set(x, row, z, lamp ? AerolargeRegistry.CABIN_LIGHT.get().defaultBlockState() : catwalk);
        }
        set(-GANGWAY_HALF_WIDTH, row + 1, z, CabinRailBlock.along(stateAt(-GANGWAY_HALF_WIDTH, row + 1, z), Direction.WEST, catwalk, rail));
        set(GANGWAY_HALF_WIDTH, row + 1, z, CabinRailBlock.along(stateAt(GANGWAY_HALF_WIDTH, row + 1, z), Direction.EAST, catwalk, rail));
    }

    /**
     * A post from under the gangway down to the ring at the bottom of the hull; none where something else, a deck or
     * the corridor from the control car, lies under the gangway there, which the post would stand in the way of.
     */
    private void post(int z) {
        int y = gangwayRow() - 1;
        while (y > 0 && isOpen(0, y, z) && inHull(0, y, z)) {
            y--;
        }
        if (!stateAt(0, y, z).is(AerolargeRegistry.ZEPPELIN_GIRDER.get()) && inHull(0, y, z)) {
            return;
        }
        for (int at = y + 1; at < gangwayRow(); at++) {
            set(0, at, z, AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
    }

    /**
     * Where the stairs up from the decks reach the gangway, a row along it: a catwalk from there across to the
     * gangway's edge, beside the stairs' top step and on past it, the gangway's rail opened there.
     */
    private void gangwayLanding(int z) {
        int row = gangwayRow();
        int outer = GANGWAY_HALF_WIDTH + 2 + FLIGHT_WIDTH;
        for (int at = z; at <= z + FLIGHT_WIDTH; at++) {
            for (int x = GANGWAY_HALF_WIDTH + 1; x <= outer; x++) {
                landingAt(x, row, at);
            }
            clear(GANGWAY_HALF_WIDTH, row + 1, at);
            railAlong(outer, row, at, Direction.EAST);
        }
        for (int x = GANGWAY_HALF_WIDTH + 1; x <= outer; x++) {
            railAlong(x, row, z + FLIGHT_WIDTH, Direction.SOUTH);
        }
        for (int x = GANGWAY_HALF_WIDTH + 1; x < outer - FLIGHT_WIDTH + 1; x++) {
            railAlong(x, row, z, Direction.NORTH);
        }
    }

    private void landingAt(int x, int y, int z) {
        if (!stateAt(x, y, z).is(AerolargeRegistry.CABIN_STAIRS.get())) {
            set(x, y, z, AerolargeRegistry.ZEPPELIN_CATWALK.get().defaultBlockState());
        }
    }

    /** The lower deck's floor row; in a hull with one deck, that deck's. */
    int lowerDeckRow() {
        return twoDecks() ? LOWER_DECK_ROWS + deckBase() : upperDeckRow();
    }

    /** The upper deck's floor row, the promenade's; in a hull with one deck, that deck's. */
    int upperDeckRow() {
        return (twoDecks() ? UPPER_DECK_ROWS : SINGLE_DECK_ROWS) + deckBase();
    }

    /** Whether the hull is deep enough for two decks, one over the other: the upper deck's roof under the gangway. */
    boolean twoDecks() {
        return twoDecks;
    }

    /** The row the passenger decks begin at, along the hull, and the row they end at. */
    int deckFront() {
        return deckFront;
    }

    int deckBack() {
        return deckBack;
    }

    /** The hull's lowest row along the passenger decks, which their floors are measured up from. */
    private int deckBase() {
        return deckBase;
    }

    /**
     * The passenger decks in the hull's belly: the lower deck's floor, the upper deck's over it, and its roof, each
     * across the hull as far as the cover leaves room for, lit by lamps set into the floors. Stairs climb from the
     * lower deck to the upper deck and from there on up through the roof to the gangway, and boarding stairs come up
     * through the belly from the ground to the lower deck.
     */
    private void decks() {
        int front = deckFront();
        int back = deckBack();
        int lower = lowerDeckRow();
        int upper = upperDeckRow();
        int reach = reach();
        int[] floors = twoDecks() ? new int[]{lower, upper, upper + ROOM_HEIGHT + 1} : new int[]{upper, upper + ROOM_HEIGHT + 1};
        for (int row : floors) {
            box(-reach, row, front, reach, row, back, this::deckFloorAt);
        }
        int middle = (front + back) / 2;
        if (twoDecks()) {
            stairs(LOWER_FLIGHT_ACROSS, lower + 1, middle - LOWER_FLIGHT_AHEAD, upper - lower, Direction.NORTH);
        }
        int rise = gangwayRow() - upper;
        stairs(GANGWAY_HALF_WIDTH + 3, upper + 1, middle + UPPER_FLIGHT_AFT, rise, Direction.SOUTH);
        stairsTop = middle + UPPER_FLIGHT_AFT + rise - 1;
        boardingStairs(-BOARDING_ACROSS, lower, front + BOARDING_AFT);
        boardingStairs(BOARDING_ACROSS, lower, front + BOARDING_AFT);
    }

    private void deckFloorAt(int x, int y, int z) {
        if (depthAt(x, y, z) > -DECK_INSET || !isOpen(x, y, z)) {
            return;
        }
        boolean lit = Math.floorMod(x, LAMP_SPACING) == 0 && Math.floorMod(z, LAMP_SPACING) == 0;
        set(x, y, z, lit ? AerolargeRegistry.CABIN_LIGHT.get().defaultBlockState() : AerolargeRegistry.CABIN_FLOOR.get().defaultBlockState());
    }

    /**
     * A flight of stairs {@link #FLIGHT_WIDTH} wide, climbing so many rows from a row up along the hull the way given,
     * starting at an x and a row along it, each step over the turned-over one under it so the flight is one piece;
     * whatever stands over it is opened up, so it can be climbed out of.
     */
    private void stairs(int x, int from, int z, int rise, Direction climbs) {
        for (int k = 0; k < rise; k++) {
            for (int dx = 0; dx < FLIGHT_WIDTH; dx++) {
                step(x + dx, from + k, z + climbs.getStepZ() * k, climbs, Integer.MAX_VALUE);
            }
        }
        for (int k = 0; k < rise; k++) {
            railAlong(x, from + k, z + climbs.getStepZ() * k, Direction.WEST);
            railAlong(x + FLIGHT_WIDTH - 1, from + k, z + climbs.getStepZ() * k, Direction.EAST);
        }
    }

    /**
     * A glass rail along one edge of the block over a step or a floor, sloping with a step under it (see
     * {@link CabinRailBlock}): along every flight's open sides and round the landing, the stairs climbing through the
     * hull's open inside, where a fall is long.
     */
    void railAlong(int x, int y, int z, Direction edge) {
        BlockState rail = AerolargeRegistry.CABIN_RAIL.get().defaultBlockState();
        set(x, y + 1, z, CabinRailBlock.along(stateAt(x, y + 1, z), edge, stateAt(x, y, z), rail));
    }

    /** A step, the turned-over step under it, and headroom over it, up to a row at most. */
    private void step(int x, int y, int z, Direction climbs, int highest) {
        BlockState step = AerolargeRegistry.CABIN_STAIRS.get().defaultBlockState().setValue(CabinStairsBlock.FACING, climbs);
        set(x, y, z, step);
        if (y > 0) {
            set(x, y - 1, z, step.setValue(CabinStairsBlock.HALF, Half.TOP));
        }
        for (int head = 1; head <= HEADROOM && y + head <= highest; head++) {
            clearHead(x, y + head, z);
        }
    }

    private void clearHead(int x, int y, int z) {
        if (!stateAt(x, y, z).is(AerolargeRegistry.ZEPPELIN_GIRDER.get())) {
            clear(x, y, z);
        }
    }

    /**
     * Boarding stairs up through the belly from the ground to the lower deck, cut through the cover: a straight flight
     * climbing aft, a block wide, opened up no further than a row over the deck; and beside its head on the deck, its
     * button, which folds the flight away into the belly, and the cover and the deck's floor back over it, as she flies
     * (see {@link BoardingStairsBlockEntity}). Everything the flight changes folds away, so with it up the hull is as
     * it would be without it.
     */
    private void boardingStairs(int x, int deck, int z) {
        Map<BlockPos, BlockState> before = flightColumn(x, deck, z);
        for (int y = 0; y <= deck; y++) {
            step(x, y, z + y, Direction.SOUTH, deck + 1);
        }
        for (int y = 0; y < deck; y++) {
            railAlong(x, y, z + y, Direction.WEST);
            railAlong(x, y, z + y, Direction.EAST);
        }
        BlockPos button = new BlockPos(x + Integer.signum(x), deck + 1, z + deck);
        List<BoardingStairsBlockEntity.Fold> folds = new ArrayList<>();
        before.forEach((at, was) -> addFold(folds, at, was, button));
        set(button.getX(), button.getY(), button.getZ(), AerolargeRegistry.BOARDING_STAIRS_BUTTON.get().defaultBlockState()
            .setValue(ButtonBlock.FACE, AttachFace.FLOOR).setValue(ButtonBlock.FACING, Direction.SOUTH));
        fit(button, (blockEntity, rotation) -> rigStairs(blockEntity, folds, rotation));
    }

    /** What stands in a flight of boarding stairs' column of the layout, every row it reaches, open air as air. */
    private Map<BlockPos, BlockState> flightColumn(int x, int deck, int z) {
        Map<BlockPos, BlockState> column = new HashMap<>();
        box(x, 0, z, x, deck + 1, z + deck, (at, y, along) -> column.put(new BlockPos(at, y, along), stateAt(at, y, along)));
        return column;
    }

    /** A block of the flight's column that the flight changed, to fold away with it, as an offset from its button. */
    private void addFold(List<BoardingStairsBlockEntity.Fold> folds, BlockPos at, BlockState was, BlockPos button) {
        BlockState now = stateAt(at.getX(), at.getY(), at.getZ());
        if (!now.equals(was)) {
            folds.add(new BoardingStairsBlockEntity.Fold(at.subtract(button), now, was));
        }
    }

    /** Hands a flight's button its folds, turned as the zeppelin was placed. */
    private static void rigStairs(BlockEntity blockEntity, List<BoardingStairsBlockEntity.Fold> folds, Rotation rotation) {
        if (blockEntity instanceof BoardingStairsBlockEntity stairs) {
            stairs.rig(folds.stream().map(fold -> new BoardingStairsBlockEntity.Fold(fold.at().rotate(rotation), fold.down().rotate(rotation),
                fold.up().rotate(rotation))).toList());
        }
    }

    /** The control car's box, in blocks: its front and back walls' rows, its half width, its floor and roof rows. */
    record Car(int front, int back, int half, int floor, int roof) {
        int lounge() {
            return (front + back) / 2;
        }

        boolean wall(int x, int z) {
            return Math.abs(x) == half || z == front || z == back;
        }
    }

    /**
     * The control car under the bow: a glazed car faired into the hull, with a wheel under it, and a ladder up a neck
     * through the fairing into the hull; inside, from the front, the control room and an observation lounge with a
     * glass floor, lit by three rows of lights along its ceiling.
     */
    private void controlCar() {
        int front = (int) Math.round(CAR_FRONT * scale);
        Car car = new Car(front, front + CAR_ROWS, CAR_HALF, CAR_FLOOR_ROW, CAR_FLOOR_ROW + CAR_ROOM + 1);
        box(-car.half(), car.floor(), car.front(), car.half(), car.roof(), car.back(), (x, y, z) -> set(x, y, z, carBlock(car, x, y, z)));
        for (int z = car.front() + 2; z < car.back(); z += LAMP_SPACING / 2) {
            for (int x = -CAR_LAMP_OUT; x <= CAR_LAMP_OUT; x += CAR_LAMP_OUT) {
                set(x, car.roof(), z, AerolargeRegistry.CABIN_LIGHT.get().defaultBlockState());
            }
        }
        new ZeppelinControlCar(this).fit(car.front(), car.back(), car.floor());
        this.car = car;
        // The wheel the zeppelin rests on at the bow, under the car's middle.
        set(0, car.floor() - 1, car.lounge(), AerolargeRegistry.LANDING_GEAR_TIRE.get().defaultBlockState());
        neck(car.back() - 1, car.floor() + 1, car.roof());
        set(-car.half() - 1, car.floor() + CALL_BUTTON_UP, car.front() + CALL_BUTTON_AFT, AerolargeRegistry.BOARDING_STAIRS_BUTTON.get().defaultBlockState()
            .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.WEST));
        box(-car.half(), car.roof() + 1, car.front(), car.half(), car.roof() + 1, car.back(), this::fairingAt);
    }

    /**
     * A column of the fairing over the control car: the cover carried down from the hull to the car's roof, as the
     * Hindenburg's car was faired into its hull, so no sky shows between them; solid, round the neck, so it holds
     * no dark hollow for anything to spawn in.
     */
    private void fairingAt(int x, int y, int z) {
        BlockState cover = ShellBlock.withShape(AerolargeRegistry.ZEPPELIN_COVER.get().defaultBlockState(), ShellGeometry.WHOLE);
        for (int at = y; at < y + FAIRING_HIGHEST && isOpen(x, at, z) && !inHull(x, at, z); at++) {
            set(x, at, z, cover);
        }
    }

    private BlockState carBlock(Car car, int x, int y, int z) {
        boolean wall = car.wall(x, z);
        if (y == car.floor()) {
            if (wall) {
                return AerolargeRegistry.ZEPPELIN_FABRIC.get().defaultBlockState();
            }
            return z > car.lounge() ? AerolargeRegistry.HULL_GLASS.get().defaultBlockState() : AerolargeRegistry.CABIN_FLOOR.get().defaultBlockState();
        }
        if (y == car.roof() || wall && y < car.floor() + CAR_SILL) {
            return AerolargeRegistry.ZEPPELIN_FABRIC.get().defaultBlockState();
        }
        return wall ? AerolargeRegistry.COCKPIT_GLASS.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    /**
     * The neck from the car up into the hull: a shaft of fabric from the car's roof to the corridor over it, a ladder
     * up its middle against the car's back wall from the car's floor up into the corridor, and the corridor running aft
     * from its top to the decks, its floor level with the lower deck's.
     */
    private void neck(int z, int from, int roof) {
        int top = lowerDeckRow();
        for (int y = roof; y < top; y++) {
            box(-1, y, z - 1, 1, y, z + 1, (x, at, along) -> shaftWallAt(x, at, along, z));
        }
        BlockState catwalk = AerolargeRegistry.ZEPPELIN_CATWALK.get().defaultBlockState();
        for (int at = z - 1; at <= deckFront(); at++) {
            box(-1, top, at, 1, top, at, (x, y, along) -> corridorAt(x, y, along, catwalk));
        }
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
        for (int y = from; y <= top; y++) {
            set(0, y, z, ladder);
        }
    }

    private void shaftWallAt(int x, int y, int z, int middle) {
        if (x != 0 || z != middle) {
            set(x, y, z, AerolargeRegistry.ZEPPELIN_FABRIC.get().defaultBlockState());
        }
    }

    private void corridorAt(int x, int y, int z, BlockState catwalk) {
        if (isOpen(x, y, z) && inHull(x, y, z)) {
            set(x, y, z, catwalk);
        }
    }

    /**
     * Where an engine car stands: its number less one, 0 and 1 to port (forward, aft), 2 and 3 to starboard (aft,
     * forward).
     */
    private record EngineCar(double station, int side) {
        static EngineCar of(int engine) {
            int side = engine < 2 ? -1 : 1;
            int pair = engine < 2 ? engine : 3 - engine;
            return new EngineCar(ENGINE_STATIONS[pair], side);
        }
    }

    /** An engine car's pod, in blocks: its axis across and up, its nose and tail rows and its radius. */
    private record Pod(int x, int y, int front, int back, double radius) {
        /** The pod's radius at a row, rounding over at its nose. */
        double radiusAt(int z) {
            return radius * Math.min(1, Math.sqrt(Math.max(0, (z - front + 0.5) / POD_NOSE)));
        }
    }

    /**
     * An engine car: a streamlined pod out on the flank, a girder strut back to the hull, the propeller's hub at its
     * tail; in its tail a ring of thrusters round the hub pushing it ahead, each with a relay in front of it, and in
     * its nose a pair pushing it astern with their relays behind them. The hub is the engine's relay too, beside the
     * ring's thrusters, so the propeller spins with its thrust (see {@link PropellerBlock}).
     */
    private void engineCar(int engine) {
        EngineCar car = EngineCar.of(engine);
        double hullRadius = radius(car.station());
        double drop = ENGINE_DROP * hullRadius;
        double out = Math.sqrt(hullRadius * hullRadius - drop * drop) * scale + POD_STANDOFF;
        int station = (int) Math.round(car.station() * scale);
        Pod pod = new Pod((int) Math.round(car.side() * out), (int) Math.round((axisHeight - drop) * scale), station - POD_HALF_LENGTH,
            station + POD_HALF_LENGTH, POD_RADIUS);
        int reach = (int) Math.ceil(pod.radius());
        box(pod.x() - reach, pod.y() - reach, pod.front(), pod.x() + reach, pod.y() + reach, pod.back(), (x, y, z) -> podAt(pod, x, y, z));
        List<BlockPos> ahead = new ArrayList<>();
        for (int[] at : AHEAD_THRUSTERS) {
            thruster(pod.x() + at[0], pod.y() + at[1], pod.back(), Direction.NORTH);
            ahead.add(relay(pod.x() + at[0], pod.y() + at[1], pod.back() - 1));
        }
        set(pod.x(), pod.y(), pod.back(), AerolargeRegistry.PROPELLER.get().defaultBlockState().setValue(PropellerBlock.FACING, Direction.NORTH));
        ahead.add(new BlockPos(pod.x(), pod.y(), pod.back()));
        List<BlockPos> astern = new ArrayList<>();
        for (int[] at : ASTERN_THRUSTERS) {
            thruster(pod.x() + at[0], pod.y() + at[1], pod.front(), Direction.SOUTH);
            astern.add(relay(pod.x() + at[0], pod.y() + at[1], pod.front() + 1));
        }
        aheadRelays.put(engine, ahead);
        asternRelays.put(engine, astern);
        engineCars.add(new BlockPos(pod.x(), pod.y(), (pod.front() + pod.back()) / 2));
        strut(pod.x(), pod.y(), station, car.side());
    }

    /** A block of a pod: its skin, closed over at its nose and tail, and open inside. */
    private void podAt(Pod pod, int x, int y, int z) {
        double off = Math.hypot(x - pod.x(), y - pod.y());
        double radius = pod.radiusAt(z);
        if (off > radius) {
            return;
        }
        boolean skin = off > radius - POD_SKIN || z == pod.front() || z == pod.back();
        set(x, y, z, skin ? AerolargeRegistry.ZEPPELIN_FABRIC.get().defaultBlockState() : Blocks.AIR.defaultBlockState());
    }

    private BlockPos relay(int x, int y, int z) {
        set(x, y, z, AerolargeRegistry.THRUST_RELAY.get().defaultBlockState());
        return new BlockPos(x, y, z);
    }

    /**
     * A creative thruster facing the way it pushes, turned up to the configured setting, with no plume: an airship's
     * engines turn propellers, and burn nothing to be seen (see {@link Thrusters}).
     */
    private void thruster(int x, int y, int z, Direction pushes) {
        if (palette.engine().isEmpty()) {
            return;
        }
        set(x, y, z, AirframePalette.with(palette.engine().get(), "facing", pushes.getSerializedName()));
        fittings.put(new BlockPos(x, y, z), Thrusters.fullThrust(EngineFlames.NONE));
    }

    /** The strut from an engine car back in to the hull: girders across, from the car's side to the cover. */
    private void strut(int x, int y, int z, int side) {
        for (int at = x - side * (int) Math.ceil(POD_RADIUS); at * side > 0 && !inHull(at, y, z); at -= side) {
            strutAt(at, y, z);
        }
    }

    private void strutAt(int x, int y, int z) {
        if (isOpen(x, y, z)) {
            set(x, y, z, AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
    }

    /**
     * The four fins in a cross: from the cover out to their outline, a block thick in the middle of their plane, the
     * lower fin cut off a row over the ground, with the wheel the zeppelin rests on at the stern under its lowest
     * point; under a hull raised over its control car, where the lower fin stops short of the ground, the wheel at the
     * foot of a tail skid of girders down from it.
     */
    private void fins() {
        int lowest = Integer.MAX_VALUE;
        int lowestZ = 0;
        for (int z = (int) Math.floor(FIN_ROOT_FRONT * scale); z <= Math.ceil(FIN_ROOT_BACK * scale); z++) {
            int reach = (int) Math.round(finReach((z + 0.5) / scale) * scale);
            for (Direction way : new Direction[]{Direction.UP, Direction.EAST, Direction.WEST}) {
                fin(way, z, reach);
            }
            int bottom = fin(Direction.DOWN, z, reach);
            if (bottom < lowest) {
                lowest = bottom;
                lowestZ = z;
            }
        }
        if (lowest == Integer.MAX_VALUE) {
            return;
        }
        for (int y = 1; y < lowest; y++) {
            set(0, y, lowestZ, AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
        set(0, 0, lowestZ, AerolargeRegistry.LANDING_GEAR_TIRE.get().defaultBlockState());
    }

    /**
     * One fin at a row along the hull, out from the hull's axis the way given as far as it reaches; the row its tip
     * ends at.
     */
    private int fin(Direction way, int z, int reach) {
        int end = Integer.MAX_VALUE;
        for (int k = 1; k <= reach; k++) {
            int x = way.getStepX() * k;
            int y = axis + way.getStepY() * k;
            if (y >= 1 && !inHull(x, y, z) && (isOpen(x, y, z) || stateAt(x, y, z).is(AerolargeRegistry.ZEPPELIN_COVER.get()))) {
                set(x, y, z, AerolargeRegistry.ZEPPELIN_FABRIC.get().defaultBlockState());
                end = y;
            }
        }
        return end;
    }

    /** Counts the hull's open air, once everything else stands. */
    private void countAir() {
        int[] count = {0};
        forEachAir((x, y, z) -> count[0]++);
        air = count[0];
    }

    /** Every block of air inside the hull: the hull's own space, wherever nothing else stands. */
    private void forEachAir(Cell cell) {
        for (int z = 0; z < length; z++) {
            int r = (int) Math.ceil(radiusAt(z)) + 1;
            box(-r, Math.max(0, axis - r), z, r, axis + r, z, (x, y, at) -> airAt(cell, x, y, at));
        }
    }

    private void airAt(Cell cell, int x, int y, int z) {
        if (inHull(x, y, z) && isOpen(x, y, z)) {
            cell.at(x, y, z);
        }
    }

    /** The hull's air first, so it is cleared before anything goes in, then everything else bottom to top. */
    @Override
    public void inOrder(BiConsumer<BlockPos, BlockState> cell) {
        BlockState open = Blocks.AIR.defaultBlockState();
        forEachAir((x, y, z) -> cell.accept(new BlockPos(x, y, z), open));
        List<Map.Entry<BlockPos, BlockState>> sorted = new ArrayList<>(cells.entrySet());
        sorted.sort((a, b) -> a.getValue().isAir() != b.getValue().isAir()
            ? (a.getValue().isAir() ? -1 : 1)
            : Integer.compare(a.getKey().getY(), b.getKey().getY()));
        sorted.forEach(entry -> cell.accept(entry.getKey(), entry.getValue()));
    }

    @Override
    public int size() {
        return air + cells.size();
    }

    @Override
    public Map<BlockPos, Fitting> fittings() {
        return fittings;
    }

    /** Too big for the Physics Assembler, it is assembled through Sable instead, and needs no glue. */
    @Override
    public boolean glued() {
        return false;
    }

    @Override
    public String message(String what) {
        return "message.aerolarge.zeppelin_" + what;
    }

    /** Sets up the block entity at a layout position once it stands, as the fitting says. */
    void fit(BlockPos at, Fitting fitting) {
        fittings.put(at, fitting);
    }

    /** Each gas cell's burners' relays, bow first, by layout position. */
    List<List<BlockPos>> burnerRelays() {
        return burnerRelays;
    }

    /** The most hot air each gas cell's burners give together, at full signal, bow first, in m³. */
    List<Double> cellGas() {
        return cellGas;
    }

    /**
     * How far aft of the nose the middle of the cells' lift is, in blocks: the middles of their bays, each weighted by
     * its burners. It is about where the zeppelin turns on the spot, and what her autopilot steers.
     */
    double liftMiddle() {
        double middle = 0;
        int burners = 0;
        for (int cell = 0; cell < burnerRelays.size(); cell++) {
            int count = burnerRelays.get(cell).size();
            middle += count * cellMiddle(cell) * scale;
            burners += count;
        }
        return burners > 0 ? middle / burners : 0;
    }

    /** Each engine's relays that drive it ahead, by its number less one, by layout position. */
    Map<Integer, List<BlockPos>> aheadRelays() {
        return aheadRelays;
    }

    /** Each engine car's pod's middle on its axis, by its engine's number less one, by layout position. */
    List<BlockPos> engineCars() {
        return engineCars;
    }

    /** The livery the zeppelin is first painted in, which its autopilot is handed as it is wired. */
    Livery livery() {
        return options.livery();
    }

    /** The control car's box, in blocks, under the bow. */
    Car car() {
        return car;
    }

    /** The row the hull's axis runs along. */
    int axis() {
        return axis;
    }

    /** The hull's blocks a metre, as its size has it. */
    double scale() {
        return scale;
    }

    /** How big its hull is. */
    ZeppelinSize hullSize() {
        return options.size();
    }

    /** A gas cell's balloon, in metres, the bow's first. */
    ZeppelinShape.Cell balloon(int cell) {
        return balloons.get(cell);
    }

    /** The hull's lowest point so many blocks aft of the nose's tip, in blocks over the ground. */
    double belly(double aft) {
        return ZeppelinShape.belly(aft / scale, axisHeight) * scale;
    }

    /** How many rows long the hull is, nose to stern. */
    int length() {
        return length;
    }

    /** Each engine's relays that drive it astern, by its number less one, by layout position. */
    Map<Integer, List<BlockPos>> asternRelays() {
        return asternRelays;
    }

    /** Two opposite corners of the box round every block of the zeppelin. */
    List<BlockPos> frameCorners() {
        BlockPos min = null;
        BlockPos max = null;
        for (BlockPos pos : cells.keySet()) {
            min = min == null ? pos : BlockPos.min(min, pos);
            max = max == null ? pos : BlockPos.max(max, pos);
        }
        return min == null ? List.of(BlockPos.ZERO, BlockPos.ZERO) : List.of(min, max);
    }
}
