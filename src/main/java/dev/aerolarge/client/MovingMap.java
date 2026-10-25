package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The flight deck's moving map, as the autopilot screen and the map panel draw it (see {@link AutopilotScreenRenderer}
 * and {@link CockpitDisplayRenderer}): the way the airliner is heading always up, the track it has flown, the circle
 * it is flying round or the heading it is turning to, a ring at half the map's reach, north marked, and the airliner
 * in the middle of the lower part.
 */
final class MovingMap {
    /** Where the airliner sits on the map: across its middle, this far up from its bottom as a share of its height. */
    private static final double PLANE_UP = 0.3;
    /** Segments a circle is drawn with. */
    private static final int CIRCLE_SEGMENTS = 64;
    /** The space between the map's layers, each lifted off the one below. */
    private static final float LAYER = 0.002F;
    private static final int RING = 0xFF2A3A55;

    private MovingMap() {
    }

    /**
     * Draws the map into its box on a screen, everything turned so the airliner's heading is up and cut to the box:
     * the track flown, the way the autopilot is taking the airliner, a ring at half its reach, north marked, and the
     * airliner itself. Its width is the range, in world blocks; z is how far off the screen it is drawn, each layer of
     * it a little further.
     */
    static void draw(VertexConsumer fill, Matrix4f matrix, Box map, Iterable<double[]> track, int range, @Nullable AutopilotBlockEntity autopilot,
                     Vec3 here, double heading, float z) {
        double scale = (map.x1() - map.x0()) / range;
        double cx = (map.x0() + map.x1()) / 2;
        double cy = map.y0() + PLANE_UP * (map.y1() - map.y0());
        MapView view = new MapView(here.x, here.z, Math.toRadians(heading), scale, cx, cy);

        // A ring at half the map's reach, and north marked on a ring further out.
        circle(fill, matrix, map, cx, cy, (map.x1() - map.x0()) / 4, 0.006, z, RING);
        double[] north = view.direction(0, (map.y1() - map.y0()) * 0.62);
        line(fill, matrix, map, cx, cy, north[0], north[1], 0.004, z, RING);
        box(fill, matrix, new Box(north[0] - 0.02, north[1] - 0.02, north[0] + 0.02, north[1] + 0.02), z, map.contains(north[0], north[1]) ? AutopilotScreenRenderer.GREY : 0);

        // Where the airliner has been.
        double[] last = null;
        for (double[] point : track) {
            double[] at = view.toScreen(point[0], point[1]);
            if (last != null) {
                line(fill, matrix, map, last[0], last[1], at[0], at[1], 0.008, z + LAYER, AutopilotScreenRenderer.TRACK);
            }
            last = at;
        }
        if (last != null) {
            line(fill, matrix, map, last[0], last[1], cx, cy, 0.008, z + LAYER, AutopilotScreenRenderer.TRACK);
        }

        // Where the autopilot is taking it: the circle it flies round, or a line along the heading it is turning to.
        if (AutopilotScreenRenderer.engaged(autopilot) && autopilot.phase() != AutopilotBlockEntity.Phase.TAKEOFF) {
            if (autopilot.lateral() == AutopilotBlockEntity.Lateral.HEADING) {
                double[] end = view.direction(autopilot.targetHeading() + 180, (map.y1() - map.y0()) * 1.5);
                line(fill, matrix, map, cx, cy, end[0], end[1], 0.01, z + 2 * LAYER, AutopilotScreenRenderer.TARGET);
            } else {
                double[] middle = view.toScreen(autopilot.orbitX(), autopilot.orbitZ());
                circle(fill, matrix, map, middle[0], middle[1], autopilot.orbitRadius() * scale, 0.01, z + 2 * LAYER, AutopilotScreenRenderer.TARGET);
                box(fill, matrix, new Box(middle[0] - 0.015, middle[1] - 0.015, middle[0] + 0.015, middle[1] + 0.015), z + 2 * LAYER,
                    map.contains(middle[0], middle[1]) ? AutopilotScreenRenderer.TARGET : 0);
            }
        }

        // The airliner, nose up.
        float top = z + 3 * LAYER;
        ScreenDrawing.vertex(fill, matrix, cx, cy + 0.06, top, AutopilotScreenRenderer.WHITE);
        ScreenDrawing.vertex(fill, matrix, cx - 0.04, cy - 0.04, top, AutopilotScreenRenderer.WHITE);
        ScreenDrawing.vertex(fill, matrix, cx, cy - 0.015, top, AutopilotScreenRenderer.WHITE);
        ScreenDrawing.vertex(fill, matrix, cx + 0.04, cy - 0.04, top, AutopilotScreenRenderer.WHITE);
    }

