package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.PassengerScreenBlock;
import dev.aerolarge.block.PassengerScreenBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * Draws a passenger screen's flight information over its face, in the dark blue of a seat-back screen: a header
 * saying how the airliner is flying (on the ground, climbing, cruising or descending), six tiles under it with its
 * altitude above sea level, its speed over the ground, how fast it climbs or sinks, the way it is heading, how high it
 * is above the ground and how far it has flown since it took off, and along the bottom the time in the air and that
 * using the screen shows the outside cameras (see {@link PassengerCameras}). The figures are read from the airliner
 * each tick as the client has it, and the page's text is laid out from them once a tick for all the airliner's
 * screens, which share one log of its flight (see {@link Trip}). On an airliner that is not assembled it shows a
 * standby page.
 *
 * <p>A screen is only drawn with the camera in front of it, as its face is drawn from the front only, and no further
 * off than the game draws any block entity, past which its block's miniature of the page stands in. Its text fades
 * out as it gets too small on the screen to be read, a few pixels tall, and is left out beyond that.
 */
public class PassengerScreenRenderer implements BlockEntityRenderer<PassengerScreenBlockEntity> {
    /** The screen's face, from the middle of its block toward the way it faces, lifted just off the panel. */
    private static final float FACE = -0.5F + PassengerScreenBlock.DEPTH / 16F + 0.002F;
    /** The lit part of the face, inside a thin bezel: half its width and half its height, in blocks. */
    private static final double HALF_WIDTH = PassengerScreenBlock.WIDTH / 32.0 - 1 / 32.0;
    private static final double HALF_HEIGHT = PassengerScreenBlock.HEIGHT / 32.0 - 1 / 32.0;
    /** The header band along the top, the footer along the bottom, the stripe under the header, in blocks. */
    private static final double HEADER = 0.07;
    private static final double FOOTER = 0.055;
    private static final double STRIPE = 0.004;
    /** The tiles: how many across and down, the margin around and between them, and the padding inside them. */
    private static final int COLUMNS = 3;
    private static final int ROWS = 2;
    private static final double MARGIN = 0.012;
    private static final double PAD = 0.012;
    /** How far down from a tile's top its label sits, and its figure from the tile's middle; the gap before a unit. */
    private static final double LABEL_DROP = 0.03;
    private static final double FIGURE_DROP = 0.015;
    private static final double UNIT_GAP = 0.008;
    /** Where the standby page's welcome and the line under it sit, up from the screen's middle. */
    private static final double WELCOME_Y = 0.04;
    private static final double STANDBY_Y = -0.05;
    /** How far a line of text's baseline is below its middle, in font pixels: lines of two sizes stand on one baseline. */
    private static final float BASELINE = 2.5F;
    /** Lifts the shapes off the face, and the text off the shapes. */
    private static final float LAYER = 0.001F;
    private static final float TEXT_LIFT = 0.004F;
    /** Text sizes, in blocks per font pixel: the header, the labels and units, the figures (at most), the welcome. */
    private static final float TITLE = 1 / 300F;
    private static final float SMALL = 1 / 330F;
    private static final float FIGURE = 1 / 150F;
    private static final float WELCOME = 1 / 190F;
    /** How tall a digit is, in font pixels. */
    private static final int DIGIT = 7;
    /**
     * How many pixels tall the figures, the biggest text, stand on the screen while the text is drawn whole, and by
     * when it has faded out: a few pixels tall, a figure can no longer be read. The game's text shader leaves out text
     * drawn fainter than {@link #FAINTEST}, so it is not drawn at all from there on.
     */
    private static final float SHARP = 3;
    private static final float GONE = 2;
    private static final float FAINTEST = 0.1F;
    /** How long an airliner's flight is kept once nothing asks after its screens any more, in ticks: a minute. */
    private static final long FORGET = 1200;
    /**
     * The height of the sea the altitude is measured from: an ordinary world's, which is all the client knows (only
     * the server's world generator knows another).
     */
    private static final int SEA_LEVEL = 63;
    /** Kilometres an hour in a block a second, a block being a metre. */
    private static final double KMH_PER_BLOCK_A_SECOND = 3.6;
    /** Climbing or sinking slower than this, in blocks a second, the airliner is flying level. */
    private static final double LEVEL = 1.5;
    /** Climbing or sinking slower than this, in blocks a second, the vertical speed shows no arrow. */
    private static final double STEADY = 0.05;
    /** Moving over the ground faster than this, in blocks a second, the way it goes is its heading. */
    private static final double MOVING = 1.0;
    /** A distance up to this many blocks is shown in metres, and up to this many in kilometres to a decimal. */
    private static final double METRES_UP_TO = 1000;
    private static final double TENTHS_UP_TO = 100_000;
    private static final String KEY = "screen.aerolarge.passenger_screen.";

