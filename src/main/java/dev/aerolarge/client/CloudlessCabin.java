package dev.aerolarge.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.platform.GlStateManager;
import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeClientConfig;
import java.util.List;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Keeps the clouds out of the airliner's cabin. Minecraft draws its cloud layer (some 192 blocks up) straight through
 * whatever is in it, so an airliner flying at that height had slabs of cloud cutting through its floor, walls and seats.
 * While the camera is inside an airliner's hull and that airliner reaches into the cloud layer, the clouds are not drawn
 * for the player's view at all; as soon as the camera is out of the hull (on the wing or the crown, on the airstairs,
 * looking through an outside camera) or the airliner is clear of the cloud layer, they are drawn as ever. From outside,
 * an airliner flying through the clouds still goes in and out of them.
 *
 * <p>With Fast or Fancy graphics that hides nothing to be seen through the windows: all the airliner's glass is
 * translucent, drawn before the clouds and into the depth they are drawn against, so the clouds behind it were never
 * drawn anyway. With Fabulous graphics, which lay the clouds in behind the glass, the clouds outside the windows go too
 * while the airliner is in the cloud layer.
 *
 * <p>Inside is looked for among the blocks round the camera: the world's, for an airliner standing as blocks, and those
 * of each assembled airliner whose bounds hold the camera, where the camera is among them as the airliner is drawn. The
 * camera is inside where the airframe ({@link Aboard#AIRFRAME}: the skin, the floors, the windows and doors, open or
 * shut) is straight above it and below it, as {@link Aboard#insideHull} has it, and 45 degrees up toward each of the
 * eight points of the compass, through whatever is fitted in the cabin. On the wing, the sky is open above, or out
 * past the side of the fuselage; under the wing or the belly, the ground below is no airframe. That is looked at again
 * every {@link #LOOK_EVERY}, and only while the clouds would be drawn.
 *
 * <p>The clouds are held back by switching the clouds setting off, without the notice a change sends, at the stage of
 * the level's drawing right before them (after the particles), and putting it back right after them (after the
 * weather): nothing else in the frame sees it off, and it is never saved. Only for the player's own view: the tail
 * camera display's picture, drawn through Veil from a camera of its own outside, keeps its clouds. Never with the
 * {@code cloudlessCabin} setting off.
 *
 * <p>The same hold leaves the clouds out of any view, a camera display's too and whatever that setting, from a camera
 * so high over them that space has faded them away (see {@link SpaceSky}).
 */
public final class CloudlessCabin {
    /** How often whether the camera is inside a hull in the clouds is looked at again, in milliseconds. */
    private static final long LOOK_EVERY = 50;
    /** How deep the cloud layer is, up from the height the world puts it at: four blocks, a third of a block above it. */
    private static final double CLOUD_DEPTH = 4.5;
    /** How far past the cloud layer, up or down, an airliner is still taken to reach into it, for how far it moves between looks. */
    private static final double CLOUD_MARGIN = 2;
    /**
     * How far up or down from the camera the hull of an airliner standing as blocks (which stands level) may reach, in
     * blocks: as far as its hull is looked for overhead.
     */
    private static final double STANDING_REACH = 48;
    /** How far the airframe is looked for up and out, in blocks: past the crown of the tallest hall at the largest scale. */
    private static final double REACH = 64;
    /** The ways up and out from the camera: 45 degrees up toward each point of the compass, {@link #REACH} long. */
    private static final List<Vec3> UP_AND_OUT = IntStream.range(0, 8)
        .mapToObj(point -> {
            double bearing = Math.toRadians(point * 45.0);
            return new Vec3(Math.cos(bearing), 1, Math.sin(bearing)).normalize().scale(REACH);
        })
        .toList();

    /** Whether the camera was inside the hull of an airliner in the cloud layer when last looked, and when to look again. */
    private static boolean shutIn;
    private static long lookDue;
    /** The clouds setting while it is switched off for the cabin, to be put back; null while it is not. */
    @Nullable
    private static CloudStatus held;

    private CloudlessCabin() {
    }

    /**
     * Right before the clouds are drawn, switches them off if the camera is inside an airliner's hull in the cloud
     * layer; right after them, and at the end of the level's drawing at the latest, puts them back.
     */
    public static void stage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            if (held == null && hidden(event)) {
                hold(event);
            }
        } else if (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER || stage == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            release();
        }
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        release();
        shutIn = false;
    }

    /**
     * Whether the clouds about to be drawn are to be left out of this view: from inside a hull in the cloud layer, or
     * from a camera so high over them they have faded away (see {@link SpaceSky}).
     */
    private static boolean hidden(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Camera camera = event.getCamera();
        if (level == null || minecraft.options.getCloudsType() == CloudStatus.OFF) {
            return false;
        }
        if (SpaceSky.cloudsGone(camera)) {
            return true;
        }
        // Only the player's own view, not a picture drawn from a camera of its own (the tail camera display's).
        if (camera != minecraft.gameRenderer.getMainCamera() || !AerolargeClientConfig.CLOUDLESS_CABIN.get()) {
            return false;
        }
        float clouds = level.effects().getCloudHeight();
        if (Float.isNaN(clouds)) {
            return false;
        }
        long now = Util.getMillis();
        if (now - lookDue >= 0) {
            lookDue = now + LOOK_EVERY;
            // The partial tick Sable draws the airliners with.
            shutIn = shutIn(level, camera.getPosition(), clouds, event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
        return shutIn;
    }

    /** Whether the camera is inside the hull of an airliner, standing as blocks or assembled, that reaches into the clouds. */
    private static boolean shutIn(ClientLevel level, Vec3 camera, float cloudHeight, float partialTick) {
        double bottom = cloudHeight - CLOUD_MARGIN;
        double top = cloudHeight + CLOUD_DEPTH + CLOUD_MARGIN;
        if (camera.y + STANDING_REACH >= bottom && camera.y - STANDING_REACH <= top && inside(level, camera)) {
            return true;
        }
        for (SableClient.Around airliner : SableClient.around(level, camera, partialTick)) {
            if (airliner.top() >= bottom && airliner.bottom() <= top && inside(level, airliner.point())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a point among blocks (the world's, or an assembled airliner's own) is inside an airliner's hull: airframe
     * straight above and below it, and 45 degrees up toward every point of the compass.
     */
    private static boolean inside(BlockGetter level, Vec3 point) {
        if (!Aboard.insideHull(level, BlockPos.containing(point))) {
            return false;
        }
        for (Vec3 way : UP_AND_OUT) {
            boolean covered = BlockGetter.traverseBlocks(point, point.add(way), level,
                (blocks, pos) -> blocks.getBlockState(pos).is(Aboard.AIRFRAME) ? Boolean.TRUE : null, blocks -> Boolean.FALSE);
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    /**
     * Switches the clouds setting off for the clouds about to be drawn, without the notice a change sends. With Fabulous
     * graphics the clouds are drawn into a framebuffer of their own and laid over the world afterwards, which the level's
     * drawing clears only when it draws them; it is cleared here instead, or the clouds of an earlier frame would be laid
     * over this one, and the framebuffer bound before is bound again.
     */
    private static void hold(RenderLevelStageEvent event) {
        OptionInstance<CloudStatus> clouds = Minecraft.getInstance().options.cloudStatus();
        held = clouds.value;
        clouds.value = CloudStatus.OFF;
        RenderTarget target = event.getLevelRenderer().getCloudsTarget();
        if (target != null) {
            int bound = GlStateManager.getBoundFramebuffer();
            target.clear(Minecraft.ON_OSX);
            GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, bound);
        }
    }

    /** Puts the clouds setting back as it was, unless something else has changed it meanwhile. */
    private static void release() {
        if (held == null) {
            return;
        }
        OptionInstance<CloudStatus> clouds = Minecraft.getInstance().options.cloudStatus();
        if (clouds.value == CloudStatus.OFF) {
            clouds.value = held;
        }
        held = null;
    }
}
