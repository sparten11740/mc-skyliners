package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.AirframeShape.*;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.AirstairControllerBlockEntity;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlock;
import dev.aerolarge.block.AutopilotScreenBlockEntity;
import dev.aerolarge.block.Brightness;
import dev.aerolarge.block.CabinControlBlock;
import dev.aerolarge.block.CabinControlBlockEntity;
import dev.aerolarge.block.CabinLightsBlock;
import dev.aerolarge.block.CabinWindowBlock;
import dev.aerolarge.block.CockpitGlassBlock;
import dev.aerolarge.block.DropReleaseBlock;
import dev.aerolarge.block.DropReleaseBlockEntity;
import dev.aerolarge.block.EngineCoreBlock;
import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.block.FairingBlock;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.HandsetBlock;
import dev.aerolarge.block.InstrumentPanelBlock;
import dev.aerolarge.block.JumpSwitchBlock;
import dev.aerolarge.block.LinkedControlBlockEntity;
import dev.aerolarge.block.NavLightBlock;
import dev.aerolarge.block.ParachuteRackBlock;
import dev.aerolarge.block.ParkingBrakeBlock;
import dev.aerolarge.block.SeatbeltSwitchBlock;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.ShellGeometry;
import dev.aerolarge.block.ThrustLeverBlock;
import dev.aerolarge.block.ThrustLeverBlockEntity;
import dev.aerolarge.block.TillerBlock;
import dev.aerolarge.block.WindowShadesBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
import java.util.function.Predicate;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The airliner cut into blocks, before it is put anywhere: every block of it, and air for every block of the cabin
 * and hold, keyed by position relative to the nose. The layout is drawn with the nose pointing north: x runs to
 * starboard (east), y up from the ground under the wheels, z back toward the tail (south). Block states are drawn
 * the same way, so turning the whole thing to face another way is one rotation of positions and states.
 */
public final class AirframeLayout {
    /** Stations between cabin lights along the fuselage. */
    private static final int LIGHT_SPACING = 6;
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
    /** Where along the tailplane and the wings the control jets stand, as shares of their half span: outboard of the engines. */
    private static final double[] TAIL_JET_STATIONS = {0.6, 0.72, 0.84};
    private static final double[] WING_JET_STATIONS = {0.76, 0.82, 0.88};
    /** Where along the cabin the seatbelt signs are, in metres from the nose: in every room and clear of the doors. */
    private static final double[] SEATBELT_SIGN_STATIONS = {16, 21, 30, 38, 45, 55};
    /** Where along the belly the drop bay is, in metres from the nose: behind the main gear, ahead of the cargo door. */
    private static final double DROP_BAY_STATION = 44;
    private static final TagKey<Block> SABLE_WEIGHTLESS = sableTag("weightless");
    private static final TagKey<Block> SABLE_SUPER_LIGHT = sableTag("super_light");
    private static final TagKey<Block> SABLE_LIGHT = sableTag("light");
    private static final TagKey<Block> SABLE_HEAVY = sableTag("heavy");
    private static final TagKey<Block> SABLE_SUPER_HEAVY = sableTag("super_heavy");

    private final double scale;
    private final AirframePalette palette;
    private final Map<BlockPos, BlockState> cells = new HashMap<>();
    /** Block entities to set up once placed, by layout position. */
    private final Map<BlockPos, Fitting> fittings = new HashMap<>();
    /** Each engine's thrust relays, by the engine's span station (negative to port). */
    private final Map<Double, List<BlockPos>> engineRelays = new HashMap<>();
    /** The control jets' relays, by what they do. */
    private final Map<AutopilotBlockEntity.Channel, List<BlockPos>> jetRelays = new EnumMap<>(AutopilotBlockEntity.Channel.class);
    /** The nose gear's steering relays, by the way they turn it, and the main gear's brake relays. */
    private final Map<String, List<BlockPos>> steerRelays = new HashMap<>();
    private final List<BlockPos> brakeRelays = new ArrayList<>();
    /** Where the flight deck leaves room for the Physics Assembler, by the wall behind the captain; null without a flight deck. */
    private BlockPos assemblerSpot;
    /** The jump light switch in the instrument panel; null without a flight deck. */
    private BlockPos jumpSwitch;
    /** The seatbelt switch in the instrument panel; null without a flight deck. */
    private BlockPos seatbeltSwitch;
    /** The drop release on the first officer's side console; null without a flight deck. */
    private BlockPos dropRelease;
    /** The cabin lights' dimmer on the panel and the window shades' control by the captain; null without a flight deck. */
    private BlockPos cabinLights;
    private BlockPos windowShades;
    /** The cabin floor's block row, in the layout's own y before it is lifted onto its wheels. */
    private final int floorY;
    private int minY = Integer.MAX_VALUE;
    /** The row the tires stand on, once the landing gear is down. */
    private int ground;

