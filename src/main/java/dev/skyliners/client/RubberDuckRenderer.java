package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.block.RubberDuckBlock;
import dev.skyliners.block.RubberDuckBlockEntity;
import dev.skyliners.zerog.GravitySwitchBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws a rubber duck from its block model: sitting still, or afloat, bobbing and rocking gently on the water, each
 * duck to its own beat so a bath full of them does not move as one; in a room whose gravity switch is off (see
 * {@link GravitySwitchBlockEntity}), drifting up off its spot, bobbing, wandering a little to and fro and slowly turning
 * over as it goes; and, squeaked, wiggling, a quick twist from side to side with a little hop that dies away.
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
    /**
     * A duck floating weightless: how high over its spot it floats, in blocks; how far it bobs up and down and wanders
     * either way, in blocks, and how fast it bobs and wanders across and along, in radians a tick, the two out of step
     * so it wanders round in loops; how fast it turns round, in degrees a tick, each duck turned its own way to start
     * with by so many degrees for each radian of its beat; and how far it tips each way, in degrees, and how fast.
     */
    private static final float FLOAT_HIGH = 0.55F;
    private static final float FLOAT_BOB = 0.2F;
    private static final float FLOAT_BOB_SPEED = 0.05F;
    private static final float FLOAT_WANDER = 0.12F;
    private static final float FLOAT_ACROSS_SPEED = 0.023F;
    private static final float FLOAT_ALONG_SPEED = 0.03F;
    private static final float FLOAT_TURN = 0.8F;
    private static final float FLOAT_TURN_SPREAD = 40;
    private static final float FLOAT_TIP = 25;
    private static final float FLOAT_TIP_SPEED = 0.031F;
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
        if (!Sight.ahead(pose.last())) {
            return;
        }
        BlockState state = duck.getBlockState();
        float time = (float) DrawingClock.ticks(partialTick);
        float beat = (duck.getBlockPos().hashCode() & 0xFF) * BEAT_SPREAD;
        float lift = 0;
        float roll = 0;
        if (state.getValue(RubberDuckBlock.WATERLOGGED)) {
            lift = (float) RubberDuckBlock.lift(state) + BOB * Mth.sin(time * BOB_SPEED + beat);
            roll = ROCK * Mth.sin(time * ROCK_SPEED + beat);
        }
        float yaw = 0;
        float across = 0;
        float along = 0;
        if (GravitySwitchBlockEntity.weightlessAt(duck.getLevel(), duck.getBlockPos())) {
            lift += FLOAT_HIGH + FLOAT_BOB * Mth.sin(time * FLOAT_BOB_SPEED + beat);
            across = FLOAT_WANDER * Mth.sin(time * FLOAT_ACROSS_SPEED + beat);
            along = FLOAT_WANDER * Mth.cos(time * FLOAT_ALONG_SPEED + beat);
            yaw = (float) ((DrawingClock.ticks(partialTick) * FLOAT_TURN + beat * FLOAT_TURN_SPREAD) % 360);
            roll = FLOAT_TIP * Mth.sin(time * FLOAT_TIP_SPEED + beat);
        }
        float age = (float) (DrawingClock.gameTime(partialTick) - duck.wiggled());
        if (age >= 0 && age < WIGGLE_TICKS) {
            float fade = 1 - age / WIGGLE_TICKS;
            yaw += WIGGLE * Mth.sin(age * WIGGLE_SPEED) * fade;
            lift += HOP * Math.abs(Mth.sin(age * WIGGLE_SPEED)) * fade;
        }
        Direction back = state.getValue(RubberDuckBlock.FACING).getOpposite();
        float perch = RubberDuckBlock.sitsBack(state) ? (float) (RubberDuckBlock.PERCH / 16) : 0;
        pose.pushPose();
        pose.translate(0.5 + back.getStepX() * perch + across, lift, 0.5 + back.getStepZ() * perch + along);
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

    /** Afloat, its head reaches above its block, it hops when it wiggles, and weightless it floats up over it. */
    @Override
    public AABB getRenderBoundingBox(RubberDuckBlockEntity duck) {
        return new AABB(duck.getBlockPos()).inflate(FLOAT_WANDER, 0, FLOAT_WANDER).expandTowards(0, 1.5, 0);
    }
}
