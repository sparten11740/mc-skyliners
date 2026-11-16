package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.FreighterFlight;
import dev.skyliners.autopilot.FreighterPilot;
import dev.skyliners.block.AirstairControllerBlockEntity;
import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.block.AutopilotScreenBlock;
import dev.skyliners.block.AutopilotScreenBlockEntity;
import dev.skyliners.block.BarCounterBlock;
import dev.skyliners.block.BoardingRamp;
import dev.skyliners.block.BunkBlock;
import dev.skyliners.block.CabinRailBlock;
import dev.skyliners.block.CabinSeatBlock;
import dev.skyliners.block.CanopyLiningBlock;
import dev.skyliners.block.CanopyLiningBlockEntity;
import dev.skyliners.block.CargoCrateBlock;
import dev.skyliners.block.CoffeeMachineBlock;
import dev.skyliners.block.DeckRobotBlock;
import dev.skyliners.block.DimmingGlassBlock;
import dev.skyliners.block.DisplayShelfBlock;
import dev.skyliners.block.DrinkTapBlock;
import dev.skyliners.block.GalleyFridgeBlock;
import dev.skyliners.block.GalleyFridgeBlockEntity;
import dev.skyliners.block.GalleyOvenBlock;
import dev.skyliners.block.GalleyUnitBlock;
import dev.skyliners.block.LegBayBlockEntity;
import dev.skyliners.block.LightTone;
import dev.skyliners.block.Mount;
import dev.skyliners.block.PalletRackBlock;
import dev.skyliners.block.ParcelScannerBlock;
import dev.skyliners.block.PilotSeatBlock;
import dev.skyliners.block.RampPanelBlock;
import dev.skyliners.block.RampPanelBlockEntity;
import dev.skyliners.block.RobotBartenderBlock;
import dev.skyliners.block.RungLadderBlock;
import dev.skyliners.block.ShellBlock;
import dev.skyliners.block.ShellGeometry;
import dev.skyliners.block.WallLightBlock;
import dev.skyliners.block.WindowDimmerBlock;
import dev.skyliners.block.WindowDimmerBlockEntity;
import dev.skyliners.club.BandstandBlock;
import dev.skyliners.console.ConsoleBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * The freighter cut into blocks before she is put anywhere (see {@link Layout}), standing on the ground, her prongs to
 * the north, her outline at her own scale (see {@link FreighterShape}) and everything a player uses at a player's
 * size. Only her blocks are kept: the air inside her hull is worked out from its shape as she is handed over.
 *
 * <p>Her hull is a skin of her own cover, the zeppelin's silver laid as riveted plates, cut to her lens and her parts
 * in the snapped style (see {@link SurfaceCut}), glazed round her cockpit's nose and over her turrets' domes, gunmetal
 * ribs over each dome.
 * Inside, one deck: a core round the turrets' ladder on her axis, a ring corridor round it, and six rooms out to an
 * outer bulkhead, where her hull grows too low to stand in; past it, the wedge out to her rim is a closed void but
 * where a passage crosses it to her cockpit's tube or along her prongs, the bulkhead opening onto it and padded walls
 * closing it off from the rest of the void.
 *
 * <p>Sable flies her as one body only as far as her blocks touch, face to face: her deck runs out to her skin and
 * every wall stands on it.
 */
public final class FreighterLayout extends HullLayout {
    /**
     * The scale she is always built at, whatever the server's, in blocks a metre: a fifth bigger than the airliner's
     * default, as Jan asked, roomy enough for her rooms and her Spookie; smaller, her hull leaves a player too little
     * headroom over her deck (two rows at scale 1, three at 1.5), and much larger, her weight outgrows her thrusters.
     */
    static final double SCALE = 2.4;
    /** How high over the ground under her legs' feet her deck is walked on, in metres. */
    private static final double DECK = 3.0;
    /** How far out from her axis her outer bulkhead stands, in metres: past it her hull is too low for her rooms. */
    static final double BULKHEAD = 9.5;
    /** The core round the turrets' ladder: its walls so many blocks out from her axis, three blocks of well inside. */
    static final int CORE = 2;
    private static final int CORE_INSIDE = 2 * CORE - 1;
    /**
     * Where across her core the ladder up to her turret stands, in blocks from her axis: in its corner, out of the
     * gunner's way.
     */
    private static final int TURRET_LADDER_X = CORE - 1;
    /** How far out from her axis the ring corridor's outer wall stands, in blocks: three blocks of corridor or more. */
    static final double CORRIDOR = 6.5;
    /** The walls between her six rooms, at these bearings in degrees clockwise from her bow. */
    private static final int[] ROOM_WALLS = {30, 90, 150, 210, 270, 330};
    /** The doorway from the ring corridor into each room, in the middle of its wall, at these bearings. */
    private static final int[] DOORWAYS = {0, 60, 120, 180, 240, 300};
    /** A doorway's height in rows over her deck and how many blocks it reaches either side of its middle. */
    private static final int DOOR_ROWS = 3;
    private static final double DOOR_HALF = 1.5;
    /** The solids a passage out through her outer bulkhead follows, across the void past it. */
    private static final List<FreighterShape.Part> PASSAGES =
        List.of(FreighterShape.Part.TUBE, FreighterShape.Part.POD, FreighterShape.Part.PRONG);
    /** How thick the padded walls closing a passage off from the void are, in blocks. */
    private static final double PASSAGE_WALL = 1;
    /**
     * Her lift clusters: how far ahead or astern and to either side of her axis each stands, in metres, on her
     * diagonals a little past half way out, in the order her pilot takes them ({@link FreighterPilot#CLUSTER_SIDES}),
     * each a plus of five thrusters round its middle, in blocks.
     */
    private static final double LIFT_OUT = 4.9;
    private static final int[][] PLUS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** Her main engines along her stern's rim, at these bearings in degrees, so many rows over her deck. */
    private static final int[] MAIN_BEARINGS = {165, 171, 177, 183, 189, 195};
    /**
     * How many struts of her skin run along her cockpit's nose where her canopy is built of blocks alone, without the
     * server config's {@code freighter.smoothCanopy}, evenly round it from straight up: four, square to the blocks, so
     * each runs straight along a row of them; between them her canopy is glass, with a ring of strut half way along.
     */
    private static final int CANOPY_SPOKES = 4;
    /**
     * How many slim ribs her smooth canopy's lining draws over its dome (see {@link #canopyLining}), evenly round it
     * from straight up: six, so none runs level across the view ahead from her seats.
     */
    private static final int CANOPY_RIBS = 6;
    /**
     * Her canopy's lining (see {@link #canopyLining}): how far in from her surface its nose is told, in blocks, at the
     * inside of her canopy's blocks, where her cockpit's walls meet the rim round its base, and how many blocks back
     * from her canopy's front along its axis an open block for it is looked for.
     */
    private static final double LINING_IN = 0.6;
    private static final int LINING_SEARCH = 4;
    /** How many corners a block has, each told by three bits, one an axis: which side of the block it lies on. */
    private static final int CUBE_CORNERS = 8;
    private static final int MAIN_ROWS_UP = 2;
    /** How far past her rim's face her engines' glow is drawn, in blocks: out in the air behind her mains' nozzles. */
    private static final double NOZZLE_OUT = 0.6;
    /** Her retros in her prongs' tips, so far out either side of her middle line, in metres. */
    private static final double RETRO_OUT = 3.4;
    /**
     * Her jets, where they show no plume: her nose's pair in her prongs near their tips, so far out either side of her
     * middle line and so far ahead of her axis, in metres, set into the outer walls of her prongs' tunnels (see
     * {@link #jet}), and her tail's pair hidden in the void at her stern, so far out either side and so far astern. Each
     * pushes toward her middle line, its exhaust outward; her pilot never runs them hard enough to burn anyone standing
     * in it (see {@link FreighterPilot#JET_SETTING}).
     */
    /**
     * Her engine grille across her stern's rim band, round her mains: so many degrees of bearing either side of
     * straight astern, looked along every so many degrees so no block of the band is missed, and from so many rows over
     * her deck to so many.
     */
    private static final int GRILLE_REACH = 30;
    private static final double GRILLE_STEP = 0.5;
    private static final int GRILLE_LOWEST_ROW = 1;
    private static final int GRILLE_HIGHEST_ROW = 3;
    private static final double NOSE_JET_OUT = 4.5;
    private static final double NOSE_JET_AHEAD = 19;
    private static final double TAIL_JET_OUT = 0.8;
    private static final double TAIL_JET_ASTERN = 10.8;
    /** The way each of her jets pushes, in their order: her nose's pair, port then starboard, then her tail's. */
    private static final Direction[] JET_PUSHES = {Direction.EAST, Direction.WEST, Direction.EAST, Direction.WEST};
    /** How many of her jets, the first, are her nose's, in her prongs: her port prong's and her starboard prong's. */
    private static final int NOSE_JETS = 2;
    private static final int PORT_NOSE_JET = 0;
    private static final int STARBOARD_NOSE_JET = 1;
    /**
     * Her cockpit's flight deck, in blocks from where the pod's round part meets its nose: the control head and the
     * pilots' seats two blocks behind it, leaving room before them for her console (see {@link FreighterConsole}), the
     * pilots so far either side of the head, the jump seats so far behind them, and the autopilot screen's middle so
     * far to the head's right, so many rows up and so far behind it, on the ledge of her skin at the co-pilot's right
     * hand, where it leaves the view ahead through her canopy clear and stands clear of the rim round the canopy's base,
     * which in line with the head came down over its front corner.
     */
    private static final int SEATS_BEHIND = 2;
    private static final int PILOTS_OUT = 2;
    private static final int JUMP_BEHIND = 2;
    private static final int SCREEN_OUT = 4;
    private static final int SCREEN_UP = 1;
    private static final int SCREEN_BEHIND = 1;
    /**
     * Her deck robots (see {@link dev.skyliners.block.DeckRobotBlock}): so far out from her axis, in blocks, a couple
     * past the ring corridor's wall, at these bearings clockwise from her bow, in degrees: one in the cargo hold at her
     * bow and one in engineering aft, each beside its room's doorway rather than in its way.
     */
    private static final double ROBOT_OUT = 9;
    private static final int[] ROBOT_BEARINGS = {20, 200};
    /**
     * Her boarding ramp (see {@link BoardingRamp}), out of her starboard side through the room at her starboard bow,
     * along the wall between it and the room aft of it, clear of her cockpit's tube ahead and her lift cluster: its
     * head, where it is hinged, so many blocks to starboard of her axis, just past the corridor's wall; its middle
     * column so many blocks ahead of her axis; and how many blocks wide it is. It falls one block in two from her deck
     * to the ground under her feet. The panel working it stands on the wall beside its head, a block out from it and so
     * many rows over her deck; a glass rail runs along its well's open side from a couple of blocks past its head.
     */
    private static final int RAMP_HEAD = 7;
    private static final int RAMP_MIDDLE = -2;
    private static final int RAMP_WIDTH = 3;
    private static final int RAMP_PANEL_OUT = 1;
    private static final int RAMP_PANEL_UP = 2;
    private static final int RAIL_FROM = 2;
    /**
     * Her ramp's call button: on which of her legs (see {@link FreighterShape#LEGS}), her starboard one aft of the
     * ramp's foot, and so many rows over the ground, on its strut's face toward the ramp, where someone standing
     * reaches it.
     */
    private static final int RAMP_BUTTON_LEG = 1;
    private static final int RAMP_BUTTON_UP = 2;
    /**
     * Her club's bandstand (see {@link BandstandBlock}): in her club, on its middle line ({@link #CLUB_BEARING}), so
     * far out from her axis, in blocks, toward its far wall, its band facing back in over the room.
     */
    private static final double BANDSTAND_OUT = 16;
    /**
     * Her club's bar (as the rocket's club has one): its counter along the club's straight wall, the one between it and
     * her crew quarters, so many blocks in from it, from so far out from her axis to so far, facing in over the room;
     * stools before every other block of it; drink taps on it so far either side of its middle; the robot bartender
     * behind its middle; and back-bar cabinets beside him, a tier of lit shelves on each, leaving the row over head
     * height clear for the wall washers.
     */
    private static final int BAR_IN = 3;
    private static final int BAR_FROM = 8;
    private static final int BAR_TO = 14;
    private static final int TAPS_APART = 2;
    private static final int BACK_BAR_SHELVES = 1;
    /**
     * Her crew quarters' bunks (see {@link BunkBlock}): single bunks against the wall between the quarters and her
     * club, facing in, each with its head so many blocks out along it from her axis and its foot a block further in,
     * the reading lights on and each blanket in the next of these colours; single, so the wall washers over them keep
     * their room.
     */
    private static final int[] BUNK_HEADS = {9, 12, 15, 18};
    private static final DyeColor[] BLANKETS = {DyeColor.BLUE, DyeColor.PURPLE, DyeColor.CYAN, DyeColor.LIGHT_BLUE};
    /** The least light her crawlways and the spaces under her deck are lit by, in light levels: dim, but never black. */
    private static final int PASSAGE_LIGHT = 4;
    /**
     * Her lounge's booth, in the room aft of her entry hall: a low table at this bearing and so far out from her axis,
     * in the room's far corner behind the partition across it, its middle kept for her beach (see {@link
     * FreighterChillout}), with a seat facing it on each side that has room so many blocks from it, leg room between,
     * in these colours by turns.
     */
    private static final int LOUNGE_BEARING = 137;
    private static final double LOUNGE_OUT = 20.5;
    private static final int LOUNGE_SEAT_OUT = 2;
    private static final DyeColor[] LOUNGE_SEATS = {DyeColor.ORANGE, DyeColor.BROWN};
    /**
     * Her cargo hold, the room at her bow: two walls of pallet racks (see {@link PalletRackBlock}) so many blocks
     * either side of her middle line, facing in, from so far ahead of her axis to so far, so many bays high, each
     * loaded in turn with these loads; and care-package crates set down before them, each at a column ahead of her axis
     * and its way, as two blocks across and along and a quarter turn.
     */
    private static final int RACKS_OUT = 5;
    private static final int RACKS_FROM = 14;
    private static final int RACKS_TO = 17;
    private static final int RACK_BAYS = 2;
    private static final PalletRackBlock.Load[] RACK_LOADS = {PalletRackBlock.Load.BOXES, PalletRackBlock.Load.DRUMS, PalletRackBlock.Load.CRATES};
    private static final Crate[] CRATES = {new Crate(-2, -12, 0), new Crate(-1, -12, 1), new Crate(2, -13, 2), new Crate(-2, -14, 3)};
    /** How far to starboard of her middle line her cargo hold's parcel scanner stands, in blocks. */
    private static final int SCANNER_X = 3;
    /** How far ahead of her axis it stands, in blocks, given as her layout's z, which runs astern. */
    private static final int SCANNER_Z = -10;
    /** The way its screen and the crate on its belt face: in over her middle line. */
    private static final Direction SCANNER_FACING = Direction.WEST;
    /**
     * Her galley, in her lounge: a run of units along the wall between the lounge and her entry hall, facing into the
     * lounge, from so far out from her axis to so far: a worktop with the coffee machine on it at its inner end, an
     * oven in its middle, the fridge, stocked, at its outer end, and stowage and worktops by turns between.
     */
    private static final int GALLEY_FROM = 8;
    private static final int GALLEY_TO = 14;
    /** The sides of her lounge's table its seats stand at, in turn. */
    private static final Direction[] LOUNGE_SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /**
     * Her wall washers (see {@link WallLightBlock}): so many rows over her deck, over the head of anyone walking past;
     * three on the corridor's outer wall between each two doorways, at a room wall's bearing and so many degrees either
     * side of it, looked for from so far out from her axis outward;
     * along each room wall, either side of it, at so many blocks out, in the first block that far along it, stepping
     * out from it this far at a time up to this far, that has room for it beside the wall; and one on each room's far
     * wall, her outer bulkhead, in its middle, so far out. The room at this bearing, her club, is lit pink, and the one
     * at this, her crew quarters, a cool night blue; the rest warm white.
     */
    private static final int LIGHT_UP = 3;
    private static final double CORRIDOR_LIGHT_FROM = 4.5;
    private static final int[] CORRIDOR_LIGHT_OFFS = {-14, 0, 14};
    private static final double[] ROOM_LIGHTS_OUT = {11, 17};
    private static final double LIGHT_STEP_OUT = 0.5;
    private static final double LIGHT_FURTHEST_OUT = 2.5;
    private static final double ROOM_END_LIGHT_OUT = 21.6;
    private static final int CLUB_BEARING = 300;
    private static final int QUARTERS_BEARING = 240;
    /**
     * Half the turn a room spans, in degrees: a room wall's light on either side of it is in the room half this far
     * round.
     */
    private static final int ROOM_HALF_TURN = 30;
    /**
     * Her canopy's dimmer: how far over the pilots' seats' floor row it stands, at a seated pilot's hand, and how far
     * out past the seat's side it may stand at most, toward her skin, so the pilot reaches it from the seat, in blocks.
     */
    private static final int DIMMER_UP = 1;
    private static final int DIMMER_OUT = 2;

