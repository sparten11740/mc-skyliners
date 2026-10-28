package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.RocketPilot;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.OvalWindowBlock;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.ShellGeometry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rocket cut into blocks before it is put anywhere (see {@link Layout}), standing on the ground, its front (the
 * door's side) to the north, its outline at the airframe's scale (see {@link RocketShape}) and everything a player
 * uses at a player's size. Only its blocks are kept: the air inside its body is worked out from its shape as it is
 * handed over, so that whatever stood there is cleared first.
 *
 * <p>Its body is a skin of the zeppelin's cover, cut to its round side and its pointed nose in the snapped style (see
 * {@link SurfaceCut}), on a flat bottom in which its engines stand: bells of creative thrusters pushing up, their
 * exhaust leaving past the four fins' feet, each thruster with a thrust relay over it that the autopilot drives (see
 * {@link RocketPilot}). Side jets in its skin, a ring of four near the nose and another near the bottom, push it
 * sideways and tilt it, which is how it stays upright. Inside, over an engine room, stories one above another round a
 * glass lift shaft up the middle, each floor's edge against the skin, a dimmable oval window out to each of the four
 * ways; the cockpit sits in the glass of the nose's lower part, its control head facing the front. The features
 * laid into it once it stands are pieces of their own (see {@link RocketPiece}).
 *
 * <p>Sable flies the rocket as one body only as far as its blocks touch, face to face: the floors run out to the skin,
 * the fins are two blocks thick where they run between the four ways, and the shaft stands on the floors.
 */
public final class RocketLayout implements Layout {
    /**
     * How many rows of open air the engines need under them, at least: Create Propulsion looks ten blocks behind a
     * thruster's nozzle for what stands in its exhaust, and the rocket's own fins and the ground count.
     */
    private static final int EXHAUST_CLEAR = 11;
    /** How many rows the engine room over the bottom takes, up to the first story's floor. */
    private static final int ENGINE_ROOM = 5;
    /** The stories' heights, floor to floor, from the entry story up, in rows: a player's size at every scale. */
    private static final int[] STORIES = {9, 10, 9, 9, 12, 12, 11, 12};
    /** How far out from the axis the lift shaft's glass walls stand, in blocks: three blocks of shaft inside them. */
    static final int SHAFT = 2;
    /** How wide and tall the front door is, in blocks, over the entry story's floor. */
    private static final int DOOR_HALF = 1;
    private static final int DOOR_ROWS = 3;
    /** How many rows over each story's floor its windows' lowest row is: a standing player's eyes are in the middle. */
    private static final int WINDOW_FROM = 1;
    /** How many rows of the nose's skin over the cockpit's floor are glass, its windows all round. */
    private static final int COCKPIT_GLASS = 7;
    /**
     * The engine bells: thrusters in a plus round each bell's middle, the middle bell under the shaft and one out to
     * each of the four ways, so many metres out from the axis.
     */
    private static final double BELL_OUT = 2.75;
    private static final int[][] BELL_THRUSTERS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** How far up from the bottom and down from the nose's foot the rings of side jets are, in rows. */
    private static final int JETS_UP = 3;
    private static final int JETS_DOWN = 4;
    /** How far behind the cockpit's front the control head stands, in blocks. */
    private static final int HEAD_BACK = 4;
    /** The fins: one between each two of the four ways. */
    private static final int[][] FINS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    /** The features laid once the body stands, in this order (see {@link RocketPiece}). */
    private static final List<Function<RocketLayout, RocketPiece>> PIECES = List.of(
        RocketLadder::new);

