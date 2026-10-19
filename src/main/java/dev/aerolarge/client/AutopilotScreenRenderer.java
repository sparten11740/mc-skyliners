package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlock;
import dev.aerolarge.block.AutopilotScreenBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Button;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the autopilot screen over the whole of its face, from the block that holds it. On the left a moving map,
 * the way the airliner is heading always up: the track it has flown, the circle it is flying round or the heading it
 * is turning to, a ring at half the map's reach, north marked, and the airliner in the middle of the lower part. Over
 * the map the autopilot's mode and targets; under it, how the airliner is flying now. On the right the buttons, the
 * ones for what the autopilot is doing lit. The airliner's place and heading come from its pose as the client draws
 * it, so the map turns as smoothly as the airliner does.
 */
public class AutopilotScreenRenderer implements BlockEntityRenderer<AutopilotScreenBlockEntity> {
    /** Lifts the drawing just off the block face, and each layer of it off the one below. */
    private static final float SURFACE = 0.502F;
    private static final float LAYER = 0.002F;
    /** Text size: blocks per font pixel. */
    private static final float TEXT = 1 / 170F;
    /** Where the airliner sits on the map: across its middle, this far up from its bottom as a share of its height. */
    private static final double PLANE_UP = 0.3;
    /** Segments a circle is drawn with. */
    private static final int CIRCLE_SEGMENTS = 64;

    private static final int CASING = 0xFF15171B;
    private static final int GLASS = 0xFF05080D;
    private static final int RING = 0xFF2A3A55;
    private static final int TRACK = 0xFF46C8F0;
    private static final int TARGET = 0xFFE040E0;
    private static final int WHITE = 0xFFF2F2F2;
    private static final int GREEN = 0xFF46E070;
    private static final int AMBER = 0xFFF2B830;
    private static final int GREY = 0xFF9AA4B0;
    private static final int BUTTON = 0xFF262B33;
    private static final int BUTTON_LIT = 0xFF1F5A34;
    private static final int BUTTON_EDGE = 0xFF4A525E;

    @Override
    public void render(AutopilotScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = screen.getBlockState().getValue(AutopilotScreenBlock.FACING);
        AutopilotBlockEntity autopilot = screen.autopilot();
        SableClient.Attitude attitude = SableClient.attitude(screen, facing.getOpposite(), partialTick);
        Vec3 here = SableClient.toWorld(screen.getLevel(), Vec3.atCenterOf(screen.getBlockPos()));
        double heading = attitude != null ? attitude.heading() : Math.floorMod((int) facing.getOpposite().toYRot() + 180, 360);
        if (screen.getLevel() != null) {
            screen.record(here, screen.getLevel().getGameTime());
        }

        pose.pushPose();
        // Onto the screen face, x to the viewer's right and y up, from the bottom left corner of the whole screen.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, -0.5, SURFACE);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer fill = buffers.getBuffer(RenderType.textBackground());
        Font font = Minecraft.getInstance().font;

        box(fill, matrix, new Box(0, 0, AutopilotScreenBlockEntity.WIDTH, AutopilotScreenBlockEntity.HEIGHT), 0, CASING);
        box(fill, matrix, AutopilotScreenBlockEntity.MAP, LAYER, GLASS);
        box(fill, matrix, AutopilotScreenBlockEntity.HEADER, LAYER, GLASS);
        map(fill, matrix, screen, autopilot, here, heading);
        for (Button button : Button.values()) {
            box(fill, matrix, button.box, LAYER, BUTTON_EDGE);
            Box inner = new Box(button.box.x0() + 0.012, button.box.y0() + 0.012, button.box.x1() - 0.012, button.box.y1() - 0.012);
            box(fill, matrix, inner, 2 * LAYER, lit(button, autopilot) ? BUTTON_LIT : BUTTON);
        }
        // Text last: it takes its own buffer, which ends the one the shapes went into.
        header(font, pose, buffers, autopilot, attitude);
        readout(font, pose, buffers, attitude, here, screen.range());
        for (Button button : Button.values()) {
            Component label = button == Button.ENGAGE
                ? Component.translatable(button.label(), Component.translatable(engaged(autopilot) ? "screen.aerolarge.autopilot.on" : "screen.aerolarge.autopilot.off"))
                : Component.translatable(button.label());
            text(font, pose, buffers, label, (button.box.x0() + button.box.x1()) / 2, (button.box.y0() + button.box.y1()) / 2, WHITE, true);
        }
        pose.popPose();
    }

    private static boolean engaged(@Nullable AutopilotBlockEntity autopilot) {
        return autopilot != null && autopilot.phase() != AutopilotBlockEntity.Phase.OFF;
    }

