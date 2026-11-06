package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.RocketFlight;
import dev.skyliners.autopilot.RocketPilot;
import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.block.AutopilotScreenBlock;
import dev.skyliners.block.AutopilotScreenBlockEntity;
import dev.skyliners.block.FlightDisplayBlock;
import dev.skyliners.block.InstrumentPanelBlock;
import dev.skyliners.block.OvalWindowBlock;
import dev.skyliners.block.RocketFinish;
import dev.skyliners.block.RocketLiningPanelBlock;
import dev.skyliners.block.ShellBlock;
import dev.skyliners.block.ShellGeometry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * ways; the cockpit sits in smoked glass round the nose's lower part, its flight deck planned to the room it has (see
 * {@link RocketFlightDeck}), its control head facing the front. The features laid into it once it stands are pieces of
 * their own (see {@link RocketPiece}).
 *
 * <p>Sable flies the rocket as one body only as far as its blocks touch, face to face: the floors run out to the skin,
 * the fins are two blocks thick where they run between the four ways, and the shaft stands on the floors.
 */
public final class RocketLayout extends HullLayout {
    /**
     * How many rows of open air the engines need under them, at least: Create Propulsion looks ten blocks behind a
     * thruster's nozzle for what stands in its exhaust, and the rocket's own fins and the ground count.
     */
    private static final int EXHAUST_CLEAR = 11;
    /** How many rows the engine room over the bottom takes, up to the first story's floor. */
    private static final int ENGINE_ROOM = 5;
    /**
     * The stories' heights, floor to floor, from the entry story up, in rows: a player's size at every scale. At the
     * default scale all nine fit, one for each of her rooms (entry, cargo bay, galley and mess, bunk room, greenhouse,
     * swimming pool, techno club, lab, observation deck); a smaller rocket has the lower of them.
     */
    private static final int[] STORIES = {9, 10, 9, 9, 12, 12, 11, 12, 14};
    /** How far out from the axis the lift shaft's glass walls stand, in blocks: three blocks of shaft inside them. */
    static final int SHAFT = 2;
    /** How wide and tall the front door is, in blocks, over the entry story's floor. */
    static final int DOOR_HALF = 1;
    static final int DOOR_ROWS = 3;
    /** How many rows over each story's floor its windows' lowest row is: a standing player's eyes are in the middle. */
    private static final int WINDOW_FROM = 1;
    /** How many rows of the nose's skin over the cockpit's floor are glass, its windows all round. */
    private static final int COCKPIT_GLASS = 7;
    /**
     * How far in from her surface the ring of ceiling over the cockpit's glass reaches, in blocks: past the first few
     * steps of the nose's lining, which it hides from under it.
     */
    private static final double SOFFIT_REACH = 3;
    /**
     * The engine bells: thrusters in a plus round each bell's middle, the middle bell under the shaft and one out to
     * each of the four ways, so many metres out from the axis.
     */
    private static final double BELL_OUT = 2.75;
    private static final int[][] BELL_THRUSTERS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** How far up from the bottom and down from the nose's foot the rings of side jets are, in rows. */
    private static final int JETS_UP = 3;
    private static final int JETS_DOWN = 4;
    /** The fins: one between each two of the four ways. */
    private static final int[][] FINS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    /**
     * The features laid once the body stands, in this order (see {@link RocketPiece}): the pool before the greenhouse
     * under it, whose trees and vines keep clear of the pool's channel hanging into it, the LED strips after the rest,
     * to fill in round everything else, and the window dimmers after them, over their panels by the lift doors.
     */
    private static final List<Function<RocketLayout, RocketPiece>> PIECES = List.of(
        RocketLadder::new,
        RocketAirstairs::new,
        RocketCameraDisplay::new,
        RocketLiverySelector::new,
        RocketLift::new,
        RocketPilotSeats::new,
        RocketCockpitControls::new,
        RocketSelfDestruct::new,
        RocketDome::new,
        RocketCockpitLights::new,
        RocketEntry::new,
        RocketMess::new,
        RocketBunks::new,
        RocketPool::new,
        RocketGreenhouse::new,
        RocketClub::new,
        RocketObservationDeck::new,
        RocketRoverBay::new,
        RocketCargoHold::new,
        RocketLab::new,
        RocketGirders::new,
        RocketGirderDecor::new,
        RocketWallLights::new,
        RocketPosters::new,
        RocketShaftDecor::new,
        RocketLedStrips::new,
        RocketWindowDimmers::new,
        RocketPaintings::new,
        RocketDomeLimit::new);
    /** The livery she is built in, one of her own (see {@link Livery#swatches}). */
    private static final Livery BUILT_IN = Livery.MIDNIGHT;

