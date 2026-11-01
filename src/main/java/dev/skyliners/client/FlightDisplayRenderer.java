package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.RocketControls;
import dev.skyliners.block.FlightDisplayBlock;
import dev.skyliners.block.FlightDisplayBlockEntity;
import dev.skyliners.client.PanelArt.Pictures;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the primary flight display on the screen face of its block: an attitude indicator (blue sky over brown
 * ground, the horizon tilted against the bank and moved with the pitch, a pitch ladder, the yellow aircraft symbol)
 * with airspeed on the left, altitude on the right, vertical speed under it and the heading at the bottom. The values
 * come from the airliner's pose as the client draws it, so the display moves as smoothly as the airliner does. On an
 * airliner or zeppelin that is not assembled it shows a standby screen. It is only drawn with the camera in front of
 * it.
 *
 * <p>On a rocket, a row of pictures along its top shows her centre console's controls (see {@link RocketControls}),
 * for whoever cannot read: a flame for the boost, dim while it waits, pulsing while she boosts and ringed by four
 * segments lighting one by one while it rests; a rocket for her stability, green and steady while her jets hold her
 * upright, amber while it stays locked on, red and wobbling with a warning triangle beside it while it is off; and the
 * picture of her flight mode, dim while she flies it straight up instead. While her stability is off, a red ring on the
 * attitude indicator marks how far she may tilt before it snaps back on, and a dot inside it how far she tilts, and
 * which way: keep the dot inside the ring.
 */
public class FlightDisplayRenderer implements BlockEntityRenderer<FlightDisplayBlockEntity> {
    /** The screen's half width and half height on the block face, in blocks. */
    static final float HALF_WIDTH = 0.44F;
    static final float HALF_HEIGHT = 0.36F;
    /** The attitude indicator's half size, how far it sits up from the screen's middle, and blocks per degree of pitch. */
    private static final float ADI = 0.2F;
    private static final float ADI_RAISE = 0.04F;
    private static final float PER_DEGREE = 0.009F;
    /** Text size: blocks per font pixel. */
    private static final float TEXT = 1 / 180F;
    /**
     * The standby screen's picture of what to press, for whoever cannot read its words: how far up its middle is from
     * the screen's, and how far out it reaches each way, in blocks.
     */
    private static final float STANDBY_PICTURE_Y = 0.17F;
    private static final float STANDBY_PICTURE = 0.13F;
    /**
     * What to press to fly while the airliner stands as blocks: the picture on the autopilot's on/off key, whose press
     * has the Physics Assembler assemble the airliner and takes off. A zeppelin's display shows the zeppelin's own.
     */
    static final ResourceLocation ASSEMBLE = Pictures.AIRLINER.cruise().texture();
    /**
     * A rocket's controls' pictures, white to be tinted, by name (see tools/generate_textures.mjs), as her centre
     * console's plates show them: her lift's own arrow straight up, and the rest of their own.
     */
    private static final Map<String, ResourceLocation> CONTROL_PICTURES = Map.of(
        "flame", picture("rocket_controls/flame"), "steady", picture("rocket_controls/steady"), "warning", picture("rocket_controls/warning"),
        "vertical", picture("lift/up"), "arc", picture("rocket_controls/arc"), "gentle", picture("rocket_controls/gentle"));
    /** Lifts the drawing just off the block face so it does not flicker against it, and the text off the drawing. */
    private static final float SURFACE = 0.502F;
    private static final float TEXT_LIFT = 0.005F;
    /** The static after a lightning strike, in specks across and up the screen. */
    private static final int NOISE_COLUMNS = 16;
    private static final int NOISE_ROWS = 12;

