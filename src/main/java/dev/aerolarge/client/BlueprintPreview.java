package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.airframe.RocketLayout;
import dev.aerolarge.airport.LaunchPad;
import dev.aerolarge.block.LaunchPadBlockEntity;
import dev.aerolarge.item.AirframeBlueprintItem;
import dev.aerolarge.item.LaunchPadBlueprintItem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Shows which way an airframe's blueprint would build: while the player holds one and aims at a block, a miniature of
 * the airliner or the zeppelin floats over that block, turned as the blueprint would build it there (see
 * {@link AirframeBlueprintItem}), its nose toward the player, as the real nose would come just past the block, and
 * its tail pointing away, the way it would stretch. It is the souvenir model without its stand, twice its size,
 * see-through and glowing, and bobbing gently, so it reads as a preview rather than a thing in the world.
 * Only the player's own view shows it, not a picture drawn from a camera of its own (the tail camera display's).
 *
 * <p>A rocket's blueprint aimed at a launch pad shows her instead where she would stand, in the pad's very middle: the
 * same model, fainter and at her full height, its fins reaching as far as hers, however far off the player stands
 * (see {@code RocketBlueprintItem}). A launch pad's blueprint shows the deck it would lay, as a box drawn over the
 * ground, and a post where its middle would be.
 */
