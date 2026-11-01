package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.block.LaserBolt;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;

/**
 * The laser path clearer's bolts, as the server sends them (see {@link dev.skyliners.block.LaserTurretBlockEntity}):
 * each a short-lived beam from the turret's muzzle to where it strikes, a white-hot core in a red glow, fading out over
 * a few ticks. Drawn in the world after the particles, glowing like lightning.
 *
 * <p>The server says only where a bolt leaves from and where it strikes, so the turret that fired it finds it by where
 * it leaves from (see {@link #from}), turns toward it (see {@link LaserTurretRenderer}), and has it drawn from the tip
 * of its barrel as it is turned and as its airliner is drawn. A bolt no turret has found, as from one too far off to be
 * drawn, starts where the server fired it from.
 */
public final class LaserBolts {
    /** How long a bolt shows, in ticks. */
    private static final int LIFE_TICKS = 4;
    /** The beam's core and glow, half widths in blocks, and their colours (alpha first, as the glow fades from it). */
    private static final float CORE = 0.05F;
    private static final float GLOW = 0.16F;
    private static final int CORE_COLOUR = 0xFFFFE8E8;
    private static final int GLOW_COLOUR = 0xA0FF2020;
    /**
     * The most a client draws an airliner behind where the server has it, in ticks: Sable shows it a tick and a half
     * back by default, and up to three as the server's ticks come unevenly, and the drawing between ticks lags up to
     * one more. The server fires from where it has the airliner, so a bolt leaves from up to this far ahead of the
     * muzzle as drawn, along the airliner's way.
     */
    private static final double MOST_LAG_TICKS = 6;
    /** How far a bolt may leave from off that stretch ahead of a turret's muzzle and still be its own, in blocks. */
    private static final double AT_MUZZLE = 2;

    private static final List<Bolt> BOLTS = new ArrayList<>();
    private static int now;

    /**
     * A bolt, from the muzzle to where it struck, in the world, and the tick it was fired; and, once the turret that
     * fired it has found it, the tip of that turret's barrel among its airliner's blocks, where it is drawn from.
     */
    static final class Bolt {
        private final Vec3 from;
        private final Vec3 to;
        private final int fired;
        @Nullable
        private Vec3 tip;

        private Bolt(Vec3 from, Vec3 to, int fired) {
            this.from = from;
            this.to = to;
            this.fired = fired;
        }

        /** Where the bolt strikes, in the world. */
        Vec3 to() {
            return to;
        }
    }

    private LaserBolts() {
    }

    /** A bolt fired. */
    public static void add(LaserBolt bolt) {
        BOLTS.add(new Bolt(bolt.from(), bolt.to(), now));
    }

    public static void tick(ClientTickEvent.Post event) {
        now++;
        BOLTS.removeIf(bolt -> now - bolt.fired > LIFE_TICKS);
    }

    /** Whether no bolt is shown at all. */
    static boolean none() {
        return BOLTS.isEmpty();
    }

    /**
     * The bolts shown that left a turret's muzzle, oldest first, given where the muzzle is in the world as drawn and
     * how fast its airliner moves, in blocks a second: those that left from near the stretch the muzzle covers in
     * {@link #MOST_LAG_TICKS} from there, as the airliner is drawn behind where the server fired from.
     */
    static List<Bolt> from(Vec3 muzzle, Vec3 velocity) {
        Vec3 lag = velocity.scale(MOST_LAG_TICKS / SharedConstants.TICKS_PER_SECOND);
        List<Bolt> found = new ArrayList<>();
        for (Bolt bolt : BOLTS) {
            if (offStretch(bolt.from, muzzle, lag) <= AT_MUZZLE) {
                found.add(bolt);
            }
        }
        return found;
    }

    /** How far a point is from the stretch from a start along a way. */
    private static double offStretch(Vec3 point, Vec3 start, Vec3 along) {
        double length = along.lengthSqr();
        double share = length == 0 ? 0 : Mth.clamp(point.subtract(start).dot(along) / length, 0, 1);
        return point.distanceTo(start.add(along.scale(share)));
    }

    /** Has a turret's bolts drawn from the tip of its barrel, a point among its airliner's blocks. */
    static void drawFrom(List<Bolt> bolts, Vec3 tip) {
        for (Bolt bolt : bolts) {
            bolt.tip = tip;
        }
    }

    /**
     * Where a point among an assembled airliner's blocks is in the world, the airliner placed as given; the point
     * itself when it is not among an airliner's blocks.
     */
    static Vec3 inWorld(@Nullable SableClient.Placing placing, Vec3 point) {
        if (placing == null) {
            return point;
        }
        Vector3d placed = placing.transform().transformPosition(new Vector3d(point.x, point.y, point.z));
        return new Vec3(placed.x, placed.y, placed.z);
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
            float fade = 1 - Math.min(1, (now - bolt.fired + partial) / LIFE_TICKS);
            Vec3 from = bolt.tip == null ? bolt.from : inWorld(SableClient.placing(bolt.tip, partial), bolt.tip);
            beam(beams, matrix, from, bolt.to, GLOW, fade(GLOW_COLOUR, fade));
            beam(beams, matrix, from, bolt.to, CORE, fade(CORE_COLOUR, fade));
        }
        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }

    /** A beam this wide from one end of a bolt to the other: two strips crossed along it, so it shows from any side. */
    private static void beam(VertexConsumer beams, Matrix4f matrix, Vec3 from, Vec3 to, float half, int colour) {
        Vec3 along = to.subtract(from).normalize();
        Vec3 side = along.cross(Math.abs(along.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize().scale(half);
        Vec3 other = along.cross(side).normalize().scale(half);
        strip(beams, matrix, from, to, side, colour);
        strip(beams, matrix, from, to, other, colour);
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
