package dev.skyliners.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.block.AutopilotScreenBlockEntity.Box;
import dev.skyliners.block.CockpitDisplayBlockEntity;
import dev.skyliners.block.CockpitDisplayBlockEntity.Link;
import dev.skyliners.block.CockpitKeys;
import dev.skyliners.block.CockpitKeys.Function;
import dev.skyliners.block.CockpitKeys.Key;
import dev.skyliners.block.CockpitKeys.Taxi;
import dev.skyliners.block.TillerBlock;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Draws the nose wheel tiller on the flight deck's sloped touch panel (see {@link dev.skyliners.block.CockpitKeys}) as
 * the ground it steers on, seen from above, so that whoever cannot read can taxi too: dark tarmac between strips of
 * grass, the airliner's white nose at the bottom with its nose wheel's black tyre turned the way it is set, and five
 * yellow taxi lines fanning out from the wheel, a hard curl and a gentle bend either way and straight on between them.
 * The line set is bright and solid, the others faint and dashed; touched, a line is set at once. Pointed at, a line
 * brightens before it is touched, and the tarmac with it, as every touch panel's tiles do (see {@link TouchPanelArt}).
 * While the airliner taxis, white dashes run along the line it follows at its speed,
 * and with the wheel turned the faint straight line pulses, so it is not left turned by mistake. A green dot at the
 * foot of the straight line shows the wheel is straight. Nothing here is the autopilot's cyan or magenta: this is the
 * ground.
 */
final class TillerArt {
    /** Each layer lifted off the one below, in blocks. */
    private static final float LAYER = 0.002F;
    /**
     * The layers it is drawn in, one over the other: the key's tile, the tarmac, as every touch panel's is (see {@link
     * TouchPanelArt}); the grass; the faint lines; the line set; the line pointed at; the dashes running along it; the
     * nose; the tyre and the dot at the straight line's foot.
     */
    private static final float TARMAC_LAYER = LAYER;
    private static final float GRASS_LAYER = 3 * LAYER;
    private static final float FAINT = 4 * LAYER;
    private static final float SET = 5 * LAYER;
    private static final float POINTED = 6 * LAYER;
    private static final float DASHES = 7 * LAYER;
    private static final float NOSE_LAYER = 8 * LAYER;
    private static final float TYRE_LAYER = 9 * LAYER;
    /** The grass along either end of the key, in blocks. */
    private static final double GRASS_WIDTH = 0.05;
    /**
     * The taxi lines, in blocks: how wide the one set, a faint one and the faint straight line are; and the faint ones'
     * dashes, each so long and so far apart.
     */
    private static final double SET_WIDTH = 0.024;
    private static final double FAINT_WIDTH = 0.012;
    private static final double STRAIGHT_WIDTH = 0.016;
    private static final double FAINT_DASH = 0.035;
    private static final double FAINT_GAP = 0.025;
    /**
     * The dashes running along the line set while the airliner rolls: each so long and so far apart, in blocks, and
     * how far they run for each block the airliner rolls; and the speeds it counts as taxiing between, in blocks a
     * second: any slower it stands still, any faster it is taking off or flying, where the nose wheel steers nothing
     * (it flies no slower than the autopilot's slowest speed).
     */
    private static final double RUN_DASH = 0.03;
    private static final double RUN_GAP = 0.05;
    private static final double RUN_SCALE = 0.02;
    private static final double ROLLING = 0.3;
    private static final double TAXI_FASTEST = 25;
    /** How wide the running dashes are, as a share of the line set's width. */
    private static final double RUN_WIDTH = 0.5;
    /** One pulse of the straight line while the wheel is turned and the airliner rolls, in milliseconds. */
    private static final float PULSE_PERIOD = 1200;
    /**
     * The airliner's nose, in blocks: half its width, and how far up the key its tip comes; the tyre, half its width
     * and half its length; the share of the line's bend the wheel is drawn turned by; and the green dot's radius, and
     * the share of the way up the straight line it sits.
     */
    private static final double NOSE_HALF = 0.08;
    private static final double NOSE_TIP = 0.17;
    private static final double TYRE_HALF_WIDTH = 0.016;
    private static final double TYRE_HALF_LENGTH = 0.03;
    private static final double WHEEL_TURN = 0.6;
    private static final double HOME_DOT = 0.016;
    private static final double HOME_DOT_ALONG = 0.25;
    /** Segments a round end is drawn with. */
    private static final int ROUND = 16;