public final class BlueprintPreview {
    /** How many times the souvenir model's size the miniature is drawn. */
    private static final float SCALE = 2;
    /**
     * How high the middle of the miniature floats over the top of the block aimed at, in blocks: low, so the player,
     * looking down at the ground, sees it from above, its wings and tail showing which way it points.
     */
    private static final double FLOAT = 0.45;
    /** How far it bobs up and down, in blocks, and how long a bob takes, in seconds. */
    private static final double BOB = 0.1;
    private static final double BOB_SECONDS = 2.5;
    /** How solid it looks, from 0 for not at all to 1 for wholly. */
    private static final float ALPHA = 0.65F;
    /** How solid the rocket shown at her full size on a launch pad looks: she is big, and must not hide the pad. */
    private static final float ALPHA_ON_PAD = 0.4F;
    /** How tall the souvenir rocket is, and how far out from her axis its fins reach, in blocks of its own block. */
    private static final float MODEL_ROCKET_TALL = 15 / 16F;
    private static final float MODEL_ROCKET_REACH = 4.5F / 16;
    /** The colour of the box a launch pad's blueprint shows, red, green and blue from 0 to 1. */
    private static final float PAD_RED = 1;
    private static final float PAD_GREEN = 0.8F;
    private static final float PAD_BLUE = 0.1F;
    /** How tall that box is, and the post at its middle, in blocks. */
    private static final double PAD_BOX_TALL = 1;
    private static final double PAD_POST_TALL = 8;
    /** The texture of the model's stand, whose faces are left out. */
    private static final ResourceLocation STAND = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "block/light_cable");
    /** Drawn see-through, from the block textures the model is made of. */
    private static final RenderType GHOST = RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);

    private BlueprintPreview() {
    }

    public static void stage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || event.getCamera() != minecraft.gameRenderer.getMainCamera()
            || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        if (holdsPadBlueprint(player)) {
            drawPadBox(event.getPoseStack(), camera, hit.getBlockPos(), player.getDirection());
            return;
        }
        AirframeBlueprintItem blueprint = heldBlueprint(player);
        if (blueprint == null) {
            return;
        }
        LaunchPadBlockEntity pad = blueprint.padUnder(player.level(), hit.getBlockPos());
        if (pad != null) {
            drawOnPad(event.getPoseStack(), camera, blueprint, pad);
            return;
        }
        float ticks = player.tickCount + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        draw(event.getPoseStack(), camera, blueprint, hit.getBlockPos(), player.getDirection(), ticks);
    }

    private static boolean holdsPadBlueprint(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof LaunchPadBlueprintItem || player.getOffhandItem().getItem() instanceof LaunchPadBlueprintItem;
    }

    /** The box over the deck a launch pad would have, built from the block aimed at, and the post at its middle. */
    private static void drawPadBox(PoseStack pose, Vec3 camera, BlockPos aimed, Direction along) {
        BlockPos middle = LaunchPadBlueprintItem.middle(aimed, along);
        int half = LaunchPad.halfAt(AerolargeConfig.SCALE.get());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        double top = aimed.getY() + 1;
        LevelRenderer.renderLineBox(pose, lines, middle.getX() - half, top, middle.getZ() - half, middle.getX() + half + 1, top + PAD_BOX_TALL,
            middle.getZ() + half + 1, PAD_RED, PAD_GREEN, PAD_BLUE, 1);
        LevelRenderer.renderLineBox(pose, lines, middle.getX(), top, middle.getZ(), middle.getX() + 1, top + PAD_POST_TALL, middle.getZ() + 1,
            PAD_RED, PAD_GREEN, PAD_BLUE, 1);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** The rocket at her full size standing where the pad would have her, turned as it would turn her. */
    private static void drawOnPad(PoseStack pose, Vec3 camera, AirframeBlueprintItem blueprint, LaunchPadBlockEntity pad) {
        double scale = AerolargeConfig.SCALE.get();
        BlockPos hub = pad.getBlockPos();
        float tall = RocketLayout.heightRows(scale) / MODEL_ROCKET_TALL;
        float across = (RocketLayout.reachAt(scale) - 1) / MODEL_ROCKET_REACH;
        pose.pushPose();
        pose.translate(hub.getX() + 0.5 - camera.x, hub.getY() + 1 - camera.y, hub.getZ() + 0.5 - camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(-pad.along().toYRot()));
        pose.scale(across, tall, across);
        pose.translate(-0.5, 0, -0.5);
        drawModel(pose, blueprint, ALPHA_ON_PAD);
        pose.popPose();
    }

    /** The blueprint the player holds, in either hand; null for none. */
    @Nullable
    private static AirframeBlueprintItem heldBlueprint(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof AirframeBlueprintItem blueprint) {
            return blueprint;
        }
        return player.getOffhandItem().getItem() instanceof AirframeBlueprintItem blueprint ? blueprint : null;
    }

    /** The blueprint's miniature over the block, the tail stretching {@code along}, bobbing with the time in ticks. */
    private static void draw(PoseStack pose, Vec3 camera, AirframeBlueprintItem blueprint, BlockPos aimed, Direction along, float ticks) {
        double bob = BOB * Mth.sin((float) (ticks / 20.0 / BOB_SECONDS * Mth.TWO_PI));
        pose.pushPose();
        pose.translate(aimed.getX() + 0.5 - camera.x, aimed.getY() + 1 + FLOAT + bob - camera.y, aimed.getZ() + 0.5 - camera.z);
        // The model is drawn nose north: turned so its tail points along the way the airframe stretches.
        pose.mulPose(Axis.YP.rotationDegrees(-along.toYRot()));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5, -blueprint.modelMiddle() / 16, -0.5);
        drawModel(pose, blueprint, ALPHA);
        pose.popPose();
    }

    /** The blueprint's souvenir model, see-through and glowing, as the pose has placed it. */
    private static void drawModel(PoseStack pose, AirframeBlueprintItem blueprint, float alpha) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GHOST);
        for (BakedQuad quad : model(blueprint.model())) {
            consumer.putBulkData(pose.last(), quad, 1, 1, 1, alpha, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
        }
        buffers.endBatch(GHOST);
    }

    /** A souvenir model's faces, nose north, but for its stand's. */
    private static List<BakedQuad> model(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RandomSource random = RandomSource.create(0);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction side : Direction.values()) {
            quads.addAll(model.getQuads(state, side, random, ModelData.EMPTY, null));
        }
        quads.addAll(model.getQuads(state, null, random, ModelData.EMPTY, null));
        quads.removeIf(quad -> quad.getSprite().contents().name().equals(STAND));
        return quads;
    }
}
