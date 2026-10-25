package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.AutopilotBlockEntity.Lateral;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.block.Brightness;
import dev.aerolarge.block.CockpitDisplayBlockEntity;
import dev.aerolarge.block.CockpitDisplayBlockEntity.Link;
import dev.aerolarge.block.CockpitKeys.Function;
import dev.aerolarge.block.CockpitKeys.Key;
import dev.aerolarge.block.DropReleaseBlock;
import dev.aerolarge.block.GearLeverBlock;
import dev.aerolarge.block.JumpLightBlock;
import dev.aerolarge.block.JumpSwitchBlock;
import dev.aerolarge.block.LaserSwitchBlock;
import dev.aerolarge.block.ParkingBrakeBlock;
import dev.aerolarge.block.SeatbeltSwitchBlock;
import dev.aerolarge.block.TillerBlock;
import dev.aerolarge.block.WindowShadesBlock;
import dev.aerolarge.client.PanelArt.Icon;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

/**
 * Draws the touch keys of the flight deck's curved row (see {@link dev.aerolarge.block.CockpitKeys}), each a picture,
 * so that whoever cannot read can still work them. The systems screen's tiles show a picture of what each works, as
 * it is now (a seated passenger buckled in, a light bulb or the moon, a skydiver beside the jump light, a window with
 * its shade up or down, the laser turret firing, the drop bay shut or dropping), its rim lit in its colour while on.
 * On the sloped panel under the screens: the tiller's arrows either side of a little wheel turned as the tiller is;
 * the parking brake as a wheel gripped red while it is set; the landing gear as a wheel with an arrow the way its
 * lever is thrown and its three lamps; the autopilot's buttons in its settings' pictures, each with the way it steps,
 * the on/off button and the circling it does lit green; and the outside cameras' buttons, each the airliner from the
 * side with a dot where its camera is, the one on the screen lit.
 * The switches' states are read from their blocks out of sight under the floor.
 */
final class CockpitKeysArt {
    /** Each layer lifted off the one below, in blocks. */
    private static final float LAYER = 0.002F;
    /** A key's rim, in blocks. */
    private static final double RIM = 0.012;
    /**
     * A lit systems tile: how much of its colour its face takes on, how many times an unlit rim's width its rim is,
     * and how tall the light along its bottom is, as a share of the tile's height (lit, on an airliner's push button,
     * dark while off).
     */
    private static final float LIT_TINT = 0.3F;
    private static final double LIT_RIM = 2.5;
    private static final double ON_LIGHT = 0.12;
    /** How big the autopilot keys' step arrows are, as a share of the key's smaller side. */
    private static final double STEP_ARROW = 0.22;
    /** Segments a ring is drawn with. */
    private static final int RING_SEGMENTS = 20;
    /** How far the little tiller wheel turns for each notch the tiller is turned, in degrees. */
    private static final double STEER_TURN = 30;

    private static final int KEY = AutopilotScreenRenderer.BUTTON;
    private static final int KEY_LIT = AutopilotScreenRenderer.BUTTON_LIT;
    private static final int EDGE = AutopilotScreenRenderer.BUTTON_EDGE;
    private static final int WHITE = AutopilotScreenRenderer.WHITE;
    private static final int GREY = AutopilotScreenRenderer.GREY;
    private static final int AMBER = AutopilotScreenRenderer.AMBER;
    private static final int GREEN = AutopilotScreenRenderer.GREEN;
    private static final int RED = PanelArt.RED;
    private static final int NIGHT_BLUE = 0xFF4A78E0;
    /** The cabin lights' tile lit bright: a warm pale yellow, apart from the white the pointer outlines keys in. */
    private static final int BRIGHT = 0xFFF2E3A0;
    private static final int LAMP_OFF = 0xFF2A2F37;
    private static final int TYRE = 0xFF15171B;
    /** Pictures as they are, or darkened. */
    private static final int PICTURE_ON = 0xFFFFFFFF;
    private static final int PICTURE_OFF = 0xFF8A8A8A;

