package dev.skyliners.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.Mth;

/**
 * A puff of a signal flare's smoke: it rises steadily, swelling and drifting a little, for half a minute, so the
 * flare's column stands a hundred blocks tall in its colour; thinning away at the end.
 */
public class FlareSmokeParticle extends TextureSheetParticle {
    /** How fast it rises, in blocks a tick, and the most it drifts sideways. */
    private static final double RISE = 0.2;
    private static final double DRIFT = 0.01;
    /** How long it lasts, in ticks, give or take a tenth. */
    private static final int LIFETIME = 500;
    /** Its size at the flare, and at the top of the column. */
    private static final float START_SIZE = 0.6F;
    private static final float END_SIZE = 4.0F;

    private FlareSmokeParticle(ClientLevel level, double x, double y, double z, ColorParticleOption colour, SpriteSet sprites) {
        super(level, x, y, z);
        setColor(colour.getRed(), colour.getGreen(), colour.getBlue());
        xd = (random.nextDouble() * 2 - 1) * DRIFT;
        yd = RISE * (0.9 + random.nextDouble() * 0.2);
        zd = (random.nextDouble() * 2 - 1) * DRIFT;
        lifetime = (int) (LIFETIME * (0.9 + random.nextDouble() * 0.2));
        hasPhysics = false;
        friction = 1.0F;
        gravity = 0;
        quadSize = START_SIZE;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        float age = (float) this.age / lifetime;
        quadSize = Mth.lerp(age, START_SIZE, END_SIZE);
        alpha = age < 0.8F ? 0.9F : 0.9F * (1 - (age - 0.8F) / 0.2F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<ColorParticleOption> {
        @Override
        public Particle createParticle(ColorParticleOption colour, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new FlareSmokeParticle(level, x, y, z, colour, sprites);
        }
    }
}