    private static final int TARMAC = 0xFF2B2F35;
    private static final int GRASS = 0xFF3F7D3A;
    private static final int YELLOW = 0xFFF2C830;
    private static final int FAINT_YELLOW = 0x70F2C830;
    private static final int POINTED_YELLOW = 0xC0FFE680;
    private static final int RUNNING = 0xCCFFFFFF;
    private static final int NOSE = 0xFFE8ECF0;
    private static final int TYRE = 0xFF15171B;
    private static final int HOME = AutopilotScreenRenderer.GREEN;

    private TillerArt() {
    }

    /** The tiller's key on the slope, from its front left corner. */
    static void draw(CockpitDisplayBlockEntity display, Level level, MultiBufferSource buffers, Matrix4f matrix, float partialTick,
                     @Nullable CockpitDisplayRenderer.Pointer pointer) {
        CockpitKeys.Keys keys = display.slopeKeys();
        Key key = keys.list().stream().filter(each -> each.function() == Function.TILLER).findFirst().orElse(null);
        if (key == null) {
            return;
        }
        Box box = key.box();
        Taxi taxi = Taxi.on(box);
        int steer = steer(display, level);
        boolean looked = key.equals(CockpitDisplayRenderer.Pointer.key(pointer, true, keys));
        Integer pointed = looked ? taxi.steer(pointer.across(), pointer.up()) : null;
        SableClient.Attitude attitude = SableClient.attitude(display, display.facing().getOpposite(), partialTick);
        double speed = attitude == null ? 0 : attitude.airspeed();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        TouchPanelArt.tile(shapes, matrix, key.tile(), TARMAC, new TouchPanelArt.Look(looked, false, true), TARMAC_LAYER);
        ScreenDrawing.rect(shapes, matrix, box.x0(), box.y0(), box.x0() + GRASS_WIDTH, box.y1(), GRASS_LAYER, GRASS);
        ScreenDrawing.rect(shapes, matrix, box.x1() - GRASS_WIDTH, box.y0(), box.x1(), box.y1(), GRASS_LAYER, GRASS);
        boolean rolling = Math.abs(speed) > ROLLING && Math.abs(speed) < TAXI_FASTEST;
        for (int each : TillerBlock.STEER.getPossibleValues()) {
            faint(shapes, matrix, taxi, each, steer, rolling);
        }
        line(shapes, matrix, taxi, steer, SET_WIDTH, SET, YELLOW);
        if (pointed != null && pointed != steer) {
            line(shapes, matrix, taxi, pointed, SET_WIDTH, POINTED, POINTED_YELLOW);
        }
        if (rolling) {
            running(shapes, matrix, taxi, steer, speed);
        }
        nose(shapes, matrix, taxi, box);
        tyre(shapes, matrix, taxi, steer);
        if (steer == TillerBlock.CENTRE) {
            double[] foot = taxi.point(TillerBlock.CENTRE, HOME_DOT_ALONG);
            ScreenDrawing.disc(shapes, matrix, foot[0], foot[1], HOME_DOT, ROUND, TYRE_LAYER, HOME);
        }
    }

    /** Where the tiller the panel works is set, hard left (0) to hard right (4); straight ahead with none linked. */
    private static int steer(CockpitDisplayBlockEntity display, Level level) {
        BlockPos at = display.linked(Link.TILLER);
        BlockState tiller = at == null ? null : level.getBlockState(at);
        return tiller != null && tiller.hasProperty(TillerBlock.STEER) ? tiller.getValue(TillerBlock.STEER) : TillerBlock.CENTRE;
    }

    /**
     * A taxi line not set, faint and dashed: the straight one a little wider, and pulsing while the wheel is turned and
     * the airliner rolls.
     */
    private static void faint(VertexConsumer shapes, Matrix4f matrix, Taxi taxi, int line, int steer, boolean rolling) {
        if (line == steer) {
            return;
        }
        boolean straight = line == TillerBlock.CENTRE;
        int colour = FAINT_YELLOW;
        if (straight && rolling) {
            colour = PanelArt.blend(FAINT_YELLOW, YELLOW, PanelArt.pulse(PULSE_PERIOD));
        }
        dashed(shapes, matrix, taxi, line, 0, FAINT_DASH, FAINT_GAP, straight ? STRAIGHT_WIDTH : FAINT_WIDTH, FAINT, colour);
    }

    /** A taxi line, solid. */
    private static void line(VertexConsumer shapes, Matrix4f matrix, Taxi taxi, int line, double width, float z, int colour) {
        for (int k = 0; k < Taxi.PIECES; k++) {
            double[] from = taxi.point(line, (double) k / Taxi.PIECES);
            double[] to = taxi.point(line, (double) (k + 1) / Taxi.PIECES);
            ScreenDrawing.line(shapes, matrix, from[0], from[1], to[0], to[1], z, (float) width, colour);
        }
    }

