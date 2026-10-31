package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.airframe.OutsideCamera;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.block.Brightness;
import dev.aerolarge.block.CockpitDisplayBlockEntity;
import dev.aerolarge.block.CockpitDisplayBlockEntity.Link;
import dev.aerolarge.block.CockpitKeys;
import dev.aerolarge.block.CockpitKeys.Function;
import dev.aerolarge.block.CockpitKeys.Key;
import dev.aerolarge.block.DropReleaseBlock;
import dev.aerolarge.block.GearLeverBlock;
import dev.aerolarge.block.JumpLightBlock;
import dev.aerolarge.block.JumpSwitchBlock;
import dev.aerolarge.block.LaserSwitchBlock;
import dev.aerolarge.block.ParkingBrakeBlock;
import dev.aerolarge.block.SeatbeltSwitchBlock;
import dev.aerolarge.block.WindowShadesBlock;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

/**
 * Draws the touch keys of the flight deck's curved row (see {@link dev.aerolarge.block.CockpitKeys}), each a picture,
 * so that whoever cannot read can still work them. Every key is a touch panel's tile (see {@link TouchPanelArt}): the
 * one under the pointer brightens, and one that is on, or the camera on the screen, is picked. The systems screen's
 * tiles show a picture of what each works, as it is now (a seated passenger buckled in, a light bulb or the moon, a
 * skydiver beside the jump light, a window with its shade up or down, the laser turret firing, the drop bay shut or
 * dropping), tinted in its colour with the light along its bottom lit while on. On the sloped panel under the screens:
 * the parking brake as a wheel gripped red while it is set; the landing gear as a wheel with an arrow the way its lever
 * is thrown and its three lamps; and the outside cameras' buttons, each the airliner from the side with a big dot where
 * its camera is, the one on the screen lit. The autopilot's desk and the nose wheel tiller have drawings of their own
 * (see {@link AutopilotDeskArt} and {@link TillerArt}). The switches' states are read from their blocks out of sight
 * under the floor.
 */
final class CockpitKeysArt {
    /** Each layer lifted off the one below, in blocks. */
    private static final float LAYER = 0.002F;
    /**
     * A lit systems tile: how much of its colour its face takes on, and how tall the light along its bottom is, as a
     * share of the tile's height (lit, on an airliner's push button, dark while off), and how far up from the tile's
     * bottom edge the room for it starts, in blocks.
     */
    private static final float LIT_TINT = 0.3F;
    private static final double ON_LIGHT = 0.12;
    private static final double LIGHT_MARGIN = 0.03;
    /** Segments a ring is drawn with. */
    private static final int RING_SEGMENTS = 20;

    private static final int KEY = AutopilotScreenRenderer.BUTTON;
    private static final int WHITE = AutopilotScreenRenderer.WHITE;
    private static final int GREY = AutopilotScreenRenderer.GREY;
    private static final int AMBER = AutopilotScreenRenderer.AMBER;
    private static final int GREEN = AutopilotScreenRenderer.GREEN;
    private static final int RED = PanelArt.RED;
    private static final int NIGHT_BLUE = 0xFF4A78E0;
    /** The cabin lights' tile lit bright: a warm pale yellow, apart from the white a key under the pointer turns to. */
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

    /** What a systems tile shows: the picture of its switch as it is now, and the colour it is lit in, 0 while off. */
    private record Face(ResourceLocation picture, int lit) {
    }

    private CockpitKeysArt() {
    }

    /**
     * The systems screen's tiles, on the screen's face from its bottom left corner, the one the pointer rests on, if it
     * rests on the screen, brightened.
     */
    static void tiles(CockpitDisplayBlockEntity display, Level level, MultiBufferSource buffers, Matrix4f matrix,
                      @Nullable CockpitDisplayRenderer.Pointer pointer) {
        CockpitKeys.Keys keys = display.screenKeys();
        Key looked = CockpitDisplayRenderer.Pointer.key(pointer, false, keys);
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        for (Key key : keys.list()) {
            systemsTile(shapes, matrix, key, face(key.function(), state(display, level, key.function().link)).lit(), key.equals(looked));
        }
        // Pictures last: each takes its own buffer, which ends the one the shapes went into.
        for (Key key : keys.list()) {
            Face face = face(key.function(), state(display, level, key.function().link));
            picture(buffers, matrix, face.picture(), overLight(key.box()), 0.8, face.lit() != 0 ? PICTURE_ON : PICTURE_OFF);
        }
    }

    /**
     * The keys on the sloped panel under the screen, on the slope from its front left corner as it would be uncut, the
     * one the pointer rests on, if it rests on the slope, brightened.
     */
    static void slope(CockpitDisplayBlockEntity display, Level level, MultiBufferSource buffers, Matrix4f matrix,
                      @Nullable CockpitDisplayRenderer.Pointer pointer) {
        CockpitKeys.Keys keys = display.slopeKeys();
        Key looked = CockpitDisplayRenderer.Pointer.key(pointer, true, keys);
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        for (Key key : keys.list()) {
            slopeKey(shapes, matrix, key, key.equals(looked), display, level);
        }
    }

