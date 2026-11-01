package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.autopilot.EdgeOfSpace;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * How high a rocket flies, as her lift's picture boards show it in her flight, but where its car stands (see {@link
 * LiftBoardRenderer}): a column from the sea at its foot to her ceiling at its top, each height in the colour the sky
 * is there by day (see {@link SpaceSky#skyAt}), the planet a band of sea and land at its foot, a dashed white line with
 * a star where space begins, stars twinkling over the line once she is past it, and a little rocket at her height, a
 * long flame flickering under it while she boosts (see the autopilot's RocketControls). Players who cannot read see how
 * high she is and how far she has to go to space.
 */
final class HeightGauge {
    /** How many bands of colour the sky is drawn in, from the sea to her ceiling. */
    private static final int BANDS = 24;
    /** How much of the column the planet takes at its foot, as a share of its height, and its land's share of that. */
    private static final double PLANET = 0.06;
    private static final double LAND = 0.35;
    /** How many dashes the edge of space is drawn in, and how thick it is, as a share of the column's height. */
    private static final int DASHES = 5;
    private static final double LINE = 0.006;
    /** How big the rocket and the star are, half their size as a share of the column's width. */
    private static final double ROCKET = 0.32;
    private static final double STAR = 0.16;
    /**
     * The stars twinkling over the edge once she is past it: how many, how big as a share of the column's width, and
     * how often they twinkle, in ticks.
     */
    private static final int STARS = 9;
    private static final double STAR_DOT = 0.035;
    private static final int TWINKLE = 8;
    /**
     * Boosting, the little rocket's flame: how far under the rocket's middle it starts, how long and how wide it is, as
     * shares of the rocket's half size, its yellow core's share of it, how far longer and shorter it flickers, as a share
     * of its length, and how long a flicker takes, in ticks.
     */
    private static final double FLAME_FROM = 0.7;
    private static final double FLAME_LONG = 1.8;
    private static final double FLAME_WIDE = 0.4;
    private static final double FLAME_CORE = 0.5;
    private static final double FLICKER = 0.15;
    private static final int FLICKER_TICKS = 6;
    private static final int FLAME_ORANGE = 0xFFFF8A1A;
    private static final int FLAME_YELLOW = 0xFFFFE070;
    /** Lifts each layer off the board's own. */
    private static final float LAYER = 0.001F;
    private static final int SEA = 0xFF2E6B99;
    private static final int LAND_GREEN = 0xFF4C9A55;
    private static final int WHITE = 0xFFF2F6FB;
    private static final ResourceLocation ROCKET_ICON = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/lift/rocket.png");
    private static final ResourceLocation STAR_ICON = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/lift/star.png");

    /** The column the gauge fills on a board's face, its corners in blocks on the face. */
    record Column(double left, double bottom, double right, double top) {
        double width() {
            return right - left;
        }

        double height() {
            return top - bottom;
        }

        /** How high up the column a height in the world is drawn, from the sea at its foot to the ceiling at its top. */
        double at(double worldY, double sea, double ceiling) {
            double planet = height() * PLANET;
            return bottom + planet + (height() - planet) * Mth.clamp((worldY - sea) / (ceiling - sea), 0, 1);
        }
    }

    private HeightGauge() {
    }

    /**
     * The gauge in a column, for a rocket so high in the world, boosting or not: every shape first, then its pictures.
     */
    static void draw(Matrix4f matrix, VertexConsumer shapes, MultiBufferSource buffers, Column column, Level level, double height, boolean boosting) {
        double sea = level.getSeaLevel();
        double ceiling = Math.max(sea + 1, AerolargeConfig.ROCKET_CEILING.get());
        sky(matrix, shapes, column, sea, ceiling);
        double edge = column.at(EdgeOfSpace.HEIGHT, sea, ceiling);
        double dash = column.width() / (2 * DASHES - 1);
        for (int k = 0; k < DASHES; k++) {
            double from = column.left() + 2 * k * dash;
            ScreenDrawing.rect(shapes, matrix, from, edge - LINE * column.height(), from + dash, edge + LINE * column.height(), 3 * LAYER, WHITE);
        }
        if (height >= EdgeOfSpace.HEIGHT) {
            stars(matrix, shapes, column, edge, level.getGameTime());
        }
        double half = column.width() * ROCKET;
        double rocketY = Mth.clamp(column.at(height, sea, ceiling), column.bottom() + column.height() * PLANET + half, column.top() - half);
        double middle = (column.left() + column.right()) / 2;
        if (boosting) {
            flame(matrix, shapes, middle, rocketY - half * FLAME_FROM, half, level.getGameTime());
        }
        ScreenDrawing.picture(buffers, matrix, STAR_ICON, column.right() - column.width() * STAR, edge + column.width() * STAR, column.width() * STAR,
            4 * LAYER, WHITE);
        ScreenDrawing.picture(buffers, matrix, ROCKET_ICON, middle, rocketY, half, 5 * LAYER, WHITE);
    }

    /**
     * The little rocket's flame while she boosts, down from its top under the rocket, flickering longer and shorter.
     */
    private static void flame(Matrix4f matrix, VertexConsumer shapes, double middle, double top, double half, long now) {
        double length = half * FLAME_LONG * (1 + FLICKER * Math.sin(Mth.TWO_PI * now / FLICKER_TICKS));
        double wide = half * FLAME_WIDE;
        ScreenDrawing.rect(shapes, matrix, middle - wide, top - length, middle + wide, top, 3 * LAYER, FLAME_ORANGE);
        ScreenDrawing.rect(shapes, matrix, middle - wide * FLAME_CORE, top - length * FLAME_CORE, middle + wide * FLAME_CORE, top, 4 * LAYER, FLAME_YELLOW);
    }

    /** The sky in bands from the sea to her ceiling, each the colour of its height, and the planet at its foot. */
    private static void sky(Matrix4f matrix, VertexConsumer shapes, Column column, double sea, double ceiling) {
        double planet = column.bottom() + column.height() * PLANET;
        double band = (column.top() - planet) / BANDS;
        for (int k = 0; k < BANDS; k++) {
            double worldY = sea + (ceiling - sea) * (k + 0.5) / BANDS;
            int colour = 0xFF000000 | rgb(SpaceSky.skyAt(worldY));
            ScreenDrawing.rect(shapes, matrix, column.left(), planet + k * band, column.right(), planet + (k + 1) * band, LAYER, colour);
        }
        double land = column.bottom() + (planet - column.bottom()) * (1 - LAND);
        ScreenDrawing.rect(shapes, matrix, column.left(), column.bottom(), column.right(), land, LAYER, SEA);
        ScreenDrawing.rect(shapes, matrix, column.left(), land, column.right(), planet, LAYER, LAND_GREEN);
    }

    /** A few stars over the edge of space, each twinkling in its own time. */
    private static void stars(Matrix4f matrix, VertexConsumer shapes, Column column, double edge, long now) {
        double dot = column.width() * STAR_DOT;
        for (int k = 0; k < STARS; k++) {
            double across = column.left() + column.width() * ((k * 0.618034) % 1);
            double up = edge + (column.top() - edge) * ((k * 0.41421 + 0.2) % 1);
            boolean lit = (now / TWINKLE + k) % 3 != 0;
            ScreenDrawing.rect(shapes, matrix, across - dot, up - dot, across + dot, up + dot, 2 * LAYER, lit ? WHITE : 0xFF8090A0);
        }
    }

    private static int rgb(Vec3 colour) {
        return (int) (colour.x * 255) << 16 | (int) (colour.y * 255) << 8 | (int) (colour.z * 255);
    }
}
