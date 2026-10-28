package dev.aerolarge.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.aerolarge.AerolargeClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The northern lights in space's sky (see {@link SpaceSky}), by night from y 600 up, where the sky is dark: a ribbon of
 * light along the planet's rim to the north, the world's north, green at its foot and shading to violet at its top,
 * rippling as it goes. It swells and sinks, its foot sways a little over the rim, and bright folds and fine rays drift
 * along it, all of it waving round once every {@link #WAVE} ticks; it fades out toward either end, and in with the
 * night.
 *
 * <p>Its shape goes by the game's time, so everyone aboard sees the same lights. It is drawn after the planet and the
 * glow of its air, added to what is behind it at about a third of its colour: one draw a frame by night, none by day,
 * its directions round the north worked out once, and nothing at all with the {@code aurora} setting off.
 */
public final class Aurora {
    /**
     * How high the camera must be for the lights to show, and how high they are at their brightest, in world blocks.
     */
    private static final double FROM = 600;
    private static final double FULL = 700;
    /**
     * How far round the rim the lights reach, in degrees, centred on the north, and how many columns they are drawn in.
     */
    private static final float SPAN = 120;
    private static final int COLUMNS = 60;
    /**
     * How tall the lights stand over their foot, in degrees: so tall on the whole, give or take a swell and a ripple
     * on top of it, so between 3 and 6 degrees; and how many swells and ripples there are along them.
     */
    private static final float TALL = 4.5F;
    private static final float SWELL = 1.0F;
    private static final float RIPPLE = 0.5F;
    private static final float SWELLS = 1.3F;
    private static final float RIPPLES = 2.7F;
    /**
     * How far over the planet's rim their foot stands, in degrees, how far it sways up and down from there, in degrees,
     * and how many sways there are along it.
     */
    private static final float FOOT_OVER = 0.6F;
    private static final float FOOT_SWAY = 0.3F;
    private static final float FOOT_SWAYS = 1.9F;
    /**
     * How the lights brighten and dim along their length: their dimmest, as a share of their brightest, and the shares
     * of the broad folds and of the fine rays on top of it; and how many folds and rays there are along them.
     */
    private static final float DIMMEST = 0.4F;
    private static final float FOLD_SHARE = 0.4F;
    private static final float RAY_SHARE = 0.2F;
    private static final float FOLDS = 3.5F;
    private static final float RAYS = 9;
    /** How long the lights take to wave round once, in ticks. */
    private static final int WAVE = 20 * 20;
    /** How opaque the lights are at their brightest, as a share of their colour added to the sky behind them. */
    private static final float OPACITY = 0.35F;
    /**
     * How far off the lights are drawn, in blocks, as far as the stars, well short of the nearest the game's far plane
     * comes; and how far under their foot they fade in from, in degrees, so their foot is soft.
     */
    private static final float DISTANCE = 100;
    private static final float SKIRT = 0.5F;
    /** The lights' colours, green at the foot, a paler green over it, and violet near the top. */
    private static final int FOOT = 0x3DFF7A;
    private static final int LOW = 0x5CFFA8;
    private static final int HIGH = 0x9A5CFF;
    /**
     * The rows the lights are drawn in, from the soft edge under their foot to their top: how far up each is, as a
     * share of how tall they are, and how far under the foot, in degrees; its colour; and how opaque it is, as a share.
     */
    private static final float[] ROW_UP = {0, 0, 0.3F, 0.7F, 1};
    private static final float[] ROW_UNDER = {SKIRT, 0, 0, 0, 0};
    private static final int[] ROW_COLOURS = {FOOT, FOOT, LOW, HIGH, HIGH};
    private static final float[] ROW_OPACITY = {0, 1, 0.85F, 0.55F, 0};

    /** Which way each column's edge lies round the north, east and south of the camera: worked out once. */
    private static final float[] EAST = new float[COLUMNS + 1];
    private static final float[] SOUTH = new float[COLUMNS + 1];
    /**
     * Each column edge's foot, in degrees over the horizon, how tall it stands there, in degrees, and how bright it is,
     * as a share of the lights' brightest: worked out anew each frame, kept for it.
     */
    private static final float[] FOOT_UP = new float[COLUMNS + 1];
    private static final float[] TALLNESS = new float[COLUMNS + 1];
    private static final float[] LIGHT = new float[COLUMNS + 1];

    static {
        for (int column = 0; column <= COLUMNS; column++) {
            double bearing = Math.toRadians(((double) column / COLUMNS - 0.5) * SPAN);
            EAST[column] = (float) Math.sin(bearing);
            SOUTH[column] = (float) -Math.cos(bearing);
        }
    }

    private Aurora() {
    }

    /**
     * The northern lights, by night: along the planet's rim, so many degrees under the horizon, as far as the night has
     * fallen (the day's light given, from 0 at night to 1 by day).
     */
    static void draw(Matrix4f pose, ClientLevel level, float partialTick, double height, double dip, float day) {
        float light = (1 - day) * SpaceSky.ramp(height, FROM, FULL);
        if (light <= 0 || !AerolargeClientConfig.AURORA.get()) {
            return;
        }
        float wave = (level.getGameTime() % WAVE + partialTick) / WAVE * Mth.TWO_PI;
        for (int column = 0; column <= COLUMNS; column++) {
            shape(column, wave, (float) dip);
        }
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, OPACITY * light);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int column = 0; column < COLUMNS; column++) {
            for (int row = 0; row + 1 < ROW_UP.length; row++) {
                vertex(builder, pose, column, row);
                vertex(builder, pose, column + 1, row);
                vertex(builder, pose, column + 1, row + 1);
                vertex(builder, pose, column, row + 1);
            }
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /**
     * Shapes the lights at a column's edge as they wave, so far round their wave, in radians: over the rim, so many
     * degrees under the horizon, swelling, swaying and brightening along their length, and fading out at their ends.
     */
    private static void shape(int column, float wave, float dip) {
        float along = (float) column / COLUMNS;
        float across = Mth.TWO_PI * along;
        FOOT_UP[column] = -dip + FOOT_OVER + FOOT_SWAY * Mth.sin(across * FOOT_SWAYS + wave);
        TALLNESS[column] = TALL + SWELL * Mth.sin(across * SWELLS + wave) + RIPPLE * Mth.sin(across * RIPPLES - 2 * wave);
        float folds = 0.5F + 0.5F * Mth.sin(across * FOLDS - wave);
        float rays = 0.5F + 0.5F * Mth.sin(across * RAYS + 2 * wave);
        LIGHT[column] = Mth.sin(Mth.PI * along) * (DIMMEST + FOLD_SHARE * folds + RAY_SHARE * rays);
    }

    /** A corner of the lights, at a column's edge and on one of their rows, in that row's colour. */
    private static void vertex(BufferBuilder builder, Matrix4f pose, int column, int row) {
        float up = (FOOT_UP[column] + ROW_UP[row] * TALLNESS[column] - ROW_UNDER[row]) * Mth.DEG_TO_RAD;
        float out = Mth.cos(up) * DISTANCE;
        int colour = FastColor.ARGB32.color(Math.round(ROW_OPACITY[row] * LIGHT[column] * 255), ROW_COLOURS[row]);
        builder.addVertex(pose, EAST[column] * out, Mth.sin(up) * DISTANCE, SOUTH[column] * out).setColor(colour);
    }
}
