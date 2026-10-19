package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.GiantRubberDuckBlock;
import dev.aerolarge.block.GiantRubberDuckBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the giant rubber duck from its origin: the rubber duck's own model {@link GiantRubberDuckBlock#SCALE} times
 * over, about the middle of its base, which sits at the middle of the bottom of the origin's block, as its blocks'
 * shapes have it. Rising, it comes up from the bottom of the pool, slowing as it surfaces; afloat, it bobs and rocks
 * gently on the water.
 */
public class GiantRubberDuckRenderer implements BlockEntityRenderer<GiantRubberDuckBlockEntity> {
    /** How deep under the water it starts rising from, in blocks: its own height, so it starts out of sight. */
    private static final float RISE_DEPTH = 3.5F;
    /** How far it bobs up and down afloat, in blocks, and how fast, in radians a tick. */
    private static final float BOB = 0.08F;
    private static final float BOB_SPEED = 0.06F;
    /** How far it rocks either way, in degrees, and how fast: slower than a little duck, as something big moves. */
    private static final float ROCK = 2.5F;
    private static final float ROCK_SPEED = 0.035F;
    /** How far past its blocks it is drawn: rising from under the water, and bobbing over the top. */
    private static final double DRAWN_BEYOND = 1;

    private final BlockRenderDispatcher blocks;
    /** The rubber duck it is drawn from, facing north. */
    private final BlockState model = AerolargeRegistry.RUBBER_DUCK.get().defaultBlockState();

    public GiantRubberDuckRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(GiantRubberDuckBlockEntity giant, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (giant.getLevel() == null) {
            return;
        }
        float time = giant.getLevel().getGameTime() + partialTick;
        float risen = giant.risen(time);
        Direction facing = giant.getBlockState().getValue(GiantRubberDuckBlock.FACING);
        float scale = (float) GiantRubberDuckBlock.SCALE;
        pose.pushPose();
        pose.translate(0.5, -RISE_DEPTH * (1 - risen) + BOB * risen * Mth.sin(time * BOB_SPEED), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.mulPose(Axis.ZP.rotationDegrees(ROCK * Mth.sin(time * ROCK_SPEED)));
        pose.scale(scale, scale, scale);
        pose.translate(-0.5, 0, -0.5);
        RubberDuckRenderer.draw(blocks, model, pose, buffers, light, overlay);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(GiantRubberDuckBlockEntity giant) {
        return true;
    }

    /** The whole duck, which reaches well past its origin's block. */
    @Override
    public AABB getRenderBoundingBox(GiantRubberDuckBlockEntity giant) {
        double reach = GiantRubberDuckBlock.SCALE / 2 + DRAWN_BEYOND;
        return new AABB(giant.getBlockPos()).inflate(reach, 0, reach).expandTowards(0, reach, 0).expandTowards(0, -RISE_DEPTH, 0);
    }
}
