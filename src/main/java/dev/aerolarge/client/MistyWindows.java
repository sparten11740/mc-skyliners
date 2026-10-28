package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3d;

/**
 * Breath on the glass: aboard a zeppelin, her promenade windows mist over on the inside while she flies through cloud
 * or rain, a pale film over the glass, and clear again once she is out of it. Holding use with an empty hand on a
 * misted pane draws on it with a finger, wiping lines of clear glass through the mist, which mists over again within
 * half a minute. Only on this client: each player's breath and drawing are their own, as on a real window. The panes
 * near the player are looked for now and then among the zeppelin's blocks, and only those are misted.
 */
public final class MistyWindows {
    /** How often the panes near the player are looked for, in ticks, and how far round them, in blocks. */
    private static final int LOOK_TICKS = 10;
    private static final int REACH = 12;
    /** How many pixels a pane's mist is across and up. */
    private static final int PIXELS = 16;
    /** How fast the mist comes and goes, a share of its thickest a tick: some five seconds. */
    private static final float MISTING = 0.01F;
    /** How thick the mist is at its thickest, as its opacity, and its pale grey. */
    private static final float THICKEST = 0.6F;
    private static final int MIST = 0xDCE4E8;
    /** How long a wiped line takes to mist over again, in ticks. */
    private static final int MISTS_OVER = 600;
    /** How wide a finger's line is, in pixels either side of where it touches. */
    private static final double FINGER = 0.8;
    /**
     * How far the finger must move, in blocks, for its squeak on the wet glass to be heard, how often at most, in
     * ticks, and how loud.
     */
    private static final double SQUEAKS_AFTER = 0.08;
    private static final int SQUEAK_TICKS = 4;
    private static final float SQUEAK_VOLUME = 0.35F;
    /**
     * How far above and below the cloud layer's bottom she must be to be in cloud, in blocks, and how much rain there
     * must be.
     */
    private static final double CLOUD_DEPTH = 5;
    private static final float RAINING = 0.2F;
    /** How far into the zeppelin off the glass the mist is drawn, in blocks, so it never flickers into it. */
    private static final double OFF_THE_GLASS = 0.01;

    /** A pane of the promenade's glass: the way its inside faces, and when each of its pixels was last wiped. */
    private record Pane(Direction inside, long[] wiped) {
        float clear(int pixel, long now) {
            long since = now - wiped[pixel];
            return wiped[pixel] == 0 || since >= MISTS_OVER ? 0 : 1 - since / (float) MISTS_OVER;
        }
    }

    private static final Map<BlockPos, Pane> PANES = new HashMap<>();
    private static float mist;
    /** Where the finger last touched, among the zeppelin's blocks, so a line is drawn on from it; null when lifted. */
    @Nullable
    private static Vec3 lastTouch;

    private MistyWindows() {
    }

    /** Once a tick: the mist coming or going, the panes near the player looked for now and then, and the finger. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        AutopilotBlockEntity zeppelin = player == null || !AerolargeClientConfig.MISTY_WINDOWS.get() ? null : SableClient.autopilot(player);
        if (level == null || zeppelin == null || zeppelin.craft() != Craft.ZEPPELIN) {
            PANES.clear();
            mist = 0;
            return;
        }
        mist = Mth.clamp(mist + (misty(level, player) ? MISTING : -MISTING), 0, 1);
        if (level.getGameTime() % LOOK_TICKS == 0) {
            look(level, player, zeppelin);
        }
        draw(minecraft, level);
    }

    /** Whether she is in cloud or rain where the player is. */
    private static boolean misty(ClientLevel level, LocalPlayer player) {
        float clouds = level.effects().getCloudHeight();
        if (!Float.isNaN(clouds) && Math.abs(player.getY() - clouds) <= CLOUD_DEPTH) {
            return true;
        }
        BlockPos at = player.blockPosition();
        return level.getRainLevel(1) >= RAINING && level.getBiome(at).value().getPrecipitationAt(at) == Biome.Precipitation.RAIN;
    }