    private final AirframePalette.Balloon palette;
    private final double scale;
    /** How tall the rocket stands, in rows, and its body's radius, in blocks. */
    private final int height;
    private final double radius;
    /** The rows of the body's flat bottom, of the nose's foot and of the cockpit's floor. */
    private final int bottom;
    private final int noseFoot;
    /** How high the bottom stands, in metres, as the surface is measured. */
    private final double base;
    /** Every block of the rocket that is not the air inside its body, by position from the axis at the ground. */
    private final Map<BlockPos, BlockState> cells = new HashMap<>();
    private final Map<BlockPos, Fitting> fittings = new HashMap<>();
    /** Each story's floor row, from the entry story up. */
    private final List<Integer> floors = new ArrayList<>();
    /** Each engine bell's thrusters' relays, the middle bell first. */
    private final List<List<BlockPos>> bells = new ArrayList<>();
    /** The side jets' relays near the nose and near the bottom, by the way each pushes it. */
    private final Map<Direction, BlockPos> topJets = new HashMap<>();
    private final Map<Direction, BlockPos> bottomJets = new HashMap<>();
    /** How many blocks of air {@link #inOrder} hands over, found once everything else stands. */
    private int air = -1;

    /** The body's surface in blocks, as its skin is cut by it: the axis runs through the middle of block 0 each way. */
    private final SurfaceCut.Surface surface = new SurfaceCut.Surface() {
        @Override
        public double depth(double x, double y, double z) {
            return RocketShape.depth(x / scale, y / scale, z / scale, base) * scale;
        }

        @Override
        public double[] outward(double x, double y, double z) {
            return RocketShape.outward(x / scale, y / scale, z / scale, base);
        }
    };

    private RocketLayout(AirframePalette.Balloon palette, double scale) {
        this.palette = palette;
        this.scale = scale;
        this.height = (int) Math.round(RocketShape.HEIGHT * scale);
        this.radius = RocketShape.RADIUS * scale;
        this.bottom = Math.max((int) Math.round(RocketShape.BASE * scale), EXHAUST_CLEAR);
        this.base = bottom / scale;
        this.noseFoot = (int) Math.round(RocketShape.NOSE_BASE * scale);
    }

    /** The finished rocket at the configured scale: its fins' feet at y 0, its axis through x 0, z 0. */
    public static RocketLayout create() {
        RocketLayout layout = new RocketLayout(AirframePalette.balloon(), AerolargeConfig.SCALE.get());
        layout.skin();
        layout.fins();
        layout.stories();
        layout.windows();
        layout.shaft();
        layout.door();
        layout.engines();
        layout.jets();
        layout.cockpit();
        for (Function<RocketLayout, RocketPiece> kind : PIECES) {
            RocketPiece piece = kind.apply(layout);
            if (piece.wanted()) {
                piece.build();
            }
        }
        layout.countAir();
        return layout;
    }

    void set(int x, int y, int z, BlockState state) {
        cells.put(new BlockPos(x, y, z), state);
    }