    /**
     * How near her hull a block counts as under it, looking for ground to come down on, in blocks: a little wide, so a
     * block just past her rim, or missed between columns as she is turned, still counts, and wider than the block and a
     * half she may come down off her spot: at one, she came to rest with her stern on stone beside it, leaning.
     */
    private static final double HULL_MARGIN = 2;
    /** The columns under her hull, worked out once for each spacing asked (see {@link #underHull}). */
    private static final Map<Integer, List<Offset>> UNDER_HULL = new ConcurrentHashMap<>();
    /** Every block of her legs' foot plates (see {@link #feet}). */
    private static final List<Offset> FEET = footPlates();
    /** How many blocks each of her foot plates is made of, in the order of {@link #feet}, a plate's together. */
    public static final int FOOT_CELLS = 9;

    private final double scale;
    /** Her deck's floor row. */
    private final int deck;
    /** How far she reaches ahead of her axis, behind it, out to either side and up, in blocks. */
    private final int front;
    private final int back;
    private final int side;
    private final int top;
    /** Her outer bulkhead's radius in blocks. */
    private final double bulkhead;
    /** Her thrusters' relays as they are laid, her pilot's to drive, and where her legs' feet stand. */
    private final List<List<BlockPos>> clusters = new ArrayList<>();
    private final List<BlockPos> mains = new ArrayList<>();
    private final List<BlockPos> retros = new ArrayList<>();
    private final BlockPos[] jets = new BlockPos[4];
    private final List<BlockPos> feet = new ArrayList<>();
    /** Her landing legs as laid, in the order of {@link FreighterShape#LEGS}. */
    private final List<Leg> legs = new ArrayList<>();
    /** Her engine grille's blocks, for her autopilot to light. */
    private final List<BlockPos> grille = new ArrayList<>();
    /** The box round her canopy's dimming glass, its corners as it is laid; null before any is. */
    @Nullable
    private BlockPos canopyLow;
    @Nullable
    private BlockPos canopyHigh;
    /** Where the autopilot's control head stands in her cockpit; null before it is laid. */
    @Nullable
    private BlockPos head;
    /** The first block of her canopy's glass laid, which her canopy's lining follows as it dims; null before any is. */
    @Nullable
    private BlockPos canopyPane;

    /** Her hull's surface in blocks, as her skin is cut by it: her axis runs through the middle of block 0 each way. */
    private final SurfaceCut.Surface surface = new SurfaceCut.Surface() {
        @Override
        public double depth(double x, double y, double z) {
            return FreighterShape.depth(x / scale, y / scale, z / scale) * scale;
        }

        @Override
        public double[] outward(double x, double y, double z) {
            return FreighterShape.outward(x / scale, y / scale, z / scale);
        }
    };

    private FreighterLayout(double scale) {
        this.scale = scale;
        this.deck = deckRow(scale);
        this.front = (int) Math.floor(FreighterShape.PRONG_TIP * scale) - 1;
        this.back = (int) Math.ceil(FreighterShape.RADIUS * scale) + 1;
        this.side = (int) Math.ceil(FreighterShape.SIDE_REACH * scale) + 1;
        this.top = (int) Math.ceil(FreighterShape.TOP_DOME_CROWN * scale) + 1;
        this.bulkhead = BULKHEAD * scale;
    }

    /**
     * A point of hers from her disc's middle, in blocks: so many to her right and astern of it, as she is laid out nose
     * north (x east, z south), and so many up from her feet's soles.
     */
    public record Offset(int right, int astern, int up) {
    }

    /**
     * One of her landing legs as laid: the block where it meets her belly, her leg bay where her legs draw up, and the
     * blocks of the leg below it, which the bay takes away as it goes up.
     */
    private record Leg(BlockPos top, boolean retracts, List<BlockPos> parts) {
    }

    /** Every block of her legs' foot plates, for her autopilot to look for ground to stand them on. */
    public static List<Offset> feet() {
        return FEET;
    }

    /**
     * Where her mains' nozzles are, for her engines' glow: so many blocks to her right of her disc's middle, up from it
     * and astern of it, level with her control head as {@code FreighterFlight.middleOf} has her middle, each just past
     * her rim's face.
     */
    public static List<Vec3> mainNozzles() {
        double out = FreighterShape.RADIUS * SCALE + NOZZLE_OUT;
        return Arrays.stream(MAIN_BEARINGS).mapToObj(FreighterLayout::way).map(way -> new Vec3(way[0] * out, MAIN_ROWS_UP - 1, way[1] * out)).toList();
    }

    private static List<Offset> footPlates() {
        List<Offset> feet = new ArrayList<>();
        for (double[] leg : FreighterShape.LEGS) {
            int x = (int) Math.round(leg[0] * SCALE);
            int z = (int) Math.round(leg[1] * SCALE);
            for (int i = 0; i < FOOT_CELLS; i++) {
                feet.add(new Offset(x + i % 3 - 1, z + i / 3 - 1, 0));
            }
        }
        return List.copyOf(feet);
    }

    /**
     * The columns under her hull and within {@link #HULL_MARGIN} of it, every so many blocks across and along, from
     * her disc's middle as {@link #feet} gives them, each with how high her hull's underside stands over her feet's
     * soles there, in blocks, as near it: what must stand no higher for her to come down over it.
     */
    public static List<Offset> underHull(int every) {
        return UNDER_HULL.computeIfAbsent(every, FreighterLayout::columnsUnderHull);
    }

    private static List<Offset> columnsUnderHull(int every) {
        int side = (int) Math.ceil(FreighterShape.SIDE_REACH * SCALE + HULL_MARGIN);
        int front = (int) Math.floor(FreighterShape.PRONG_TIP * SCALE - HULL_MARGIN);
        int back = (int) Math.ceil(FreighterShape.RADIUS * SCALE + HULL_MARGIN);
        List<Offset> columns = new ArrayList<>();
        for (int x = -side; x <= side; x += every) {
            for (int z = front; z <= back; z += every) {
                int underside = underside(x, z);
                if (underside >= 0) {
                    columns.add(new Offset(x, z, underside));
                }
            }
        }
        return List.copyOf(columns);
    }