    private static final int BLACK = 0xFF0A0C10;
    private static final int SKY = 0xFF2D6FD1;
    private static final int GROUND = 0xFF7A4E26;
    private static final int WHITE = 0xFFF2F2F2;
    private static final int YELLOW = 0xFFF2CF30;
    private static final int GREEN = 0xFF46E070;
    /**
     * A rocket's controls' pictures along the top: how high their middles are, how far apart, and how far out each
     * reaches, in blocks; how far the warning triangle stands right of the rocket, and the ring of the boost's rest
     * round the flame, how far out and how thick, in blocks.
     */
    private static final float CONTROLS_Y = 0.30F;
    private static final float CONTROLS_APART = 0.13F;
    private static final float CONTROL = 0.04F;
    private static final float WARNING_RIGHT = 0.065F;
    private static final float REST_RING = 0.052F;
    private static final float REST_RING_THICK = 0.008F;
    /**
     * How fast the boosting flame pulses and the unstable rocket wobbles, in turns a second, and how far it wobbles, in
     * degrees; and how many ticks their swings are counted over, a whole number of turns of each, so that a world's
     * long-running game time never blurs them.
     */
    private static final float PULSE_TURNS = 3;
    private static final float WOBBLE_TURNS = 1.5F;
    private static final float WOBBLE_DEGREES = 12;
    private static final int SWING_TICKS = 1200;
    /** How often the warning triangle blinks, in ticks lit and as many dark. */
    private static final int WARNING_BLINK = 6;
    /**
     * The tilt ring with stability off: how thick, in blocks, how many pieces round, and the dot's radius, in blocks.
     */
    private static final float TILT_RING_THICK = 0.006F;
    private static final int TILT_RING_PIECES = 32;
    private static final float TILT_DOT = 0.012F;
    /** How far a dimmed picture is darkened, as a share of its colour kept. */
    private static final float DIMMED = 0.35F;
    private static final int FLAME_IDLE = 0xFF8A5A2E;
    private static final int FLAME_LIT = 0xFFFF9A2A;
    private static final int FLAME_HOT = 0xFFFFEE90;
    private static final int FLAME_RESTING = 0xFFC03424;
    private static final int RED = 0xFFE83030;
    private static final int AMBER = 0xFFF0A830;
    private static final int UNLIT = 0xFF30343A;

    /** Only with the camera in front of the screen, which is drawn from the front only, and as far off as ever. */
    @Override
    public boolean shouldRender(FlightDisplayBlockEntity display, Vec3 camera) {
        Direction screen = display.getBlockState().getValue(FlightDisplayBlock.FACING);
        return Sight.facing(display.getBlockPos(), screen, SURFACE, camera, getViewDistance());
    }

    @Override
    public void render(FlightDisplayBlockEntity display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction screen = display.getBlockState().getValue(FlightDisplayBlock.FACING);
        SableClient.Attitude attitude = SableClient.attitude(display, screen.getOpposite(), partialTick);
        Level level = display.getLevel();
        AutopilotBlockEntity autopilot = level != null ? AutopilotBlockEntity.nearest(level, display.getBlockPos()) : null;

        pose.pushPose();
        // Onto the screen face, x to the viewer's right and y up.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-screen.toYRot()));
        pose.translate(0, 0, SURFACE);
        long staticSeed = level == null ? 0 : display.getBlockPos().asLong() + level.getGameTime();
        draw(pose, buffers, attitude, staticSeed, standby(autopilot));
        if (attitude != null && autopilot != null && autopilot.craft() == Craft.ROCKET && level != null) {
            long now = level.getGameTime();
            rocketControls(pose, buffers, autopilot, attitude, now, (float) (DrawingClock.ticks(partialTick) % SWING_TICKS));
        }
        pose.popPose();
    }

    /** The standby screen's picture of what to press: the one on the nearby autopilot's on/off key, if there is one. */
    private static ResourceLocation standby(@Nullable AutopilotBlockEntity autopilot) {
        return autopilot != null ? Pictures.of(autopilot.craft()).cruise().texture() : ASSEMBLE;
    }

    /**
     * A rocket's console controls along the top of her display, at a game time, their swings at so many ticks into
     * their round (see {@link #SWING_TICKS}): the flame of her boost, the rocket of her stability and the picture of
     * her flight mode; with her stability off, the tilt ring on the attitude indicator. Every shape first, in one
     * buffer, then the pictures, each taking its own.
     */
    private static void rocketControls(PoseStack pose, MultiBufferSource buffers, AutopilotBlockEntity rocket, SableClient.Attitude attitude, long now,
                                       float swing) {
        Matrix4f matrix = pose.last().pose();
        RocketControls controls = rocket.rocketControls();
        VertexConsumer fill = ScreenDrawing.shapes(buffers);
        if (controls.cooling(now)) {
            restRing(fill, matrix, controls.rested(now));
        }
        if (controls.unstable()) {
            tiltRing(fill, matrix, attitude);
        }
        ScreenDrawing.picture(buffers, matrix, CONTROL_PICTURES.get("flame"), -CONTROLS_APART, CONTROLS_Y, CONTROL, TEXT_LIFT, flame(controls, now, swing));
        stability(pose, buffers, controls, now, swing);
        boolean straightUp = controls.mode() == RocketControls.Mode.ARC
            && !RocketControls.arcFlown(rocket.lateral() == AutopilotBlockEntity.Lateral.DESTINATION);
        int colour = controls.mode().colour();
        ScreenDrawing.picture(buffers, matrix, CONTROL_PICTURES.get(controls.mode().getSerializedName()), CONTROLS_APART, CONTROLS_Y, CONTROL,
            TEXT_LIFT, straightUp ? dimmed(colour) : colour);
    }