    /**
     * The promenade's panes within reach of the player, among the zeppelin's blocks, each with the way its inside faces:
     * toward her middle. Those still near keep what was drawn on them.
     */
    private static void look(ClientLevel level, LocalPlayer player, AutopilotBlockEntity zeppelin) {
        SableClient.Frame frame = SableClient.frame(zeppelin);
        SableClient.Around around = SableClient.around(level, player.getEyePosition(), 1).stream().findFirst().orElse(null);
        if (frame == null || around == null) {
            PANES.clear();
            return;
        }
        BlockPos eye = BlockPos.containing(around.point());
        Set<BlockPos> near = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(eye.offset(-REACH, -REACH, -REACH), eye.offset(REACH, REACH, REACH))) {
            Direction inside = level.getBlockState(pos).is(AerolargeRegistry.HULL_GLASS.get()) ? inside(frame, pos) : null;
            if (inside != null && level.getBlockState(pos.relative(inside)).isAir()) {
                near.add(pos.immutable());
                PANES.computeIfAbsent(pos.immutable(), key -> new Pane(inside, new long[PIXELS * PIXELS]));
            }
        }
        PANES.keySet().retainAll(near);
    }

    /** The way from a pane toward the zeppelin's middle, across her; null on her middle itself. */
    @Nullable
    private static Direction inside(SableClient.Frame frame, BlockPos pos) {
        Direction across = frame.nose().getClockWise();
        double off = Vec3.atCenterOf(pos).subtract(frame.origin()).dot(Vec3.atLowerCornerOf(across.getNormal()));
        return Math.abs(off) < 1 ? null : off > 0 ? across.getOpposite() : across;
    }

    /** A finger drawing on a misted pane while use is held with an empty hand, a line on from where it last touched. */
    private static void draw(Minecraft minecraft, ClientLevel level) {
        HitResult hit = minecraft.hitResult;
        boolean drawing = mist > 0.3F && minecraft.options.keyUse.isDown() && minecraft.player.getMainHandItem().isEmpty()
            && hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && PANES.containsKey(block.getBlockPos());
        if (!drawing) {
            lastTouch = null;
            return;
        }
        BlockHitResult touch = (BlockHitResult) hit;
        Pane pane = PANES.get(touch.getBlockPos());
        if (touch.getDirection() != pane.inside()) {
            lastTouch = null;
            return;
        }
        Vec3 from = lastTouch == null ? touch.getLocation() : lastTouch;
        for (int step = 0; step <= PIXELS; step++) {
            wipe(touch.getBlockPos(), pane, from.lerp(touch.getLocation(), step / (double) PIXELS), level.getGameTime());
        }
        if (from.distanceTo(touch.getLocation()) >= SQUEAKS_AFTER && level.getGameTime() % SQUEAK_TICKS == 0) {
            // At the player, who draws within arm's reach: aboard, the pane's place is among her blocks in Sable's
            // plot.
            LocalPlayer player = minecraft.player;
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), AerolargeRegistry.GLASS_SQUEAK.get(), SoundSource.PLAYERS,
                SQUEAK_VOLUME, 0.9F + level.random.nextFloat() * 0.2F, false);
        }
        lastTouch = touch.getLocation();
    }

    /** The pixels of a pane within a finger's width of a point on it wiped clear now. */
    private static void wipe(BlockPos pos, Pane pane, Vec3 point, long now) {
        double u = across(pane.inside(), point.subtract(Vec3.atLowerCornerOf(pos))) * PIXELS;
        double v = (point.y - pos.getY()) * PIXELS;
        for (int pixel = 0; pixel < PIXELS * PIXELS; pixel++) {
            if (Math.hypot(pixel % PIXELS + 0.5 - u, pixel / PIXELS + 0.5 - v) <= FINGER) {
                pane.wiped()[pixel] = now;
            }
        }
    }

    /** How far across a pane's face a point within its block lies, from 0 to 1, along the face's level edge. */
    private static double across(Direction inside, Vec3 within) {
        return inside.getAxis() == Direction.Axis.X ? within.z : within.x;
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PANES.clear();
        mist = 0;
    }

    /** After the translucent blocks are drawn: every misted pane's film, row by row, the wiped pixels left clear. */
    public static void stage(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || level == null || mist <= 0 || PANES.isEmpty()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(FlatQuads.WHITE);
        VertexConsumer consumer = buffers.getBuffer(type);
        PoseStack.Pose pose = event.getPoseStack().last();
        long now = level.getGameTime();
        PANES.forEach((pos, pane) -> film(consumer, pose, camera, level, pos, pane, now, partialTick));
        buffers.endBatch(type);
    }

    /** One pane's mist: each row's pixels alike in clearness drawn as one strip. */
    private static void film(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, ClientLevel level, BlockPos pos, Pane pane, long now,
                             float partialTick) {
        SableClient.Placing placing = SableClient.placing(Vec3.atCenterOf(pos), partialTick);
        if (placing == null) {
            return;
        }
        int light = SableClient.light(level, pos.relative(pane.inside()));
        for (int row = 0; row < PIXELS; row++) {
            row(consumer, pose, camera, placing, pos, pane, row, now, light);
        }
    }

    /** One row of a pane's mist: its pixels alike in clearness side by side drawn as one strip. */
    private static void row(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, SableClient.Placing placing, BlockPos pos, Pane pane, int row,
                            long now, int light) {
        int start = 0;
        for (int pixel = 1; pixel <= PIXELS; pixel++) {
            if (pixel == PIXELS || pane.clear(row * PIXELS + pixel, now) != pane.clear(row * PIXELS + start, now)) {
                strip(consumer, pose, camera, placing, pos, pane, row, start, pixel, mist * (1 - pane.clear(row * PIXELS + start, now)), light);
                start = pixel;
            }
        }
    }

    /** A strip of a pane's mist, a row of its pixels from one across to another, so thick. */
    private static void strip(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, SableClient.Placing placing, BlockPos pos, Pane pane,
                              int row, int from, int to, float thickness, int light) {
        if (thickness <= 0) {
            return;
        }
        int colour = (int) (THICKEST * thickness * 0xFF) << 24 | MIST;
        double bottom = row / (double) PIXELS;
        double top = (row + 1) / (double) PIXELS;
        corner(consumer, pose, camera, placing, pos, pane.inside(), from / (double) PIXELS, bottom, colour, light);
        corner(consumer, pose, camera, placing, pos, pane.inside(), to / (double) PIXELS, bottom, colour, light);
        corner(consumer, pose, camera, placing, pos, pane.inside(), to / (double) PIXELS, top, colour, light);
        corner(consumer, pose, camera, placing, pos, pane.inside(), from / (double) PIXELS, top, colour, light);
    }

    /** A corner of the mist on a pane's inside face, so far across and up it, placed in the world as she is drawn. */
    private static void corner(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, SableClient.Placing placing, BlockPos pos, Direction inside,
                               double across, double up, int colour, int light) {
        double face = inside.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 + OFF_THE_GLASS : -OFF_THE_GLASS;
        Vector3d at = inside.getAxis() == Direction.Axis.X
            ? new Vector3d(pos.getX() + face, pos.getY() + up, pos.getZ() + across)
            : new Vector3d(pos.getX() + across, pos.getY() + up, pos.getZ() + face);
        placing.transform().transformPosition(at);
        consumer.addVertex(pose, (float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z)).setColor(colour)
            .setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 1, 0);
    }
}
