package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.block.LaserBolt;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The laser path clearer's bolts, as the server sends them (see {@link dev.aerolarge.block.LaserTurretBlockEntity}):
 * each a short-lived beam from the turret's muzzle to where it strikes, a white-hot core in a red glow, fading out over
 * a few ticks. Drawn in the world after the particles, glowing like lightning.
 */
public final class LaserBolts {
    /** How long a bolt shows, in ticks. */
    private static final int LIFE_TICKS = 4;
    /** The beam's core and glow, half widths in blocks, and their colours (alpha first, as the glow fades from it). */
    private static final float CORE = 0.05F;
    private static final float GLOW = 0.16F;
    private static final int CORE_COLOUR = 0xFFFFE8E8;
    private static final int GLOW_COLOUR = 0xA0FF2020;

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static int now;

    /** A bolt, from the muzzle to where it struck, and the tick it was fired. */
    private record Bolt(Vec3 from, Vec3 to, int fired) {
    }

    private LaserBolts() {
    }

    /** A bolt fired. */
    public static void add(LaserBolt bolt) {
        BOLTS.add(new Bolt(bolt.from(), bolt.to(), now));
    }

    public static void tick(ClientTickEvent.Post event) {
        now++;
        BOLTS.removeIf(bolt -> now - bolt.fired() > LIFE_TICKS);
    }

    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BOLTS.isEmpty()) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer beams = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        for (Bolt bolt : BOLTS) {
            float fade = 1 - Math.min(1, (now - bolt.fired() + partial) / LIFE_TICKS);
            beam(beams, matrix, bolt, GLOW, fade(GLOW_COLOUR, fade));
            beam(beams, matrix, bolt, CORE, fade(CORE_COLOUR, fade));
        }
        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }

    /** A beam this wide from one end of the bolt to the other: two strips crossed along it, so it shows from any side. */
    private static void beam(VertexConsumer beams, Matrix4f matrix, Bolt bolt, float half, int colour) {
        Vec3 along = bolt.to().subtract(bolt.from()).normalize();
        Vec3 side = along.cross(Math.abs(along.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize().scale(half);
        Vec3 other = along.cross(side).normalize().scale(half);
        strip(beams, matrix, bolt.from(), bolt.to(), side, colour);
        strip(beams, matrix, bolt.from(), bolt.to(), other, colour);
    }

    private static void strip(VertexConsumer beams, Matrix4f matrix, Vec3 from, Vec3 to, Vec3 half, int colour) {
        vertex(beams, matrix, from.subtract(half), colour);
        vertex(beams, matrix, from.add(half), colour);
        vertex(beams, matrix, to.add(half), colour);
        vertex(beams, matrix, to.subtract(half), colour);
    }

    private static void vertex(VertexConsumer beams, Matrix4f matrix, Vec3 at, int colour) {
        beams.addVertex(matrix, (float) at.x, (float) at.y, (float) at.z).setColor(colour);
    }

    /** A colour with its alpha scaled down this share of the way to nothing. */
    private static int fade(int colour, float share) {
        int alpha = Math.round((colour >>> 24) * share);
        return alpha << 24 | colour & 0xFFFFFF;
    }
}