    /** The systems tiles' pictures (from tools/generate_textures.mjs), by the switch and its state. */
    private static final ResourceLocation SEATBELTS = picture("seatbelts");
    private static final ResourceLocation SHADES_OPEN = picture("shades_open");
    private static final ResourceLocation SHADES_CLOSED = picture("shades_closed");
    private static final ResourceLocation LASER = picture("laser");
    private static final ResourceLocation DROP_SAFE = picture("drop_safe");
    private static final ResourceLocation DROP_ARMED = picture("drop_armed");
    private static final ResourceLocation[] CABIN_LIGHTS = {picture("cabin_lights_bright"), picture("cabin_lights_dim"), picture("cabin_lights_night")};
    private static final ResourceLocation[] JUMP_LIGHTS = {picture("jump_lights_off"), picture("jump_lights_red"), picture("jump_lights_green")};
    private static final Icon ENGAGE = Icon.of("autopilot/engage");
    private static final Icon HEADING = Icon.of("autopilot/heading");
    private static final Icon ORBIT_LEFT = Icon.of("autopilot/orbit_left");
    private static final Icon ORBIT_RIGHT = Icon.of("autopilot/orbit_right");
    private static final Icon ALTITUDE = Icon.of("autopilot/altitude");
    private static final Icon SPEED = Icon.of("autopilot/speed");
    private static final Icon RADIUS = Icon.of("autopilot/radius");

    /** A systems tile's look: the picture of its switch as it is now, and the colour it is lit in, or 0 while off. */
    private record Look(ResourceLocation picture, int lit) {
    }

    private CockpitKeysArt() {
    }

    /** The systems screen's tiles, on the screen's face from its bottom left corner. */
    static void tiles(CockpitDisplayBlockEntity display, Level level, MultiBufferSource buffers, Matrix4f matrix) {
        List<Key> keys = display.screenKeys();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        for (Key key : keys) {
            systemsTile(shapes, matrix, key.box(), look(key.function(), state(display, level, key.function().link)).lit());
        }
        // Pictures last: each takes its own buffer, which ends the one the shapes went into.
        for (Key key : keys) {
            Look look = look(key.function(), state(display, level, key.function().link));
            picture(buffers, matrix, look.picture(), overLight(key.box()), 0.8, look.lit() != 0 ? PICTURE_ON : PICTURE_OFF);
        }
    }

    /**
     * The keys on the sloped panel under the screen, on the slope from its front left corner as it would be uncut,
     * between its mitred ends, the slope this long.
     */
    static void slope(CockpitDisplayBlockEntity display, Level level, MultiBufferSource buffers, Matrix4f matrix, float left, float right, float length) {
        List<Key> keys = display.slopeKeys();
        AutopilotBlockEntity autopilot = display.autopilot();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        for (Key key : keys) {
            tile(shapes, matrix, key.box(), EDGE, lit(key.function(), autopilot) ? KEY_LIT : KEY);
            drawn(shapes, matrix, key, display, level);
        }
        if (display.desk() == CockpitDisplayBlockEntity.Desk.TILLER) {
            tillerWheel(shapes, matrix, (left + right) / 2, length / 2, Math.min((right - left) / 7, length / 2.5), state(display, level, Link.TILLER));
        }
        for (Key key : keys) {
            Icon icon = icon(key.function());
            if (icon != null) {
                Box box = key.box();
                Box beside = new Box(box.x0(), box.y0(), box.x0() + (box.x1() - box.x0()) * 0.7, box.y1());
                picture(buffers, matrix, icon.texture(), beside, 0.85, bright(key.function(), autopilot) ? PICTURE_ON : PICTURE_OFF);
            }
        }
    }

    /** What the block a link leads to is now; air where the panel has no such link. */
    private static BlockState state(CockpitDisplayBlockEntity display, Level level, Link link) {
        BlockPos at = display.linked(link);
        return at == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(at);
    }

    private static boolean on(BlockState state, Property<Boolean> property) {
        return state.hasProperty(property) && state.getValue(property);
    }

    /** A systems tile's look, by the switch it works and that switch's state. */
    private static Look look(Function function, BlockState state) {
        return switch (function) {
            case CABIN_LIGHTS -> cabinLights(state);
            case SEATBELTS -> new Look(SEATBELTS, on(state, SeatbeltSwitchBlock.ON) ? AMBER : 0);
            case JUMP_LIGHTS -> jumpLights(state);
            case SHADES -> on(state, WindowShadesBlock.SHADED) ? new Look(SHADES_CLOSED, AMBER) : new Look(SHADES_OPEN, 0);
            case LASER -> new Look(LASER, on(state, LaserSwitchBlock.ARMED) ? RED : 0);
            case DROP -> on(state, DropReleaseBlock.ARMED) ? new Look(DROP_ARMED, RED) : new Look(DROP_SAFE, 0);
            default -> new Look(SEATBELTS, 0);
        };
    }

