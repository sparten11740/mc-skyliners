package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.ShellBlock;
import dev.skyliners.block.ShellGeometry;
import java.util.List;
import java.util.function.IntPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

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
     * The scale she is always built at, whatever the server's, in blocks a metre: smaller, her hull leaves a player too
     * little headroom over her deck (two rows at scale 1, three at 1.5); larger, her weight outgrows her thrusters.
     */
    static final double SCALE = 2;
    /** How high over the ground under her legs' feet her deck is walked on, in metres. */
    private static final double DECK = 3.0;
    /** How far out from her axis her outer bulkhead stands, in metres: past it her hull is too low for her rooms. */
    static final double BULKHEAD = 9.5;
    /** The core round the turrets' ladder: its walls so many blocks out from her axis, three blocks of well inside. */
    static final int CORE = 2;
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
        layout.countAir();
        return layout;
    }

    /**
     * Her skin: every block her surface passes through, cut to it; then the glass of her cockpit's cone and her
     * turrets' domes set into it (see {@link #glaze}).
     */
    private void skin() {
        BlockState cover = SkylinersRegistry.ZEPPELIN_COVER.get().defaultBlockState();
        BlockState glass = SkylinersRegistry.SMOKED_GLASS.get().defaultBlockState();
        for (int y = 0; y <= top; y++) {
            for (int x = -side; x <= side; x++) {
                skinRow(cover, x, y);
            }
        }
        for (int y = 0; y <= top; y++) {
            for (int x = -side; x <= side; x++) {
                glassRow(glass, x, y);
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

    private void glassRow(BlockState glass, int x, int y) {
        for (int z = front; z <= back; z++) {
            if (!isOpen(x, y, z) && glazed(x, y + 0.5, z)) {
                glaze(x, y, z, glass);
            }
        }
    }

    /**
     * Whether her skin is glass at a point, in blocks: on her cockpit's cone and its front, but for its struts, eight
     * spokes and a ring half way along; and on her turrets' domes.
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
        double spoke = Math.PI / 4;
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
        }
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
