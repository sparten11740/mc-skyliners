package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.AirframeShape.*;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.AirstairControllerBlockEntity;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlock;
import dev.aerolarge.block.AutopilotScreenBlockEntity;
import dev.aerolarge.block.EngineCoreBlock;
import dev.aerolarge.block.FairingBlock;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.InstrumentPanelBlock;
import dev.aerolarge.block.LinkedControlBlockEntity;
import dev.aerolarge.block.NavLightBlock;
import dev.aerolarge.block.ParkingBrakeBlock;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.ShellGeometry;
import dev.aerolarge.block.ThrustLeverBlock;
import dev.aerolarge.block.ThrustLeverBlockEntity;
import dev.aerolarge.block.TillerBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;
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
    /** Where the flight deck leaves room for the Physics Assembler, behind the pilots; null without a flight deck. */
    private BlockPos assemblerSpot;
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
        layout.shell();
        layout.landingGear();
        layout.trimTail();
        layout.boardingStairs();
        layout.assembler();
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

    /** How far out from the centreline the cabin is open at this height and station; -1 where it is closed. */
    int openHalfWidth(int y, int z) {
        int x = -1;
        while (isOpen(x + 1, y, z)) {
            x++;
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
     * Two rows of cabin windows down each side, one at eye level over the main deck and one where the upper deck's
     * would be.
     */
    private void windows() {
        int mainRow = floorY + 2;
        int upperRow = floorY + (int) Math.round(3.0 * scale) + 2;
        int first = (int) Math.ceil((NOSE_LENGTH + 1) * scale);
        int last = (int) Math.floor((TAIL_START + 8) * scale);
        for (int z = first; z <= last; z += 2) {
            for (int y : new int[]{mainRow, upperRow}) {
                for (int side : new int[]{1, -1}) {
                    int x = side * (openHalfWidth(y, z) + 1);
                    // Only where the hull there is a wall facing out sideways, with the cabin right behind it, and
                    // whole: not cut to the curve, which a window would stick out of.
                    if (openHalfWidth(y, z) >= 0 && has(x, y, z) && !inFuselage(x + side, y, z) && shapeAt(x, y, z) == ShellGeometry.WHOLE) {
                        set(x, y, z, palette.cabinWindow());
                    }
                }
            }
        }

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
     * Cuts the fuselage's skin to its curve. Every skin block the fuselage's surface passes through becomes shell, cut
     * where the surface crosses it, and under the windscreen the glass is cut the same way. Outside blocks the surface
     * still passes through get shell (or glass) of their own, so the skin closes into one smooth surface.
     * Skin next to anything built onto the hull from outside, the wings, gear or exterior lights, stays as it is so
     * nothing opens up around it, and no shell is added over the windows and doors set into the skin.
     */
    private void shell() {
        BlockState shell = AerolargeRegistry.AIRFRAME_SHELL.get().defaultBlockState();
        BlockState glass = AerolargeRegistry.COCKPIT_GLASS.get().defaultBlockState();
        Map<BlockPos, BlockState> shaped = new HashMap<>();
        Set<BlockPos> outside = new HashSet<>();
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            BlockPos pos = cell.getKey();
            boolean glazed = cell.getValue().is(glass.getBlock());
            if (!glazed && !cell.getValue().is(palette.hull().getBlock()) || !inSkin(pos)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                if (!cells.containsKey(pos.relative(side))) {
                    outside.add(pos.relative(side));
                }
            }
            int shape = shapeAt(pos.getX(), pos.getY(), pos.getZ());
            if (shape <= ShellGeometry.WHOLE || nextToFitting(pos, false)) {
                continue;
            }
            shaped.put(pos, (glazed ? glass : shell).setValue(ShellBlock.SHAPE, shape));
        }
        for (BlockPos pos : outside) {
            int shape = shapeAt(pos.getX(), pos.getY(), pos.getZ());
            if (inFuselage(pos) || shape <= ShellGeometry.WHOLE || nextToFitting(pos, true)) {
                continue;
            }
            shaped.put(pos, (onGlass(pos) ? glass : shell).setValue(ShellBlock.SHAPE, shape));
        }
        cells.putAll(shaped);
    }

    /** Whether the skin block straight in from this one, against the way the fuselage faces there, is glass. */
    private boolean onGlass(BlockPos pos) {
        double[] out = AirframeShape.outward(pos.getX() / scale, (pos.getY() + 0.5) / scale, (pos.getZ() + 0.5) / scale);
        Direction in = Direction.getNearest(-out[0], -out[1], -out[2]);
        return stateAt(pos.getX() + in.getStepX(), pos.getY() + in.getStepY(), pos.getZ() + in.getStepZ()).is(AerolargeRegistry.COCKPIT_GLASS.get());
    }

    /**
     * Whether something other than skin is built onto the hull next to this block: whatever stands outside the
     * fuselage and is not hull, glass or a light; with {@code setIn}, also whatever is set into the skin itself, like
     * the windows and doors.
     */
    private boolean nextToFitting(BlockPos pos, boolean setIn) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            BlockState state = cells.get(next);
            if (state == null || state.isAir() || state.is(palette.hull().getBlock()) || state.is(palette.light().getBlock())
                || state.is(AerolargeRegistry.COCKPIT_GLASS.get())) {
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
        // Steps from the main deck up to the flight deck's back edge, rising toward the nose.
        BlockState step = Blocks.POLISHED_ANDESITE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int k = 1; deck - k > floorY; k++) {
            for (int x = -1; x <= 1; x++) {
                furnish(x, deck - k, deckEnd + k, step);
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
        assemblerSpot = new BlockPos(0, deck + 1, pedestalZ + 2);
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
     * One engine: a round nacelle under the wing at span station x (negative to port), the intake face up front,
     * the thrusters filling the middle of the exhaust face, and a pylon up to the wing.
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
                        state = palette.intake();
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
            .ifPresent(core -> set(core.getX(), core.getY(), core.getZ(),
                AerolargeRegistry.ENGINE_CORE.get().defaultBlockState().setValue(EngineCoreBlock.FACING, Direction.NORTH)));
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

    private boolean inNacelle(double x, double centreY, double front, int xb, int y, int z) {
        double radius = nacelleRadius((z + 0.5) / scale - front);
        double dx = xb / scale - x;
        double dy = (y + 0.5) / scale - centreY;
        return radius > 0 && dx * dx + dy * dy <= radius * radius;
    }

    /**
     * A two-leaf door, two blocks wide, cut through the hull on one side at station z, its bottom on row y. Where
     * the curve of the hull makes the wall more than a block thick, the rest is cut away so the door opens into the
     * cabin.
     */
    private void door(int side, double station, int bottom) {
        int fore = (int) Math.round(station * scale);
        Direction facing = side > 0 ? Direction.WEST : Direction.EAST;
        for (int leaf = 0; leaf < 2; leaf++) {
            int z = fore + leaf;
            // The aft leaf's partner is ahead of it (north), the fore leaf's behind it (south); a leaf hinges on the
            // side away from its partner, the way two doors placed side by side by hand pair up.
            Direction partner = leaf == 0 ? Direction.SOUTH : Direction.NORTH;
            DoorHingeSide hinge = partner == facing.getCounterClockWise() ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;
            for (int part = 0; part < 2; part++) {
                int y = bottom + part;
                int outer = side * ((int) Math.ceil(WIDTH / 2 * scale) + 1);
                while (outer != 0 && !inFuselage(outer, y, z)) {
                    outer -= side;
                }
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
        for (int k = 1; floorY - k >= ground; k++) {
            stages.add(stage(List.of(new BlockPos(door - 1, floorY - k, fore - k), new BlockPos(door - 2, floorY - k, fore - k)), step));
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
            for (int y = axle; y < axle + 64 && (y == axle || !has(x, y, z)); y++) {
                set(x, y, z, palette.gear());
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
        for (int y = ground + tireHeight; y < ground + tireHeight + 64 && !has(x, y, z); y++) {
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