    /**
     * How high her hull's underside stands over her feet's soles in a column, in blocks, within {@link #HULL_MARGIN} of
     * it; -1 where she is not over it.
     */
    private static int underside(int x, int z) {
        int top = (int) Math.ceil(FreighterShape.TOP_DOME_CROWN * SCALE);
        for (int y = 0; y <= top; y++) {
            if (FreighterShape.depth(x / SCALE, (y + 0.5) / SCALE, z / SCALE) * SCALE < HULL_MARGIN) {
                return y;
            }
        }
        return -1;
    }

    /** The floor row of a freighter's deck built at a scale. */
    static int deckRow(double scale) {
        return (int) Math.round(DECK * scale) - 1;
    }

    /** How many rows over her feet's soles her autopilot's control head stands, as {@link #cockpit} lays it. */
    public static int headOverFeet() {
        return deckRow(SCALE) + 1;
    }

    /** The finished freighter at her scale: her legs' feet at y 0, her axis through x 0, z 0. */
    public static FreighterLayout create() {
        FreighterLayout layout = new FreighterLayout(SCALE);
        layout.skin();
        layout.deck();
        layout.bulkhead();
        layout.passageWalls();
        layout.core();
        layout.corridor();
        layout.roomWalls();
        FreighterFloors.build(layout, layout.deck, layout.side, layout.front);
        layout.legs();
        layout.engines();
        layout.engineBays();
        layout.engineGrille();
        layout.cockpit();
        layout.robots();
        layout.ramp();
        layout.wireLegs();
        layout.turret();
        FreighterPlating.build(layout);
        FreighterLights.build(layout);
        FreighterExterior.build(layout);
        layout.bandstand();
        layout.clubBar();
        layout.quarters();
        layout.lounge();
        layout.cargoHold();
        FreighterTractorBeam.build(layout, layout.deck);
        layout.galley();
        LoungeTank.build(layout, layout.deck);
        FreighterChillout.build(layout, layout.deck);
        FreighterPaintings.build(layout, layout.deck);
        FreighterGallery.build(layout, layout.deck, layout.scale);
        FreighterSmugglerDen.build(layout, layout.deck, layout.jets[STARBOARD_NOSE_JET]);
        FreighterEngineering.build(layout, layout.deck);
        FreighterEntryHall.build(layout, layout.deck);
        FreighterStickerMap.build(layout, layout.deck);
        FreighterRepairs.build(layout, layout.deck);
        FreighterFurnishings.build(layout, layout.deck);
        FreighterSpookie.build(layout, layout.deck);
        FreighterEngineering.breakerBoard(layout, layout.deck, FreighterLedStrips.build(layout, layout.deck, layout.side, layout.front, layout.scale));
        FreighterZeroGravity.build(layout, layout.deck, layout.side, layout.front);
        FreighterPortholes.build(layout, layout.deck, layout.scale);
        FreighterLookout.build(layout, layout.deck, layout.jets[PORT_NOSE_JET]);
        FreighterBowling.build(layout, layout.deck, layout.jets[STARBOARD_NOSE_JET]);
        layout.lights();
        if (SkylinersConfig.FREIGHTER_LIGHTS.get()) {
            DarkCorners.light(layout, LightTone.WARM, layout::leastLight, at -> !layout.underCanopy(at), at -> !layout.onCanopy(at));
        }
        layout.countAir();
        return layout;
    }

    /**
     * Her skin: every block her surface passes through, cut to it; then the glass of her cockpit's nose and her
     * turrets' domes set into it (see {@link #glaze}), and, where her canopy is smooth, glass in any open block of her
     * nose that stands out past her surface (see {@link #throughCanopy}).
     */
    private void skin() {
        BlockState cover = SkylinersRegistry.FREIGHTER_COVER.get().defaultBlockState();
        for (int y = 0; y <= top; y++) {
            for (int x = -side; x <= side; x++) {
                skinRow(cover, x, y);
            }
        }
        for (int y = 0; y <= top; y++) {
            for (int x = -side; x <= side; x++) {
                glassRow(x, y);
            }
        }
        fillCanopy();
    }

    /**
     * Glass where an open block stands through her smooth canopy (see {@link #throughCanopy}), row by row along her
     * length within a box round her nose; none where her canopy is built of blocks alone.
     */
    private void fillCanopy() {
        if (!SkylinersConfig.FREIGHTER_SMOOTH_CANOPY.get()) {
            return;
        }
        BlockState canopy = SkylinersRegistry.DIMMING_GLASS.get().defaultBlockState().setValue(DimmingGlassBlock.LINED, true);
        boolean dimming = SkylinersConfig.FREIGHTER_CANOPY_DIMMER.get();
        int noseFront = (int) Math.floor(FreighterShape.POD_TIP * scale);
        int noseBack = (int) Math.ceil(FreighterShape.NOSE_FROM * scale);
        int across = (int) Math.ceil(FreighterShape.POD_RADIUS * scale) + 1;
        int wide = 2 * across + 1;
        int axisX = (int) Math.round(FreighterShape.TUBE_OUT * scale);
        int axisY = (int) Math.round(FreighterShape.POD_UP * scale);
        for (int cell = 0; cell < wide * wide; cell++) {
            fillNose(axisX + cell % wide - across, axisY + cell / wide - across, noseFront, noseBack, canopy, dimming);
        }
    }

    /** Fills a row of her smooth canopy's nose along her length with glass where an open block stands through it. */
    private void fillNose(int x, int y, int from, int to, BlockState canopy, boolean dimming) {
        for (int z = from; z <= to; z++) {
            if (isOpen(x, y, z) && throughCanopy(x, y, z)) {
                set(x, y, z, canopy);
                noteCanopy(dimming, new BlockPos(x, y, z));
            }
        }
    }

    /**
     * Whether a block is under her cockpit's canopy, in the glass nose of her pod: no wall washer goes there, as its
     * light up the wall would hang in the air under the glass, where her cockpit has no wall above it.
     */
    private boolean underCanopy(BlockPos at) {
        double x = at.getX() / scale;
        double y = (at.getY() + 0.5) / scale;
        double z = at.getZ() / scale;
        return z < FreighterShape.NOSE_FROM && FreighterShape.nearest(x, y, z) == FreighterShape.Part.POD;
    }

    /**
     * Whether a block stands on her canopy's glass, along the rim of her cockpit's nose: no floor light goes there, as
     * the canopy is drawn as one smooth dome over its blocks, and a lamp lying on them hung in the air under it. Nothing
     * spawns on the glass, so the spot needs none.
     */
    private boolean onCanopy(BlockPos at) {
        return stateAt(at.getX(), at.getY() - 1, at.getZ()).getBlock() instanceof DimmingGlassBlock;
    }

    /**
     * Whether an open block inside her smooth canopy's nose has a corner out past her surface, where her lining draws
     * her glass smooth however her blocks step round her curve: her skin's blocks, cut to her curve but snapped, leave
     * such blocks open. Such a block is glass too, which draws nothing, so nothing laid inside her, her floor, her
     * lights or her console, stands out through her glass.
     */
    private boolean throughCanopy(int x, int y, int z) {
        boolean inNose = surface.depth(x, y + 0.5, z) < 0 && z / scale < FreighterShape.NOSE_FROM
            && FreighterShape.nearest(x / scale, (y + 0.5) / scale, z / scale) == FreighterShape.Part.POD;
        if (!inNose) {
            return false;
        }
        for (int corner = 0; corner < CUBE_CORNERS; corner++) {
            double cornerX = x - 0.5 + (corner & 1);
            double cornerY = y + (corner >> 1 & 1);
            double cornerZ = z - 0.5 + (corner >> 2 & 1);
            if (FreighterShape.depth(FreighterShape.Part.POD, cornerX / scale, cornerY / scale, cornerZ / scale) > 0) {
                return true;
            }
        }
        return false;
    }

    private void skinRow(BlockState cover, int x, int y) {
        for (int z = front; z <= back; z++) {
            int shape = SurfaceCut.shape(surface, x - 0.5, y, z - 0.5);
            if (shape != ShellGeometry.NONE) {
                set(x, y, z, ShellBlock.withShape(cover, shape));
            }
        }
    }

    /**
     * Her glass along a row: the canopy's dimming glass (see {@link dev.skyliners.block.DimmingGlassBlock}), smoked
     * glass with the server configs {@code freighter.canopyDimmer} and {@code freighter.smoothCanopy} both off, and
     * smoked glass over her turrets' domes. Smooth, her canopy is glass all over, which its lining draws inside and out
     * with its ribs (see {@link #canopyLining}).
     */
    private void glassRow(int x, int y) {
        BlockState glass = SkylinersRegistry.SMOKED_GLASS.get().defaultBlockState();
        boolean dimming = SkylinersConfig.FREIGHTER_CANOPY_DIMMER.get();
        boolean smooth = SkylinersConfig.FREIGHTER_SMOOTH_CANOPY.get();
        BlockState dimmingGlass = SkylinersRegistry.DIMMING_GLASS.get().defaultBlockState().setValue(DimmingGlassBlock.LINED, smooth);
        BlockState canopy = dimming || smooth ? dimmingGlass : glass;
        for (int z = front; z <= back; z++) {
            if (isOpen(x, y, z)) {
                continue;
            }
            if (glazed(x, y + 0.5, z, smooth)) {
                boolean onCanopy = FreighterShape.nearest(x / scale, (y + 0.5) / scale, z / scale) == FreighterShape.Part.POD;
                glaze(x, y, z, onCanopy ? canopy : glass);
                noteCanopy(onCanopy && dimming, new BlockPos(x, y, z));
                canopyPane = onCanopy && canopyPane == null && !isOpen(x, y, z) ? new BlockPos(x, y, z) : canopyPane;
            } else if (FreighterShape.nearest(x / scale, (y + 0.5) / scale, z / scale) == FreighterShape.Part.DOME) {
                domeRib(x, y, z);
            }
        }
    }

    /** A rib of one of her turrets' domes made dark gunmetal plate, cut to her curve as the cover there was. */
    private void domeRib(int x, int y, int z) {
        BlockState cover = stateAt(x, y, z);
        if (cover.is(SkylinersRegistry.FREIGHTER_COVER.get())) {
            set(x, y, z, ShellBlock.withShape(SkylinersRegistry.HULL_PLATE_DARK.get().defaultBlockState(), ShellBlock.shape(cover)));
        }
    }

    /** Widens the box round her canopy's dimming glass to take in a block of it. */
    private void noteCanopy(boolean dimming, BlockPos at) {
        if (dimming) {
            canopyLow = canopyLow == null ? at : BlockPos.min(canopyLow, at);
            canopyHigh = canopyHigh == null ? at : BlockPos.max(canopyHigh, at);
        }
    }

    /**
     * Whether her skin is glass at a point, in blocks: on her cockpit's nose, all over where her canopy is smooth, its
     * lining drawing her ribs, and else but for its struts (see {@link #CANOPY_SPOKES}); and on her turrets' domes, but
     * for their ribs.
     */
    private boolean glazed(double x, double y, double z, boolean smooth) {
        double mx = x / scale;
        double my = y / scale;
        double mz = z / scale;
        FreighterShape.Part part = FreighterShape.nearest(mx, my, mz);
        if (part == FreighterShape.Part.DOME) {
            return !onDomeRib(mx, mz);
        }
        return part == FreighterShape.Part.POD && mz < FreighterShape.NOSE_FROM && (smooth || !strut(mx, my, mz));
    }

