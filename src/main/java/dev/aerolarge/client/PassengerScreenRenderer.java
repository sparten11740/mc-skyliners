package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.PassengerScreenBlock;
import dev.aerolarge.block.PassengerScreenBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.joml.Vector3d;

/**
 * Draws a passenger screen's flight information over its face, in the dark blue of a seat-back screen: a header
 * saying how the airliner is flying (on the ground, climbing, cruising or descending), six tiles under it with its
 * altitude above sea level, its speed over the ground, how fast it climbs or sinks, the way it is heading, how high it
 * is above the ground and how far it has flown since it took off, and along the bottom the time in the air and that
 * using the screen shows the outside cameras (see {@link PassengerCameras}). Every ten seconds the page gives way to
 * the route map for as long (see {@link RouteMap}): the land below, the villages and the route flown since take-off,
 * the airliner on it, under the same header. The flight deck's flight map shows that map for good, big, on a square
 * page of its own laid out here too (see {@link #drawFlightMap}), with three of the figures under it. The figures are
 * read from the airliner each tick as the client has it, and the page's text is laid out from them once a tick for all
 * the airliner's screens, which share one log of its flight (see {@link Trip}). On an airliner that is not assembled
 * it shows a standby page.
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
    /** How long each page, the flight information and the route map, stays up before the other, in ticks. */
    private static final long PAGE_TICKS = 200;
    /** How far the airliner flies between the points of its route kept for the map, in blocks, and how many at most. */
    private static final double ROUTE_STEP = 8;
    private static final int MOST_ROUTE = 2000;

    private static final int BACKGROUND = 0xFF081226;
    private static final int BAND = 0xFF10254A;
    private static final int TILE = 0xFF132D59;
    private static final int LABEL = 0xFF8FB4D6;
    private static final int WHITE = 0xFFF2F6FB;
    private static final int ACCENT = 0xFF46C8F0;
    private static final int CLIMB = 0xFF46E070;
    private static final int SINK = 0xFFF2B830;

    private static final Tile[] TILES = tiles();

    /**
     * The flight map's square page, in a screen's units round its middle: as wide as a screen's lit face, and as
     * tall. The route map runs under its header as wide as the page, as tall as the map's own shape, and a row of
     * tiles under it shows three of the six figures: the altitude, the speed over the ground and the distance flown.
     */
    static final double FLIGHT_MAP_WIDTH = 2 * HALF_WIDTH;
    private static final double FLIGHT_MAP_TOP = HALF_WIDTH - HEADER - STRIPE;
    private static final double FLIGHT_MAP_BOTTOM = FLIGHT_MAP_TOP - FLIGHT_MAP_WIDTH * RouteMap.HEIGHT / RouteMap.WIDTH;
    private static final int[] FLIGHT_MAP_READINGS = {0, 1, 5};
    private static final Tile[] FLIGHT_MAP_TILES = flightMapTiles();

    /**
     * Each assembled airliner's flight, as all its screens and its flight map show it; forgotten a while after nothing
     * asks after it.
     */
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    /** The standby page's text, and the game time it was laid out at. */
    private static List<ScreenDrawing.Label> standby = List.of();
    private static long standbyLaidOut = Long.MIN_VALUE;

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
        long now = level.getGameTime();
        boolean map = flight != null && now / PAGE_TICKS % 2 == 1;
        if (map) {
            header(shapes, matrix, HALF_HEIGHT);
            // After the shapes, which fetching the map's own buffer ends.
            flight.map(level, now).draw(buffers, matrix, -HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH, HALF_HEIGHT - HEADER - STRIPE, LAYER);
        } else if (flight != null) {
            panels(shapes, matrix);
        }
        text(pose, buffers, font -> flight == null ? standby(font, now) : map ? flight.mapPage(font, now) : flight.page(font, now));
        pose.popPose();
    }

    /**
     * The flight map's page (see {@link FlightMapRenderer}), square round the middle of the pose, {@link
     * #FLIGHT_MAP_WIDTH} wide and tall: the header, the route map under it and three figures under that, in a screen's
     * colours; the standby page while the airliner is not assembled.
     */
    static void drawFlightMap(@Nullable Flight flight, Level level, PoseStack pose, MultiBufferSource buffers) {
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH, -HALF_WIDTH, HALF_WIDTH, HALF_WIDTH, 0, BACKGROUND);
        long now = level.getGameTime();
        if (flight != null) {
            header(shapes, matrix, HALF_WIDTH);
            for (Tile tile : FLIGHT_MAP_TILES) {
                ScreenDrawing.rect(shapes, matrix, tile.x0(), tile.y0(), tile.x1(), tile.y1(), LAYER, TILE);
            }
            flight.map(level, now).draw(buffers, matrix, -HALF_WIDTH, FLIGHT_MAP_BOTTOM, HALF_WIDTH, FLIGHT_MAP_TOP, LAYER);
        }
        text(pose, buffers, font -> flight == null ? standby(font, now) : flight.flightMapPage(font, now));
    }

    /** The text over it all, as clear as it can still be made out from here: none once it is too small to read. */
    private static void text(PoseStack pose, MultiBufferSource buffers, Function<Font, List<ScreenDrawing.Label>> layout) {
        float strength = Math.clamp((Sight.pixels(pose.last(), DIGIT * FIGURE) - GONE) / (SHARP - GONE), 0F, 1F);
        if (strength < FAINTEST) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int alpha = Math.round(255 * strength);
        for (ScreenDrawing.Label label : layout.apply(font)) {
            label.draw(font, pose, buffers, TEXT_LIFT, alpha);
        }
    }

    /**
     * The flight of the assembled airliner a screen (or the flight map) is part of, followed up to this tick; null
     * while it stands among blocks that are not assembled.
     */
    @Nullable
    static Flight follow(BlockEntity screen) {
        Level level = screen.getLevel();
        UUID airliner = level != null ? SableClient.airliner(screen) : null;
        if (airliner == null) {
            return null;
        }
        long now = level.getGameTime();
        Flight flight = FLIGHTS.get(airliner);
        if (flight == null) {
            forgetOld(now);
            flight = new Flight(airliner, now);
            FLIGHTS.put(airliner, flight);
        }
        flight.follow(screen, now);
        return flight;
    }

    /** Forgets the flights nothing has asked after for a long while, letting go of their maps. */
    private static void forgetOld(long now) {
        Iterator<Flight> each = FLIGHTS.values().iterator();
        while (each.hasNext()) {
            Flight old = each.next();
            if (old.forgotten(now)) {
                old.close();
                each.remove();
            }
        }
    }

    /** The standby page's text, laid out once a tick. */
    private static List<ScreenDrawing.Label> standby(Font font, long now) {
        if (standbyLaidOut != now) {
            standbyLaidOut = now;
            standby = List.of(
                ScreenDrawing.Label.of(font, Component.translatable(KEY + "welcome"), 0, WELCOME_Y, WELCOME, WHITE, ScreenDrawing.Align.CENTRE),
                ScreenDrawing.Label.of(font, Component.translatable(KEY + "standby"), 0, STANDBY_Y, SMALL, ACCENT, ScreenDrawing.Align.CENTRE));
        }
        return standby;
    }

    /** The header band along the top of a page, its top edge this high, and the stripe under it. */
    private static void header(VertexConsumer shapes, Matrix4f matrix, double top) {
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH, top - HEADER, HALF_WIDTH, top, LAYER, BAND);
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH, top - HEADER - STRIPE, HALF_WIDTH, top - HEADER, LAYER, ACCENT);
    }

    /** The page of flight information's bands and tiles, under its text. */
    private static void panels(VertexConsumer shapes, Matrix4f matrix) {
        header(shapes, matrix, HALF_HEIGHT);
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH, -HALF_HEIGHT + FOOTER, LAYER, BAND);
        for (Tile tile : TILES) {
            ScreenDrawing.rect(shapes, matrix, tile.x0(), tile.y0(), tile.x1(), tile.y1(), LAYER, TILE);
        }
    }

    /** The header's text, on every page, its top edge this high: the page's title, and what the airliner is doing. */
    private static List<ScreenDrawing.Label> header(Font font, String title, String status, double top) {
        List<ScreenDrawing.Label> text = new ArrayList<>();
        double header = top - HEADER / 2;
        text.add(ScreenDrawing.Label.of(font, Component.translatable(KEY + title), -HALF_WIDTH + MARGIN, header, TITLE, WHITE, ScreenDrawing.Align.LEFT));
        text.add(ScreenDrawing.Label.of(font, Component.translatable(KEY + "status." + status), HALF_WIDTH - MARGIN, header, TITLE, ACCENT,
            ScreenDrawing.Align.RIGHT));
        return text;
    }

    /** The page of flight information's text: the header, a reading in each tile and the footer. */
    private static List<ScreenDrawing.Label> flightInfo(Font font, Reading[] readings, String status, long flightTicks) {
        List<ScreenDrawing.Label> text = header(font, "title", status, HALF_HEIGHT);
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

    /** The flight map's row of tiles, across its page under the route map, down to the page's bottom margin. */
    private static Tile[] flightMapTiles() {
        double top = FLIGHT_MAP_BOTTOM - MARGIN;
        double bottom = -HALF_WIDTH + MARGIN;
        double width = (FLIGHT_MAP_WIDTH - (FLIGHT_MAP_READINGS.length + 1) * MARGIN) / FLIGHT_MAP_READINGS.length;
        Tile[] tiles = new Tile[FLIGHT_MAP_READINGS.length];
        for (int column = 0; column < tiles.length; column++) {
            double x0 = -HALF_WIDTH + MARGIN + column * (width + MARGIN);
            tiles[column] = new Tile(x0, bottom, x0 + width, top);
        }
        return tiles;
    }

    /**
     * An assembled airliner as its screens show it: its flight, followed once a tick however many screens ask, as the
     * airliner was that tick, and the page's text, laid out from them once a tick for whichever screens are drawn.
     */
    static final class Flight implements AutoCloseable {
        private final UUID airliner;
        private final Trip trip = new Trip();
        @Nullable
        private SableClient.Ride ride;
        /** Its route map, made the first time it is shown. */
        @Nullable
        private RouteMap map;
        /** The route map page's text, and the game time it was laid out at. */
        private List<ScreenDrawing.Label> mapPage = List.of();
        private long mapLaidOut = Long.MIN_VALUE;
        /** The flight map page's text, and the game time it was laid out at. */
        private List<ScreenDrawing.Label> flightMapPage = List.of();
        private long flightMapLaidOut = Long.MIN_VALUE;
        /** The game times something last asked after it, it was last followed and its page last laid out at. */
        private long asked;
        private long followed = Long.MIN_VALUE;
        private long laidOut = Long.MIN_VALUE;
        private List<ScreenDrawing.Label> page = List.of();

        Flight(UUID airliner, long now) {
            this.airliner = airliner;
            asked = now;
        }

        void follow(BlockEntity screen, long now) {
            asked = now;
            if (now == followed) {
                return;
            }
            SableClient.Ride found = SableClient.ride(screen);
            if (found != null) {
                followed = now;
                ride = found;
                trip.update(found, now);
                see(screen.getLevel(), found);
            }
        }

        /** Takes in the land below the airliner for the route map, whether or not the map is being shown. */
        private static void see(@Nullable Level level, SableClient.Ride ride) {
            TerrainMap terrain = level != null ? TerrainMap.of(level) : null;
            if (terrain != null) {
                terrain.see(level, ride.position().x, ride.position().z);
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

        /** The route map page's text: only its header, the map under it. */
        List<ScreenDrawing.Label> mapPage(Font font, long now) {
            if (mapLaidOut != now && ride != null) {
                mapLaidOut = now;
                mapPage = header(font, "map_title", trip.status(ride), HALF_HEIGHT);
            }
            return mapPage;
        }

        /** The flight map page's text: its header, and three of the figures under the map. */
        List<ScreenDrawing.Label> flightMapPage(Font font, long now) {
            if (flightMapLaidOut != now && ride != null) {
                flightMapLaidOut = now;
                flightMapPage = header(font, "map_title", trip.status(ride), HALF_WIDTH);
                Reading[] readings = readings(ride, trip);
                for (int i = 0; i < FLIGHT_MAP_TILES.length; i++) {
                    reading(font, flightMapPage, FLIGHT_MAP_TILES[i], readings[FLIGHT_MAP_READINGS[i]]);
                }
            }
            return flightMapPage;
        }

        /** The route map, drawn again as it falls due. */
        RouteMap map(Level level, long now) {
            if (map == null) {
                map = new RouteMap(airliner);
            }
            if (ride != null) {
                map.update(level, trip.route(), ride.position().x, ride.position().z, trip.track(), now);
            }
            return map;
        }

        @Override
        public void close() {
            if (map != null) {
                map.close();
                map = null;
            }
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
        /** The points of the route flown since take-off, in world x and z, a few blocks apart, for the route map. */
        private final List<Vector2d> route = new ArrayList<>();
        /** The compass heading it was last seen going, 0 north and 90 east; NaN before it has moved. */
        private double track = Double.NaN;

        /** Takes in the airliner as it is this tick, once a tick. */
        void update(SableClient.Ride ride, long gameTime) {
            boolean onGround = ground.onGround(ride);
            if (!onGround && !airborne) {
                airborne = true;
                tookOff = gameTime;
                distance = 0;
                route.clear();
            } else if (onGround && airborne) {
                airborne = false;
                landed = gameTime;
            }
            Vector3d here = ride.position();
            if (airborne && last != null) {
                distance += Math.hypot(here.x - last.x, here.z - last.z);
            }
            if (airborne && (route.isEmpty() || route.getLast().distance(here.x, here.z) >= ROUTE_STEP)) {
                addToRoute(here);
            }
            last = here;
            Vector3d velocity = ride.velocity();
            if (Math.hypot(velocity.x, velocity.z) > MOVING) {
                track = (Math.toDegrees(Math.atan2(velocity.x, -velocity.z)) + 360) % 360;
            }
        }

        /**
         * Adds a point to the route; past {@link #MOST_ROUTE} points, a long flight keeps every other one, its route
         * drawn as it was, a little less finely.
         */
        private void addToRoute(Vector3d here) {
            route.add(new Vector2d(here.x, here.z));
            if (route.size() <= MOST_ROUTE) {
                return;
            }
            for (int i = route.size() - 2; i > 0; i -= 2) {
                route.remove(i);
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

        List<Vector2d> route() {
            return route;
        }

        double track() {
            return track;
        }

        double wheelHeight(SableClient.Ride ride) {
            return ground.wheelHeight(ride);
        }
    }
}