    /**
     * The boost's flame's colour: pulsing from orange to white-hot while she boosts, red while it rests, dim waiting.
     */
    private static int flame(RocketControls controls, long now, float swing) {
        if (controls.boosting(now)) {
            float pulse = (Mth.sin(swing / 20 * Mth.TWO_PI * PULSE_TURNS) + 1) / 2;
            return FastColor.ARGB32.lerp(pulse, FLAME_LIT, FLAME_HOT);
        }
        return controls.cooling(now) ? FLAME_RESTING : FLAME_IDLE;
    }

    /** The ring round the flame while the boost rests: four sides, lit green one by one, clockwise from the top. */
    private static void restRing(VertexConsumer fill, Matrix4f matrix, double rested) {
        float x = -CONTROLS_APART;
        float r = REST_RING;
        float[][] sides = {{x - r, CONTROLS_Y + r, x + r, CONTROLS_Y + r}, {x + r, CONTROLS_Y + r, x + r, CONTROLS_Y - r},
            {x + r, CONTROLS_Y - r, x - r, CONTROLS_Y - r}, {x - r, CONTROLS_Y - r, x - r, CONTROLS_Y + r}};
        for (int side = 0; side < sides.length; side++) {
            int colour = rested >= (side + 1.0) / sides.length ? GREEN : UNLIT;
            float[] at = sides[side];
            ScreenDrawing.line(fill, matrix, at[0], at[1], at[2], at[3], TEXT_LIFT / 2, REST_RING_THICK, colour);
        }
    }

