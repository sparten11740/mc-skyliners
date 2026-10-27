package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.item.AirframeBlueprintItem;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
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
 * Shows which way an airframe's blueprint would build: while the player holds one and aims at a block, a miniature of
 * the airliner or the zeppelin floats over that block, turned as the blueprint would build it there (see
 * {@link AirframeBlueprintItem}), its nose toward the player, as the real nose would come just past the block, and
 * its tail pointing away, the way it would stretch. It is the souvenir model without its stand, twice its size,
 * see-through and glowing, and bobbing gently, so it reads as a preview rather than a thing in the world.
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
        AirframeBlueprintItem blueprint = player == null ? null : heldBlueprint(player);
        if (blueprint == null || event.getCamera() != minecraft.gameRenderer.getMainCamera()
            || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        float ticks = player.tickCount + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        draw(event.getPoseStack(), event.getCamera().getPosition(), blueprint, hit.getBlockPos(), player.getDirection(), ticks);
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
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GHOST);
        for (BakedQuad quad : model(blueprint.model())) {
            consumer.putBulkData(pose.last(), quad, 1, 1, 1, ALPHA, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, false);
        }
        buffers.endBatch(GHOST);
        pose.popPose();
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
