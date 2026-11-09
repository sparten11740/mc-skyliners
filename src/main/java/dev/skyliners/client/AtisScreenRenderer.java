package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.airport.Sky;
import dev.skyliners.airport.Wind;
import dev.skyliners.block.AtisScreenBlock;
import dev.skyliners.block.AtisScreenBlockEntity;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws the tower's weather report on its screen, on the radar screen's monitor (see {@link TowerRadarRenderer}),
 * lit as a screen is whatever the light around it: along the top a band with "ATIS" and "INFORMATION" and the report's
 * letter on an amber tile; under it the time of day as a 24-hour clock in green, and beside it a picture of the sky (a
 * sun, or a moon at night; a cloud with rain, snow or a lightning bolt) over its word; and under a rule, a compass dial
 * with an arrow blowing across it from where the wind comes from, beside the wind's bearing and strength (or "CALM")
 * and how far one sees. Drawn only with the camera in front of the screen.
 */
public class AtisScreenRenderer implements BlockEntityRenderer<AtisScreenBlockEntity> {
    private static final float HALF_WIDTH = TowerRadarRenderer.GLASS_HALF_WIDTH;
    private static final float HALF_HEIGHT = TowerRadarRenderer.GLASS_HALF_HEIGHT;
    /** How far in from the glass's edges everything keeps, in blocks. */
    private static final float INSET = 0.03F;
    /** The band along the top: its height, and the letter's tile at its right end, how wide. */
    private static final float BAND = 0.09F;
    private static final float TILE = 0.08F;
    /** Where the clock and the sky's picture sit, how far up from the glass's middle, and the picture's size. */
    private static final float CLOCK_Y = 0.12F;
    private static final float SKY_X = HALF_WIDTH - 0.13F;
    private static final float SKY_SIZE = 0.05F;
    /** The rule across the screen under them, how far down from the glass's middle. */
    private static final float RULE_Y = -0.04F;
    /** The wind's compass dial: its middle and its radius; its arrow's width and its head's length. */
    private static final float DIAL_X = -HALF_WIDTH + 0.13F;
    private static final float DIAL_Y = -0.19F;
    private static final float DIAL = 0.1F;
    private static final float ARROW = 0.008F;
    private static final float ARROW_HEAD = 0.035F;
    /** The readings beside the dial: how far in from the left, and the height of each label and its value. */
    private static final float READING_X = DIAL_X + DIAL + 0.05F;
    private static final float[] READING_Y = {-0.09F, -0.14F, -0.21F, -0.26F};
    /** Slower than this, in knots, the wind is calm. */
    private static final float CALM = 2;
    /** Text sizes, in blocks per font pixel: the labels, the readings, the letter and the clock. */
    private static final float SMALL = 1 / 300F;
    private static final float MEDIUM = 1 / 220F;
    private static final float LETTER = 1 / 160F;
    private static final float CLOCK = 1 / 120F;
    /** Lifts each layer off the one below it, and the text off them all. */
    private static final float LAYER = 0.001F;
    private static final float TEXT_LIFT = 0.005F;
    /** The hours of the night, when the clear sky's picture is the moon. */
    private static final int DUSK = 19;
    private static final int DAWN = 6;
    private static final String KEY = "screen.skyliners.atis.";

    private static final int GLASS = TowerRadarRenderer.GLASS;
    private static final int BAND_COLOUR = 0xFF10254A;
    private static final int RULE = 0xFF1D3A5A;
    private static final int CYAN = 0xFF46C8F0;
    private static final int WHITE = 0xFFF2F2F2;
    private static final int GREY = 0xFF8A93A0;
    private static final int GREEN = 0xFF46E070;
    private static final int AMBER = 0xFFF2CF30;
    private static final int MOON = 0xFFE6E6D2;
    private static final int RAIN_DROPS = 0xFF46A0F0;
    private static final int STORM_CLOUD = 0xFF6A7380;