    private static final int BACKGROUND = 0xFF081226;
    private static final int BAND = 0xFF10254A;
    private static final int TILE = 0xFF132D59;
    private static final int LABEL = 0xFF8FB4D6;
    private static final int WHITE = 0xFFF2F6FB;
    private static final int ACCENT = 0xFF46C8F0;
    private static final int CLIMB = 0xFF46E070;
    private static final int SINK = 0xFFF2B830;

    private static final Tile[] TILES = tiles();

    /** Each assembled airliner's flight, as all its screens show it; forgotten a while after nothing asks after it. */
    private final Map<UUID, Flight> flights = new HashMap<>();
    /** The standby page's text, and the game time it was laid out at. */
    private List<ScreenDrawing.Label> standby = List.of();
    private long standbyLaidOut = Long.MIN_VALUE;

    /** A rectangle on the screen. */
    private record Tile(double x0, double y0, double x1, double y1) {
    }

    /** One tile's worth: the key of its label, the figure, the key of its unit (or none) and the figure's colour. */
    private record Reading(String label, String figure, @Nullable String unit, int colour) {
    }

    /**
     * Only with the camera in front of the screen, as its face is drawn from the front only, and as far off as the game
     * draws any block entity. Sable asks this of every screen of an assembled airliner every frame, whether it is in
     * sight or not, so the airliner's flight is followed here, the same while no screen of it is drawn.
     */
    @Override
    public boolean shouldRender(PassengerScreenBlockEntity screen, Vec3 camera) {
        follow(screen);
        Direction facing = screen.getBlockState().getValue(PassengerScreenBlock.FACING);
        return Sight.facing(screen.getBlockPos(), facing, FACE, camera, getViewDistance());
    }

    @Override
    public void render(PassengerScreenBlockEntity screen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = screen.getLevel();
        if (level == null) {
            return;
        }
        Direction facing = screen.getBlockState().getValue(PassengerScreenBlock.FACING);
        Flight flight = follow(screen);

        pose.pushPose();
        // Onto the screen's face, x to the viewer's right and y up, from its middle.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, 0, FACE);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH, HALF_HEIGHT, 0, BACKGROUND);
        if (flight != null) {
            panels(shapes, matrix);
        }
        // The text over it all, as clear as it can still be made out from here.
        float strength = Math.clamp((Sight.pixels(pose.last(), DIGIT * FIGURE) - GONE) / (SHARP - GONE), 0F, 1F);
        if (strength >= FAINTEST) {
            Font font = Minecraft.getInstance().font;
            long now = level.getGameTime();
            int alpha = Math.round(255 * strength);
            for (ScreenDrawing.Label label : flight != null ? flight.page(font, now) : standby(font, now)) {
                label.draw(font, pose, buffers, TEXT_LIFT, alpha);
            }
        }
        pose.popPose();
    }

    /**
     * The flight of the assembled airliner the screen is part of, followed up to this tick; null while the screen
     * stands among blocks that are not assembled.
     */
    @Nullable
    private Flight follow(PassengerScreenBlockEntity screen) {
        Level level = screen.getLevel();
        UUID airliner = level != null ? SableClient.airliner(screen) : null;
        if (airliner == null) {
            return null;
        }
        long now = level.getGameTime();
        Flight flight = flights.get(airliner);
        if (flight == null) {
            flights.values().removeIf(old -> old.forgotten(now));
            flight = new Flight(now);
            flights.put(airliner, flight);
        }
        flight.follow(screen, now);
        return flight;
    }

    /** The standby page's text, laid out once a tick. */
    private List<ScreenDrawing.Label> standby(Font font, long now) {
        if (standbyLaidOut != now) {
            standbyLaidOut = now;
            standby = List.of(
                ScreenDrawing.Label.of(font, Component.translatable(KEY + "welcome"), 0, WELCOME_Y, WELCOME, WHITE, ScreenDrawing.Align.CENTRE),
                ScreenDrawing.Label.of(font, Component.translatable(KEY + "standby"), 0, STANDBY_Y, SMALL, ACCENT, ScreenDrawing.Align.CENTRE));
        }
        return standby;
    }

