package dev.skyliners.client;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.lighting.QuadLighter;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;

/**
 * Quads baked from their corners for the mod's own block models, the cabin archway's (see {@link CabinArchModel}) and
 * the oval window's (see {@link OvalWindowModel}): lit as a block's sides are, or, round a curve, with each corner's
 * shade for the way it faces baked into its colour, so a curved face brightens and darkens smoothly round it.
 */
final class ModelQuads {
    /** A quad its block's colour handler leaves as it is. */
    static final int NO_TINT = -1;

    private ModelQuads() {
    }

    /**
     * A corner of a quad: where it is in the block, the way the face looks there, and where on the texture it takes its
     * colour from, across and down it as a fraction of the texture.
     */
    record Corner(Vector3f at, Vector3f normal, float u, float v) {
    }

    /**
     * A quad baked from its corners: lit as a block's side is, or, round a curve, with each corner's shade for the way
     * it faces baked into its colour and no other shading or corner darkening.
     */
    static BakedQuad bake(Corner[] quad, TextureAtlasSprite sprite, Direction side, boolean round) {
        return bake(quad, sprite, side, round, NO_TINT);
    }

    /** The same, tinted as its block's colour handler tints this index (see {@code RegisterColorHandlersEvent}). */
    static BakedQuad bake(Corner[] quad, TextureAtlasSprite sprite, Direction side, boolean round, int tint) {
        return bake(quad, sprite, side, round, !round, tint);
    }

    /**
     * A quad of a flat face cut into slivers, a fan of thin triangles, lit as a block's side is but evenly over it,
     * without its corners darkened: the world darkens each quad's corners from the quad's own bounds, so slivers side
     * by side came out lit each its own way, in light and dark rays fanning out across a face meant to look flat.
     */
    static BakedQuad bakeEven(Corner[] quad, TextureAtlasSprite sprite, Direction side) {
        return bake(quad, sprite, side, false, false, NO_TINT);
    }

    /**
     * A quad baked from its corners, its corners darkened by the world's lighting where something stands near them or
     * not.
     */
    private static BakedQuad bake(Corner[] quad, TextureAtlasSprite sprite, Direction side, boolean round, boolean darkened, int tint) {
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
        builder.setSprite(sprite);
        builder.setTintIndex(tint);
        builder.setDirection(side);
        builder.setShade(!round);
        builder.setHasAmbientOcclusion(darkened);
        for (Corner corner : quad) {
            Vector3f n = corner.normal();
            int grey = round ? Math.round(255 * QuadLighter.calculateShade(n.x(), n.y(), n.z(), false)) : 255;
            builder.addVertex(corner.at().x(), corner.at().y(), corner.at().z()).setColor(grey, grey, grey, 255)
                .setUv(sprite.getU(corner.u()), sprite.getV(corner.v())).setUv2(0, 0).setNormal(n.x(), n.y(), n.z());
        }
        return builder.bakeQuad();
    }
}
