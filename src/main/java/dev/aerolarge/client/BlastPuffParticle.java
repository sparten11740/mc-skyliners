package dev.aerolarge.client;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

/**
 * One huge, slow puff of an airliner blowing up (see {@link dev.aerolarge.selfdestruct.Blast}), as Ultrasplosive's
 * nuke draws its mushroom cloud: a few thousand of these big puffs rather than tens of thousands of vanilla's smoke, so
 * the blast reads as a solid mass and costs the client little; and, the same way, a rocket's exhaust plume and the
 * clouds and dust her lift-off raises (see {@link RocketExhaust} and the autopilot's RocketPlumes and
 * RocketLaunchBlast), and the club's fog (see {@link dev.aerolarge.block.FogMachineBlockEntity}). Every kind but the
 * fog shares the one soft puff picture, and each only lives, grows, fades, drifts and colours differently (see
 * {@link Kind}). The fire, the embers and the exhaust glow whatever the light round them while they
 * burn; the rest is lit as the sky and the blocks round it light it, never quite black.
 */
public final class BlastPuffParticle extends TextureSheetParticle {
    /** The least block light a puff is drawn with, so the smoke never goes black at night. */
    private static final int LEAST_LIGHT = 7;
    /** How far past its size a puff's drawing box reaches, so one many blocks across is not left out while it shows. */
    private static final double BOX_MARGIN = 1.5;
    /** The most a puff turns a tick, in radians, either way. */
    private static final float SPIN = 0.006F;
    /** The share of a fire puff's life it glows for, an ember's and an exhaust puff's. */
    private static final float FIRE_GLOWS = 0.8F;
    private static final float EMBER_GLOWS = 0.7F;
    private static final float EXHAUST_GLOWS = 0.45F;
    /** The share of the puffs made with the particle setting at Decreased: half; at Minimal, none. */
    private static final float DECREASED_SHARE = 0.5F;

    /** The kinds of puff, by the part of the blast they make up. */
    public enum Kind {
        /** Grey, long lived and big: the column of smoke and the cap it spreads into. */
        SMOKE,
        /** Starting white-hot, cooling through orange and red to soot: the fireball, and the column's glowing foot. */
        FIRE,
        /** Short lived and pale: the dust of the blast wave racing out. */
        WAVE,
        /** Small and glowing, falling as it cools from yellow to dull red: the burning bits thrown off the airliner. */
        EMBER,
        /**
         * Quick and fierce, white-hot where it leaves a rocket's engines, cooling through orange into smoke as it
         * streams away and swells: her exhaust plume.
         */
        EXHAUST,
        /** Big, white and billowing, rolling out over the ground and piling up: a rocket's lift-off clouds. */
        CLOUD,
        /** Big, thin and pale, hanging about twenty seconds indoors, the only kind walls stop: a fog machine's fog. */
        FOG
    }

    /**
     * How a kind of puff looks over its life: how long it lives (in ticks, at least, and up to how many more), how big
     * it starts (in blocks, at least, and up to how much bigger) and how much bigger it grows by its end, how opaque
     * it is at most, the shares of its life it fades in over and fades out from, what the air leaves of its speed each
     * tick across and up, how fast it falls (in blocks a tick each tick), and its shade.
     */
    private record Look(int lifetime, int lifetimeMore, float size, float sizeMore, float growth, float opacity, float fadeIn, float fadeOut,
                        float dragAcross, float dragUp, float fall, float shade) {
        static Look of(Kind kind, RandomSource random) {
            return switch (kind) {
                // Big, dark and slow: the smoke hangs over where the airliner was for a quarter of a minute.
                case SMOKE -> new Look(240, 160, 2.6F, 2.0F, 0.8F, 0.82F, 0.08F, 0.55F, 0.985F, 0.996F, 0, 0.30F + random.nextFloat() * 0.32F);
                // The fire glows red in the smoke a good while after the flash.
                case FIRE -> new Look(120, 60, 2.2F, 1.8F, 0.9F, 0.95F, 0.06F, 0.60F, 0.985F, 0.996F, 0, 0);
                case WAVE -> new Look(40, 20, 3.0F, 2.0F, 1.2F, 0.88F, 0.10F, 0.45F, 0.965F, 0.97F, 0, 0.66F + random.nextFloat() * 0.18F);
                // The shade is how yellow an ember burns, from red-hot to yellow.
                case EMBER -> new Look(60, 60, 0.5F, 0.6F, 0.4F, 0.9F, 0.05F, 0.45F, 0.98F, 0.98F, 0.012F, random.nextFloat());
                // Gone in a second, the plume always renewed behind it, so it streams rather than hangs.
                case EXHAUST -> new Look(18, 12, 1.5F, 1.0F, 2.4F, 0.95F, 0.03F, 0.5F, 0.95F, 0.95F, 0, 0);
                // The shade is how white a cloud is: some sunlit, some in the shadow of the others.
                case CLOUD -> new Look(110, 90, 2.8F, 2.4F, 1.5F, 0.86F, 0.06F, 0.55F, 0.955F, 0.975F, 0, 0.80F + random.nextFloat() * 0.18F);
                // Twenty seconds, fading over the last half of it, and thin, so many overlapping read as a haze.
                case FOG -> new Look(360, 80, 3.2F, 1.4F, 0.9F, 0.34F, 0.05F, 0.5F, 0.985F, 0.992F, 0, 0.78F + random.nextFloat() * 0.12F);
            };
        }
    }

