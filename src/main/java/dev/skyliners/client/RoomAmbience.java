package dev.skyliners.client;

import dev.skyliners.SkylinersClientConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.airframe.RocketSilhouette;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A rocket's rooms as whoever stands in them hears them (see {@code tools/generate_sounds.mjs}): in her greenhouse, the
 * air through its leaves over a trickle of irrigation, and now and then a bee buzzing past somewhere round the
 * listener; in her pool, its water lapping in its tiled hall, and now and then a bigger slap of water against its side.
 * Each loop plays round the listener, swelling in as they step into its room and fading as they leave it, so a lift
 * riding through a room only passes it by; her other rooms are quiet, or have sounds of their own. A rocket whose
 * stories were left bare has none. Without the {@code roomAmbience} client setting, none of it.
 */
public final class RoomAmbience {
    /** How often the listener's room is looked for, in ticks: finding her autopilot looks through her chunks. */
    private static final int LOOK_TICKS = 20;
    /** How long a room's loop takes to swell in to how loud it is, or to fade out from it, in ticks: two seconds. */
    private static final int FADE_TICKS = 40;
    /** How far under a floor's top the listener's feet may be and still stand on it, in blocks. */
    private static final double SLACK = 0.5;
    /**
     * Where what is heard now and then in a room comes from: how far from the listener, at least and at most, in
     * blocks; and how far its pitch strays either way, a share.
     */
    private static final double NEAR = 3;
    private static final double FAR = 9;
    private static final float STRAY = 0.08F;

    /**
     * A room that is heard: its loop and how loud, what is heard now and then over it and how loud, how long between
     * two of those, at least and at most, in ticks, and how far over or under the listener's eyes they come from, in
     * blocks.
     */
    private enum Room {
        GREENHOUSE(SkylinersRegistry.GREENHOUSE_AIR, 0.3F, SkylinersRegistry.GREENHOUSE_BEE, 0.5F, 160, 480, 1.5),
        POOL(SkylinersRegistry.POOL_WATER, 0.5F, SkylinersRegistry.POOL_SLAP, 0.6F, 120, 360, -1.5);

        private final Supplier<SoundEvent> loop;
        private final float loopVolume;
        private final Supplier<SoundEvent> occasional;
        private final float occasionalVolume;
        private final int least;
        private final int most;
        private final double rise;

        Room(Supplier<SoundEvent> loop, float loopVolume, Supplier<SoundEvent> occasional, float occasionalVolume, int least, int most,
             double rise) {
            this.loop = loop;
            this.loopVolume = loopVolume;
            this.occasional = occasional;
            this.occasionalVolume = occasionalVolume;
            this.least = least;
            this.most = most;
            this.rise = rise;
        }
    }

    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<Room, Loop> LOOPS = new EnumMap<>(Room.class);
    /**
     * The room the listener stood in at the last look, null for none, and the game time the next sound heard now and
     * then in it is due; none is due until they have stood in it a while.
     */
    @Nullable
    private static Room here;
    private static long nextOccasional = Long.MIN_VALUE;

    private RoomAmbience() {
    }

    /** A room's loop, played round the listener, swelling and fading toward how loud it should be. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final float step;
        private float wanted;

        Loop(Room room) {
            super(room.loop.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            step = room.loopVolume / FADE_TICKS;
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            volume = volume < wanted ? Math.min(wanted, volume + step) : Math.max(wanted, volume - step);
            if (wanted <= 0 && volume <= 0) {
                stop();
            }
        }
    }

    /** Once a tick: the room the listener stands in looked for now and then, its loop swelled and the others faded. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            here = null;
            nextOccasional = Long.MIN_VALUE;
            LOOPS.clear();
            return;
        }
        if (level.getGameTime() % LOOK_TICKS == 0) {
            here = SkylinersClientConfig.ROOM_AMBIENCE.get() ? roomOf(player) : null;
        }
        SoundManager sounds = minecraft.getSoundManager();
        // A loop the sound engine has let go of, as it does going to another dimension, is never stopped: start anew.
        LOOPS.values().removeIf(loop -> loop.isStopped() || !sounds.isActive(loop));
        if (here != null && !LOOPS.containsKey(here)) {
            Loop loop = new Loop(here);
            LOOPS.put(here, loop);
            sounds.play(loop);
        }
        LOOPS.forEach((room, loop) -> loop.wanted = room == here ? room.loopVolume : 0);
        if (here == null) {
            nextOccasional = Long.MIN_VALUE;
        } else {
            occasional(level, player, here);
        }
    }

    /** The room of a furnished rocket the player stands in that is heard, null for none. */
    @Nullable
    private static Room roomOf(LocalPlayer player) {
        AutopilotBlockEntity autopilot = rocketAround(player);
        if (autopilot == null || !autopilot.rocketRooms()) {
            return null;
        }
        int story = storyOf(player, autopilot);
        if (story == RocketSilhouette.GREENHOUSE_STORY) {
            return Room.GREENHOUSE;
        }
        return story == RocketSilhouette.POOL_STORY ? Room.POOL : null;
    }