    private AirframeLayout(double scale, AirframePalette palette) {
        this.scale = scale;
        this.palette = palette;
        this.floorY = (int) Math.floor(FLOOR * HEIGHT * scale);
    }

    /** The finished layout, its lowest blocks (the tires) at y 0 and its nose tip at z 0. */
    public static AirframeLayout create(double scale, boolean furnished) {
        AirframeLayout layout = new AirframeLayout(scale, AirframePalette.fromConfig());
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
        for (double z : PASSENGER_DOORS) {
            layout.door(1, z, layout.floorY + 1);
            layout.door(-1, z, layout.floorY + 1);
        }
        // Only where the hold is tall enough to walk into; at the smallest scales it is a crawlspace.
        if (layout.floorY - 2 >= 1) {
            layout.door(1, CARGO_DOOR, layout.floorY - 2);
        }
        layout.fairings();
        layout.navLights();
        layout.flightDeck();
        // Furnished before the gear goes on: the gear stands under the centre of mass, pool and all.
        if (furnished) {
            new CabinFitOut(layout).apply();
        }
        new CabinLining(layout).apply(furnished ? CabinFitOut::lockersFit : metres -> true, 0, (int) Math.ceil(LENGTH * layout.scale));
        layout.skydiving();
        layout.seatbeltSigns();
        layout.dropBay();
        layout.shell();
        layout.landingGear();
        layout.trimTail();
        layout.boardingStairs();
        layout.assembler();
        layout.cabinControls();
        return layout.lifted();
    }

    public Map<BlockPos, BlockState> cells() {
        return cells;
    }

    double scale() {
        return scale;
    }

