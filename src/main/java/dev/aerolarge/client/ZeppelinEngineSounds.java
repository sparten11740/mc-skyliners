package dev.aerolarge.client;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.PropellerBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A zeppelin's engine cars heard from this client: each turning propeller plays the looping thrum of its car's big
 * diesel and the beat of its blades, louder and higher the faster it turns, played at its hub's block so that on a
 * zeppelin in flight Sable carries it along, as the airliner's engines are (see {@link EngineSounds}). Heard from
 * inside her hull it is muffled, the cars being out on her flanks.
 */
public final class ZeppelinEngineSounds {
    /** The sound each turning propeller is playing, until it stops or goes away. */
    private static final Map<PropellerBlockEntity, EngineSound> PLAYING = new WeakHashMap<>();
    /** Below this spin, a share of full speed, an engine car is silent. */
    private static final float AUDIBLE = 0.02F;
    /**
     * How loud an engine car is, as a share of full, ticking over and at full speed, and heard from inside the hull;
     * and its pitch ticking over and at full speed. How far it carries is set with the sound (see
     * tools/generate_resources.mjs).
     */
    private static final float IDLE_VOLUME = 0.35F;
    private static final float FULL_VOLUME = 1.0F;
    private static final float ABOARD_VOLUME = 0.4F;
    private static final float IDLE_PITCH = 0.7F;
    private static final float FULL_PITCH = 1.15F;

    private ZeppelinEngineSounds() {
    }

    /** Each tick of a propeller on this client: its car's engine heard while it turns. */
    public static void tick(PropellerBlockEntity propeller) {
        if (propeller.spin() <= AUDIBLE) {
            return;
        }
        EngineSound sound = PLAYING.get(propeller);
        if (sound == null || sound.isStopped()) {
            sound = new EngineSound(propeller);
            // Those stopped are let go of as a new one starts: each holds its propeller, which would keep its level.
            PLAYING.values().removeIf(EngineSound::isStopped);
            PLAYING.put(propeller, sound);
            Minecraft.getInstance().getSoundManager().play(sound);
        }
    }

    /** One engine car's running sound, following its propeller's spin from the block entity it plays at. */
    private static final class EngineSound extends AbstractTickableSoundInstance {
        private final PropellerBlockEntity propeller;

        EngineSound(PropellerBlockEntity propeller) {
            super(AerolargeRegistry.ZEPPELIN_ENGINE.get(), SoundSource.BLOCKS, RandomSource.create());
            this.propeller = propeller;
            this.looping = true;
            this.delay = 0;
            Vec3 hub = Vec3.atCenterOf(propeller.getBlockPos());
            this.x = hub.x;
            this.y = hub.y;
            this.z = hub.z;
            update();
        }

        @Override
        public void tick() {
            if (propeller.isRemoved() || propeller.spin() <= AUDIBLE) {
                stop();
                return;
            }
            update();
        }

        private void update() {
            float spin = propeller.spin();
            boolean aboard = SableClient.isAboard(Minecraft.getInstance().player, propeller);
            volume = Mth.lerp(spin, IDLE_VOLUME, FULL_VOLUME) * (aboard ? ABOARD_VOLUME : 1F);
            pitch = Mth.lerp(spin, IDLE_PITCH, FULL_PITCH);
        }
    }
}
