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
import net.minecraft.util.Mth;
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
    /**
     * How loud an engine is, as a share of full: idling, and heard from inside the airliner it is part of, muffled by
     * the hull. Never above full, which only makes Minecraft cut it off there: how far it carries is set with the sound
     * itself (see tools/generate_resources.mjs).
     */
    private static final float IDLE_VOLUME = 0.3F;
    private static final float ABOARD_VOLUME = 0.35F;
    /** Height in the world above which running engines leave contrails, at any thrust: even idling, they run hot. */
    private static final double CONTRAIL_HEIGHT = 120;
    private static final float CONTRAIL_SPOOL = 0.05F;
    /**
     * How far apart, at most, an engine's contrail puffs are laid along the way it came since the tick before, in
     * blocks: a little less than a new puff is wide, so they touch. At the autopilot's default cruising speed (50
     * blocks a second) that is two a tick.
     */
    private static final double CONTRAIL_SPACING = 1.4;
    /**
     * The most contrail puffs an engine lays a tick, and how many each stands for (see {@link ContrailParticle}): an
     * engine going slower lays fewer, each as opaque as that many over one another, so its trail is as thick and white
     * with fewer puffs to tick and draw. Going faster than this many spacings a tick, they are laid further apart.
     */
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
                // Those stopped are let go of as a new one starts: each holds its engine, which would keep its level.
                PLAYING.values().removeIf(EngineSound::isStopped);
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
        int puffs = Mth.clamp(Mth.ceil(exhaust.distanceTo(last) / CONTRAIL_SPACING), 1, CONTRAIL_PUFFS);
        double standsFor = CONTRAIL_PUFFS / (double) puffs;
        for (int i = 0; i < puffs; i++) {
            Vec3 at = last.lerp(exhaust, (i + 1) / (double) puffs);
            engine.getLevel().addParticle(AerolargeRegistry.CONTRAIL.get(), at.x, at.y, at.z, standsFor, 0, 0);
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
            volume = Mth.lerp(spool, IDLE_VOLUME, 1F) * (aboard ? ABOARD_VOLUME : 1F);
            pitch = 0.55F + 0.85F * spool;
        }
    }
}