    /**
     * The rocket the player is in: the one they ride, or one standing as she was built, before she first flew, round
     * them; null for none. An assembled rocket's blocks are in her plot, far from any player.
     */
    @Nullable
    private static AutopilotBlockEntity rocketAround(LocalPlayer player) {
        AutopilotBlockEntity ridden = SableClient.autopilot(player);
        if (ridden != null) {
            return ridden.craft() == Craft.ROCKET ? ridden : null;
        }
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(player.level())) {
            BoundingBox frame = autopilot.frame();
            if (autopilot.craft() == Craft.ROCKET && frame != null && within(autopilot, frame, player)) {
                return autopilot;
            }
        }
        return null;
    }

    /**
     * Whether the player is within a rocket standing as built: in the box round her blocks, which is square round her
     * axis, and no further from her axis than her body's radius at her middle, as she was built, not out among her
     * fins.
     */
    private static boolean within(AutopilotBlockEntity autopilot, BoundingBox frame, LocalPlayer player) {
        int[] floors = autopilot.rocketStories();
        if (floors.length == 0 || !frame.isInside(player.blockPosition())) {
            return false;
        }
        double axisX = (frame.minX() + frame.maxX() + 1) / 2.0;
        double axisZ = (frame.minZ() + frame.maxZ() + 1) / 2.0;
        double body = RocketSilhouette.body(RocketSilhouette.height() / 2) * RocketSilhouette.scaleOf(floors[floors.length - 1] + 1);
        return Math.hypot(player.getX() - axisX, player.getZ() - axisZ) <= body;
    }

    /**
     * The story the player stands on, from the entry story up; -1 under her entry story, in her cockpit, or where her
     * stories are not known. Reckoned from her control head, which stands on her cockpit's floor, along her own up, so
     * it holds as she leans; in water, as in her pool's channel sunk into its floor, from the water's top.
     */
    private static int storyOf(LocalPlayer player, AutopilotBlockEntity autopilot) {
        int[] floors = autopilot.rocketStories();
        Level level = autopilot.getLevel();
        if (floors.length < 2 || level == null) {
            return -1;
        }
        Vec3 head = Vec3.atBottomCenterOf(autopilot.getBlockPos());
        Vec3 onCockpitFloor = SableClient.toWorld(level, head);
        Vec3 up = SableClient.toWorld(level, head.add(0, 1, 0)).subtract(onCockpitFloor);
        double swum = player.isInWater() ? RocketSilhouette.POOL_DEPTH : 0;
        double feet = floors[floors.length - 1] + 1 + player.position().subtract(onCockpitFloor).dot(up) + SLACK + swum;
        for (int story = floors.length - 1; story >= 0; story--) {
            if (feet >= floors[story] + 1) {
                return story == floors.length - 1 ? -1 : story;
            }
        }
        return -1;
    }

    /** What is heard now and then in the room, from a spot of its own round the listener, once it is due. */
    private static void occasional(ClientLevel level, LocalPlayer player, Room room) {
        long time = level.getGameTime();
        if (time < nextOccasional) {
            return;
        }
        if (nextOccasional != Long.MIN_VALUE) {
            double away = Mth.lerp(RANDOM.nextDouble(), NEAR, FAR);
            double round = RANDOM.nextDouble() * 2 * Math.PI;
            float pitch = 1 + (RANDOM.nextFloat() * 2 - 1) * STRAY;
            level.playLocalSound(player.getX() + Math.cos(round) * away, player.getEyeY() + room.rise, player.getZ() + Math.sin(round) * away,
                room.occasional.get(), SoundSource.AMBIENT, room.occasionalVolume, pitch, false);
        }
        nextOccasional = time + room.least + RANDOM.nextInt(room.most - room.least);
    }
}
