package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.CockpitDisplayBlock;
import dev.aerolarge.block.CockpitDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * The casing of a panel of the flight deck's curved row (see {@link CockpitDisplayRenderer}): the screen's slab and the
 * desk under it with its sloped top, in the panel's own frame (x along it, y up from the floor, z out toward the
 * seats). Each face is tiled with a block texture, a block to a tile, the same the centre console's modules are made
 * of: the screen casing round the screen, the console's plate on the slope, its casing on the desk. And each is lit as
 * a block is, by the light where the panel stands and shaded by the way it faces, so the panels, turned each a step
 * further toward the seats, and their slopes stand apart from one another instead of reading as one flat grey.
 */
final class CockpitCasing {
    private static final ResourceLocation SCREEN_CASING = block("passenger_screen_casing");
    private static final ResourceLocation DESK = block("flight_console");
    private static final ResourceLocation SLOPE = block("panel_side");

    private CockpitCasing() {
    }

    /** The casing of a panel this wide, lit with the light where it stands. */
    static void draw(MultiBufferSource buffers, PoseStack.Pose pose, float width, CockpitDisplayBlockEntity.Panel panel, int light) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        Face face = new Face(consumer, pose, light);
        slab(face, sprite(SCREEN_CASING), width, panel);
        if (panel.desk() > 0) {
            desk(face, sprite(DESK), sprite(SLOPE), width, panel);
        }
    }

    /** The screen's slab: its face round the glass, its back, top and ends, and its bottom where no desk stands under it. */
    private static void slab(Face face, TextureAtlasSprite sprite, float width, CockpitDisplayBlockEntity.Panel panel) {
        float back = -CockpitDisplayBlock.DEPTH;
        float bottom = panel.bottom();
        float height = panel.height();
        face.tiled(sprite, new Vector3f(0, bottom, 0), new Vector3f(width, 0, 0), new Vector3f(0, height, 0));
        face.tiled(sprite, new Vector3f(width, bottom, back), new Vector3f(-width, 0, 0), new Vector3f(0, height, 0));
        face.tiled(sprite, new Vector3f(0, panel.top(), 0), new Vector3f(width, 0, 0), new Vector3f(0, 0, back));
        face.tiled(sprite, new Vector3f(0, bottom, back), new Vector3f(0, 0, -back), new Vector3f(0, height, 0));
        face.tiled(sprite, new Vector3f(width, bottom, 0), new Vector3f(0, 0, back), new Vector3f(0, height, 0));
        if (panel.desk() <= 0) {
            face.tiled(sprite, new Vector3f(0, bottom, back), new Vector3f(width, 0, 0), new Vector3f(0, 0, -back));
        }
    }

    /**
     * The desk under the screen, down to the floor: its front, its back, its sloped top, cut short at either end where
     * it is mitred against the desk beside it, and its ends, square behind the screen and along the mitre in front.
     */
    private static void desk(Face face, TextureAtlasSprite desk, TextureAtlasSprite slope, float width, CockpitDisplayBlockEntity.Panel panel) {
        float back = -CockpitDisplayBlock.DEPTH;
        float bottom = panel.bottom();
        float reach = panel.desk();
        float front = bottom - reach;
        float left = panel.leftCut();
        float right = width - panel.rightCut();
        face.tiled(desk, new Vector3f(left, 0, reach), new Vector3f(right - left, 0, 0), new Vector3f(0, front, 0));
        face.tiled(desk, new Vector3f(width, 0, back), new Vector3f(-width, 0, 0), new Vector3f(0, bottom, 0));
        face.tiled(desk, new Vector3f(0, 0, back), new Vector3f(0, 0, -back), new Vector3f(0, bottom, 0));
        face.tiled(desk, new Vector3f(width, 0, 0), new Vector3f(0, 0, back), new Vector3f(0, bottom, 0));
        float mitre = (float) Math.hypot(panel.leftCut(), reach);
        face.quad(desk, new Vector3f(0, 0, 0), new Vector3f(left, 0, reach), new Vector3f(left, front, reach), new Vector3f(0, bottom, 0),
            new float[]{0, 1, mitre, 1, mitre, 1 - front, 0, 1 - bottom});
        float rightMitre = (float) Math.hypot(panel.rightCut(), reach);
        face.quad(desk, new Vector3f(right, 0, reach), new Vector3f(width, 0, 0), new Vector3f(width, bottom, 0), new Vector3f(right, front, reach),
            new float[]{0, 1, rightMitre, 1, rightMitre, 1 - bottom, 0, 1 - front});
        for (int tile = 0; tile < Math.ceil(width - 1e-4); tile++) {
            slopeTile(face, slope, tile, width, panel);
        }
    }

    /**
     * A block's width of the slope, from its front edge up to the screen, cut where the slope is mitred: along the
     * slope a tile's height of the texture is a block, so the slope, shorter than that, shows the bottom of it.
     */
    private static void slopeTile(Face face, TextureAtlasSprite sprite, int tile, float width, CockpitDisplayBlockEntity.Panel panel) {
        float reach = panel.desk();
        float front = panel.bottom() - reach;
        float frontLeft = Math.max(tile, panel.leftCut());
        float frontRight = Math.max(frontLeft, Math.min(tile + 1, width - panel.rightCut()));
        float backLeft = tile;
        float backRight = Math.min(tile + 1, width);
        float top = 1 - panel.slope();
        face.quad(sprite, new Vector3f(frontLeft, front, reach), new Vector3f(frontRight, front, reach), new Vector3f(backRight, panel.bottom(), 0),
            new Vector3f(backLeft, panel.bottom(), 0), new float[]{frontLeft - tile, 1, frontRight - tile, 1, backRight - tile, top, backLeft - tile, top});
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
    }

    private static ResourceLocation block(String name) {
        return ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "block/" + name);
    }

    /** Drawing faces into a buffer, posed, lit with one light. */
    private record Face(VertexConsumer consumer, PoseStack.Pose pose, int light) {
        /**
         * A rectangle from a corner along two sides, tiled a block to a tile, the texture's top toward the second
         * side's end; facing the way the first side turns into the second (anticlockwise, seen from in front).
         */
        void tiled(TextureAtlasSprite sprite, Vector3f corner, Vector3f across, Vector3f up) {
            float acrossLength = across.length();
            float upLength = up.length();
            for (int i = 0; i < Math.ceil(acrossLength - 1e-4); i++) {
                for (int j = 0; j < Math.ceil(upLength - 1e-4); j++) {
                    tile(sprite, corner, across, up, i, j);
                }
            }
        }

        /** One tile of a tiled rectangle: the i-th along its first side and the j-th along its second, cut at its ends. */
        private void tile(TextureAtlasSprite sprite, Vector3f corner, Vector3f across, Vector3f up, int i, int j) {
            float acrossLength = across.length();
            float upLength = up.length();
            float a1 = Math.min(i + 1, acrossLength);
            float u1 = Math.min(j + 1, upLength);
            Vector3f along = new Vector3f(across).div(acrossLength);
            Vector3f rise = new Vector3f(up).div(upLength);
            quad(sprite, at(corner, along, i, rise, j), at(corner, along, a1, rise, j), at(corner, along, a1, rise, u1), at(corner, along, i, rise, u1),
                new float[]{0, 1, a1 - i, 1, a1 - i, 1 - (u1 - j), 0, 1 - (u1 - j)});
        }

        private static Vector3f at(Vector3f corner, Vector3f along, float a, Vector3f rise, float u) {
            return new Vector3f(corner).add(new Vector3f(along).mul(a)).add(new Vector3f(rise).mul(u));
        }

        /**
         * A quad through four corners, anticlockwise seen from in front, each with its place on the texture as shares
         * of it (u, v pairs), lit as a block facing that way.
         */
        void quad(TextureAtlasSprite sprite, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float[] uv) {
            // Across its diagonals, which holds where one of its sides has shrunk to nothing.
            Vector3f normal = new Vector3f(c).sub(a).cross(new Vector3f(d).sub(b)).normalize();
            Vector3f[] corners = {a, b, c, d};
            for (int k = 0; k < 4; k++) {
                float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * uv[2 * k];
                float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * uv[2 * k + 1];
                consumer.addVertex(pose, corners[k].x, corners[k].y, corners[k].z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(pose, normal.x, normal.y, normal.z);
            }
        }
    }
}