    /**
     * Whether a point on one of her turrets' domes, in metres, lies on one of its ribs: four running up it from her
     * hull to its crown, where they meet, square to the blocks as her canopy's struts are, so each runs straight along
     * a row of them. A dome stands only a few blocks proud of her hull, so a ring of ribs round its foot would hide most
     * of its glass. Her gunner aims from her cannon's sight over the dome, so the ribs stand in nobody's way.
     */
    private boolean onDomeRib(double x, double z) {
        return Math.min(Math.abs(x), Math.abs(z)) * scale < 0.5;
    }

    /** Whether a point on her cockpit's nose, in metres, lies on one of its struts. */
    private boolean strut(double x, double y, double z) {
        double ring = (FreighterShape.NOSE_FROM + FreighterShape.POD_TIP) / 2;
        if (Math.abs(z - ring) * scale < 0.5) {
            return true;
        }
        double round = Math.atan2(y - FreighterShape.POD_UP, x - FreighterShape.TUBE_OUT);
        double spoke = 2 * Math.PI / CANOPY_SPOKES;
        double off = Math.abs(round - spoke * Math.round(round / spoke));
        return off * Math.hypot(x - FreighterShape.TUBE_OUT, y - FreighterShape.POD_UP) * scale < 0.5;
    }

    /** Her deck: every open block inside her hull on its row, out to her skin. */
    private void deck() {
        BlockState floor = SkylinersRegistry.CABIN_FLOOR.get().defaultBlockState();
        for (int x = -side; x <= side; x++) {
            for (int z = front; z <= back; z++) {
                if (isOpen(x, deck, z) && surface.depth(x, deck + 0.5, z) < 0) {
                    set(x, deck, z, floor);
                }
            }
        }
    }

    /**
     * Her outer bulkhead: a padded wall round her deck where her hull grows too low for her rooms, every block its
     * circle passes through, so it is closed edge to edge, open only where a passage crosses it.
     */
    private void bulkhead() {
        for (int x = -side; x <= side; x++) {
            for (int z = front; z <= back; z++) {
                int column = x;
                int row = z;
                if (onCircle(x, z, bulkhead)) {
                    wallUp(x, z, y -> inPassage(column, y, row));
                }
            }
        }
    }

    /**
     * The walls closing each passage off from the void past her outer bulkhead: every open block of the void over her
     * deck within a wall's thickness of a passage's solid, but not inside it.
     */
    private void passageWalls() {
        for (int y = deck + 1; y <= top; y++) {
            for (int x = -side; x <= side; x++) {
                passageWallRow(x, y);
            }
        }
    }

    private void passageWallRow(int x, int y) {
        BlockState wall = SkylinersRegistry.PADDED_BULKHEAD.get().defaultBlockState();
        for (int z = front; z <= back; z++) {
            boolean inVoid = Math.hypot(x, z) > bulkhead && isOpen(x, y, z) && surface.depth(x, y + 0.5, z) < 0;
            if (inVoid && !inPassage(x, y, z) && nearPassage(x, y, z)) {
                set(x, y, z, wall);
            }
        }
    }

    /** Whether a block's middle lies inside one of the solids her passages follow. */
    private boolean inPassage(int x, int y, int z) {
        return passageDepth(x, y, z) < 0;
    }

    /** Whether a block's middle lies within a wall's thickness outside the solids her passages follow. */
    private boolean nearPassage(int x, int y, int z) {
        return passageDepth(x, y, z) < PASSAGE_WALL;
    }

    /** How far a block's middle lies outside the nearest of the solids her passages follow, in blocks. */
    private double passageDepth(int x, int y, int z) {
        double least = Double.MAX_VALUE;
        for (FreighterShape.Part part : PASSAGES) {
            least = Math.min(least, FreighterShape.depth(part, x / scale, (y + 0.5) / scale, z / scale) * scale);
        }
        return least;
    }

    /** The core round the turrets' ladder on her axis: padded walls round a well three blocks across. */
    private void core() {
        for (int x = -CORE; x <= CORE; x++) {
            for (int z = -CORE; z <= CORE; z++) {
                if (Math.abs(x) == CORE || Math.abs(z) == CORE) {
                    wallUp(x, z, z == -CORE && x == 0 ? this::doorway : y -> false);
                }
            }
        }
    }

    /** The ring corridor's outer wall round the core, with a doorway into each room. */
    private void corridor() {
        for (int x = -side; x <= side; x++) {
            for (int z = front; z <= back; z++) {
                if (onCircle(x, z, CORRIDOR)) {
                    wallUp(x, z, inDoorway(x, z) ? this::doorway : y -> false);
                }
            }
        }
    }

    /** Whether a block of the corridor's wall is in a room's doorway. */
    private static boolean inDoorway(int x, int z) {
        for (int bearing : DOORWAYS) {
            double[] way = way(bearing);
            double along = x * way[0] + z * way[1];
            double across = Math.abs(-x * way[1] + z * way[0]);
            if (along > 0 && across < DOOR_HALF) {
                return true;
            }
        }
        return false;
    }

    /** The walls between her rooms, from the corridor's wall out to her outer bulkhead, each closed edge to edge. */
    private void roomWalls() {
        for (int bearing : ROOM_WALLS) {
            for (int x = -side; x <= side; x++) {
                roomWallRow(bearing, x);
            }
        }
    }

    private void roomWallRow(int bearing, int x) {
        double[] way = way(bearing);
        double cover = (Math.abs(way[0]) + Math.abs(way[1])) / 2;
        for (int z = front; z <= back; z++) {
            double along = x * way[0] + z * way[1];
            double across = Math.abs(-x * way[1] + z * way[0]);
            if (across <= cover && along > CORRIDOR && along < bulkhead) {
                wallUp(x, z, y -> false);
            }
        }
    }

    /**
     * A wall standing on her deck at a column, up to her hull: padded bulkhead in every open block inside it over the
     * deck, but the rows left open, a doorway's or a passage's.
     */
    private void wallUp(int x, int z, IntPredicate leftOpen) {
        BlockState wall = SkylinersRegistry.PADDED_BULKHEAD.get().defaultBlockState();
        for (int y = deck + 1; y <= top && surface.depth(x, y + 0.5, z) < 0; y++) {
            if (isOpen(x, y, z) && !leftOpen.test(y)) {
                set(x, y, z, wall);
            }
        }
    }

    /** Whether a row of a wall is in a doorway through it: up to a doorway's height over her deck. */
    private boolean doorway(int y) {
        return y <= deck + DOOR_ROWS;
    }

    /**
     * Her landing legs (see {@link FreighterShape#LEGS}): each a strut of landing gear down from her belly to a foot
     * plate on the ground, three blocks square, that she stands on, the strut meeting her belly in a solid block (see
     * {@link #legTop}).
     */
    private void legs() {
        BlockState strut = SkylinersRegistry.LANDING_GEAR.get().defaultBlockState();
        BlockState foot = SkylinersRegistry.ZEPPELIN_FABRIC.get().defaultBlockState();
        boolean retracting = SkylinersConfig.FREIGHTER_RETRACTING_LEGS.get();
        for (double[] leg : FreighterShape.LEGS) {
            int x = (int) Math.round(leg[0] * scale);
            int z = (int) Math.round(leg[1] * scale);
            List<BlockPos> parts = new ArrayList<>();
            int y = 1;
            for (; isOpen(x, y, z) && surface.depth(x, y + 0.5, z) > 0; y++) {
                set(x, y, z, strut);
                parts.add(new BlockPos(x, y, z));
            }
            for (int i = 0; i < FOOT_CELLS; i++) {
                BlockPos plate = new BlockPos(x + i % 3 - 1, 0, z + i / 3 - 1);
                set(plate.getX(), 0, plate.getZ(), foot);
                parts.add(plate);
            }
            feet.add(new BlockPos(x, 0, z));
            legs.add(legTop(new BlockPos(x, y, z), retracting, parts));
        }
    }

    /**
     * Where a leg's strut meets her belly, the strut stopped under a block: that block made solid, a block more of
     * strut or her leg bay where her legs draw up, in place of her skin there, which is cut to her belly's curve and so
     * often leaves the underside of its block open, the strut stopping short of the skin the eye sees; the strut's own
     * top where something else of hers stands there. The leg with its blocks below that top.
     */
    private Leg legTop(BlockPos over, boolean retracting, List<BlockPos> parts) {
        boolean intoSkin = isOpen(over.getX(), over.getY(), over.getZ()) || skin(stateAt(over.getX(), over.getY(), over.getZ()));
        BlockPos top = intoSkin ? over : over.below();
        if (top.getY() < 1) {
            return new Leg(top, false, parts);
        }
        parts.remove(top);
        BlockState mount = retracting ? SkylinersRegistry.LEG_BAY.get().defaultBlockState() : SkylinersRegistry.LANDING_GEAR.get().defaultBlockState();
        set(top.getX(), top.getY(), top.getZ(), mount);
        return new Leg(top, retracting, parts);
    }

    /** Hands each leg's bay the blocks of its leg once it stands, her ramp's call button among them on its leg. */
    private void wireLegs() {
        for (Leg leg : legs) {
            if (!leg.retracts()) {
                continue;
            }
            BlockPos top = leg.top();
            List<BlockPos> parts = List.copyOf(leg.parts());
            fit(top, (blockEntity, rotation) -> {
                if (blockEntity instanceof LegBayBlockEntity bay) {
                    bay.configure(offsets(top, parts, rotation));
                }
            });
        }
    }

    /** Her leg bays, each where a leg that draws up meets her belly. */
    private List<BlockPos> legBays() {
        return legs.stream().filter(Leg::retracts).map(Leg::top).toList();
    }

    /**
     * Her thrusters, each with its relay for her autopilot to drive: four clusters of lift thrusters set into her belly
     * pushing up, their plumes under her; six mains in her stern's rim pushing her ahead, their plumes behind her; two
     * retros in her prongs' tips; and her four jets, hidden inside, which show no plume.
     */
    private void engines() {
        Optional<BlockState> engine = AirframePalette.balloon().engine();
        if (engine.isEmpty()) {
            return;
        }
        int out = (int) Math.round(LIFT_OUT * scale);
        for (int[] sides : FreighterPilot.CLUSTER_SIDES) {
            List<BlockPos> relays = new ArrayList<>();
            for (int[] at : PLUS) {
                BlockPos relay = liftThruster(engine.get(), sides[1] * out + at[0], -sides[0] * out + at[1]);
                if (relay != null) {
                    relays.add(relay);
                }
            }
            clusters.add(relays);
        }
        for (int bearing : MAIN_BEARINGS) {
            BlockPos relay = mainThruster(engine.get(), bearing);
            if (relay != null) {
                mains.add(relay);
            }
        }
        for (int side : new int[]{-1, 1}) {
            BlockPos relay = retro(engine.get(), (int) Math.round(side * RETRO_OUT * scale));
            if (relay != null) {
                retros.add(relay);
            }
        }
        int noseX = (int) Math.round(NOSE_JET_OUT * scale);
        int noseZ = (int) Math.round(-NOSE_JET_AHEAD * scale);
        int tailX = (int) Math.round(TAIL_JET_OUT * scale);
        int tailZ = (int) Math.round(TAIL_JET_ASTERN * scale);
        jets[0] = jet(engine.get(), -noseX, noseZ, JET_PUSHES[0]);
        jets[1] = jet(engine.get(), noseX, noseZ, JET_PUSHES[1]);
        jets[2] = jet(engine.get(), -tailX, tailZ, JET_PUSHES[2]);
        jets[3] = jet(engine.get(), tailX, tailZ, JET_PUSHES[3]);
    }

