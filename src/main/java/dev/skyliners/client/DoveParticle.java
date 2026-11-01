package dev.skyliners.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A white dove released at a zeppelin's christening (see {@code Christening}): it flies off from where the bottle
 * broke, each its own way, climbing and weaving a little, its wings beating, and is gone in the sky some seven
 * seconds later.
 */
public class DoveParticle extends TextureSheetParticle {
    /** How fast a dove flies off, in blocks a tick, give or take a third, and how fast it climbs. */
    private static final double FLY = 0.3;
    private static final double CLIMB = 0.1;
    /** How far it weaves up and down as it flies, in blocks a tick, and how long a weave takes, in ticks. */
    private static final double WEAVE = 0.04;
    private static final float WEAVE_TICKS = 16;
    /** How long a wingbeat takes, in ticks. */
    private static final int WINGBEAT = 3;
    /** How long it flies, in ticks, give or take a fifth, and over how much of the end of that it fades. */
    private static final int LIFETIME = 140;
    private static final float FADE = 0.2F;
    /** How big it is drawn, in blocks. */
    private static final float SIZE = 0.5F;

    private final SpriteSet sprites;
    /**
     * How fast it climbs, in blocks a tick, and where in its weave it starts, a share of one, so the flock does not
     * weave together.
     */
    private final double climb;
    private final float weave;

    private DoveParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        double heading = random.nextDouble() * 2 * Math.PI;
        double speed = FLY * (2 / 3.0 + random.nextDouble() * 2 / 3.0);
        xd = Math.cos(heading) * speed;
        zd = Math.sin(heading) * speed;
        climb = CLIMB * (0.5 + random.nextDouble());
        yd = climb;
        weave = random.nextFloat();
        lifetime = (int) (LIFETIME * (0.8 + random.nextDouble() * 0.4));
        hasPhysics = false;
        friction = 1.0F;
        gravity = 0;
        quadSize = SIZE;
        setSprite(sprites.get(0, 1));
    }

    @Override
    public void tick() {
        yd = climb + WEAVE * Mth.sin((float) (2 * Math.PI * (age / WEAVE_TICKS + weave)));
        super.tick();
        setSprite(sprites.get(age / WINGBEAT % 2, 1));
        float left = 1 - (float) age / lifetime;
        alpha = left < FADE ? left / FADE : 1;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new DoveParticle(level, x, y, z, sprites);
        }
    }
}
