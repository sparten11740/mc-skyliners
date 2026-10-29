package dev.aerolarge.client;

import com.mojang.logging.LogUtils;
import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.Brightness;
import dev.aerolarge.block.CabinLightBlock;
import dev.aerolarge.block.LightStripBlock;
import dev.aerolarge.block.Mount;
import dev.aerolarge.block.PendantLightBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Vector3d;
import org.slf4j.Logger;

/**
 * The cabin's blue night lighting. With the flight deck's dimmer set for the night, the cabin lights near the camera
 * that are still on (all but those kept on, see {@link CabinLightBlock}), the LED strips and the pendant lights cast
 * real blue light round the cabin, as an airliner's mood lighting does, through Veil's light renderer (see
 * {@link VeilLights}). The light is only drawn: the world's light levels, and so what spawns where, stay as the dimmer
 * has them. The lounge's starlit ceiling (a whole ceiling of panels, its stars its night light) and the den's coloured
 * fairy lights keep only their own glow. While a door stands open in flight the same lamps cast red instead, whatever
 * the dimmer's setting, all but the starlit ceiling and the fairy lights (see {@link Brightness#ALARM}).
 *
 * <p>Twice a second the blocks round the camera are looked through for lamps set for the night: those of an airliner
 * standing as blocks in the world, and those of each assembled airliner within reach, among its own blocks. Lamps of a
 * kind in a row, shining the same way, become one of Veil's area lights, a glowing strip as long as the row (up to
 * {@link #MAX_RUN} blocks); of all those, only the {@link #MAX_LIGHTS} nearest are drawn. An assembled airliner's
 * lights are placed anew every frame, as Sable draws the airliner at that partial tick, so they fly with it. A look
 * goes through the blocks a section at a time, a little in each frame (see {@link #LOOK_PER_FRAME}), so no one frame
 * waits for all of it.
 *
 * <p>While its lights are near, this class keeps Veil's buffers on (see {@link VeilLights.Buffers}), so the cabin does
 * not cause a hitch each time the player turns round.
 *
 * <p>Never where Veil's lights cannot be drawn ({@link VeilLights#USABLE}), nor with the {@code nightLights} setting
 * off; and once drawing the lights has failed, the failure is logged and they stay off until the game is restarted.
 * The lamps' own blue night glow stays.
 */
public final class NightLights {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * How far from the camera lamps are looked for, at least, in blocks: either way along and across, and up and down.
     * The look takes in the whole of every section that reaches into that, so what it finds changes only as the camera
     * crosses from one section into the next, not with every step (see {@link Box}).
     */
    private static final int REACH = 24;
    private static final int REACH_UP = 12;
    /** How often a look through the blocks round the camera starts, in milliseconds, once the one before is done. */
    private static final long LOOK_EVERY = 500;
    /**
     * How long each frame goes on with a look, in nanoseconds: it looks through a section at a time, at least one a
     * frame, until this is up.
     */
    private static final long LOOK_PER_FRAME = 250_000;
    /** The most lights drawn at once, the nearest. */
    private static final int MAX_LIGHTS = 32;
    /** Lamps in a row at most this many blocks apart shine as one light: the crown's stand six apart. */
    private static final int MAX_GAP = 6;
    /**
     * The longest a row of lamps is drawn as one light, in blocks, so that the rows out of reach can be left out. A row
     * is cut at every multiple of this along it, where it lies whatever the camera does, so a long row stays cut the same
     * way look after look: cut from its first lamp found, each step that brought another lamp into the look cut it anew,
     * and every light along it was dropped and drawn again a little longer or shorter, flickering twice a second.
     */
    private static final int MAX_RUN = 24;
    /**
     * How much nearer than a light already drawn a row must be to take its place among the {@link #MAX_LIGHTS}, in
     * blocks, so that two rows about as far off do not take turns from one look to the next.
     */
    private static final double KEEP_MARGIN = 4;
    /**
     * How far in front of its lamps' glowing faces a light is, in blocks: a pixel. In the face's own plane it lit the
     * face, and the ceiling or wall round it flush with the face, edge on, as a light so placed has no way to tell
     * which side of it they are, and they glittered in ragged blue fringes; just in front, they are behind it, unlit.
     */
    private static final double STANDOFF = 1 / 16.0;
    /** Daylight coming in this strongly, in light levels, is open sky: the outside. */
    private static final int OPEN_SKY = 15;
    /**
     * A cabin light shines into the space beside it only where that space has at most this much more daylight than
     * the space across from it, in light levels: the outside under the belly is not taken for the hold over it.
     */
    private static final int SKY_MARGIN = 4;
    /** Veil's buffers, kept on while the lights are near. */
    private static final VeilLights.Buffers BUFFERS = new VeilLights.Buffers("night_lights");
    /** Whether a block is a lamp set for the night: sections whose palettes hold none are skipped whole. */
    private static final Predicate<BlockState> MAYBE_LAMP = state -> lamp(state) != null;
    /** The key the world's own lights are kept under, among the assembled airliners' own. */
    private static final UUID WORLD = new UUID(0, 0);
    /** The six ways out of a block, by their ordinals, to look round a cabin light. */
    private static final Direction[] DIRECTIONS = Direction.values();