    /**
     * Her engine bays in her prongs' tips (see {@link FreighterEngineBays}): a bulkhead across each tip round her
     * retro's relay, and a housing round each of her nose jets' relays.
     */
    private void engineBays() {
        FreighterEngineBays bays = new FreighterEngineBays(this, deck);
        retros.forEach(bays::bulkhead);
        for (int jet = 0; jet < NOSE_JETS; jet++) {
            bays.housing(jets[jet], JET_PUSHES[jet]);
        }
    }

    /**
     * A lift thruster in the lowest block of her belly's skin at a column, pushing up, its relay over it, in the
     * crawlspace or in place of a second block of skin where her belly is two thick there; null if none fits.
     */
    @Nullable
    private BlockPos liftThruster(BlockState engine, int x, int z) {
        for (int y = 0; y + 1 < deck; y++) {
            boolean over = isOpen(x, y + 1, z) || skin(stateAt(x, y + 1, z));
            if (!isOpen(x, y, z) && skin(stateAt(x, y, z)) && over) {
                return thruster(engine, x, y, z, Direction.UP, EngineFlames.PROPULSION_PLUME, FreighterPilot.LIFT_SETTING);
            }
        }
        return null;
    }

    /**
     * Her engine grille (see {@link dev.skyliners.block.EngineGrilleBlock}) in place of the outermost skin across her
     * stern's rim band round her mains, dark as she is built; none with the server config's
     * {@code freighter.engineGrille} off.
     */
    private void engineGrille() {
        if (!SkylinersConfig.FREIGHTER_ENGINE_GRILLE.get()) {
            return;
        }
        BlockState dark = SkylinersRegistry.ENGINE_GRILLE.get().defaultBlockState();
        for (double bearing = 180 - GRILLE_REACH; bearing <= 180 + GRILLE_REACH; bearing += GRILLE_STEP) {
            for (int row = GRILLE_LOWEST_ROW; row <= GRILLE_HIGHEST_ROW; row++) {
                BlockPos cell = outermostSkin(bearing, deck + row);
                if (cell != null && !grille.contains(cell)) {
                    set(cell.getX(), cell.getY(), cell.getZ(), dark);
                    grille.add(cell);
                }
            }
        }
    }

    /**
     * The outermost block of her skin at a bearing and a row, walking in from past her rim; null if the first block it
     * meets is not skin. Unlike {@link #mainThruster}'s walk, it stops at that first block, skin or not.
     */
    @Nullable
    private BlockPos outermostSkin(double bearing, int y) {
        double[] way = way(bearing);
        for (int k = back + 1; k > bulkhead; k--) {
            int x = (int) Math.round(way[0] * k);
            int z = (int) Math.round(way[1] * k);
            if (!isOpen(x, y, z)) {
                return skin(stateAt(x, y, z)) ? new BlockPos(x, y, z) : null;
            }
        }
        return null;
    }

    /** A main engine in the outermost block of her rim at a bearing, pushing her ahead, its plume behind her. */
    @Nullable
    private BlockPos mainThruster(BlockState engine, int bearing) {
        double[] way = way(bearing);
        int y = deck + MAIN_ROWS_UP;
        for (int k = back + 1; k > bulkhead; k--) {
            int x = (int) Math.round(way[0] * k);
            int z = (int) Math.round(way[1] * k);
            if (!isOpen(x, y, z) && skin(stateAt(x, y, z)) && isOpen(x, y, z - 1)) {
                return thruster(engine, x, y, z, Direction.NORTH, EngineFlames.PROPULSION_PLUME, FreighterPilot.MAIN_SETTING);
            }
        }
        return null;
    }

    /** A retro in the frontmost block of a prong's tip, so far across, pushing her back, its exhaust out ahead. */
    @Nullable
    private BlockPos retro(BlockState engine, int x) {
        int y = deck + 1;
        for (int z = front; z < 0; z++) {
            if (!isOpen(x, y, z) && skin(stateAt(x, y, z)) && isOpen(x, y, z + 1)) {
                return thruster(engine, x, y, z, Direction.SOUTH, EngineFlames.NONE, FreighterPilot.RETRO_SETTING);
            }
        }
        return null;
    }

    /**
     * A jet inside her at a column over her deck, pushing the way given, no plume; null where it does not fit. Where
     * the column is in a passage someone walks, the jet goes into the passage's wall on its exhaust's side instead, its
     * nozzle out in the open and its relay a box against the wall inside, as her retros' are at her prongs' tips, so
     * no engine stands out in the passage.
     */
    @Nullable
    private BlockPos jet(BlockState engine, int x, int z, Direction pushes) {
        int y = deck + 1;
        if (!isOpen(x, y, z) || surface.depth(x, y + 0.5, z) >= 0) {
            return null;
        }
        BlockPos at = walkedThrough(x, y, z) ? intoWall(new BlockPos(x, y, z), pushes.getOpposite()) : new BlockPos(x, y, z);
        BlockPos relay = at == null ? null : at.relative(pushes);
        if (relay == null || !isOpen(relay.getX(), y, relay.getZ())) {
            return null;
        }
        return thruster(engine, at.getX(), y, at.getZ(), pushes, EngineFlames.NONE, FreighterPilot.JET_SETTING);
    }

    /** Whether a block is in one of her passages out past her outer bulkhead, where someone walks. */
    private boolean walkedThrough(int x, int y, int z) {
        return Math.hypot(x, z) > bulkhead && inPassage(x, y, z);
    }

    /** The first block of her skin the way given from a block, through open air; null if open air runs on past it. */
    @Nullable
    private BlockPos intoWall(BlockPos from, Direction way) {
        BlockPos at = from;
        for (int step = 0; step <= 2 * side && isOpen(at.getX(), at.getY(), at.getZ()); step++) {
            at = at.relative(way);
        }
        return skin(stateAt(at.getX(), at.getY(), at.getZ())) ? at : null;
    }