    /** The dimmer: lit pale yellow while bright, amber while dimmed and blue at night. */
    private static Look cabinLights(BlockState state) {
        Brightness brightness = state.hasProperty(Brightness.PROPERTY) ? state.getValue(Brightness.PROPERTY) : Brightness.BRIGHT;
        int lit = switch (brightness) {
            case BRIGHT -> BRIGHT;
            case DIM -> AMBER;
            case NIGHT -> NIGHT_BLUE;
        };
        return new Look(CABIN_LIGHTS[brightness.ordinal()], lit);
    }

    /** The jump light switch: lit red to stand by and green to go. */
    private static Look jumpLights(BlockState state) {
        JumpLightBlock.Signal signal = state.hasProperty(JumpSwitchBlock.SIGNAL) ? state.getValue(JumpSwitchBlock.SIGNAL) : JumpLightBlock.Signal.OFF;
        int lit = switch (signal) {
            case OFF -> 0;
            case RED -> RED;
            case GREEN -> GREEN;
        };
        return new Look(JUMP_LIGHTS[signal.ordinal()], lit);
    }

    /** Whether one of the autopilot's keys is lit: the on/off key while it flies, a circling key while it circles so. */
    private static boolean lit(Function function, @Nullable AutopilotBlockEntity autopilot) {
        if (autopilot == null || !AutopilotScreenRenderer.engaged(autopilot)) {
            return false;
        }
        return switch (function) {
            case ENGAGE -> true;
            case ORBIT_LEFT -> autopilot.lateral() == Lateral.ORBIT_LEFT;
            case ORBIT_RIGHT -> autopilot.lateral() == Lateral.ORBIT_RIGHT;
            default -> false;
        };
    }

    /**
     * Whether an autopilot key's picture is drawn bright: every one while the autopilot flies, and its on/off key
     * always, the one to find first.
     */
    private static boolean bright(Function function, @Nullable AutopilotBlockEntity autopilot) {
        return function == Function.ENGAGE || autopilot != null && AutopilotScreenRenderer.engaged(autopilot);
    }

    /** An autopilot key's picture from its settings; null for the keys drawn in shapes. */
    @Nullable
    private static Icon icon(Function function) {
        return switch (function) {
            case ENGAGE -> ENGAGE;
            case HEADING_LEFT, HEADING_RIGHT -> HEADING;
            case ORBIT_LEFT -> ORBIT_LEFT;
            case ORBIT_RIGHT -> ORBIT_RIGHT;
            case ALTITUDE_DOWN, ALTITUDE_UP -> ALTITUDE;
            case SPEED_DOWN, SPEED_UP -> SPEED;
            case RADIUS_DOWN, RADIUS_UP -> RADIUS;
            default -> null;
        };
    }

    /** What a key shows in shapes: the tiller's arrows, the brake, the gear, and the way the autopilot's keys step. */
    private static void drawn(VertexConsumer shapes, Matrix4f matrix, Key key, CockpitDisplayBlockEntity display, Level level) {
        Box box = key.box();
        double cx = (box.x0() + box.x1()) / 2;
        double cy = (box.y0() + box.y1()) / 2;
        double size = Math.min(box.x1() - box.x0(), box.y1() - box.y0());
        double stepX = box.x0() + (box.x1() - box.x0()) * 0.84;
        switch (key.function()) {
            case TILLER_LEFT -> arrow(shapes, matrix, cx, cy, size * 0.3, 180, WHITE);
            case TILLER_RIGHT -> arrow(shapes, matrix, cx, cy, size * 0.3, 0, WHITE);
            case BRAKE -> brake(shapes, matrix, cx, cy, size * 0.32, on(state(display, level, Link.BRAKE), ParkingBrakeBlock.SET));
            case GEAR -> gear(shapes, matrix, box, state(display, level, Link.GEAR));
            case HEADING_LEFT -> arrow(shapes, matrix, stepX, cy, size * STEP_ARROW, 180, WHITE);
            case HEADING_RIGHT -> arrow(shapes, matrix, stepX, cy, size * STEP_ARROW, 0, WHITE);
            case ALTITUDE_DOWN, SPEED_DOWN, RADIUS_DOWN -> arrow(shapes, matrix, stepX, cy, size * STEP_ARROW, 270, WHITE);
            case ALTITUDE_UP, SPEED_UP, RADIUS_UP -> arrow(shapes, matrix, stepX, cy, size * STEP_ARROW, 90, WHITE);
            case TAIL_CAMERA, WING_CAMERA, NOSE_CAMERA, FRONT_CAMERA -> TailCameraDisplayRenderer.cameraButton(shapes, matrix, key.function().camera,
                key.function().camera == display.camera(), box.x0() + RIM, box.y0() + RIM, box.x1() - RIM, box.y1() - RIM, 2 * LAYER);
            default -> {
            }
        }
    }

