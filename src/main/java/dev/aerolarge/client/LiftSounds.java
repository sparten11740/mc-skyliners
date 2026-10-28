package dev.aerolarge.client;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.entity.LiftCar;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * A ship's lift heard from this client: while its car rides the shaft, its motor's low hum and whine follow it, played
 * where the car is among her blocks, so that on a ship in flight Sable carries it along, as her engines' sounds are.
 */
public final class LiftSounds {
    /** The hum each moving car is playing, until it stands still or goes away. */
    private static final Map<LiftCar, Hum> PLAYING = new WeakHashMap<>();
    /** How loud the hum is, as a share of full. */
    private static final float VOLUME = 0.7F;

    private LiftSounds() {
    }

    /** Each tick of a car on this client: its hum heard while it moves. */
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
    }

    /** One car's hum, following it up and down the shaft until it stands still. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final LiftCar car;

        Hum(LiftCar car) {
            super(AerolargeRegistry.LIFT_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
            this.car = car;
            this.looping = true;
            this.delay = 0;
            this.volume = VOLUME;
            follow();
        }

        @Override
        public void tick() {
            if (car.isRemoved() || car.heading() == 0) {
                stop();
                return;
            }
            follow();
        }

        private void follow() {
            x = car.getX();
            y = car.getY() + 1.5;
            z = car.getZ();
        }
    }
}
