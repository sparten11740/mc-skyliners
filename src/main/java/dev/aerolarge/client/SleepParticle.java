package dev.aerolarge.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A "z" rising from a sleeping creature (see {@code dev.aerolarge.entity.Tardigrade}): it drifts slowly up, swaying a
 * little from side to side, grows as it rises and shrinks away at the last, so even someone who cannot read sees it is
 * asleep. It is drawn solid, with the particles drawn before see-through blocks, so it shows through the tank's glass.
 */
public final class SleepParticle extends TextureSheetParticle {
    /** How long a "z" rises for, in ticks. */
    private static final int LIFETIME = 50;
    /** How big it is as it starts and at its biggest, in blocks. */
    private static final float SIZE_FROM = 0.12F;
    private static final float SIZE_TO = 0.3F;
    /** How far it sways either way, in blocks a tick, and how fast, in radians a tick. */
    private static final double SWAY = 0.008;
    private static final float SWAY_RATE = 0.15F;
    /** The share of its life over which it shrinks away at the end. */
    private static final float SHRINKS = 0.25F;

    private final float swayFrom;

    private SleepParticle(ClientLevel level, double x, double y, double z, double rise, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = LIFETIME;
        gravity = 0;
        hasPhysics = false;
        xd = 0;
        yd = rise;
        zd = 0;
        swayFrom = random.nextFloat() * Mth.TWO_PI;
        quadSize = SIZE_FROM;
        setColor(0.92F, 0.94F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / lifetime;
        float grown = Mth.lerp(Math.min(1, life / (1 - SHRINKS)), SIZE_FROM, SIZE_TO);
        quadSize = life < 1 - SHRINKS ? grown : SIZE_TO * (1 - life) / SHRINKS;
        xd = SWAY * Mth.sin(swayFrom + age * SWAY_RATE);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    /** Makes a "z" rising as fast as it was sent up. */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new SleepParticle(level, x, y, z, yd, sprites);
        }
    }
}
