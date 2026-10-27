package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.ObjIntConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * A banner drawn with its own layers, as the game draws one: its base tinted its colour and each of its patterns over
 * it, tinted theirs, from the banner textures' face, for the renderers that show the ship's colours (the flag flying
 * from the Sky Deck's staff, the crest on a ship's plaque).
 */
final class BannerQuads {
    /** Where a banner's face is on its 64-pixel textures: a pixel in from the corner, 20 across by 40 down. */
    static final float FACE_LEFT = 1 / 64F;
    static final float FACE_TOP = 1 / 64F;
    static final float FACE_WIDTH = 20 / 64F;
    static final float FACE_HEIGHT = 40 / 64F;

    private BannerQuads() {
    }

    /**
     * A banner's layers, its base first: each handed over as the buffer it is drawn into, its texture's face from 0
     * to 1 across it, and its dye's colour.
     */
    static void layers(MultiBufferSource buffers, DyeColor base, BannerPatternLayers patterns, ObjIntConsumer<VertexConsumer> draw) {
        draw.accept(Sheets.BANNER_BASE.sprite().wrap(buffers.getBuffer(Sheets.BANNER_BASE.renderType(RenderType::entityCutoutNoCull))),
            0xFF000000 | base.getTextureDiffuseColor());
        for (BannerPatternLayers.Layer layer : patterns.layers()) {
            Material material = Sheets.getBannerMaterial(layer.pattern());
            draw.accept(material.sprite().wrap(buffers.getBuffer(Sheets.bannerSheet())), 0xFF000000 | layer.color().getTextureDiffuseColor());
        }
    }

    /** A banner upright on a flat face from one corner to the other, top left to bottom right, facing +z. */
    static void flat(PoseStack.Pose pose, MultiBufferSource buffers, DyeColor base, BannerPatternLayers patterns, float left, float top,
                     float right, float bottom, int light) {
        layers(buffers, base, patterns, (consumer, colour) -> face(pose, consumer, colour, light, left, top, right, bottom));
    }

    private static void face(PoseStack.Pose pose, VertexConsumer consumer, int colour, int light, float left, float top, float right, float bottom) {
        corner(pose, consumer, colour, light, left, top, 0, 0);
        corner(pose, consumer, colour, light, left, bottom, 0, 1);
        corner(pose, consumer, colour, light, right, bottom, 1, 1);
        corner(pose, consumer, colour, light, right, top, 1, 0);
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer consumer, int colour, int light, float x, float y, float across, float down) {
        consumer.addVertex(pose, x, y, 0)
            .setColor(colour)
            .setUv(FACE_LEFT + across * FACE_WIDTH, FACE_TOP + down * FACE_HEIGHT)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0, 0, 1);
    }
}
