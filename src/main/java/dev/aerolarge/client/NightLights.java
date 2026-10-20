package dev.aerolarge.client;

import com.mojang.logging.LogUtils;
import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.Brightness;
import dev.aerolarge.block.CabinLightBlock;
import dev.aerolarge.block.LightStripBlock;
import java.util.ArrayList;
import java.util.Comparator;
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
 * fairy lights keep only their own glow.
 *
 * <p>Twice a second the blocks round the camera are looked through for lamps set for the night: those of an airliner
 * standing as blocks in the world, and those of each assembled airliner within reach, among its own blocks. Lamps of a
 * kind in a row, shining the same way, become one of Veil's area lights, a glowing strip as long as the row (up to
 * {@link #MAX_RUN} blocks); of all those, only the {@link #MAX_LIGHTS} nearest are drawn. An assembled airliner's
 * lights are placed anew every frame, as Sable draws the airliner at that partial tick, so they fly with it.
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
    /** How far from the camera lamps are looked for, in blocks: either way along and across, and up and down. */
    private static final int REACH = 24;
    private static final int REACH_UP = 12;
    /** How often the blocks round the camera are looked through, in milliseconds. */
    private static final long LOOK_EVERY = 500;
    /** The most lights drawn at once, the nearest. */
    private static final int MAX_LIGHTS = 32;
    /** Lamps in a row at most this many blocks apart shine as one light: the crown's stand six apart. */
    private static final int MAX_GAP = 6;
    /** The longest a row of lamps is drawn as one light, in blocks, so that the rows out of reach can be left out. */
    private static final int MAX_RUN = 24;
    /** Daylight coming in this strongly, in light levels, is open sky: the outside. */
    private static final int OPEN_SKY = 15;
    /**
     * A cabin light shines into the space beside it only where that space has at most this much more daylight than
     * the space across from it, in light levels: the outside under the belly is not taken for the hold over it.
     */
    private static final int SKY_MARGIN = 4;
    /** The night's blue, its red, green and blue: an airliner's mood lighting. */
    private static final float RED = 0.25F;
    private static final float GREEN = 0.45F;
    private static final float BLUE = 1.0F;
    /** Veil's buffers, kept on while the lights are near. */
    private static final VeilLights.Buffers BUFFERS = new VeilLights.Buffers("night_lights");
    /** Whether a block is a lamp set for the night: sections whose palettes hold none are skipped whole. */
    private static final Predicate<BlockState> MAYBE_LAMP = state -> lamp(state) != null;
    /** The key the world's own lights are kept under, among the assembled airliners' own. */
    private static final UUID WORLD = new UUID(0, 0);

    /** The lights now drawn, by the frame their lamps are in: the world's, or an assembled airliner's. */
    private static final Map<UUID, Cabin> CABINS = new HashMap<>();
    /** Where a light goes in the world, the way its row runs and the way it shines: reused, as lights fly every frame. */
    private static final Vector3d MIDDLE = new Vector3d();
    private static final Vector3d ALONG = new Vector3d();
    private static final Vector3d OUT = new Vector3d();
    private static long lookDue;
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
        /** A pendant light, glowing down from under its shade. */
        PENDANT(2.5F, 9, 75, 0.3, 0.25, 0);

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

    /** A lamp set for the night as found: its kind, its block and a way it shines. */
    private record Found(Lamp lamp, BlockPos pos, Direction out) {
    }

    /** A row of lamps of one kind shining one way: the line it lies on, with the way it runs. */
    private record Row(Lamp lamp, Direction out, Direction.Axis along, BlockPos line) {
    }

    /**
     * One light: a row of lamps, in the frame their blocks are in (the world, or an assembled airliner's blocks): the
     * middle of their glowing faces, the way the row runs and the way it shines, and how far it reaches either side of
     * its middle, in blocks.
     */
    private record Glow(Lamp lamp, Vec3 middle, Vec3 along, Vec3 out, double reach) {
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

    /** Before each frame: looks for lamps now and then, and places the lights of the airliners flying. */
    public static void frame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !available()) {
            forget();
            return;
        }
        long now = Util.getMillis();
        try {
            if (now - lookDue >= 0) {
                lookDue = now + LOOK_EVERY;
                look(minecraft.level, minecraft.gameRenderer.getMainCamera().getPosition());
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

    /**
     * Looks through the blocks round the camera, in the world and among each assembled airliner's within reach, for
     * lamps set for the night; keeps the nearest rows, and adds and drops lights to match.
     */
    private static void look(Level level, Vec3 camera) {
        record Candidate(UUID frame, Glow glow, double distance) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Glow glow : rows(lamps(level, camera))) {
            candidates.add(new Candidate(WORLD, glow, glow.distance(camera)));
        }
        for (SableClient.Near airliner : SableClient.near(level, camera, REACH)) {
            for (Glow glow : rows(lamps(level, airliner.point()))) {
                candidates.add(new Candidate(airliner.airliner(), glow, glow.distance(airliner.point())));
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));
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

    /** Drops every light and lets Veil's buffers go. */
    private static void forget() {
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

    /** The kind of lamp a block is while it is set for the night and on; null for any other block or setting. */
    @Nullable
    private static Lamp lamp(BlockState state) {
        if (!state.hasProperty(Brightness.PROPERTY) || state.getValue(Brightness.PROPERTY) != Brightness.NIGHT) {
            return null;
        }
        if (state.is(AerolargeRegistry.CABIN_LIGHT.get())) {
            return state.getValue(CabinLightBlock.KEPT_ON) ? null : Lamp.PANEL;
        }
        if (state.is(AerolargeRegistry.LIGHT_STRIP.get())) {
            return Lamp.STRIP;
        }
        return state.is(AerolargeRegistry.PENDANT_LIGHT.get()) ? Lamp.PENDANT : null;
    }

    /** The lamps set for the night round a point, among the level's blocks there, each once for every way it shines. */
    private static List<Found> lamps(Level level, Vec3 centre) {
        List<Found> found = new ArrayList<>();
        int minX = Mth.floor(centre.x) - REACH;
        int maxX = Mth.floor(centre.x) + REACH;
        int minY = Mth.floor(centre.y) - REACH_UP;
        int maxY = Mth.floor(centre.y) + REACH_UP;
        int minZ = Mth.floor(centre.z) - REACH;
        int maxZ = Mth.floor(centre.z) + REACH;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }
                for (int sectionY = minY >> 4; sectionY <= maxY >> 4; sectionY++) {
                    int index = chunk.getSectionIndexFromSectionY(sectionY);
                    if (index < 0 || index >= chunk.getSectionsCount()) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(MAYBE_LAMP)) {
                        continue;
                    }
                    for (int x = Math.max(minX, chunkX << 4); x <= Math.min(maxX, (chunkX << 4) + 15); x++) {
                        for (int y = Math.max(minY, sectionY << 4); y <= Math.min(maxY, (sectionY << 4) + 15); y++) {
                            for (int z = Math.max(minZ, chunkZ << 4); z <= Math.min(maxZ, (chunkZ << 4) + 15); z++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                Lamp lamp = lamp(state);
                                if (lamp != null) {
                                    BlockPos at = pos.set(x, y, z).immutable();
                                    for (Direction out : shining(level, at, lamp, state)) {
                                        found.add(new Found(lamp, at, out));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    /** The ways a lamp shines: a strip out from its wall, a pendant down, a cabin light as {@link #panel} finds. */
    private static List<Direction> shining(Level level, BlockPos pos, Lamp lamp, BlockState state) {
        return switch (lamp) {
            case STRIP -> List.of(state.getValue(LightStripBlock.FACING).getOpposite());
            case PENDANT -> List.of(Direction.DOWN);
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
        List<Direction> ways = new ArrayList<>(2);
        for (Direction way : Direction.values()) {
            int sky = daylight(level, pos.relative(way));
            int across = daylight(level, pos.relative(way.getOpposite()));
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
     * along the cabin, across it), at most {@link #MAX_GAP} apart and {@link #MAX_RUN} long.
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
            rows.computeIfAbsent(new Row(lamp.lamp(), lamp.out(), along, line), row -> new ArrayList<>()).add(lamp);
        }
        List<Glow> glows = new ArrayList<>();
        rows.forEach((row, members) -> {
            members.sort(Comparator.comparingInt(lamp -> lamp.pos().get(row.along())));
            Found first = members.getFirst();
            Found last = first;
            for (Found lamp : members.subList(1, members.size())) {
                int at = lamp.pos().get(row.along());
                if (at - last.pos().get(row.along()) > MAX_GAP || at - first.pos().get(row.along()) >= MAX_RUN) {
                    glows.add(glow(row, first, last));
                    first = lamp;
                }
                last = lamp;
            }
            glows.add(glow(row, first, last));
        });
        return glows;
    }

    /** One light for the lamps of a row from the first to the last. */
    private static Glow glow(Row row, Found first, Found last) {
        Vec3 along = Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(row.along(), Direction.AxisDirection.POSITIVE).getNormal());
        Vec3 out = Vec3.atLowerCornerOf(row.out().getNormal());
        Lamp lamp = row.lamp();
        Vec3 middle = Vec3.atCenterOf(first.pos()).add(Vec3.atCenterOf(last.pos())).scale(0.5)
            .add(out.scale(lamp.forward)).add(0, lamp.up, 0);
        double reach = (last.pos().get(row.along()) - first.pos().get(row.along())) / 2.0 + 0.5;
        return new Glow(lamp, middle, along, out, reach);
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
        VeilLights.shine(light, lamp.distance, RED, GREEN, BLUE, lamp.brightness);
        return light;
    }
}
