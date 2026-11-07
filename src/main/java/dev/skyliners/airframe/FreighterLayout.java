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
import dev.skyliners.block.CargoCrateBlock;
import dev.skyliners.block.CoffeeMachineBlock;
import dev.skyliners.block.DeckRobotBlock;
import dev.skyliners.block.DisplayShelfBlock;
import dev.skyliners.block.DrinkTapBlock;
import dev.skyliners.block.GalleyFridgeBlock;
import dev.skyliners.block.GalleyFridgeBlockEntity;
import dev.skyliners.block.GalleyOvenBlock;
import dev.skyliners.block.GalleyUnitBlock;
import dev.skyliners.block.LightTone;
import dev.skyliners.block.PalletRackBlock;
import dev.skyliners.block.PilotSeatBlock;
import dev.skyliners.block.RampPanelBlock;
import dev.skyliners.block.RampPanelBlockEntity;
import dev.skyliners.block.RobotBartenderBlock;
import dev.skyliners.block.ShellBlock;
import dev.skyliners.block.ShellGeometry;
import dev.skyliners.block.WallLightBlock;
import dev.skyliners.block.WindowDimmerBlock;
import dev.skyliners.block.WindowDimmerBlockEntity;
import dev.skyliners.club.BandstandBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The freighter cut into blocks before she is put anywhere (see {@link Layout}), standing on the ground, her prongs to
 * the north, her outline at her own scale (see {@link FreighterShape}) and everything a player uses at a player's
 * size. Only her blocks are kept: the air inside her hull is worked out from its shape as she is handed over.
 *
 * <p>Her hull is a skin of the zeppelin's cover, cut to her lens and her parts in the snapped style (see
 * {@link SurfaceCut}), glazed round her cockpit's cone and over her turrets' domes. Inside, one deck: a core round
 * the turrets' ladder on her axis, a ring corridor round it, and six rooms out to an outer bulkhead, where her hull
 * grows too low to stand in; past it, the wedge out to her rim is a closed void but where a passage crosses it to her
 * cockpit's tube or along her prongs, the bulkhead opening onto it and padded walls closing it off from the rest of the
 * void.
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
     * How many struts run along her cockpit's cone, evenly round it from straight up: four, square to the blocks, so
     * each runs straight along a row of them; between them her canopy is glass, with a ring of strut half way along.
     */
    private static final int CANOPY_SPOKES = 4;
    private static final int MAIN_ROWS_UP = 2;
    /** Her retros in her prongs' tips, so far out either side of her middle line, in metres. */
    private static final double RETRO_OUT = 3.4;
    /**
     * Her jets, hidden inside her, where they show no plume: her nose's pair in her prongs near their tips, so far out
     * either side of her middle line and so far ahead of her axis, in metres, and her tail's pair in the void at her
     * stern, so far out either side and so far astern. Each pushes toward her middle line, its exhaust outward; her
     * pilot never runs them hard enough to burn anyone standing in it (see {@link FreighterPilot#JET_SETTING}).
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
    /**
     * Her cockpit's flight deck, in blocks from where the pod's round part meets its cone: the control head and the
     * pilots' seats a block behind it, the pilots so far either side of the head, the jump seats so far behind them,
     * and the autopilot screen's middle so far to the head's right and so many rows up, on the ledge of her skin at the
     * co-pilot's right hand, where it leaves the view ahead through her canopy clear.
     */
    private static final int SEATS_BEHIND = 1;
    private static final int PILOTS_OUT = 2;
    private static final int JUMP_BEHIND = 2;
    private static final int SCREEN_OUT = 4;
    private static final int SCREEN_UP = 1;
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
    /**
     * Her lounge's booth, in the room aft of her entry hall: a low table at this bearing and so far out from her axis,
     * the middle of the room, with a seat facing it on each side so many blocks from it, leg room between, in these
     * colours by turns.
     */
    private static final int LOUNGE_BEARING = 120;
    private static final double LOUNGE_OUT = 14;
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
     * block just past her rim, or missed between columns as she is turned, still counts.
     */
    private static final double HULL_MARGIN = 1;
    /** The columns under her hull, worked out once for each spacing asked (see {@link #underHull}). */
    private static final Map<Integer, List<Offset>> UNDER_HULL = new ConcurrentHashMap<>();
    /** Every block of her legs' foot plates (see {@link #feet}). */
    private static final List<Offset> FEET = footPlates();

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

    /** Every block of her legs' foot plates, for her autopilot to look for ground to stand them on. */
    public static List<Offset> feet() {
        return FEET;
    }

    private static List<Offset> footPlates() {
        List<Offset> feet = new ArrayList<>();
        for (double[] leg : FreighterShape.LEGS) {
            int x = (int) Math.round(leg[0] * SCALE);
            int z = (int) Math.round(leg[1] * SCALE);
            for (int i = 0; i < 9; i++) {
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
        layout.legs();
        layout.engines();
        layout.engineGrille();
        layout.cockpit();
        layout.robots();
        layout.ramp();
        layout.turret();
        layout.bandstand();
        layout.clubBar();
        layout.quarters();
        layout.lounge();
        layout.cargoHold();
        layout.galley();
        LoungeTank.build(layout, layout.deck);
        FreighterPaintings.build(layout, layout.deck);
        layout.lights();
        layout.countAir();
        return layout;
    }

    /**
     * Her skin: every block her surface passes through, cut to it; then the glass of her cockpit's cone and her
     * turrets' domes set into it (see {@link #glaze}).
     */
    private void skin() {
        BlockState cover = SkylinersRegistry.ZEPPELIN_COVER.get().defaultBlockState();
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
     * glass with the server config's {@code freighter.canopyDimmer} off, and smoked glass over her turrets' domes.
     */
    private void glassRow(int x, int y) {
        BlockState glass = SkylinersRegistry.SMOKED_GLASS.get().defaultBlockState();
        boolean dimming = SkylinersConfig.FREIGHTER_CANOPY_DIMMER.get();
        BlockState canopy = dimming ? SkylinersRegistry.DIMMING_GLASS.get().defaultBlockState() : glass;
        for (int z = front; z <= back; z++) {
            if (!isOpen(x, y, z) && glazed(x, y + 0.5, z)) {
                boolean onCanopy = FreighterShape.nearest(x / scale, (y + 0.5) / scale, z / scale) == FreighterShape.Part.POD;
                glaze(x, y, z, onCanopy ? canopy : glass);
                noteCanopy(onCanopy && dimming, new BlockPos(x, y, z));
            }
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
     * Whether her skin is glass at a point, in blocks: on her cockpit's cone and its front, but for its struts (see
     * {@link #CANOPY_SPOKES}); and on her turrets' domes.
     */
    private boolean glazed(double x, double y, double z) {
        double mx = x / scale;
        double my = y / scale;
        double mz = z / scale;
        FreighterShape.Part part = FreighterShape.nearest(mx, my, mz);
        if (part == FreighterShape.Part.DOME) {
            return true;
        }
        return part == FreighterShape.Part.POD && mz < FreighterShape.CONE_FROM && !strut(mx, my, mz);
    }

    /** Whether a point on her cockpit's cone, in metres, lies on one of its struts. */
    private boolean strut(double x, double y, double z) {
        double ring = (FreighterShape.CONE_FROM + FreighterShape.POD_TIP) / 2;
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
     * plate on the ground, three blocks square, that she stands on.
     */
    private void legs() {
        BlockState strut = SkylinersRegistry.LANDING_GEAR.get().defaultBlockState();
        BlockState foot = SkylinersRegistry.ZEPPELIN_FABRIC.get().defaultBlockState();
        for (double[] leg : FreighterShape.LEGS) {
            int x = (int) Math.round(leg[0] * scale);
            int z = (int) Math.round(leg[1] * scale);
            for (int y = 1; isOpen(x, y, z) && surface.depth(x, y + 0.5, z) > 0; y++) {
                set(x, y, z, strut);
            }
            for (int i = 0; i < 9; i++) {
                set(x + i % 3 - 1, 0, z + i / 3 - 1, foot);
            }
            feet.add(new BlockPos(x, 0, z));
        }
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
        jets[0] = jet(engine.get(), -noseX, noseZ, Direction.EAST);
        jets[1] = jet(engine.get(), noseX, noseZ, Direction.WEST);
        jets[2] = jet(engine.get(), -tailX, tailZ, Direction.EAST);
        jets[3] = jet(engine.get(), tailX, tailZ, Direction.WEST);
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

    /** A jet inside her at a column over her deck, pushing the way given, no plume; null where it does not fit. */
    @Nullable
    private BlockPos jet(BlockState engine, int x, int z, Direction pushes) {
        int y = deck + 1;
        BlockPos relay = new BlockPos(x, y, z).relative(pushes);
        if (!isOpen(x, y, z) || !isOpen(relay.getX(), y, relay.getZ()) || surface.depth(x, y + 0.5, z) >= 0) {
            return null;
        }
        return thruster(engine, x, y, z, pushes, EngineFlames.NONE, FreighterPilot.JET_SETTING);
    }

    /**
     * Her cockpit in the pod: the autopilot's control head facing her bow where the pod's round part meets its cone,
     * the pilot's seat on its left and the co-pilot's on its right, a sidestick at each outboard hand, the jump seats
     * behind them, and the autopilot screen at the co-pilot's right, facing in, linked to the head; the head wired once
     * it stands.
     */
    private void cockpit() {
        int x = (int) Math.round(FreighterShape.TUBE_OUT * scale);
        int y = deck + 1;
        int z = (int) Math.round(FreighterShape.CONE_FROM * scale) + SEATS_BEHIND;
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
        autopilotScreen(new BlockPos(x + SCREEN_OUT, y + SCREEN_UP, z - AutopilotScreenBlock.COLUMNS / 2), Direction.WEST);
        canopyDimmer(new BlockPos(x - PILOTS_OUT - 1, y + DIMMER_UP, z));
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
     * at its head, the glass rail along its well's open side, its panel on the wall beside its head, and the panel
     * handed the ramp once it stands, its hatch every block of hers in its well's columns from her deck's floor down;
     * none with the server config's {@code freighter.ramp} off.
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
        BlockPos panel = new BlockPos(RAMP_HEAD + RAMP_PANEL_OUT, deck + RAMP_PANEL_UP, left + RAMP_WIDTH - 1);
        BlockState panelState = SkylinersRegistry.RAMP_PANEL.get().defaultBlockState().setValue(RampPanelBlock.FACING, Direction.NORTH);
        set(panel.getX(), panel.getY(), panel.getZ(), panelState);
        rampButton();
        List<AirstairControllerBlockEntity.Step> hatch = new ArrayList<>();
        for (int cell = 0; cell < reach * RAMP_WIDTH; cell++) {
            hatchColumn(hatch, RAMP_HEAD + cell / RAMP_WIDTH, left + cell % RAMP_WIDTH, panel);
        }
        BlockPos head = new BlockPos(RAMP_HEAD, deck, RAMP_MIDDLE).subtract(panel);
        fit(panel, (blockEntity, rotation) -> {
            if (blockEntity instanceof RampPanelBlockEntity keeper) {
                keeper.rigRamp(new BoardingRamp.Frame(head.rotate(rotation), rotation.rotate(Direction.EAST), RAMP_WIDTH, rise),
                    hatch.stream().map(part -> turned(part, rotation)).toList());
            }
        });
    }

    /** A block of a hatch as it lies once she is placed, turned as she was. */
    private static AirstairControllerBlockEntity.Step turned(AirstairControllerBlockEntity.Step part, Rotation rotation) {
        return new AirstairControllerBlockEntity.Step(part.offset().rotate(rotation), part.state().rotate(rotation));
    }

    /**
     * Her top turret, up her core: a ladder up the core's back wall from her deck to the top of the shaft under her top
     * dome, a platform there round the ladder's top, the gunner's seat on it facing her bow, its back to the ladder,
     * their head in the dome's glass, the core's walls taken out of the dome's row so the gunner sees out all round,
     * and the laser cannon on the glass over them; none with the server config's {@code freighter.turrets} off.
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
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
        for (int y = deck + 1; y <= seat; y++) {
            set(0, y, CORE - 1, ladder);
        }
        BlockState floor = SkylinersRegistry.CABIN_FLOOR.get().defaultBlockState();
        for (int cell = 0; cell < 2 * CORE_INSIDE; cell++) {
            set(cell % CORE_INSIDE - 1, seat - 1, cell / CORE_INSIDE - 1, floor);
        }
        openDomeRow(under);
        seat(0, seat, 0, PilotSeatBlock.Stick.NONE);
        int crown = under + 1;
        while (!isOpen(0, crown, 0) && crown < top) {
            crown++;
        }
        set(0, crown, 0, SkylinersRegistry.LASER_CANNON.get().defaultBlockState());
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
     * Her cargo hold's racks and crates, each where it has room over her deck's floor; none with the server config's
     * {@code freighter.rooms} off.
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
     * Her ramp's call button on her starboard leg's strut, facing ahead toward the ramp, where there is room for it.
     */
    private void rampButton() {
        double[] leg = FreighterShape.LEGS[RAMP_BUTTON_LEG];
        int x = (int) Math.round(leg[0] * scale);
        int z = (int) Math.round(leg[1] * scale) - 1;
        if (isOpen(x, RAMP_BUTTON_UP, z) && stateAt(x, RAMP_BUTTON_UP, z + 1).is(SkylinersRegistry.LANDING_GEAR.get())) {
            set(x, RAMP_BUTTON_UP, z, SkylinersRegistry.RAMP_BUTTON.get().defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
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

    /** Whether a block may be part of her ramp's hatch: her skin, her deck or a wall, nothing that works. */
    private static boolean hatchable(BlockState state) {
        return skin(state) || state.is(SkylinersRegistry.CABIN_FLOOR.get()) || state.is(SkylinersRegistry.PADDED_BULKHEAD.get());
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
     * Wires the control head once it stands: her thrusters' relays and her legs' feet as offsets from it, turned as she
     * was placed, where her disc's middle is from it, how far its block stands above her legs' feet, and the box round
     * her blocks.
     */
    private void wire(AutopilotBlockEntity autopilot, BlockPos at, Rotation rotation) {
        List<List<BlockPos>> turned = clusters.stream().map(cluster -> offsets(at, cluster, rotation)).toList();
        FreighterPilot.Jets jetRelays = new FreighterPilot.Jets(offset(at, jets[0], rotation), offset(at, jets[1], rotation),
            offset(at, jets[2], rotation), offset(at, jets[3], rotation));
        FreighterPilot.Wiring wiring = new FreighterPilot.Wiring(turned, offsets(at, mains, rotation), offsets(at, retros, rotation),
            jetRelays, offsets(at, feet, rotation), offsets(at, grille, rotation), -at.getX(), at.getZ(), Math.round(LIFT_OUT * scale));
        autopilot.wire(FreighterFlight.class, at.getY()).configure(wiring);
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
    private static double[] way(double bearing) {
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