    /** Whether a button stands for what the autopilot is doing now. */
    private static boolean lit(Button button, @Nullable AutopilotBlockEntity autopilot) {
        if (!engaged(autopilot)) {
            return false;
        }
        return switch (button) {
            case ENGAGE -> true;
            case ORBIT_LEFT -> autopilot.lateral() == AutopilotBlockEntity.Lateral.ORBIT_LEFT;
            case ORBIT_RIGHT -> autopilot.lateral() == AutopilotBlockEntity.Lateral.ORBIT_RIGHT;
            default -> false;
        };
    }

    /** The moving map: everything turned so the airliner's heading is up, cut to the map. */
    private static void map(VertexConsumer fill, Matrix4f matrix, AutopilotScreenBlockEntity screen, @Nullable AutopilotBlockEntity autopilot, Vec3 here, double heading) {
        Box map = AutopilotScreenBlockEntity.MAP;
        double scale = (map.x1() - map.x0()) / screen.range();
        double cx = (map.x0() + map.x1()) / 2;
        double cy = map.y0() + PLANE_UP * (map.y1() - map.y0());
        MapView view = new MapView(here.x, here.z, Math.toRadians(heading), scale, cx, cy);
        float z = 2 * LAYER;

        // A ring at half the map's reach, and north marked on a ring further out.
        circle(fill, matrix, map, cx, cy, (map.x1() - map.x0()) / 4, 0.006, z, RING);
        double[] north = view.direction(0, (map.y1() - map.y0()) * 0.62);
        line(fill, matrix, map, cx, cy, north[0], north[1], 0.004, z, RING);
        box(fill, matrix, new Box(north[0] - 0.02, north[1] - 0.02, north[0] + 0.02, north[1] + 0.02), z, map.contains(north[0], north[1]) ? GREY : 0);

        // Where the airliner has been.
        double[] last = null;
        for (double[] point : screen.track()) {
            double[] at = view.toScreen(point[0], point[1]);
            if (last != null) {
                line(fill, matrix, map, last[0], last[1], at[0], at[1], 0.008, z + LAYER, TRACK);
            }
            last = at;
        }
        if (last != null) {
            line(fill, matrix, map, last[0], last[1], cx, cy, 0.008, z + LAYER, TRACK);
        }

        // Where the autopilot is taking it: the circle it flies round, or a line along the heading it is turning to.
        if (engaged(autopilot) && autopilot.phase() != AutopilotBlockEntity.Phase.TAKEOFF) {
            if (autopilot.lateral() == AutopilotBlockEntity.Lateral.HEADING) {
                double[] end = view.direction(autopilot.targetHeading() + 180, (map.y1() - map.y0()) * 1.5);
                line(fill, matrix, map, cx, cy, end[0], end[1], 0.01, z + 2 * LAYER, TARGET);
            } else {
                double[] middle = view.toScreen(autopilot.orbitX(), autopilot.orbitZ());
                circle(fill, matrix, map, middle[0], middle[1], autopilot.orbitRadius() * scale, 0.01, z + 2 * LAYER, TARGET);
                box(fill, matrix, new Box(middle[0] - 0.015, middle[1] - 0.015, middle[0] + 0.015, middle[1] + 0.015), z + 2 * LAYER,
                    map.contains(middle[0], middle[1]) ? TARGET : 0);
            }
        }

        // The airliner, nose up.
        float top = z + 3 * LAYER;
        vertex(fill, matrix, cx, cy + 0.06, top, WHITE);
        vertex(fill, matrix, cx - 0.04, cy - 0.04, top, WHITE);
        vertex(fill, matrix, cx, cy - 0.015, top, WHITE);
        vertex(fill, matrix, cx + 0.04, cy - 0.04, top, WHITE);
    }

    /** The autopilot's mode and targets, over the map. */
    private static void header(Font font, PoseStack pose, MultiBufferSource buffers, @Nullable AutopilotBlockEntity autopilot, @Nullable SableClient.Attitude attitude) {
        Box header = AutopilotScreenBlockEntity.HEADER;
        double y = (header.y0() + header.y1()) / 2;
        Component left;
        Component right = Component.empty();
        if (attitude == null) {
            left = Component.translatable("screen.aerolarge.autopilot.standby");
        } else if (!engaged(autopilot)) {
            left = Component.translatable("screen.aerolarge.autopilot.disengaged");
        } else {
            Component phase = Component.translatable("message.aerolarge.autopilot.phase." + autopilot.phase().name().toLowerCase(Locale.ROOT));
            Component mode = switch (autopilot.lateral()) {
                case HEADING -> Component.translatable("screen.aerolarge.autopilot.mode.heading", AutopilotBlockEntity.compass(autopilot.targetHeading()));
                case ORBIT_LEFT -> Component.translatable("screen.aerolarge.autopilot.mode.orbit_left", Math.round(autopilot.orbitRadius()));
                case ORBIT_RIGHT -> Component.translatable("screen.aerolarge.autopilot.mode.orbit_right", Math.round(autopilot.orbitRadius()));
            };
            left = Component.translatable("screen.aerolarge.autopilot.mode", phase, mode);
            right = Component.translatable("screen.aerolarge.autopilot.targets", Math.round(autopilot.targetAltitude()), Math.round(autopilot.targetSpeed()));
        }
        text(font, pose, buffers, left, header.x0() + 0.04, y, GREEN, false);
        textRight(font, pose, buffers, right, header.x1() - 0.04, y, TARGET);
    }