    /** A key's tile: its rim, and inside it its face. */
    /**
     * A systems tile in a colour, 0 while off: lit, its face tinted and its rim broad in the colour and the light along
     * its bottom on, so on and off differ at a glance, not by the rim alone.
     */
    private static void systemsTile(VertexConsumer shapes, Matrix4f matrix, Box box, int lit) {
        boolean on = lit != 0;
        double rim = on ? RIM * LIT_RIM : RIM;
        ScreenDrawing.rect(shapes, matrix, box.x0(), box.y0(), box.x1(), box.y1(), LAYER, on ? lit : EDGE);
        ScreenDrawing.rect(shapes, matrix, box.x0() + rim, box.y0() + rim, box.x1() - rim, box.y1() - rim, 2 * LAYER,
            on ? PanelArt.blend(KEY, lit, LIT_TINT) : KEY);
        double light = (box.y1() - box.y0()) * ON_LIGHT;
        double inset = (box.x1() - box.x0()) * 0.25;
        double bottom = box.y0() + RIM * LIT_RIM + light * 0.4;
        ScreenDrawing.rect(shapes, matrix, box.x0() + inset, bottom, box.x1() - inset, bottom + light, 3 * LAYER, on ? lit : LAMP_OFF);
    }

    /** The part of a systems tile over the light along its bottom, for its picture. */
    private static Box overLight(Box box) {
        double light = (box.y1() - box.y0()) * ON_LIGHT;
        return new Box(box.x0(), box.y0() + RIM * LIT_RIM + light * 1.6, box.x1(), box.y1());
    }

    private static void tile(VertexConsumer shapes, Matrix4f matrix, Box box, int rim, int face) {
        ScreenDrawing.rect(shapes, matrix, box.x0(), box.y0(), box.x1(), box.y1(), LAYER, rim);
        ScreenDrawing.rect(shapes, matrix, box.x0() + RIM, box.y0() + RIM, box.x1() - RIM, box.y1() - RIM, 2 * LAYER, face);
    }

    /** A triangle pointing a way (degrees round from the right, anticlockwise), round a middle. */
    private static void arrow(VertexConsumer shapes, Matrix4f matrix, double x, double y, double size, double way, int colour) {
        double a = Math.toRadians(way);
        double tipX = x + Math.cos(a) * size;
        double tipY = y + Math.sin(a) * size;
        double backX = x - Math.cos(a) * size * 0.6;
        double backY = y - Math.sin(a) * size * 0.6;
        double sideX = -Math.sin(a) * size * 0.8;
        double sideY = Math.cos(a) * size * 0.8;
        float z = 3 * LAYER;
        ScreenDrawing.vertex(shapes, matrix, tipX, tipY, z, colour);
        ScreenDrawing.vertex(shapes, matrix, backX + sideX, backY + sideY, z, colour);
        ScreenDrawing.vertex(shapes, matrix, backX - sideX, backY - sideY, z, colour);
        ScreenDrawing.vertex(shapes, matrix, tipX, tipY, z, colour);
    }

    /** A ring round a middle, this thick. */
    private static void ring(VertexConsumer shapes, Matrix4f matrix, double x, double y, double radius, float thickness, int colour) {
        for (int k = 0; k < RING_SEGMENTS; k++) {
            double a0 = 2 * Math.PI * k / RING_SEGMENTS;
            double a1 = 2 * Math.PI * (k + 1) / RING_SEGMENTS;
            ScreenDrawing.line(shapes, matrix, x + radius * Math.cos(a0), y + radius * Math.sin(a0), x + radius * Math.cos(a1), y + radius * Math.sin(a1),
                3 * LAYER, thickness, colour);
        }
    }

