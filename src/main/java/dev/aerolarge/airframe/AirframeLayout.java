package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.AirframeShape.*;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.AirstairBlockEntity;
import dev.aerolarge.block.AirstairButtonBlock;
import dev.aerolarge.block.AirstairControllerBlockEntity;
import dev.aerolarge.block.AirstairFlight;
import dev.aerolarge.block.AirstairStepBlock;
import dev.aerolarge.block.AnnouncementPanelBlock;
import dev.aerolarge.block.AnnouncementPanelBlockEntity;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlock;
import dev.aerolarge.block.AutopilotScreenBlockEntity;
import dev.aerolarge.block.Brightness;
import dev.aerolarge.block.CabinControlBlock;
import dev.aerolarge.block.CabinControlBlockEntity;
import dev.aerolarge.block.CabinStairsBlock;
import dev.aerolarge.block.CabinWindowBlock;
import dev.aerolarge.block.CockpitDoorBlock;
import dev.aerolarge.block.CockpitGlassBlock;
import dev.aerolarge.autopilot.ControlSurfaces;
import dev.aerolarge.block.CrewLockerBlock;
import dev.aerolarge.block.CrewLockerBlockEntity;
import dev.aerolarge.block.CurtainBlock;
import dev.aerolarge.block.DoorButtonBlock;
import dev.aerolarge.block.DropReleaseBlockEntity;
import dev.aerolarge.block.EngineCoreBlock;
import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.block.FairingBlock;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.GearBayBlock;
import dev.aerolarge.block.GearBayBlockEntity;
import dev.aerolarge.block.GearLeverBlockEntity;
import dev.aerolarge.block.LaserSwitchBlockEntity;
import dev.aerolarge.block.LaserTurretBlock;
import dev.aerolarge.block.InstrumentPanelBlock;
import dev.aerolarge.block.Joining;
import dev.aerolarge.block.JumpSwitchBlock;
import dev.aerolarge.block.LinkedControlBlockEntity;
import dev.aerolarge.block.NavLightBlock;
import dev.aerolarge.block.ParachuteRackBlock;
import dev.aerolarge.block.ParkingBrakeBlock;
import dev.aerolarge.block.PilotSeatBlock;
import dev.aerolarge.block.PlugDoorBlock;
import dev.aerolarge.block.SeatbeltSignBlock;
import dev.aerolarge.block.SeatbeltSwitchBlock;
import dev.aerolarge.block.SeatbeltSwitchBlockEntity;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.ShellGeometry;
import dev.aerolarge.block.SlideControlBlock;
import dev.aerolarge.block.SlideControlBlockEntity;
import dev.aerolarge.block.SurfacePanelBlock;
import dev.aerolarge.block.TailCameraDisplayBlock;
import dev.aerolarge.block.ThrustLeverBlock;
import dev.aerolarge.block.ThrustLeverBlockEntity;
import dev.aerolarge.block.TillerBlock;
import dev.aerolarge.block.TransponderBlockEntity;
import dev.aerolarge.radar.MarkerColour;
import dev.aerolarge.radar.MarkerIcon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * The airliner cut into blocks, before it is put anywhere: every block of it, and air for every block of the cabin
 * and hold, keyed by position relative to the nose. The layout is drawn with the nose pointing north: x runs to
 * starboard (east), y up from the ground under the wheels, z back toward the tail (south). Block states are drawn
 * the same way, so turning the whole thing to face another way is one rotation of positions and states.
 */
public final class AirframeLayout {
    /** Stations between cabin lights along the fuselage. */
    static final int LIGHT_SPACING = 6;
    /** How many blocks tall a cabin window is. */
    private static final int WINDOW_HEIGHT = 2;
    /** How square to the side the hull has to face for a window there: the sideways share of its outward direction. */
    private static final double WINDOW_FACING = 0.75;
    /**
     * Each wheel mount's suspension strength, Offroad's own 5 to 180 setting (its value box), set to the stiffest: at
     * the default of 10 a handful of wheels cannot carry an airliner, and their springs bottom out with the tires sunk
     * into the ground.
     */
    private static final int SUSPENSION_STRENGTH = 180;
    /** How far from a block's middle, in blocks, its skin is looked for: past it either way the block is whole or empty. */
    private static final double SKIN_REACH = 1.5;
    /**
     * How nearly another way in from a piece of shell has to match the straightest one (as the cosine of its angle to
     * the way the fuselage faces) for glass that way to count as the skin it closes over.
     */
    private static final double GLASS_TIE = 0.1;
    /** Where along the tailplane and the wings the control jets stand, as shares of their half span: outboard of the engines. */
    private static final double[] TAIL_JET_STATIONS = {0.6, 0.72, 0.84};
    private static final double[] WING_JET_STATIONS = {0.76, 0.82, 0.88};
    /**
     * The ailerons: the wings' lifting blocks from this share of their half span out to this share, aft of this share of
     * the chord. A lifting block turned over pushes so little against an airliner's weight that a real aileron's strip
     * along the trailing edge rolls it no faster than one step of the jets; so here the aileron is the whole outer half
     * of the wing, front to back, which rolls it about five times as hard.
     */
    private static final double AILERON_INBOARD = 0.45;
    private static final double AILERON_OUTBOARD = 0.97;
    private static final double AILERON_HINGE = 0;
    /**
     * The panels drawn to show the control surfaces at work, where an airliner's ailerons and elevators hang off the
     * trailing edges (see {@link #surfacePanels}): the ailerons from this share of the wing's half span, outboard of the
     * outer engine, to this one; the elevators from this share of the tailplane's to this one, past the tail cone; each
     * reaching aft about this share of the chord there, and at most this wide, in metres.
     */
    private static final double AILERON_PANEL_FROM = 0.7;
    private static final double AILERON_PANEL_TO = 0.95;
    private static final double ELEVATOR_PANEL_FROM = 0.2;
    private static final double ELEVATOR_PANEL_TO = 0.95;
    private static final double PANEL_CHORD = 0.2;
    private static final double MOST_PANEL_SPAN = 4;
    /**
     * The control surfaces a starboard lifting block can be part of: the right aileron, whose mirror image is the
     * left's, or the tailplane's, one way or the other.
     */
    private static final AutopilotBlockEntity.Channel[] AILERON = {AutopilotBlockEntity.Channel.ROLL_RIGHT};
    private static final AutopilotBlockEntity.Channel[] ELEVATOR = {
        AutopilotBlockEntity.Channel.PITCH_UP, AutopilotBlockEntity.Channel.PITCH_DOWN
    };
    /**
     * How far either side of the middle the pilots' seats stand: right beside the centre console, close enough together
     * that from either seat the whole console is within reach, but for the far thrust lever (any lever moves all four).
     */
    private static final int SEAT_X = 3;
    /** How many rows up from the lowest a column is looked up for the airframe's underside, in blocks. */
    private static final int COLUMN_SEARCH = 64;
    /**
     * Rows between the instrument panel and the pilots' seats, where their legs stretch out beside the centre console's
     * front row and its throttle quadrant.
     */
    private static final int LEGROOM = 2;
    /** The four engines' span stations, engines 1 to 4 from left to right, as their thrust levers stand. */
    private static final double[] ENGINE_STATIONS = {-ENGINE_X[1], -ENGINE_X[0], ENGINE_X[0], ENGINE_X[1]};
    /**
     * Where the flight deck ends at the bulkhead with the cockpit door, in metres from the nose: behind the pilots'
     * seats with room to move about, and ahead of the WC and the crew room.
     */
    private static final double COCKPIT_DOOR = 7.0;
    /**
     * The big cockpit door's left column, seen from the aisle: one to port of the middle, so that its doorway, three
     * blocks wide, stands in the middle of the bulkhead and the aisle, and swung open about its port side the panel
     * stands back along the port side of the aisle by a WC's wall, short of its door, and leaves the middle of the
     * aisle and the starboard side free to walk through.
     */
    private static final int COCKPIT_DOOR_LEFT = -1;
    /** How many blocks of standing room the flight deck keeps under the tail camera display. */
    private static final int TAIL_CAMERA_DISPLAY_HEADROOM = 2;
    /**
     * How many blocks of standing room the flight deck keeps under the announcement panel: enough to walk under, and
     * low enough that from either pilot's seat it is within reach overhead.
     */
    private static final int ANNOUNCEMENT_PANEL_HEADROOM = 3;
    /** How many rows of open space over the announcement panel, at most, are boxed in up to the crown. */
    private static final int ANNOUNCEMENT_PANEL_MOUNT = 2;
    /** How many rows of main deck floor stay clear between the foot of the steps down from the flight deck and the forward doors. */
    private static final int STEPS_CLEARANCE = 1;
    /** How far either side of the middle the steps up to the flight deck reach. */
    static final int STEPS_HALF_WIDTH = 1;
    /** Where along the cabin the seatbelt signs are, in metres from the nose: in every room and clear of the doors. */
    private static final double[] SEATBELT_SIGN_STATIONS = {16, 21, 30, 38, 45, 55};
    /** Where along the belly the drop bay is, in metres from the nose: behind the main gear, ahead of the cargo door. */
    private static final double DROP_BAY_STATION = 44;
    /**
     * Where along the cabin the hatch down to the hold is, in metres from the nose: a few steps aft of the drop bay, so
     * it can be reloaded in flight, just behind the gym hall's court and ahead of the aft doors' aisle.
     */
    private static final double HOLD_HATCH_STATION = 48;
    /** How many stations forward of that the hatch may move, to keep its border out of the doors' aisle. */
    private static final int HOLD_HATCH_SHIFT = 3;
    /** Rows of floor either side of a pair of passenger doors kept clear as its aisle. */
    private static final int DOOR_AISLE = 2;
    /** A player's height in blocks: the hatch only opens onto a hold someone can stand up in. */
    private static final int STANDING_ROOM = 2;
    /** How tall the main deck doors are, in metres: the A380's are 1.93 m. */
    private static final double PASSENGER_DOOR_HEIGHT = 1.93;
    /** How many rows tall the hold's cargo door is: all the hold leaves under the floor at the default scale. */
    private static final int CARGO_DOOR_ROWS = 2;
    /** How far in from the door's column the hull below the forward port door is looked through for the hold. */
    private static final int STAIR_MOUTH_SEARCH = 4;
    private static final TagKey<Block> SABLE_WEIGHTLESS = sableTag("weightless");
    private static final TagKey<Block> SABLE_SUPER_LIGHT = sableTag("super_light");
    private static final TagKey<Block> SABLE_LIGHT = sableTag("light");
    private static final TagKey<Block> SABLE_HEAVY = sableTag("heavy");
    private static final TagKey<Block> SABLE_SUPER_HEAVY = sableTag("super_heavy");

    private final double scale;
    private final AirframePalette palette;
    private final ShellStyle style;
    /** The stations of the windscreen's side posts, by side (1 to starboard, -1 to port) and the post's angle. */
    private final Map<Integer, Map<Double, Integer>> postStations = new HashMap<>();
    private final Map<BlockPos, BlockState> cells = new HashMap<>();
    /** Block entities to set up once placed, by layout position. */
    private final Map<BlockPos, Fitting> fittings = new HashMap<>();
    /** The WCs built into the cabin, which the overhead WC signs show the way to. */
    private final List<WcSigns.Wc> wcs = new ArrayList<>();
    /** Each engine's thrust relays, by the engine's span station (negative to port). */
    private final Map<Double, List<BlockPos>> engineRelays = new HashMap<>();
    /** The control jets' relays, by what they do. */
    private final Map<AutopilotBlockEntity.Channel, List<BlockPos>> jetRelays = new EnumMap<>(AutopilotBlockEntity.Channel.class);
    /** The lifting blocks of the autopilot's control surfaces, by what they do, each in the order they turn over. */
    private final Map<AutopilotBlockEntity.Channel, List<BlockPos>> surfaces = new EnumMap<>(AutopilotBlockEntity.Channel.class);
    /** The nose gear's steering relays, by the way they turn it, and the main gear's brake relays. */
    private final Map<String, List<BlockPos>> steerRelays = new HashMap<>();
    private final List<BlockPos> brakeRelays = new ArrayList<>();
    /** The retractable gear's legs, each with the bay at its top; none where the gear does not retract. */
    private final List<GearLeg> gearLegs = new ArrayList<>();
    /** The landing gear lever on the instrument panel; null without retractable gear or room for it. */
    @Nullable
    private BlockPos gearLever;
    /** The ground crew's call button on the nose gear; null where it found no room. */
    @Nullable
    private BlockPos callButton;
    /** The laser path clearer's switch on the flight deck; null with the laser switched off or no room for it. */
    @Nullable
    private BlockPos laserSwitch;
    /** Where the flight deck leaves room for the Physics Assembler, by the wall behind the captain; null without a flight deck. */
    private BlockPos assemblerSpot;
    /** The jump light switch in the instrument panel; null without a flight deck. */
    private BlockPos jumpSwitch;
    /** The seatbelt switch in the instrument panel; null without a flight deck. */
    private BlockPos seatbeltSwitch;
    /** The drop release on the first officer's side console; null without a flight deck. */
    private BlockPos dropRelease;
    /** The drop bay in the belly; null where it found no belly under the hold. */
    private BlockPos bay;
    /** The airstairs' bay under the cabin floor behind the forward port door; null where the hold has no room. */
    @Nullable
    private StairBay stairBay;
    /** The cabin lights' dimmer on the panel and the window shades' control by the captain; null without a flight deck. */
    private BlockPos cabinLights;
    private BlockPos windowShades;
    /** The doors cut through the hull's sides, in the order they were cut. */
    private final List<HullDoor> hullDoors = new ArrayList<>();
    /** The blocks under the doorways, from each door's sill in to the cabin, which the skin pass keeps whole. */
    private final Set<BlockPos> doorwayFloors = new HashSet<>();
    /** The cabin floor's block row, in the layout's own y before it is lifted onto its wheels. */
    private final int floorY;
    /** The station of the flight deck's raised floor's back edge, where the steps down to the main deck start. */
    private int flightDeckBack = -1;
    /** The upper deck, once its floor is laid; null where the airliner is too small for one. */
    @Nullable
    private UpperDeck upperDeck;
    private int minY = Integer.MAX_VALUE;
    /** The row the tires stand on, once the landing gear is down. */
    private int ground;

    private AirframeLayout(double scale, AirframePalette palette, ShellStyle style) {
        this.scale = scale;
        this.palette = palette;
        this.style = style;
        this.floorY = (int) Math.floor(FLOOR * HEIGHT * scale);
    }

    /** The finished layout, its lowest blocks (the tires) at y 0 and its nose tip at z 0. */
    public static AirframeLayout create(double scale, boolean furnished) {
        AirframeLayout layout = new AirframeLayout(scale, AirframePalette.fromConfig(), AerolargeConfig.SHELL_STYLE.get());
        layout.fuselage();
        layout.windows();
        layout.lights();
        layout.windscreen();
        layout.wings(WING_HALF_SPAN, WING_ROOT_CHORD, WING_TIP_CHORD, WING_ROOT_THICKNESS, WING_TIP_THICKNESS,
            AirframeShape::wingLeadingEdge, AirframeShape::wingY);
        layout.wings(STABILIZER_HALF_SPAN, STABILIZER_ROOT_CHORD, STABILIZER_TIP_CHORD, STABILIZER_ROOT_THICKNESS,
            STABILIZER_TIP_THICKNESS, x -> STABILIZER_ROOT_LEADING_EDGE + STABILIZER_SWEEP * x,
            x -> STABILIZER_Y + STABILIZER_DIHEDRAL * x);
        layout.fin();
        for (double x : ENGINE_X) {
            layout.engine(x);
            layout.engine(-x);
        }
        layout.controlJets();
        // The passenger doors hinge on their forward edges, which seen from outside is the right edge to starboard and
        // the left to port, and swing forward against the hull; the cargo door hinges along its top and swings up.
        for (double z : PASSENGER_DOORS) {
            layout.door(1, z, layout.floorY + 1, layout.passengerDoorRows(), PlugDoorBlock.Hinge.RIGHT);
            layout.door(-1, z, layout.floorY + 1, layout.passengerDoorRows(), PlugDoorBlock.Hinge.LEFT);
        }
        // Only where the hold is tall enough to walk into; at the smallest scales it is a crawlspace.
        if (layout.floorY - CARGO_DOOR_ROWS >= 1) {
            layout.door(1, CARGO_DOOR, layout.floorY - CARGO_DOOR_ROWS, CARGO_DOOR_ROWS, PlugDoorBlock.Hinge.TOP);
        }
        layout.fairings();
        layout.navLights();
        layout.flightDeck();
        // The upper deck's floor is structure, in both blueprints; the rooms under it are fitted out round it.
        layout.upperDeck = UpperDeck.lay(layout, furnished);
        // Furnished before the gear goes on: the gear stands under the centre of mass, pool and all.
        if (furnished) {
            new CabinFitOut(layout).apply();
        }
        new CabinLining(layout).apply(furnished ? CabinFitOut::lockersFit : metres -> true, 0, (int) Math.ceil(LENGTH * layout.scale));
        // The stairs up to it go in once the rooms below stand and are lined, and in the furnished airliner its seats.
        if (layout.upperDeck != null) {
            layout.upperDeck.finish();
            if (furnished) {
                layout.upperDeck.furnish();
            }
        }
        // Once every room stands, the overhead WC signs go up where they are seen on the way to the WCs.
        if (furnished) {
            new WcSigns(layout).apply();
        }
        layout.skydiving();
        layout.slideControls();
        layout.seatbeltSigns();
        layout.dropBay();
        layout.holdHatch();
        if (furnished) {
            new HoldFitOut(layout).apply();
            new DuckHunt(layout).apply();
        }
        // The airstairs' bay, behind the hull's skin, which the shell pass then cuts as ever: shut, the hatch is skin.
        layout.stairBay();
        layout.shell();
        layout.landingGear();
        layout.trimTail();
        // The control surfaces are picked once the tail's trim has turned its blocks the way they rest.
        layout.controlSurfaces();
        // And what the panels drawn for them are walked on, aft of the trailing edges.
        layout.panelFloors();
        // The skin is lined inside once it is cut and the gear meets it, before the airstairs keep their hatch's skin.
        new HullLining(layout).apply();
        layout.boardingStairs();
        // Once the skin is cut and lined and everything by the doors stands, so they only go on whole panels.
        layout.doorButtons();
        layout.assembler();
        layout.crewLocker();
        layout.cabinControls();
        // Once everything hanging on the gear legs stands (the call button), each leg knows its blocks.
        layout.wireGear();
        // In the nose's skin as finally cut and lined.
        layout.laserTurret();
        return layout.lifted();
    }

