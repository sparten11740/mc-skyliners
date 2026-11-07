package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.turret.LaserCannonBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The whirr of the freighter's laser cannon's servos as its barrels swing to its gunner's aim (see
 * {@link LaserCannonBlockEntity}), heard on this client: its looped servo sound, louder the faster the barrels swing,
 * from where the cannon is among her blocks, fading out once they come to rest.
 */
public final class CannonServo extends AbstractTickableSoundInstance {
    /** How fast the barrels must swing to be heard, and how fast to be heard at full, in degrees a tick. */
    private static final float HEARD_FROM = 1.5F;
    private static final float FULL_AT = 12;
    /** How loud the servos are at full, as a share of full, and how fast they swell or fade, a share of full a tick. */
    private static final float LOUDEST = 0.7F;
    /** The least share of full loudness a swing heard at all is heard at. */
    private static final float QUIETEST = 0.3F;
    private static final float FADE = 0.15F;
    /**
     * The servos whirring now, by cannon, let go of with the cannon; a sound the game has stopped, as it stops every
     * sound when this client leaves a level, counts as none.
     */
    private static final Map<LaserCannonBlockEntity, CannonServo> WHIRRING = new WeakHashMap<>();

    private final LaserCannonBlockEntity cannon;
    private float wanted;

    private CannonServo(LaserCannonBlockEntity cannon, float loudness) {
        super(SkylinersRegistry.BALL_TURRET_SERVO.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.cannon = cannon;
        looping = true;
        delay = 0;
        volume = loudness;
        wanted = loudness;
        place();
    }

    /** Each client tick of a cannon: its servos heard as fast as its barrels swing, started as they begin to. */
    public static void listen(LaserCannonBlockEntity cannon) {
        float swing = cannon.swing();
        float loudness = swing < HEARD_FROM ? 0 : LOUDEST * Mth.clamp(swing / FULL_AT, QUIETEST, 1);
        CannonServo whirring = WHIRRING.get(cannon);
        if ((whirring == null || whirring.isStopped()) && loudness > 0) {
            whirring = new CannonServo(cannon, loudness);
            WHIRRING.values().removeIf(CannonServo::isStopped);
            WHIRRING.put(cannon, whirring);
            Minecraft.getInstance().getSoundManager().play(whirring);
        } else if (whirring != null) {
            whirring.wanted = loudness;
        }
    }

    @Override
    public void tick() {
        if (cannon.isRemoved() || cannon.getLevel() != Minecraft.getInstance().level) {
            stop();
            return;
        }
        volume += Mth.clamp(wanted - volume, -FADE, FADE);
        place();
        if (volume <= 0 && wanted <= 0) {
            stop();
        }
    }

    /** Heard from where the cannon is now, among her blocks as Sable carries them. */
    private void place() {
        Vec3 at = SableClient.toWorld(cannon.getLevel(), Vec3.atCenterOf(cannon.getBlockPos()));
        x = at.x;
        y = at.y;
        z = at.z;
    }
}
