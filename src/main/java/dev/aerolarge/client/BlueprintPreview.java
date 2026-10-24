package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.item.AirlinerBlueprintItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
 * Shows which way an airliner blueprint would build: while the player holds one and aims at a block, a miniature of
 * the airliner floats over that block, turned as the blueprint would build it there (see
 * {@link AirlinerBlueprintItem}), its nose toward the player, as the real nose would come just past the block, and its
 * tail pointing away, the way the airliner would stretch. It is the souvenir model airliner without its stand, twice
 * its size, see-through and glowing, and bobbing gently, so it reads as a preview rather than a thing in the world.
 * Only the player's own view shows it, not a picture drawn from a camera of its own (the tail camera display's).
 */
public final class BlueprintPreview {
    /** How many times the souvenir model's size the miniature is drawn. */
    private static final float SCALE = 2;
    /**
     * How high the middle of the miniature floats over the top of the block aimed at, in blocks: low, so the player,
     * looking down at the ground, sees it from above, its wings and tail showing which way it points.
     */
    private static final double FLOAT = 0.45;
    /** Where the middle of the model's fuselage is, up its block, in sixteenths: what the miniature turns about. */
    private static final float FUSELAGE_MIDDLE = 7;
    /** How far it bobs up and down, in blocks, and how long a bob takes, in seconds. */
    private static final double BOB = 0.1;
    private static final double BOB_SECONDS = 2.5;
    /** How solid it looks, from 0 for not at all to 1 for wholly. */
    private static final float ALPHA = 0.65F;
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
        if (player == null || event.getCamera() != minecraft.gameRenderer.getMainCamera() || !holdsBlueprint(player)
            || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        float ticks = player.tickCount + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        draw(event.getPoseStack(), event.getCamera().getPosition(), hit.getBlockPos(), player.getDirection(), ticks);
    }

    private static boolean holdsBlueprint(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof AirlinerBlueprintItem
            || player.getOffhandItem().getItem() instanceof AirlinerBlueprintItem;
    }

    /** The miniature over the block, the airliner's tail stretching {@code along}, bobbing with the time in ticks. */
    private static void draw(PoseStack pose, Vec3 camera, BlockPos aimed, Direction along, float ticks) {
        double bob = BOB * Mth.sin((float) (ticks / 20.0 / BOB_SECONDS * Mth.TWO_PI));
        pose.pushPose();
        pose.translate(aimed.getX() + 0.5 - camera.x, aimed.getY() + 1 + FLOAT + bob - camera.y, aimed.getZ() + 0.5 - camera.z);
        // The model is drawn nose north: turned so its tail points along the way the airliner stretches.
        pose.mulPose(Axis.YP.rotationDegrees(-along.toYRot()));
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-0.5, -FUSELAGE_MIDDLE / 16, -0.5);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GHOST);
        for (BakedQuad quad : airliner()) {
            consumer.putBulkData(pose.last(), quad, 1, 1, 1, ALPHA, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
        }
        buffers.endBatch(GHOST);
        pose.popPose();
    }

    /** The souvenir model airliner's faces, nose north, but for its stand's. */
    private static List<BakedQuad> airliner() {
        BlockState state = AerolargeRegistry.MODEL_AIRLINER.get().defaultBlockState();
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
