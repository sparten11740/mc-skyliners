package dev.skyliners.client;

import dev.skyliners.SkylinersClientConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.club.BandstandBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The club's band as this client hears it (see {@link BandstandBlockEntity}): as the player comes within reach of a
 * bandstand, its song, "Long Haul Swing", swells up from the stage, wherever the freighter has taken it, looping for as
 * long as they stay near and fading away as they go, as loud as the game's Jukebox/Note Blocks volume sets. The players
 * on the stage sway to the same beat (see {@link BandstandRenderer}). The client config's {@code clubMusic} silences
 * it.
 */
public final class ClubBand {
    /** How near the stage the song starts, and how far off it fades away, in blocks: apart, so it does not flicker. */
    private static final double STARTS_WITHIN = 14;
    private static final double FADES_PAST = 18;
    /** How fast the song fades away, and back, a share of its loudness a tick. */
    private static final float FADE = 0.05F;
    /** How long a beat of the song lasts, in ticks, at 160 beats a minute. */
    static final float BEAT_TICKS = 20 * 60 / 160F;

    @Nullable
    private static Song song;

    private ClubBand() {
    }

    /** The song, played from its stage, counting its own ticks so the players keep its beat. */
    private static final class Song extends AbstractTickableSoundInstance {
        private final BandstandBlockEntity stand;
        private int ticks;
        private boolean leaving;

        Song(BandstandBlockEntity stand) {
            super(SkylinersRegistry.CLUB_BAND.get(), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
            this.stand = stand;
            looping = true;
            delay = 0;
            attenuation = Attenuation.LINEAR;
            volume = FADE;
            follow();
        }

        @Override
        public void tick() {
            if (stand.isRemoved()) {
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
            Vec3 stage = SableClient.toWorld(stand.getLevel(), Vec3.atCenterOf(stand.getBlockPos()));
            x = stage.x;
            y = stage.y;
            z = stage.z;
        }
    }

    /** Once a tick: the song starts as the player comes near a bandstand, and fades as they go. */
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        LocalPlayer player = Minecraft.getInstance().player;
        if (song != null && (song.isStopped() || song.stand.isRemoved() || song.stand.getLevel() != level)) {
            song = null;
        }
        if (level == null || player == null || !SkylinersClientConfig.CLUB_MUSIC.get()) {
            if (song != null) {
                song.leaving = true;
            }
            return;
        }
        if (song != null) {
            song.leaving = distance(song.stand, player) > FADES_PAST;
            return;
        }
        BandstandBlockEntity near = nearest(level, player);
        if (near != null) {
            song = new Song(near);
            Minecraft.getInstance().getSoundManager().play(song);
        }
    }

    /** The bandstand within {@link #STARTS_WITHIN} of the player, nearest; null for none. */
    @Nullable
    private static BandstandBlockEntity nearest(ClientLevel level, LocalPlayer player) {
        BandstandBlockEntity nearest = null;
        double best = STARTS_WITHIN;
        for (BandstandBlockEntity stand : BandstandBlockEntity.loaded(level)) {
            double distance = distance(stand, player);
            if (distance < best) {
                nearest = stand;
                best = distance;
            }
        }
        return nearest;
    }

    /** How far the player is from a bandstand, in blocks, among its blocks. */
    private static double distance(BandstandBlockEntity stand, LocalPlayer player) {
        return SableClient.toPlot(stand.getLevel(), stand.getBlockPos(), player.position(), false).distanceTo(Vec3.atCenterOf(stand.getBlockPos()));
    }

    /**
     * How far through its beat a bandstand's band is now for this player, 0 to 1; less than nothing while it is silent.
     */
    static float beat(BandstandBlockEntity stand, float partialTick) {
        if (song == null || song.stand != stand || song.leaving) {
            return -1;
        }
        return (song.ticks + partialTick) % BEAT_TICKS / BEAT_TICKS;
    }
}
