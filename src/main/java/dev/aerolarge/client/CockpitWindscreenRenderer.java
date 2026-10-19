package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.airframe.Windscreen;
import dev.aerolarge.block.CockpitWindscreenBlock;
import dev.aerolarge.block.CockpitWindscreenBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

/**
 * Draws the nose as one curved mesh from its anchor block: the smooth painted skin over the stepped nose blocks,
 * and the windscreen set into it, tinted glass seen from both sides in a dark frame. The mesh is worked out with the
 * nose pointing north and turned to the way the anchor says it points. On an assembled airliner Sable draws block
 * entity renderers with the airliner's own movement, so the nose flies along.
 */
public class CockpitWindscreenRenderer implements BlockEntityRenderer<CockpitWindscreenBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "block/windscreen");
    private static final float[] SKIN = {0.90F, 0.92F, 0.94F, 1.0F};
    private static final float[] GLASS = {0.42F, 0.58F, 0.70F, 0.45F};
    private static final float[] FRAME = {0.16F, 0.17F, 0.19F, 1.0F};

    @Override
    public void render(CockpitWindscreenBlockEntity windscreen, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Windscreen.Mesh mesh = windscreen.mesh();
        if (mesh == null) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WHITE);
        // Lit by the sky only: the anchor sits by a cabin light, which would make the outside of the nose glow at night.
        light = LightTexture.pack(0, LightTexture.sky(light));
        pose.pushPose();
        // From nose-north to the way this airliner's nose points, about the anchor block's centre.
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - windscreen.getBlockState().getValue(CockpitWindscreenBlock.FACING).toYRot()));
        pose.translate(-0.5, 0, -0.5);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        draw(pose, solid, mesh.skin(), SKIN, sprite, light);
        draw(pose, solid, mesh.frame(), FRAME, sprite, light);
        draw(pose, buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS)), mesh.glass(), GLASS, sprite, light);
        pose.popPose();
    }

    private static void draw(PoseStack pose, VertexConsumer consumer, Iterable<Windscreen.Quad> quads, float[] colour, TextureAtlasSprite sprite, int light) {
        PoseStack.Pose last = pose.last();
        float u = (sprite.getU0() + sprite.getU1()) / 2;
        float v = (sprite.getV0() + sprite.getV1()) / 2;
        for (Windscreen.Quad quad : quads) {
            float[] c = quad.corners();
            float[] n = quad.normal();
            // Glass that faces the sky catches a little more of it.
            float sheen = 1 + 0.15F * Math.max(0, n[1]);
            for (int k = 0; k < 4; k++) {
                consumer.addVertex(last, c[k * 3], c[k * 3 + 1], c[k * 3 + 2])
                    .setColor(Math.min(1, colour[0] * sheen), Math.min(1, colour[1] * sheen), Math.min(1, colour[2] * sheen), colour[3])
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(last, n[0], n[1], n[2]);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(CockpitWindscreenBlockEntity windscreen) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }

    @Override
    public AABB getRenderBoundingBox(CockpitWindscreenBlockEntity windscreen) {
        Windscreen.Mesh mesh = windscreen.mesh();
        return new AABB(windscreen.getBlockPos()).inflate(mesh != null ? mesh.reach() : 1);
    }
}
