package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.SkylinersClientConfig;
import dev.skyliners.airframe.ZeppelinSilhouette;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The Great Shadow: an assembled zeppelin's shadow on the land under her, as this client sees it, cast by her hull and
 * her four fins (see {@link ZeppelinSilhouette}) from where the sun is. At noon it lies right under her, sharp-edged;
 * as the sun sinks it is thrown further off and drawn out long, its edges softening as they would in the sun, and it
 * fades away near sunrise and sunset, thrown far off by a low sun, in rain, and at night. It lies over the top of the
 * highest block in each column, treetops and water too, darkening it: it is only drawn, the light under her is as it
 * ever was. Where her lit floodlights' light falls on it, it is drawn the lighter the nearer they are to the ground
 * (see {@link Floodlights#pools}).
 *
 * <p>It is worked out afresh every {@link #REFRESH_TICKS} ticks, only over the ground her box could shade: first once for
 * each cell of {@link #CELL} by {@link #CELL} columns, by whether the line from its middle toward the sun passes
 * through her, then column by column only in the cells along its edges, the cells inside it shaded whole and those
 * well clear of it left alone. The shaded columns of a row at one height are drawn as one strip.
 */
public final class GreatShadow {
    /** How dark the shadow is at its darkest, under a high sun: the share of the land's light it takes away. */
    private static final float DARKEST = 0.45F;
    /** How high the sun is, in degrees over the horizon, when the shadow begins to show and when it is at its darkest. */
    private static final double LOWEST_SUN = 8;
    private static final double FULL_SUN = 25;
    /**
     * How far off the sun throws her shadow, from the ground under her to where it falls, at which it has faded away,
     * in blocks: further off it would be faint and long, and costly to work out.
     */
    private static final double FARTHEST_THROW = 220;
    /**
     * How much the sun's breadth softens the shadow's edge, in blocks for each block from the ground to her, and how
     * soft it is at least, in blocks.
     */
    private static final double PENUMBRA = 0.0093;
    private static final double SHARPEST = 1;
    /** How thick her fins are as they cast a shadow edge on, in blocks either side of each. */
    private static final double FIN_HALF = 0.75;
    /** How far above and below the ground under her the land is looked at for the shadow, in blocks. */
    private static final double BAND = 16;
    /** How many shades of the shadow's soft edge are told apart, the darkest included. */
    private static final int SHADES = 6;
    /** How far over the ground the shadow is drawn, in blocks, and how much further for each block off the camera. */
    private static final double LIFT = 0.02;
    private static final double LIFT_FAR = 0.0006;
    /** How many columns across a cell is, worked out first by its middle alone. */
    private static final int CELL = 4;
    /** How often the shadow is worked out afresh, in ticks. */
    private static final int REFRESH_TICKS = 3;

    /** A strip of shadow: a row of columns from one x to another at one z, over ground of one height, in one shade. */
    private record Strip(int fromX, int toX, int z, int ground, int shade) {
    }

    /**
     * Where a zeppelin's hull is in the world and her outline, and the way to the sun in her own frame: her axis at her
     * nose's tip, and which ways are across her to starboard, up through her and aft along her axis, with the way to
     * the sun along each; and the outline, on the land, of where her shadow may fall.
     */
    private record Hull(ZeppelinSilhouette shape, Vec3 tip, Vec3 across, Vec3 up, Vec3 aft, double sunAcross, double sunUp, double sunAft,
                        List<Vec3> outline, List<Side> sides) {
    }

    /**
     * A side of the outline, as the line it runs along seen from above: a point is inside it by so much, in blocks, as
     * {@code across * x + along * z + offset}.
     */
    private record Side(double across, double along, double offset) {
        double inside(double x, double z) {
            return across * x + along * z + offset;
        }

        static Side of(Vec3 from, Vec3 to) {
            double length = Math.hypot(to.x - from.x, to.z - from.z);
            double wayX = length > 0 ? (to.x - from.x) / length : 0;
            double wayZ = length > 0 ? (to.z - from.z) / length : 0;
            return new Side(-wayZ, wayX, wayZ * from.x - wayX * from.z);
        }
    }

    /**
     * The cells of one zeppelin's shadow worked out by their middles: from which column and row, how many across and
     * along, and each one's shade where it and the cells all round it are alike, wholly in the sun (0) or wholly in her
     * shadow; less than nothing along the shadow's edges, where each column's own is to be worked out.
     */
    private record Cells(int fromX, int fromZ, int width, int depth, int[] alike) {
        /** A column's cell's shade, where it and all round it are alike; less than nothing along the edges. */
        int at(int x, int z) {
            int column = (x - fromX) / CELL;
            int row = (z - fromZ) / CELL;
            return column < 0 || row < 0 || column >= width || row >= depth ? 0 : alike[row * width + column];
        }
    }

    private static final List<Strip> STRIPS = new ArrayList<>();
    private static float strength;
    /** Where the lit floodlights' light falls on the land, as it was when the shadow was last worked out. */
    private static List<Floodlights.Pool> pools = List.of();

    private GreatShadow() {
    }

    /** Every {@link #REFRESH_TICKS}: the shadow of each zeppelin near enough worked out afresh, if the sun is up. */
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !SkylinersClientConfig.GREAT_SHADOW.get()) {
            STRIPS.clear();
            return;
        }
        if (level.getGameTime() % REFRESH_TICKS != 0) {
            return;
        }
        STRIPS.clear();
        Vec3 sun = sun(level);
        strength = strength(level, sun);
        pools = strength > 0 ? Floodlights.pools(level, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()) : List.of();
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(level)) {
            Hull hull = strength > 0 && autopilot.craft() == Craft.ZEPPELIN ? hull(level, autopilot, sun) : null;
            if (hull != null) {
                cast(level, hull);
            }
        }
    }

    /** The way to the sun, as the sky draws it: rising in the east, overhead at noon, setting in the west. */
    private static Vec3 sun(ClientLevel level) {
        double angle = level.getSunAngle(1);
        return new Vec3(-Math.sin(angle), Math.cos(angle), 0);
    }

    /** How dark the shadow is, from none to all of {@link #DARKEST}: by how high the sun is, and less in rain. */
    private static float strength(ClientLevel level, Vec3 sun) {
        if (!level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
            return 0;
        }
        double height = Math.toDegrees(Math.asin(Mth.clamp(sun.y, -1, 1)));
        double up = Mth.clamp((height - LOWEST_SUN) / (FULL_SUN - LOWEST_SUN), 0, 1);
        return (float) (up * (1 - level.getRainLevel(1)));
    }

    /**
     * Where an assembled zeppelin's hull is in the world, and the outline on the land her shadow may fall in: her box,
     * nose to stern and as far round her axis as her fins reach, thrown down by the sun onto the ground under her
     * middle and a band above and below it. Null for one not assembled, or one whose shadow is thrown too far off.
     */
    @Nullable
    private static Hull hull(ClientLevel level, AutopilotBlockEntity autopilot, Vec3 sun) {
        SableClient.Frame frame = SableClient.frame(autopilot);
        if (frame == null) {
            return null;
        }
        ZeppelinSilhouette shape = ZeppelinSilhouette.of(frame.size());
        Vec3 plotTip = frame.origin().add(0, shape.axis(), 0);
        Vec3 tip = SableClient.toWorld(level, plotTip);
        Vec3 aft = SableClient.toWorld(level, plotTip.add(Vec3.atLowerCornerOf(frame.nose().getOpposite().getNormal()))).subtract(tip).normalize();
        Vec3 up = SableClient.toWorld(level, plotTip.add(0, 1, 0)).subtract(tip).normalize();
        Vec3 across = aft.cross(up).normalize();
        Vec3 middle = tip.add(aft.scale(shape.length() / 2));
        double ground = groundUnder(level, middle, sun);
        if (middle.distanceTo(onto(middle, sun, ground)) > FARTHEST_THROW) {
            return null;
        }
        List<Vec3> corners = new ArrayList<>();
        for (int corner = 0; corner < 8; corner++) {
            Vec3 at = tip.add(aft.scale(corner % 2 * shape.length())).add(across.scale((corner / 2 % 2 * 2 - 1) * shape.reach()))
                .add(up.scale((corner / 4 * 2 - 1) * shape.reach()));
            corners.add(onto(at, sun, ground - BAND));
            corners.add(onto(at, sun, ground + BAND));
        }
        List<Vec3> outline = outline(corners);
        List<Side> sides = new ArrayList<>();
        for (int side = 0; side < outline.size(); side++) {
            sides.add(Side.of(outline.get(side), outline.get((side + 1) % outline.size())));
        }
        return new Hull(shape, tip, across, up, aft, sun.dot(across), sun.dot(up), sun.dot(aft), outline, sides);
    }

    /** The ground's height under a point as the sun throws it down there, found again where it first falls. */
    private static double groundUnder(ClientLevel level, Vec3 point, Vec3 sun) {
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(point.x), Mth.floor(point.z));
        for (int again = 0; again < 2; again++) {
            Vec3 falls = onto(point, sun, ground);
            ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(falls.x), Mth.floor(falls.z));
        }
        return ground;
    }

    /** Where a point's shadow falls on level ground at a height. */
    private static Vec3 onto(Vec3 point, Vec3 sun, double height) {
        return point.subtract(sun.scale((point.y - height) / sun.y));
    }

    /** The outline round points on the land, seen from above: the smallest shape with no dent that holds them all. */
    private static List<Vec3> outline(List<Vec3> points) {
        List<Vec3> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparingDouble(Vec3::x).thenComparingDouble(Vec3::z));
        List<Vec3> outline = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            int start = outline.size();
            for (Vec3 point : sorted) {
                keepTurning(outline, start, point);
            }
            outline.remove(outline.size() - 1);
            sorted = sorted.reversed();
        }
        return outline;
    }

    /** Adds a point to one side of an outline being drawn round, first dropping the points it would leave a dent at. */
    private static void keepTurning(List<Vec3> outline, int start, Vec3 point) {
        while (outline.size() >= start + 2 && turn(outline.get(outline.size() - 2), outline.get(outline.size() - 1), point) <= 0) {
            outline.remove(outline.size() - 1);
        }
        outline.add(point);
    }

    /** Which way three points turn, seen from above: more than nothing one way, less the other. */
    private static double turn(Vec3 a, Vec3 b, Vec3 c) {
        return (b.x - a.x) * (c.z - a.z) - (b.z - a.z) * (c.x - a.x);
    }

    /** A zeppelin's shadow over the land in its outline within sight of the camera: its cells, then its strips. */
    private static void cast(ClientLevel level, Hull hull) {
        double near = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        int fromX = (int) Math.floor(Math.max(hull.outline().stream().mapToDouble(Vec3::x).min().orElse(0), camera.x - near));
        int toX = (int) Math.ceil(Math.min(hull.outline().stream().mapToDouble(Vec3::x).max().orElse(0), camera.x + near));
        int fromZ = (int) Math.floor(Math.max(hull.outline().stream().mapToDouble(Vec3::z).min().orElse(0), camera.z - near));
        int toZ = (int) Math.ceil(Math.min(hull.outline().stream().mapToDouble(Vec3::z).max().orElse(0), camera.z + near));
        if (toX < fromX || toZ < fromZ) {
            return;
        }
        Cells cells = cells(level, hull, fromX, fromZ, (toX - fromX) / CELL + 1, (toZ - fromZ) / CELL + 1);
        for (int z = fromZ; z <= toZ; z++) {
            row(level, hull, cells, z, fromX, toX);
        }
    }

    /**
     * The shade of each cell's middle, where it lies in the outline, none where it lies outside; then each cell's as
     * it is alike with all round it.
     */
    private static Cells cells(ClientLevel level, Hull hull, int fromX, int fromZ, int width, int depth) {
        int[] shades = new int[width * depth];
        for (int cell = 0; cell < shades.length; cell++) {
            double x = fromX + cell % width * CELL + CELL / 2.0;
            double z = fromZ + cell / width * CELL + CELL / 2.0;
            shades[cell] = inside(hull.sides(), x, z, CELL) ? shadeAt(level, hull, Mth.floor(x), Mth.floor(z)) : 0;
        }
        int[] alike = new int[shades.length];
        for (int cell = 0; cell < shades.length; cell++) {
            alike[cell] = alike(shades, width, depth, cell % width, cell / width);
        }
        return new Cells(fromX, fromZ, width, depth, alike);
    }

    /** A cell's shade where it and the cells all round it are alike, wholly in the sun or in her shadow; else -1. */
    private static int alike(int[] shades, int width, int depth, int column, int row) {
        int own = shades[row * width + column];
        if (own > 0 && own < SHADES) {
            return -1;
        }
        for (int near = 0; near < 9; near++) {
            int x = column + near % 3 - 1;
            int z = row + near / 3 - 1;
            int there = x < 0 || z < 0 || x >= width || z >= depth ? 0 : shades[z * width + x];
            if (there != own) {
                return -1;
            }
        }
        return own;
    }

    /** Whether a point on the land lies within so far of an outline's sides, seen from above. */
    private static boolean inside(List<Side> sides, double x, double z, double margin) {
        for (Side side : sides) {
            if (side.inside(x, z) < -margin) {
                return false;
            }
        }
        return true;
    }

    /**
     * One row of the shadow: each column shaded as its cell is where the cell and all round it are alike, or worked out
     * for itself along the shadow's edges; those side by side over ground of one height in one shade drawn as a strip.
     */
    private static void row(ClientLevel level, Hull hull, Cells cells, int z, int fromX, int toX) {
        Ground ground = new Ground(level, z);
        int openFrom = Integer.MIN_VALUE;
        int openGround = 0;
        int openShade = 0;
        for (int x = fromX; x <= toX; x++) {
            int own = cells.at(x, z);
            int height = own == 0 ? Integer.MIN_VALUE : ground.at(x);
            int cast = height == Integer.MIN_VALUE ? 0 : own > 0 ? own : shade(hull, x + 0.5, height, z + 0.5);
            int shade = cast > 0 ? lit(cast, x + 0.5, z + 0.5) : 0;
            if (openFrom != Integer.MIN_VALUE && (shade != openShade || height != openGround)) {
                STRIPS.add(new Strip(openFrom, x - 1, z, openGround, openShade));
                openFrom = Integer.MIN_VALUE;
            }
            if (shade > 0 && openFrom == Integer.MIN_VALUE) {
                openFrom = x;
                openGround = height;
                openShade = shade;
            }
        }
        if (openFrom != Integer.MIN_VALUE) {
            STRIPS.add(new Strip(openFrom, toX, z, openGround, openShade));
        }
    }

    /** A column's shade, lifted by the floodlights' light where it falls there (see {@link Floodlights#pools}). */
    private static int lit(int shade, double x, double z) {
        double lift = 0;
        for (Floodlights.Pool pool : pools) {
            lift = Math.max(lift, pool.liftAt(x, z));
        }
        return (int) Math.round(shade * (1 - lift));
    }

    /**
     * The tops of the ground along a row, read from each chunk's own heights, the chunk looked up once for every
     * sixteen columns rather than for each.
     */
    private static final class Ground {
        private final ClientLevel level;
        private final int z;
        private int chunkX = Integer.MIN_VALUE;
        @Nullable
        private LevelChunk chunk;

        Ground(ClientLevel level, int z) {
            this.level = level;
            this.z = z;
        }

        /** The height the top of the ground is at in a column, over its highest block; none where it is not loaded. */
        int at(int x) {
            if (x >> 4 != chunkX) {
                chunkX = x >> 4;
                chunk = level.getChunkSource().getChunk(chunkX, z >> 4, false);
            }
            return chunk == null ? Integer.MIN_VALUE : chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
        }
    }

    /** A column's shade from the line from the top of its ground toward the sun. */
    private static int shadeAt(ClientLevel level, Hull hull, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return 0;
        }
        return shade(hull, x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
    }

    /**
     * How deep in her shadow a point on the ground is, from 0 (in the sun) to {@link #SHADES}: whether the line from
     * it toward the sun passes through her hull or crosses a fin, soft at the edges by how far the shadow falls.
     */
    private static int shade(Hull hull, double x, double y, double z) {
        double fromX = x - hull.tip().x;
        double fromY = y - hull.tip().y;
        double fromZ = z - hull.tip().z;
        double across = fromX * hull.across().x + fromY * hull.across().y + fromZ * hull.across().z;
        double up = fromX * hull.up().x + fromY * hull.up().y + fromZ * hull.up().z;
        double aft = fromX * hull.aft().x + fromY * hull.aft().y + fromZ * hull.aft().z;
        double cover = Math.max(hullCover(hull, across, up, aft), Math.max(finCover(hull, across, up, aft, true), finCover(hull, across, up, aft, false)));
        return (int) Math.round(cover * SHADES);
    }

    /** How far inside her hull the line toward the sun passes where it comes nearest her axis, as a share of the edge. */
    private static double hullCover(Hull hull, double across, double up, double aft) {
        double along = nearestAxis(hull, across, up);
        if (along <= 0) {
            return 0;
        }
        double inside = hull.shape().hull(aft + along * hull.sunAft()) - Math.hypot(across + along * hull.sunAcross(), up + along * hull.sunUp());
        return edge(inside, along);
    }

    /**
     * How far inside her upright fins (up and down) or her level ones (out to either side) the line toward the sun
     * passes, as a share of the edge: where it crosses their plane or, running along it, where it comes nearest her
     * axis.
     */
    private static double finCover(Hull hull, double across, double up, double aft, boolean upright) {
        double off = upright ? across : up;
        double step = upright ? hull.sunAcross() : hull.sunUp();
        double along = Math.abs(step) > 1e-3 ? -off / step : nearestAxis(hull, across, up);
        if (along <= 0) {
            return 0;
        }
        double aside = Math.abs(off + along * step);
        double outward = Math.abs(upright ? up + along * hull.sunUp() : across + along * hull.sunAcross());
        return aside > FIN_HALF ? 0 : edge(hull.shape().fin(aft + along * hull.sunAft()) - outward, along);
    }

    /** How far along the line toward the sun it comes nearest her axis, in blocks; 0 for a line along the axis. */
    private static double nearestAxis(Hull hull, double across, double up) {
        double square = hull.sunAcross() * hull.sunAcross() + hull.sunUp() * hull.sunUp();
        return square < 1e-6 ? 0 : -(across * hull.sunAcross() + up * hull.sunUp()) / square;
    }

    /** How far inside an edge a point of the shadow is, as a share of the edge's softness that far from her. */
    private static double edge(double inside, double distance) {
        return Mth.clamp(inside / Math.max(SHARPEST, PENUMBRA * distance) + 0.5, 0, 1);
    }

    /** After the translucent blocks are drawn: every strip of shadow, as a dark film over the ground's top. */
    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || strength <= 0 || STRIPS.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(FlatQuads.WHITE);
        VertexConsumer consumer = buffers.getBuffer(type);
        PoseStack.Pose pose = event.getPoseStack().last();
        for (Strip strip : STRIPS) {
            draw(consumer, pose, camera, strip);
        }
        buffers.endBatch(type);
    }

    /** One strip, lifted off the ground a little more the further off it is, so the ground never shows through it. */
    private static void draw(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, Strip strip) {
        double distance = Math.hypot((strip.fromX() + strip.toX()) / 2.0 - camera.x, strip.z() - camera.z);
        float y = (float) (strip.ground() + LIFT + LIFT_FAR * distance - camera.y);
        float x0 = (float) (strip.fromX() - camera.x);
        float x1 = (float) (strip.toX() + 1 - camera.x);
        float z0 = (float) (strip.z() - camera.z);
        float z1 = (float) (strip.z() + 1 - camera.z);
        int colour = (int) (DARKEST * strength * strip.shade() / SHADES * 0xFF) << 24;
        corner(consumer, pose, x0, y, z0, colour);
        corner(consumer, pose, x0, y, z1, colour);
        corner(consumer, pose, x1, y, z1, colour);
        corner(consumer, pose, x1, y, z0, colour);
    }

    private static void corner(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int colour) {
        consumer.addVertex(pose, x, y, z).setColor(colour).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0, 1, 0);
    }
}