    /**
     * Her cockpit in the pod: the autopilot's control head facing her bow where the pod's round part meets its nose,
     * the pilot's seat on its left and the co-pilot's on its right, a sidestick at each outboard hand, the jump seats
     * behind them, and the autopilot screen at the co-pilot's right, facing in, linked to the head; her self-destruct
     * button on the wall just aft of the co-pilot's jump seat; the head wired once it stands; her canopy's dimmer and
     * lining; and her console curving round before the seats.
     */
    private void cockpit() {
        int x = (int) Math.round(FreighterShape.TUBE_OUT * scale);
        int y = deck + 1;
        int z = (int) Math.round(FreighterShape.NOSE_FROM * scale) + SEATS_BEHIND;
        BlockPos at = new BlockPos(x, y, z);
        head = at;
        set(x, y, z, SkylinersRegistry.AUTOPILOT.get().defaultBlockState().setValue(AutopilotBlock.FACING, Direction.NORTH));
        fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof AutopilotBlockEntity autopilot) {
                wire(autopilot, at, rotation);
            }
        });
        seat(x - PILOTS_OUT, y, z, PilotSeatBlock.Stick.LEFT);
        seat(x + PILOTS_OUT, y, z, PilotSeatBlock.Stick.RIGHT);
        seat(x - PILOTS_OUT, y, z + JUMP_BEHIND, PilotSeatBlock.Stick.NONE);
        seat(x + PILOTS_OUT, y, z + JUMP_BEHIND, PilotSeatBlock.Stick.NONE);
        // Laid out nose north, her right is east: the screen at the co-pilot's right faces west, into her cockpit.
        autopilotScreen(new BlockPos(x + SCREEN_OUT, y + SCREEN_UP, z + SCREEN_BEHIND - AutopilotScreenBlock.COLUMNS / 2), Direction.WEST);
        selfDestruct(new BlockPos(x + PILOTS_OUT, y + 1, z + JUMP_BEHIND + 1), at);
        canopyDimmer(new BlockPos(x - PILOTS_OUT - 1, y + DIMMER_UP, z));
        canopyLining();
        FreighterConsole.build(this, at);
    }

    /**
     * Her canopy's lining (see {@link dev.skyliners.block.CanopyLiningBlock}), whose renderer draws her whole canopy,
     * inside and out, as one smooth dome with its ribs: unseen in the air on her canopy's axis just behind its front,
     * told once it stands the dome a skin's thickness in from her surface, where the inside of her glass is, and the
     * pane of glass to follow as it dims; none with the server config's {@code freighter.smoothCanopy} off, or no open
     * block there.
     */
    private void canopyLining() {
        if (!SkylinersConfig.FREIGHTER_SMOOTH_CANOPY.get()) {
            return;
        }
        int x = (int) Math.round(FreighterShape.TUBE_OUT * scale);
        int y = (int) Math.floor(FreighterShape.POD_UP * scale);
        int tip = (int) Math.round(FreighterShape.POD_TIP * scale);
        int z = tip + 1;
        while (z <= tip + LINING_SEARCH && !isOpen(x, y, z)) {
            z++;
        }
        if (!isOpen(x, y, z)) {
            return;
        }
        BlockPos at = new BlockPos(x, y, z);
        set(x, y, z, SkylinersRegistry.CANOPY_LINING.get().defaultBlockState().setValue(CanopyLiningBlock.FACING, Direction.NORTH));
        CanopyLiningBlockEntity.Cone cone = liningCone(at);
        BlockPos pane = canopyPane == null ? null : canopyPane.subtract(at);
        fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof CanopyLiningBlockEntity lining) {
                lining.shape(cone, pane, rotation);
            }
        });
    }

    /**
     * The dome her canopy's lining is drawn on, from the middle of the block given, as she is laid out nose north: her
     * nose's rounded shoulder and its flat front each moved in from her surface by {@link #LINING_IN}, the skin out to
     * her surface as thick, no ring round it, and {@link #CANOPY_RIBS} ribs over it.
     */
    private CanopyLiningBlockEntity.Cone liningCone(BlockPos at) {
        double length = (FreighterShape.NOSE_FROM - FreighterShape.POD_TIP) * scale - LINING_IN;
        double baseZ = FreighterShape.NOSE_FROM * scale;
        // The surface's x and z run through the middle of a block, its y from a block's foot.
        Vec3 base = new Vec3(FreighterShape.TUBE_OUT * scale - at.getX(), FreighterShape.POD_UP * scale - at.getY() - 0.5, baseZ - at.getZ());
        return new CanopyLiningBlockEntity.Cone(base, length, FreighterShape.POD_RADIUS * scale - LINING_IN,
            FreighterShape.TIP_RADIUS * scale - LINING_IN, CanopyLiningBlockEntity.NO_RING, CANOPY_RIBS, LINING_IN, true);
    }

    /** A pilot seat facing her bow, both its halves, where both are open. */
    private void seat(int x, int y, int z, PilotSeatBlock.Stick stick) {
        if (!isOpen(x, y, z) || !isOpen(x, y + 1, z)) {
            return;
        }
        BlockState seat = SkylinersRegistry.PILOT_SEAT.get().defaultBlockState().setValue(PilotSeatBlock.FACING, Direction.NORTH)
            .setValue(PilotSeatBlock.STICK, stick);
        set(x, y, z, seat);
        set(x, y + 1, z, seat.setValue(PilotSeatBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * The window dimmer for her canopy (see {@link dev.skyliners.block.WindowDimmerBlock}), at the pilot's left hand:
     * from beside the seat out toward her skin as far as it is open, facing in, told once it stands the box round the
     * canopy's glass, as two corners from it turned as she was placed; none where her canopy is smoked glass.
     */
    private void canopyDimmer(BlockPos beside) {
        BlockPos at = beside;
        for (int out = 0; out < DIMMER_OUT && isOpen(at.getX() - 1, at.getY(), at.getZ())
            && surface.depth(at.getX() - 1, at.getY() + 0.5, at.getZ()) < 0; out++) {
            at = at.west();
        }
        if (canopyLow == null || canopyHigh == null || !isOpen(at.getX(), at.getY(), at.getZ())) {
            return;
        }
        // Her left is west as she is laid out, so the dimmer faces east, into her cockpit.
        BlockState facingIn = SkylinersRegistry.WINDOW_DIMMER.get().defaultBlockState().setValue(WindowDimmerBlock.FACING, Direction.EAST);
        set(at.getX(), at.getY(), at.getZ(), facingIn);
        BlockPos from = canopyLow.subtract(at);
        BlockPos to = canopyHigh.subtract(at);
        // Her canopy is one box, as a rocket's story is, laid clear.
        fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof WindowDimmerBlockEntity dimmer) {
                dimmer.story(from.rotate(rotation), to.rotate(rotation), 0);
            }
        });
    }

    /**
     * Her deck robots, each standing on her deck in a room at its bearing, facing in toward the doorway and her axis,
     * where there is room over a floor; none with the server config's {@code freighter.robots} off.
     */
    private void robots() {
        if (!SkylinersConfig.FREIGHTER_ROBOTS.get()) {
            return;
        }
        BlockState robot = SkylinersRegistry.DECK_ROBOT.get().defaultBlockState();
        for (int bearing : ROBOT_BEARINGS) {
            double[] way = way(bearing);
            int x = (int) Math.round(way[0] * ROBOT_OUT);
            int z = (int) Math.round(way[1] * ROBOT_OUT);
            if (isOpen(x, deck + 1, z) && !isOpen(x, deck, z)) {
                set(x, deck + 1, z, robot.setValue(DeckRobotBlock.FACING, Direction.getNearest(-way[0], 0, -way[1])));
            }
        }
    }

    /**
     * Her boarding ramp, where its well holds nothing but her skin, her deck and her walls: the corridor's wall opened
     * at its head, the glass rail along its well's open side, its well's sides under her deck lined, its panel on the
     * wall beside its head, and the panel handed the ramp once it stands, its hatch every block of hers in its well's
     * columns from her deck's floor down, the row over it kept clear; none with the server config's
     * {@code freighter.ramp} off.
     */
    private void ramp() {
        int rise = deck + 1;
        int reach = (int) Math.ceil(Math.hypot(2 * rise, rise));
        int left = RAMP_MIDDLE - RAMP_WIDTH / 2;
        if (!SkylinersConfig.FREIGHTER_RAMP.get() || !wellHolds(left, reach)) {
            return;
        }
        openWellHead(left);
        railAlongWell(left - 1, reach);
        lineWell(left - 1, reach);
        lineWell(left + RAMP_WIDTH, reach);
        BlockPos panel = new BlockPos(RAMP_HEAD + RAMP_PANEL_OUT, deck + RAMP_PANEL_UP, left + RAMP_WIDTH - 1);
        BlockState panelState = SkylinersRegistry.RAMP_PANEL.get().defaultBlockState().setValue(RampPanelBlock.FACING, Direction.NORTH);
        set(panel.getX(), panel.getY(), panel.getZ(), panelState);
        rampButton();
        List<AirstairControllerBlockEntity.Step> hatch = new ArrayList<>();
        for (int cell = 0; cell < reach * RAMP_WIDTH; cell++) {
            hatchColumn(hatch, RAMP_HEAD + cell / RAMP_WIDTH, left + cell % RAMP_WIDTH, panel);
            keepOffHatch(RAMP_HEAD + cell / RAMP_WIDTH, left + cell % RAMP_WIDTH);
        }
        BlockPos head = new BlockPos(RAMP_HEAD, deck, RAMP_MIDDLE).subtract(panel);
        fit(panel, (blockEntity, rotation) -> {
            if (blockEntity instanceof RampPanelBlockEntity keeper) {
                keeper.rigRamp(new BoardingRamp.Frame(head.rotate(rotation), rotation.rotate(Direction.EAST), RAMP_WIDTH, rise),
                    hatch.stream().map(part -> turned(part, rotation)).toList());
            }
        });
    }

    /**
     * Keeps the row over a column of her ramp's hatch clear of what her rooms are furnished with after, where it would
     * be left standing on nothing as the ramp comes down; what hangs on her walls over it stays free to.
     */
    private void keepOffHatch(int x, int z) {
        if (isOpen(x, deck + 1, z) && !isOpen(x, deck, z) && inside(x, deck + 1, z)) {
            keepClear(x, deck + 1, z);
        }
    }

    /** A block of a hatch as it lies once she is placed, turned as she was. */
    private static AirstairControllerBlockEntity.Step turned(AirstairControllerBlockEntity.Step part, Rotation rotation) {
        return new AirstairControllerBlockEntity.Step(part.offset().rotate(rotation), part.state().rotate(rotation));
    }

    /**
     * Her top turret, up her core: a ladder up the corner of the core's back wall from her deck to the top of the shaft
     * under her top dome, a floor over the shaft there round the ladder's top, and on it the gunner's seat in the
     * middle, under the cannon and facing her bow, with a scanner console in front of it, a desk with its scope facing
     * the seat, so whoever climbs up steps back off beside the seat and sees where the turret is worked; a floor light
     * either side of the console lighting it and the step off kept clear, their head in the dome's glass, the core's
     * walls taken out of the dome's row so the gunner sees out all round, and the laser cannon on its pedestal on the
     * hub where the dome's ribs meet over them, facing her bow, its sight seeing past her rim; none with the server
     * config's {@code freighter.turrets} off.
     */
    private void turret() {
        int under = deck + 1;
        while (isOpen(0, under + 1, 0)) {
            under++;
        }
        int seat = under - 1;
        if (!SkylinersConfig.FREIGHTER_TURRETS.get() || seat <= deck + 2) {
            return;
        }
        BlockState ladder = SkylinersRegistry.CREW_LADDER.get().defaultBlockState()
            .setValue(RungLadderBlock.FACING, Direction.NORTH);
        for (int y = deck + 1; y <= seat; y++) {
            set(TURRET_LADDER_X, y, CORE - 1, ladder.setValue(RungLadderBlock.TOP, y == seat));
        }
        turretFloor(seat - 1);
        openDomeRow(under);
        seat(0, seat, 0, PilotSeatBlock.Stick.NONE);
        set(0, seat, -1, SkylinersRegistry.CONSOLE_SCANNER.get().defaultBlockState().setValue(ConsoleBlock.FACING, Direction.SOUTH)
            .setValue(ConsoleBlock.MOUNT, Mount.FLOOR));
        BlockState lamp = SkylinersRegistry.FLOOR_LIGHT.get().defaultBlockState();
        set(-1, seat, -1, lamp);
        set(1, seat, -1, lamp);
        // Lit by those, so the pass for her dark corners sets no floor light where the climber steps off.
        keepClear(TURRET_LADDER_X, seat, 0);
        int crown = under + 1;
        while (!isOpen(0, crown, 0) && crown < top) {
            crown++;
        }
        set(0, crown, 0, SkylinersRegistry.LASER_CANNON.get().defaultBlockState());
        FreighterExterior.pad(this, new BlockPos(0, crown, 0), Direction.DOWN);
    }

    /** The gunner's floor over her core's shaft, everywhere across the shaft but where the ladder comes up through it. */
    private void turretFloor(int y) {
        BlockState floor = SkylinersRegistry.CABIN_FLOOR.get().defaultBlockState();
        for (int cell = 0; cell < CORE_INSIDE * CORE_INSIDE; cell++) {
            int x = cell % CORE_INSIDE - (CORE - 1);
            int z = cell / CORE_INSIDE - (CORE - 1);
            if (x != TURRET_LADDER_X || z != CORE - 1) {
                set(x, y, z, floor);
            }
        }
    }

    /**
     * Her club's bandstand, its stage on her deck and its band facing back in toward her axis, where there is room over
     * a floor for its three players; none with the server config's {@code freighter.bandstand} off.
     */
    private void bandstand() {
        double[] way = way(CLUB_BEARING);
        int x = (int) Math.round(way[0] * BANDSTAND_OUT);
        int z = (int) Math.round(way[1] * BANDSTAND_OUT);
        Direction facing = Direction.getNearest(-way[0], 0, -way[1]);
        Direction across = facing.getClockWise();
        boolean room = isOpen(x + across.getStepX(), deck + 1, z + across.getStepZ())
            && isOpen(x - across.getStepX(), deck + 1, z - across.getStepZ());
        if (SkylinersConfig.FREIGHTER_BANDSTAND.get() && room && isOpen(x, deck + 1, z) && !isOpen(x, deck, z)) {
            set(x, deck + 1, z, SkylinersRegistry.BANDSTAND.get().defaultBlockState().setValue(BandstandBlock.FACING, facing));
        }
    }

    /**
     * Her club's bar, along the club's straight wall, the counter facing in, wherever each of its blocks has room over
     * her deck's floor; none with the server config's {@code freighter.clubBar} off.
     */
    private void clubBar() {
        if (!SkylinersConfig.FREIGHTER_CLUB_BAR.get()) {
            return;
        }
        int counterZ = -BAR_IN;
        int middle = -(BAR_FROM + BAR_TO) / 2;
        BlockState counter = SkylinersRegistry.BAR_COUNTER.get().defaultBlockState().setValue(BarCounterBlock.FACING, Direction.NORTH);
        for (int x = -BAR_FROM; x >= -BAR_TO; x--) {
            boolean placed = onFloor(x, counterZ, counter);
            if (placed && (x - middle) % 2 == 0) {
                onFloor(x, counterZ - 1, SkylinersRegistry.BAR_STOOL.get().defaultBlockState());
            }
        }
        BlockState tap = SkylinersRegistry.DRINK_TAP.get().defaultBlockState().setValue(DrinkTapBlock.FACING, Direction.NORTH);
        for (int side = -1; side <= 1; side += 2) {
            int x = middle + side * TAPS_APART;
            if (stateAt(x, deck + 1, counterZ).is(SkylinersRegistry.BAR_COUNTER.get()) && isOpen(x, deck + 2, counterZ)) {
                set(x, deck + 2, counterZ, tap);
            }
        }
        BlockState lower = RobotBartenderBlock.lower(SkylinersRegistry.ROBOT_BARTENDER.get(), Direction.NORTH);
        // His upper half's room is looked at first, so his lower half is never laid without it.
        if (isOpen(middle, deck + 2, counterZ + 1) && onFloor(middle, counterZ + 1, lower)) {
            set(middle, deck + 2, counterZ + 1, RobotBartenderBlock.upper(lower));
        }
        backBar(middle, counterZ + 1);
    }

    /**
     * Back-bar cabinets along a row either side of the bartender, out to the counter's ends, a tier of shelves on each.
     */
    private void backBar(int middle, int z) {
        BlockState shelf = SkylinersRegistry.BACK_BAR.get().defaultBlockState().setValue(DisplayShelfBlock.FACING, Direction.NORTH);
        for (int x = -BAR_FROM; x >= -BAR_TO; x--) {
            boolean placed = x != middle && onFloor(x, z, shelf.setValue(DisplayShelfBlock.TIER, DisplayShelfBlock.Tier.CABINET));
            if (placed) {
                shelves(x, z, shelf.setValue(DisplayShelfBlock.TIER, DisplayShelfBlock.Tier.SHELVES));
            }
        }
    }

    /**
     * The back bar's tiers of shelves over a cabinet, as many as {@link #BACK_BAR_SHELVES} and the room over it allow.
     */
    private void shelves(int x, int z, BlockState shelves) {
        for (int tier = 1; tier <= BACK_BAR_SHELVES && isOpen(x, deck + 1 + tier, z); tier++) {
            set(x, deck + 1 + tier, z, shelves);
        }
    }

    /** Sets a block on her deck, at a column over her deck's floor, if it is open; gives whether it did. */
    private boolean onFloor(int x, int z, BlockState state) {
        if (!floorFree(x, z)) {
            return false;
        }
        set(x, deck + 1, z, state);
        return true;
    }

    /** Whether a column has its block over her deck open, over a floor. */
    private boolean floorFree(int x, int z) {
        return isOpen(x, deck + 1, z) && !isOpen(x, deck, z);
    }

    /**
     * Her crew quarters' single bunks along the wall they share with her club, facing into the quarters, where each
     * bunk's head and foot have room over her deck's floor; none with the server config's {@code freighter.rooms} off.
     */
    private void quarters() {
        if (!SkylinersConfig.FREIGHTER_ROOMS.get()) {
            return;
        }
        // Laid out nose north, her crew quarters lie south of the wall they share with her club, which runs along z 0.
        Direction facing = Direction.SOUTH;
        int z = 1;
        Direction foot = BunkBlock.footward(facing);
        for (int bunk = 0; bunk < BUNK_HEADS.length; bunk++) {
            int x = -BUNK_HEADS[bunk];
            int footX = x + foot.getStepX();
            if (!floorFree(x, z) || !floorFree(footX, z)) {
                continue;
            }
            BlockState head = SkylinersRegistry.BUNK.get().defaultBlockState().setValue(BunkBlock.FACING, facing)
                .setValue(BunkBlock.COLOR, BLANKETS[bunk % BLANKETS.length]).setValue(BunkBlock.LIT, true)
                .setValue(BunkBlock.TIER, BunkBlock.Tier.SINGLE);
            set(x, deck + 1, z, head);
            set(footX, deck + 1, z, head.setValue(BunkBlock.PART, BedPart.FOOT));
        }
    }

    /**
     * Her lounge's booth: a low table in the room's middle and a seat facing it on each side, a block of leg room
     * between, that has room over her deck's floor; none with the server config's {@code freighter.rooms} off.
     */
    private void lounge() {
        if (!SkylinersConfig.FREIGHTER_ROOMS.get()) {
            return;
        }
        double[] way = way(LOUNGE_BEARING);
        int x = (int) Math.round(way[0] * LOUNGE_OUT);
        int z = (int) Math.round(way[1] * LOUNGE_OUT);
        if (!onFloor(x, z, SkylinersRegistry.LOUNGE_TABLE.get().defaultBlockState())) {
            return;
        }
        for (int seat = 0; seat < LOUNGE_SIDES.length; seat++) {
            Direction side = LOUNGE_SIDES[seat];
            loungeSeat(x + side.getStepX() * LOUNGE_SEAT_OUT, z + side.getStepZ() * LOUNGE_SEAT_OUT, side.getOpposite(),
                LOUNGE_SEATS[seat % LOUNGE_SEATS.length]);
        }
    }

    /** A seat of her lounge's booth, both its halves, facing the table, where it has room over her deck's floor. */
    private void loungeSeat(int x, int z, Direction facing, DyeColor colour) {
        if (!floorFree(x, z) || !isOpen(x, deck + 2, z)) {
            return;
        }
        BlockState seat = CabinSeatBlock.seat(SkylinersRegistry.BUSINESS_SEAT.get(), colour, facing);
        set(x, deck + 1, z, seat);
        set(x, deck + 2, z, seat.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * Her galley's run of units along the lounge's wall, facing in, each where it has room over her deck's floor; none
     * with the server config's {@code freighter.rooms} off.
     */
    private void galley() {
        if (!SkylinersConfig.FREIGHTER_ROOMS.get()) {
            return;
        }
        // Laid out nose north, her lounge lies south of the wall it shares with her entry hall, which runs along z 0.
        Direction facing = Direction.SOUTH;
        int z = 1;
        int oven = (GALLEY_FROM + GALLEY_TO) / 2;
        for (int x = GALLEY_FROM; x <= GALLEY_TO; x++) {
            GalleyUnit unit = x == GALLEY_FROM ? GalleyUnit.COFFEE : x == oven ? GalleyUnit.OVEN : x == GALLEY_TO ? GalleyUnit.FRIDGE
                : GalleyUnit.COUNTER;
            galleyUnit(x, z, facing, unit);
        }
    }

    /**
     * What a unit of her galley is: the worktop the coffee machine stands on, the oven, the fridge, or plain counter.
     */
    private enum GalleyUnit { COFFEE, OVEN, FRIDGE, COUNTER }

    /**
     * A unit of her galley at a column, facing the way given: the fridge, stocked once it stands; the oven; the worktop
     * with the coffee machine on it, where the block over it is open; or plain counter, stowage and worktops by turns.
     */
    private void galleyUnit(int x, int z, Direction facing, GalleyUnit unit) {
        if (unit == GalleyUnit.FRIDGE) {
            if (onFloor(x, z, SkylinersRegistry.GALLEY_FRIDGE.get().defaultBlockState().setValue(GalleyFridgeBlock.FACING, facing))) {
                fit(new BlockPos(x, deck + 1, z), FreighterLayout::stockFridge);
            }
            return;
        }
        boolean placed = onFloor(x, z, unit == GalleyUnit.OVEN ? oven(facing) : counter(x, facing));
        if (placed && unit == GalleyUnit.COFFEE && isOpen(x, deck + 2, z)) {
            set(x, deck + 2, z, SkylinersRegistry.COFFEE_MACHINE.get().defaultBlockState().setValue(CoffeeMachineBlock.FACING, facing));
        }
    }

    /** Her galley's oven, facing the way given. */
    private static BlockState oven(Direction facing) {
        return SkylinersRegistry.GALLEY_OVEN.get().defaultBlockState().setValue(GalleyOvenBlock.FACING, facing);
    }

    /**
     * A unit of her galley's counter at a column, facing the way given: worktops on even steps from its inner end, so
     * the coffee machine's is one, and stowage between.
     */
    private static BlockState counter(int x, Direction facing) {
        boolean stowage = (x - GALLEY_FROM) % 2 == 1;
        return (stowage ? SkylinersRegistry.GALLEY_STOWAGE : SkylinersRegistry.GALLEY_WORKTOP).get().defaultBlockState()
            .setValue(GalleyUnitBlock.FACING, facing);
    }

    /** Stocks her galley's fridge once it stands, as the rocket's mess stocks its own. */
    private static void stockFridge(BlockEntity blockEntity, Rotation rotation) {
        if (blockEntity instanceof GalleyFridgeBlockEntity fridge) {
            fridge.stock();
        }
    }

    /**
     * Her cargo hold's racks and crates and its parcel scanner, each where it has room over her deck's floor; none with
     * the server config's {@code freighter.rooms} off.
     */
    private void cargoHold() {
        if (!SkylinersConfig.FREIGHTER_ROOMS.get()) {
            return;
        }
        int load = 0;
        for (int z = -RACKS_FROM; z >= -RACKS_TO; z--) {
            load = rack(-RACKS_OUT, z, Direction.EAST, load);
            load = rack(RACKS_OUT, z, Direction.WEST, load);
        }
        for (Crate crate : CRATES) {
            onFloor(crate.x(), crate.z(), SkylinersRegistry.CARGO_CRATE.get().defaultBlockState()
                .setValue(CargoCrateBlock.FACING, Direction.from2DDataValue(crate.turn())));
        }
        scanner();
    }

    /**
     * Her cargo hold's parcel scanner, the rocket's (see {@link ParcelScannerBlock}), its screen facing in over her
     * middle line, an empty care-package crate waiting on its belt to be packed and scanned; where it and the crate
     * over it have room.
     */
    private void scanner() {
        BlockState scanner = SkylinersRegistry.PARCEL_SCANNER.get().defaultBlockState().setValue(ParcelScannerBlock.FACING, SCANNER_FACING);
        if (isOpen(SCANNER_X, deck + 2, SCANNER_Z) && onFloor(SCANNER_X, SCANNER_Z, scanner)) {
            set(SCANNER_X, deck + 2, SCANNER_Z,
                SkylinersRegistry.CARGO_CRATE.get().defaultBlockState().setValue(CargoCrateBlock.FACING, SCANNER_FACING));
        }
    }

    /** A crate set down in her cargo hold: its column, and the quarter turn it was set down at, from south. */
    private record Crate(int x, int z, int turn) {
    }

    /**
     * A pallet rack of bays one on another at a column, facing the way given, as many as {@link #RACK_BAYS} and the
     * room over it allow, each with the next load from so far through {@link #RACK_LOADS}; gives how far through them
     * it got.
     */
    private int rack(int x, int z, Direction facing, int load) {
        int next = load;
        for (int bay = 0; bay < RACK_BAYS && bayFree(x, z, bay); bay++) {
            set(x, deck + 1 + bay, z, PalletRackBlock.loaded(SkylinersRegistry.PALLET_RACK.get(), RACK_LOADS[next++ % RACK_LOADS.length], facing));
        }
        return next;
    }

    /** Whether a rack's bay so many up has room: the first over her deck's floor, those over it open. */
    private boolean bayFree(int x, int z, int bay) {
        return bay == 0 ? floorFree(x, z) : isOpen(x, deck + 1 + bay, z);
    }

    /**
     * Her wall washers round her ring corridor and her rooms, each where there is room for it and its light up the wall
     * beside one of her padded walls; none with the server config's {@code freighter.lights} off.
     */
    private void lights() {
        if (!SkylinersConfig.FREIGHTER_LIGHTS.get()) {
            return;
        }
        for (int wall : ROOM_WALLS) {
            corridorLights(wall);
            for (double out : ROOM_LIGHTS_OUT) {
                wallLight(wall, out, -1, toneAt(wall - ROOM_HALF_TURN));
                wallLight(wall, out, 1, toneAt(wall + ROOM_HALF_TURN));
            }
        }
        for (int room : DOORWAYS) {
            wallLight(room, ROOM_END_LIGHT_OUT, 0, toneAt(room));
        }
    }

    /**
     * The least light a block someone could stand in is lit by in her, in light levels: her rooms and ring corridor,
     * over her deck inside her outer bulkhead, only never left unlit, as each keeps its own mood; her crawlways, her
     * cockpit's way and the spaces under her deck so much more that none of them looks black.
     */
    private int leastLight(BlockPos at) {
        boolean roomy = at.getY() > deck && Math.hypot(at.getX(), at.getZ()) < bulkhead;
        return roomy ? DarkCorners.UNLIT : PASSAGE_LIGHT;
    }

    /**
     * Her corridor's wall washers round a room wall's bearing, between two doorways: one on the wall's line and one
     * either side of it, each on the corridor's outer wall, looked for outward across the corridor.
     */
    private void corridorLights(int wall) {
        for (int off : CORRIDOR_LIGHT_OFFS) {
            boolean lit = false;
            for (double out = CORRIDOR_LIGHT_FROM; out <= CORRIDOR && !lit; out += LIGHT_STEP_OUT) {
                lit = wallLight(wall + off, out, 0, LightTone.WARM);
            }
        }
    }

    /** The tone a room at a bearing is lit in: pink in her club, a cool night blue in her crew quarters, else warm. */
    private static LightTone toneAt(double bearing) {
        // Her rooms' middles are a room's turn apart round her, from her bow: the nearest is the room it is in.
        int room = (int) Math.round(Math.floorMod(Math.round(bearing), 360) / (2.0 * ROOM_HALF_TURN)) * 2 * ROOM_HALF_TURN % 360;
        return room == CLUB_BEARING ? LightTone.PINK : room == QUARTERS_BEARING ? LightTone.COOL : LightTone.WARM;
    }

    /**
     * A wall washer at a bearing so far out from her axis, {@link #LIGHT_UP} rows over her deck: stepping out to one
     * side of that line, clockwise or not (none for the spot itself), to the first block with room for it and its light
     * up the wall, beside one of her padded walls, fixed to that wall and shining away from it. Gives whether it found
     * one.
     */
    private boolean wallLight(double bearing, double out, int side, LightTone tone) {
        double[] way = way(bearing);
        double[] across = {-way[1], way[0]};
        double furthest = side == 0 ? 0 : LIGHT_FURTHEST_OUT;
        for (double step = 0; step <= furthest; step += LIGHT_STEP_OUT) {
            int x = (int) Math.round(way[0] * out + across[0] * side * step);
            int z = (int) Math.round(way[1] * out + across[1] * side * step);
            if (wallLightAt(x, deck + LIGHT_UP, z, tone)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fixes a wall washer in a block to the padded wall beside it, shining away from it, if the block and the one over
     * it are open; gives whether it did.
     */
    private boolean wallLightAt(int x, int y, int z, LightTone tone) {
        if (!isOpen(x, y, z) || !isOpen(x, y + 1, z)) {
            return false;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (stateAt(x + side.getStepX(), y, z + side.getStepZ()).is(SkylinersRegistry.PADDED_BULKHEAD.get())) {
                set(x, y, z, SkylinersRegistry.WALL_LIGHT.get().shining(side.getOpposite(), tone));
                return true;
            }
        }
        return false;
    }

    /** Takes her core's walls out of a row, the top dome's, where they would stand between the gunner and the glass. */
    private void openDomeRow(int y) {
        int across = 2 * CORE + 1;
        for (int cell = 0; cell < across * across; cell++) {
            BlockPos at = new BlockPos(cell % across - CORE, y, cell / across - CORE);
            if (stateAt(at.getX(), y, at.getZ()).is(SkylinersRegistry.PADDED_BULKHEAD.get())) {
                cells.remove(at);
            }
        }
    }

    /**
     * Her ramp's call button on her starboard leg's strut, facing ahead toward the ramp, where there is room for it:
     * one of the leg's blocks, so it goes up and comes down with it.
     */
    private void rampButton() {
        double[] leg = FreighterShape.LEGS[RAMP_BUTTON_LEG];
        int x = (int) Math.round(leg[0] * scale);
        int z = (int) Math.round(leg[1] * scale) - 1;
        if (isOpen(x, RAMP_BUTTON_UP, z) && stateAt(x, RAMP_BUTTON_UP, z + 1).is(SkylinersRegistry.LANDING_GEAR.get())) {
            set(x, RAMP_BUTTON_UP, z, SkylinersRegistry.RAMP_BUTTON.get().defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
            legs.get(RAMP_BUTTON_LEG).parts().add(new BlockPos(x, RAMP_BUTTON_UP, z));
        }
    }

    /** Whether her ramp's well, from its head out as far as the plate reaches, holds only her skin, deck and walls. */
    private boolean wellHolds(int left, int reach) {
        for (int cell = 0; cell < reach * RAMP_WIDTH; cell++) {
            if (!columnHatchable(RAMP_HEAD + cell / RAMP_WIDTH, left + cell % RAMP_WIDTH)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a column of her ramp's well holds only what may be its hatch, from her deck's floor down. */
    private boolean columnHatchable(int x, int z) {
        for (int y = 0; y <= deck; y++) {
            BlockState there = stateAt(x, y, z);
            if (!there.isAir() && !hatchable(there)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a block may be part of her ramp's hatch: her skin, her deck, its carpet or a room's own floor, or a wall,
     * nothing that works.
     */
    private static boolean hatchable(BlockState state) {
        return skin(state) || state.is(SkylinersRegistry.CABIN_FLOOR.get()) || FreighterFloors.isRoomFloor(state)
            || state.is(SkylinersRegistry.PADDED_BULKHEAD.get());
    }

    /** Adds a column of her ramp's well to its hatch: every block of hers in it from her deck's floor down. */
    private void hatchColumn(List<AirstairControllerBlockEntity.Step> hatch, int x, int z, BlockPos panel) {
        for (int y = 0; y <= deck; y++) {
            BlockState there = stateAt(x, y, z);
            if (!there.isAir()) {
                hatch.add(new AirstairControllerBlockEntity.Step(new BlockPos(x, y, z).subtract(panel), there));
            }
        }
    }

    /**
     * Opens the corridor's wall where her ramp's well meets it, as high as a doorway, so the ramp leads into the ring.
     */
    private void openWellHead(int left) {
        for (int cell = 0; cell < RAMP_HEAD * RAMP_WIDTH; cell++) {
            int x = cell / RAMP_WIDTH;
            int z = left + cell % RAMP_WIDTH;
            for (int y = deck + 1; y <= deck + DOOR_ROWS && onCircle(x, z, CORRIDOR); y++) {
                cells.remove(new BlockPos(x, y, z));
            }
        }
    }

    /**
     * Lines a long side of her ramp's well under her deck with padded wall, as her passages are closed off from the
     * void: the well opens through her deck and her belly, and from the ramp the low space under her deck lay open
     * beside it, its thrusters and the underside of her floors showing.
     */
    private void lineWell(int z, int reach) {
        BlockState wall = SkylinersRegistry.PADDED_BULKHEAD.get().defaultBlockState();
        for (int cell = 0; cell < reach * deck; cell++) {
            int x = RAMP_HEAD + cell / deck;
            int y = cell % deck;
            if (isOpen(x, y, z) && surface.depth(x, y + 0.5, z) < 0) {
                set(x, y, z, wall);
            }
        }
    }

    /** The glass rail along her ramp's well's open side, standing along its edge toward the well, on her deck. */
    private void railAlongWell(int z, int reach) {
        BlockState rail = SkylinersRegistry.CABIN_RAIL.get().defaultBlockState().setValue(CabinRailBlock.SOUTH, true);
        for (int x = RAMP_HEAD + RAIL_FROM; x < RAMP_HEAD + reach; x++) {
            if (isOpen(x, deck + 1, z) && !isOpen(x, deck, z)) {
                set(x, deck + 1, z, rail);
            }
        }
    }

    /**
     * The autopilot screen facing a way, its first part, at the bottom on the left as one looks at it, where given,
     * linked to the control head once it stands.
     */
    /**
     * Her self-destruct button, the airliner's own (see {@link SelfDestructFitting}): fixed to her starboard wall over
     * the ledge just aft of the co-pilot's jump seat, where whoever stands by the seats faces it, linked to her control
     * head; only where that spot is open, and none with the server config's {@code selfDestruct} off, as on the
     * airliner and the rocket.
     */
    private void selfDestruct(BlockPos button, BlockPos head) {
        if (!SkylinersConfig.SELF_DESTRUCT.get() || !isOpen(button.getX(), button.getY(), button.getZ())) {
            return;
        }
        // Laid out nose north, her starboard wall is east of it: whoever presses it faces east.
        set(button.getX(), button.getY(), button.getZ(), SelfDestructFitting.button(Mount.WALL, Direction.EAST));
        fit(button, SelfDestructFitting.linkedTo(button, head));
    }

    private void autopilotScreen(BlockPos origin, Direction facing) {
        BlockState screen = SkylinersRegistry.AUTOPILOT_SCREEN.get().defaultBlockState().setValue(AutopilotScreenBlock.FACING, facing);
        Direction viewersRight = facing.getCounterClockWise();
        for (int part = 0; part < AutopilotScreenBlock.COLUMNS * AutopilotScreenBlock.ROWS; part++) {
            BlockPos at = origin.relative(viewersRight, part % AutopilotScreenBlock.COLUMNS).above(part / AutopilotScreenBlock.COLUMNS);
            cells.put(at, screen.setValue(AutopilotScreenBlock.PART, part));
        }
        BlockPos linked = head.subtract(origin);
        fit(origin, (blockEntity, rotation) -> {
            if (blockEntity instanceof AutopilotScreenBlockEntity shown) {
                shown.configure(linked.rotate(rotation));
            }
        });
    }

    /**
     * Wires the control head once it stands: her thrusters' relays, her legs' feet and her leg bays as offsets from it,
     * turned as she was placed, where her disc's middle is from it, how far its block stands above her legs' feet, the
     * box round her blocks, and where her jump drive stands in engineering, to spool and fire as she jumps.
     */
    private void wire(AutopilotBlockEntity autopilot, BlockPos at, Rotation rotation) {
        List<List<BlockPos>> turned = clusters.stream().map(cluster -> offsets(at, cluster, rotation)).toList();
        FreighterPilot.Jets jetRelays = new FreighterPilot.Jets(offset(at, jets[0], rotation), offset(at, jets[1], rotation),
            offset(at, jets[2], rotation), offset(at, jets[3], rotation));
        FreighterPilot.Wiring wiring = new FreighterPilot.Wiring(turned, offsets(at, mains, rotation), offsets(at, retros, rotation),
            jetRelays, offsets(at, feet, rotation), offsets(at, grille, rotation), -at.getX(), at.getZ(), Math.round(LIFT_OUT * scale));
        FreighterFlight flight = autopilot.wire(FreighterFlight.class, at.getY());
        flight.configure(wiring);
        flight.wireLegs(offsets(at, legBays(), rotation));
        flight.wireJumpDrive(offset(at, FreighterEngineering.driveAt(deck), rotation));
        List<BlockPos> frame = offsets(at, List.of(new BlockPos(-side, 0, front), new BlockPos(side, top, back)), rotation);
        autopilot.frame(frame.get(0), frame.get(1));
    }

    /** A position as an offset from a block, turned with her; null for none. */
    @Nullable
    private static BlockPos offset(BlockPos from, @Nullable BlockPos to, Rotation rotation) {
        return to == null ? null : to.subtract(from).rotate(rotation);
    }

    /** Whether a circle round her axis, so many blocks across, passes through a column: some corners in, some out. */
    private static boolean onCircle(int x, int z, double radius) {
        boolean in = false;
        boolean out = false;
        for (int corner = 0; corner < 4; corner++) {
            double distance = Math.hypot(x - 0.5 + (corner & 1), z - 0.5 + (corner >> 1));
            in |= distance < radius;
            out |= distance >= radius;
        }
        return in && out;
    }

    /** The way along her deck at a bearing, clockwise from her bow, as x and z of a unit vector. */
    static double[] way(double bearing) {
        double turn = Math.toRadians(bearing);
        return new double[]{Math.sin(turn), -Math.cos(turn)};
    }

    @Override
    SurfaceCut.Surface surface() {
        return surface;
    }

    /** Her hull's air: from her belly dome's foot to her top dome's crown, out to a block past her skin each way. */
    @Override
    Box airBox() {
        return new Box(-side, 0, front, side, top, back);
    }

    /** Moved back from her axis by her prongs' reach, so her prongs' tips stand just past where she is built from. */
    @Override
    int handedBack() {
        return -front;
    }

    @Override
    public String message(String what) {
        return "message.skyliners.freighter_" + what;
    }
}
