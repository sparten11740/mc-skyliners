package dev.aerolarge.client;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.TechnoDeckBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The secret techno room as this client hears and feels it (see {@link TechnoDeckBlockEntity}): the moment the player
 * walks into a room its track starts, from the room itself as the zeppelin flies, looping for as long as they stay,
 * and fades away as they leave. Half a minute in, on the drop, the camera shakes with the boom, hard at first and
 * dying away. The room's lights follow the same beat (see {@link TechnoRoomRenderer}).
 */
public final class TechnoRoom {
    /** The track's length and where its drop is in it, in ticks, and how long a beat lasts, at 128 a minute. */
    static final int TRACK_TICKS = 1200;
    static final int DROP_TICK = 600;
    static final float BEAT_TICKS = 20 * 60 / 128F;
    /** How long the camera shakes at the drop, in ticks, and how hard at first, in degrees. */
    private static final int SHAKE_TICKS = 40;
    private static final float SHAKE_DEGREES = 3;
    /**
     * How fast the track fades away as the player leaves, and back as they come back, a share of its loudness a tick.
     */
    private static final float FADE = 0.1F;

    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable
    private static Music music;

    private TechnoRoom() {
    }

    /**
     * The track, played from the middle of its room wherever the zeppelin has taken it. It counts its own ticks, which
     * stop with it when the game is paused, so the lights keep its beat even when the client's clock is set back to the
     * server's.
     */
    private static final class Music extends AbstractTickableSoundInstance {
        private final TechnoDeckBlockEntity deck;
        private int ticks;
        private boolean leaving;

        Music(TechnoDeckBlockEntity deck) {
            super(AerolargeRegistry.TECHNO_TRACK.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.deck = deck;
            looping = true;
            delay = 0;
            attenuation = Attenuation.LINEAR;
            volume = 1;
            follow();
        }

        @Override
        public void tick() {
            if (deck.isRemoved()) {
                stop();
                return;
            }
            ticks++;
            follow();
            volume = Math.min(1, volume + (leaving ? -FADE : FADE));
            if (volume <= 0) {
                stop();
            }
        }

        private void follow() {
            Vec3 middle = SableClient.toWorld(deck.getLevel(), middle(deck));
            x = middle.x;
            y = middle.y;
            z = middle.z;
        }
    }

    /** Once a tick: the music starts as the player walks into a room, and fades as they walk out. */
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        LocalPlayer player = Minecraft.getInstance().player;
        TechnoDeckBlockEntity inside = level == null || player == null ? null : roomAround(level, player.position().add(0, player.getBbHeight() / 2, 0));
        if (music != null && (music.isStopped() || !Minecraft.getInstance().getSoundManager().isActive(music) && music.leaving)) {
            music = null;
        }
        if (music != null) {
            music.leaving = inside != music.deck;
            return;
        }
        if (inside != null) {
            music = new Music(inside);
            Minecraft.getInstance().getSoundManager().play(music);
        }
    }

    /** The room a point in the world is in; null for none. */
    @Nullable
    private static TechnoDeckBlockEntity roomAround(ClientLevel level, Vec3 point) {
        for (TechnoDeckBlockEntity deck : TechnoDeckBlockEntity.loaded(level)) {
            if (contains(deck, point)) {
                return deck;
            }
        }
        return null;
    }

    /** Whether a point in the world is in a deck's room, wherever the zeppelin has turned it. */
    private static boolean contains(TechnoDeckBlockEntity deck, Vec3 point) {
        BlockPos from = deck.roomFrom();
        BlockPos to = deck.roomTo();
        if (from == null || to == null || deck.getLevel() == null) {
            return false;
        }
        Vec3 middle = middle(deck);
        Vec3 centre = SableClient.toWorld(deck.getLevel(), middle);
        Vec3 off = point.subtract(centre);
        Vec3 across = SableClient.toWorld(deck.getLevel(), middle.add(1, 0, 0)).subtract(centre);
        Vec3 up = SableClient.toWorld(deck.getLevel(), middle.add(0, 1, 0)).subtract(centre);
        Vec3 along = SableClient.toWorld(deck.getLevel(), middle.add(0, 0, 1)).subtract(centre);
        return Math.abs(off.dot(across)) <= (to.getX() - from.getX() + 1) / 2.0 && Math.abs(off.dot(up)) <= (to.getY() - from.getY() + 1) / 2.0
            && Math.abs(off.dot(along)) <= (to.getZ() - from.getZ() + 1) / 2.0;
    }

    /** The middle of a deck's room, among its blocks. */
    private static Vec3 middle(TechnoDeckBlockEntity deck) {
        BlockPos from = deck.roomFrom();
        BlockPos to = deck.roomTo();
        Vec3 base = Vec3.atLowerCornerOf(deck.getBlockPos());
        return from == null || to == null ? base.add(0.5, 0.5, 0.5)
            : base.add((from.getX() + to.getX() + 1) / 2.0, (from.getY() + to.getY() + 1) / 2.0, (from.getZ() + to.getZ() + 1) / 2.0);
    }

    /**
     * How far into its track a deck's room is now for this player, in ticks; less than nothing while it is not playing.
     */
    static float playing(TechnoDeckBlockEntity deck, float partialTick) {
        if (music == null || music.deck != deck || music.leaving) {
            return -1;
        }
        return (music.ticks + partialTick) % TRACK_TICKS;
    }

    /** The camera shaken on the drop, by a few degrees and dying away, while the player is in the room. */
    public static void shake(ViewportEvent.ComputeCameraAngles event) {
        if (music == null || music.leaving) {
            return;
        }
        float since = (music.ticks + (float) event.getPartialTick()) % TRACK_TICKS - DROP_TICK;
        if (since < 0 || since >= SHAKE_TICKS) {
            return;
        }
        float hard = SHAKE_DEGREES * (1 - since / SHAKE_TICKS) * (1 - since / SHAKE_TICKS);
        event.setPitch(event.getPitch() + (RANDOM.nextFloat() * 2 - 1) * hard);
        event.setYaw(event.getYaw() + (RANDOM.nextFloat() * 2 - 1) * hard);
        event.setRoll(event.getRoll() + (RANDOM.nextFloat() * 2 - 1) * hard);
    }
}