    /** How the airliner is flying now, along the bottom of the map. */
    private static void readout(Font font, PoseStack pose, MultiBufferSource buffers, @Nullable SableClient.Attitude attitude, Vec3 here, int range) {
        Box map = AutopilotScreenBlockEntity.MAP;
        double y = map.y0() + 0.05;
        if (attitude != null) {
            Component now = Component.translatable("screen.aerolarge.autopilot.now", Math.round(attitude.airspeed()), Math.round(here.y),
                String.format(Locale.ROOT, "%03d", Math.round(attitude.heading()) % 360));
            text(font, pose, buffers, now, map.x0() + 0.04, y, WHITE, false);
        }
        textRight(font, pose, buffers, Component.translatable("screen.aerolarge.autopilot.range", range), map.x1() - 0.04, y, GREY);
    }

    /** World places turned onto the map, the airliner's heading up. */
    private record MapView(double x, double z, double heading, double scale, double cx, double cy) {
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

    private static void circle(VertexConsumer fill, Matrix4f matrix, Box clip, double cx, double cy, double radius, double width, float z, int colour) {
        for (int k = 0; k < CIRCLE_SEGMENTS; k++) {
            double a0 = 2 * Math.PI * k / CIRCLE_SEGMENTS;
            double a1 = 2 * Math.PI * (k + 1) / CIRCLE_SEGMENTS;
            line(fill, matrix, clip, cx + radius * Math.cos(a0), cy + radius * Math.sin(a0), cx + radius * Math.cos(a1), cy + radius * Math.sin(a1), width, z, colour);
        }
    }

    /** A line as a thin quad, the part of it inside the clip (Liang-Barsky). */
    private static void line(VertexConsumer fill, Matrix4f matrix, Box clip, double x0, double y0, double x1, double y1, double width, float z, int colour) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double t0 = 0;
        double t1 = 1;
        double[] p = {-dx, dx, -dy, dy};
        double[] q = {x0 - clip.x0(), clip.x1() - x0, y0 - clip.y0(), clip.y1() - y0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (q[i] < 0) {
                    return;
                }
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
        vertex(fill, matrix, ax - nx, ay - ny, z, colour);
        vertex(fill, matrix, bx - nx, by - ny, z, colour);
        vertex(fill, matrix, bx + nx, by + ny, z, colour);
        vertex(fill, matrix, ax + nx, ay + ny, z, colour);
    }

    private static void box(VertexConsumer fill, Matrix4f matrix, Box box, float z, int colour) {
        if (colour == 0) {
            return;
        }
        vertex(fill, matrix, box.x0(), box.y0(), z, colour);
        vertex(fill, matrix, box.x1(), box.y0(), z, colour);
        vertex(fill, matrix, box.x1(), box.y1(), z, colour);
        vertex(fill, matrix, box.x0(), box.y1(), z, colour);
    }

    private static void vertex(VertexConsumer fill, Matrix4f matrix, double x, double y, float z, int colour) {
        fill.addVertex(matrix, (float) x, (float) y, z).setColor(colour).setLight(LightTexture.FULL_BRIGHT);
    }

    /** Text starting at x (or centred on it), centred on y, in the screen's units, lit as the screen is. */
    private static void text(Font font, PoseStack pose, MultiBufferSource buffers, Component text, double x, double y, int colour, boolean centred) {
        float left = centred ? -font.width(text) / 2F : 0;
        draw(font, pose, buffers, text, x, y, left, colour);
    }

    /** Text ending at x, centred on y. */
    private static void textRight(Font font, PoseStack pose, MultiBufferSource buffers, Component text, double x, double y, int colour) {
        draw(font, pose, buffers, text, x, y, -font.width(text), colour);
    }

    private static void draw(Font font, PoseStack pose, MultiBufferSource buffers, Component text, double x, double y, float left, int colour) {
        pose.pushPose();
        pose.translate(x, y, 0.01);
        pose.scale(TEXT, -TEXT, TEXT);
        font.drawInBatch(text, left, -font.lineHeight / 2F, colour, false, pose.last().pose(), buffers, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(AutopilotScreenBlockEntity screen) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(AutopilotScreenBlockEntity screen) {
        return new AABB(screen.getBlockPos()).inflate(Math.max(AutopilotScreenBlock.COLUMNS, AutopilotScreenBlock.ROWS));
    }
}
