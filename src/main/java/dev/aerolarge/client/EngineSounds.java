package dev.aerolarge.client;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.EngineCoreBlock;
import dev.aerolarge.block.EngineCoreBlockEntity;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The engines heard and seen from the client. Each running engine plays a looping turbofan that swells and rises in
 * pitch as it spools up, played at the engine's block so that on a flying airliner Sable carries it along (Doppler
 * and all); inside the airliner it is muffled. High up, a running engine also lays a contrail behind it.
 */
public final class EngineSounds {
    /** The sound each engine is playing, until the engine goes quiet or away. */
    private static final Map<EngineCoreBlockEntity, EngineSound> PLAYING = new WeakHashMap<>();
    /** Below this spool an engine is silent. */
    private static final float AUDIBLE = 0.02F;
    /** Height in the world above which running engines leave contrails, at any thrust: even idling, they run hot. */
    private static final double CONTRAIL_HEIGHT = 120;
    private static final float CONTRAIL_SPOOL = 0.05F;
    /** Contrail puffs laid per tick, spread along the way the engine came since the last one. */
    private static final int CONTRAIL_PUFFS = 4;
    /** How far behind the engine's middle the contrail starts, in blocks. */
    private static final double EXHAUST_BEHIND = 2.5;
    /** Where each engine's exhaust was in the world last tick, to spread its contrail along. */
    private static final Map<EngineCoreBlockEntity, Vec3> LAST_EXHAUST = new WeakHashMap<>();

    private EngineSounds() {
    }

    public static void tick(EngineCoreBlockEntity engine) {
        Minecraft minecraft = Minecraft.getInstance();
        if (engine.spool() > AUDIBLE) {
            EngineSound sound = PLAYING.get(engine);
            if (sound == null || sound.isStopped()) {
                sound = new EngineSound(engine);
                PLAYING.put(engine, sound);
                minecraft.getSoundManager().play(sound);
            }
        }
        contrail(engine);
    }

    private static void contrail(EngineCoreBlockEntity engine) {
        if (engine.getLevel() == null) {
            return;
        }
        Direction nose = engine.getBlockState().getValue(EngineCoreBlock.FACING);
        Vec3 exhaust = SableClient.toWorld(engine.getLevel(), Vec3.atCenterOf(engine.getBlockPos())
            .add(Vec3.atLowerCornerOf(nose.getOpposite().getNormal()).scale(EXHAUST_BEHIND)));
        Vec3 last = LAST_EXHAUST.put(engine, exhaust);
        if (last == null || engine.spool() < CONTRAIL_SPOOL || exhaust.y < CONTRAIL_HEIGHT) {
            return;
        }
        for (int i = 0; i < CONTRAIL_PUFFS; i++) {
            Vec3 at = last.lerp(exhaust, (i + 1) / (double) CONTRAIL_PUFFS);
            engine.getLevel().addParticle(AerolargeRegistry.CONTRAIL.get(), at.x, at.y, at.z, 0, 0, 0);
        }
    }

    /** One engine's running sound, following its spool from the block entity it plays at. */
    private static final class EngineSound extends AbstractTickableSoundInstance {
        private final EngineCoreBlockEntity engine;

        EngineSound(EngineCoreBlockEntity engine) {
            super(AerolargeRegistry.ENGINE_SOUND.get(), SoundSource.BLOCKS, RandomSource.create());
            this.engine = engine;
            this.looping = true;
            this.delay = 0;
            Vec3 centre = Vec3.atCenterOf(engine.getBlockPos());
            this.x = centre.x;
            this.y = centre.y;
            this.z = centre.z;
            update();
        }

        @Override
        public void tick() {
            if (engine.isRemoved() || engine.spool() <= AUDIBLE) {
                stop();
                return;
            }
            update();
        }

        private void update() {
            float spool = engine.spool();
            boolean aboard = SableClient.isAboard(Minecraft.getInstance().player, engine);
            // Volume above 1 carries further: a jet engine is heard from a long way off.
            volume = (0.3F + 2.7F * spool) * (aboard ? 0.35F : 1F);
            pitch = 0.55F + 0.85F * spool;
        }
    }
}
