package dev.skyliners.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A streak of light of a freighter's hyperspace jump as those outside her see it (see {@link HyperspaceWakes}): it
 * shoots from where it is born the way and as fast as it was sent, bright at its head and fading to nothing along its
 * tail, for {@link #MOVE_TICKS}; its tail follows {@link #LAG} ticks behind and catches its head up where it stopped,
 * so it grows out of a point, races, and shrinks into a point again. Sent off from her hull it shoots off ahead as she
 * goes; sent in at where she comes out it rushes in and is gone into it. It glows whatever the light, widens with
 * distance so it is seen from far off, and is drawn as a band turned to face the eye along its length, not as a square.
 */
public final class HyperspaceStreakParticle extends TextureSheetParticle {
    /** How long its head races and how far behind its tail follows, in ticks; it lives as long as both together. */
    public static final int MOVE_TICKS = 10;
    public static final int LAG = 5;
    /**
     * How wide it is, in blocks, at least, and as a share of how far it is from the eye, so it is never thinner than a
     * few pixels however far off it is seen; and how bright its tail is, as a share of its head's.
     */
    private static final float WIDTH = 0.8F;
    private static final float WIDTH_AFAR = 0.012F;
    private static final float TAIL = 0.3F;
    /** How many streaks in so many are cyan, the rest blue-white, as the streaks seen from inside her are. */
    private static final int CYAN_ONE_IN = 5;
    private static final int STREAK = 0xD8ECFF;
    private static final int STREAK_CYAN = 0x8CE6FF;
    /** The share of the streaks made with the particle setting at Decreased: half; at Minimal, none. */
    private static final float DECREASED_SHARE = 0.5F;

    /** Where it was born and how far it races a tick, in blocks; and the box it fills over its whole life. */
    private final Vec3 start;
    private final Vec3 speed;
    private final AABB box;

    private HyperspaceStreakParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        pickSprite(sprites);
        start = new Vec3(x, y, z);
        speed = new Vec3(xd, yd, zd);
        box = new AABB(start, start.add(speed.scale(MOVE_TICKS))).inflate(WIDTH);
        lifetime = MOVE_TICKS + LAG;
        gravity = 0;
        hasPhysics = false;
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        int colour = random.nextInt(CYAN_ONE_IN) == 0 ? STREAK_CYAN : STREAK;
        setColor((colour >> 16 & 0xFF) / 255F, (colour >> 8 & 0xFF) / 255F, (colour & 0xFF) / 255F);
    }

    /**
     * Its head and tail, at least as far apart as it is wide so it shows as a dot of light, drawn round the eye, its
     * corners going round anticlockwise as the eye sees them, as the game draws only the faces turned to it.
     */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float t = age + partialTicks;
        Vec3 way = speed.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : speed.normalize();
        Vec3 eye = camera.getPosition();
        Vec3 head = start.add(speed.scale(Math.min(t, MOVE_TICKS))).subtract(eye);
        Vec3 tail = start.add(speed.scale(Mth.clamp(t - LAG, 0, MOVE_TICKS))).subtract(eye);
        if (head.distanceToSqr(tail) < WIDTH * WIDTH) {
            tail = head.subtract(way.scale(WIDTH));
        }
        Vector3f near = new Vector3f((float) head.x, (float) head.y, (float) head.z);
        Vector3f far = new Vector3f((float) tail.x, (float) tail.y, (float) tail.z);
        Vector3f side = new Vector3f(far).sub(near).cross(near);
        if (side.lengthSquared() < 1e-6F) {
            return;
        }
        side.normalize(Math.max(WIDTH, WIDTH_AFAR * near.length()) / 2);
        Vector3f otherSide = new Vector3f(side).negate();
        int light = getLightColor(partialTicks);
        corner(buffer, near, otherSide, getU0(), getV0(), alpha, light);
        corner(buffer, far, otherSide, getU1(), getV0(), alpha * TAIL, light);
        corner(buffer, far, side, getU1(), getV1(), alpha * TAIL, light);
        corner(buffer, near, side, getU0(), getV1(), alpha, light);
    }

    /** One corner of its band, a side's width off its line, so textured, coloured and see-through. */
    private void corner(VertexConsumer buffer, Vector3f along, Vector3f side, float u, float v, float opacity, int light) {
        buffer.addVertex(along.x + side.x, along.y + side.y, along.z + side.z).setUv(u, v).setColor(rCol, gCol, bCol, opacity).setLight(light);
    }

    /** It glows as brightly in the dark as in the light. */
    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    /** The whole way it races, so it is not left out while its point of birth is out of sight. */
    @Override
    public AABB getRenderBoundingBox(float partialTicks) {
        return box;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /**
     * Makes a streak racing the way and as fast as it was sent, in blocks a tick; as the player's particle setting
     * asks, only some of them ({@link #DECREASED_SHARE}) or none. A jump is shown to players far past the game's own
     * limit on particles, so its setting is heeded here.
     */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            ParticleStatus setting = Minecraft.getInstance().options.particles().get();
            boolean dropped = setting == ParticleStatus.MINIMAL || setting == ParticleStatus.DECREASED && level.random.nextFloat() >= DECREASED_SHARE;
            return dropped ? null : new HyperspaceStreakParticle(level, x, y, z, xd, yd, zd, sprites);
        }
    }
}