    private BlockState stateAt(int x, int y, int z) {
        return cells.getOrDefault(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
    }

    /** Whether nothing of the rocket stands at the block yet: inside the body, its open air. */
    boolean isOpen(int x, int y, int z) {
        return !cells.containsKey(new BlockPos(x, y, z));
    }

    /** Whether a block's middle lies inside the body, a skin's thickness in from its surface. */
    boolean inside(int x, int y, int z) {
        return surface.depth(x, y + 0.5, z) < -1;
    }

    /** How far out the body reaches at most, fins and all, in blocks. */
    int reach() {
        return (int) Math.ceil(RocketShape.FIN_TIP * scale) + 1;
    }

    /** Each story's floor row, from the entry story up. */
    List<Integer> floors() {
        return floors;
    }

    /**
     * Where the skin's frontmost block is at a block across and a row, as far forward as it reaches, in blocks: the
     * axis's own row where nothing of the rocket stands there.
     */
    int frontSkin(int x, int y) {
        for (int z = -(int) Math.ceil(radius) - 2; z < 0; z++) {
            if (!isOpen(x, y, z)) {
                return z;
            }
        }
        return 0;
    }

    /**
     * The skin: every block the body's surface passes through, cut to it, over the flat bottom; glass all round the
     * cockpit in the nose (each story's windows are set in once its floors stand).
     */
    private void skin() {
        int out = (int) Math.ceil(radius) + 2;
        for (int y = bottom; y <= height; y++) {
            for (int x = -out; x <= out; x++) {
                skinRow(x, y, out);
            }
        }
    }

    private void skinRow(int x, int y, int out) {
        for (int z = -out; z <= out; z++) {
            int shape = SurfaceCut.shape(surface, x - 0.5, y, z - 0.5);
            if (shape != ShellGeometry.NONE) {
                set(x, y, z, ShellBlock.withShape(skinAt(y), shape));
            }
        }
    }

    /** What the skin is made of at a row: glass round the cockpit, the cover elsewhere. */
    private BlockState skinAt(int y) {
        return y > noseFoot && y <= noseFoot + COCKPIT_GLASS ? AerolargeRegistry.HULL_GLASS.get().defaultBlockState()
            : AerolargeRegistry.ZEPPELIN_COVER.get().defaultBlockState();
    }

    /**
     * The four fins, one between each two of the four ways: two blocks thick, so their blocks touch face to face,
     * from the body's side out to their outline (see {@link RocketShape#finReach}), and under the body from its
     * bottom's edge down to their feet on the ground.
     */
    private void fins() {
        int top = (int) Math.ceil(RocketShape.FIN_ROOT_TOP * scale);
        for (int[] fin : FINS) {
            for (int y = 0; y <= top; y++) {
                finRow(fin, y);
            }
        }
    }

    private void finRow(int[] fin, int y) {
        double height = (y + 0.5) / scale;
        double out = RocketShape.finReach(height) * scale;
        double in = RocketShape.finInner(height, base) * scale;
        for (int i = 0; i <= out; i++) {
            for (int j = Math.max(0, i - 1); j <= i + 1; j++) {
                finAt(fin[0] * i, y, fin[1] * j, in, out);
            }
        }
    }

    private void finAt(int x, int y, int z, double in, double out) {
        double across = Math.hypot(x, z);
        if (across <= out && across >= in && surface.depth(x, y + 0.5, z) > 0 && isOpen(x, y, z)) {
            set(x, y, z, AerolargeRegistry.ZEPPELIN_FABRIC.get().defaultBlockState());
        }
    }

    /** The stories' floors, from the entry story up as many as fit under the cockpit, each out to the skin. */
    private void stories() {
        int floor = bottom + ENGINE_ROOM;
        for (int story : STORIES) {
            if (floor + story > noseFoot) {
                break;
            }
            floors.add(floor);
            floorAt(floor);
            floor += story;
        }
        floorAt(noseFoot);
    }

    /** A floor at a row: every open block inside the body, out to its skin. */
    private void floorAt(int y) {
        int out = (int) Math.ceil(radius);
        for (int x = -out; x <= out; x++) {
            for (int z = -out; z <= out; z++) {
                floorBlock(x, y, z);
            }
        }
    }

    private void floorBlock(int x, int y, int z) {
        if (surface.depth(x, y + 0.5, z) < 0 && isOpen(x, y, z)) {
            set(x, y, z, AerolargeRegistry.CABIN_FLOOR.get().defaultBlockState());
        }
    }

    /**
     * Each story's oval windows (see {@link OvalWindowBlock}): one out to each of the four ways, where the skin runs
     * straight up and flat across a window's width, but none in front of the entry story, where the door is.
     */
    private void windows() {
        for (int floor : floors) {
            for (Direction way : Direction.Plane.HORIZONTAL) {
                window(floor, way);
            }
        }
    }

    /** A story's window looking out one way, set into the skin there; none where the skin is not all cover. */
    private void window(int floor, Direction way) {
        if (way == Direction.NORTH && floor == floors.getFirst()) {
            return;
        }
        int pieces = OvalWindowBlock.COLUMNS * OvalWindowBlock.ROWS;
        Map<BlockPos, BlockState> window = new HashMap<>();
        for (int piece = 0; piece < pieces; piece++) {
            int column = piece % OvalWindowBlock.COLUMNS;
            int row = piece / OvalWindowBlock.COLUMNS;
            BlockPos at = windowPiece(floor, way, column, row);
            if (at == null) {
                return;
            }
            window.put(at, AerolargeRegistry.OVAL_WINDOW.get().piece(way, column, row));
        }
        cells.putAll(window);
    }

    /**
     * Where a piece of a story's window looking out one way goes: in the outermost block of the skin there, its column
     * from the left as seen from outside and its row from the top; null where that block is not the cover.
     */
    private BlockPos windowPiece(int floor, Direction way, int column, int row) {
        Direction left = way.getClockWise();
        int across = OvalWindowBlock.COLUMNS / 2 - column;
        int y = floor + WINDOW_FROM + OvalWindowBlock.ROWS - 1 - row;
        BlockPos skin = outermost(way, across * left.getStepX(), y, across * left.getStepZ());
        return skin != null && stateAt(skin.getX(), y, skin.getZ()).is(AerolargeRegistry.ZEPPELIN_COVER.get()) ? skin : null;
    }

    /**
     * The lift shaft up the middle, from the entry story's floor to the cockpit's: walls of glass round three blocks of
     * open shaft, the floors cut away inside them, and a doorway in its front wall at every story and the cockpit.
     */
    private void shaft() {
        if (floors.isEmpty()) {
            return;
        }
        int from = floors.getFirst();
        for (int y = from; y <= noseFoot; y++) {
            for (int x = -SHAFT; x <= SHAFT; x++) {
                shaftRow(x, y);
            }
        }
        List<Integer> landings = new ArrayList<>(floors);
        landings.add(noseFoot);
        for (int floor : landings) {
            doorway(floor);
        }
    }

    private void shaftRow(int x, int y) {
        for (int z = -SHAFT; z <= SHAFT; z++) {
            boolean wall = Math.abs(x) == SHAFT || Math.abs(z) == SHAFT;
            if (wall) {
                set(x, y, z, AerolargeRegistry.HULL_GLASS.get().defaultBlockState());
            } else if (y > floors.getFirst()) {
                cells.remove(new BlockPos(x, y, z));
            }
        }
    }

    /** The doorway out of the shaft onto a floor, in its front wall: a block wide, two tall. */
    private void doorway(int floor) {
        for (int y = floor + 1; y <= floor + 2; y++) {
            cells.remove(new BlockPos(0, y, -SHAFT));
        }
    }

    /** The front door: an opening in the skin over the entry story's floor, at the front. */
    private void door() {
        if (floors.isEmpty()) {
            return;
        }
        int floor = floors.getFirst();
        int front = -(int) Math.ceil(radius) - 1;
        for (int z = front; z < 0; z++) {
            doorColumn(floor, z);
        }
    }

    private void doorColumn(int floor, int z) {
        for (int x = -DOOR_HALF; x <= DOOR_HALF; x++) {
            for (int y = floor + 1; y <= floor + DOOR_ROWS; y++) {
                doorBlock(x, y, z);
            }
        }
    }

    /** A block of the doorway: the skin there taken away, nothing inside the body. */
    private void doorBlock(int x, int y, int z) {
        if (!inside(x, y, z)) {
            cells.remove(new BlockPos(x, y, z));
        }
    }

    /**
     * The engines: bells of creative thrusters set into the bottom, pushing up, their plumes shown, each thruster with
     * a thrust relay over it in the engine room for the autopilot to drive, the middle bell first.
     */
    private void engines() {
        Optional<BlockState> engine = palette.engine();
        if (engine.isEmpty()) {
            return;
        }
        int out = (int) Math.round(BELL_OUT * scale);
        int[][] middles = {{0, 0}, {0, -out}, {out, 0}, {0, out}, {-out, 0}};
        for (int[] middle : middles) {
            List<BlockPos> relays = new ArrayList<>();
            for (int[] at : BELL_THRUSTERS) {
                relays.add(thruster(engine.get(), middle[0] + at[0], bottom, middle[1] + at[1], Direction.UP));
            }
            bells.add(relays);
        }
    }

    /**
     * A creative thruster at a block pushing the way given, at the configured setting with its plume shown, and the
     * relay that drives it beside it, on its far side from its nozzle; where the relay stands.
     */
    private BlockPos thruster(BlockState engine, int x, int y, int z, Direction pushes) {
        set(x, y, z, AirframePalette.with(engine, "facing", pushes.getSerializedName()));
        fittings.put(new BlockPos(x, y, z), Thrusters.thrust(EngineFlames.PROPULSION_PLUME, 1));
        BlockPos relay = new BlockPos(x, y, z).relative(pushes);
        cells.put(relay, AerolargeRegistry.THRUST_RELAY.get().defaultBlockState());
        return relay;
    }

    /**
     * The side jets: a ring of four in the skin near the nose and another near the bottom, one at the front, the back
     * and either side of each, each pushing the rocket across toward the side opposite it, its exhaust leaving out of
     * the skin and its relay inside.
     */
    private void jets() {
        Optional<BlockState> engine = palette.engine();
        if (engine.isEmpty()) {
            return;
        }
        ring(engine.get(), noseFoot - JETS_DOWN, topJets);
        ring(engine.get(), bottom + JETS_UP, bottomJets);
    }

    private void ring(BlockState engine, int y, Map<Direction, BlockPos> jets) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos skin = outermost(side, 0, y, 0);
            if (skin != null) {
                jets.put(side.getOpposite(), thruster(engine, skin.getX(), y, skin.getZ(), side.getOpposite()));
            }
        }
    }

    /**
     * The outermost block of the skin out the way given at a row, from the axis or a block beside it; null for none.
     */
    private BlockPos outermost(Direction side, int fromX, int y, int fromZ) {
        for (int k = (int) Math.ceil(radius) + 2; k > 0; k--) {
            int x = fromX + side.getStepX() * k;
            int z = fromZ + side.getStepZ() * k;
            if (!isOpen(x, y, z)) {
                return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    /**
     * The cockpit on the floor at the nose's foot: the autopilot's control head facing the front, a flight display on
     * either side of it, wired once it stands.
     */
    private void cockpit() {
        int y = noseFoot + 1;
        int front = frontWall(y);
        BlockPos head = new BlockPos(0, y, front + HEAD_BACK);
        set(head.getX(), y, head.getZ(), AerolargeRegistry.AUTOPILOT.get().defaultBlockState().setValue(AutopilotBlock.FACING, Direction.NORTH));
        for (int side = -1; side <= 1; side += 2) {
            set(side * 2, y, front + 1, AerolargeRegistry.FLIGHT_DISPLAY.get().defaultBlockState().setValue(FlightDisplayBlock.FACING, Direction.SOUTH));
        }
        fit(head, (blockEntity, rotation) -> {
            if (blockEntity instanceof AutopilotBlockEntity autopilot) {
                wire(autopilot, head, rotation);
            }
        });
    }

    /** How far forward the inside of the front wall is at a row, in blocks: the first block a skin's thickness in. */
    private int frontWall(int y) {
        int z = -(int) Math.ceil(radius) - 1;
        while (z < 0 && !inside(0, y, z)) {
            z++;
        }
        return z;
    }

    /**
     * Wires the control head once it stands: each engine bell's relays and the side jets' relays, as offsets from it
     * turned as the rocket was placed, the jets by the way each pushes it as seen from the head facing the front; how
     * far its block stands above the fins' feet; and the box round the rocket's blocks.
     */
    private void wire(AutopilotBlockEntity autopilot, BlockPos at, Rotation rotation) {
        List<List<BlockPos>> turned = bells.stream().map(relays -> offsets(at, relays, rotation)).toList();
        autopilot.configureRocket(turned, jets(at, topJets, rotation), jets(at, bottomJets, rotation), at.getY());
        int reach = reach();
        List<BlockPos> frame = offsets(at, List.of(new BlockPos(-reach, 0, -reach), new BlockPos(reach, height, reach)), rotation);
        autopilot.frame(frame.get(0), frame.get(1));
    }

    /** A ring's jets' relays as the pilot takes them, by the way each pushes: ahead, right, back and left. */
    private static RocketPilot.Jets jets(BlockPos at, Map<Direction, BlockPos> jets, Rotation rotation) {
        Function<Direction, BlockPos> offset = way -> jets.containsKey(way) ? jets.get(way).subtract(at).rotate(rotation) : null;
        return new RocketPilot.Jets(offset.apply(Direction.NORTH), offset.apply(Direction.EAST), offset.apply(Direction.SOUTH), offset.apply(Direction.WEST));
    }

    /** Positions in the layout as offsets from a block, turned with the rocket. */
    private static List<BlockPos> offsets(BlockPos from, List<BlockPos> positions, Rotation rotation) {
        return positions.stream().map(pos -> pos.subtract(from).rotate(rotation)).toList();
    }

    /** Counts the body's open air, once everything else stands. */
    private void countAir() {
        int[] count = {0};
        forEachAir((x, y, z) -> count[0]++);
        air = count[0];
    }

    /** A block position, handed over. */
    @FunctionalInterface
    private interface Cell {
        void at(int x, int y, int z);
    }

    /** Every block of air inside the body: its own space, wherever nothing else stands. */
    private void forEachAir(Cell cell) {
        int out = (int) Math.ceil(radius) + 1;
        for (int y = bottom + 1; y < height; y++) {
            for (int x = -out; x <= out; x++) {
                airRow(cell, x, y, out);
            }
        }
    }

    private void airRow(Cell cell, int x, int y, int out) {
        for (int z = -out; z <= out; z++) {
            if (surface.depth(x, y + 0.5, z) < 0 && isOpen(x, y, z)) {
                cell.at(x, y, z);
            }
        }
    }

    /**
     * The body's air first, so it is cleared before anything goes in, then everything else bottom to top. Handed over
     * as every airframe's blocks are, from its front at z 0 back (see {@link Layout}): moved back from its axis by its
     * reach, so it stands just past where it is built from.
     */
    @Override
    public void inOrder(BiConsumer<BlockPos, BlockState> cell) {
        BlockState open = Blocks.AIR.defaultBlockState();
        forEachAir((x, y, z) -> cell.accept(handed(new BlockPos(x, y, z)), open));
        List<Map.Entry<BlockPos, BlockState>> sorted = new ArrayList<>(cells.entrySet());
        sorted.sort((a, b) -> Integer.compare(a.getKey().getY(), b.getKey().getY()));
        sorted.forEach(entry -> cell.accept(handed(entry.getKey()), entry.getValue()));
    }

    /** A position from the axis as it is handed over, from the front at z 0. */
    private BlockPos handed(BlockPos fromAxis) {
        return fromAxis.offset(0, 0, reach());
    }

    @Override
    public int size() {
        return air + cells.size();
    }

    @Override
    public Map<BlockPos, Fitting> fittings() {
        Map<BlockPos, Fitting> handed = new HashMap<>();
        fittings.forEach((at, fitting) -> handed.put(handed(at), fitting));
        return handed;
    }

    /** Too big for the Physics Assembler, it is assembled through Sable instead, and needs no glue. */
    @Override
    public boolean glued() {
        return false;
    }

    @Override
    public String message(String what) {
        return "message.aerolarge.rocket_" + what;
    }

    /** Sets up the block entity at a layout position once it stands, as the fitting says. */
    void fit(BlockPos at, Fitting fitting) {
        fittings.put(at, fitting);
    }
}