    /** World places turned onto the map, the airliner's heading up. */
    record MapView(double x, double z, double heading, double scale, double cx, double cy) {
        double[] toScreen(double worldX, double worldZ) {
            double dx = worldX - x;
            double dz = worldZ - z;
            double across = dx * Math.cos(heading) + dz * Math.sin(heading);
            double ahead = dx * Math.sin(heading) - dz * Math.cos(heading);
            return new double[]{cx + across * scale, cy + ahead * scale};
        }

        /** A point this far (in screen units) from the airliner toward a compass bearing. */
        double[] direction(double bearing, double distance) {
            double turn = Math.toRadians(bearing) - heading;
            return new double[]{cx + Math.sin(turn) * distance, cy + Math.cos(turn) * distance};
        }
    }

    static void circle(VertexConsumer fill, Matrix4f matrix, Box clip, double cx, double cy, double radius, double width, float z, int colour) {
        for (int k = 0; k < CIRCLE_SEGMENTS; k++) {
            double a0 = 2 * Math.PI * k / CIRCLE_SEGMENTS;
            double a1 = 2 * Math.PI * (k + 1) / CIRCLE_SEGMENTS;
            line(fill, matrix, clip, cx + radius * Math.cos(a0), cy + radius * Math.sin(a0), cx + radius * Math.cos(a1), cy + radius * Math.sin(a1), width, z, colour);
        }
    }

    /** A line as a thin quad, the part of it inside the clip (Liang-Barsky). */
    static void line(VertexConsumer fill, Matrix4f matrix, Box clip, double x0, double y0, double x1, double y1, double width, float z, int colour) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double t0 = 0;
        double t1 = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0 - clip.x0(), clip.x1() - x0, y0 - clip.y0(), clip.y1() - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0 && q[i] < 0) {
                return;
            }
            if (p[i] == 0) {
                continue;
            }
            double t = q[i] / p[i];
            if (p[i] < 0) {
                t0 = Math.max(t0, t);
            } else {
                t1 = Math.min(t1, t);
            }
        }
        if (t0 > t1) {
            return;
        }
        double ax = x0 + dx * t0;
        double ay = y0 + dy * t0;
        double bx = x0 + dx * t1;
        double by = y0 + dy * t1;
        double length = Math.hypot(bx - ax, by - ay);
        if (length < 1e-6) {
            return;
        }
        double nx = -(by - ay) / length * width / 2;
        double ny = (bx - ax) / length * width / 2;
        ScreenDrawing.vertex(fill, matrix, ax - nx, ay - ny, z, colour);
        ScreenDrawing.vertex(fill, matrix, bx - nx, by - ny, z, colour);
        ScreenDrawing.vertex(fill, matrix, bx + nx, by + ny, z, colour);
        ScreenDrawing.vertex(fill, matrix, ax + nx, ay + ny, z, colour);
    }

    /** A box on the screen; nothing for a colour of 0. */
    static void box(VertexConsumer fill, Matrix4f matrix, Box box, float z, int colour) {
        if (colour != 0) {
            ScreenDrawing.rect(fill, matrix, box.x0(), box.y0(), box.x1(), box.y1(), z, colour);
        }
    }

}
