package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.airport.Wind;
import dev.aerolarge.block.WindSockBlock;
import dev.aerolarge.block.WindSockBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Draws a wind sock's sock: a tapering tube striped orange and white along its length, from a collar at the top of its
 * pole, flying the way the wind blows (see {@link Wind}). Its spine is a chain of short pieces, each bent a little
 * further down than the one before as the wind slackens, so in a calm the sock hangs limp and narrowed, in rain it
 * stands half out, and in a storm straight out; a wave runs down it from the mouth to the tail, swinging it side to
 * side and up and down, harder and faster the stronger the wind. It is lit by the light where it is, as cloth is.
 */
public class WindSockRenderer implements BlockEntityRenderer<WindSockBlockEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/block/wind_sock.png");
    /** How many pieces its spine is made of, and how many sides its tube has. */
    private static final int PIECES = 10;
    private static final int SIDES = 8;
    /** Its length, the radius of its mouth and of its tail, and how far the mouth stands off the pole, in blocks. */
    private static final float LENGTH = 1.6F;
    private static final float MOUTH = 0.2F;
    private static final float TAIL = 0.09F;
    private static final float STANDOFF = 0.1F;
    /**
     * How far down a limp sock's tail hangs from level, in degrees, and how much of that its first piece already
     * droops; how much narrower a limp sock's tail is than a full one's.
     */
    private static final double LIMP_DROOP = 82;
    private static final double FIRST_DROOP = 0.3;
    private static final float LIMP_NARROWING = 0.45F;
    /**
     * How far its tail swings in a calm and how much further in a storm, in degrees; how fast the wave runs down it in
     * a calm and how much faster in a storm, in radians a second; and how far behind each piece is in the wave.
     */
    private static final double SWING_CALM = 3;
    private static final double SWING_STORM = 9;
    private static final double WAVE_CALM = 2.5;
    private static final double WAVE_STORM = 6;
    private static final double WAVE_LAG = 0.7;
    /** How much faster the up-and-down swing goes than the side to side, so the two never fall into step. */
    private static final double BOB = 1.3;

    @Override
    public void render(WindSockBlockEntity sock, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = sock.getLevel();
        if (level == null) {
            return;
        }
        Wind wind = Wind.at(level, partialTick);
        double seconds = DrawingClock.ticks(partialTick) / 20.0;
        Vector3f[] spine = spine(wind, seconds);
        Vector3f[][] rings = new Vector3f[PIECES + 1][];
        for (int i = 0; i <= PIECES; i++) {
            rings[i] = ring(spine, i, wind.fill());
        }
        VertexConsumer cloth = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for (int i = 0; i < PIECES; i++) {
            piece(cloth, pose.last(), spine, rings, i, light, overlay);
        }
    }

    /**
     * The middle of the tube at each end of each piece, in the block: from just off the pole the way the wind blows,
     * each piece a little lower than the one before by as much as the wind is too weak to hold it up, and swung by the
     * wave running down it.
     */
    private static Vector3f[] spine(Wind wind, double seconds) {
        float fill = wind.fill();
        double toward = Math.toRadians(wind.toward());
        double droop = Math.toRadians(LIMP_DROOP) * (1 - fill);
        double swing = Math.toRadians(SWING_CALM + SWING_STORM * fill);
        double wave = seconds * (WAVE_CALM + WAVE_STORM * fill);
        Vector3f[] spine = new Vector3f[PIECES + 1];
        spine[0] = new Vector3f((float) (0.5 + Math.sin(toward) * STANDOFF), (float) WindSockBlock.HOOK, (float) (0.5 - Math.cos(toward) * STANDOFF));
        for (int i = 0; i < PIECES; i++) {
            double along = (i + 1.0) / PIECES;
            double yaw = toward + Math.sin(wave - i * WAVE_LAG) * swing * along;
            double pitch = droop * (FIRST_DROOP + (1 - FIRST_DROOP) * along) + Math.cos(BOB * wave - i * WAVE_LAG) * swing / 2 * along;
            // A compass heading: 0 toward -z (north), 90 toward +x (east); pitched down from level.
            Vector3f step = new Vector3f((float) (Math.sin(yaw) * Math.cos(pitch)), (float) -Math.sin(pitch), (float) (-Math.cos(yaw) * Math.cos(pitch)));
            spine[i + 1] = step.mul(LENGTH / PIECES).add(spine[i]);
        }
        return spine;
    }

    /** The tube's ring at one end of a piece: its sides round the spine there, square to the way it runs. */
    private static Vector3f[] ring(Vector3f[] spine, int i, float fill) {
        Vector3f along = new Vector3f(spine[Math.min(i + 1, PIECES)]).sub(spine[Math.max(i - 1, 0)]).normalize();
        Vector3f side = new Vector3f(along).cross(0, 1, 0);
        side = side.lengthSquared() < 1e-6F ? new Vector3f(1, 0, 0) : side.normalize();
        Vector3f up = new Vector3f(side).cross(along).normalize();
        float share = (float) i / PIECES;
        float radius = Mth.lerp(share, MOUTH, TAIL) * (1 - (1 - fill) * LIMP_NARROWING * share);
        Vector3f[] ring = new Vector3f[SIDES];
        for (int j = 0; j < SIDES; j++) {
            double angle = 2 * Math.PI * j / SIDES;
            ring[j] = new Vector3f(side).mul((float) Math.cos(angle) * radius).add(new Vector3f(up).mul((float) Math.sin(angle) * radius)).add(spine[i]);
        }
        return ring;
    }

    /** One piece of the tube, between the ring at its start and the ring at its end; the stripes run across it. */
    private static void piece(VertexConsumer cloth, PoseStack.Pose pose, Vector3f[] spine, Vector3f[][] rings, int i, int light, int overlay) {
        float u0 = (float) i / PIECES;
        float u1 = (float) (i + 1) / PIECES;
        for (int j = 0; j < SIDES; j++) {
            int next = (j + 1) % SIDES;
            float v0 = (float) j / SIDES;
            float v1 = (float) (j + 1) / SIDES;
            vertex(cloth, pose, rings[i][j], spine[i], u0, v0, light, overlay);
            vertex(cloth, pose, rings[i][next], spine[i], u0, v1, light, overlay);
            vertex(cloth, pose, rings[i + 1][next], spine[i + 1], u1, v1, light, overlay);
            vertex(cloth, pose, rings[i + 1][j], spine[i + 1], u1, v0, light, overlay);
        }
    }

    /** A corner of the tube, facing out from the spine. */
    private static void vertex(VertexConsumer cloth, PoseStack.Pose pose, Vector3f at, Vector3f middle, float u, float v, int light, int overlay) {
        Vector3f normal = new Vector3f(at).sub(middle).normalize();
        cloth.addVertex(pose, at.x, at.y, at.z).setColor(-1).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, normal.x, normal.y, normal.z);
    }

    /** The sock flies well past its pole's block. */
    @Override
    public AABB getRenderBoundingBox(WindSockBlockEntity sock) {
        return new AABB(sock.getBlockPos()).inflate(LENGTH + MOUTH);
    }
}