    private final Kind kind;
    private final Look look;
    /** How big the puff starts, in blocks. */
    private final float size;
    private final float spin;
    /** The box the puff fills, worked out once a tick rather than every frame. */
    private AABB box;

    private BlastPuffParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, Kind kind) {
        super(level, x, y, z);
        pickSprite(sprites);
        this.kind = kind;
        look = Look.of(kind, random);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        // Never colliding with blocks is the biggest saving a puff can make. Fog keeps to its room.
        hasPhysics = kind == Kind.FOG;
        gravity = 0;
        lifetime = look.lifetime() + random.nextInt(look.lifetimeMore());
        size = look.size() + random.nextFloat() * look.sizeMore();
        spin = (random.nextFloat() - 0.5F) * 2 * SPIN;
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
        appear(0);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        move(xd, yd, zd);
        xd *= look.dragAcross();
        zd *= look.dragAcross();
        yd = yd * look.dragUp() - look.fall();
        roll += spin;
        appear((float) age / lifetime);
    }

    /** Sets the puff's size, opacity and colour at this share of its life. */
    private void appear(float t) {
        quadSize = size * (1 + look.growth() * t);
        double reach = quadSize * BOX_MARGIN;
        box = new AABB(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach);
        float fadeIn = Mth.clamp(t / look.fadeIn(), 0, 1);
        float fadeOut = Mth.clamp((1 - t) / (1 - look.fadeOut()), 0, 1);
        alpha = look.opacity() * fadeIn * fadeOut;
        switch (kind) {
            case FIRE -> fire(t);
            case SMOKE -> {
                // Smoke pales a little as it thins.
                float shade = look.shade() * (0.78F + 0.22F * t);
                setColor(shade, shade, Math.min(1, shade * 1.04F));
            }
            case WAVE -> setColor(look.shade(), look.shade() * 0.96F, look.shade() * 0.90F);
            case EMBER -> {
                float heat = 1 - t;
                setColor(0.55F + 0.45F * heat, (0.18F + 0.35F * look.shade()) * heat, 0.04F * heat);
            }
            case EXHAUST -> exhaust(t);
            case CLOUD, FOG -> setColor(look.shade(), look.shade(), Math.min(1, look.shade() * 1.03F));
        }
    }

    /**
     * A fire puff's colour at this share of its life: white-hot to orange over its first eighth, a long slide through
     * deep red, and choked into soot over its last fifth.
     */
    private void fire(float t) {
        if (t < 0.12F) {
            float k = 1 - t / 0.12F;
            setColor(1, Mth.lerp(k, 0.45F, 0.88F), Mth.lerp(k, 0.08F, 0.50F));
        } else if (t < 0.8F) {
            float k = (t - 0.12F) / 0.68F;
            setColor(Mth.lerp(k, 1, 0.62F), Mth.lerp(k, 0.45F, 0.10F), Mth.lerp(k, 0.08F, 0.06F));
        } else {
            float k = (t - 0.8F) / 0.2F;
            setColor(Mth.lerp(k, 0.62F, 0.20F), Mth.lerp(k, 0.10F, 0.18F), Mth.lerp(k, 0.06F, 0.18F));
        }
    }

    /**
     * An exhaust puff's colour at this share of its life: white-hot as it leaves the engines, yellow to orange over its
     * first third, and cooling into pale grey smoke.
     */
    private void exhaust(float t) {
        if (t < 0.3F) {
            float k = t / 0.3F;
            setColor(1, Mth.lerp(k, 0.95F, 0.55F), Mth.lerp(k, 0.75F, 0.12F));
        } else {
            float k = Math.min(1, (t - 0.3F) / 0.5F);
            setColor(Mth.lerp(k, 1, 0.72F), Mth.lerp(k, 0.55F, 0.70F), Mth.lerp(k, 0.12F, 0.68F));
        }
    }

    /** Full bright while the fire or an ember burns; otherwise the light round it, but never too dark. */
    @Override
    protected int getLightColor(float partialTick) {
        boolean burning = kind == Kind.FIRE && age < lifetime * FIRE_GLOWS || kind == Kind.EMBER && age < lifetime * EMBER_GLOWS
            || kind == Kind.EXHAUST && age < lifetime * EXHAUST_GLOWS;
        if (burning) {
            return LightTexture.FULL_BRIGHT;
        }
        int light = super.getLightColor(partialTick);
        int block = Math.max(LightTexture.block(light), LEAST_LIGHT);
        return LightTexture.pack(block, LightTexture.sky(light));
    }

    /** The box the puff fills: culled by its middle alone, as the game culls particles, a big puff would vanish. */
    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        return box;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * Makes the puffs of one kind, each moving exactly as it was sent; as the player's particle setting asks, only some
     * of them ({@link #DECREASED_SHARE}) or none. The blast is sent to the player however far off it is, past the
     * game's own limit on particles, so its setting is heeded here.
     */
    public record Provider(SpriteSet sprites, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            ParticleStatus setting = Minecraft.getInstance().options.particles().get();
            boolean dropped = setting == ParticleStatus.MINIMAL || setting == ParticleStatus.DECREASED && level.random.nextFloat() >= DECREASED_SHARE;
            return dropped ? null : new BlastPuffParticle(level, x, y, z, xd, yd, zd, sprites, kind);
        }
    }
}