    public Map<BlockPos, BlockState> cells() {
        return cells;
    }

    double scale() {
        return scale;
    }

    /** Where the flight deck leaves room for the Physics Assembler, which must stay open; null without a flight deck. */
    @Nullable
    BlockPos assemblerSpot() {
        return assemblerSpot;
    }

    int floorY() {
        return floorY;
    }

    /** The upper deck floor's row, UPPER_DECK over the main deck's, whether the airliner has room for it or not. */
    int upperDeckY() {
        return floorY + (int) Math.round(UPPER_DECK * scale);
    }

    /** The station of the flight deck's raised floor's back edge, where the steps down to the main deck start. */
    int flightDeckBack() {
        return flightDeckBack;
    }

    /** The upper deck, once its floor is laid; null where the airliner is too small for one. */
    @Nullable
    UpperDeck upperDeck() {
        return upperDeck;
    }

    /**
     * Whether the block is the upper deck's floor, the cabin lights set into it included, or over it: the floor closes
     * off the main deck under it as the hull's crown does, and nothing hung from the main deck's ceiling reaches
     * through it.
     */
    boolean onDeck(int x, int y, int z) {
        if (upperDeck == null || z <= flightDeckBack || y < upperDeckY()) {
            return false;
        }
        BlockState deck = stateAt(x, upperDeckY(), z);
        return deck.is(palette.floor().getBlock()) || deck.is(palette.light().getBlock());
    }

    AirframePalette palette() {
        return palette;
    }

    /** The drop bay in the belly, once laid; null without one. */
    @Nullable
    BlockPos bay() {
        return bay;
    }

    /** Whether the block at this position is part of the cabin or hold's open space. */
    boolean isOpen(int x, int y, int z) {
        BlockState state = cells.get(new BlockPos(x, y, z));
        return state != null && state.isAir();
    }

    boolean isCabinFloor(int x, int y, int z) {
        BlockState state = cells.get(new BlockPos(x, y, z));
        return state != null && state.is(palette.floor().getBlock());
    }

    /** Whether the block at this position is plain hull: no window, light or anything else set into it. */
    boolean isPlainHull(int x, int y, int z) {
        return stateAt(x, y, z).is(palette.hull().getBlock());
    }

    /** Puts a fitting into the cabin; only where there was open space, so the hull is never cut into. */
    void furnish(int x, int y, int z, BlockState state) {
        if (isOpen(x, y, z)) {
            set(x, y, z, state);
        }
    }

    /**
     * Puts a cabin seat into the cabin, the seat (the lower half given) at the spot and the top of its back over it;
     * only where both are open, and where the top of the back would not stand against a window and cover it. Whether
     * it went in.
     */
    boolean seat(int x, int y, int z, BlockState seat) {
        return !besideWindow(x, y + 1, z) && tall(x, y, z, seat);
    }