    private final AirframePalette.Balloon palette;
    private final double scale;
    /** Whether her stories are furnished as her rooms, as the server config says as she is laid out. */
    private final boolean rooms;
    /** How tall the rocket stands, in rows, and its body's radius, in blocks. */
    private final int height;
    private final double radius;
    /** The rows of the body's flat bottom, of the nose's foot and of the cockpit's floor. */
    private final int bottom;
    private final int noseFoot;
    /** How high the bottom stands, in metres, as the surface is measured. */
    private final double base;
    /** Each story's floor row, from the entry story up. */
    private final List<Integer> floors = new ArrayList<>();
    /** Each engine bell's thrusters' relays, the middle bell first. */
    private final List<List<BlockPos>> bells = new ArrayList<>();
    /** The side jets' relays near the nose and near the bottom, by the way each pushes it. */
    private final Map<Direction, BlockPos> topJets = new HashMap<>();
    private final Map<Direction, BlockPos> bottomJets = new HashMap<>();
    /** Where the autopilot's control head stands in the cockpit; null before it is laid. */
    @Nullable
    private BlockPos head;
    /** Where everything on the cockpit's flight deck stands; null before the cockpit is laid. */
    @Nullable
    private RocketFlightDeck flightDeck;
    /** How her nose is finished inside over the ring of ceiling on the cockpit's glass: padded, or as the dome. */
    private RocketFinish nose = RocketFinish.COCKPIT;

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
        this.rooms = SkylinersConfig.ROCKET_ROOMS.get();
        this.height = heightRows(scale);
        this.radius = RocketShape.RADIUS * scale;
        this.bottom = bottomRow(scale);
        this.base = bottom / scale;
        this.noseFoot = noseFootRow(scale);
    }

    /** How tall a rocket built at a scale stands, in rows. */
    public static int heightRows(double scale) {
        return (int) Math.round(RocketShape.HEIGHT * scale);
    }

    /** The row of a rocket's flat bottom at a scale. */
    private static int bottomRow(double scale) {
        return Math.max((int) Math.round(RocketShape.BASE * scale), EXHAUST_CLEAR);
    }

    /** The row of a rocket's nose's foot at a scale, the cockpit's floor. */
    private static int noseFootRow(double scale) {
        return (int) Math.round(RocketShape.NOSE_BASE * scale);
    }

    /**
     * The floors' rows of a rocket built at a scale, from the entry story up as many as fit under the cockpit, and
     * last the cockpit's floor.
     */
    public static int[] storyRows(double scale) {
        int noseFoot = noseFootRow(scale);
        List<Integer> rows = new ArrayList<>();
        int floor = bottomRow(scale) + ENGINE_ROOM;
        for (int story : STORIES) {
            if (floor + story > noseFoot) {
                break;
            }
            rows.add(floor);
            floor += story;
        }
        rows.add(noseFoot);
        return rows.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The body's radius of a rocket built at a scale, in blocks. */
    public static double radiusAt(double scale) {
        return RocketShape.RADIUS * scale;
    }

    /** How far out from her axis the box round a rocket built at a scale reaches each way, in blocks. */
    public static int reachAt(double scale) {
        return (int) Math.ceil(RocketShape.FIN_TIP * scale) + 1;
    }

    /**
     * The columns a rocket built at a scale stands on, as x and z from her axis: her fins' feet, on the row they meet
     * the ground, for a launch pad to paint its plates round.
     */
    public static List<int[]> footColumns(double scale) {
        double height = 0.5 / scale;
        double out = RocketShape.finReach(height) * scale;
        double in = RocketShape.finInner(height, bottomRow(scale) / scale) * scale;
        List<int[]> feet = new ArrayList<>();
        for (int[] fin : FINS) {
            addFoot(feet, fin, in, out);
        }
        return feet;
    }

    /** One fin's columns on its foot's row, cut as {@link #finRow} cuts them. */
    private static void addFoot(List<int[]> feet, int[] fin, double in, double out) {
        for (int i = 0; i <= out; i++) {
            addFootRow(feet, fin, i, in, out);
        }
    }

    private static void addFootRow(List<int[]> feet, int[] fin, int i, double in, double out) {
        for (int j = Math.max(0, i - 1); j <= i + 1; j++) {
            double across = Math.hypot(i, j);
            if (across <= out && across >= in) {
                feet.add(new int[]{fin[0] * i, fin[1] * j});
            }
        }
    }

    /** The finished rocket at the configured scale: its fins' feet at y 0, its axis through x 0, z 0. */
    public static RocketLayout create() {
        RocketLayout layout = new RocketLayout(AirframePalette.balloon(), SkylinersConfig.SCALE.get());
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
        if (SkylinersConfig.ROCKET_FINISHES.get()) {
            layout.finishInside();
        }
        layout.countAir();
        return layout;
    }

    /**
     * Her rooms finished inside, once everything else stands (see {@link RocketFinish}): every block of her cover a
     * room sees lined in that room's finish, and every block of plain floor left on a story's floor row finished as
     * that story's floor over the ceiling of the story under it. Laid last, so it lines what the rooms leave of her
     * cover and keeps what they put on her floors, the club's dance floor and the pool's channel among them.
     */
    private void finishInside() {
        Map<BlockPos, BlockState> finished = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            BlockState done = finished(cell.getKey(), cell.getValue());
            if (done != null) {
                finished.put(cell.getKey(), done);
            }
        }
        cells.putAll(finished);
    }

    /**
     * A block of her cover, floor or windows as it is finished inside: the cover lined, the floor finished, the wall
     * round a window's oval the room's; null for any other block, or cover no room sees. Her cover on the row of the
     * ring of ceiling over her cockpit is seen from the cockpit under it, so it is lined as the cockpit is, not as her
     * dome over it, whose sky hides it from above.
     */
    @Nullable
    private BlockState finished(BlockPos at, BlockState state) {
        if (state.is(SkylinersRegistry.CABIN_FLOOR.get())) {
            return withinSurface(at) ? finishedFloor(at.getY()) : null;
        }
        RocketFinish finish = at.getY() == domeFloor() ? RocketFinish.COCKPIT : finishAt(at.getY());
        if (finish != null && state.getBlock() instanceof OvalWindowBlock) {
            return state.setValue(OvalWindowBlock.WALL, OvalWindowBlock.Wall.of(finish));
        }
        if (!state.is(SkylinersRegistry.ZEPPELIN_COVER.get()) || finish == null) {
            return null;
        }
        int shape = ShellBlock.shape(state);
        if (shape != ShellGeometry.WHOLE) {
            return seenFromInside(at, null) ? SkylinersRegistry.ROCKET_LINED_COVER.get().lined(shape, finish) : null;
        }
        BlockState panel = SkylinersRegistry.ROCKET_LINING_PANEL.get().defaultBlockState().setValue(RocketFinish.PROPERTY, finish);
        boolean seen = false;
        for (Direction side : Direction.values()) {
            if (seenFromInside(at, side)) {
                panel = RocketLiningPanelBlock.linedOn(panel, side);
                seen = true;
            }
        }
        return seen ? panel : null;
    }

    /**
     * A block of plain floor finished: on a story's floor row, as that story's floor over the ceiling of the story
     * under it, and on the cockpit's, as the cockpit's over her highest story's, however many stories she has; within
     * a story, as a mezzanine or a lower ceiling of its own is, its room's on both sides; null under her entry story.
     */
    @Nullable
    private BlockState finishedFloor(int y) {
        int story = floors.indexOf(y);
        if (y == noseFoot) {
            return SkylinersRegistry.ROCKET_FLOOR.get().between(RocketFinish.COCKPIT, RocketFinish.ofStory(floors.size() - 1));
        }
        if (story >= 0) {
            return SkylinersRegistry.ROCKET_FLOOR.get().between(RocketFinish.ofStory(story), RocketFinish.ofStory(Math.max(0, story - 1)));
        }
        RocketFinish within = finishAt(y);
        RocketFinish under = finishAt(y - 1);
        return within == null || under == null ? null : SkylinersRegistry.ROCKET_FLOOR.get().between(within, under);
    }

    /**
     * The finish of the room a row of her cover is part of: its story's, her cockpit's from its floor up; none under
     * her entry story.
     */
    @Nullable
    private RocketFinish finishAt(int y) {
        if (floors.isEmpty() || y < floors.getFirst()) {
            return null;
        }
        if (y >= noseFoot) {
            return y >= domeFloor() ? nose : RocketFinish.COCKPIT;
        }
        int story = 0;
        while (story + 1 < floors.size() && floors.get(story + 1) <= y) {
            story++;
        }
        return RocketFinish.ofStory(story);
    }

    /** Whether her stories are furnished as her rooms. */
    boolean rooms() {
        return rooms;
    }

    /** The highest row of a story, its index from the entry story up: under the next floor, or the cockpit's. */
    int storyTop(int story) {
        return (story + 1 < floors.size() ? floors.get(story + 1) : noseFoot) - 1;
    }

    /** How far the body's inside reaches out from its axis, in blocks: its radius. */
    double radius() {
        return radius;
    }

    /** How far out the body reaches at most, fins and all, in blocks. */
    int reach() {
        return reachAt(scale);
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

    /** The row of the cockpit's floor, at the nose's foot. */
    int cockpitFloor() {
        return noseFoot;
    }

    /** The row of the ring of ceiling on the cockpit's glass: the foot of the dome in her nose over it. */
    int domeFloor() {
        return noseFoot + COCKPIT_GLASS + 1;
    }

    /** The highest row of her body, the tip of her nose. */
    int height() {
        return height;
    }

    /** Has her nose finished inside over the ring of ceiling on the cockpit's glass as its dome is, not padded. */
    void finishNose(RocketFinish finish) {
        nose = finish;
    }

    /** Where the autopilot's control head stands in the cockpit; null where the cockpit has none. */
    @Nullable
    BlockPos autopilot() {
        return head;
    }

    /** Where everything on the cockpit's flight deck stands, its seats and wing consoles among them. */
    RocketFlightDeck flightDeck() {
        return flightDeck;
    }

    /**
     * The skin: every block the body's surface passes through, cut to it, over the flat bottom; glass all round the
     * cockpit in the nose, one pane thick (each story's windows are set in once its floors stand).
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
            if (shape != ShellGeometry.NONE && !behindGlass(x, y, z, shape)) {
                set(x, y, z, ShellBlock.withShape(skinAt(y), shape));
            }
        }
    }

    /** What the skin is made of at a row: smoked glass round the cockpit, cut to the nose's curve, the cover elsewhere. */
    private BlockState skinAt(int y) {
        return cockpitGlass(y) ? SkylinersRegistry.SMOKED_GLASS.get().defaultBlockState() : SkylinersRegistry.ZEPPELIN_COVER.get().defaultBlockState();
    }

    private boolean cockpitGlass(int y) {
        return y > noseFoot && y <= noseFoot + COCKPIT_GLASS;
    }

    /**
     * Whether a whole block of the cockpit's glass stands behind others of her skin, touching the sky on no side, as
     * where the nose curves in: left out, so the glass is one pane thick there and shows no darker doubled pane. Each
     * block of her skin beside it keeps its whole side toward it, as every corner it shares with a whole block is
     * kept, so her skin stays closed.
     */
    private boolean behindGlass(int x, int y, int z, int shape) {
        return shape == ShellGeometry.WHOLE && cockpitGlass(y) && !touchesSky(x, y, z);
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
            set(x, y, z, SkylinersRegistry.ZEPPELIN_FABRIC.get().defaultBlockState());
        }
    }

    /** The stories' floors, from the entry story up as many as fit under the cockpit, each out to the skin. */
    private void stories() {
        int[] rows = storyRows(scale);
        for (int story = 0; story < rows.length - 1; story++) {
            floors.add(rows[story]);
            floorAt(rows[story]);
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
            set(x, y, z, SkylinersRegistry.CABIN_FLOOR.get().defaultBlockState());
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

    /**
     * A story's window looking out one way, set into the skin there; none where the skin is not all cover, nor where
     * the rover bay's door takes its place (see {@link RocketRoverBay}).
     */
    private void window(int floor, Direction way) {
        if (way == Direction.NORTH && floor == floors.getFirst() || RocketRoverBay.takesWindow(this, floor, way)) {
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
            window.put(at, SkylinersRegistry.OVAL_WINDOW.get().piece(way, column, row));
        }
        cells.putAll(window);
    }

    /**
     * Where a piece of a story's window looking out one way goes: in the outermost block of the skin there, its column
     * from the left as seen from outside and its row from the top; null where that block is not the cover.
     */
    BlockPos windowPiece(int floor, Direction way, int column, int row) {
        Direction left = way.getClockWise();
        int across = OvalWindowBlock.COLUMNS / 2 - column;
        int y = floor + WINDOW_FROM + OvalWindowBlock.ROWS - 1 - row;
        BlockPos skin = outermost(way, across * left.getStepX(), y, across * left.getStepZ());
        return skin != null && stateAt(skin.getX(), y, skin.getZ()).is(SkylinersRegistry.ZEPPELIN_COVER.get()) ? skin : null;
    }

    /**
     * The lift shaft up the middle, from the entry story's floor to the cockpit's: walls of dark lift glass round three
     * blocks of open shaft, a steel girder up each of its four corners, the floors cut away inside them, and a doorway
     * in its front wall at every story and the cockpit.
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
                set(x, y, z, shaftWall(x, z));
            } else if (y > floors.getFirst()) {
                cells.remove(new BlockPos(x, y, z));
            }
        }
    }

    /** A block of the lift shaft's wall: a steel girder standing up at its corners, lift glass between them. */
    static BlockState shaftWall(int x, int z) {
        boolean corner = Math.abs(x) == SHAFT && Math.abs(z) == SHAFT;
        return corner ? SkylinersRegistry.STEEL_GIRDER.get().defaultBlockState() : SkylinersRegistry.LIFT_GLASS.get().defaultBlockState();
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
                relays.add(thruster(engine.get(), middle[0] + at[0], bottom, middle[1] + at[1], Direction.UP, EngineFlames.PROPULSION_PLUME));
            }
            bells.add(relays);
        }
    }

    /**
     * The side jets: a ring of four in the skin near the nose and another near the bottom, one at the front, the back
     * and either side of each, each pushing the rocket across toward the side opposite it, its exhaust leaving out of
     * the skin and its relay inside. They show no plume: firing in short bursts all the way up, they are heard puffing
     * instead (see {@link dev.skyliners.autopilot.RocketSounds}).
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
                jets.put(side.getOpposite(), thruster(engine, skin.getX(), y, skin.getZ(), side.getOpposite(), EngineFlames.NONE));
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
     * The cockpit on the floor at the nose's foot, its flight deck planned to the room left under the ring of ceiling
     * (see {@link RocketFlightDeck}): the panel's blank casing and the glareshield ahead of it, a flight display in
     * the panel dead ahead of each pilot's seat, the autopilot screen between them, and the autopilot's control head
     * facing the front, wired once it stands. Her pieces lay the seats and the wing consoles where the plan says.
     */
    private void cockpit() {
        soffit();
        int front = -(int) Math.ceil(radius) - 1;
        flightDeck = RocketFlightDeck.plan(noseFoot, front, this::isOpen, (x, y, z) -> inFrom(x, y, z, 0), this::inside);
        BlockState casing = SkylinersRegistry.INSTRUMENT_PANEL.get().defaultBlockState().setValue(InstrumentPanelBlock.FACING, Direction.SOUTH)
            .setValue(InstrumentPanelBlock.BLANK, true);
        flightDeck.casing().forEach(at -> cells.put(at, casing));
        BlockState display = SkylinersRegistry.FLIGHT_DISPLAY.get().defaultBlockState().setValue(FlightDisplayBlock.FACING, Direction.SOUTH);
        flightDeck.flightDisplays().forEach(at -> cells.put(at, display));
        head = flightDeck.head();
        if (head == null) {
            return;
        }
        set(head.getX(), head.getY(), head.getZ(), SkylinersRegistry.AUTOPILOT.get().defaultBlockState().setValue(AutopilotBlock.FACING, Direction.NORTH));
        fit(head, (blockEntity, rotation) -> {
            if (blockEntity instanceof AutopilotBlockEntity autopilot) {
                wire(autopilot, head, rotation);
            }
        });
        if (flightDeck.screen() != null) {
            autopilotScreen(flightDeck.screen());
        }
    }

    /**
     * The autopilot screen between the flight displays, facing the pilots, its first part where the plan says: linked
     * to the control head once it stands, as the airliner's is.
     */
    private void autopilotScreen(BlockPos origin) {
        BlockState screen = SkylinersRegistry.AUTOPILOT_SCREEN.get().defaultBlockState().setValue(AutopilotScreenBlock.FACING, Direction.SOUTH);
        for (int part = 0; part < AutopilotScreenBlock.COLUMNS * AutopilotScreenBlock.ROWS; part++) {
            // Facing south, the viewer's right is east.
            cells.put(origin.offset(part % AutopilotScreenBlock.COLUMNS, part / AutopilotScreenBlock.COLUMNS, 0),
                screen.setValue(AutopilotScreenBlock.PART, part));
        }
        BlockPos linked = head.subtract(origin);
        fit(origin, (blockEntity, rotation) -> {
            if (blockEntity instanceof AutopilotScreenBlockEntity shown) {
                shown.configure(linked.rotate(rotation));
            }
        });
    }

    /**
     * A ring of ceiling round the top of the cockpit's glass, flush with it, from her skin a few blocks in: the glass
     * meets her hull in one level line, inside and out, rather than under the steps of the nose's lining, and the nose
     * stays open over the middle of the cockpit. Laid as floor, it is finished as the cockpit's ceiling.
     */
    private void soffit() {
        int y = domeFloor();
        int out = (int) Math.ceil(radius);
        int side = 2 * out + 1;
        for (int i = 0; i < side * side; i++) {
            int x = i % side - out;
            int z = i / side - out;
            if (isOpen(x, y, z) && inFrom(x, y, z, 0) && !inFrom(x, y, z, SOFFIT_REACH)) {
                set(x, y, z, SkylinersRegistry.CABIN_FLOOR.get().defaultBlockState());
            }
        }
    }

    /**
     * Wires the control head once it stands: each engine bell's relays and the side jets' relays, as offsets from it
     * turned as the rocket was placed, the jets by the way each pushes it as seen from the head facing the front; how
     * far its block stands above the fins' feet; her stories' floors and whether they are furnished; the livery she is
     * built in; and the box round the rocket's blocks.
     */
    private void wire(AutopilotBlockEntity autopilot, BlockPos at, Rotation rotation) {
        List<List<BlockPos>> turned = bells.stream().map(relays -> offsets(at, relays, rotation)).toList();
        RocketFlight rocket = autopilot.wire(RocketFlight.class, at.getY());
        rocket.configure(turned, jets(at, topJets, rotation), jets(at, bottomJets, rotation), storyRows(scale), rooms);
        autopilot.paint(BUILT_IN);
        int reach = reach();
        List<BlockPos> frame = offsets(at, List.of(new BlockPos(-reach, 0, -reach), new BlockPos(reach, height, reach)), rotation);
        autopilot.frame(frame.get(0), frame.get(1));
    }

    /** A ring's jets' relays as the pilot takes them, by the way each pushes: ahead, right, back and left. */
    private static RocketPilot.Jets jets(BlockPos at, Map<Direction, BlockPos> jets, Rotation rotation) {
        Function<Direction, BlockPos> offset = way -> jets.containsKey(way) ? jets.get(way).subtract(at).rotate(rotation) : null;
        return new RocketPilot.Jets(offset.apply(Direction.NORTH), offset.apply(Direction.EAST), offset.apply(Direction.SOUTH), offset.apply(Direction.WEST));
    }

    @Override
    SurfaceCut.Surface surface() {
        return surface;
    }

    /** Her body's air: over her flat bottom, under her nose's tip, out to a block past her skin. */
    @Override
    Box airBox() {
        int out = (int) Math.ceil(radius) + 1;
        return new Box(-out, bottom + 1, -out, out, height - 1, out);
    }

    /** Moved back from her axis by her reach, so she stands just past where she is built from. */
    @Override
    int handedBack() {
        return reach();
    }

    @Override
    public String message(String what) {
        return "message.skyliners.rocket_" + what;
    }
}
