package dev.aerolarge.client;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.entity.Rover;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The rover's motor heard from the client: while it drives, a looping electric whine over the crunch of gravel under
 * its wheels, played where the rover is and rising in pitch and swelling with its speed, fading out once it stops.
 */
public final class RoverSounds {
    /** The motor each rover is playing, until it goes quiet or away. */
    private static final Map<Rover, Motor> PLAYING = new WeakHashMap<>();

    private RoverSounds() {
    }

    /** Each tick of each rover on this client: its motor started as it begins to move. */
    public static void tick(Rover rover) {
        if (!rover.moving()) {
            return;
        }
        Motor motor = PLAYING.get(rover);
        if (motor == null || motor.isStopped()) {
            motor = new Motor(rover);
            // Those stopped are let go of as a new one starts: each holds its rover, which would keep its level.
            PLAYING.values().removeIf(Motor::isStopped);
            PLAYING.put(rover, motor);
            Minecraft.getInstance().getSoundManager().play(motor);
        }
    }

    /**
     * Leaving the world, every motor is let go of: the sound engine cuts them off without telling them, and each holds
     * its rover, which would keep the world it was in.
     */
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PLAYING.clear();
    }

    /** One rover's motor, following it. */
    private static final class Motor extends AbstractTickableSoundInstance {
        /** Its pitch standing and at the rover's top speed, and its volume at top speed and turning on the spot. */
        private static final float LOWEST_PITCH = 0.8F;
        private static final float HIGHEST_PITCH = 1.4F;
        private static final float LOUDEST = 0.9F;
        private static final float TURNING = 0.4F;
        /** How much of the way to its new loudness it goes each tick, and below what it stops. */
        private static final float EASING = 0.25F;
        private static final float SILENT = 0.02F;

        private final Rover rover;
        private Vec3 last;

        Motor(Rover rover) {
            super(AerolargeRegistry.ROVER_MOTOR.get(), SoundSource.NEUTRAL, RandomSource.create());
            this.rover = rover;
            this.looping = true;
            this.delay = 0;
            this.volume = SILENT;
            this.pitch = LOWEST_PITCH;
            this.last = where();
            this.x = last.x;
            this.y = last.y;
            this.z = last.z;
        }

        /** Where the rover is in the world, stowed among her blocks or out in it. */
        private Vec3 where() {
            return SableClient.toWorld(rover.level(), rover.position());
        }

        @Override
        public void tick() {
            if (rover.isRemoved()) {
                stop();
                return;
            }
            Vec3 now = where();
            double speed = Math.min(now.subtract(last).horizontalDistance(), Rover.TOP_SPEED);
            last = now;
            x = now.x;
            y = now.y;
            z = now.z;
            float share = (float) (speed / Rover.TOP_SPEED);
            float wanted = rover.moving() ? Math.max(TURNING, share) * LOUDEST : 0;
            volume += (wanted - volume) * EASING;
            pitch = Mth.lerp(share, LOWEST_PITCH, HIGHEST_PITCH);
            if (!rover.moving() && volume < SILENT) {
                stop();
            }
        }
    }
}