    /** The lights now drawn, by the frame their lamps are in: the world's, or an assembled airliner's. */
    private static final Map<UUID, Cabin> CABINS = new HashMap<>();
    /** Where a light goes in the world, the way its row runs and the way it shines: reused, as lights fly every frame. */
    private static final Vector3d MIDDLE = new Vector3d();
    private static final Vector3d ALONG = new Vector3d();
    private static final Vector3d OUT = new Vector3d();
    private static long lookDue;
    /** The look through the blocks round the camera under way, if one is. */
    @Nullable
    private static Look looking;
    /** Whether drawing the lights has failed; then none are drawn until the game is restarted. */
    private static boolean failed;

    private NightLights() {
    }

    /**
     * The lamps that shine blue at night, and how: how bright, how far their light reaches and how wide of straight out
     * (in degrees), how far their glowing face reaches either side of their row, in blocks; and where that face is in
     * the block, from its middle: so far the way it shines, and so far up.
     */
    private enum Lamp {
        /** An LED strip along the top of a wall, washing the wall, the lockers and the ceiling in front of it. */
        STRIP(3.5F, 12, 80, 0.1, -0.3125, 0.34375),
        /** A cabin light set into the crown, a wall, the hold's belly or the upper deck's floor, shining from a face. */
        PANEL(3.0F, 12, 70, 0.5, 0.5, 0),
        /** A pendant light, hung or on a wall, glowing down from under its shade. */
        PENDANT(2.5F, 9, 75, 0.3, 0.25, 0),
        /** A pendant light standing on the floor as a floor lamp, its shade half a block higher in its block. */
        FLOOR_LAMP(2.5F, 9, 75, 0.3, 0.25, 0.5);

        final float brightness;
        final float distance;
        final float angle;
        final double halfWidth;
        final double forward;
        final double up;

        Lamp(float brightness, float distance, float angle, double halfWidth, double forward, double up) {
            this.brightness = brightness;
            this.distance = distance;
            this.angle = (float) Math.toRadians(angle);
            this.halfWidth = halfWidth;
            this.forward = forward;
            this.up = up;
        }
    }

    /** The colour a lamp shines, its red, green and blue: the night's blue, or the alarm's red. */
    private enum Tint {
        /** An airliner's mood lighting. */
        NIGHT(0.25F, 0.45F, 1.0F),
        /** A door open in flight. */
        ALARM(1.0F, 0.1F, 0.08F);

        final float red;
        final float green;
        final float blue;

        Tint(float red, float green, float blue) {
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        static Tint of(BlockState lamp) {
            return Brightness.alarm(lamp) ? ALARM : NIGHT;
        }
    }

    /** A lamp set for the night, or red for the alarm, as found: its kind, its colour, its block and a way it shines. */
    private record Found(Lamp lamp, Tint tint, BlockPos pos, Direction out) {
    }

    /** A row of lamps of one kind and colour shining one way: the line it lies on, with the way it runs. */
    private record Row(Lamp lamp, Tint tint, Direction out, Direction.Axis along, BlockPos line) {
    }