    /** White dashes running along the line set, as far along as the airliner has rolled. */
    private static void running(VertexConsumer shapes, Matrix4f matrix, Taxi taxi, int steer, double speed) {
        double travelled = Util.getMillis() / 1000.0 * speed * RUN_SCALE;
        dashed(shapes, matrix, taxi, steer, travelled, RUN_DASH, RUN_GAP, SET_WIDTH * RUN_WIDTH, DASHES, RUNNING);
    }

    /**
     * A taxi line in dashes of a length with gaps between, the first starting a distance along it, measured along the
     * line, in blocks.
     */
    private static void dashed(VertexConsumer shapes, Matrix4f matrix, Taxi taxi, int line, double offset, double dash, double gap, double width, float z,
                               int colour) {
        double period = dash + gap;
        double along = 0;
        double[] from = taxi.point(line, 0);
        for (int k = 1; k <= Taxi.PIECES; k++) {
            double[] to = taxi.point(line, (double) k / Taxi.PIECES);
            double length = Math.hypot(to[0] - from[0], to[1] - from[1]);
            if (Mth.positiveModulo(along + length / 2 - offset, period) < dash) {
                ScreenDrawing.line(shapes, matrix, from[0], from[1], to[0], to[1], z, (float) width, colour);
            }
            along += length;
            from = to;
        }
    }

    /** The airliner's nose from above: white, rounded at its tip, rising from the key's bottom edge over the wheel. */
    private static void nose(VertexConsumer shapes, Matrix4f matrix, Taxi taxi, Box box) {
        double tipY = box.y0() + NOSE_TIP;
        double roundY = tipY - NOSE_HALF;
        ScreenDrawing.rect(shapes, matrix, taxi.x() - NOSE_HALF, box.y0(), taxi.x() + NOSE_HALF, roundY, NOSE_LAYER, NOSE);
        for (int k = 0; k < ROUND; k++) {
            double a0 = Math.PI * k / ROUND;
            double a1 = Math.PI * (k + 1) / ROUND;
            triangle(shapes, matrix, taxi.x(), roundY, taxi.x() + Math.cos(a0) * NOSE_HALF, roundY + Math.sin(a0) * NOSE_HALF,
                taxi.x() + Math.cos(a1) * NOSE_HALF, roundY + Math.sin(a1) * NOSE_HALF, NOSE_LAYER, NOSE);
        }
    }

    /** The nose wheel's tyre, black, on the nose, turned part of the way its line bends. */
    private static void tyre(VertexConsumer shapes, Matrix4f matrix, Taxi taxi, int steer) {
        double turn = Math.toRadians(Taxi.bend(steer) * WHEEL_TURN);
        double alongX = Math.sin(turn) * TYRE_HALF_LENGTH;
        double alongY = Math.cos(turn) * TYRE_HALF_LENGTH;
        double acrossX = Math.cos(turn) * TYRE_HALF_WIDTH;
        double acrossY = -Math.sin(turn) * TYRE_HALF_WIDTH;
        double x = taxi.x();
        double y = taxi.y();
        // Anticlockwise, as every shape on the panel is wound.
        ScreenDrawing.vertex(shapes, matrix, x - alongX - acrossX, y - alongY - acrossY, TYRE_LAYER, TYRE);
        ScreenDrawing.vertex(shapes, matrix, x - alongX + acrossX, y - alongY + acrossY, TYRE_LAYER, TYRE);
        ScreenDrawing.vertex(shapes, matrix, x + alongX + acrossX, y + alongY + acrossY, TYRE_LAYER, TYRE);
        ScreenDrawing.vertex(shapes, matrix, x + alongX - acrossX, y + alongY - acrossY, TYRE_LAYER, TYRE);
    }

    /** A filled triangle, its corners given anticlockwise, the last given twice to make the shapes' four corners. */
    private static void triangle(VertexConsumer shapes, Matrix4f matrix, double ax, double ay, double bx, double by, double cx, double cy, float z, int colour) {
        ScreenDrawing.vertex(shapes, matrix, ax, ay, z, colour);
        ScreenDrawing.vertex(shapes, matrix, bx, by, z, colour);
        ScreenDrawing.vertex(shapes, matrix, cx, cy, z, colour);
        ScreenDrawing.vertex(shapes, matrix, cx, cy, z, colour);
    }
}
