package dev.aerolarge.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * One puff of a contrail: it hangs where it was laid, swells and fades over half a minute, so a line of them behind
 * each engine spreads into a white trail across the sky.
 */
public class ContrailParticle extends TextureSheetParticle {
    /** Lifetime in ticks, and how big a puff starts and ends, in blocks. */
    private static final int LIFETIME = 600;
    private static final float START_SIZE = 0.8F;
    private static final float END_SIZE = 4.5F;

    ContrailParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = LIFETIME - random.nextInt(LIFETIME / 4);
        hasPhysics = false;
        gravity = 0;
        xd = (random.nextDouble() - 0.5) * 0.004;
        yd = 0;
        zd = (random.nextDouble() - 0.5) * 0.004;
        quadSize = START_SIZE;
        alpha = 0.75F;
    }

    @Override
    public void tick() {
        super.tick();
        float age = (float) this.age / lifetime;
        quadSize = START_SIZE + (END_SIZE - START_SIZE) * (float) Math.sqrt(age);
        alpha = 0.75F * (1 - age) * (1 - age);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new ContrailParticle(level, x, y, z, sprites);
        }
    }
}
