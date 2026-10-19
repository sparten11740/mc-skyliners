package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aerolarge.block.RubberDuckBlock;
import dev.aerolarge.block.RubberDuckBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a rubber duck from its block model: sitting still, or afloat, bobbing and rocking gently on the water, each
 * duck to its own beat so a bath full of them does not move as one; and, squeaked, wiggling, a quick twist from side
 * to side with a little hop that dies away.
 */
public class RubberDuckRenderer implements BlockEntityRenderer<RubberDuckBlockEntity> {
    /** How far a duck afloat bobs up and down, in blocks, and how fast, in radians a tick. */
    private static final float BOB = 0.6F / 16;
    private static final float BOB_SPEED = 0.08F;
    /** How far it rocks either way, in degrees, and how fast. */
    private static final float ROCK = 5;
    private static final float ROCK_SPEED = 0.05F;
    /** How long a wiggle lasts, in ticks, how far it twists each way at first, in degrees, and how fast. */
    private static final float WIGGLE_TICKS = 12;
    private static final float WIGGLE = 25;
    private static final float WIGGLE_SPEED = 1.6F;
    /** How high the hop at the start of a wiggle is, in blocks. */
    private static final float HOP = 1.5F / 16;
    /** Spreads the ducks' beats apart: radians of beat for each step of a position's hash. */
    private static final float BEAT_SPREAD = 0.1F;
    /** The seed the model's render types are picked with, as for any block drawn on its own. */
    private static final long MODEL_SEED = 42;

    private final BlockRenderDispatcher blocks;

    public RubberDuckRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(RubberDuckBlockEntity duck, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (duck.getLevel() == null) {
            return;
        }
        BlockState state = duck.getBlockState();
        float time = duck.getLevel().getGameTime() + partialTick;
        float beat = (duck.getBlockPos().hashCode() & 0xFF) * BEAT_SPREAD;
        float lift = 0;
        float roll = 0;
        if (state.getValue(RubberDuckBlock.WATERLOGGED)) {
            lift = (float) (RubberDuckBlock.FLOAT / 16) + BOB * Mth.sin(time * BOB_SPEED + beat);
            roll = ROCK * Mth.sin(time * ROCK_SPEED + beat);
        }
        float yaw = 0;
        float age = time - duck.wiggled();
        if (age >= 0 && age < WIGGLE_TICKS) {
            float fade = 1 - age / WIGGLE_TICKS;
            yaw = WIGGLE * Mth.sin(age * WIGGLE_SPEED) * fade;
            lift += HOP * Math.abs(Mth.sin(age * WIGGLE_SPEED)) * fade;
        }
        pose.pushPose();
        pose.translate(0.5, lift, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.ZP.rotationDegrees(roll));
        pose.translate(-0.5, 0, -0.5);
        draw(blocks, state, pose, buffers, light, overlay);
        pose.popPose();
    }

    /** Draws a block's model where the pose has it, as an entity is drawn, whatever its render shape. */
    static void draw(BlockRenderDispatcher blocks, BlockState state, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = blocks.getBlockModel(state);
        for (RenderType type : model.getRenderTypes(state, RandomSource.create(MODEL_SEED), ModelData.EMPTY)) {
            blocks.getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderTypeHelper.getEntityRenderType(type, false)), state, model,
                1, 1, 1, light, overlay, ModelData.EMPTY, type);
        }
    }

    /** Afloat, its head reaches above its block, and it hops when it wiggles. */
    @Override
    public AABB getRenderBoundingBox(RubberDuckBlockEntity duck) {
        return new AABB(duck.getBlockPos()).expandTowards(0, 1, 0);
    }
}
