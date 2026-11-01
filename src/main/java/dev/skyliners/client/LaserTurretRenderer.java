package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.block.LaserTurretBlock;
import dev.skyliners.block.LaserTurretBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * Draws the laser path clearer's turret out of its hatch under the nose (see {@link LaserTurretBlockEntity}): lowered
 * out of the block as it is armed and raised back in as it is disarmed, its head swung round and its barrel tilted to
 * point where it is shooting. Folded in, nothing is drawn: the hatch is the block's own model.
 *
 * <p>A client hears of each bolt the turret fires, where it leaves from and where it strikes (see {@link LaserBolts}),
 * and of nothing else it aims at. So the turret takes the bolts that left its muzzle as its own and turns toward the
 * newest one's strike as a turret on a motor would: gathering speed, no faster than {@link #TOP_SPEED}, and slowing as
 * it comes round. Once it has not fired for {@link #HOLD_TICKS}, or is disarmed, it turns back to face ahead. Hanging
 * under the belly, it turns only so far: {@link #MOST_SWING} either way, its barrel down to {@link #MOST_DOWN} and up
 * to {@link #MOST_UP}, short of the skin above it. Its bolts are drawn from the tip of its barrel as it is turned.
 */
public class LaserTurretRenderer implements BlockEntityRenderer<LaserTurretBlockEntity> {
    /** How far from the camera the turret is drawn, in blocks: from out on the apron. */
    private static final int VIEW_DISTANCE = 128;
    /**
     * Where the turret turns about, in blocks up its model from the bottom of the block it is drawn in: the middle of
     * its housing, level with its barrel, on the stalk's line. The barrel's model reaches back to it.
     */
    private static final float PIVOT_HEIGHT = 11 / 16F;
    /** How far out from where it turns about the tip of the barrel's glowing emitter is, in blocks: its bolts start there. */
    private static final double TIP = 13 / 16D;
    /** How far it swings either way from straight ahead, in degrees: square to the nose at most, never round behind. */
    private static final double MOST_SWING = 90;
    /**
     * How far its barrel tilts down and up, in degrees: down to nearly straight below, and up only until the tip of the
     * barrel is just short of the belly's skin above it.
     */
    private static final double MOST_DOWN = 80;
    private static final double MOST_UP = 15;
    /** How fast it turns at most, in degrees a second, either way. */
    private static final double TOP_SPEED = 180;
    /** About how long it takes to come round to where it is to point, in seconds, unless the top speed holds it back. */
    private static final double SETTLE = 0.15;
    /** How long it keeps pointing where its last bolt went, in ticks, before it turns back to face ahead. */
    private static final double HOLD_TICKS = 20;
    /** The longest a frame is counted as, in seconds, so the turret does not leap after the game stalls. */
    private static final double LONGEST_FRAME = 0.25;
    /** How far round the turret's middle anything of it is drawn, in blocks: its barrel's tip, tilted any way. */
    private static final float REACH = 1.5F;

    private final BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
    /** How each turret is turned as drawn, and where it turns to. */
    private final Map<LaserTurretBlockEntity, Aim> aims = new WeakHashMap<>();

    /**
     * How a turret is turned as drawn: swung (to its left for more) and its barrel tilted (up for more), where it is
     * turning to (in degrees), the newest of its bolts and when that was first seen, and when it was last drawn (in
     * game ticks).
     */
    private static final class Aim {
        final Turn swing = new Turn();
        final Turn tilt = new Turn();
        double swingTo;
        double tiltTo;
        @Nullable
        LaserBolts.Bolt newest;
        double firedAt = Double.NEGATIVE_INFINITY;
        double drawnAt = Double.NaN;
    }

    /** One way the turret turns: how far, in degrees, and how fast it is turning, in degrees a second. */
    private static final class Turn {
        double angle;
        double speed;
    }

    @Override
    public void render(LaserTurretBlockEntity turret, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = turret.getLevel();
        float out = turret.deployed(partialTick);
        if (level == null || out <= 0) {
            return;
        }
        BlockState state = turret.getBlockState();
        Direction nose = state.getValue(LaserTurretBlock.FACING);
        Vec3 pivot = Vec3.atLowerCornerOf(turret.getBlockPos()).add(0.5, PIVOT_HEIGHT - out, 0.5);
        SableClient.Placing placing = SableClient.placing(pivot, partialTick);
        Aim aim = aims.computeIfAbsent(turret, ignored -> new Aim());
        double now = DrawingClock.ticks(partialTick);
        Vec3 muzzle = LaserBolts.inWorld(placing, LaserTurretBlockEntity.muzzle(turret.getBlockPos(), nose));
        List<LaserBolts.Bolt> fired = LaserBolts.none() ? List.of() : LaserBolts.from(muzzle, velocity(turret));
        take(aim, fired, nose, LaserBolts.inWorld(placing, pivot), placing, now);
        turn(aim, turret.armed() && now - aim.firedAt < HOLD_TICKS, now);
        LaserBolts.drawFrom(fired, pivot.add(barrel(nose, aim).scale(TIP)));
        if (!Sight.ahead(pose.last(), 0.5F, PIVOT_HEIGHT - out, 0.5F, REACH)) {
            return;
        }
        int light = LevelRenderer.getLightColor(level, turret.getBlockPos().below());
        pose.pushPose();
        pose.translate(0, -out, 0);
        pose.rotateAround(Axis.YP.rotationDegrees((float) aim.swing.angle), 0.5F, PIVOT_HEIGHT, 0.5F);
        part(state, LaserTurretBlock.Part.HEAD, pose, buffers, light);
        // Tilted about the line across the nose, which the swing above has already turned with the head.
        Quaternionf tilt = new Quaternionf().rotationAxis((float) Math.toRadians(aim.tilt.angle), -nose.getStepZ(), 0, nose.getStepX());
        pose.rotateAround(tilt, 0.5F, PIVOT_HEIGHT, 0.5F);
        part(state, LaserTurretBlock.Part.BARREL, pose, buffers, light);
        pose.popPose();
    }

    /** One part of the turret out of its hatch, turned as the pose has it. */
    private void part(BlockState state, LaserTurretBlock.Part part, PoseStack pose, MultiBufferSource buffers, int light) {
        blocks.renderSingleBlock(state.setValue(LaserTurretBlock.PART, part), pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    /** How fast the airliner the turret is part of moves, in blocks a second; not at all when it is part of none. */
    private static Vec3 velocity(LaserTurretBlockEntity turret) {
        SableClient.Ride ride = SableClient.ride(turret);
        return ride == null ? Vec3.ZERO : new Vec3(ride.velocity().x, ride.velocity().y, ride.velocity().z);
    }

    /**
     * Takes the newest of the turret's bolts, the first time it is seen, as where to turn to: the way from where it
     * turns about (in the world, as drawn) to the bolt's strike, turned into the airliner's blocks and measured from
     * its nose, as far as the turret turns.
     */
    private static void take(Aim aim, List<LaserBolts.Bolt> fired, Direction nose, Vec3 pivot, @Nullable SableClient.Placing placing, double now) {
        if (fired.isEmpty() || fired.getLast() == aim.newest) {
            return;
        }
        aim.newest = fired.getLast();
        aim.firedAt = now;
        Vec3 way = amongBlocks(placing, aim.newest.to().subtract(pivot));
        double ahead = way.x * nose.getStepX() + way.z * nose.getStepZ();
        double left = way.x * nose.getStepZ() - way.z * nose.getStepX();
        aim.swingTo = Mth.clamp(Math.toDegrees(Math.atan2(left, ahead)), -MOST_SWING, MOST_SWING);
        aim.tiltTo = Mth.clamp(Math.toDegrees(Math.atan2(way.y, Math.hypot(ahead, left))), -MOST_DOWN, MOST_UP);
    }

    /** A way in the world turned into an airliner's blocks, the airliner placed as given; the way itself off one. */
    private static Vec3 amongBlocks(@Nullable SableClient.Placing placing, Vec3 way) {
        if (placing == null) {
            return way;
        }
        Vector3d turned = new Quaterniond(placing.turn()).conjugate().transform(new Vector3d(way.x, way.y, way.z));
        return new Vec3(turned.x, turned.y, turned.z);
    }

    /**
     * Turns the turret as far as the time since it was last drawn goes: toward where its last bolt went while it is
     * firing, and back to face ahead once it is not.
     */
    private static void turn(Aim aim, boolean firing, double now) {
        double seconds = Double.isNaN(aim.drawnAt) ? 0 : Mth.clamp((now - aim.drawnAt) / SharedConstants.TICKS_PER_SECOND, 0, LONGEST_FRAME);
        aim.drawnAt = now;
        if (seconds > 0) {
            ease(aim.swing, firing ? aim.swingTo : 0, seconds);
            ease(aim.tilt, firing ? aim.tiltTo : 0, seconds);
        }
    }

    /**
     * Turns one way toward an angle, this many seconds on, as a critically damped spring would: gathering speed from
     * rest, no faster than about {@link #TOP_SPEED}, and slowing as it comes round, without swinging past (the smooth
     * damping of Game Programming Gems 4, 1.10).
     */
    private static void ease(Turn turn, double to, double seconds) {
        double stiffness = 2 / SETTLE;
        double x = stiffness * seconds;
        double decay = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x);
        double off = Mth.clamp(turn.angle - to, -TOP_SPEED * SETTLE, TOP_SPEED * SETTLE);
        double pull = (turn.speed + stiffness * off) * seconds;
        double before = turn.angle;
        turn.speed = (turn.speed - stiffness * pull) * decay;
        turn.angle = turn.angle - off + (off + pull) * decay;
        if ((to > before) == (turn.angle > to)) {
            turn.angle = to;
            turn.speed = 0;
        }
    }

    /** The way the barrel points among the airliner's blocks as the turret is turned: its nose's way, swung and tilted. */
    private static Vec3 barrel(Direction nose, Aim aim) {
        double tilt = Math.toRadians(aim.tilt.angle);
        Vec3 swung = new Vec3(nose.getStepX(), 0, nose.getStepZ()).yRot((float) Math.toRadians(aim.swing.angle));
        return swung.scale(Math.cos(tilt)).add(0, Math.sin(tilt), 0);
    }

    /** Drawn whether or not the turret's section is in view: the block is in the hull's skin, the turret out under it. */
    @Override
    public boolean shouldRenderOffScreen(LaserTurretBlockEntity turret) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(LaserTurretBlockEntity turret) {
        return new AABB(turret.getBlockPos()).expandTowards(0, -1, 0).inflate(0.5);
    }
}