    /** Whether a window's pane is right beside this block, which anything standing in it against the wall would cover. */
    boolean besideWindow(int x, int y, int z) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (CabinWindowBlock.isPane(stateAt(x + side.getStepX(), y, z + side.getStepZ()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts a fitting two blocks tall into the cabin, a seat or a basin with its mirror: the lower half given at the
     * spot and its upper half over it; only where both are open. Whether it went in.
     */
    boolean tall(int x, int y, int z, BlockState lower) {
        if (!isOpen(x, y, z) || !isOpen(x, y + 1, z)) {
            return false;
        }
        set(x, y, z, lower);
        set(x, y + 1, z, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        return true;
    }

    /** Swaps a stretch of cabin floor (and only cabin floor) for another surface: tiles, parquet, water. */
    void refloor(int x, int y, int z, BlockState state) {
        if (isCabinFloor(x, y, z)) {
            set(x, y, z, state);
        }
    }

    /**
     * Swaps a block of the hull's skin, a plain panel or a cabin light set into it, for clear hull glass, which the
     * shell then cuts to the curve with the skin around it, as it does the windscreen's; anything else stays. It has to
     * come before the shell pass, which is what does the cutting.
     */
    void glaze(int x, int y, int z) {
        BlockState state = stateAt(x, y, z);
        if (state.is(palette.hull().getBlock()) || state.is(palette.light().getBlock())) {
            set(x, y, z, AerolargeRegistry.HULL_GLASS.get().defaultBlockState());
        }
    }

    /** How far out from the centreline the cabin is open at this height and station; -1 where it is closed. */
    int openHalfWidth(int y, int z) {
        int x = -1;
        while (isOpen(x + 1, y, z)) {
            x++;
        }
        return x;
    }

    /**
     * The cabin's block against the hull on one side (1 to starboard, -1 to port) at this height and station, found
     * from outside in through the skin; 0 where the hull closes in to the middle. Unlike {@link #openHalfWidth}, which
     * measures out from the middle and only to starboard, it finds each wall on its own, so nothing standing in the
     * cabin, on either side, is taken for the wall. Whatever already stands in that block is the caller's to check.
     */
    int besideWall(int side, int y, int z) {
        int x = side * ((int) Math.ceil(WIDTH / 2 * scale) + 1);
        while (x != 0 && (!inFuselage(x, y, z) || inSkin(new BlockPos(x, y, z)))) {
            x -= side;
        }
        return x;
    }

    /** The highest open block above the floor at this column, below the crown of the hull. */
    int ceiling(int x, int z) {
        int y = floorY + 1;
        while (isOpen(x, y + 1, z)) {
            y++;
        }
        return y;
    }

    /** Puts a block anywhere, over whatever was there. */
    void set(int x, int y, int z, BlockState state) {
        cells.put(new BlockPos(x, y, z), state);
        minY = Math.min(minY, y);
    }

    BlockState stateAt(int x, int y, int z) {
        return cells.getOrDefault(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
    }

    /**
     * Joins every block of the layout that joins (see {@link Joining}) on to what the layout holds round it: nothing in
     * the layout updates its neighbours as it goes in, so they are joined once everything round them stands.
     */
    void joinAll() {
        List<BlockPos> joining = cells.entrySet().stream().filter(cell -> cell.getValue().getBlock() instanceof Joining).map(Map.Entry::getKey).toList();
        for (BlockPos pos : joining) {
            BlockState state = cells.get(pos);
            cells.put(pos, ((Joining) state.getBlock()).joined(state, way -> cells.getOrDefault(pos.relative(way), Blocks.AIR.defaultBlockState())));
        }
    }

    private boolean has(int x, int y, int z) {
        return cells.containsKey(new BlockPos(x, y, z));
    }

    boolean inFuselage(int x, int y, int z) {
        return AirframeShape.inFuselage(x / scale, (y + 0.5) / scale, (z + 0.5) / scale);
    }

    private boolean inFuselage(BlockPos pos) {
        return inFuselage(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Whether a point in the layout, in blocks (a block spans its own coordinates to the next), is inside the fuselage. */
    boolean inFuselageAt(double x, double y, double z) {
        return AirframeShape.inFuselage((x - 0.5) / scale, y / scale, z / scale);
    }

    /**
     * How the fuselage's skin cuts through the block, as a {@link ShellGeometry} shape: the whole block where the skin
     * lies beyond it, and -1 where it leaves next to nothing of it. The skin is found from the block's middle, straight
     * out the way the fuselage faces there.
     */
    int shapeAt(int x, int y, int z) {
        double cx = x / scale;
        double cy = (y + 0.5) / scale;
        double cz = (z + 0.5) / scale;
        double[] out = AirframeShape.outward(cx, cy, cz);
        // How far out from the middle, in blocks, the skin lies: found by halving between a point inside and one out.
        double in = -SKIN_REACH;
        double beyond = SKIN_REACH;
        if (AirframeShape.inFuselage(cx + out[0] * beyond / scale, cy + out[1] * beyond / scale, cz + out[2] * beyond / scale)) {
            return ShellGeometry.WHOLE;
        }
        if (!AirframeShape.inFuselage(cx + out[0] * in / scale, cy + out[1] * in / scale, cz + out[2] * in / scale)) {
            return -1;
        }
        for (int k = 0; k < 24; k++) {
            double middle = (in + beyond) / 2;
            if (AirframeShape.inFuselage(cx + out[0] * middle / scale, cy + out[1] * middle / scale, cz + out[2] * middle / scale)) {
                in = middle;
            } else {
                beyond = middle;
            }
        }
        double skin = (in + beyond) / 2;
        return ShellGeometry.cut(out[0], out[1], out[2], 0.5 + out[0] * skin, 0.5 + out[1] * skin, 0.5 + out[2] * skin);
    }

    /** Whether the block at the position is the fuselage's skin: inside it and touching the outside. */
    boolean inSkin(BlockPos pos) {
        if (!inFuselage(pos)) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (!inFuselage(pos.relative(side))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The hull: every block of the fuselage that touches the outside is skin, the rest is cabin and hold, split by
     * the main deck floor.
     */
    private void fuselage() {
        int halfWidth = (int) Math.ceil(WIDTH / 2 * scale) + 1;
        int height = (int) Math.ceil(HEIGHT * scale) + 1;
        int length = (int) Math.ceil(LENGTH * scale) + 1;
        for (int z = -1; z <= length; z++) {
            for (int y = -1; y <= height; y++) {
                for (int x = -halfWidth; x <= halfWidth; x++) {
                    if (!inFuselage(x, y, z)) {
                        continue;
                    }
                    boolean skin = !inFuselage(x + 1, y, z) || !inFuselage(x - 1, y, z)
                        || !inFuselage(x, y + 1, z) || !inFuselage(x, y - 1, z)
                        || !inFuselage(x, y, z + 1) || !inFuselage(x, y, z - 1);
                    if (skin) {
                        set(x, y, z, palette.hull());
                    } else {
                        set(x, y, z, y == floorY ? palette.floor() : Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /**
     * Two rows of cabin windows down each side, each window two blocks tall, taller than wide like an airliner's: one
     * row from eye level over the main deck up past the eyes of someone seated, the other the same over the upper
     * deck's floor (see {@link UpperDeck}), as an A380's two window lines are. They stand on every other station,
     * never one a wall light or a door takes, wherever the hull faces out sideways, flat or curved: the skin pass cuts
     * them to the curve along with the skin around them.
     */
    private void windows() {
        int mainRow = floorY + 2;
        int upperRow = upperDeckY() + 2;
        int first = (int) Math.ceil((NOSE_LENGTH + 1) * scale);
        int last = (int) Math.floor((TAIL_START + 8) * scale);
        // The wall lights all stand on stations of one parity (see lights()): the windows take the other.
        if (Math.floorMod(first - LIGHT_SPACING / 2, 2) == 0) {
            first++;
        }
        for (int z = first; z <= last; z += 2) {
            if (doorAt(z)) {
                continue;
            }
            for (int bottom : new int[]{mainRow, upperRow}) {
                for (int y = bottom; y < bottom + WINDOW_HEIGHT; y++) {
                    for (int side : new int[]{1, -1}) {
                        window(side, y, z);
                    }
                }
            }
        }
    }

    /**
     * One row of a window on one side at station z: a pane in every hull block from the cabin's wall out to the skin,
     * where the hull there faces out sideways. The skin pass joins the panes into tall windows once they are cut.
     */
    private void window(int side, int y, int z) {
        int open = openHalfWidth(y, z);
        if (open < 0) {
            return;
        }
        double[] out = AirframeShape.outward(side * (open + 1) / scale, (y + 0.5) / scale, (z + 0.5) / scale);
        if (Math.abs(out[0]) < WINDOW_FACING) {
            return;
        }
        for (int x = side * (open + 1); inFuselage(x, y, z) && stateAt(x, y, z).is(palette.hull().getBlock()); x += side) {
            set(x, y, z, palette.cabinWindow());
        }
    }

    /**
     * Whether one of the passenger doors, cut through the hull later, takes the station, or the controls set into the
     * hull just beside it (the airstairs' panel, a jump light, a slide's control).
     */
    boolean doorAt(int z) {
        for (double station : PASSENGER_DOORS) {
            int fore = (int) Math.round(station * scale);
            if (z >= fore - 1 && z <= fore + PlugDoorBlock.COLUMNS) {
                return true;
            }
        }
        return false;
    }

    /**
     * How many rows tall the main deck doors are: the A380's height at this scale, at least a player's, and no taller
     * than a plug door may be, so a jet bridge's tunnel frames the whole door.
     */
    int passengerDoorRows() {
        return Math.clamp(Math.round(PASSENGER_DOOR_HEIGHT * scale), STANDING_ROOM, PlugDoorBlock.MOST_ROWS);
    }

    /** The doors cut through the hull's sides so far. */
    List<HullDoor> hullDoors() {
        return hullDoors;
    }

    /**
     * A door cut through the hull's side (1 to starboard, -1 to port): its forward station, the row of its sill, the
     * column it stands in and how many rows tall it is.
     */
    record HullDoor(int side, int fore, int bottom, int outer, int rows) {
        /** Where the door's bottom left part is, as seen from outside, which keeps its block entity. */
        BlockPos controller() {
            return new BlockPos(outer, bottom, side > 0 ? fore + PlugDoorBlock.COLUMNS - 1 : fore);
        }

        /** The middle of the doorway across its sill, (x, z): where decompression measures its pull from. */
        Vec2 middle() {
            return new Vec2(outer + 0.5F, fore + PlugDoorBlock.COLUMNS / 2F);
        }
    }

    /** Whether a seatbelt sign goes into the cabin walls at the station, once the cabin is fitted out. */
    boolean seatbeltSignAt(int z) {
        for (double station : SEATBELT_SIGN_STATIONS) {
            if ((int) Math.round(station * scale) == z) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cabin lights set into the hull, on every few stations: one in the crown and one in each wall just above head
     * height over the main deck, and one in the belly of the hold. Spaced so that light reaches every block of the
     * cabin and hold when they are bright; mobs are kept out by the spawn guard, not by the light, so the dimmer can
     * turn them down to a night glow.
     */
    private void lights() {
        int wallRow = floorY + 3;
        for (int z = 0; z < LENGTH * scale; z++) {
            if (Math.floorMod(z, LIGHT_SPACING) == 0 && isOpen(0, floorY + 1, z)) {
                light(0, ceiling(0, z) + 1, z);
            }
            if (Math.floorMod(z, LIGHT_SPACING) == LIGHT_SPACING / 2 && openHalfWidth(wallRow, z) >= 0) {
                int x = openHalfWidth(wallRow, z) + 1;
                light(x, wallRow, z);
                light(-x, wallRow, z);
            }
            if (Math.floorMod(z, LIGHT_SPACING) == 0 && isOpen(0, floorY - 1, z)) {
                int y = floorY - 1;
                while (isOpen(0, y - 1, z)) {
                    y--;
                }
                light(0, y - 1, z);
            }
        }
    }

    /**
     * Swaps a plain hull panel for a light; windows, doors and anything else stay, and so does skin that will be cut
     * to the curve, which a whole light would stick out of.
     */
    private void light(int x, int y, int z) {
        if (stateAt(x, y, z).is(palette.hull().getBlock()) && shapeAt(x, y, z) == ShellGeometry.WHOLE) {
            set(x, y, z, palette.light());
        }
    }

    /**
     * A pair of swept, tapered lifting surfaces, mirrored to both sides: the wings, or the tailplane. Its middle
     * layer is the lifting block, laid flat; the rest of its thickness is skin. Inside the fuselage nothing is
     * placed, so the hull stays whole and the hold stays open.
     */
    private void wings(double halfSpan, double rootChord, double tipChord, double rootThickness, double tipThickness,
                       DoubleUnaryOperator leadingEdge, DoubleUnaryOperator centreY) {
        int span = (int) Math.ceil(halfSpan * scale);
        for (int xb = 0; xb <= span; xb++) {
            double x = xb / scale;
            double out = x / halfSpan;
            double front = leadingEdge.applyAsDouble(x);
            double chord = lerp(rootChord, tipChord, out);
            double thickness = lerp(rootThickness, tipThickness, out);
            double centre = centreY.applyAsDouble(x);
            int core = (int) Math.floor(centre * scale);
            for (int z = (int) Math.floor(front * scale); z <= Math.ceil((front + chord) * scale); z++) {
                double chordwise = ((z + 0.5) / scale - front) / chord;
                if (chordwise < 0 || chordwise > 1) {
                    continue;
                }
                double half = thickness * airfoil(chordwise) / 2;
                for (int y = (int) Math.floor((centre - half) * scale) - 1; y <= Math.ceil((centre + half) * scale); y++) {
                    if (y != core && Math.abs((y + 0.5) / scale - centre) > half) {
                        continue;
                    }
                    BlockState state = y == core ? palette.lift(Direction.UP, palette.hull()) : palette.hull();
                    for (int side : new int[]{1, -1}) {
                        if (!inFuselage(side * xb, y, z) && !has(side * xb, y, z)) {
                            set(side * xb, y, z, state);
                        }
                    }
                }
            }
        }
    }

    /**
     * The fin: swept back over the tail cone, livery on both faces around an upright lifting layer. A lifting block
     * pushes along its face's normal at any speed, whichever way the air meets it, so the layer alternates between
     * facing port and starboard: the pushes cancel and only the part that steadies the tail against sideslip is left.
     */
    private void fin() {
        int bottom = (int) Math.floor((HEIGHT - 2) * scale);
        int top = (int) Math.ceil((HEIGHT + FIN_HEIGHT) * scale);
        for (int y = bottom; y <= top; y++) {
            double up = Math.max(0, (y + 0.5) / scale - HEIGHT);
            if (up > FIN_HEIGHT) {
                continue;
            }
            double front = FIN_ROOT_LEADING_EDGE + FIN_SWEEP * up;
            double chord = lerp(FIN_ROOT_CHORD, FIN_TIP_CHORD, up / FIN_HEIGHT);
            double thickness = lerp(FIN_ROOT_THICKNESS, FIN_TIP_THICKNESS, up / FIN_HEIGHT);
            for (int z = (int) Math.floor(front * scale); z <= Math.ceil((front + chord) * scale); z++) {
                double chordwise = ((z + 0.5) / scale - front) / chord;
                if (chordwise < 0 || chordwise > 1) {
                    continue;
                }
                double half = thickness * airfoil(chordwise) / 2;
                int reach = (int) Math.ceil(half * scale);
                for (int x = -reach; x <= reach; x++) {
                    if (x != 0 && Math.abs(x / scale) > half || inFuselage(x, y, z) || has(x, y, z)) {
                        continue;
                    }
                    Direction face = Math.floorMod(y + z, 2) == 0 ? Direction.EAST : Direction.WEST;
                    set(x, y, z, x == 0 ? palette.lift(face, palette.livery()) : palette.livery());
                }
            }
        }
    }

    /**
     * Rounds off the airframe: every plain skin block that is the outer step of a curve, open to the outside on one
     * side and above or below it, gets its outer edge cut at 45 degrees. Only where the step runs on along the
     * block, so the cuts line up into smooth facets along the nacelles, the wings' edges and the tail.
     */
    private void fairings() {
        BlockState fairing = AerolargeRegistry.AIRFRAME_FAIRING.get().defaultBlockState();
        Map<BlockPos, BlockState> cut = new HashMap<>();
        // Not on the fuselage: its skin is cut to the curve by the shell instead.
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            if (!cell.getValue().is(palette.hull().getBlock()) || inFuselage(cell.getKey())) {
                continue;
            }
            BlockPos pos = cell.getKey();
            BlockState chosen = null;
            int found = 0;
            for (Direction out : Direction.Plane.HORIZONTAL) {
                for (Direction vertical : new Direction[]{Direction.UP, Direction.DOWN}) {
                    Direction along = out.getClockWise();
                    boolean step = !cells.containsKey(pos.relative(out)) && !cells.containsKey(pos.relative(vertical))
                        && !cells.containsKey(pos.relative(out).relative(vertical))
                        && cells.containsKey(pos.relative(out.getOpposite())) && cells.containsKey(pos.relative(vertical.getOpposite()))
                        && isSkin(pos.relative(along)) && isSkin(pos.relative(along.getOpposite()));
                    if (step) {
                        found++;
                        chosen = fairing.setValue(FairingBlock.FACING, out)
                            .setValue(FairingBlock.HALF, vertical == Direction.UP ? Half.TOP : Half.BOTTOM);
                    }
                }
            }
            if (found == 1) {
                cut.put(pos, chosen);
            }
        }
        cells.putAll(cut);
    }

    private boolean isSkin(BlockPos pos) {
        BlockState state = cells.get(pos);
        return state != null && (state.is(palette.hull().getBlock()) || state.is(AerolargeRegistry.AIRFRAME_FAIRING.get()));
    }

    /**
     * The exterior lights: a red navigation light on the port wingtip and a green one on the starboard tip, each with
     * a white strobe just behind it, a white light on the tail cone, and red beacons on top of and under the
     * fuselage.
     */
    private void navLights() {
        BlockState light = AerolargeRegistry.NAV_LIGHT.get().defaultBlockState();
        for (int side : new int[]{1, -1}) {
            Direction out = side > 0 ? Direction.EAST : Direction.WEST;
            BlockPos tip = outermost(side);
            if (tip != null) {
                NavLightBlock.Kind nav = side > 0 ? NavLightBlock.Kind.GREEN : NavLightBlock.Kind.RED;
                lamp(tip.relative(out), light.setValue(NavLightBlock.FACING, out).setValue(NavLightBlock.KIND, nav));
                lamp(tip.relative(out).south(), light.setValue(NavLightBlock.FACING, out).setValue(NavLightBlock.KIND, NavLightBlock.Kind.STROBE));
            }
        }
        int tail = -1;
        int tailY = 0;
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            BlockPos pos = cell.getKey();
            if (pos.getX() == 0 && !cell.getValue().isAir() && inFuselage(0, pos.getY(), pos.getZ()) && pos.getZ() > tail) {
                tail = pos.getZ();
                tailY = pos.getY();
            }
        }
        if (tail >= 0) {
            lamp(new BlockPos(0, tailY, tail + 1), light.setValue(NavLightBlock.FACING, Direction.SOUTH).setValue(NavLightBlock.KIND, NavLightBlock.Kind.WHITE));
        }
        int beaconZ = (int) Math.round(BEACON_STATION * scale);
        int top = Integer.MIN_VALUE;
        int bottom = Integer.MAX_VALUE;
        for (int y = -16; y < HEIGHT * scale + 16; y++) {
            if (has(0, y, beaconZ) && inFuselage(0, y, beaconZ)) {
                top = Math.max(top, y);
                bottom = Math.min(bottom, y);
            }
        }
        if (top != Integer.MIN_VALUE) {
            BlockState beacon = light.setValue(NavLightBlock.KIND, NavLightBlock.Kind.BEACON);
            lamp(new BlockPos(0, top + 1, beaconZ), beacon.setValue(NavLightBlock.FACING, Direction.UP));
            lamp(new BlockPos(0, bottom - 1, beaconZ), beacon.setValue(NavLightBlock.FACING, Direction.DOWN));
        }
    }

    /** The wingtip: the wing's lifting layer at its outermost span station on one side, or null without wings. */
    private BlockPos outermost(int side) {
        BlockPos tip = null;
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            BlockPos pos = cell.getKey();
            boolean wing = pos.getY() < HEIGHT * scale && !cell.getValue().isAir() && !inFuselage(pos.getX(), pos.getY(), pos.getZ());
            if (wing && Integer.signum(pos.getX()) == side && (tip == null || Math.abs(pos.getX()) > Math.abs(tip.getX()))) {
                tip = pos;
            }
        }
        return tip;
    }

    private void lamp(BlockPos pos, BlockState state) {
        if (!has(pos.getX(), pos.getY(), pos.getZ())) {
            set(pos.getX(), pos.getY(), pos.getZ(), state);
        }
    }

    /**
     * The windscreen: the skin under the glass becomes cockpit glass, but for the posts that frame it into panes,
     * which stay skin. Each post stands upright at one station along the nose on each side: the one whose skin, at
     * the pilots' eye level, faces closest to the post's angle round the nose. Picked row by row instead, the post
     * would wander back a block every row or two up the curving nose and break up into specks of skin across the
     * glass. The middle column is glass like the rest (it is counted with the starboard side), so the front is one
     * wide pane. The shell cuts the glass to the curve with the skin around it.
     */
    private void windscreen() {
        BlockState glass = AerolargeRegistry.COCKPIT_GLASS.get().defaultBlockState();
        int last = (int) Math.ceil(NOSE_LENGTH * scale);
        int halfWidth = (int) Math.ceil(WIDTH / 2 * scale) + 1;
        int eyes = (int) Math.floor(Windscreen.EYES * scale);
        for (int side : new int[]{1, -1}) {
            Map<Double, Integer> stations = new HashMap<>();
            postStations.put(side, stations);
            Map<Integer, List<BlockPos>> rows = new HashMap<>();
            for (int y = 0; y <= HEIGHT * scale; y++) {
                for (int z = 0; z <= last; z++) {
                    for (int x = side > 0 ? 0 : -1; Math.abs(x) <= halfWidth; x += side) {
                        BlockState state = stateAt(x, y, z);
                        boolean skin = state.is(palette.hull().getBlock()) || state.is(palette.light().getBlock());
                        if (skin && Windscreen.covers((y + 0.5) / scale, (z + 0.5) / scale)) {
                            rows.computeIfAbsent(y, ignored -> new ArrayList<>()).add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
            for (double post : Windscreen.SIDE_POSTS) {
                rows.getOrDefault(eyes, List.of()).stream()
                    .filter(pos -> Math.abs(facing(pos) - post) <= Windscreen.POST_TOLERANCE)
                    .min(Comparator.comparingDouble(pos -> Math.abs(facing(pos) - post)))
                    .ifPresent(pos -> stations.put(post, pos.getZ()));
            }
            for (List<BlockPos> row : rows.values()) {
                for (BlockPos pos : row) {
                    if (windscreenAt(pos)) {
                        set(pos.getX(), pos.getY(), pos.getZ(), glass);
                    }
                }
            }
        }
    }

    /**
     * Whether skin at this block is the windscreen's glass: under the glass and not one of its posts. The shell pass
     * asks it too, for the skin it adds around the curve, so the glass ends along the same rows and stations.
     */
    boolean windscreenAt(BlockPos pos) {
        if (!Windscreen.covers((pos.getY() + 0.5) / scale, (pos.getZ() + 0.5) / scale)) {
            return false;
        }
        Map<Double, Integer> stations = postStations.getOrDefault(pos.getX() >= 0 ? 1 : -1, Map.of());
        return stations.entrySet().stream().noneMatch(station -> pos.getZ() == station.getValue()
            && Math.abs(facing(pos) - station.getKey()) <= Windscreen.POST_TOLERANCE);
    }

    /** Which way the fuselage's skin faces at a block, in degrees round from straight ahead: 0 forward, 90 sideways. */
    private double facing(BlockPos pos) {
        double[] out = AirframeShape.outward(pos.getX() / scale, (pos.getY() + 0.5) / scale, (pos.getZ() + 0.5) / scale);
        return Math.toDegrees(Math.atan2(Math.abs(out[0]), -out[2]));
    }

    /**
     * Skydiving: a jump light in the wall just ahead of the aft passenger doors and the cargo door, each with the
     * relay that lights it beside it in the wall, and a rack of parachutes on the floor by it; the jump switch in the
     * panel is wired to them all.
     */
    private void skydiving() {
        List<BlockPos> relays = new ArrayList<>();
        for (int side : new int[]{1, -1}) {
            jumpDoor(side, PASSENGER_DOORS[PASSENGER_DOORS.length - 1], floorY + 1, relays);
        }
        if (floorY - CARGO_DOOR_ROWS >= 1) {
            jumpDoor(1, CARGO_DOOR, floorY - CARGO_DOOR_ROWS, relays);
        }
        if (jumpSwitch == null || relays.isEmpty()) {
            return;
        }
        BlockPos at = jumpSwitch;
        fittings.put(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof LinkedControlBlockEntity control) {
                control.configure(Map.of(JumpSwitchBlock.LIGHTS, offsets(at, relays, rotation)));
            }
        });
    }

    /** The jump light by one door: in the hull just ahead of it, in one of the door's rows; the rack on the floor inside by it. */
    private void jumpDoor(int side, double station, int bottom, List<BlockPos> relays) {
        int z = (int) Math.round(station * scale) - 1;
        BlockPos light = wallLamp(side, z, new int[]{bottom + 1, bottom, bottom + 2}, AerolargeRegistry.JUMP_LIGHT.get().defaultBlockState(), relays);
        if (light == null || !isOpen(light.getX() - side, bottom, z)) {
            return;
        }
        // Facing the door's aisle, aft of it, standing on the floor: at the biggest scales the hold's floor curves on
        // down a little below the cargo door's sill.
        int y = bottom;
        while (isOpen(light.getX() - side, y - 1, z)) {
            y--;
        }
        set(light.getX() - side, y, z, AerolargeRegistry.PARACHUTE_RACK.get().defaultBlockState().setValue(ParachuteRackBlock.FACING, Direction.SOUTH));
    }

    /**
     * Evacuation slides at the passenger doors, but for the forward port door, which has the airstairs (and the cargo
     * door in the hold, which is no way out for passengers): each worked by a control set into the cabin wall beside
     * its door, its panel facing into the cabin, a block above the sill in the jamb just aft of the door, or failing
     * that just ahead of it (the aft doors' jump lights take that one), or failing both level with the sill; each where
     * it replaces plain skin with open cabin in front of it. The slides themselves are packed in the doors, so nothing
     * more is built for them.
     */
    private void slideControls() {
        HullDoor stairs = boardingDoor();
        for (HullDoor door : hullDoors) {
            if (door == stairs || door.bottom() != floorY + 1) {
                continue;
            }
            BlockPos control = slideControl(door);
            if (control != null) {
                BlockPos served = door.controller().subtract(control);
                fittings.put(control, (blockEntity, rotation) -> {
                    if (blockEntity instanceof SlideControlBlockEntity slide) {
                        slide.configure(served.rotate(rotation));
                    }
                });
            }
        }
    }

    /** The slide control beside one door, set into the wall in the first spot that has room for it; null for none. */
    @Nullable
    private BlockPos slideControl(HullDoor door) {
        int[] jambs = {door.fore() + PlugDoorBlock.COLUMNS, door.fore() - 1};
        BlockState control = AerolargeRegistry.SLIDE_CONTROL.get().defaultBlockState()
            .setValue(SlideControlBlock.FACING, door.side() > 0 ? Direction.WEST : Direction.EAST);
        for (int y : new int[]{door.bottom() + 1, door.bottom()}) {
            for (int z : jambs) {
                int inside = besideWall(door.side(), y, z);
                int wall = inside + door.side();
                if (inside != 0 && isOpen(inside, y, z) && isPlainHull(wall, y, z)) {
                    set(wall, y, z, control);
                    return new BlockPos(wall, y, z);
                }
            }
        }
        return null;
    }

    /**
     * The drop bay: set into the belly on the centreline behind the main gear, the lowest block of the hull with the
     * hold above it, so it can be loaded from the hold; wired to the drop release on the flight deck.
     */
    private void dropBay() {
        int station = (int) Math.round(DROP_BAY_STATION * scale);
        for (int dz : new int[]{0, 1, -1, 2, -2}) {
            int z = station + dz;
            for (int y = 0; y < floorY; y++) {
                if (stateAt(0, y, z).is(palette.hull().getBlock()) && isOpen(0, y + 1, z)) {
                    BlockPos laid = new BlockPos(0, y, z);
                    bay = laid;
                    set(0, y, z, AerolargeRegistry.DROP_BAY.get().defaultBlockState());
                    if (dropRelease != null) {
                        BlockPos at = dropRelease;
                        fittings.put(at, (blockEntity, rotation) -> {
                            if (blockEntity instanceof DropReleaseBlockEntity control) {
                                control.configure(laid.subtract(at).rotate(rotation));
                            }
                        });
                    }
                    return;
                }
            }
        }
    }

    /**
     * The hatch down to the hold, so the drop bay can be reloaded in flight: a trapdoor in the cabin floor over a
     * ladder down to the hold's floor, bolted to a post of hull panels, inside a border of yellow and black carpet. It
     * stands on the port side, at the edge of the hold's floor where the ladder reaches all the way down, well away
     * from the cargo door to starboard, whose rushing air reaches only a few steps in. Only where the floor there is
     * whole, the cabin above it clear and the hold below open and tall enough to stand in (at the smallest scales it
     * is a crawlspace), with its border clear of the doors' aisle; failing that, a station further forward.
     */
    private void holdHatch() {
        int station = (int) Math.round(HOLD_HATCH_STATION * scale);
        for (int z = station; z >= station - HOLD_HATCH_SHIFT; z--) {
            if (!inDoorAisle(z - 1) && !inDoorAisle(z + 1) && holdHatch(z)) {
                return;
            }
        }
    }

    /** Whether station z is in one of the passenger doors' aisles. */
    boolean inDoorAisle(int z) {
        for (double station : PASSENGER_DOORS) {
            int fore = (int) Math.round(station * scale);
            if (z >= fore - DOOR_AISLE && z <= fore + DOOR_AISLE) {
                return true;
            }
        }
        return false;
    }

    /** The hatch at station z, if it fits there; whether it did. */
    private boolean holdHatch(int z) {
        int bottom = 0;
        while (bottom < floorY && !isOpen(0, bottom, z)) {
            bottom++;
        }
        if (floorY - bottom < STANDING_ROOM) {
            return false;
        }
        int x = -openHalfWidth(bottom, z);
        boolean fits = isOpen(x, floorY + 1, z) && isOpen(x, floorY + 2, z) && wholeFloor(x, z);
        for (int y = bottom; y < floorY; y++) {
            fits &= isOpen(x, y, z) && isOpen(x, y, z - 1);
        }
        if (!fits) {
            return false;
        }
        // Hinged at the top of the floor's row, so it lies flush with the floor; it opens against the post's side,
        // facing the way the ladder does, which is what lets an open hatch be climbed like the ladder under it.
        set(x, floorY, z, AerolargeRegistry.FLOOR_HATCH.get().defaultBlockState()
            .setValue(TrapDoorBlock.FACING, Direction.SOUTH)
            .setValue(TrapDoorBlock.HALF, Half.TOP));
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH);
        for (int y = bottom; y < floorY; y++) {
            set(x, y, z - 1, palette.hull());
            set(x, y, z, ladder);
        }
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dz != 0) && wholeFloor(x + dx, z + dz)) {
                    furnish(x + dx, floorY + 1, z + dz, (Math.floorMod(dx + dz, 2) == 0 ? Blocks.YELLOW_CARPET : Blocks.BLACK_CARPET).defaultBlockState());
                }
            }
        }
        return true;
    }

    /** Whether the deck's floor at this column is a whole block to stand on, whatever it is laid with: no pool, no hole. */
    private boolean wholeFloor(int x, int z) {
        return stateAt(x, floorY, z).isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /**
     * "Fasten seatbelts" signs in both cabin walls along the cabin, at head height under the overhead lockers, and over
     * the upper deck's walkways beside them, each lit through a relay beside it; the seatbelt switch in the panel is
     * wired to them all, and to every curtain the fit-out has hung, which it opens front to back as the signs come on.
     */
    private void seatbeltSigns() {
        List<BlockPos> relays = new ArrayList<>();
        int head = floorY + 4;
        // Over the upper deck at the height of its windows, between two of them: a station on or back where one takes
        // the wall, or a banner hangs there.
        int[] upperRows = {upperDeckY() + 2};
        BlockState sign = AerolargeRegistry.SEATBELT_SIGN.get().defaultBlockState();
        for (double station : SEATBELT_SIGN_STATIONS) {
            int z = (int) Math.round(station * scale);
            for (int side : new int[]{1, -1}) {
                // Its face looks in across the cabin, to the middle; outside it is plated like the skin.
                BlockState facingIn = SeatbeltSignBlock.facingInto(sign, side > 0 ? Direction.WEST : Direction.EAST);
                wallLamp(side, z, new int[]{head, head + 1, head - 1}, facingIn, relays);
                for (int at : new int[]{z, z + 1, z - 1}) {
                    if (upperDeck != null && upperDeck.walkwayBeside(side, at) && wallLamp(side, at, upperRows, facingIn, relays) != null) {
                        break;
                    }
                }
            }
        }
        if (seatbeltSwitch == null || relays.isEmpty()) {
            return;
        }
        BlockPos at = seatbeltSwitch;
        List<BlockPos> curtains = frontToBack(CurtainBlock::isCurtain);
        fittings.put(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof LinkedControlBlockEntity control) {
                control.configure(Map.of(SeatbeltSwitchBlock.SIGNS, offsets(at, relays, rotation)));
            }
            if (blockEntity instanceof SeatbeltSwitchBlockEntity seatbelts) {
                seatbelts.configureCurtains(offsets(at, curtains, rotation));
            }
        });
    }

    /**
     * A lamp set into the hull at a station on one side, with the relay that lights it just above or below it in the
     * wall, plated like the skin: in the first of the rows where both replace plain skin, not a window, and nothing
     * fitted into the cabin (the gym hall's wall bars, a locker) stands over the lamp's face. Adds the relay to the
     * list and gives where the lamp went, or null if no row had room.
     */
    @Nullable
    private BlockPos wallLamp(int side, int z, int[] rows, BlockState lamp, List<BlockPos> relays) {
        for (int y : rows) {
            int x = side * ((int) Math.ceil(WIDTH / 2 * scale) + 1);
            while (x != 0 && !inFuselage(x, y, z)) {
                x -= side;
            }
            BlockState face = stateAt(x - side, y, z);
            if (!stateAt(x, y, z).is(palette.hull().getBlock()) || !face.isAir() && !face.is(palette.hull().getBlock())) {
                continue;
            }
            int relayY = stateAt(x, y - 1, z).is(palette.hull().getBlock()) ? y - 1 : y + 1;
            if (!stateAt(x, relayY, z).is(palette.hull().getBlock())) {
                continue;
            }
            set(x, y, z, lamp);
            set(x, relayY, z, AerolargeRegistry.HULL_RELAY.get().defaultBlockState());
            relays.add(new BlockPos(x, relayY, z));
            return new BlockPos(x, y, z);
        }
        return null;
    }

    /**
     * Cuts the fuselage's skin to its curve, in the configured style ({@link ShellStyle}); then, cut or not, window
     * panes above one another are joined into tall windows, as {@link CabinWindowBlock#goesOn} joins them in the world.
     */
    private void shell() {
        if (style == ShellStyle.SNAPPED) {
            new SnappedShell(this).apply();
        } else {
            fittedShell();
        }
        Function<BlockPos, BlockState> blocks = at -> stateAt(at.getX(), at.getY(), at.getZ());
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            if (CabinWindowBlock.isPane(cell.getValue())) {
                BlockPos pos = cell.getKey();
                cell.setValue(cell.getValue()
                    .setValue(CabinWindowBlock.UP, CabinWindowBlock.goesOn(blocks, pos, Direction.UP))
                    .setValue(CabinWindowBlock.DOWN, CabinWindowBlock.goesOn(blocks, pos, Direction.DOWN)));
            }
        }
    }

    /**
     * The fitted style: every skin block the fuselage's surface passes through becomes shell, cut where the surface
     * crosses it, and the windscreen's glass and the cabin windows are cut the same way. Outside blocks the surface
     * still passes through get shell (or glass, or window) of their own, so the skin closes into one smooth surface.
     * Skin next to anything built onto the hull from outside (the wings, gear or exterior lights) or round a door stays
     * as it is so nothing opens up around it, and no shell is added over the doors set into the skin.
     */
    private void fittedShell() {
        BlockState shell = AerolargeRegistry.AIRFRAME_SHELL.get().defaultBlockState();
        BlockState curvedPane = AerolargeRegistry.CABIN_WINDOW_SHELL.get().defaultBlockState();
        Map<BlockPos, BlockState> shaped = new HashMap<>();
        Set<BlockPos> outside = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            BlockPos pos = cell.getKey();
            boolean glazed = cell.getValue().getBlock() instanceof CockpitGlassBlock;
            boolean pane = CabinWindowBlock.isPane(cell.getValue());
            if (!glazed && !pane && !cell.getValue().is(palette.hull().getBlock()) || !inSkin(pos)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                if (!cells.containsKey(pos.relative(side))) {
                    outside.add(pos.relative(side));
                }
            }
            int shape = shapeAt(pos.getX(), pos.getY(), pos.getZ());
            if (shape <= ShellGeometry.WHOLE || nextToFitting(pos, false) || nextToDoor(pos)) {
                continue;
            }
            shaped.put(pos, ShellBlock.withShape(glazed ? cell.getValue().getBlock().defaultBlockState() : pane ? curvedPane : shell, shape));
        }
        for (BlockPos pos : outside) {
            int shape = shapeAt(pos.getX(), pos.getY(), pos.getZ());
            if (inFuselage(pos) || shape <= ShellGeometry.WHOLE || nextToFitting(pos, true)) {
                continue;
            }
            BlockState in = inward(pos);
            BlockState outer = in.getBlock() instanceof CockpitGlassBlock ? in.getBlock().defaultBlockState() : CabinWindowBlock.isPane(in) ? curvedPane : shell;
            shaped.put(pos, ShellBlock.withShape(outer, shape));
        }
        cells.putAll(shaped);
    }

    /**
     * Whether a door is set into the skin right beside this block, or the block is a doorway's floor. The skin round a
     * door stays whole: a door is flat, and skin cut to the curve beside it would leave a wedge of open air along its
     * edges; and a doorway's floor cut to the curve, where a door stands proud of the hull's curve, would trip whoever
     * steps through.
     */
    boolean nextToDoor(BlockPos pos) {
        if (doorwayFloors.contains(pos)) {
            return true;
        }
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            BlockState state = stateAt(next.getX(), next.getY(), next.getZ());
            if (PlugDoorBlock.isDoor(state) || state.is(AerolargeRegistry.AIRLINER_DOOR.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The skin block this piece of shell closes over: the one straight in from it, against the way the fuselage faces
     * there, unless the fuselage faces nearly as much another way and the block that way is glass. Along the glass's
     * edges, where the skin turns, the glass then closes as glass instead of with specks of skin.
     */
    private BlockState inward(BlockPos pos) {
        double[] out = AirframeShape.outward(pos.getX() / scale, (pos.getY() + 0.5) / scale, (pos.getZ() + 0.5) / scale);
        Direction in = Direction.getNearest(-out[0], -out[1], -out[2]);
        double straight = -(in.getStepX() * out[0] + in.getStepY() * out[1] + in.getStepZ() * out[2]);
        for (Direction side : Direction.values()) {
            double along = -(side.getStepX() * out[0] + side.getStepY() * out[1] + side.getStepZ() * out[2]);
            BlockState next = stateAt(pos.getX() + side.getStepX(), pos.getY() + side.getStepY(), pos.getZ() + side.getStepZ());
            if ((side == in || along > 0 && along >= straight - GLASS_TIE) && next.getBlock() instanceof CockpitGlassBlock) {
                return next;
            }
        }
        return stateAt(pos.getX() + in.getStepX(), pos.getY() + in.getStepY(), pos.getZ() + in.getStepZ());
    }

    /**
     * Whether something other than skin is built onto the hull next to this block: whatever stands outside the
     * fuselage and is not hull, glass, a window or a light; with {@code setIn}, also whatever is set into the skin
     * itself, like the doors.
     */
    boolean nextToFitting(BlockPos pos, boolean setIn) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            BlockState state = cells.get(next);
            if (state == null || state.isAir() || state.is(palette.hull().getBlock()) || state.is(palette.light().getBlock())
                || state.getBlock() instanceof CockpitGlassBlock || CabinWindowBlock.isPane(state)) {
                continue;
            }
            if (!inFuselage(next) || setIn && inSkin(next)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The flight deck: a raised floor in the nose at the height where the pilots look straight out through the
     * windscreen, steps up to it from the main deck, and the instrument panel across the nose with the glareshield
     * filling the nose ahead of it, a flight display in it in front of each of the two pilot seats. In the middle of
     * the panel, standing just in front of it, is the autopilot screen, and on top of the screen, as one sheet of glass
     * with it, the tail camera display. From the panel back between the seats runs the centre console the pilots share
     * (see {@link #centreConsole}). The panel stands at the first station where the nose leaves room for all that,
     * ahead of the cockpit door with room behind the seats for the Physics Assembler; in a nose too small for it, at
     * the first station where two pilots fit side by side at all, with the controls fitted round them (see
     * {@link #narrowControls}), and the door moves back if it has to, but no further than the raised floor's back edge.
     * Behind the door, on the same raised floor at the top of the steps, are the WC and the crew room.
     */
    private void flightDeck() {
        int deck = Math.max(floorY + 1, Windscreen.deckRow(scale));
        // The raised floor reaches back as far as the steps down from it, one row for each row it is raised, leave the
        // forward doors' aisle clear.
        int deckEnd = (int) Math.round(PASSENGER_DOORS[0] * scale) - 1 - STEPS_CLEARANCE - (deck - floorY);
        flightDeckBack = deckEnd;
        for (int z = 0; z <= deckEnd; z++) {
            int width = openHalfWidth(deck, z);
            for (int x = -width; x <= width; x++) {
                set(x, deck, z, palette.floor());
            }
        }
        // Under its back edge the raised floor is walled down to the main deck, so the space beneath it is shut away.
        for (int y = floorY + 1; y < deck; y++) {
            int width = openHalfWidth(y, deckEnd);
            for (int x = -width; x <= width; x++) {
                set(x, y, deckEnd, palette.hull());
            }
        }
        // Steps from the main deck up to the flight deck's back edge, rising toward the nose; the top one stands level
        // with the deck, so its high side runs flush onto the deck's floor.
        BlockState step = AerolargeRegistry.CABIN_STAIRS.get().defaultBlockState().setValue(CabinStairsBlock.FACING, Direction.NORTH);
        for (int k = 1; deck + 1 - k > floorY; k++) {
            for (int x = -STEPS_HALF_WIDTH; x <= STEPS_HALF_WIDTH; x++) {
                furnish(x, deck + 1 - k, deckEnd + k, step);
            }
        }

        // Rather without the camera display on the screen than without the centre console.
        int doorStation = (int) Math.round(COCKPIT_DOOR * scale);
        int screenZ = consoleStation(deck, doorStation - 1, true);
        if (screenZ < 0) {
            screenZ = consoleStation(deck, doorStation - 1, false);
        }
        boolean console = screenZ >= 0;
        // With the console the screen stands just in front of the panel, without it set into the panel.
        int panelZ = console ? screenZ - 1 : panelStation(deck, deckEnd + 1);
        if (!console) {
            screenZ = panelZ;
        }
        int seatZ = screenZ + (console ? LEGROOM + 1 : 2);
        int bulkhead = Math.min(deckEnd, Math.max(doorStation, seatZ + 3));
        if (cockpitDoor(deck, bulkhead)) {
            new CrewRooms(this, deck, bulkhead + 1, deckEnd).apply();
        }
        if (panelZ < 0) {
            return;
        }
        BlockState panel = AerolargeRegistry.INSTRUMENT_PANEL.get().defaultBlockState().setValue(InstrumentPanelBlock.FACING, Direction.SOUTH);
        int panelWidth = openHalfWidth(deck + 1, panelZ);
        // The panel, and the glareshield: the nose ahead of it filled to the panel's height under the windscreen.
        for (int z = 0; z <= panelZ; z++) {
            int width = z == panelZ ? panelWidth : openHalfWidth(deck + 1, z);
            for (int x = -width; x <= width; x++) {
                set(x, deck + 1, z, panel);
            }
        }
        // A flight display in the panel in front of each seat.
        for (int side : new int[]{1, -1}) {
            int displayX = side * Math.min(SEAT_X, panelWidth);
            if (stateAt(displayX, deck + 1, panelZ).is(panel.getBlock())) {
                set(displayX, deck + 1, panelZ, AerolargeRegistry.FLIGHT_DISPLAY.get().defaultBlockState().setValue(FlightDisplayBlock.FACING, Direction.SOUTH));
            }
        }
        pilotSeat(-SEAT_X, deck + 1, seatZ);
        pilotSeat(SEAT_X, deck + 1, seatZ);
        BlockPos autopilot = console ? centreConsole(deck + 1, screenZ, seatZ) : narrowControls(deck + 1, panelZ, seatZ);
        autopilotScreen(deck + 1, screenZ, autopilot);
        if (!cameraOnScreen(deck + 1, screenZ)) {
            tailCameraDisplay(deck, seatZ - 1);
        }
        announcementPanel(deck, seatZ, autopilot);
        // The Physics Assembler out of the way, against the wall behind the captain's seat.
        int assemblerZ = seatZ + 2;
        assemblerSpot = new BlockPos(-openHalfWidth(deck + 1, assemblerZ), deck + 1, assemblerZ);
    }

    /**
     * The first station along the nose where the autopilot screen stands in front of the instrument panel with the
     * centre console and the seats behind it: the panel, the row ahead, wide enough for a flight display in front of
     * each seat; the screen's rows open (and, with {@code camera}, the row over them for the tail camera display), a
     * block clear either side so the glass stands free of the windscreen; room beside the console's front rows for the
     * pilots' legs; the seats' row a step wider than the seats, the tops of their backs open over them; and the Physics
     * Assembler's spot behind the seats no further back than {@code last}. -1 if there is none.
     */
    private int consoleStation(int deck, int last, boolean camera) {
        int screen = AutopilotScreenBlock.COLUMNS / 2;
        for (int z = 1; z + LEGROOM + 3 <= last; z++) {
            int seatZ = z + LEGROOM + 1;
            boolean fits = openHalfWidth(deck + 1, z - 1) >= SEAT_X && openHalfWidth(deck + 1, z) > screen
                && openHalfWidth(deck + 2, z) > screen && (!camera || openHalfWidth(deck + 3, z) > screen)
                && openHalfWidth(deck + 1, seatZ) > SEAT_X && openHalfWidth(deck + 2, seatZ) >= SEAT_X;
            for (int k = 1; fits && k <= LEGROOM; k++) {
                fits = openHalfWidth(deck + 1, z + k) >= SEAT_X;
            }
            if (fits) {
                return z;
            }
        }
        return -1;
    }

    /**
     * The first station along the nose where the instrument panel fits with two pilots side by side two rows back, at
     * least as wide as the seats either side of the middle at both, with the Physics Assembler's spot behind the seats
     * no further back than {@code last}; -1 if there is none.
     */
    private int panelStation(int deck, int last) {
        for (int z = 0; z + 4 <= last; z++) {
            if (openHalfWidth(deck + 1, z) >= SEAT_X && openHalfWidth(deck + 1, z + 2) >= SEAT_X) {
                return z;
            }
        }
        return -1;
    }

    /**
     * A pilot's seat facing the windscreen: the seat at the spot, the top of its back and its headrest over it; only
     * where both are open.
     */
    private void pilotSeat(int x, int y, int z) {
        if (!isOpen(x, y, z) || !isOpen(x, y + 1, z)) {
            return;
        }
        BlockState seat = AerolargeRegistry.PILOT_SEAT.get().defaultBlockState().setValue(PilotSeatBlock.FACING, Direction.NORTH);
        set(x, y, z, seat);
        set(x, y + 1, z, seat.setValue(PilotSeatBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * The centre console, from the autopilot screen back between the pilots' seats, a module of it for each control,
     * low enough to see the screen over: everything on it within reach from either seat but for the far thrust lever
     * (any lever moves all four), and the window shades' control and the drop release, each beside the seat that works
     * it. Across its front, as wide as the screen just ahead of it, the autopilot's control head between the seatbelt
     * switch and the jump light switch, and the transponder outboard of the seatbelt switch. Behind them, in front of the seats, the throttle quadrant: the four thrust
     * levers with the parking brake in the middle of them. Between the seats the nose wheel tiller in the middle, the
     * cabin lights' dimmer and the captain's handset either side of it, and at the seats' sides the window shades'
     * control by the captain and the drop release by the first officer, each facing the one beside it. Gives where the
     * autopilot went.
     */
    private BlockPos centreConsole(int y, int screenZ, int seatZ) {
        int front = screenZ + 1;
        int quadrant = screenZ + 2;
        List<BlockPos> controls = new ArrayList<>(thrustLevers(y, quadrant));
        BlockPos tiller = new BlockPos(0, y, seatZ);
        BlockPos brake = new BlockPos(0, y, quadrant);
        groundControls(tiller, brake);
        controls.add(tiller);
        controls.add(brake);
        seatbeltSwitch = control(-1, y, front, AerolargeRegistry.SEATBELT_SWITCH.get(), Direction.SOUTH);
        jumpSwitch = control(1, y, front, AerolargeRegistry.JUMP_SWITCH.get(), Direction.SOUTH);
        transponder(control(-2, y, front, AerolargeRegistry.TRANSPONDER.get(), Direction.SOUTH));
        cabinLights = control(-1, y, seatZ, AerolargeRegistry.CABIN_LIGHTS.get(), Direction.SOUTH);
        control(1, y, seatZ, AerolargeRegistry.HANDSET.get(), Direction.SOUTH);
        windowShades = control(-(SEAT_X - 1), y, seatZ, AerolargeRegistry.WINDOW_SHADES.get(), Direction.WEST);
        dropRelease = control(SEAT_X - 1, y, seatZ, AerolargeRegistry.DROP_RELEASE.get(), Direction.EAST);
        gearLever(2, y, screenZ - 1);
        if (AerolargeConfig.LASER_PATH_CLEARER.get()) {
            laserSwitch = control(2, y, front, AerolargeRegistry.LASER_SWITCH.get(), Direction.SOUTH);
        }
        BlockPos autopilot = new BlockPos(0, y, front);
        autopilot(autopilot, controls);
        return autopilot;
    }

    /**
     * The controls where the nose leaves no room for the centre console: the throttle quadrant between the seats with
     * the autopilot's control head in the middle of it; the tiller and the parking brake outboard of the seats where
     * there is room for both, and behind them the window shades' control by the captain and the drop release by the
     * first officer; and on the panel either side of the autopilot screen the seatbelt switch and the captain's
     * handset (and the transponder past it), the jump light switch and the cabin lights' dimmer. Gives where the
     * autopilot went.
     */
    private BlockPos narrowControls(int y, int panelZ, int seatZ) {
        List<BlockPos> controls = new ArrayList<>(thrustLevers(y, seatZ));
        BlockPos tiller = new BlockPos(-SEAT_X - 1, y, seatZ);
        BlockPos brake = new BlockPos(SEAT_X + 1, y, seatZ);
        if (isOpen(tiller.getX(), tiller.getY(), tiller.getZ()) && isOpen(brake.getX(), brake.getY(), brake.getZ())) {
            groundControls(tiller, brake);
            controls.add(tiller);
            controls.add(brake);
        }
        windowShades = control(-SEAT_X - 1, y, seatZ + 1, AerolargeRegistry.WINDOW_SHADES.get(), Direction.EAST);
        dropRelease = control(SEAT_X + 1, y, seatZ + 1, AerolargeRegistry.DROP_RELEASE.get(), Direction.WEST);
        int beside = AutopilotScreenBlock.COLUMNS / 2 + 1;
        seatbeltSwitch = onPanel(-beside, y, panelZ, AerolargeRegistry.SEATBELT_SWITCH.get());
        onPanel(-beside - 1, y, panelZ, AerolargeRegistry.HANDSET.get());
        jumpSwitch = onPanel(beside, y, panelZ, AerolargeRegistry.JUMP_SWITCH.get());
        cabinLights = onPanel(beside + 1, y, panelZ, AerolargeRegistry.CABIN_LIGHTS.get());
        transponder(onPanel(-beside - 2, y, panelZ, AerolargeRegistry.TRANSPONDER.get()));
        gearLever(beside + 2, y, panelZ);
        if (AerolargeConfig.LASER_PATH_CLEARER.get()) {
            laserSwitch = onPanel(-beside - 3, y, panelZ, AerolargeRegistry.LASER_SWITCH.get());
        }
        BlockPos autopilot = new BlockPos(0, y, seatZ);
        autopilot(autopilot, controls);
        return autopilot;
    }

    /**
     * The throttle quadrant's four thrust levers across station z, engines 1 to 4 from left to right as on the A380,
     * either side of the middle, each wired when the airliner is built to its engine's relays. Gives where they went.
     */
    private List<BlockPos> thrustLevers(int y, int z) {
        int[] leverX = {-2, -1, 1, 2};
        List<BlockPos> levers = new ArrayList<>();
        for (int i = 0; i < ENGINE_STATIONS.length; i++) {
            BlockPos lever = new BlockPos(leverX[i], y, z);
            levers.add(lever);
            set(lever.getX(), lever.getY(), lever.getZ(),
                AerolargeRegistry.THRUST_LEVER.get().defaultBlockState().setValue(ThrustLeverBlock.FACING, Direction.NORTH));
            List<BlockPos> relays = engineRelays.getOrDefault(ENGINE_STATIONS[i], List.of()).stream().map(relay -> relay.subtract(lever)).toList();
            fittings.put(lever, (blockEntity, rotation) -> {
                if (blockEntity instanceof ThrustLeverBlockEntity thrustLever) {
                    thrustLever.configure(relays.stream().map(offset -> offset.rotate(rotation)).toList());
                }
            });
        }
        return levers;
    }

    /**
     * The airliner's transponder, where it went (if it did): set when the airliner is built to show the airliner on the
     * tower's radar and on other airliners' maps by an airliner in white.
     */
    private void transponder(@Nullable BlockPos at) {
        if (at == null) {
            return;
        }
        fittings.put(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof TransponderBlockEntity transponder) {
                transponder.show(MarkerIcon.AIRLINER, MarkerColour.WHITE);
            }
        });
    }

    /** A control of the flight deck at the spot, its face looking the given way, if the spot is open; where it went. */
    @Nullable
    private BlockPos control(int x, int y, int z, Block block, Direction facing) {
        if (!isOpen(x, y, z)) {
            return null;
        }
        set(x, y, z, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
        return new BlockPos(x, y, z);
    }

    /**
     * A control standing on the instrument panel at column x, facing the pilots, if the panel is there, a flight
     * display in it or not; where it went.
     */
    @Nullable
    private BlockPos onPanel(int x, int y, int panelZ, Block block) {
        if (!(stateAt(x, y, panelZ).getBlock() instanceof InstrumentPanelBlock)) {
            return null;
        }
        return control(x, y + 1, panelZ, block, Direction.SOUTH);
    }

    /**
     * The bulkhead that makes the flight deck a room of its own: across the cabin at station z, from the raised floor
     * up to the crown, of the hull's own panels, with the cockpit door in the middle and a cabin light over it shining
     * both ways. The door is the big one (see {@link CockpitDoorBlock}), as tall as the room leaves it, its panel
     * between half a block of frame either side, swinging out into the aisle behind about its port side, where it
     * stands back along the WC's wall; where even the shorter one does not fit, an airliner door (opened by hand from
     * either side). False, and nothing built, where no door fits.
     */
    private boolean cockpitDoor(int deck, int z) {
        if (!isOpen(0, deck + 1, z) || !isOpen(0, deck + 2, z)) {
            return false;
        }
        int rows = cockpitDoorRows(deck, z);
        int top = deck + 1 + (rows > 0 ? rows : 2);
        // Only a light in the bulkhead itself, never in the skin above it.
        boolean lit = isOpen(0, top, z);
        for (int y = deck + 1; isOpen(0, y, z); y++) {
            int width = openHalfWidth(y, z);
            for (int x = -width; x <= width; x++) {
                set(x, y, z, palette.hull());
            }
        }
        if (rows > 0) {
            BlockState door = AerolargeRegistry.COCKPIT_DOOR.get().defaultBlockState()
                .setValue(CockpitDoorBlock.FACING, Direction.SOUTH)
                .setValue(CockpitDoorBlock.HINGE, CockpitDoorBlock.Hinge.LEFT)
                .setValue(CockpitDoorBlock.FRAMED, true);
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < CockpitDoorBlock.COLUMNS; column++) {
                    // Seen from the aisle, looking forward, its left is to port.
                    set(COCKPIT_DOOR_LEFT + column, deck + 1 + row, z,
                        door.setValue(CockpitDoorBlock.COLUMN, column).setValue(CockpitDoorBlock.ROW, row));
                }
            }
        } else {
            BlockState door = AerolargeRegistry.AIRLINER_DOOR.get().defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
            set(0, deck + 1, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(0, deck + 2, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        if (lit) {
            set(0, top, z, palette.light());
        }
        return true;
    }

    /**
     * How tall the big cockpit door in the bulkhead at station z can be: as tall as it may be, or a row shorter, where
     * its doorway is open and so is the aisle behind it, as far as its panel swings into it; 0 where it does not fit at
     * all.
     */
    private int cockpitDoorRows(int deck, int z) {
        for (int rows = CockpitDoorBlock.MOST_ROWS; rows >= CockpitDoorBlock.MOST_ROWS - 1; rows--) {
            boolean fits = true;
            for (int y = deck + 1; y <= deck + rows && fits; y++) {
                for (int x = COCKPIT_DOOR_LEFT; x < COCKPIT_DOOR_LEFT + CockpitDoorBlock.COLUMNS && fits; x++) {
                    for (int behind = 0; behind <= CockpitDoorBlock.PANEL_WIDTH && fits; behind++) {
                        fits = isOpen(x, y, z + behind);
                    }
                }
            }
            if (fits) {
                return rows;
            }
        }
        return 0;
    }

    /**
     * The autopilot screen in the middle of the instrument panel, facing the pilots, its bottom row at the panel's
     * height (set into the panel, where it stands in the panel's row) and its top row over it, if the nose leaves it
     * the room; linked to the autopilot when the airliner is built.
     */
    private void autopilotScreen(int bottom, int z, BlockPos autopilot) {
        int left = -(AutopilotScreenBlock.COLUMNS / 2);
        // Its bottom row is set into the panel; the rows over it need open space.
        if (!fits(left, bottom + 1, z, AutopilotScreenBlock.COLUMNS, AutopilotScreenBlock.ROWS - 1)) {
            return;
        }
        BlockState screen = AerolargeRegistry.AUTOPILOT_SCREEN.get().defaultBlockState().setValue(AutopilotScreenBlock.FACING, Direction.SOUTH);
        for (int row = 0; row < AutopilotScreenBlock.ROWS; row++) {
            for (int column = 0; column < AutopilotScreenBlock.COLUMNS; column++) {
                // Facing south, the viewer's right is east.
                set(left + column, bottom + row, z, screen.setValue(AutopilotScreenBlock.PART, row * AutopilotScreenBlock.COLUMNS + column));
            }
        }
        BlockPos origin = new BlockPos(left, bottom, z);
        fittings.put(origin, (blockEntity, rotation) -> {
            if (blockEntity instanceof AutopilotScreenBlockEntity linked) {
                linked.configure(autopilot.subtract(origin).rotate(rotation));
            }
        });
    }

    /**
     * The tail camera display stood upright on top of the autopilot screen, as wide as it and with its face in the same
     * plane, so the two read as one glass-cockpit unit; false, and nothing put up, where the screen is not there or the
     * room over it is not open.
     */
    private boolean cameraOnScreen(int bottom, int z) {
        int left = -(TailCameraDisplayBlock.COLUMNS / 2);
        int top = bottom + AutopilotScreenBlock.ROWS;
        if (!stateAt(left, bottom, z).is(AerolargeRegistry.AUTOPILOT_SCREEN.get()) || !fits(left, top, z, TailCameraDisplayBlock.COLUMNS, TailCameraDisplayBlock.ROWS)) {
            return false;
        }
        BlockState display = AerolargeRegistry.TAIL_CAMERA_DISPLAY.get().defaultBlockState().setValue(TailCameraDisplayBlock.FACING, Direction.SOUTH);
        for (int column = 0; column < TailCameraDisplayBlock.COLUMNS; column++) {
            // Facing south, the viewer's right is east.
            set(left + column, top, z, display.setValue(TailCameraDisplayBlock.PART, column));
        }
        return true;
    }

    /**
     * Where it cannot stand on the autopilot screen, the tail camera display, hung from the crown in the middle, a
     * station ahead of the pilots' seats, where they look up at it like an overhead monitor: right under the three
     * middle blocks of the skin across the top of the cabin there, tilted down toward the seats, its top edge along the
     * ceiling and its bottom edge back against the top of the windscreen. The skin itself stays as it is, cut to the
     * curve with the rest by the shell pass, so outside the crown keeps its shape. Nothing where the crown there is not
     * three blocks of skin or glass over open cabin, or where it would hang in the way of anyone standing under it.
     */
    private void tailCameraDisplay(int deck, int z) {
        int under = deck + 1;
        while (isOpen(0, under + 1, z)) {
            under++;
        }
        int crown = under + 1;
        int left = -(TailCameraDisplayBlock.COLUMNS / 2);
        if (under - (deck + 1) < TAIL_CAMERA_DISPLAY_HEADROOM || !fits(left, under, z, TailCameraDisplayBlock.COLUMNS, 1)) {
            return;
        }
        for (int column = 0; column < TailCameraDisplayBlock.COLUMNS; column++) {
            BlockState skin = stateAt(left + column, crown, z);
            if (!skin.is(palette.hull().getBlock()) && !skin.is(palette.light().getBlock()) && !(skin.getBlock() instanceof CockpitGlassBlock)) {
                return;
            }
        }
        BlockState display = AerolargeRegistry.TAIL_CAMERA_DISPLAY.get().defaultBlockState()
            .setValue(TailCameraDisplayBlock.FACING, Direction.SOUTH)
            .setValue(TailCameraDisplayBlock.TILTED, true);
        for (int column = 0; column < TailCameraDisplayBlock.COLUMNS; column++) {
            // Facing south, the viewer's right is east.
            set(left + column, under, z, display.setValue(TailCameraDisplayBlock.PART, column));
        }
    }

    /**
     * The announcement panel, hung overhead in the middle like an airliner's overhead panel: from the pilots' seats'
     * row forward over the centre console, {@link #ANNOUNCEMENT_PANEL_HEADROOM} rows of standing room under it, so from
     * either seat the pilot looks up at it, and in among the crown's skin, clear of the windscreen ahead and of the
     * screens under it. Where the crown curves up away from the panel's back, the gap over it is boxed in with the
     * hull's own panels, as far as {@link #ANNOUNCEMENT_PANEL_MOUNT} rows; nothing where the room under the crown is not
     * open, or the crown is further up. Linked to the autopilot when the airliner is built.
     */
    private void announcementPanel(int deck, int seatZ, BlockPos autopilot) {
        int y = deck + 1 + ANNOUNCEMENT_PANEL_HEADROOM;
        int left = -(AnnouncementPanelBlock.COLUMNS / 2);
        int front = seatZ - AnnouncementPanelBlock.ROWS + 1;
        if (!fitsOver(left, y, front, AnnouncementPanelBlock.COLUMNS, AnnouncementPanelBlock.ROWS)) {
            return;
        }
        for (int z = front; z <= seatZ; z++) {
            if (mount(left, y + 1, z, AnnouncementPanelBlock.COLUMNS) > ANNOUNCEMENT_PANEL_MOUNT) {
                return;
            }
        }
        for (int z = front; z <= seatZ; z++) {
            for (int x = left; x < left + AnnouncementPanelBlock.COLUMNS; x++) {
                boxIn(x, y + 1, z);
            }
        }
        // Facing the nose, as its viewers do: part 0 is the back row's left, over the seats' row, and the rows go
        // forward from there, the columns to the right (east, facing north).
        BlockState panel = AerolargeRegistry.ANNOUNCEMENT_PANEL.get().defaultBlockState().setValue(AnnouncementPanelBlock.FACING, Direction.NORTH);
        for (int part = 0; part < AnnouncementPanelBlock.COLUMNS * AnnouncementPanelBlock.ROWS; part++) {
            set(left + part % AnnouncementPanelBlock.COLUMNS, y, seatZ - part / AnnouncementPanelBlock.COLUMNS, panel.setValue(AnnouncementPanelBlock.PART, part));
        }
        BlockPos origin = new BlockPos(left, y, seatZ);
        fittings.put(origin, (blockEntity, rotation) -> {
            if (blockEntity instanceof AnnouncementPanelBlockEntity linked) {
                linked.configure(autopilot.subtract(origin).rotate(rotation));
            }
        });
    }

    /** Whether the cabin is open over the whole of a level rectangle at height y, from its corner at station z on. */
    private boolean fitsOver(int left, int y, int z, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            if (!fits(left, y, z + row, columns, 1)) {
                return false;
            }
        }
        return true;
    }

    /** How many rows of open cabin there are at most over a row of blocks from y up, before the hull closes it. */
    private int mount(int left, int y, int z, int columns) {
        int most = 0;
        for (int x = left; x < left + columns; x++) {
            int rows = 0;
            while (isOpen(x, y + rows, z)) {
                rows++;
            }
            most = Math.max(most, rows);
        }
        return most;
    }

    /** Fills the open cabin from y up with the hull's own panels, until the hull closes it. */
    private void boxIn(int x, int y, int z) {
        for (int up = y; isOpen(x, up, z); up++) {
            set(x, up, z, palette.hull());
        }
    }

    /** Whether the cabin is open over the whole of a rectangle across station z, from its bottom left corner. */
    private boolean fits(int left, int bottom, int z, int columns, int rows) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (!isOpen(left + column, bottom + row, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The autopilot's control head on the centre console, wired to every relay it flies with: the four engines', the
     * control jets', and the landing gear's steering and brake relays; to the controls it hands the airliner back to;
     * and to the lifting blocks of its control surfaces.
     */
    private void autopilot(BlockPos at, List<BlockPos> controls) {
        set(at.getX(), at.getY(), at.getZ(), AerolargeRegistry.AUTOPILOT.get().defaultBlockState().setValue(AutopilotBlock.FACING, Direction.NORTH));
        AutopilotBlockEntity.Channel[] engineChannels = {
            AutopilotBlockEntity.Channel.ENGINE_1, AutopilotBlockEntity.Channel.ENGINE_2,
            AutopilotBlockEntity.Channel.ENGINE_3, AutopilotBlockEntity.Channel.ENGINE_4
        };
        // Wired up when the airliner is built: the landing gear, whose steering and brake relays it works too, goes on
        // after the flight deck.
        fittings.put(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof AutopilotBlockEntity autopilot) {
                Map<AutopilotBlockEntity.Channel, List<BlockPos>> channels = new EnumMap<>(AutopilotBlockEntity.Channel.class);
                for (int i = 0; i < ENGINE_STATIONS.length; i++) {
                    channels.put(engineChannels[i], engineRelays.getOrDefault(ENGINE_STATIONS[i], List.of()));
                }
                channels.putAll(jetRelays);
                channels.put(AutopilotBlockEntity.Channel.STEER_LEFT, steerRelays.getOrDefault(TillerBlock.LEFT, List.of()));
                channels.put(AutopilotBlockEntity.Channel.STEER_RIGHT, steerRelays.getOrDefault(TillerBlock.RIGHT, List.of()));
                channels.put(AutopilotBlockEntity.Channel.BRAKES, brakeRelays);
                Map<AutopilotBlockEntity.Channel, List<BlockPos>> turned = new EnumMap<>(AutopilotBlockEntity.Channel.class);
                channels.forEach((channel, relays) -> turned.put(channel, offsets(at, relays, rotation)));
                autopilot.configure(turned, offsets(at, controls, rotation), at.getY() - ground);
                autopilot.wireGear(gearLever == null ? null : gearLever.subtract(at).rotate(rotation));
                List<BlockPos> frame = offsets(at, frameCorners(), rotation);
                autopilot.frame(frame.get(0), frame.get(1));
                Map<AutopilotBlockEntity.Channel, List<BlockPos>> turnedSurfaces = new EnumMap<>(AutopilotBlockEntity.Channel.class);
                surfaces.forEach((channel, blocks) -> turnedSurfaces.put(channel, offsets(at, blocks, rotation)));
                autopilot.wireSurfaces(turnedSurfaces, surfacePanels(at));
            }
        });
    }

    /** Two opposite corners of the box round every block of the airliner, air aside. */
    private List<BlockPos> frameCorners() {
        BlockPos min = null;
        BlockPos max = null;
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            if (!cell.getValue().isAir()) {
                min = min == null ? cell.getKey() : BlockPos.min(min, cell.getKey());
                max = max == null ? cell.getKey() : BlockPos.max(max, cell.getKey());
            }
        }
        return min == null ? List.of(BlockPos.ZERO, BlockPos.ZERO) : List.of(min, max);
    }

    /** Positions in the layout as offsets from a block, turned with the airliner. */
    private static List<BlockPos> offsets(BlockPos from, List<BlockPos> positions, Rotation rotation) {
        return positions.stream().map(pos -> pos.subtract(from).rotate(rotation)).toList();
    }

    /**
     * The nose wheel tiller beside the captain's seat and the parking brake beside the first officer's, wired when the
     * airliner is built to the steering relays at the nose gear's wheel mounts and the brake relays on the main gear's.
     */
    private void groundControls(BlockPos tiller, BlockPos brake) {
        set(tiller.getX(), tiller.getY(), tiller.getZ(), AerolargeRegistry.TILLER.get().defaultBlockState().setValue(TillerBlock.FACING, Direction.NORTH));
        fittings.put(tiller, (blockEntity, rotation) -> {
            if (blockEntity instanceof LinkedControlBlockEntity control) {
                control.configure(Map.of(
                    TillerBlock.LEFT, offsets(tiller, steerRelays.getOrDefault(TillerBlock.LEFT, List.of()), rotation),
                    TillerBlock.RIGHT, offsets(tiller, steerRelays.getOrDefault(TillerBlock.RIGHT, List.of()), rotation)
                ));
            }
        });
        set(brake.getX(), brake.getY(), brake.getZ(), AerolargeRegistry.PARKING_BRAKE.get().defaultBlockState().setValue(ParkingBrakeBlock.FACING, Direction.NORTH));
        fittings.put(brake, (blockEntity, rotation) -> {
            if (blockEntity instanceof LinkedControlBlockEntity control) {
                control.configure(Map.of(ParkingBrakeBlock.BRAKES, offsets(brake, brakeRelays, rotation)));
            }
        });
    }

    /**
     * Control jets for the autopilot, standing in for control surfaces (and beside them, where it flies with its
     * experimental ones, see {@link #controlSurfaces}): jets on top of and under the outer tailplane to pitch it
     * (pushing the tail down lifts the nose), and on top of and under the outer wings to roll it, at a few stations
     * along each so they can hold the airliner against its engines. Each is a thruster pointing up or down with its own
     * relay right behind it.
     */
    private void controlJets() {
        if (palette.engine().isEmpty()) {
            return;
        }
        BlockState down = AirframePalette.with(palette.engine().get(), "facing", "down");
        BlockState up = AirframePalette.with(palette.engine().get(), "facing", "up");
        for (int side : new int[]{1, -1}) {
            for (double station : TAIL_JET_STATIONS) {
                double tailX = station * STABILIZER_HALF_SPAN;
                double tailChord = lerp(STABILIZER_ROOT_CHORD, STABILIZER_TIP_CHORD, station);
                int x = side * (int) Math.round(tailX * scale);
                int z = (int) Math.round((STABILIZER_ROOT_LEADING_EDGE + STABILIZER_SWEEP * tailX + tailChord / 2) * scale);
                jetPair(x, z, down, up, AutopilotBlockEntity.Channel.PITCH_UP, AutopilotBlockEntity.Channel.PITCH_DOWN);
            }
            for (double station : WING_JET_STATIONS) {
                double wingX = station * WING_HALF_SPAN;
                double wingChord = lerp(WING_ROOT_CHORD, WING_TIP_CHORD, station);
                int x = side * (int) Math.round(wingX * scale);
                int z = (int) Math.round((wingLeadingEdge(wingX) + wingChord / 2) * scale);
                // Pushing the right wing down rolls right; pushing the left wing down rolls left; pushing up the opposite.
                jetPair(x, z, down, up,
                    side > 0 ? AutopilotBlockEntity.Channel.ROLL_RIGHT : AutopilotBlockEntity.Channel.ROLL_LEFT,
                    side > 0 ? AutopilotBlockEntity.Channel.ROLL_LEFT : AutopilotBlockEntity.Channel.ROLL_RIGHT);
            }
        }
    }

    /** A jet on top of the surface at (x, z) pushing down, and one under it pushing up, each with its relay. */
    private void jetPair(int x, int z, BlockState down, BlockState up, AutopilotBlockEntity.Channel downChannel, AutopilotBlockEntity.Channel upChannel) {
        int top = Integer.MIN_VALUE;
        int bottom = Integer.MAX_VALUE;
        for (int y = -64; y < 64 * scale; y++) {
            if (has(x, y, z)) {
                top = Math.max(top, y);
                bottom = Math.min(bottom, y);
            }
        }
        if (top == Integer.MIN_VALUE) {
            return;
        }
        jet(x, top + 1, z, down, downChannel);
        jet(x, bottom - 1, z, up, upChannel);
    }

    private void jet(int x, int y, int z, BlockState thruster, AutopilotBlockEntity.Channel channel) {
        if (has(x, y, z) || has(x, y, z + 1)) {
            return;
        }
        set(x, y, z, thruster);
        fullThrust(new BlockPos(x, y, z));
        set(x, y, z + 1, AerolargeRegistry.THRUST_RELAY.get().defaultBlockState());
        jetRelays.computeIfAbsent(channel, ignored -> new ArrayList<>()).add(new BlockPos(x, y, z + 1));
    }

    /**
     * One engine: a round nacelle under the wing at span station x (negative to port), cut to its curve, the intake
     * up front, the thrusters filling the middle of the exhaust face, and a pylon up to the wing. The intake's face is
     * the cowl's skin round a mouth of intake blocks; the engine's core is told where it is, so the client can draw
     * the cowl's lip, the fan and its spinner over it, finer than blocks at any scale.
     */
    private void engine(double x) {
        double front = nacelleFront(Math.abs(x));
        double centreY = nacelleY(Math.abs(x));
        int reach = (int) Math.ceil(NACELLE_RADIUS * scale) + 1;
        int centreX = (int) Math.round(x * scale);
        int centreYb = (int) Math.floor(centreY * scale);
        int firstZ = (int) Math.floor(front * scale);
        int lastZ = (int) Math.ceil((front + NACELLE_LENGTH) * scale);
        for (int z = firstZ; z <= lastZ; z++) {
            for (int y = centreYb - reach; y <= centreYb + reach; y++) {
                for (int xb = centreX - reach; xb <= centreX + reach; xb++) {
                    if (!inNacelle(x, centreY, front, xb, y, z)) {
                        continue;
                    }
                    BlockState state = palette.hull();
                    if (!inNacelle(x, centreY, front, xb, y, z - 1)) {
                        if (inMouth(x, centreY, front, xb, y, z)) {
                            state = palette.intake();
                        }
                    } else if (!inNacelle(x, centreY, front, xb, y, z + 1)) {
                        double radius = nacelleRadius((z + 0.5) / scale - front);
                        double off = Math.hypot(xb / scale - x, (y + 0.5) / scale - centreY);
                        if (off <= Math.max(0.85 * radius, 0.5 / scale)) {
                            state = palette.engine().orElse(palette.intake());
                            if (palette.engine().isPresent()) {
                                fullThrust(new BlockPos(xb, y, z));
                                // The relay just ahead of the thruster, inside the nacelle, carries its thrust lever's signal.
                                set(xb, y, z - 1, AerolargeRegistry.THRUST_RELAY.get().defaultBlockState());
                                engineRelays.computeIfAbsent(x, ignored -> new ArrayList<>()).add(new BlockPos(xb, y, z - 1));
                            }
                        }
                    }
                    set(xb, y, z, state);
                }
            }
        }
        // The relay nearest the middle of the exhaust is the engine's core, where it is heard and its contrail starts.
        engineRelays.getOrDefault(x, List.of()).stream()
            .min(Comparator.comparingDouble(relay -> Math.hypot(relay.getX() - x * scale, relay.getY() - centreY * scale)))
            .ifPresent(core -> {
                set(core.getX(), core.getY(), core.getZ(),
                    AerolargeRegistry.ENGINE_CORE.get().defaultBlockState().setValue(EngineCoreBlock.FACING, Direction.NORTH));
                fitIntake(core, x, centreY, front);
            });
        int pylonFrom = (int) Math.floor((front + 1.5) * scale);
        int pylonTo = (int) Math.floor((front + NACELLE_LENGTH - 1) * scale);
        for (int z = pylonFrom; z <= pylonTo; z++) {
            int y = centreYb;
            while (has(centreX, y, z)) {
                y++;
            }
            for (int limit = y + (int) Math.ceil(3 * scale); y < limit && !has(centreX, y, z); y++) {
                set(centreX, y, z, palette.hull());
            }
        }
        roundNacelle(x, centreY, front);
    }

    /**
     * Tells the engine's core where its intake is (see {@link EngineCoreBlockEntity.Intake}): from the core's middle
     * along the nose to the intake's face, the first row of the nacelle, and across to the nacelle's axis there, with
     * the radii of the nacelle and of the mouth, in blocks. A block's middle stands for the point x / scale across,
     * (y + 0.5) / scale up and (z + 0.5) / scale back.
     */
    private void fitIntake(BlockPos core, double x, double centreY, double front) {
        int face = (int) Math.ceil(front * scale - 0.5);
        EngineCoreBlockEntity.Intake intake = new EngineCoreBlockEntity.Intake(
            (float) (core.getZ() + 0.5 - face),
            (float) (x * scale - core.getX()),
            (float) (centreY * scale - core.getY() - 0.5),
            (float) (NACELLE_RADIUS * scale),
            (float) (INTAKE_MOUTH * NACELLE_RADIUS * scale)
        );
        fittings.put(core, (blockEntity, rotation) -> {
            if (blockEntity instanceof EngineCoreBlockEntity engine) {
                engine.configure(intake);
            }
        });
    }

    /**
     * Cuts the nacelle's skin to its curve, as the fuselage's is: every plain skin block on its surface becomes shell,
     * cut where the barrel or the exhaust cone passes through it (the intake's lip included) or whole where the
     * surface lies beyond it, and the empty blocks beside it that the surface still clips get a sliver of shell, so
     * the nacelle reads round from every side. Being shell, none of it is cut again by the fairings. Skin the pylon or
     * the wing stands against stays as it is, so nothing opens up where they meet it, and nothing is added above or
     * below the nacelle's own rows, so it hangs no lower.
     */
    private void roundNacelle(double x, double centreY, double front) {
        BlockState shell = AerolargeRegistry.AIRFRAME_SHELL.get().defaultBlockState();
        int reach = (int) Math.ceil(NACELLE_RADIUS * scale) + 2;
        int centreX = (int) Math.round(x * scale);
        int centreYb = (int) Math.floor(centreY * scale);
        Map<BlockPos, BlockState> shaped = new HashMap<>();
        for (int z = (int) Math.floor(front * scale); z <= Math.ceil((front + NACELLE_LENGTH) * scale); z++) {
            double along = (z + 0.5) / scale - front;
            if (along < 0 || along > NACELLE_LENGTH) {
                continue;
            }
            for (int y = centreYb - reach; y <= centreYb + reach; y++) {
                for (int xb = centreX - reach; xb <= centreX + reach; xb++) {
                    BlockPos pos = new BlockPos(xb, y, z);
                    int shape = nacelleShape(x, centreY, front, xb, y, z);
                    boolean surface = inNacelle(x, centreY, front, xb, y, z)
                        ? stateAt(xb, y, z).is(palette.hull().getBlock()) && openSurface(x, centreY, front, pos)
                        : shape > ShellGeometry.WHOLE && !has(xb, y, z)
                            && (inNacelle(x, centreY, front, xb - 1, y, z) || inNacelle(x, centreY, front, xb + 1, y, z));
                    if (surface) {
                        shaped.put(pos, ShellBlock.withShape(shell, Math.max(shape, ShellGeometry.WHOLE)));
                    }
                }
            }
        }
        shaped.forEach((pos, state) -> set(pos.getX(), pos.getY(), pos.getZ(), state));
    }

    /**
     * Whether a block of the nacelle is on its surface in the open: next to a block outside the nacelle, and with
     * nothing else (the pylon, the wing) standing against it there.
     */
    private boolean openSurface(double x, double centreY, double front, BlockPos pos) {
        boolean open = false;
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (!inNacelle(x, centreY, front, next.getX(), next.getY(), next.getZ())) {
                if (has(next.getX(), next.getY(), next.getZ())) {
                    return false;
                }
                open = true;
            }
        }
        return open;
    }

    /**
     * How the nacelle's skin cuts through a block, as a {@link ShellGeometry} shape: the surface's way out is straight
     * out from the axis along the barrel and tipped aft down the exhaust cone, and the surface lies along that line
     * from the block's middle as far as the nacelle's radius there is from it.
     */
    private int nacelleShape(double x, double centreY, double front, int xb, int y, int z) {
        double along = (z + 0.5) / scale - front;
        double dx = xb / scale - x;
        double dy = (y + 0.5) / scale - centreY;
        double off = Math.hypot(dx, dy);
        if (off < 1e-9) {
            return ShellGeometry.WHOLE;
        }
        double taper = nacelleTaper(along);
        double length = Math.sqrt(1 + taper * taper);
        double nx = dx / off / length;
        double ny = dy / off / length;
        double nz = taper / length;
        double skin = (nacelleRadius(along) - off) / length * scale;
        return ShellGeometry.cut(nx, ny, nz, 0.5 + nx * skin, 0.5 + ny * skin, 0.5 + nz * skin);
    }

    /**
     * Turns a creative thruster up to the configured setting, the way its value box would: Create keeps a block
     * entity's scroll value under "ScrollValue". Other thrusters have no such setting and keep the value unread.
     */
    private void fullThrust(BlockPos thruster) {
        int setting = AerolargeConfig.THRUST_SETTING.get();
        fittings.put(thruster, (blockEntity, rotation) -> {
            if (blockEntity.getLevel() == null) {
                return;
            }
            HolderLookup.Provider registries = blockEntity.getLevel().registryAccess();
            CompoundTag data = blockEntity.saveWithoutMetadata(registries);
            data.putInt("ScrollValue", setting);
            blockEntity.loadWithComponents(data, registries);
        });
    }

    /**
     * Whether a block of the intake's face is in its mouth: within the cowl's lip, and with the nacelle all round it,
     * so the nacelle's sides are skin right to the front, however coarse the scale.
     */
    private boolean inMouth(double x, double centreY, double front, int xb, int y, int z) {
        return Math.hypot(xb / scale - x, (y + 0.5) / scale - centreY) <= INTAKE_MOUTH * NACELLE_RADIUS
            && inNacelle(x, centreY, front, xb - 1, y, z) && inNacelle(x, centreY, front, xb + 1, y, z)
            && inNacelle(x, centreY, front, xb, y - 1, z) && inNacelle(x, centreY, front, xb, y + 1, z);
    }

    private boolean inNacelle(double x, double centreY, double front, int xb, int y, int z) {
        double radius = nacelleRadius((z + 0.5) / scale - front);
        double dx = xb / scale - x;
        double dy = (y + 0.5) / scale - centreY;
        return radius > 0 && dx * dx + dy * dy <= radius * radius;
    }

    /**
     * A plug door (see {@link PlugDoorBlock}), two blocks wide and {@code rows} tall, cut through the hull on one side
     * at station z, its sill on row y, hinged on the given edge, standing flat in one column at the hull's outermost
     * reach there and framed by hull. Where the curve of the hull makes the wall more than a block thick, the rest is
     * cut away so the doorway opens into the cabin.
     */
    private void door(int side, double station, int bottom, int rows, PlugDoorBlock.Hinge hinge) {
        int fore = (int) Math.round(station * scale);
        Direction out = side > 0 ? Direction.EAST : Direction.WEST;
        // The whole door stands in one column, the outermost the hull reaches at any of its blocks: its panel is flat,
        // and below the floor the hull curves in so fast that the lower row's skin can lie a block further in than the
        // upper's.
        int outer = 0;
        for (int z = fore; z < fore + PlugDoorBlock.COLUMNS; z++) {
            for (int y = bottom; y < bottom + rows; y++) {
                int x = side * ((int) Math.ceil(WIDTH / 2 * scale) + 1);
                while (x != 0 && !inFuselage(x, y, z)) {
                    x -= side;
                }
                if (Math.abs(x) > Math.abs(outer)) {
                    outer = x;
                }
            }
        }
        HullDoor door = new HullDoor(side, fore, bottom, outer, rows);
        hullDoors.add(door);
        Direction right = out.getCounterClockWise();
        BlockState part = AerolargeRegistry.PLUG_DOOR.get().defaultBlockState()
            .setValue(PlugDoorBlock.FACING, out)
            .setValue(PlugDoorBlock.HINGE, hinge);
        for (int column = 0; column < PlugDoorBlock.COLUMNS; column++) {
            BlockPos foot = door.controller().relative(right, column);
            int z = foot.getZ();
            doorwayFloors.add(new BlockPos(outer, bottom - 1, z));
            for (int row = 0; row < rows; row++) {
                int y = bottom + row;
                for (int x = outer - side; x != 0 && has(x, y, z) && !isOpen(x, y, z); x -= side) {
                    set(x, y, z, Blocks.AIR.defaultBlockState());
                    if (row == 0) {
                        doorwayFloors.add(new BlockPos(x, y - 1, z));
                    }
                }
                // Below the door the hull already curves inward: close the gap with a sill out to the door.
                for (int x = outer; row == 0 && x != 0 && !has(x, y - 1, z); x -= side) {
                    set(x, y - 1, z, palette.hull());
                }
                set(outer, y, z, part.setValue(PlugDoorBlock.COLUMN, column).setValue(PlugDoorBlock.ROW, row));
            }
        }
        // A frame of hull round the doorway, flush with the door, wherever the curve leaves the hull short of it: the
        // jambs either side and the lintel above. The skin pass leaves it whole (see nextToDoor), so nothing gapes.
        for (int y = bottom; y <= bottom + rows; y++) {
            for (int z = fore - 1; z <= fore + PlugDoorBlock.COLUMNS; z++) {
                boolean frame = y == bottom + rows || z == fore - 1 || z == fore + PlugDoorBlock.COLUMNS;
                if (frame && !has(outer, y, z)) {
                    set(outer, y, z, palette.hull());
                }
            }
        }
    }

    /**
     * Struts and tires under the nose, the wings and the belly, long enough to hold the inboard engines (the lowest
     * part of the airframe) clear of the ground.
     */
    private void landingGear() {
        ground = groundRow();
        double balance = centreOfMassZ();
        strut(0, (int) Math.round(NOSE_GEAR_Z * scale), ground, true);
        for (double[] gear : MAIN_GEAR) {
            int x = (int) Math.round(gear[0] * scale);
            int z = (int) Math.round(balance + gear[1] * scale);
            strut(x, z, ground, false);
            strut(-x, z, ground, false);
        }
        // The tires reach below the wheel mounts to the ground row, so that is what stands on the ground.
        minY = Math.min(minY, ground);
    }

    /**
     * The row the tires will stand on: far enough below the lowest part of the airframe (the inboard engines, placed
     * long before the gear) to hold it clear of the ground.
     */
    private int groundRow() {
        return minY - Math.max(2, (int) Math.round(GROUND_CLEARANCE * scale));
    }

    /** The forward port door, which the airstairs serve; null if it was not cut. */
    @Nullable
    private HullDoor boardingDoor() {
        int fore = (int) Math.round(PASSENGER_DOORS[0] * scale);
        return hullDoors.stream()
            .filter(door -> door.side() < 0 && door.fore() == fore && door.bottom() == floorY + 1)
            .findFirst()
            .orElse(null);
    }

    /**
     * Where the airstairs stow: how many blocks in from the door's column the bay starts, past the hull's skin below
     * the sill, which is its mouth, and how long it is.
     */
    private record StairBay(int start, int length) {
    }

    /**
     * The airstairs' bay: a box under the cabin floor behind the forward port door, in the top row of the hold, walled
     * off from the rest of it with hull panels and as long as the stairs need folded up in it (see
     * {@link AirstairFlight}), their sections as few as fit the hold. Its mouth is the hull's skin in the row below the
     * sill, left as it is: the shell pass cuts it to the curve like the rest, and the controller takes it away as the
     * hatch opens and puts it back as it shuts. Where the hold has no room for it, there is none, and the stairs slide
     * into the hull without a bay.
     */
    private void stairBay() {
        HullDoor served = boardingDoor();
        if (served == null) {
            return;
        }
        int rise = floorY + 1 - groundRow();
        Direction out = served.side() > 0 ? Direction.EAST : Direction.WEST;
        BlockPos[] rows = stairRows(served);
        int start = 0;
        for (BlockPos row : rows) {
            int in = 0;
            while (in < STAIR_MOUTH_SEARCH && !isOpen(row.relative(out, -in))) {
                in++;
            }
            start = Math.max(start, in);
        }
        int room = 0;
        while (open(rows, out, start + room)) {
            room++;
        }
        int sections = AirstairFlight.sectionsFor(rise, room);
        if (start >= STAIR_MOUTH_SEARCH || sections == 0) {
            return;
        }
        int length = (int) Math.ceil(AirstairFlight.bayLength(rise, sections));
        Direction right = out.getCounterClockWise();
        for (int in = start; in <= start + length; in++) {
            for (int column = -1; column <= AirstairFlight.WIDTH; column++) {
                BlockPos at = rows[0].relative(right, column).relative(out, -in);
                boolean channel = in < start + length && column >= 0 && column < AirstairFlight.WIDTH;
                wall(channel ? at.below() : at);
            }
        }
        stairBay = new StairBay(start, length);
    }

    /** The row under the sill in each of the stairs' two columns, at the door's column: the bay's mouth. */
    private BlockPos[] stairRows(HullDoor served) {
        Direction right = (served.side() > 0 ? Direction.EAST : Direction.WEST).getCounterClockWise();
        BlockPos door = served.controller();
        BlockPos row = new BlockPos(door.getX(), floorY - 1, door.getZ());
        return new BlockPos[]{row, row.relative(right)};
    }

    /** Whether both rows are open this many blocks in from the door's column. */
    private boolean open(BlockPos[] rows, Direction out, int in) {
        for (BlockPos row : rows) {
            if (!isOpen(row.relative(out, -in))) {
                return false;
            }
        }
        return true;
    }

    private boolean isOpen(BlockPos pos) {
        return isOpen(pos.getX(), pos.getY(), pos.getZ());
    }

    /** A hull panel of the bay's walls, where the hold was open; anything else there stays. */
    private void wall(BlockPos pos) {
        if (isOpen(pos)) {
            set(pos.getX(), pos.getY(), pos.getZ(), palette.hull());
        }
    }

    /**
     * The integral airstairs at the forward port door, the way passengers board (see
     * {@link AirstairBlockEntity}): a flight running straight out from the door down to the ground,
     * stowed in the bay below it, and built down, its steps laid and its hatch open. The airstair controller set into
     * the hull just aft of the door folds them away and brings them out again, opening the door as they come out and
     * shutting it as they go; a lit push-button on it inside (see {@link AirstairButtonBlock}), one outside by the top of
     * the stairs, and a call button on the nose gear for the ground crew work it. It is aft of the door, where the door,
     * swung forward against the hull, leaves its outside button free.
     */
    private void boardingStairs() {
        HullDoor served = boardingDoor();
        if (served == null) {
            return;
        }
        // The controller is the jamb just aft of the door, a block above its sill.
        int row = floorY + 2;
        int panelZ = served.fore() + PlugDoorBlock.COLUMNS;
        int panelX = served.outer();
        if (!has(panelX, row, panelZ)) {
            return;
        }
        set(panelX, row, panelZ, AerolargeRegistry.AIRSTAIR_CONTROLLER.get().defaultBlockState());
        BlockState button = AerolargeRegistry.AIRSTAIR_BUTTON.get().defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL);
        if (isOpen(panelX + 1, row, panelZ)) {
            set(panelX + 1, row, panelZ, button.setValue(ButtonBlock.FACING, Direction.EAST));
        }
        if (!has(panelX - 1, row, panelZ)) {
            set(panelX - 1, row, panelZ, button.setValue(ButtonBlock.FACING, Direction.WEST));
        }
        BlockPos controller = new BlockPos(panelX, row, panelZ);
        int rise = floorY + 1 - ground;
        int fitting = stairBay == null ? 0 : AirstairFlight.sectionsFor(rise, stairBay.length());
        int sections = fitting == 0 ? AirstairFlight.FEWEST_SECTIONS : fitting;
        List<AirstairControllerBlockEntity.Step> hatch = stairHatch(served, controller);
        int length = laidStairs(served, rise);
        if (length < AirstairFlight.shortest(rise, sections)) {
            length = 0;
        }
        // Built with the stairs down, the hatch is open: its skin is the controller's to put back.
        for (AirstairControllerBlockEntity.Step part : length > 0 ? hatch : List.<AirstairControllerBlockEntity.Step>of()) {
            BlockPos at = controller.offset(part.offset());
            set(at.getX(), at.getY(), at.getZ(), Blocks.AIR.defaultBlockState());
        }
        layStairs(served, length);
        float inset = hatchInset(served);
        BlockPos servedDoor = served.controller().subtract(controller);
        int laid = length;
        fittings.put(controller, (blockEntity, rotation) -> {
            if (blockEntity instanceof AirstairBlockEntity airstairs) {
                airstairs.configure(servedDoor.rotate(rotation), rise, laid, sections, inset, hatch.stream().map(part -> {
                    BlockPos offset = part.offset().rotate(rotation);
                    BlockState turned = part.state().rotate(blockEntity.getLevel(), blockEntity.getBlockPos().offset(offset), rotation);
                    return new AirstairControllerBlockEntity.Step(offset, turned);
                }).toList());
            }
        });

        // The ground crew's call button, on the front of the nose gear's strut above the axle, clear of the wheels.
        int noseZ = (int) Math.round(NOSE_GEAR_Z * scale);
        int callY = ground + 2;
        if (stateAt(0, callY, noseZ).is(palette.gear().getBlock()) && !has(0, callY, noseZ - 1)) {
            set(0, callY, noseZ - 1, AerolargeRegistry.AIRSTAIR_CALL_BUTTON.get().defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
            callButton = new BlockPos(0, callY, noseZ - 1);
        }
    }

    /**
     * The hatch over the bay's mouth, as the shell pass left it: the skin in the row under the sill, in both columns,
     * from just outside the door's face (a sliver of skin the curve leaves there) in to the bay; none without a bay.
     * Each block from the controller, shut.
     */
    private List<AirstairControllerBlockEntity.Step> stairHatch(HullDoor served, BlockPos controller) {
        if (stairBay == null) {
            return List.of();
        }
        Direction out = served.side() > 0 ? Direction.EAST : Direction.WEST;
        List<AirstairControllerBlockEntity.Step> hatch = new ArrayList<>();
        for (BlockPos row : stairRows(served)) {
            for (int in = -1; in < stairBay.start(); in++) {
                BlockPos at = row.relative(out, -in);
                BlockState skin = cells.get(at);
                if (skin != null && !skin.isAir()) {
                    hatch.add(new AirstairControllerBlockEntity.Step(at.subtract(controller), skin));
                }
            }
        }
        return List.copyOf(hatch);
    }

    /**
     * How far down the stairs can be laid as built: blocks down from the sill, each a block further out, as far as
     * nothing of the airliner stands in their way, at most the whole rise.
     */
    private int laidStairs(HullDoor served, int rise) {
        for (int down = 1; down <= rise; down++) {
            for (int column = 0; column < AirstairFlight.WIDTH; column++) {
                BlockPos step = stairStep(served, down, column);
                if (has(step.getX(), step.getY(), step.getZ()) || has(step.getX(), step.getY() + 1, step.getZ())) {
                    return down - 1;
                }
            }
        }
        return rise;
    }

    /** The stairs' steps down this far, each with the guard over it holding its side's handrail. */
    private void layStairs(HullDoor served, int length) {
        Direction up = served.side() > 0 ? Direction.WEST : Direction.EAST;
        BlockState block = AerolargeRegistry.AIRSTAIR_STEP.get().defaultBlockState();
        for (int down = 1; down <= length; down++) {
            for (int column = 0; column < AirstairFlight.WIDTH; column++) {
                BlockPos step = stairStep(served, down, column);
                AirstairStepBlock.Edge edge = column == 0 ? AirstairStepBlock.Edge.LEFT : AirstairStepBlock.Edge.RIGHT;
                set(step.getX(), step.getY(), step.getZ(), AirstairStepBlock.step(block, up));
                set(step.getX(), step.getY() + 1, step.getZ(), AirstairStepBlock.guard(block, up, edge));
            }
        }
    }

    /**
     * The airstairs' step {@code down} blocks below the sill, as far out from the door's face, in one of the flight's
     * columns (0 the left, seen from outside), as {@link AirstairBlockEntity} lays them.
     */
    private BlockPos stairStep(HullDoor served, int down, int column) {
        Direction out = served.side() > 0 ? Direction.EAST : Direction.WEST;
        return served.controller().relative(out.getCounterClockWise(), column).relative(out, down).below(down);
    }

    /**
     * How far in from the door's face the hull's skin lies at the bay's mouth, in blocks: where the curve below the
     * door crosses the mouth's middle, between the stairs' two columns. The hatch is drawn there as it swings.
     */
    private float hatchInset(HullDoor served) {
        double y = (floorY - 0.5) / scale;
        double z = (served.fore() + PlugDoorBlock.COLUMNS / 2.0) / scale;
        double inside = 0;
        double outside = WIDTH;
        for (int halving = 0; halving < 24; halving++) {
            double middle = (inside + outside) / 2;
            if (AirframeShape.inFuselage(middle, y, z)) {
                inside = middle;
            } else {
                outside = middle;
            }
        }
        double face = Math.abs(served.outer()) + 0.5;
        return (float) Math.max(0, face - inside * scale);
    }

    /**
     * A door button inside beside every hull door (see {@link DoorButtonBlock}), so the door can be opened and shut
     * from the cabin without aiming at the doorway, which is hard while the cabin decompresses through it: on the
     * cabin wall's inside face in one of the door's jambs, facing into the cabin, a block above the sill or failing
     * that level with it, just ahead of the door or failing that just aft of it. It goes in the first of those spots
     * where the wall is a whole plain panel, nothing set into it (a slide control, a jump light or its relay, the
     * airstair controller), and the cabin in front of it is open, nothing standing there (the airstair button, a rack
     * of parachutes) and room to stand in front of that; a door with no such spot by it has no button.
     */
    private void doorButtons() {
        BlockState button = AerolargeRegistry.DOOR_BUTTON.get().defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL);
        for (HullDoor door : hullDoors) {
            BlockPos spot = doorButtonSpot(door);
            if (spot != null) {
                set(spot.getX(), spot.getY(), spot.getZ(), button.setValue(ButtonBlock.FACING, door.side() > 0 ? Direction.WEST : Direction.EAST));
            }
        }
    }

    /** Where the door button beside one door goes: the first spot by it that has room for it; null for none. */
    @Nullable
    private BlockPos doorButtonSpot(HullDoor door) {
        for (int y : new int[]{door.bottom() + 1, door.bottom()}) {
            BlockPos spot = doorButtonSpot(door, y);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Where in this row the door button beside one door goes: in the jamb ahead of it, else aft; null for neither. */
    @Nullable
    private BlockPos doorButtonSpot(HullDoor door, int y) {
        for (int z : new int[]{door.fore() - 1, door.fore() + PlugDoorBlock.COLUMNS}) {
            int inside = besideWall(door.side(), y, z);
            if (inside != 0 && isOpen(inside, y, z) && isOpen(inside - door.side(), y, z) && isPlainHull(inside + door.side(), y, z)) {
                return new BlockPos(inside, y, z);
            }
        }
        return null;
    }


    public Map<BlockPos, Fitting> fittings() {
        return fittings;
    }

    /** Sets up the block entity at a layout position once it stands, as the fitting says. */
    void fit(BlockPos at, Fitting fitting) {
        fittings.put(at, fitting);
    }

    /** The WCs built into the cabin so far, with the signs over their doors, in the order they were built. */
    List<WcSigns.Wc> wcs() {
        return wcs;
    }

    /**
     * Where along the fuselage the airframe balances, in blocks from the nose, weighing each block the way Sable will
     * once it is assembled: by its mass tag.
     */
    private double centreOfMassZ() {
        double mass = 0;
        double moment = 0;
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            double weight = sableMass(cell.getValue());
            mass += weight;
            moment += weight * (cell.getKey().getZ() + 0.5);
        }
        return mass > 0 ? moment / mass : LENGTH * scale / 2;
    }

    /**
     * Trims the tailplane like a real one, which pushes down to balance the wings. Every lifting block pushes along its
     * face's normal in proportion to the airspeed, whatever the angle, and the wings lift behind the centre of mass,
     * which would pitch the nose down the faster the airliner goes, harder than the pitch jets can hold. So some of
     * the tailplane's blocks are turned to face down, the furthest aft first and in mirrored pairs, until the steady
     * lift no longer turns the airliner about its centre of mass. What the angle of the air adds still steadies it.
     */
    private void trimTail() {
        double balance = centreOfMassZ();
        double tailStart = STABILIZER_ROOT_LEADING_EDGE * scale;
        BlockState up = palette.lift(Direction.UP, palette.hull());
        double moment = 0;
        List<BlockPos> tail = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            if (!palette.isLift(cell.getValue()) || !cell.getValue().equals(up)) {
                continue;
            }
            BlockPos pos = cell.getKey();
            moment += pos.getZ() + 0.5 - balance;
            if (pos.getZ() >= tailStart && pos.getX() >= 0) {
                tail.add(pos);
            }
        }
        tail.sort(Comparator.comparingInt((BlockPos pos) -> pos.getZ()).reversed());
        BlockState down = palette.lift(Direction.DOWN, palette.hull());
        for (BlockPos pos : tail) {
            BlockPos mirror = new BlockPos(-pos.getX(), pos.getY(), pos.getZ());
            boolean pair = pos.getX() != 0 && palette.isLift(stateAt(mirror.getX(), mirror.getY(), mirror.getZ()));
            double change = 2 * (pos.getZ() + 0.5 - balance) * (pair ? 2 : 1);
            if (Math.abs(moment - change) >= Math.abs(moment)) {
                continue;
            }
            moment -= change;
            set(pos.getX(), pos.getY(), pos.getZ(), down);
            if (pair) {
                set(mirror.getX(), mirror.getY(), mirror.getZ(), down);
            }
        }
    }

    /**
     * Picks the lifting blocks of the autopilot's control surfaces, once the tail is trimmed (see
     * {@link ControlSurfaces}, which turns them over in flight): ailerons at the outer wings' trailing edges, the right
     * wing's and the left's mirror images of each other, the furthest out turned first; and the whole tailplane as the
     * elevator, the way a real one's trimmable stabiliser moves, its blocks that face up (to raise the nose) apart from
     * those the trim turned down (to lower it), in mirrored pairs, the furthest aft first. Picked whether the autopilot
     * flies with them or not; they stand as built until it does.
     */
    private void controlSurfaces() {
        Map<AutopilotBlockEntity.Channel, List<BlockPos>> starboard = new EnumMap<>(AutopilotBlockEntity.Channel.class);
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            AutopilotBlockEntity.Channel channel = surface(cell.getKey(), cell.getValue());
            if (channel != null) {
                starboard.computeIfAbsent(channel, ignored -> new ArrayList<>()).add(cell.getKey());
            }
        }
        List<BlockPos> ailerons = starboard.getOrDefault(AutopilotBlockEntity.Channel.ROLL_RIGHT, new ArrayList<>());
        ailerons.sort(Comparator.comparingInt((BlockPos pos) -> pos.getX()).thenComparingInt(BlockPos::getZ).reversed());
        surfaces.put(AutopilotBlockEntity.Channel.ROLL_RIGHT, ailerons);
        surfaces.put(AutopilotBlockEntity.Channel.ROLL_LEFT, ailerons.stream().map(AirframeLayout::mirror).toList());
        for (AutopilotBlockEntity.Channel pitch : ELEVATOR) {
            List<BlockPos> tail = starboard.getOrDefault(pitch, new ArrayList<>());
            tail.sort(Comparator.comparingInt((BlockPos pos) -> pos.getZ()).thenComparingInt(BlockPos::getX).reversed());
            surfaces.put(pitch, tail.stream().flatMap(pos -> Stream.of(pos, mirror(pos))).toList());
        }
    }

    /**
     * Which of the autopilot's control surfaces a block on the starboard side is part of, by the way it rests, its
     * mirror image to port resting the same way; null for none. The tailplane's lifting blocks lie aft of where it
     * starts, which the wingtips stop short of; the fin's stand on edge in the middle.
     */
    @Nullable
    private AutopilotBlockEntity.Channel surface(BlockPos pos, BlockState state) {
        if (pos.getX() <= 0 || !palette.isLift(state) || !state.equals(stateAt(-pos.getX(), pos.getY(), pos.getZ()))) {
            return null;
        }
        boolean tail = pos.getZ() >= STABILIZER_ROOT_LEADING_EDGE * scale;
        if (!tail && !aileron(pos)) {
            return null;
        }
        for (AutopilotBlockEntity.Channel channel : tail ? ELEVATOR : AILERON) {
            if (state.equals(palette.lift(ControlSurfaces.rest(channel), palette.hull()))) {
                return channel;
            }
        }
        return null;
    }

    /** Whether a block of the wings' lifting layer lies in an aileron: in its stretch of the span, aft of its hinge. */
    private boolean aileron(BlockPos pos) {
        double x = pos.getX() / scale;
        double out = x / WING_HALF_SPAN;
        if (out < AILERON_INBOARD || out > AILERON_OUTBOARD) {
            return false;
        }
        double chordwise = ((pos.getZ() + 0.5) / scale - wingLeadingEdge(x)) / lerp(WING_ROOT_CHORD, WING_TIP_CHORD, out);
        return chordwise >= AILERON_HINGE;
    }

    /**
     * The panels drawn to show the control surfaces at work (see {@link ControlSurfaces.Panel}), from the autopilot at
     * the given block: ailerons along each outer wing's trailing edge, and elevators along each side of the
     * tailplane's. They stand in the autopilot's frame, which turns with it, so nothing about them is turned here.
     */
    private List<ControlSurfaces.Panel> surfacePanels(BlockPos at) {
        return panelRuns().stream().map(run -> panel(at, run)).toList();
    }

    /**
     * Where the panels drawn to show the control surfaces at work lie: ailerons along each outer wing's trailing edge,
     * and elevators along each side of the tailplane's; none for wings without lifting blocks, which have no surfaces
     * to show.
     */
    private List<PanelRun> panelRuns() {
        List<PanelRun> runs = new ArrayList<>();
        if (surfaces.values().stream().allMatch(List::isEmpty)) {
            return runs;
        }
        Wing wing = new Wing(WING_HALF_SPAN, WING_ROOT_CHORD, WING_TIP_CHORD, AirframeShape::wingLeadingEdge, AirframeShape::wingY);
        Wing tailplane = new Wing(STABILIZER_HALF_SPAN, STABILIZER_ROOT_CHORD, STABILIZER_TIP_CHORD,
            x -> STABILIZER_ROOT_LEADING_EDGE + STABILIZER_SWEEP * x, x -> STABILIZER_Y + STABILIZER_DIHEDRAL * x);
        for (int side : new int[]{1, -1}) {
            runs.addAll(panelRuns(ControlSurfaces.Axis.ROLL, side, side, AILERON_PANEL_FROM, AILERON_PANEL_TO, wing));
            runs.addAll(panelRuns(ControlSurfaces.Axis.PITCH, 1, side, ELEVATOR_PANEL_FROM, ELEVATOR_PANEL_TO, tailplane));
        }
        return runs;
    }

    /** A wing's planform, as {@link #wings} lays it: its half span, its chord at the root and the tip, and where it lies. */
    private record Wing(double halfSpan, double rootChord, double tipChord, DoubleUnaryOperator leadingEdge, DoubleUnaryOperator centreY) {
    }

    /**
     * Where one panel lies: along a wing's trailing edge from column {@code from} out from the middle to {@code to}, on
     * one side (1 starboard, -1 port), swinging the given way for its axis (see {@link ControlSurfaces.Panel}).
     */
    private record PanelRun(ControlSurfaces.Axis axis, int way, int side, int from, int to, Wing wing) {
    }

    /**
     * The panels along one side of a wing, from one share of its half span to another: one for each step of the
     * trailing edge, the stretch of the span where the wing's last block stands as far aft and as high, so each lies
     * flush along the blocks it hangs from, and no wider than {@link #MOST_PANEL_SPAN}.
     */
    private List<PanelRun> panelRuns(ControlSurfaces.Axis axis, int way, int side, double from, double to, Wing wing) {
        List<PanelRun> runs = new ArrayList<>();
        int first = (int) Math.ceil(from * wing.halfSpan() * scale);
        int last = (int) Math.floor(to * wing.halfSpan() * scale);
        int widest = Math.max(1, (int) Math.round(MOST_PANEL_SPAN * scale));
        int start = first;
        for (int xb = first + 1; xb <= last + 1; xb++) {
            if (xb > last || xb - start >= widest || trailingBlock(wing, xb) != trailingBlock(wing, start)) {
                runs.add(new PanelRun(axis, way, side, start, xb - 1, wing));
                start = xb;
            }
        }
        return runs;
    }

    /**
     * Where the last block of a wing's trailing edge stands at column xb out from the middle: how far back, and in
     * which row, as one number, so two columns can be told to stand alike or not. As {@link #wings} lays it: the last
     * row back whose middle still lies within the chord, in the row of the wing's lifting layer.
     */
    private long trailingBlock(Wing wing, int xb) {
        double x = xb / scale;
        double chord = lerp(wing.rootChord(), wing.tipChord(), x / wing.halfSpan());
        int back = (int) Math.floor((wing.leadingEdge().applyAsDouble(x) + chord) * scale - 0.5);
        int row = (int) Math.floor(wing.centreY().applyAsDouble(x) * scale);
        return BlockPos.asLong(0, row, back);
    }

    /**
     * One panel, hinged along the back face of the wing's last blocks over its run, level with the middle of the wing's
     * lifting layer there (all of the wing that reaches back to its trailing edge), and drawn with its lifting block.
     * In blocks from the middle of the autopilot's block, to starboard, up and ahead; a block's middle stands for the
     * point x / scale across, (y + 0.5) / scale up and (z + 0.5) / scale back.
     */
    private ControlSurfaces.Panel panel(BlockPos at, PanelRun run) {
        BlockPos edge = BlockPos.of(trailingBlock(run.wing(), run.from()));
        double up = edge.getY() + 0.5 - (at.getY() + 0.5);
        double ahead = at.getZ() + 0.5 - (edge.getZ() + 1);
        double inner = run.side() * (run.from() - 0.5) - at.getX();
        double outer = run.side() * (run.to() + 0.5) - at.getX();
        return new ControlSurfaces.Panel(run.axis(), run.way(), new Vec3(inner, up, ahead), new Vec3(outer, up, ahead), rows(run),
            palette.lift(Direction.UP, palette.hull()));
    }

    /** How many whole rows deep a panel is: so many as make {@link #PANEL_CHORD} of the chord in the middle of its run. */
    private int rows(PanelRun run) {
        double middle = (run.from() + run.to()) / 2.0 / scale / run.wing().halfSpan();
        return Math.max(1, (int) Math.round(PANEL_CHORD * lerp(run.wing().rootChord(), run.wing().tipChord(), middle) * scale));
    }

    /**
     * Lays what the panels are walked on (see {@link SurfacePanelBlock}), where each lies at rest: its rows aft of the
     * wing's last blocks, in the row of its lifting layer, each hanging from the block ahead of it toward the nose;
     * wherever nothing else stands.
     */
    private void panelFloors() {
        BlockState floor = SurfacePanelBlock.toward(AerolargeRegistry.SURFACE_PANEL.get().defaultBlockState(), Direction.NORTH);
        for (PanelRun run : panelRuns()) {
            BlockPos edge = BlockPos.of(trailingBlock(run.wing(), run.from()));
            int rows = rows(run);
            for (int xb = run.from(); xb <= run.to(); xb++) {
                floorRow(new BlockPos(run.side() * xb, edge.getY(), edge.getZ()), rows, floor);
            }
        }
    }

    /** A panel's floor so many rows deep aft of one of the wing's last blocks, wherever nothing else stands. */
    private void floorRow(BlockPos edge, int rows, BlockState floor) {
        for (int row = 1; row <= rows; row++) {
            BlockPos pos = edge.south(row);
            if (!has(pos.getX(), pos.getY(), pos.getZ())) {
                set(pos.getX(), pos.getY(), pos.getZ(), floor);
            }
        }
    }

    /** A position's mirror image across the middle of the airliner. */
    private static BlockPos mirror(BlockPos pos) {
        return new BlockPos(-pos.getX(), pos.getY(), pos.getZ());
    }

    /** The block's mass in Sable, from the tags Sable weighs blocks by; air and fluids weigh nothing here. */
    private static double sableMass(BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return 0;
        }
        if (state.is(SABLE_WEIGHTLESS)) {
            return 0;
        }
        if (state.is(SABLE_SUPER_LIGHT)) {
            return 0.25;
        }
        if (state.is(SABLE_SUPER_HEAVY)) {
            return 4;
        }
        if (state.is(SABLE_HEAVY)) {
            return 2;
        }
        return state.is(SABLE_LIGHT) ? 0.5 : 1;
    }

    private static TagKey<Block> sableTag(String name) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("sable", name));
    }

    /**
     * One landing gear leg at (x, z): a strut down from the airframe to an axle a block above the ground, with a
     * wheel mount on either side facing out, each fitted with a tire so the assembled airliner rolls fore and aft.
     * The nose leg's mounts get steering relays before and behind them, the main legs' brake relays on top (a mount
     * brakes by a signal from above). A mount steers by the signal at one side less the one at the other, both seen
     * from the way it faces (more at its clockwise side turns the airliner left), so the two mounts of the nose leg,
     * facing apart, are each wired their own way round for their wheels to turn together. Without wheel mounts the leg
     * ends in fixed tire blocks instead.
     */
    private void strut(int x, int z, int ground, boolean steers) {
        int axle = ground + 1;
        if (palette.wheelMount().isPresent() && palette.tireItem().isPresent()) {
            List<BlockPos> parts = new ArrayList<>();
            for (Direction out : new Direction[]{Direction.EAST, Direction.WEST}) {
                BlockPos at = new BlockPos(x + out.getStepX(), axle, z);
                set(at.getX(), at.getY(), at.getZ(), AirframePalette.with(palette.wheelMount().get(), "facing", out.getSerializedName()));
                parts.add(at);
                Item tire = palette.tireItem().get();
                fittings.put(at, (blockEntity, rotation) -> fitWheel(blockEntity, tire));
                if (steers) {
                    relay(at.relative(out.getClockWise()), steerRelays.computeIfAbsent(TillerBlock.LEFT, ignored -> new ArrayList<>()), parts);
                    relay(at.relative(out.getCounterClockWise()), steerRelays.computeIfAbsent(TillerBlock.RIGHT, ignored -> new ArrayList<>()), parts);
                } else {
                    relay(at.above(), brakeRelays, parts);
                }
            }
            set(x, axle, z, palette.gear());
            parts.add(new BlockPos(x, axle, z));
            int top = leg(x, axle + 1, z, parts);
            // Only where the flight deck has its lever: nothing else could raise the gear.
            if (gearLever != null && top > axle) {
                gearBay(new BlockPos(x, top, z), steers ? Direction.NORTH : x > 0 ? Direction.WEST : Direction.EAST, parts);
            }
            return;
        }
        int tireHalfWidth = (int) Math.round(0.45 * scale);
        int tireHalfLength = (int) Math.round(0.5 * scale);
        int tireHeight = Math.max(1, (int) Math.round(1.1 * scale));
        for (int y = ground; y < ground + tireHeight; y++) {
            for (int dx = -tireHalfWidth; dx <= tireHalfWidth; dx++) {
                for (int dz = -tireHalfLength; dz <= tireHalfLength; dz++) {
                    set(x + dx, y, z + dz, palette.tire());
                }
            }
        }
        leg(x, ground + tireHeight, z, new ArrayList<>());
    }

    /**
     * A gear leg from row y up to the airframe, and on up through any skin there cut to the hull's curve (the gear goes
     * on after the skin is cut), so the leg meets the hull itself instead of stopping short under a sliver of skin. Each
     * block of it is added to the leg's parts; gives the row of its top block, the row under y if there is none.
     */
    private int leg(int x, int y, int z, List<BlockPos> parts) {
        int top = y + 64;
        int last = y - 1;
        for (; y < top && !has(x, y, z); y++) {
            set(x, y, z, palette.gear());
            parts.add(new BlockPos(x, y, z));
            last = y;
        }
        for (; y < top && stateAt(x, y, z).is(AerolargeRegistry.AIRFRAME_SHELL.get()); y++) {
            set(x, y, z, palette.gear());
            parts.add(new BlockPos(x, y, z));
            last = y;
        }
        return last;
    }

    /** A relay at the position if it is free, added to the group and to the blocks of its gear leg. */
    private void relay(BlockPos pos, List<BlockPos> group, List<BlockPos> parts) {
        if (!has(pos.getX(), pos.getY(), pos.getZ())) {
            set(pos.getX(), pos.getY(), pos.getZ(), AerolargeRegistry.THRUST_RELAY.get().defaultBlockState());
            group.add(pos);
            parts.add(pos);
        }
    }

    /**
     * Makes the top block of a gear leg its bay, which raises and lowers the rest of the leg (see
     * {@link GearBayBlockEntity}), the leg swinging up the given way; wired once the whole leg stands (see
     * {@link #wireGear}).
     */
    private void gearBay(BlockPos at, Direction swing, List<BlockPos> parts) {
        set(at.getX(), at.getY(), at.getZ(), AerolargeRegistry.GEAR_BAY.get().defaultBlockState().setValue(GearBayBlock.FACING, swing));
        parts.remove(at);
        gearLegs.add(new GearLeg(at, parts));
    }

    /** The landing gear lever on the panel's top at the spot, with retractable gear and room for it. */
    private void gearLever(int x, int y, int panelZ) {
        if (AerolargeConfig.RETRACTABLE_GEAR.get() && palette.wheelMount().isPresent() && palette.tireItem().isPresent()) {
            gearLever = onPanel(x, y, panelZ, AerolargeRegistry.GEAR_LEVER.get());
        }
    }

    /**
     * Wires the retractable gear once everything on its legs stands: each bay to the blocks of its leg (the nose leg's
     * with the ground crew's call button on it), and the lever to every bay.
     */
    private void wireGear() {
        for (GearLeg leg : gearLegs) {
            List<BlockPos> parts = new ArrayList<>(leg.parts());
            if (callButton != null && callButton.getX() == leg.bay().getX() && Math.abs(callButton.getZ() - leg.bay().getZ()) == 1) {
                parts.add(callButton);
            }
            fittings.put(leg.bay(), (blockEntity, rotation) -> {
                if (blockEntity instanceof GearBayBlockEntity bay) {
                    bay.configure(offsets(leg.bay(), parts, rotation));
                }
            });
        }
        if (gearLever == null) {
            return;
        }
        BlockPos lever = gearLever;
        List<BlockPos> bays = gearLegs.stream().map(GearLeg::bay).toList();
        fittings.put(lever, (blockEntity, rotation) -> {
            if (blockEntity instanceof GearLeverBlockEntity gear) {
                gear.configure(offsets(lever, bays, rotation));
            }
        });
    }

    /**
     * The laser path clearer's turret in the underside of the nose, a few metres back from its tip, in place of the
     * lowest block of the skin there, wired to the switch on the flight deck; only with a switch to arm it.
     */
    private void laserTurret() {
        int z = (int) Math.round(LASER_TURRET_Z * scale);
        BlockPos at = lowestBlock(0, z);
        if (laserSwitch == null || at == null) {
            return;
        }
        set(at.getX(), at.getY(), at.getZ(), AerolargeRegistry.LASER_TURRET.get().defaultBlockState().setValue(LaserTurretBlock.FACING, Direction.NORTH));
        BlockPos button = laserSwitch;
        fittings.put(button, (blockEntity, rotation) -> {
            if (blockEntity instanceof LaserSwitchBlockEntity laser) {
                laser.configure(at.subtract(button).rotate(rotation));
            }
        });
    }

    /** The lowest block of the layout in a column, no more than {@link #COLUMN_SEARCH} rows up; null if there is none. */
    @Nullable
    private BlockPos lowestBlock(int x, int z) {
        for (int y = minY; y < minY + COLUMN_SEARCH; y++) {
            if (has(x, y, z)) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    /** A retractable gear leg: the bay at its top, and the blocks below it that it raises and lowers. */
    private record GearLeg(BlockPos bay, List<BlockPos> parts) {
    }

    /**
     * Puts a tire on a wheel mount and stiffens its suspension, the way Offroad keeps them: the tire as the mount's
     * held stack, under "CurrentStack", and the suspension strength as its scroll value, under "ScrollValue". The
     * mount's own data is read back with both changed, so everything else it holds stays as it was.
     */
    private static void fitWheel(BlockEntity mount, Item tire) {
        if (mount.getLevel() == null) {
            return;
        }
        HolderLookup.Provider registries = mount.getLevel().registryAccess();
        CompoundTag data = mount.saveWithoutMetadata(registries);
        data.put("CurrentStack", new ItemStack(tire).save(registries));
        data.putInt("ScrollValue", SUSPENSION_STRENGTH);
        mount.loadWithComponents(data, registries);
    }

    /** The Physics Assembler, on the flight deck behind the pilots, or failing that the first spot on the cabin floor. */
    private void assembler() {
        palette.assembler().ifPresent(state -> {
            if (assemblerSpot != null && isOpen(assemblerSpot.getX(), assemblerSpot.getY(), assemblerSpot.getZ())) {
                set(assemblerSpot.getX(), assemblerSpot.getY(), assemblerSpot.getZ(), state);
                return;
            }
            for (int z = (int) Math.round(0.7 * NOSE_LENGTH * scale); z < LENGTH * scale; z++) {
                if (isOpen(0, floorY + 1, z) && isCabinFloor(0, floorY, z)) {
                    set(0, floorY + 1, z, state);
                    return;
                }
            }
        });
    }

    /**
     * The flight crew's locker on the flight deck, across from the Physics Assembler: its back to the wall behind the
     * first officer's seat and its door facing in, stocked when the airliner is built with a pilot helmet and a pilot
     * cap for each pilot. It goes in once the skin is cut, against the wall as the cut left it, so the skin and the
     * glass round it are cut to the curve as they are without it; only on the flight deck's floor.
     */
    private void crewLocker() {
        if (assemblerSpot == null) {
            return;
        }
        int y = assemblerSpot.getY();
        int z = assemblerSpot.getZ();
        int x = openHalfWidth(y, z);
        if (x <= 0 || !isCabinFloor(x, y - 1, z)) {
            return;
        }
        BlockPos spot = new BlockPos(x, y, z);
        set(x, y, z, AerolargeRegistry.CREW_LOCKER.get().defaultBlockState().setValue(CrewLockerBlock.FACING, Direction.WEST));
        fittings.put(spot, (blockEntity, rotation) -> {
            if (blockEntity instanceof CrewLockerBlockEntity locker) {
                List<Item> kit = List.of(AerolargeRegistry.PILOT_HELMET.get(), AerolargeRegistry.PILOT_HELMET.get(),
                    AerolargeRegistry.PILOT_CAP.get(), AerolargeRegistry.PILOT_CAP.get());
                for (int slot = 0; slot < kit.size(); slot++) {
                    locker.setItem(slot, new ItemStack(kit.get(slot)));
                }
            }
        });
    }

    /**
     * Wires the cabin controls on the flight deck, once everything stands, to what they work, front to back so each
     * setting runs down the cabin: the dimmer to every light in the airliner that has a brightness, the window shades'
     * control to every cabin window.
     */
    private void cabinControls() {
        wire(cabinLights, state -> state.hasProperty(Brightness.PROPERTY) && !(state.getBlock() instanceof CabinControlBlock));
        wire(windowShades, CabinWindowBlock::isPane);
    }

    private void wire(@Nullable BlockPos control, Predicate<BlockState> works) {
        if (control == null) {
            return;
        }
        List<BlockPos> targets = frontToBack(works);
        fittings.put(control, (blockEntity, rotation) -> {
            if (blockEntity instanceof CabinControlBlockEntity cabinControl) {
                cabinControl.configure(offsets(control, targets, rotation));
            }
        });
    }

    /** Where the blocks the test accepts stand, front to back, then bottom to top and port to starboard. */
    private List<BlockPos> frontToBack(Predicate<BlockState> test) {
        return cells.entrySet().stream()
            .filter(cell -> test.test(cell.getValue()))
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getZ()).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX))
            .toList();
    }

    /** Moves everything up so the tires stand on y 0. */
    private AirframeLayout lifted() {
        Map<BlockPos, BlockState> moved = new HashMap<>(cells.size() * 2);
        cells.forEach((pos, state) -> moved.put(pos.below(minY), state));
        cells.clear();
        cells.putAll(moved);
        Map<BlockPos, Fitting> movedFittings = new HashMap<>();
        fittings.forEach((pos, fitting) -> movedFittings.put(pos.below(minY), fitting));
        fittings.clear();
        fittings.putAll(movedFittings);
        return this;
    }
}