    /**
     * A key on the slope on its tile, and what it shows: a camera's button, the one on the screen picked and lit and
     * the others dimmed, as the camera display's buttons are; or the brake, picked while it is set, or the gear.
     */
    private static void slopeKey(VertexConsumer shapes, Matrix4f matrix, Key key, boolean looked, CockpitDisplayBlockEntity display, Level level) {
        OutsideCamera camera = key.function().camera;
        if (camera != null) {
            boolean shown = camera == display.camera();
            TailCameraDisplayRenderer.cameraButton(shapes, matrix, camera, key.tile(), new TouchPanelArt.Look(looked, shown, shown), LAYER);
            return;
        }
        boolean set = key.function() == Function.BRAKE && on(state(display, level, Link.BRAKE), ParkingBrakeBlock.SET);
        TouchPanelArt.tile(shapes, matrix, key.tile(), KEY, new TouchPanelArt.Look(looked, set, true), LAYER);
        drawn(shapes, matrix, key, display, level);
    }

    /** What the block a link leads to is now; air where the panel has no such link. */
    private static BlockState state(CockpitDisplayBlockEntity display, Level level, Link link) {
        BlockPos at = display.linked(link);
        return at == null ? Blocks.AIR.defaultBlockState() : level.getBlockState(at);
    }

    private static boolean on(BlockState state, Property<Boolean> property) {
        return state.hasProperty(property) && state.getValue(property);
    }

    /** What a systems tile shows, by the switch it works and that switch's state. */
    private static Face face(Function function, BlockState state) {
        return switch (function) {
            case CABIN_LIGHTS -> cabinLights(state);
            case SEATBELTS -> new Face(SEATBELTS, on(state, SeatbeltSwitchBlock.ON) ? AMBER : 0);
            case JUMP_LIGHTS -> jumpLights(state);
            case SHADES -> on(state, WindowShadesBlock.SHADED) ? new Face(SHADES_CLOSED, AMBER) : new Face(SHADES_OPEN, 0);
            case LASER -> new Face(LASER, on(state, LaserSwitchBlock.ARMED) ? RED : 0);
            case DROP -> on(state, DropReleaseBlock.ARMED) ? new Face(DROP_ARMED, RED) : new Face(DROP_SAFE, 0);
            default -> new Face(SEATBELTS, 0);
        };
    }

    /** The dimmer: lit pale yellow while bright, amber while dimmed and blue at night. */
    private static Face cabinLights(BlockState state) {
        Brightness brightness = state.hasProperty(Brightness.PROPERTY) ? state.getValue(Brightness.PROPERTY) : Brightness.BRIGHT;
        int lit = switch (brightness) {
            case BRIGHT -> BRIGHT;
            case DIM -> AMBER;
            case NIGHT -> NIGHT_BLUE;
        };
        return new Face(CABIN_LIGHTS[brightness.ordinal()], lit);
    }

    /** The jump light switch: lit red to stand by and green to go. */
    private static Face jumpLights(BlockState state) {
        JumpLightBlock.Signal signal = state.hasProperty(JumpSwitchBlock.SIGNAL) ? state.getValue(JumpSwitchBlock.SIGNAL) : JumpLightBlock.Signal.OFF;
        int lit = switch (signal) {
            case OFF -> 0;
            case RED -> RED;
            case GREEN -> GREEN;
        };
        return new Face(JUMP_LIGHTS[signal.ordinal()], lit);
    }

    /** What the brake's or the gear's key shows over its tile, in shapes. */
    private static void drawn(VertexConsumer shapes, Matrix4f matrix, Key key, CockpitDisplayBlockEntity display, Level level) {
        Box box = key.box();
        double cx = (box.x0() + box.x1()) / 2;
        double cy = (box.y0() + box.y1()) / 2;
        double size = Math.min(box.x1() - box.x0(), box.y1() - box.y0());
        switch (key.function()) {
            case BRAKE -> brake(shapes, matrix, cx, cy, size * 0.32, on(state(display, level, Link.BRAKE), ParkingBrakeBlock.SET));
            case GEAR -> gear(shapes, matrix, box, state(display, level, Link.GEAR));
            default -> {
            }
        }
    }

    /**
     * A systems tile in a colour, 0 while off, on its tile as every touch panel's are, brightened while the pointer
     * rests on it: on, its face tinted in the colour, the light along its bottom lit and the tile picked, so on and off
     * differ at a glance.
     */
    private static void systemsTile(VertexConsumer shapes, Matrix4f matrix, Key key, int lit, boolean looked) {
        boolean on = lit != 0;
        TouchPanelArt.tile(shapes, matrix, key.tile(), on ? PanelArt.blend(KEY, lit, LIT_TINT) : KEY, new TouchPanelArt.Look(looked, on, true), LAYER);
        Box box = key.box();
        double light = (box.y1() - box.y0()) * ON_LIGHT;
        double inset = (box.x1() - box.x0()) * 0.25;
        double bottom = box.y0() + LIGHT_MARGIN + light * 0.4;
        ScreenDrawing.rect(shapes, matrix, box.x0() + inset, bottom, box.x1() - inset, bottom + light, 3 * LAYER, on ? lit : LAMP_OFF);
    }

    /** The part of a systems tile over the light along its bottom, for its picture. */
    private static Box overLight(Box box) {
        double light = (box.y1() - box.y0()) * ON_LIGHT;
        return new Box(box.x0(), box.y0() + LIGHT_MARGIN + light * 1.6, box.x1(), box.y1());
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
        double half = Math.min(box.x1() - box.x0(), box.y1() - box.y0()) * share / 2;
        ScreenDrawing.picture(buffers, matrix, texture, (box.x0() + box.x1()) / 2, (box.y0() + box.y1()) / 2, half, 3 * LAYER, tint);
    }

    private static ResourceLocation picture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/cockpit/" + name + ".png");
    }
}
