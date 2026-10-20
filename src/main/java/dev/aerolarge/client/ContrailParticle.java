package dev.aerolarge.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * One puff of a contrail: it hangs where it was laid, swells and fades over a quarter of a minute, so a line of them
 * behind each engine spreads into a white trail across the sky. A puff can stand for several laid together (see
 * {@link EngineSounds}): it is then as opaque as they would be over one another, so a line of fewer puffs is as white.
 *
 * <p>For its first seconds, the part of the trail seen most, close behind the engines, it swells and fades much as a
 * puff living twice as long would, by the square of what was left of that life; from there it fades out the faster,
 * evenly to nothing at its end.
 */
public class ContrailParticle extends TextureSheetParticle {
    /** Lifetime in ticks, up to a quarter of it taken off at random. */
    private static final int LIFETIME = 300;
    /** How big a puff starts, and how big it would be at twice its lifetime, in blocks. */
    private static final float START_SIZE = 0.8F;
    private static final float END_SIZE = 4.5F;
    /** How opaque a puff is as it is laid; it fades evenly to nothing over its lifetime. */
    private static final float OPACITY = 0.75F;
    /** How many puffs laid together this one stands for, at least one. */
    private final double standsFor;

    ContrailParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, double standsFor) {
        super(level, x, y, z);
        pickSprite(sprites);
        this.standsFor = Math.max(1, standsFor);
        lifetime = LIFETIME - random.nextInt(LIFETIME / 4);
        hasPhysics = false;
        gravity = 0;
        xd = (random.nextDouble() - 0.5) * 0.004;
        yd = 0;
        zd = (random.nextDouble() - 0.5) * 0.004;
        quadSize = START_SIZE;
        alpha = opacity(0);
    }

    @Override
    public void tick() {
        super.tick();
        float age = (float) this.age / lifetime;
        quadSize = START_SIZE + (END_SIZE - START_SIZE) * (float) Math.sqrt(age / 2);
        alpha = opacity(age);
    }

    /** How opaque the puff is at this share of its life: as the puffs it stands for are over one another. */
    private float opacity(float age) {
        return (float) (1 - Math.pow(1 - OPACITY * (1 - age), standsFor));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * Lays a puff. The x speed it is laid with is how many puffs it stands for, as vanilla's notes carry their colour
     * in theirs; it does not move by it, and drifts only a little of its own accord.
     */
    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new ContrailParticle(level, x, y, z, sprites, dx);
        }
    }
}