    /** The page of flight information's bands and tiles, under its text. */
    private static void panels(VertexConsumer shapes, Matrix4f matrix) {
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH, HALF_HEIGHT - HEADER, HALF_WIDTH, HALF_HEIGHT, LAYER, BAND);
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH, HALF_HEIGHT - HEADER - STRIPE, HALF_WIDTH, HALF_HEIGHT - HEADER, LAYER, ACCENT);
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH, -HALF_HEIGHT + FOOTER, LAYER, BAND);
        for (Tile tile : TILES) {
            ScreenDrawing.rect(shapes, matrix, tile.x0(), tile.y0(), tile.x1(), tile.y1(), LAYER, TILE);
        }
    }

    /** The page of flight information's text: the header, a reading in each tile and the footer. */
    private static List<ScreenDrawing.Label> flightInfo(Font font, Reading[] readings, String status, long flightTicks) {
        List<ScreenDrawing.Label> text = new ArrayList<>();
        double header = HALF_HEIGHT - HEADER / 2;
        text.add(ScreenDrawing.Label.of(font, Component.translatable(KEY + "title"), -HALF_WIDTH + MARGIN, header, TITLE, WHITE, ScreenDrawing.Align.LEFT));
        text.add(ScreenDrawing.Label.of(font, Component.translatable(KEY + "status." + status), HALF_WIDTH - MARGIN, header, TITLE, ACCENT,
            ScreenDrawing.Align.RIGHT));
        for (int i = 0; i < TILES.length; i++) {
            reading(font, text, TILES[i], readings[i]);
        }
        double footer = -HALF_HEIGHT + FOOTER / 2;
        text.add(ScreenDrawing.Label.of(font, Component.translatable(KEY + "flight_time", time(flightTicks)), -HALF_WIDTH + MARGIN, footer, SMALL, LABEL,
            ScreenDrawing.Align.LEFT));
        text.add(ScreenDrawing.Label.of(font, Component.translatable(KEY + "cameras"), HALF_WIDTH - MARGIN, footer, SMALL, ACCENT, ScreenDrawing.Align.RIGHT));
        return text;
    }

    /**
     * One tile's text: its label in the top corner, the figure across its middle with the unit after it, the figure
     * made smaller where it would not otherwise fit.
     */
    private static void reading(Font font, List<ScreenDrawing.Label> text, Tile tile, Reading reading) {
        text.add(ScreenDrawing.Label.of(font, Component.translatable(KEY + reading.label()), tile.x0() + PAD, tile.y1() - LABEL_DROP, SMALL, LABEL,
            ScreenDrawing.Align.LEFT));
        Component figure = Component.literal(reading.figure());
        Component unit = reading.unit() != null ? Component.translatable(KEY + reading.unit()) : Component.empty();
        double unitWidth = font.width(unit) * SMALL;
        double gap = reading.unit() != null ? UNIT_GAP : 0;
        double room = tile.x1() - tile.x0() - 2 * PAD - unitWidth - gap;
        float size = (float) Math.min(FIGURE, room / Math.max(1, font.width(figure)));
        double figureWidth = font.width(figure) * size;
        double left = (tile.x0() + tile.x1() - figureWidth - gap - unitWidth) / 2;
        double y = (tile.y0() + tile.y1()) / 2 - FIGURE_DROP;
        text.add(ScreenDrawing.Label.of(font, figure, left, y, size, reading.colour(), ScreenDrawing.Align.LEFT));
        text.add(ScreenDrawing.Label.of(font, unit, left + figureWidth + gap, y - BASELINE * (size - SMALL), SMALL, LABEL, ScreenDrawing.Align.LEFT));
    }

    /** What the six tiles show, in their order across and down. */
    private static Reading[] readings(SableClient.Ride ride, Trip trip) {
        Vector3d velocity = ride.velocity();
        double climb = velocity.y;
        String arrow = climb > STEADY ? "▲ " : climb < -STEADY ? "▼ " : "";
        int climbColour = climb > STEADY ? CLIMB : climb < -STEADY ? SINK : WHITE;
        double track = trip.track();
        boolean heading = !Double.isNaN(track);
        double distance = trip.distance();
        return new Reading[]{
            new Reading("altitude", whole(ride.position().y - SEA_LEVEL), "unit.metres", WHITE),
            new Reading("ground_speed", whole(Math.hypot(velocity.x, velocity.z) * KMH_PER_BLOCK_A_SECOND), "unit.kilometres_per_hour", WHITE),
            new Reading("vertical_speed", arrow + String.format(Locale.ROOT, "%.1f", Math.abs(climb)), "unit.metres_per_second", climbColour),
            new Reading("heading", heading ? String.format(Locale.ROOT, "%03d°", Math.round(track) % 360) : "---",
                heading ? "compass." + Math.round(track / 45) % 8 : null, WHITE),
            new Reading("height", whole(trip.wheelHeight(ride)), "unit.metres", WHITE),
            distance < METRES_UP_TO ? new Reading("distance", whole(distance), "unit.metres", WHITE)
                : new Reading("distance", distance < TENTHS_UP_TO ? String.format(Locale.ROOT, "%.1f", distance / 1000) : whole(distance / 1000),
                    "unit.kilometres", WHITE)
        };
    }

    private static String whole(double value) {
        return String.format(Locale.ROOT, "%,d", Math.round(value));
    }

    /** Ticks as hours, minutes and seconds. */
    private static String time(long ticks) {
        long seconds = ticks / 20;
        return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    /** The tiles, across and then down, filling the room between the header and the footer. */
    private static Tile[] tiles() {
        double top = HALF_HEIGHT - HEADER - STRIPE - MARGIN;
        double bottom = -HALF_HEIGHT + FOOTER + MARGIN;
        double width = (2 * HALF_WIDTH - (COLUMNS + 1) * MARGIN) / COLUMNS;
        double height = (top - bottom - (ROWS - 1) * MARGIN) / ROWS;
        Tile[] tiles = new Tile[COLUMNS * ROWS];
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                double x0 = -HALF_WIDTH + MARGIN + column * (width + MARGIN);
                double y1 = top - row * (height + MARGIN);
                tiles[row * COLUMNS + column] = new Tile(x0, y1 - height, x0 + width, y1);
            }
        }
        return tiles;
    }

    /**
     * An assembled airliner as its screens show it: its flight, followed once a tick however many screens ask, as the
     * airliner was that tick, and the page's text, laid out from them once a tick for whichever screens are drawn.
     */
    private static final class Flight {
        private final Trip trip = new Trip();
        @Nullable
        private SableClient.Ride ride;
        /** The game times something last asked after it, it was last followed and its page last laid out at. */
        private long asked;
        private long followed = Long.MIN_VALUE;
        private long laidOut = Long.MIN_VALUE;
        private List<ScreenDrawing.Label> page = List.of();

        Flight(long now) {
            asked = now;
        }

        void follow(PassengerScreenBlockEntity screen, long now) {
            asked = now;
            if (now == followed) {
                return;
            }
            SableClient.Ride found = SableClient.ride(screen);
            if (found != null) {
                followed = now;
                ride = found;
                trip.update(found, now);
            }
        }

        /** Whether nothing has asked after it for so long that the airliner is gone, or it was in another world. */
        boolean forgotten(long now) {
            return now < asked || now - asked > FORGET;
        }

        List<ScreenDrawing.Label> page(Font font, long now) {
            if (laidOut != now && ride != null) {
                laidOut = now;
                page = flightInfo(font, readings(ride, trip), trip.status(ride), trip.flightTicks(now));
            }
            return page;
        }
    }

    /**
     * What the screens have seen of their airliner's flight: whether it is in the air, when it last took off and how
     * far across the ground it has flown since, and the way it was last seen going. The distance adds up the airliner's
     * moves tick by tick; a stretch it was not followed for counts as the crow flies. Taking off starts a new flight;
     * landing stops the clock but keeps the figures up until the next take-off.
     */
    private static final class Trip {
        private final GroundContact ground = new GroundContact();
        @Nullable
        private Vector3d last;
        private boolean airborne;
        /** Game times of the last take-off and landing; -1 before the screen has seen one. */
        private long tookOff = -1;
        private long landed = -1;
        private double distance;
        /** The compass heading it was last seen going, 0 north and 90 east; NaN before it has moved. */
        private double track = Double.NaN;

        /** Takes in the airliner as it is this tick, once a tick. */
        void update(SableClient.Ride ride, long gameTime) {
            boolean onGround = ground.onGround(ride);
            if (!onGround && !airborne) {
                airborne = true;
                tookOff = gameTime;
                distance = 0;
            } else if (onGround && airborne) {
                airborne = false;
                landed = gameTime;
            }
            Vector3d here = ride.position();
            if (airborne && last != null) {
                distance += Math.hypot(here.x - last.x, here.z - last.z);
            }
            last = here;
            Vector3d velocity = ride.velocity();
            if (Math.hypot(velocity.x, velocity.z) > MOVING) {
                track = (Math.toDegrees(Math.atan2(velocity.x, -velocity.z)) + 360) % 360;
            }
        }

        /** The key of what the airliner is doing: on the ground, or climbing, cruising or descending. */
        String status(SableClient.Ride ride) {
            if (!airborne) {
                return "ground";
            }
            double climb = ride.velocity().y;
            return climb > LEVEL ? "climbing" : climb < -LEVEL ? "descending" : "cruising";
        }

        /** How long it has been in the air on this flight, or was on the last one, in ticks. */
        long flightTicks(long gameTime) {
            if (tookOff < 0) {
                return 0;
            }
            return (airborne ? gameTime : landed) - tookOff;
        }

        double distance() {
            return distance;
        }

        double track() {
            return track;
        }

        double wheelHeight(SableClient.Ride ride) {
            return ground.wheelHeight(ride);
        }
    }
}
