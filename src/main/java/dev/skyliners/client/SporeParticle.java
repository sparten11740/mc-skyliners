package dev.skyliners.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A glowing spore of the spore drive the rocket's tardigrade jumps through (see {@code entity.Tardigrade}): a speck
 * of blue-white light drifting the way it was sent and slowing, flickering as it goes and shrinking away. It glows
 * whatever the light round it, and is drawn solid, with the particles drawn before see-through blocks, so it shows
 * through the tank's glass.
 */
public final class SporeParticle extends TextureSheetParticle {
    /** How long a spore glows, from and to, in ticks. */
    private static final int LIFETIME_FROM = 20;
    private static final int LIFETIME_TO = 40;
    /** How big a spore is at most, in blocks, and how much of its speed it keeps each tick. */
    private static final float SIZE = 0.09F;
    private static final double DRAG = 0.9;

    private final float size;

    private SporeParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        lifetime = LIFETIME_FROM + random.nextInt(LIFETIME_TO - LIFETIME_FROM);
        gravity = 0;
        hasPhysics = false;
        friction = (float) DRAG;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        size = SIZE * (0.5F + random.nextFloat() * 0.5F);
        quadSize = size;
        float blue = 0.85F + random.nextFloat() * 0.15F;
        setColor(0.6F + random.nextFloat() * 0.3F, blue, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        float left = 1 - (float) age / lifetime;
        quadSize = size * left * (0.75F + 0.25F * random.nextFloat());
    }

    /** It glows as brightly in the dark as in the light. */
    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    /** Makes a spore drifting the way it was sent. */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new SporeParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
