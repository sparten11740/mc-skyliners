package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlock;
import dev.aerolarge.block.AutopilotScreenBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Button;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the autopilot screen over the whole of its face, from the block that holds it. On the left a moving map,
 * the way the airliner is heading always up: the track it has flown, the circle it is flying round or the heading it
 * is turning to, a ring at half the map's reach, north marked, and the airliner in the middle of the lower part. Over
 * the map the autopilot's mode and targets; under it, how the airliner is flying now. On the right the buttons, the
 * ones for what the autopilot is doing lit. The airliner's place and heading come from its pose as the client draws
 * it, so the map turns as smoothly as the airliner does. It is only drawn with the camera in front of it.
 */
public class AutopilotScreenRenderer implements BlockEntityRenderer<AutopilotScreenBlockEntity> {
    /**
     * Where the face is, from the middle of the block toward the way it faces: on the front of the slim screen, the
     * drawing lifted just off it; each layer of the drawing lifted off the one below, and the text off them all.
     */
    private static final float SURFACE = -0.5F + AutopilotScreenBlock.DEPTH / 16F + 0.002F;
    private static final float LAYER = 0.002F;
    private static final float TEXT_LIFT = 0.01F;
    /** Text size: blocks per font pixel. */
    private static final float TEXT = 1 / 170F;

    /** The screen's colours, which the panels of its settings screen ({@link AutopilotSettingsScreen}, {@link PanelArt}) share. */
    static final int CASING = 0xFF15171B;
    static final int GLASS = 0xFF05080D;
    static final int TRACK = 0xFF46C8F0;
    static final int TARGET = 0xFFE040E0;
    static final int WHITE = 0xFFF2F2F2;
    static final int GREEN = 0xFF46E070;
    static final int AMBER = 0xFFF2B830;
    static final int GREY = 0xFF9AA4B0;
    static final int BUTTON = 0xFF262B33;
    static final int BUTTON_LIT = 0xFF1F5A34;
    static final int BUTTON_EDGE = 0xFF4A525E;

    /**
     * Only with the camera in front of the screen, which is drawn from the front only, and as far off as ever. The game
     * asks this whenever the screen is in view and in reach, from either side, so the track for the map is kept here.
     */
    @Override
    public boolean shouldRender(AutopilotScreenBlockEntity screen, Vec3 camera) {
        Level level = screen.getLevel();
        if (level == null || !BlockEntityRenderer.super.shouldRender(screen, camera)) {
            return false;
        }
        screen.record(SableClient.toWorld(level, Vec3.atCenterOf(screen.getBlockPos())), level.getGameTime());
        Direction facing = screen.getBlockState().getValue(AutopilotScreenBlock.FACING);
        return Sight.facing(screen.getBlockPos(), facing, SURFACE, camera, getViewDistance());
    }

    @Override
    public void render(AutopilotScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = screen.getBlockState().getValue(AutopilotScreenBlock.FACING);
        AutopilotBlockEntity autopilot = screen.autopilot();
        SableClient.Attitude attitude = SableClient.attitude(screen, facing.getOpposite(), partialTick);
        Vec3 here = SableClient.toWorld(screen.getLevel(), Vec3.atCenterOf(screen.getBlockPos()));
        double heading = attitude != null ? attitude.heading() : Math.floorMod((int) facing.getOpposite().toYRot() + 180, 360);

        pose.pushPose();
        // Onto the screen face, x to the viewer's right and y up, from the bottom left corner of the whole screen.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, -0.5, SURFACE);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer fill = ScreenDrawing.shapes(buffers);
        Font font = Minecraft.getInstance().font;

        MovingMap.box(fill, matrix, new Box(0, 0, AutopilotScreenBlockEntity.WIDTH, AutopilotScreenBlockEntity.HEIGHT), 0, CASING);
        MovingMap.box(fill, matrix, AutopilotScreenBlockEntity.MAP, LAYER, GLASS);
        MovingMap.box(fill, matrix, AutopilotScreenBlockEntity.HEADER, LAYER, GLASS);
        MovingMap.draw(fill, matrix, AutopilotScreenBlockEntity.MAP, screen.track(), screen.range(), autopilot, here, heading, 2 * LAYER);
        for (Button button : Button.values()) {
            MovingMap.box(fill, matrix, button.box, LAYER, BUTTON_EDGE);
            Box inner = new Box(button.box.x0() + 0.012, button.box.y0() + 0.012, button.box.x1() - 0.012, button.box.y1() - 0.012);
            MovingMap.box(fill, matrix, inner, 2 * LAYER, lit(button, autopilot) ? BUTTON_LIT : BUTTON);
        }
        // Text last: it takes its own buffer, which ends the one the shapes went into.
        header(font, pose, buffers, autopilot, attitude);
        readout(font, pose, buffers, attitude, here, screen.range());
        for (Button button : Button.values()) {
            Component label = button == Button.ENGAGE
                ? Component.translatable(button.label(), Component.translatable(engaged(autopilot) ? "screen.aerolarge.autopilot.on" : "screen.aerolarge.autopilot.off"))
                : Component.translatable(button.label());
            text(font, pose, buffers, label, (button.box.x0() + button.box.x1()) / 2, (button.box.y0() + button.box.y1()) / 2, WHITE, ScreenDrawing.Align.CENTRE);
        }
        pose.popPose();
    }