    int floorY() {
        return floorY;
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

    /** Puts a fitting into the cabin; only where there was open space, so the hull is never cut into. */
    void furnish(int x, int y, int z, BlockState state) {
        if (isOpen(x, y, z)) {
            set(x, y, z, state);
        }
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

    private boolean has(int x, int y, int z) {
        return cells.containsKey(new BlockPos(x, y, z));
    }

    private boolean inFuselage(int x, int y, int z) {
        return AirframeShape.inFuselage(x / scale, (y + 0.5) / scale, (z + 0.5) / scale);
    }

    private boolean inFuselage(BlockPos pos) {
        return inFuselage(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * How the fuselage's skin cuts through the block, as a {@link ShellGeometry} shape: the whole block where the skin
     * lies beyond it, and -1 where it leaves next to nothing of it. The skin is found from the block's middle, straight
     * out the way the fuselage faces there.
     */
    private int shapeAt(int x, int y, int z) {
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
    private boolean inSkin(BlockPos pos) {
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
     * row from eye level over the main deck up past the eyes of someone seated, the other where the upper deck's
     * would be. They stand on every other station, never one a wall light or a door takes, wherever the hull faces
     * out sideways, flat or curved: the skin pass cuts them to the curve along with the skin around them.
     */
    private void windows() {
        int mainRow = floorY + 2;
        int upperRow = floorY + (int) Math.round(3.0 * scale) + 2;
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
     * hull just beside it (the airstairs' panel, a jump light).
     */
    private boolean doorAt(int z) {
        for (double station : PASSENGER_DOORS) {
            int fore = (int) Math.round(station * scale);
            if (z >= fore - 1 && z <= fore + 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cabin lights set into the hull, on every few stations: one in the crown and one in each wall just above head
     * height over the main deck, and one in the belly of the hold. Spaced so that light reaches every block of the
     * cabin and hold, and no mob finds a dark spot to spawn in.
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
     * The windscreen: the skin under the glass becomes cockpit glass, but for the posts that divide it into panes,
     * which stay skin: the column down the middle, and in each row and on each side, the block facing closest to
     * each side post's angle round the nose. The shell cuts the glass to the curve with the skin around it.
     */
    private void windscreen() {
        BlockState glass = AerolargeRegistry.COCKPIT_GLASS.get().defaultBlockState();
        int last = (int) Math.ceil(NOSE_LENGTH * scale);
        int halfWidth = (int) Math.ceil(WIDTH / 2 * scale) + 1;
        for (int y = 0; y <= HEIGHT * scale; y++) {
            for (int side : new int[]{1, -1}) {
                List<BlockPos> row = new ArrayList<>();
                for (int z = 0; z <= last; z++) {
                    for (int x = side; Math.abs(x) <= halfWidth; x += side) {
                        BlockState state = stateAt(x, y, z);
                        boolean skin = state.is(palette.hull().getBlock()) || state.is(palette.light().getBlock());
                        if (skin && Windscreen.covers((y + 0.5) / scale, (z + 0.5) / scale)) {
                            row.add(new BlockPos(x, y, z));
                        }
                    }
                }
                Set<BlockPos> posts = new HashSet<>();
                for (double post : Windscreen.SIDE_POSTS) {
                    row.stream()
                        .filter(pos -> Math.abs(facing(pos) - post) <= Windscreen.POST_TOLERANCE)
                        .min(Comparator.comparingDouble(pos -> Math.abs(facing(pos) - post)))
                        .ifPresent(posts::add);
                }
                row.stream().filter(pos -> !posts.contains(pos)).forEach(pos -> set(pos.getX(), pos.getY(), pos.getZ(), glass));
            }
        }
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
        if (floorY - 2 >= 1) {
            jumpDoor(1, CARGO_DOOR, floorY - 2, relays);
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

    /** The jump light by one door: in the hull just ahead of it, in one of the door's rows; the rack inside by it. */
    private void jumpDoor(int side, double station, int bottom, List<BlockPos> relays) {
        int z = (int) Math.round(station * scale) - 1;
        BlockPos light = wallLamp(side, z, new int[]{bottom + 1, bottom, bottom + 2}, AerolargeRegistry.JUMP_LIGHT.get().defaultBlockState(), relays);
        // Facing the door's aisle, aft of it.
        if (light != null && isOpen(light.getX() - side, bottom, z)) {
            set(light.getX() - side, bottom, z, AerolargeRegistry.PARACHUTE_RACK.get().defaultBlockState().setValue(ParachuteRackBlock.FACING, Direction.SOUTH));
        }
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
                    BlockPos bay = new BlockPos(0, y, z);
                    set(0, y, z, AerolargeRegistry.DROP_BAY.get().defaultBlockState());
                    if (dropRelease != null) {
                        BlockPos at = dropRelease;
                        fittings.put(at, (blockEntity, rotation) -> {
                            if (blockEntity instanceof DropReleaseBlockEntity control) {
                                control.configure(bay.subtract(at).rotate(rotation));
                            }
                        });
                    }
                    return;
                }
            }
        }
    }

    /**
     * "Fasten seatbelts" signs in both cabin walls along the cabin, at head height under the overhead lockers, each lit
     * through a relay beside it; the seatbelt switch in the panel is wired to them all.
     */
    private void seatbeltSigns() {
        List<BlockPos> relays = new ArrayList<>();
        int head = floorY + 4;
        for (double station : SEATBELT_SIGN_STATIONS) {
            int z = (int) Math.round(station * scale);
            for (int side : new int[]{1, -1}) {
                wallLamp(side, z, new int[]{head, head + 1, head - 1}, AerolargeRegistry.SEATBELT_SIGN.get().defaultBlockState(), relays);
            }
        }
        if (seatbeltSwitch == null || relays.isEmpty()) {
            return;
        }
        BlockPos at = seatbeltSwitch;
        fittings.put(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof LinkedControlBlockEntity control) {
                control.configure(Map.of(SeatbeltSwitchBlock.SIGNS, offsets(at, relays, rotation)));
            }
        });
    }

    /**
     * A lamp set into the hull at a station on one side, with the relay that lights it just above or below it in the
     * wall, plated like the skin: in the first of the rows where both replace plain skin, not a window. Adds the relay to the list and gives
     * where the lamp went, or null if no row had room.
     */
    @Nullable
    private BlockPos wallLamp(int side, int z, int[] rows, BlockState lamp, List<BlockPos> relays) {
        for (int y : rows) {
            int x = side * ((int) Math.ceil(WIDTH / 2 * scale) + 1);
            while (x != 0 && !inFuselage(x, y, z)) {
                x -= side;
            }
            if (!stateAt(x, y, z).is(palette.hull().getBlock())) {
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
     * Cuts the fuselage's skin to its curve. Every skin block the fuselage's surface passes through becomes shell, cut
     * where the surface crosses it, and the windscreen's glass and the cabin windows are cut the same way. Outside
     * blocks the surface still passes through get shell (or glass, or window) of their own, so the skin closes into one
     * smooth surface. Cut or not, window panes right above one another are then joined into tall windows.
     * Skin next to anything built onto the hull from outside (the wings, gear or exterior lights) or round a door stays
     * as it is so nothing opens up around it, and no shell is added over the doors set into the skin.
     */
    private void shell() {
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
            shaped.put(pos, (glazed ? cell.getValue().getBlock().defaultBlockState() : pane ? curvedPane : shell).setValue(ShellBlock.SHAPE, shape));
        }
        for (BlockPos pos : outside) {
            int shape = shapeAt(pos.getX(), pos.getY(), pos.getZ());
            if (inFuselage(pos) || shape <= ShellGeometry.WHOLE || nextToFitting(pos, true)) {
                continue;
            }
            BlockState in = inward(pos);
            BlockState outer = in.getBlock() instanceof CockpitGlassBlock ? in.getBlock().defaultBlockState() : CabinWindowBlock.isPane(in) ? curvedPane : shell;
            shaped.put(pos, outer.setValue(ShellBlock.SHAPE, shape));
        }
        cells.putAll(shaped);
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            if (CabinWindowBlock.isPane(cell.getValue())) {
                BlockPos pos = cell.getKey();
                cell.setValue(cell.getValue()
                    .setValue(CabinWindowBlock.UP, CabinWindowBlock.isPane(stateAt(pos.getX(), pos.getY() + 1, pos.getZ())))
                    .setValue(CabinWindowBlock.DOWN, CabinWindowBlock.isPane(stateAt(pos.getX(), pos.getY() - 1, pos.getZ()))));
            }
        }
    }

    /**
     * Whether a door is set into the skin right beside this block. The skin round a door stays whole: a door is flat,
     * and skin cut to the curve beside it would leave a wedge of open air along its edges.
     */
    private boolean nextToDoor(BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (stateAt(next.getX(), next.getY(), next.getZ()).is(AerolargeRegistry.AIRLINER_DOOR.get())) {
                return true;
            }
        }
        return false;
    }

    /** The skin block straight in from this one, against the way the fuselage faces there. */
    private BlockState inward(BlockPos pos) {
        double[] out = AirframeShape.outward(pos.getX() / scale, (pos.getY() + 0.5) / scale, (pos.getZ() + 0.5) / scale);
        Direction in = Direction.getNearest(-out[0], -out[1], -out[2]);
        return stateAt(pos.getX() + in.getStepX(), pos.getY() + in.getStepY(), pos.getZ() + in.getStepZ());
    }

    /**
     * Whether something other than skin is built onto the hull next to this block: whatever stands outside the
     * fuselage and is not hull, glass, a window or a light; with {@code setIn}, also whatever is set into the skin
     * itself, like the doors.
     */
    private boolean nextToFitting(BlockPos pos, boolean setIn) {
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
     * windscreen, steps up to it from the main deck, the instrument panel along the front, and two seats either side
     * of the pedestal with the four thrust levers, engines 1 to 4 from left to right as on the A380.
     */
    private void flightDeck() {
        int deck = Math.max(floorY + 1, Windscreen.deckRow(scale));
        int deckEnd = (int) Math.round(0.95 * NOSE_LENGTH * scale);
        for (int z = 0; z <= deckEnd; z++) {
            int width = openHalfWidth(deck, z);
            for (int x = -width; x <= width; x++) {
                set(x, deck, z, palette.floor());
            }
        }
        // Steps from the main deck up to the flight deck's back edge, rising toward the nose; the top one stands level
        // with the deck, so its high side runs flush onto the deck's floor.
        BlockState step = Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int k = 1; deck + 1 - k > floorY; k++) {
            for (int x = -1; x <= 1; x++) {
                furnish(x, deck + 1 - k, deckEnd + k, step);
            }
        }

        // The panel goes along the first station where two pilots fit side by side, the pedestal two rows back.
        int panelZ = -1;
        for (int z = 0; z <= deckEnd - 3; z++) {
            if (openHalfWidth(deck + 1, z) >= 3 && openHalfWidth(deck + 1, z + 2) >= 3) {
                panelZ = z;
                break;
            }
        }
        if (panelZ < 0) {
            return;
        }
        BlockState panel = AerolargeRegistry.INSTRUMENT_PANEL.get().defaultBlockState().setValue(InstrumentPanelBlock.FACING, Direction.SOUTH);
        int panelWidth = openHalfWidth(deck + 1, panelZ);
        for (int x = -panelWidth; x <= panelWidth; x++) {
            set(x, deck + 1, panelZ, panel);
        }
        int pedestalZ = panelZ + 2;
        double[] engines = {-ENGINE_X[1], -ENGINE_X[0], ENGINE_X[0], ENGINE_X[1]};
        int[] leverX = {-2, -1, 1, 2};
        List<BlockPos> levers = new ArrayList<>();
        for (int i = 0; i < engines.length; i++) {
            BlockPos lever = new BlockPos(leverX[i], deck + 1, pedestalZ);
            levers.add(lever);
            set(lever.getX(), lever.getY(), lever.getZ(),
                AerolargeRegistry.THRUST_LEVER.get().defaultBlockState().setValue(ThrustLeverBlock.FACING, Direction.NORTH));
            List<BlockPos> relays = engineRelays.getOrDefault(engines[i], List.of()).stream().map(relay -> relay.subtract(lever)).toList();
            fittings.put(lever, (blockEntity, rotation) -> {
                if (blockEntity instanceof ThrustLeverBlockEntity thrustLever) {
                    thrustLever.configure(relays.stream().map(offset -> offset.rotate(rotation)).toList());
                }
            });
        }
        int seatX = openHalfWidth(deck + 1, pedestalZ) >= 4 ? 4 : 3;
        // A flight display in the panel in front of each seat.
        for (int side : new int[]{1, -1}) {
            int displayX = side * Math.min(seatX, panelWidth);
            if (stateAt(displayX, deck + 1, panelZ).is(panel.getBlock())) {
                set(displayX, deck + 1, panelZ, AerolargeRegistry.FLIGHT_DISPLAY.get().defaultBlockState().setValue(FlightDisplayBlock.FACING, Direction.SOUTH));
            }
        }
        palette.seat().ifPresent(seat -> {
            set(-seatX, deck + 1, pedestalZ, seat);
            set(seatX, deck + 1, pedestalZ, seat);
        });
        List<BlockPos> controls = new ArrayList<>(levers);
        BlockPos tiller = new BlockPos(-seatX - 1, deck + 1, pedestalZ);
        BlockPos brake = new BlockPos(seatX + 1, deck + 1, pedestalZ);
        if (isOpen(tiller.getX(), tiller.getY(), tiller.getZ()) && isOpen(brake.getX(), brake.getY(), brake.getZ())) {
            groundControls(tiller, brake);
            controls.add(tiller);
            controls.add(brake);
        }
        BlockPos autopilot = new BlockPos(0, deck + 1, pedestalZ);
        autopilot(autopilot, engines, controls);
        autopilotScreen(deck + 1, panelZ, autopilot);
        // The jump light switch in the panel, beside the screen on the first officer's side.
        int switchX = AutopilotScreenBlock.COLUMNS / 2 + 1;
        if (switchX <= panelWidth && stateAt(switchX, deck + 1, panelZ).is(panel.getBlock())) {
            jumpSwitch = new BlockPos(switchX, deck + 1, panelZ);
            set(switchX, deck + 1, panelZ, AerolargeRegistry.JUMP_SWITCH.get().defaultBlockState().setValue(JumpSwitchBlock.FACING, Direction.SOUTH));
            // The cabin lights' dimmer on it.
            if (isOpen(switchX, deck + 2, panelZ)) {
                cabinLights = new BlockPos(switchX, deck + 2, panelZ);
                set(switchX, deck + 2, panelZ, AerolargeRegistry.CABIN_LIGHTS.get().defaultBlockState().setValue(CabinLightsBlock.FACING, Direction.SOUTH));
            }
        }
        // On the captain's side of the screen: the seatbelt switch in the panel, and the handset on it.
        int captainX = -switchX;
        if (captainX >= -panelWidth && stateAt(captainX, deck + 1, panelZ).is(panel.getBlock())) {
            seatbeltSwitch = new BlockPos(captainX, deck + 1, panelZ);
            set(captainX, deck + 1, panelZ, AerolargeRegistry.SEATBELT_SWITCH.get().defaultBlockState().setValue(SeatbeltSwitchBlock.FACING, Direction.SOUTH));
            if (isOpen(captainX, deck + 2, panelZ)) {
                set(captainX, deck + 2, panelZ, AerolargeRegistry.HANDSET.get().defaultBlockState().setValue(HandsetBlock.FACING, Direction.SOUTH));
            }
        }
        // The window shades' control on the captain's side console, just behind the tiller.
        BlockPos shades = new BlockPos(-seatX - 1, deck + 1, pedestalZ + 1);
        if (isOpen(shades.getX(), shades.getY(), shades.getZ())) {
            windowShades = shades;
            set(shades.getX(), shades.getY(), shades.getZ(), AerolargeRegistry.WINDOW_SHADES.get().defaultBlockState().setValue(WindowShadesBlock.FACING, Direction.EAST));
        }
        // The drop release on the first officer's side console, just behind the parking brake.
        BlockPos release = new BlockPos(seatX + 1, deck + 1, pedestalZ + 1);
        if (isOpen(release.getX(), release.getY(), release.getZ())) {
            dropRelease = release;
            set(release.getX(), release.getY(), release.getZ(), AerolargeRegistry.DROP_RELEASE.get().defaultBlockState().setValue(DropReleaseBlock.FACING, Direction.WEST));
        }
        // The Physics Assembler out of the way, against the wall behind the captain's seat.
        int assemblerZ = pedestalZ + 2;
        assemblerSpot = new BlockPos(-openHalfWidth(deck + 1, assemblerZ), deck + 1, assemblerZ);
    }

    /**
     * The autopilot screen, set into the middle of the instrument panel and standing a block above it, facing the
     * pilots, if the nose leaves it the room; linked to the autopilot when the airliner is built.
     */
    private void autopilotScreen(int bottom, int z, BlockPos autopilot) {
        int left = -(AutopilotScreenBlock.COLUMNS / 2);
        for (int row = 1; row < AutopilotScreenBlock.ROWS; row++) {
            for (int column = 0; column < AutopilotScreenBlock.COLUMNS; column++) {
                if (!isOpen(left + column, bottom + row, z)) {
                    return;
                }
            }
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
     * The autopilot panel in the middle of the pedestal, wired to every relay it flies with: the four engines', the
     * control jets', and the landing gear's steering and brake relays, and to the controls it hands the airliner back
     * to.
     */
    private void autopilot(BlockPos at, double[] engines, List<BlockPos> controls) {
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
                for (int i = 0; i < engines.length; i++) {
                    channels.put(engineChannels[i], engineRelays.getOrDefault(engines[i], List.of()));
                }
                channels.putAll(jetRelays);
                channels.put(AutopilotBlockEntity.Channel.STEER_LEFT, steerRelays.getOrDefault(TillerBlock.LEFT, List.of()));
                channels.put(AutopilotBlockEntity.Channel.STEER_RIGHT, steerRelays.getOrDefault(TillerBlock.RIGHT, List.of()));
                channels.put(AutopilotBlockEntity.Channel.BRAKES, brakeRelays);
                Map<AutopilotBlockEntity.Channel, List<BlockPos>> turned = new EnumMap<>(AutopilotBlockEntity.Channel.class);
                channels.forEach((channel, relays) -> turned.put(channel, offsets(at, relays, rotation)));
                autopilot.configure(turned, offsets(at, controls, rotation), at.getY() - ground);
            }
        });
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
     * Control jets for the autopilot, standing in for the control surfaces the airliner does not have: jets on top of
     * and under the outer tailplane to pitch it (pushing the tail down lifts the nose), and on top of and under the
     * outer wings to roll it, at a few stations along each so they can hold the airliner against its engines. Each
     * is a thruster pointing up or down with its own relay right behind it.
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
     * the cowl's lip, the fan and its spinner over it, round at any scale.
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
                        shaped.put(pos, shell.setValue(ShellBlock.SHAPE, Math.max(shape, ShellGeometry.WHOLE)));
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
     * A two-leaf door, two blocks wide, cut through the hull on one side at station z, its bottom on row y, standing
     * flat in one column at the hull's outermost reach there and framed by hull. Where the curve of the hull makes the
     * wall more than a block thick, the rest is cut away so the door opens into the cabin.
     */
    private void door(int side, double station, int bottom) {
        int fore = (int) Math.round(station * scale);
        Direction facing = side > 0 ? Direction.WEST : Direction.EAST;
        // The whole door stands in one column, the outermost the hull reaches at any of its blocks: a door's halves
        // must stand one on the other, and below the floor the hull curves in so fast that the lower row's skin can
        // lie a block further in than the upper's.
        int outer = 0;
        for (int leaf = 0; leaf < 2; leaf++) {
            for (int part = 0; part < 2; part++) {
                int row = side * ((int) Math.ceil(WIDTH / 2 * scale) + 1);
                while (row != 0 && !inFuselage(row, bottom + part, fore + leaf)) {
                    row -= side;
                }
                if (Math.abs(row) > Math.abs(outer)) {
                    outer = row;
                }
            }
        }
        for (int leaf = 0; leaf < 2; leaf++) {
            int z = fore + leaf;
            // The aft leaf's partner is ahead of it (north), the fore leaf's behind it (south); a leaf hinges on the
            // side away from its partner, the way two doors placed side by side by hand pair up.
            Direction partner = leaf == 0 ? Direction.SOUTH : Direction.NORTH;
            DoorHingeSide hinge = partner == facing.getCounterClockWise() ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
            for (int part = 0; part < 2; part++) {
                int y = bottom + part;
                for (int x = outer - side; x != 0 && has(x, y, z) && !isOpen(x, y, z); x -= side) {
                    set(x, y, z, Blocks.AIR.defaultBlockState());
                }
                // Below the door the hull already curves inward: close the gap with a sill out to the door.
                for (int x = outer; part == 0 && x != 0 && !has(x, y - 1, z); x -= side) {
                    set(x, y - 1, z, palette.hull());
                }
                set(outer, y, z, AerolargeRegistry.AIRLINER_DOOR.get().defaultBlockState()
                    .setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HINGE, hinge)
                    .setValue(DoorBlock.HALF, part == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER));
            }
        }
        // A frame of hull round the doorway, flush with the door, wherever the curve leaves the hull short of it: the
        // jambs either side and the lintel above. The skin pass leaves it whole (see nextToDoor), so nothing gapes.
        for (int y = bottom; y <= bottom + 2; y++) {
            for (int z = fore - 1; z <= fore + 2; z++) {
                if ((y == bottom + 2 || z == fore - 1 || z == fore + 2) && !has(outer, y, z)) {
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
        ground = minY - Math.max(2, (int) Math.round(GROUND_CLEARANCE * scale));
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
     * Folding airstairs up to the forward port door, the way passengers board: a landing in front of the door and a
     * two-wide flight running down toward the nose to the ground, built standing. The airstair controller set into
     * the hull just ahead of the door folds them into nothing and back out, so they are out of the way for flight;
     * a button beside it inside, one outside, and a call button on the nose gear for the ground crew work it.
     */
    private void boardingStairs() {
        int fore = (int) Math.round(PASSENGER_DOORS[0] * scale);
        int door = -((int) Math.ceil(WIDTH / 2 * scale) + 1);
        while (door < 0 && !inFuselage(door, floorY + 1, fore)) {
            door++;
        }
        BlockState landing = Blocks.POLISHED_ANDESITE.defaultBlockState();
        // Climbing them walks south, toward the door; a stair rises toward the way it faces.
        BlockState step = Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        List<List<AirstairControllerBlockEntity.Step>> stages = new ArrayList<>();
        stages.add(stage(List.of(
            new BlockPos(door - 1, floorY, fore), new BlockPos(door - 2, floorY, fore),
            new BlockPos(door - 1, floorY, fore + 1), new BlockPos(door - 2, floorY, fore + 1)
        ), landing));
        // The top step stands level with the landing, so its high side runs flush onto it.
        for (int k = 1; floorY + 1 - k >= ground; k++) {
            int y = floorY + 1 - k;
            stages.add(stage(List.of(new BlockPos(door - 1, y, fore - k), new BlockPos(door - 2, y, fore - k)), step));
        }
        stages.removeIf(List::isEmpty);

        // The controller is the hull block just ahead of the door, at the height of the door's upper half.
        int row = floorY + 2;
        int panelZ = fore - 1;
        int panelX = -(openHalfWidth(row, panelZ) + 1);
        if (stages.isEmpty() || panelX >= 0 || !has(panelX, row, panelZ)) {
            return;
        }
        set(panelX, row, panelZ, AerolargeRegistry.AIRSTAIR_CONTROLLER.get().defaultBlockState());
        BlockState button = Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState().setValue(ButtonBlock.FACE, AttachFace.WALL);
        set(panelX + 1, row, panelZ, button.setValue(ButtonBlock.FACING, Direction.EAST));
        if (!has(panelX - 1, row, panelZ)) {
            set(panelX - 1, row, panelZ, button.setValue(ButtonBlock.FACING, Direction.WEST));
        }
        BlockPos controller = new BlockPos(panelX, row, panelZ);
        List<List<AirstairControllerBlockEntity.Step>> relative = stages.stream()
            .map(stage -> stage.stream().map(at -> new AirstairControllerBlockEntity.Step(at.offset().subtract(controller), at.state())).toList())
            .toList();
        fittings.put(controller, (blockEntity, rotation) -> {
            if (blockEntity instanceof AirstairControllerBlockEntity airstairs) {
                airstairs.configure(relative.stream().map(stage -> stage.stream().map(at -> {
                    BlockPos offset = at.offset().rotate(rotation);
                    BlockState turned = at.state().rotate(blockEntity.getLevel(), blockEntity.getBlockPos().offset(offset), rotation);
                    return new AirstairControllerBlockEntity.Step(offset, turned);
                }).toList()).toList());
            }
        });

        // The ground crew's call button, on the front of the nose gear's strut above the axle, clear of the wheels.
        int noseZ = (int) Math.round(NOSE_GEAR_Z * scale);
        int callY = ground + 2;
        if (stateAt(0, callY, noseZ).is(palette.gear().getBlock()) && !has(0, callY, noseZ - 1)) {
            set(0, callY, noseZ - 1, AerolargeRegistry.AIRSTAIR_CALL_BUTTON.get().defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
        }
    }

    /** One stage of the stairs: those of its blocks that fit, placed standing. */
    private List<AirstairControllerBlockEntity.Step> stage(List<BlockPos> positions, BlockState state) {
        List<AirstairControllerBlockEntity.Step> stage = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (!has(pos.getX(), pos.getY(), pos.getZ())) {
                set(pos.getX(), pos.getY(), pos.getZ(), state);
                stage.add(new AirstairControllerBlockEntity.Step(pos, state));
            }
        }
        return stage;
    }

    public Map<BlockPos, Fitting> fittings() {
        return fittings;
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
            for (Direction out : new Direction[]{Direction.EAST, Direction.WEST}) {
                BlockPos at = new BlockPos(x + out.getStepX(), axle, z);
                set(at.getX(), at.getY(), at.getZ(), AirframePalette.with(palette.wheelMount().get(), "facing", out.getSerializedName()));
                Item tire = palette.tireItem().get();
                fittings.put(at, (blockEntity, rotation) -> fitWheel(blockEntity, tire));
                if (steers) {
                    relay(at.relative(out.getClockWise()), steerRelays.computeIfAbsent(TillerBlock.LEFT, ignored -> new ArrayList<>()));
                    relay(at.relative(out.getCounterClockWise()), steerRelays.computeIfAbsent(TillerBlock.RIGHT, ignored -> new ArrayList<>()));
                } else {
                    relay(at.above(), brakeRelays);
                }
            }
            set(x, axle, z, palette.gear());
            leg(x, axle + 1, z);
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
        leg(x, ground + tireHeight, z);
    }

    /**
     * A gear leg from row y up to the airframe, and on up through any skin there cut to the hull's curve (the gear goes
     * on after the skin is cut), so the leg meets the hull itself instead of stopping short under a sliver of skin.
     */
    private void leg(int x, int y, int z) {
        int top = y + 64;
        for (; y < top && !has(x, y, z); y++) {
            set(x, y, z, palette.gear());
        }
        for (; y < top && stateAt(x, y, z).is(AerolargeRegistry.AIRFRAME_SHELL.get()); y++) {
            set(x, y, z, palette.gear());
        }
    }

    /** A relay at the position if it is free, added to the group. */
    private void relay(BlockPos pos, List<BlockPos> group) {
        if (!has(pos.getX(), pos.getY(), pos.getZ())) {
            set(pos.getX(), pos.getY(), pos.getZ(), AerolargeRegistry.THRUST_RELAY.get().defaultBlockState());
            group.add(pos);
        }
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
        List<BlockPos> targets = cells.entrySet().stream()
            .filter(cell -> works.test(cell.getValue()))
            .map(Map.Entry::getKey)
            .sorted(Comparator.comparingInt((BlockPos pos) -> pos.getZ()).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX))
            .toList();
        fittings.put(control, (blockEntity, rotation) -> {
            if (blockEntity instanceof CabinControlBlockEntity cabinControl) {
                cabinControl.configure(offsets(control, targets, rotation));
            }
        });
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
