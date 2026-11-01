package dev.skyliners.client;

import dev.skyliners.SkylinersClientConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.entity.LiftCar;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A ship's lift heard from this client: while its car rides the shaft, its motor's low hum and whine follow it, and in
 * the car its music plays, soft bossa nova on the jukebox's volume, for whoever rides it or stands right by it, fading
 * away as the car comes in. Both are played where the car is among her blocks, so that on a ship in flight Sable
 * carries them along, as her engines' sounds are.
 */
public final class LiftSounds {
    /** The hum each moving car is playing, until it stands still or goes away. */
    private static final Map<LiftCar, Hum> PLAYING = new WeakHashMap<>();
    /** The music each moving car is playing for this client's player, until it has faded away. */
    private static final Map<LiftCar, Music> MUSIC = new WeakHashMap<>();
    /** How loud the hum is, as a share of full. */
    private static final float VOLUME = 0.7F;
    /** How high over the car's floor its sounds are played, in blocks: about where its riders' heads are. */
    private static final double HEAD_HEIGHT = 1.5;
    /**
     * How near to where the car's sounds are played a player not riding it has to be for its music to start for them,
     * in blocks: at its open side, or at a landing as it passes or comes in.
     */
    private static final double MUSIC_REACH = 3;
    /** How fast the music fades away as the car comes in, and back if it sets off again, a share of full a tick. */
    private static final float MUSIC_FADE = 1F / 6;
    /**
     * How long music the sound engine has not ticked is waited for before it is started afresh, in ticks: music it
     * never played, as when the jukebox's volume is down, or has let go of. Whether the engine plays it cannot be asked
     * of it, since on a ship in flight Sable plays her sounds through instances of its own.
     */
    private static final int MUSIC_RETRY = 20;

    private LiftSounds() {
    }

    /** Each tick of a car on this client: its hum heard while it moves, and its music in it. */
    public static void tick(LiftCar car) {
        if (car.heading() == 0) {
            return;
        }
        Hum hum = PLAYING.get(car);
        if (hum == null || hum.isStopped()) {
            hum = new Hum(car);
            PLAYING.values().removeIf(Hum::isStopped);
            PLAYING.put(car, hum);
            Minecraft.getInstance().getSoundManager().play(hum);
        }
        if (SkylinersClientConfig.LIFT_MUSIC.get()) {
            music(car);
        }
    }

    /**
     * Starts a riding car's music for this client's player when it is not playing yet and they are in earshot. Music
     * the sound engine has not ticked for a while (see {@link #MUSIC_RETRY}) is started afresh.
     */
    private static void music(LiftCar car) {
        Music music = MUSIC.get(car);
        long now = car.level().getGameTime();
        if (music != null && !music.isStopped() && now - music.ticked < MUSIC_RETRY) {
            return;
        }
        if (!inEarshot(car)) {
            return;
        }
        music = new Music(car, now);
        MUSIC.values().removeIf(Music::isStopped);
        MUSIC.put(car, music);
        Minecraft.getInstance().getSoundManager().play(music);
    }

    /** Whether this client's player rides a car or stands right by it, where its music is heard. */
    private static boolean inEarshot(LiftCar car) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (player.getVehicle() == car) {
            return true;
        }
        Vec3 heard = SableClient.toWorld(car.level(), car.position().add(0, HEAD_HEIGHT, 0));
        return heard.distanceToSqr(player.getEyePosition()) <= MUSIC_REACH * MUSIC_REACH;
    }

    /** A sound riding up and down the shaft with a car, played where its riders' heads are. */
    private abstract static class Riding extends AbstractTickableSoundInstance {
        protected final LiftCar car;

        Riding(SoundEvent sound, SoundSource source, LiftCar car, float volume) {
            super(sound, source, RandomSource.create());
            this.car = car;
            this.looping = true;
            this.delay = 0;
            this.volume = volume;
            follow();
        }

        protected final void follow() {
            x = car.getX();
            y = car.getY() + HEAD_HEIGHT;
            z = car.getZ();
        }
    }

    /** One car's hum, following it up and down the shaft until it stands still. */
    private static final class Hum extends Riding {
        Hum(LiftCar car) {
            super(SkylinersRegistry.LIFT_HUM.get(), SoundSource.BLOCKS, car, VOLUME);
        }

        @Override
        public void tick() {
            if (car.isRemoved() || car.heading() == 0) {
                stop();
                return;
            }
            follow();
        }
    }

    /**
     * One car's music, from the top of its loop on the hook, on the jukebox's volume; it fades away as the car stands
     * still or the client config turns it off, and back in if the car sets off again first.
     */
    private static final class Music extends Riding {
        /** The game tick the sound engine last ticked it, or it was started. */
        private long ticked;

        Music(LiftCar car, long startedAt) {
            super(SkylinersRegistry.LIFT_MUSIC.get(), SoundSource.RECORDS, car, 1);
            ticked = startedAt;
        }

        @Override
        public void tick() {
            ticked = car.level().getGameTime();
            if (car.isRemoved()) {
                stop();
                return;
            }
            follow();
            boolean playing = car.heading() != 0 && SkylinersClientConfig.LIFT_MUSIC.get();
            volume = Math.min(1, volume + (playing ? MUSIC_FADE : -MUSIC_FADE));
            if (volume <= 0) {
                stop();
            }
        }
    }
}