    /** The little tiller wheel between its arrows, turned as the tiller is: a rim, and a spoke up from its hub. */
    private static void tillerWheel(VertexConsumer shapes, Matrix4f matrix, double x, double y, double radius, BlockState tiller) {
        int steer = tiller.hasProperty(TillerBlock.STEER) ? tiller.getValue(TillerBlock.STEER) : TillerBlock.CENTRE;
        double a = Math.toRadians(90 - (steer - TillerBlock.CENTRE) * STEER_TURN);
        ring(shapes, matrix, x, y, radius, (float) (radius * 0.25), WHITE);
        ScreenDrawing.line(shapes, matrix, x, y, x + Math.cos(a) * radius, y + Math.sin(a) * radius, 3 * LAYER, (float) (radius * 0.25), AMBER);
        ScreenDrawing.disc(shapes, matrix, x, y, radius * 0.25, 10, 4 * LAYER, WHITE);
    }

    /** The parking brake: a wheel with its two pads, gripping it red while set, standing off it grey while released. */
    private static void brake(VertexConsumer shapes, Matrix4f matrix, double x, double y, double radius, boolean set) {
        ring(shapes, matrix, x, y, radius, (float) (radius * 0.3), GREY);
        double padAt = set ? radius * 1.1 : radius * 1.45;
        int pad = set ? RED : GREY;
        ScreenDrawing.rect(shapes, matrix, x - padAt - radius * 0.2, y - radius * 0.5, x - padAt + radius * 0.1, y + radius * 0.5, 4 * LAYER, pad);
        ScreenDrawing.rect(shapes, matrix, x + padAt - radius * 0.1, y - radius * 0.5, x + padAt + radius * 0.2, y + radius * 0.5, 4 * LAYER, pad);
    }

    /** The landing gear: a wheel, an arrow the way the lever is thrown (down for the gear down), and its three lamps. */
    private static void gear(VertexConsumer shapes, Matrix4f matrix, Box box, BlockState lever) {
        double width = box.x1() - box.x0();
        double height = box.y1() - box.y0();
        double radius = Math.min(width * 0.18, height * 0.3);
        double wheelX = box.x0() + width * 0.3;
        double wheelY = box.y0() + height * 0.42;
        ScreenDrawing.disc(shapes, matrix, wheelX, wheelY, radius, 14, 3 * LAYER, TYRE);
        ring(shapes, matrix, wheelX, wheelY, radius, (float) (radius * 0.25), GREY);
        ScreenDrawing.disc(shapes, matrix, wheelX, wheelY, radius * 0.35, 10, 4 * LAYER, GREY);
        boolean down = !lever.hasProperty(GearLeverBlock.DOWN) || lever.getValue(GearLeverBlock.DOWN);
        arrow(shapes, matrix, wheelX, box.y0() + height * 0.85, radius * 0.45, down ? 270 : 90, WHITE);
        int lamp = lampColour(lever);
        double lampX = box.x0() + width * 0.72;
        for (int k = 0; k < 3; k++) {
            ScreenDrawing.disc(shapes, matrix, lampX, box.y0() + height * (0.25 + 0.25 * k), radius * 0.38, 10, 3 * LAYER, lamp);
        }
    }

    /** The gear lever's lamps' colour: green down and locked, red while moving, dark up and stowed. */
    private static int lampColour(BlockState lever) {
        GearLeverBlock.Lamps lamps = lever.hasProperty(GearLeverBlock.LAMPS) ? lever.getValue(GearLeverBlock.LAMPS) : GearLeverBlock.Lamps.OFF;
        return switch (lamps) {
            case GREEN -> GREEN;
            case RED -> RED;
            case OFF -> LAMP_OFF;
        };
    }

    /** A picture, square in the middle of a box, so big a share of its smaller side, tinted. */
    private static void picture(MultiBufferSource buffers, Matrix4f matrix, ResourceLocation texture, Box box, double share, int tint) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(texture));
        double half = Math.min(box.x1() - box.x0(), box.y1() - box.y0()) * share / 2;
        float x = (float) ((box.x0() + box.x1()) / 2);
        float y = (float) ((box.y0() + box.y1()) / 2);
        float h = (float) half;
        float z = 3 * LAYER;
        consumer.addVertex(matrix, x - h, y - h, z).setColor(tint).setUv(0, 1).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix, x + h, y - h, z).setColor(tint).setUv(1, 1).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix, x + h, y + h, z).setColor(tint).setUv(1, 0).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix, x - h, y + h, z).setColor(tint).setUv(0, 0).setLight(LightTexture.FULL_BRIGHT);
    }

    private static ResourceLocation picture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/cockpit/" + name + ".png");
    }
}