    /**
     * One light: a row of lamps, in the frame their blocks are in (the world, or an assembled airliner's blocks): the
     * middle of their glowing faces, the way the row runs and the way it shines, and how far it reaches either side of
     * its middle, in blocks.
     */
    private record Glow(Lamp lamp, Tint tint, Vec3 middle, Vec3 along, Vec3 out, double reach) {
        /** How far a point is from the row, in blocks. */
        double distance(Vec3 point) {
            Vec3 offset = point.subtract(middle);
            return offset.subtract(along.scale(Mth.clamp(offset.dot(along), -reach, reach))).length();
        }
    }

    /**
     * The lights drawn in one frame, each by the row it draws, and a point among an assembled airliner's blocks to find
     * it by (none for the world's).
     */
    private static final class Cabin {
        final Map<Glow, Object> lights = new HashMap<>();
        @Nullable
        Vec3 anchor;
    }

    /** Before each frame: looks on for lamps now and then, and places the lights of the airliners flying. */
    public static void frame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !available() || !VeilLights.settled(minecraft)) {
            forget();
            return;
        }
        long now = Util.getMillis();
        try {
            if (looking != null && looking.level != minecraft.level) {
                looking = null;
            }
            if (looking == null && now - lookDue >= 0) {
                lookDue = now + LOOK_EVERY;
                looking = new Look(minecraft.level, minecraft.gameRenderer.getMainCamera().getPosition());
            }
            if (looking != null && looking.lookOn()) {
                light(looking.candidates);
                looking = null;
            }
            // The partial tick Sable draws the airliners with.
            place(event.getPartialTick().getGameTimeDeltaPartialTick(false));
            BUFFERS.keep(CABINS.values().stream().anyMatch(cabin -> !cabin.lights.isEmpty()), now);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.error("The cabin's blue night lights could not be drawn; they stay off until the game is restarted", e);
            forget();
        }
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forget();
    }

    /** Whether the lights may be drawn now at all. */
    private static boolean available() {
        return VeilLights.USABLE && !failed && AerolargeClientConfig.NIGHT_LIGHTS.get();
    }

    /** A row of lamps found, as a light, with the frame its blocks are in and how far it is from the camera. */
    private record Candidate(UUID frame, Glow glow, double distance) {
    }

    /**
     * A look through the blocks round the camera for lamps set for the night, in the world and among each assembled
     * airliner's within reach, a section at a time over as many frames as it takes.
     */
    private static final class Look {
        final Level level;
        /** The boxes of blocks still to look through, the one under way first. */
        final Deque<Box> boxes = new ArrayDeque<>();
        /** The rows of lamps found in the boxes looked through. */
        final List<Candidate> candidates = new ArrayList<>();

        Look(Level level, Vec3 camera) {
            this.level = level;
            boxes.add(new Box(WORLD, camera));
            for (SableClient.Near airliner : SableClient.near(level, camera, REACH)) {
                boxes.add(new Box(airliner.airliner(), airliner.point()));
            }
        }

        /** Looks on through the boxes, a section at a time, until this frame's time is up; whether the look is done. */
        boolean lookOn() {
            long until = Util.getNanos() + LOOK_PER_FRAME;
            do {
                Box box = boxes.peek();
                if (box == null) {
                    return true;
                }
                if (!box.next(level)) {
                    boxes.remove();
                    for (Glow glow : rows(box.found)) {
                        candidates.add(new Candidate(box.frame, glow, glow.distance(box.centre)));
                    }
                }
            } while (Util.getNanos() - until < 0);
            return boxes.isEmpty();
        }
    }

    /**
     * The blocks round a point in one frame (the world, or an assembled airliner's blocks), the whole of every section
     * within {@link #REACH} of it either way along and across and {@link #REACH_UP} up and down, looked through for lamps
     * a section at a time: up each chunk's sections, chunk by chunk.
     */
    private static final class Box {
        final UUID frame;
        final Vec3 centre;
        final int minX;
        final int maxX;
        final int minY;
        final int maxY;
        final int minZ;
        final int maxZ;
        /** The lamps found so far, each once for every way it shines. */
        final List<Found> found = new ArrayList<>();
        /** The section to look through next. */
        int chunkX;
        int chunkZ;
        int sectionY;

        Box(UUID frame, Vec3 centre) {
            this.frame = frame;
            this.centre = centre;
            minX = sectionStart(Mth.floor(centre.x) - REACH);
            maxX = sectionEnd(Mth.floor(centre.x) + REACH);
            minY = sectionStart(Mth.floor(centre.y) - REACH_UP);
            maxY = sectionEnd(Mth.floor(centre.y) + REACH_UP);
            minZ = sectionStart(Mth.floor(centre.z) - REACH);
            maxZ = sectionEnd(Mth.floor(centre.z) + REACH);
            chunkX = minX >> 4;
            chunkZ = minZ >> 4;
            sectionY = minY >> 4;
        }

        /** The first and the last block of the section a block is in, along one axis. */
        private static int sectionStart(int block) {
            return block & ~15;
        }

        private static int sectionEnd(int block) {
            return block | 15;
        }

        /** Looks through the next section for lamps; false once all have been. */
        boolean next(Level level) {
            if (chunkX > maxX >> 4) {
                return false;
            }
            lamps(level);
            if (++sectionY > maxY >> 4) {
                sectionY = minY >> 4;
                if (++chunkZ > maxZ >> 4) {
                    chunkZ = minZ >> 4;
                    chunkX++;
                }
            }
            return true;
        }

        /** The lamps set for the night in the section to look through next, as far as it lies in the box. */
        private void lamps(Level level) {
            LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
            if (chunk == null) {
                return;
            }
            int index = chunk.getSectionIndexFromSectionY(sectionY);
            if (index < 0 || index >= chunk.getSectionsCount()) {
                return;
            }
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir() || !section.maybeHas(MAYBE_LAMP)) {
                return;
            }
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = Math.max(minX, chunkX << 4); x <= Math.min(maxX, (chunkX << 4) + 15); x++) {
                for (int y = Math.max(minY, sectionY << 4); y <= Math.min(maxY, (sectionY << 4) + 15); y++) {
                    for (int z = Math.max(minZ, chunkZ << 4); z <= Math.min(maxZ, (chunkZ << 4) + 15); z++) {
                        BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                        Lamp lamp = lamp(state);
                        if (lamp != null) {
                            BlockPos at = pos.set(x, y, z).immutable();
                            for (Direction out : shining(level, at, lamp, state)) {
                                found.add(new Found(lamp, Tint.of(state), at, out));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Keeps the nearest rows of lamps a look found, those already drawn counted {@link #KEEP_MARGIN} nearer, and adds and
     * drops lights to match.
     */
    private static void light(List<Candidate> candidates) {
        candidates.sort(Comparator.comparingDouble(candidate -> candidate.distance() - (drawn(candidate) ? KEEP_MARGIN : 0)));
        Map<UUID, Set<Glow>> wanted = new HashMap<>();
        for (Candidate candidate : candidates.subList(0, Math.min(MAX_LIGHTS, candidates.size()))) {
            wanted.computeIfAbsent(candidate.frame(), frame -> new HashSet<>()).add(candidate.glow());
        }
        Iterator<Map.Entry<UUID, Cabin>> cabins = CABINS.entrySet().iterator();
        while (cabins.hasNext()) {
            Map.Entry<UUID, Cabin> entry = cabins.next();
            Set<Glow> keep = wanted.getOrDefault(entry.getKey(), Set.of());
            Iterator<Map.Entry<Glow, Object>> lights = entry.getValue().lights.entrySet().iterator();
            while (lights.hasNext()) {
                Map.Entry<Glow, Object> light = lights.next();
                if (!keep.contains(light.getKey())) {
                    VeilLights.remove(light.getValue());
                    lights.remove();
                }
            }
            if (entry.getValue().lights.isEmpty() && keep.isEmpty()) {
                cabins.remove();
            }
        }
        wanted.forEach((frame, glows) -> {
            Cabin cabin = CABINS.computeIfAbsent(frame, key -> new Cabin());
            for (Glow glow : glows) {
                if (!cabin.lights.containsKey(glow)) {
                    Object light = add(glow);
                    cabin.lights.put(glow, light);
                    if (frame.equals(WORLD)) {
                        // The world's lamps stay put: placed once.
                        VeilLights.place(light, set(MIDDLE, glow.middle()), set(ALONG, glow.along()), set(OUT, glow.out()));
                    }
                }
            }
            cabin.anchor = frame.equals(WORLD) ? null : glows.iterator().next().middle();
        });
    }

    /** Whether a row of lamps a look found is drawn already. */
    private static boolean drawn(Candidate candidate) {
        Cabin cabin = CABINS.get(candidate.frame());
        return cabin != null && cabin.lights.containsKey(candidate.glow());
    }

    /** Places the lights of each assembled airliner as Sable draws it at this partial tick. */
    private static void place(float partialTick) {
        for (Cabin cabin : CABINS.values()) {
            if (cabin.anchor == null) {
                continue;
            }
            SableClient.Placing placing = SableClient.placing(cabin.anchor, partialTick);
            if (placing == null) {
                // Gone, or not yet where it was looked for: dropped at the next look if it stays so.
                continue;
            }
            cabin.lights.forEach((glow, light) -> VeilLights.place(light,
                placing.transform().transformPosition(set(MIDDLE, glow.middle())),
                placing.turn().transform(set(ALONG, glow.along())),
                placing.turn().transform(set(OUT, glow.out()))));
        }
    }

    /** Drops every light, and the look under way, and lets Veil's buffers go. */
    private static void forget() {
        looking = null;
        if (CABINS.isEmpty() && !BUFFERS.held()) {
            return;
        }
        try {
            CABINS.values().forEach(cabin -> cabin.lights.values().forEach(VeilLights::remove));
            BUFFERS.release();
        } catch (RuntimeException | LinkageError e) {
            if (!failed) {
                failed = true;
                LOGGER.error("The cabin's blue night lights could not be let go; they stay off until the game is restarted", e);
            }
        } finally {
            CABINS.clear();
        }
    }

    /**
     * The kind of lamp a block is while it is set for the night and on, or red for the alarm, every cabin light then;
     * null for any other block or setting.
     */
    @Nullable
    private static Lamp lamp(BlockState state) {
        if (!state.hasProperty(Brightness.PROPERTY)) {
            return null;
        }
        boolean alarm = Brightness.alarm(state);
        if (!alarm && state.getValue(Brightness.PROPERTY) != Brightness.NIGHT) {
            return null;
        }
        if (state.is(AerolargeRegistry.CABIN_LIGHT.get())) {
            return !alarm && state.getValue(CabinLightBlock.KEPT_ON) ? null : Lamp.PANEL;
        }
        if (state.is(AerolargeRegistry.LIGHT_STRIP.get())) {
            return Lamp.STRIP;
        }
        if (state.is(AerolargeRegistry.PENDANT_LIGHT.get())) {
            return state.getValue(PendantLightBlock.MOUNT) == Mount.FLOOR ? Lamp.FLOOR_LAMP : Lamp.PENDANT;
        }
        return null;
    }

    /** The ways a lamp shines: a strip out from its wall, a pendant down, a cabin light as {@link #panel} finds. */
    private static List<Direction> shining(Level level, BlockPos pos, Lamp lamp, BlockState state) {
        return switch (lamp) {
            case STRIP -> List.of(state.getValue(LightStripBlock.FACING).getOpposite());
            case PENDANT, FLOOR_LAMP -> List.of(Direction.DOWN);
            case PANEL -> panel(level, pos);
        };
    }

    /**
     * The ways a cabin light shines. It has no way it faces: it is set into the skin, the upper deck's floor or a wall,
     * a block among others. It shines into each open space beside it (air or water) that is under cover: not open to
     * the sky, nor much brighter with daylight than the open space across from it, as the outside under the belly is
     * beside the hold over it. So a light in the skin shines into the cabin only, one in the floor between the decks
     * both up and down.
     */
    private static List<Direction> panel(Level level, BlockPos pos) {
        int[] daylight = new int[DIRECTIONS.length];
        for (Direction way : DIRECTIONS) {
            daylight[way.ordinal()] = daylight(level, pos.relative(way));
        }
        List<Direction> ways = new ArrayList<>(2);
        for (Direction way : DIRECTIONS) {
            int sky = daylight[way.ordinal()];
            int across = daylight[way.getOpposite().ordinal()];
            if (sky >= 0 && sky < OPEN_SKY && (across < 0 || sky <= across + SKY_MARGIN)) {
                ways.add(way);
            }
        }
        return ways;
    }

    /** The daylight reaching an open space (air or water), in light levels; -1 where the block is not open. */
    private static int daylight(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !state.getFluidState().isEmpty() ? level.getBrightness(LightLayer.SKY, pos) : -1;
    }

    /**
     * The lamps' rows, each as one light: lamps of a kind shining the same way, in a line along the cabin (or, shining
     * along the cabin, across it), at most {@link #MAX_GAP} apart, and cut at every multiple of {@link #MAX_RUN}.
     */
    private static List<Glow> rows(List<Found> lamps) {
        Direction.Axis cabin = cabinAxis(lamps);
        Map<Row, List<Found>> rows = new HashMap<>();
        for (Found lamp : lamps) {
            Direction.Axis along = switch (lamp.out().getAxis()) {
                case X -> Direction.Axis.Z;
                case Z -> Direction.Axis.X;
                case Y -> cabin;
            };
            BlockPos line = along == Direction.Axis.X ? new BlockPos(0, lamp.pos().getY(), lamp.pos().getZ()) : new BlockPos(lamp.pos().getX(), lamp.pos().getY(), 0);
            rows.computeIfAbsent(new Row(lamp.lamp(), lamp.tint(), lamp.out(), along, line), row -> new ArrayList<>()).add(lamp);
        }
        List<Glow> glows = new ArrayList<>();
        rows.forEach((row, members) -> {
            members.sort(Comparator.comparingInt(lamp -> lamp.pos().get(row.along())));
            Found first = members.getFirst();
            Found last = first;
            for (Found lamp : members.subList(1, members.size())) {
                int at = lamp.pos().get(row.along());
                boolean nextRun = Math.floorDiv(at, MAX_RUN) != Math.floorDiv(first.pos().get(row.along()), MAX_RUN);
                if (at - last.pos().get(row.along()) > MAX_GAP || nextRun) {
                    glows.add(glow(row, first, last));
                    first = lamp;
                }
                last = lamp;
            }
            glows.add(glow(row, first, last));
        });
        return glows;
    }

    /** One light for the lamps of a row from the first to the last, just in front of their glowing faces. */
    private static Glow glow(Row row, Found first, Found last) {
        Vec3 along = Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(row.along(), Direction.AxisDirection.POSITIVE).getNormal());
        Vec3 out = Vec3.atLowerCornerOf(row.out().getNormal());
        Lamp lamp = row.lamp();
        Vec3 middle = Vec3.atCenterOf(first.pos()).add(Vec3.atCenterOf(last.pos())).scale(0.5)
            .add(out.scale(lamp.forward + STANDOFF)).add(0, lamp.up, 0);
        double reach = (last.pos().get(row.along()) - first.pos().get(row.along())) / 2.0 + 0.5;
        return new Glow(lamp, row.tint(), middle, along, out, reach);
    }

    /**
     * The way the cabin runs among the lamps' blocks: the way most of the lamps shining sideways (the strips, the lights
     * in the side walls) run along the walls. Along it the rows of the lights shining up or down are found.
     */
    private static Direction.Axis cabinAxis(List<Found> lamps) {
        int alongX = 0;
        int alongZ = 0;
        for (Found lamp : lamps) {
            switch (lamp.out().getAxis()) {
                case X -> alongZ++;
                case Z -> alongX++;
                default -> {
                }
            }
        }
        return alongX > alongZ ? Direction.Axis.X : Direction.Axis.Z;
    }

    private static Vector3d set(Vector3d into, Vec3 vector) {
        return into.set(vector.x, vector.y, vector.z);
    }

    /**
     * A light for the row, an area light the size of its lamps' glowing faces, its row along its length; placed with
     * {@link VeilLights#place}.
     */
    private static Object add(Glow glow) {
        Lamp lamp = glow.lamp();
        Object light = VeilLights.add(glow.reach(), lamp.halfWidth, lamp.angle);
        Tint tint = glow.tint();
        VeilLights.shine(light, lamp.distance, tint.red, tint.green, tint.blue, lamp.brightness);
        return light;
    }
}