    static boolean engaged(@Nullable AutopilotBlockEntity autopilot) {
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

    /** The autopilot's mode and targets, over the map. */
    private static void header(Font font, PoseStack pose, MultiBufferSource buffers, @Nullable AutopilotBlockEntity autopilot, @Nullable SableClient.Attitude attitude) {
        Box header = AutopilotScreenBlockEntity.HEADER;
        double y = (header.y0() + header.y1()) / 2;
        text(font, pose, buffers, state(autopilot, attitude), header.x0() + 0.04, y, GREEN, ScreenDrawing.Align.LEFT);
        text(font, pose, buffers, targets(autopilot), header.x1() - 0.04, y, TARGET, ScreenDrawing.Align.RIGHT);
    }

    /** What the autopilot is doing: waiting for the airliner to be assembled, switched off, or its phase and mode. */
    static Component state(@Nullable AutopilotBlockEntity autopilot, @Nullable SableClient.Attitude attitude) {
        if (attitude == null) {
            return Component.translatable("screen.aerolarge.autopilot.standby");
        }
        if (!engaged(autopilot)) {
            return Component.translatable("screen.aerolarge.autopilot.disengaged");
        }
        Component phase = Component.translatable("message.aerolarge.autopilot.phase." + autopilot.phase().name().toLowerCase(Locale.ROOT));
        Component mode = switch (autopilot.lateral()) {
            case HEADING -> Component.translatable("screen.aerolarge.autopilot.mode.heading", AutopilotBlockEntity.compass(autopilot.targetHeading()));
            case ORBIT_LEFT -> Component.translatable("screen.aerolarge.autopilot.mode.orbit_left", Math.round(autopilot.orbitRadius()));
            case ORBIT_RIGHT -> Component.translatable("screen.aerolarge.autopilot.mode.orbit_right", Math.round(autopilot.orbitRadius()));
            case DESTINATION -> Component.translatable("screen.aerolarge.autopilot.mode.destination", Math.round(autopilot.orbitRadius()));
        };
        return Component.translatable("screen.aerolarge.autopilot.mode", phase, mode);
    }

    /** The height and speed the autopilot flies to, the touchdown while landing, its brakes once down; nothing off. */
    static Component targets(@Nullable AutopilotBlockEntity autopilot) {
        if (!engaged(autopilot)) {
            return Component.empty();
        }
        return switch (autopilot.phase()) {
            case LAND -> Component.translatable("screen.aerolarge.autopilot.targets.land", Math.round(autopilot.targetSpeed()));
            case ROLLOUT -> Component.translatable("screen.aerolarge.autopilot.targets.rollout");
            default -> Component.translatable("screen.aerolarge.autopilot.targets", Math.round(autopilot.targetAltitude()), Math.round(autopilot.targetSpeed()));
        };
    }

    /** How the airliner is flying now: its speed, its height (where the block drawing it is) and its heading. */
    static Component now(SableClient.Attitude attitude, Vec3 here) {
        return Component.translatable("screen.aerolarge.autopilot.now", Math.round(attitude.airspeed()), Math.round(here.y),
            String.format(Locale.ROOT, "%03d", Math.round(attitude.heading()) % 360));
    }

    /** How the airliner is flying now, along the bottom of the map. */
    private static void readout(Font font, PoseStack pose, MultiBufferSource buffers, @Nullable SableClient.Attitude attitude, Vec3 here, int range) {
        Box map = AutopilotScreenBlockEntity.MAP;
        double y = map.y0() + 0.05;
        if (attitude != null) {
            text(font, pose, buffers, now(attitude, here), map.x0() + 0.04, y, WHITE, ScreenDrawing.Align.LEFT);
        }
        text(font, pose, buffers, Component.translatable("screen.aerolarge.autopilot.range", range), map.x1() - 0.04, y, GREY, ScreenDrawing.Align.RIGHT);
    }

    /** Text placed against x as aligned, centred on y, in the screen's units. */
    private static void text(Font font, PoseStack pose, MultiBufferSource buffers, Component text, double x, double y, int colour, ScreenDrawing.Align align) {
        ScreenDrawing.text(font, pose, buffers, text, x, y, TEXT_LIFT, TEXT, colour, align);
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