    @Override
    public boolean shouldRender(AtisScreenBlockEntity screen, Vec3 camera) {
        Direction facing = screen.getBlockState().getValue(AtisScreenBlock.FACING);
        return Sight.facing(screen.getBlockPos(), facing, TowerRadarRenderer.surface(screen.getBlockState()), camera, getViewDistance());
    }

    @Override
    public void render(AtisScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (SpaceWeatherSky.blackedOut()) {
            return;
        }
        Level level = screen.getLevel();
        if (level == null) {
            return;
        }
        Direction facing = screen.getBlockState().getValue(AtisScreenBlock.FACING);
        Sky sky = Sky.at(level, screen.getBlockPos());
        Wind wind = Wind.at(level, partialTick);
        int minutes = minuteOfDay(level.getDayTime());
        pose.pushPose();
        // Onto the screen's face, x to the viewer's right and y up, from the glass's middle.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, TowerRadarRenderer.MIDDLE_Y, TowerRadarRenderer.surface(screen.getBlockState()));
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH, HALF_HEIGHT, 0, GLASS);
        float bandTop = HALF_HEIGHT - INSET;
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH + INSET, bandTop - BAND, HALF_WIDTH - INSET, bandTop, LAYER, BAND_COLOUR);
        ScreenDrawing.rect(shapes, matrix, HALF_WIDTH - INSET - TILE, bandTop - BAND, HALF_WIDTH - INSET, bandTop, 2 * LAYER, AMBER);
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH + INSET, RULE_Y - 0.002, HALF_WIDTH - INSET, RULE_Y + 0.002, LAYER, RULE);
        skyPicture(shapes, matrix, sky, minutes);
        dial(shapes, matrix, wind);
        // Text last: it takes its own buffer, which ends the one the shapes went into.
        text(pose, buffers, screen, sky, wind, minutes);
        pose.popPose();
    }

    /** The minute of the day a day's time is: the day starts at six in the morning, a thousand ticks an hour. */
    private static int minuteOfDay(long dayTime) {
        return (int) ((Math.floorMod(dayTime, 24000L) * 60 / 1000 + DAWN * 60) % (24 * 60));
    }

    /** The report's words: its letter, the time, the sky's word, the wind and how far one sees. */
    private static void text(PoseStack pose, MultiBufferSource buffers, AtisScreenBlockEntity screen, Sky sky, Wind wind, int minutes) {
        Font font = Minecraft.getInstance().font;
        float band = HALF_HEIGHT - INSET - BAND / 2;
        float left = -HALF_WIDTH + 2 * INSET;
        ScreenDrawing.text(font, pose, buffers, Component.translatable(KEY + "title"), left, band, TEXT_LIFT, SMALL, CYAN, ScreenDrawing.Align.LEFT);
        ScreenDrawing.text(font, pose, buffers, Component.translatable(KEY + "information"), HALF_WIDTH - 2 * INSET - TILE, band, TEXT_LIFT, SMALL, WHITE,
            ScreenDrawing.Align.RIGHT);
        ScreenDrawing.text(font, pose, buffers, Component.literal(String.valueOf(screen.letter())), HALF_WIDTH - INSET - TILE / 2, band, TEXT_LIFT, LETTER,
            GLASS, ScreenDrawing.Align.CENTRE);
        String clock = String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
        ScreenDrawing.text(font, pose, buffers, Component.literal(clock), left, CLOCK_Y, TEXT_LIFT, CLOCK, GREEN, ScreenDrawing.Align.LEFT);
        ScreenDrawing.text(font, pose, buffers, sky.title(), SKY_X, CLOCK_Y - 2 * SKY_SIZE, TEXT_LIFT, SMALL, WHITE, ScreenDrawing.Align.CENTRE);
        Component[] readings = {Component.translatable(KEY + "wind"), windReading(wind), Component.translatable(KEY + "visibility"), visibility(sky)};
        for (int i = 0; i < readings.length; i++) {
            boolean label = i % 2 == 0;
            ScreenDrawing.text(font, pose, buffers, readings[i], READING_X, READING_Y[i], TEXT_LIFT, label ? SMALL : MEDIUM, label ? GREY : WHITE,
                ScreenDrawing.Align.LEFT);
        }
    }

    /** The wind as a report gives it: where it comes from to the nearest ten degrees (north 360) and its knots, or calm. */
    private static Component windReading(Wind wind) {
        if (wind.knots() < CALM) {
            return Component.translatable(KEY + "wind.calm");
        }
        int bearing = Math.round(wind.from() / 10) * 10 % 360;
        String from = String.format(Locale.ROOT, "%03d", bearing == 0 ? 360 : bearing);
        return Component.translatable(KEY + "wind.value", from, Math.round(wind.knots()));
    }

    /** How far one sees, in kilometres from ten up, in metres under. */
    private static Component visibility(Sky sky) {
        int metres = sky.visibility();
        return metres >= 10_000 ? Component.translatable(KEY + "visibility.km", metres / 1000) : Component.translatable(KEY + "visibility.metres", metres);
    }

    /** The sky's picture: the sun or, at night, the moon; a cloud with rain or snow under it, or a dark one with a bolt. */
    private static void skyPicture(VertexConsumer shapes, Matrix4f matrix, Sky sky, int minutes) {
        float x = SKY_X;
        float y = CLOCK_Y + SKY_SIZE * 0.2F;
        int hour = minutes / 60;
        switch (sky) {
            case CLEAR -> {
                if (hour >= DUSK || hour < DAWN) {
                    moon(shapes, matrix, x, y);
                } else {
                    sun(shapes, matrix, x, y);
                }
            }
            case RAIN -> {
                cloud(shapes, matrix, x, y + SKY_SIZE * 0.25F, WHITE);
                drops(shapes, matrix, x, y, RAIN_DROPS, false);
            }
            case SNOW -> {
                cloud(shapes, matrix, x, y + SKY_SIZE * 0.25F, WHITE);
                drops(shapes, matrix, x, y, WHITE, true);
            }
            case THUNDER -> {
                cloud(shapes, matrix, x, y + SKY_SIZE * 0.25F, STORM_CLOUD);
                bolt(shapes, matrix, x, y);
            }
        }
    }

    private static void sun(VertexConsumer shapes, Matrix4f matrix, float x, float y) {
        for (int ray = 0; ray < 8; ray++) {
            double angle = Math.PI * ray / 4;
            double sin = Math.sin(angle) * SKY_SIZE;
            double cos = Math.cos(angle) * SKY_SIZE;
            ScreenDrawing.line(shapes, matrix, x + 0.65 * sin, y + 0.65 * cos, x + sin, y + cos, 2 * LAYER, 0.006F, AMBER);
        }
        ScreenDrawing.disc(shapes, matrix, x, y, 0.45 * SKY_SIZE, 24, 2 * LAYER, AMBER);
    }

    /** A crescent: the moon with the night's dark glass over most of it. */
    private static void moon(VertexConsumer shapes, Matrix4f matrix, float x, float y) {
        ScreenDrawing.disc(shapes, matrix, x, y, 0.55 * SKY_SIZE, 24, 2 * LAYER, MOON);
        ScreenDrawing.disc(shapes, matrix, x + 0.25 * SKY_SIZE, y + 0.15 * SKY_SIZE, 0.5 * SKY_SIZE, 24, 3 * LAYER, GLASS);
    }

    /** A cloud: three puffs over a flat bottom. */
    private static void cloud(VertexConsumer shapes, Matrix4f matrix, float x, float y, int colour) {
        ScreenDrawing.disc(shapes, matrix, x - 0.4 * SKY_SIZE, y, 0.35 * SKY_SIZE, 16, 2 * LAYER, colour);
        ScreenDrawing.disc(shapes, matrix, x, y + 0.15 * SKY_SIZE, 0.48 * SKY_SIZE, 16, 2 * LAYER, colour);
        ScreenDrawing.disc(shapes, matrix, x + 0.42 * SKY_SIZE, y, 0.33 * SKY_SIZE, 16, 2 * LAYER, colour);
        ScreenDrawing.rect(shapes, matrix, x - 0.4 * SKY_SIZE, y - 0.33 * SKY_SIZE, x + 0.42 * SKY_SIZE, y, 2 * LAYER, colour);
    }

    /** Three streaks of rain slanting down under the cloud, or three flakes of snow. */
    private static void drops(VertexConsumer shapes, Matrix4f matrix, float x, float y, int colour, boolean flakes) {
        for (int drop = -1; drop <= 1; drop++) {
            double top = y - 0.25 * SKY_SIZE;
            double dropX = x + drop * 0.35 * SKY_SIZE;
            if (flakes) {
                double half = 0.08 * SKY_SIZE;
                ScreenDrawing.rect(shapes, matrix, dropX - half, top - 0.45 * SKY_SIZE - half, dropX + half, top - 0.45 * SKY_SIZE + half, 2 * LAYER, colour);
                continue;
            }
            ScreenDrawing.line(shapes, matrix, dropX, top, dropX - 0.15 * SKY_SIZE, top - 0.6 * SKY_SIZE, 2 * LAYER, 0.005F, colour);
        }
    }

    /** A lightning bolt zigzagging down from under the cloud. */
    private static void bolt(VertexConsumer shapes, Matrix4f matrix, float x, float y) {
        double[] zigzag = {0.1, -0.15, -0.12, -0.5, 0.1, -0.5, -0.08, -0.95};
        for (int i = 0; i + 3 < zigzag.length; i += 2) {
            ScreenDrawing.line(shapes, matrix, x + zigzag[i] * SKY_SIZE, y + zigzag[i + 1] * SKY_SIZE, x + zigzag[i + 2] * SKY_SIZE,
                y + zigzag[i + 3] * SKY_SIZE, 3 * LAYER, 0.007F, AMBER);
        }
    }

    /**
     * The wind's compass dial: a ring with north marked at its top and an arrow across it from the bearing the wind
     * comes from, its head the way it blows; in a calm, only a dot in the middle.
     */
    private static void dial(VertexConsumer shapes, Matrix4f matrix, Wind wind) {
        ScreenDrawing.disc(shapes, matrix, DIAL_X, DIAL_Y, DIAL, 32, LAYER, RULE);
        ScreenDrawing.disc(shapes, matrix, DIAL_X, DIAL_Y, DIAL - 0.008, 32, 2 * LAYER, GLASS);
        ScreenDrawing.rect(shapes, matrix, DIAL_X - 0.004, DIAL_Y + DIAL - 0.02, DIAL_X + 0.004, DIAL_Y + DIAL, 3 * LAYER, WHITE);
        if (wind.knots() < CALM) {
            ScreenDrawing.disc(shapes, matrix, DIAL_X, DIAL_Y, 0.012, 12, 3 * LAYER, CYAN);
            return;
        }
        // North up: a bearing goes clockwise from the top.
        double from = Math.toRadians(wind.from());
        double reach = DIAL - 0.02;
        double sin = Math.sin(from);
        double cos = Math.cos(from);
        double tailX = DIAL_X + sin * reach;
        double tailY = DIAL_Y + cos * reach;
        double tipX = DIAL_X - sin * reach;
        double tipY = DIAL_Y - cos * reach;
        ScreenDrawing.line(shapes, matrix, tailX, tailY, tipX + sin * ARROW_HEAD, tipY + cos * ARROW_HEAD, 3 * LAYER, ARROW, CYAN);
        // The head: a triangle back from the tip, drawn as a quad with its last corner on its first.
        double backX = tipX + sin * ARROW_HEAD;
        double backY = tipY + cos * ARROW_HEAD;
        double wide = ARROW_HEAD * 0.6;
        ScreenDrawing.vertex(shapes, matrix, tipX, tipY, 3 * LAYER, CYAN);
        ScreenDrawing.vertex(shapes, matrix, backX + cos * wide, backY - sin * wide, 3 * LAYER, CYAN);
        ScreenDrawing.vertex(shapes, matrix, backX - cos * wide, backY + sin * wide, 3 * LAYER, CYAN);
        ScreenDrawing.vertex(shapes, matrix, tipX, tipY, 3 * LAYER, CYAN);
    }
}