    /**
     * Her stability's rocket with her side jets puffing: green holding her, amber locked on, red and wobbling while
     * off, with a warning triangle blinking beside it.
     */
    private static void stability(PoseStack pose, MultiBufferSource buffers, RocketControls controls, long now, float swing) {
        boolean off = controls.unstable();
        int colour = off ? RED : controls.lockedOut(now) ? AMBER : GREEN;
        pose.pushPose();
        pose.translate(0, CONTROLS_Y, 0);
        if (off) {
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(swing / 20 * Mth.TWO_PI * WOBBLE_TURNS) * WOBBLE_DEGREES));
        }
        ScreenDrawing.picture(buffers, pose.last().pose(), CONTROL_PICTURES.get("steady"), 0, 0, CONTROL, TEXT_LIFT, colour);
        pose.popPose();
        if (off && now / WARNING_BLINK % 2 == 0) {
            ScreenDrawing.picture(buffers, pose.last().pose(), CONTROL_PICTURES.get("warning"), WARNING_RIGHT, CONTROLS_Y - CONTROL / 2, CONTROL / 2,
                TEXT_LIFT, YELLOW);
        }
    }

    /**
     * The tilt ring with her stability off: a red ring round the attitude indicator's middle, as far out as she may
     * tilt before it snaps back on, and a white dot inside it, as far out as she tilts, the way she tilts: ahead up,
     * right to the right.
     */
    private static void tiltRing(VertexConsumer fill, Matrix4f matrix, SableClient.Attitude attitude) {
        float radius = (float) (RocketControls.SNAP_TILT * PER_DEGREE);
        for (int piece = 0; piece < TILT_RING_PIECES; piece++) {
            double a = Mth.TWO_PI * piece / TILT_RING_PIECES;
            double b = Mth.TWO_PI * (piece + 1) / TILT_RING_PIECES;
            ScreenDrawing.line(fill, matrix, Math.sin(a) * radius, ADI_RAISE + Math.cos(a) * radius, Math.sin(b) * radius, ADI_RAISE + Math.cos(b) * radius,
                TEXT_LIFT, TILT_RING_THICK, RED);
        }
        double x = Mth.clamp(attitude.roll() * PER_DEGREE, -ADI, ADI);
        double y = ADI_RAISE + Mth.clamp(-attitude.pitch() * PER_DEGREE, -ADI, ADI);
        ScreenDrawing.disc(fill, matrix, x, y, TILT_DOT, TILT_RING_PIECES / 2, TEXT_LIFT, WHITE);
    }

    /** One of the gui pictures, by its path under textures/gui without its extension. */
    private static ResourceLocation picture(String path) {
        return ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/gui/" + path + ".png");
    }

    /** A colour darkened, for a picture of what does not apply now. */
    private static int dimmed(int colour) {
        return FastColor.ARGB32.lerp(1 - DIMMED, colour, 0xFF000000);
    }

    /**
     * Draws the display round the pose's origin, {@link #HALF_WIDTH} and {@link #HALF_HEIGHT} out each way and facing
     * out along its z: the standby screen without an attitude to show, the autopilot's start picture given over its
     * words, and static over it all just after lightning struck, its specks picked by the seed.
     */
    static void draw(PoseStack pose, MultiBufferSource buffers, @Nullable SableClient.Attitude attitude, long staticSeed, ResourceLocation standby) {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer fill = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(fill, matrix, -HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH, HALF_HEIGHT, 0, BLACK);

        Font font = Minecraft.getInstance().font;
        if (attitude == null) {
            ScreenDrawing.picture(buffers, matrix, standby, 0, STANDBY_PICTURE_Y, STANDBY_PICTURE, TEXT_LIFT, 0xFFFFFFFF);
            text(font, pose, buffers, "STBY", 0, 0, GREEN);
            text(font, pose, buffers, "ASSEMBLE TO FLY", 0, -0.12F, WHITE);
            return;
        }
        attitudeIndicator(fill, matrix, attitude);

        text(font, pose, buffers, String.valueOf(Math.round(attitude.airspeed())), -0.34F, ADI_RAISE, WHITE);
        text(font, pose, buffers, "SPD", -0.34F, ADI_RAISE + 0.08F, GREEN);
        text(font, pose, buffers, String.valueOf(Math.round(attitude.altitude())), 0.34F, ADI_RAISE, WHITE);
        text(font, pose, buffers, "ALT", 0.34F, ADI_RAISE + 0.08F, GREEN);
        text(font, pose, buffers, String.format(Locale.ROOT, "%+d", Math.round(attitude.verticalSpeed())), 0.34F, ADI_RAISE - 0.09F, GREEN);
        text(font, pose, buffers, String.format(Locale.ROOT, "HDG %03d", Math.round(attitude.heading()) % 360), 0, -0.29F, WHITE);
        if (LightningFlash.screensStatic()) {
            // A buffer of its own: drawing the text has moved the buffers on from the one the screen was drawn into.
            noise(ScreenDrawing.shapes(buffers), matrix, staticSeed);
        }
    }

    /**
     * Static over the whole screen, just after lightning struck: a grid of grey specks of every brightness, different
     * each tick.
     */
    private static void noise(VertexConsumer fill, Matrix4f matrix, long seed) {
        RandomSource random = RandomSource.create(seed);
        float width = 2 * HALF_WIDTH / NOISE_COLUMNS;
        float height = 2 * HALF_HEIGHT / NOISE_ROWS;
        for (int column = 0; column < NOISE_COLUMNS; column++) {
            for (int row = 0; row < NOISE_ROWS; row++) {
                int grey = random.nextInt(256);
                float x = -HALF_WIDTH + column * width;
                float y = -HALF_HEIGHT + row * height;
                ScreenDrawing.rect(fill, matrix, x, y, x + width, y + height, 2 * TEXT_LIFT, 0xFF000000 | grey << 16 | grey << 8 | grey);
            }
        }
    }

    /**
     * Sky and ground split by the horizon: the horizon's up direction turns against the bank, and the horizon moves
     * down as the nose comes up. The ground is the square cut to the part below the horizon.
     */
    private static void attitudeIndicator(VertexConsumer fill, Matrix4f matrix, SableClient.Attitude attitude) {
        double roll = Math.toRadians(attitude.roll());
        float upX = (float) -Math.sin(roll);
        float upY = (float) Math.cos(roll);
        float shift = (float) (-attitude.pitch() * PER_DEGREE);
        float cx = shift * upX;
        float cy = ADI_RAISE + shift * upY;

        ScreenDrawing.rect(fill, matrix, -ADI, ADI_RAISE - ADI, ADI, ADI_RAISE + ADI, 0.001F, SKY);
        List<float[]> ground = clipBelow(new float[][]{
            {-ADI, ADI_RAISE - ADI}, {ADI, ADI_RAISE - ADI}, {ADI, ADI_RAISE + ADI}, {-ADI, ADI_RAISE + ADI}
        }, cx, cy, upX, upY);
        polygon(fill, matrix, ground, 0.002F, GROUND);

        // The horizon line and the pitch ladder every ten degrees, each cut to the indicator.
        for (int degrees = -20; degrees <= 20; degrees += 10) {
            float along = degrees == 0 ? ADI : ADI * 0.35F;
            float offset = (float) (degrees * PER_DEGREE);
            float px = cx + offset * upX;
            float py = cy + offset * upY;
            float thickness = degrees == 0 ? 0.006F : 0.003F;
            List<float[]> rung = clipToBox(new float[][]{
                {px - upY * along - upX * thickness, py + upX * along - upY * thickness},
                {px + upY * along - upX * thickness, py - upX * along - upY * thickness},
                {px + upY * along + upX * thickness, py - upX * along + upY * thickness},
                {px - upY * along + upX * thickness, py + upX * along + upY * thickness}
            });
            polygon(fill, matrix, rung, 0.003F, WHITE);
        }
        // The aircraft symbol: wings either side of a dot in the middle, fixed to the screen.
        ScreenDrawing.rect(fill, matrix, -0.13F, ADI_RAISE - 0.006F, -0.05F, ADI_RAISE + 0.006F, 0.004F, YELLOW);
        ScreenDrawing.rect(fill, matrix, 0.05F, ADI_RAISE - 0.006F, 0.13F, ADI_RAISE + 0.006F, 0.004F, YELLOW);
        ScreenDrawing.rect(fill, matrix, -0.01F, ADI_RAISE - 0.01F, 0.01F, ADI_RAISE + 0.01F, 0.004F, YELLOW);
    }

    /** The part of a convex polygon on the far side of a line from its up direction (Sutherland-Hodgman). */
    private static List<float[]> clipBelow(float[][] polygon, float px, float py, float upX, float upY) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < polygon.length; i++) {
            float[] a = polygon[i];
            float[] b = polygon[(i + 1) % polygon.length];
            float da = (a[0] - px) * upX + (a[1] - py) * upY;
            float db = (b[0] - px) * upX + (b[1] - py) * upY;
            if (da <= 0) {
                out.add(a);
            }
            if (da * db < 0) {
                float t = da / (da - db);
                out.add(new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
            }
        }
        return out;
    }

    /** A polygon cut to the attitude indicator's square. */
    private static List<float[]> clipToBox(float[][] polygon) {
        float[][] current = polygon;
        float[][] sides = {{1, 0, ADI}, {-1, 0, ADI}, {0, 1, ADI_RAISE + ADI}, {0, -1, ADI - ADI_RAISE}};
        for (float[] side : sides) {
            // Keep where side.x * x + side.y * y <= limit: below a line facing that way.
            float px = side[0] * side[2];
            float py = side[1] * side[2];
            List<float[]> clipped = clipBelow(current, px, py, side[0], side[1]);
            current = clipped.toArray(new float[0][]);
            if (current.length == 0) {
                break;
            }
        }
        return List.of(current);
    }

    /** A convex polygon as a fan of quads (the last corner doubled where a quad needs only three). */
    private static void polygon(VertexConsumer fill, Matrix4f matrix, List<float[]> points, float z, int colour) {
        for (int i = 1; i + 1 < points.size(); i += 2) {
            float[] a = points.getFirst();
            float[] b = points.get(i);
            float[] c = points.get(i + 1);
            float[] d = i + 2 < points.size() ? points.get(i + 2) : c;
            ScreenDrawing.vertex(fill, matrix, a[0], a[1], z, colour);
            ScreenDrawing.vertex(fill, matrix, b[0], b[1], z, colour);
            ScreenDrawing.vertex(fill, matrix, c[0], c[1], z, colour);
            ScreenDrawing.vertex(fill, matrix, d[0], d[1], z, colour);
        }
    }

    /** Text centred on (x, y), in the screen's units. */
    private static void text(Font font, PoseStack pose, MultiBufferSource buffers, String text, float x, float y, int colour) {
        ScreenDrawing.text(font, pose, buffers, Component.literal(text), x, y, TEXT_LIFT, TEXT, colour, ScreenDrawing.Align.CENTRE);
    }
}
