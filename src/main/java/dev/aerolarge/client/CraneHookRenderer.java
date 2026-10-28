package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.entity.CraneHook;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * The sky crane's hook (see {@link CraneHook}): its manila rope up to where it comes out under the zeppelin's floor, as
 * this client draws her, a black weighted ball and an iron hook at its end, and from the hook, rope slings down to the
 * corners of the back of whatever it holds, or a net round the chest, barrel or crate it carries, which is drawn
 * hanging there.
 */
public class CraneHookRenderer extends EntityRenderer<CraneHook> {
    /** The rope's half thickness, and the slings' and net's, in blocks, and their manila colour. */
    private static final float ROPE_HALF = 0.05F;
    private static final float SLING_HALF = 0.03F;
    private static final int ROPE = 0xFFC9B27A;
    /** The iron hook's height and the weighted ball's over it, in blocks, and their half widths and colours. */
    private static final float HOOK_HEIGHT = 0.25F;
    private static final float BALL_HEIGHT = 0.3F;
    private static final float HOOK_HALF = 0.06F;
    private static final float BALL_HALF = 0.17F;
    private static final int IRON = 0xFF8A8D91;
    private static final int BALL = 0xFF1E1F22;
    /** How far in from its load's edges the slings meet it, and how far down, in blocks. */
    private static final float SLING_IN = 0.15F;
    private static final float SLING_DOWN = 0.1F;
    /** How far off the camera the hook and its long rope are drawn, in blocks. */
    private static final double SEEN = 160;

    private final BlockRenderDispatcher blocks;

    public CraneHookRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.3F;
    }

    /** Drawn while near, its box or not in view, as its rope reaches up out of it to her. */
    @Override
    public boolean shouldRender(CraneHook hook, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        return hook.distanceToSqr(cameraX, cameraY, cameraZ) < SEEN * SEEN;
    }

    @Override
    public void render(CraneHook hook, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 at = hook.getPosition(partialTick);
        boolean loaded = hook.loaded();
        float base = loaded ? (float) hook.hookHeight() : 0;
        float ropeFrom = base + HOOK_HEIGHT + BALL_HEIGHT;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        PoseStack.Pose last = pose.last();
        Vec3 out = drawnWinchOut(hook, partialTick).subtract(at);
        Vec3 camera = entityRenderDispatcher.camera.getPosition().subtract(at);
        strand(consumer, last, light, camera, new Vec3(0, ropeFrom, 0), out, ROPE_HALF);
        FlatQuads.box(consumer, last, light, BALL, -BALL_HALF, base + HOOK_HEIGHT, -BALL_HALF, BALL_HALF, ropeFrom, BALL_HALF);
        FlatQuads.box(consumer, last, light, IRON, -HOOK_HALF, base, -HOOK_HALF, HOOK_HALF, base + HOOK_HEIGHT, HOOK_HALF);
        if (hook.cargoBlock().isPresent()) {
            net(consumer, last, light, camera, base);
        } else if (loaded) {
            slings(consumer, last, light, camera, hook, base);
        }
        hook.cargoBlock().ifPresent(state -> {
            pose.pushPose();
            pose.translate(-0.5, 0, -0.5);
            blocks.renderSingleBlock(state, pose, buffers, light, OverlayTexture.NO_OVERLAY);
            pose.popPose();
        });
        super.render(hook, yaw, partialTick, pose, buffers, light);
    }

    /** Where its winch's rope comes out under her floor, as she is drawn this frame, or else as she last ticked. */
    private static Vec3 drawnWinchOut(CraneHook hook, float partialTick) {
        Vec3 out = Vec3.atBottomCenterOf(hook.winch()).subtract(0, 1, 0);
        SableClient.View view = SableClient.view(out, new Vec3(0, 0, 1), new Vec3(0, 1, 0), partialTick);
        return view != null ? view.eye() : SableClient.toWorld(hook.level(), out);
    }

    /** Four slings from the hook down to the corners of the top of its load. */
    private static void slings(VertexConsumer consumer, PoseStack.Pose at, int light, Vec3 camera, CraneHook hook, float top) {
        float half = hook.getBbWidth() / 2 - SLING_IN;
        Vec3 hookEye = new Vec3(0, top, 0);
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? -half : half;
            float z = (corner & 2) == 0 ? -half : half;
            strand(consumer, at, light, camera, hookEye, new Vec3(x, top - SLING_DOWN, z), SLING_HALF);
        }
    }

    /**
     * The net round a chest, barrel or crate: from the hook down to its top corners, down its corners, and crossed
     * under it.
     */
    private static void net(VertexConsumer consumer, PoseStack.Pose at, int light, Vec3 camera, float top) {
        float half = 0.5F + SLING_HALF;
        Vec3 hookEye = new Vec3(0, top, 0);
        Vec3[] upper = new Vec3[4];
        Vec3[] lower = new Vec3[4];
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? -half : half;
            float z = (corner & 2) == 0 ? -half : half;
            upper[corner] = new Vec3(x, top, z);
            lower[corner] = new Vec3(x, -SLING_HALF, z);
            strand(consumer, at, light, camera, hookEye, upper[corner], SLING_HALF);
            strand(consumer, at, light, camera, upper[corner], lower[corner], SLING_HALF);
        }
        strand(consumer, at, light, camera, lower[0], lower[3], SLING_HALF);
        strand(consumer, at, light, camera, lower[1], lower[2], SLING_HALF);
    }

    /** A strand of rope between two points, a thin strip turned to face the camera, all in the hook's own place. */
    private static void strand(VertexConsumer consumer, PoseStack.Pose at, int light, Vec3 camera, Vec3 from, Vec3 to, float half) {
        FlatQuads.strand(consumer, at, light, ROPE, camera, from, to, half);
    }

    @Override
    public ResourceLocation getTextureLocation(CraneHook hook) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    protected boolean shouldShowName(CraneHook hook) {
        return false;
    }
}
