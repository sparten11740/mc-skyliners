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
 * seats). Each face is tiled with a block texture, a block to a tile, and lit as a block is, by the light where the
 * panel stands and shaded by the way it faces, so the panels, turned each a step further toward the seats, stand
 * apart. The desks of neighbouring panels are drawn as one moulded console round the seats: their textures plain, the
 * slope's brushed along its length, so nothing breaks off where one desk meets the next; their shading blended across
 * each join, so the light rolls round the turn instead of creasing at it; and a steel trim along the slope's front lip
 * and its top edge, where it meets the screen, which runs on unbroken from panel to panel.
 */
final class CockpitCasing {
    private static final ResourceLocation SCREEN_CASING = block("passenger_screen_casing");
    private static final ResourceLocation DESK = block("cockpit_desk");
    private static final ResourceLocation SLOPE = block("cockpit_desk_top");
    private static final ResourceLocation TRIM = block("cockpit_trim");
    /** How wide the trim along the slope's edges is, up the slope, in blocks. */
    private static final float TRIM_WIDTH = 0.04F;
    /** How far the trim stands off the slope, in blocks, so it is drawn over it. */
    private static final float TRIM_LIFT = 0.0015F;
    /**
     * How high the desk's back stands where it carries on past the screen, in blocks over the floor: as high as the
     * blank casing behind the row, a block.
     */
    private static final float UPSTAND = 1;

    private CockpitCasing() {
    }

    /** The casing of a panel this wide, lit with the light where it stands. */
    static void draw(MultiBufferSource buffers, PoseStack.Pose pose, float width, CockpitDisplayBlockEntity.Panel panel, int light) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        Face face = new Face(consumer, pose, light);
        slab(face, sprite(SCREEN_CASING), width, panel);
        if (panel.desk() > 0) {
            Desk desk = new Desk(panel, width);
            desk.body(face, sprite(DESK));
            desk.slope(face, sprite(SLOPE));
            desk.trim(face, sprite(TRIM));
            desk.upstand(face, sprite(DESK));
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

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
    }

    private static ResourceLocation block(String name) {
        return ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "block/" + name);
    }

    /**
     * The desk under a panel's screen, down to the floor, from where it starts along the panel to where it ends (past
     * the screen's ends where it carries on to the hull): where its sloped top's front edge stops short of either end,
     * mitred against the desk beside it, and how far its neighbours are turned from it there, read back from the
     * mitre (none where it has no neighbour).
     */
    private record Desk(CockpitDisplayBlockEntity.Panel panel, float width, float start, float end, float left, float right, double leftTurn,
                        double rightTurn) {
        Desk(CockpitDisplayBlockEntity.Panel panel, float width) {
            this(panel, width, -panel.leftEnd(), width + panel.rightEnd(), panel.leftCut() - panel.leftEnd(),
                width + panel.rightEnd() - panel.rightCut(), 2 * Math.atan(panel.leftCut() / panel.desk()), 2 * Math.atan(panel.rightCut() / panel.desk()));
        }

        float reach() {
            return panel.desk();
        }

        float front() {
            return panel.bottom() - panel.desk();
        }

        /** Its front, its shading blended at its ends toward the fronts beside it; its back; and its ends. */
        void body(Face face, TextureAtlasSprite sprite) {
            float back = -CockpitDisplayBlock.DEPTH;
            float bottom = panel.bottom();
            Vector3f out = new Vector3f(0, 0, 1);
            Vector3f leftOut = blended(out, new Vector3f((float) Math.sin(leftTurn), 0, (float) Math.cos(leftTurn)));
            Vector3f rightOut = blended(out, new Vector3f((float) -Math.sin(rightTurn), 0, (float) Math.cos(rightTurn)));
            for (int tile = 0; tile < Math.ceil(right - left - 1e-4); tile++) {
                float x0 = left + tile;
                float x1 = Math.min(x0 + 1, right);
                Vector3f atLeft = tile == 0 ? leftOut : out;
                Vector3f atRight = x1 == right ? rightOut : out;
                face.quad(sprite, new Vector3f(x0, 0, reach()), new Vector3f(x1, 0, reach()), new Vector3f(x1, front(), reach()),
                    new Vector3f(x0, front(), reach()), new float[]{0, 1, x1 - x0, 1, x1 - x0, 1 - front(), 0, 1 - front()},
                    new Vector3f[]{atLeft, atRight, atRight, atLeft});
            }
            face.tiled(sprite, new Vector3f(end, 0, back), new Vector3f(start - end, 0, 0), new Vector3f(0, bottom, 0));
            face.tiled(sprite, new Vector3f(start, 0, back), new Vector3f(0, 0, -back), new Vector3f(0, bottom, 0));
            face.tiled(sprite, new Vector3f(end, 0, 0), new Vector3f(0, 0, back), new Vector3f(0, bottom, 0));
            float mitre = (float) Math.hypot(left - start, reach());
            face.quad(sprite, new Vector3f(start, 0, 0), new Vector3f(left, 0, reach()), new Vector3f(left, front(), reach()),
                new Vector3f(start, bottom, 0), new float[]{0, 1, mitre, 1, mitre, 1 - front(), 0, 1 - bottom});
            float rightMitre = (float) Math.hypot(end - right, reach());
            face.quad(sprite, new Vector3f(right, 0, reach()), new Vector3f(end, 0, 0), new Vector3f(end, bottom, 0), new Vector3f(right, front(), reach()),
                new float[]{0, 1, rightMitre, 1, rightMitre, 1 - bottom, 0, 1 - front()});
        }

        /**
         * The back of the desk where it carries on past the screen, stood up as high as the blank casing behind it, so
         * that does not show over it.
         */
        void upstand(Face face, TextureAtlasSprite sprite) {
            if (panel.bottom() >= UPSTAND) {
                return;
            }
            if (start < 0) {
                box(face, sprite, start, 0);
            }
            if (end > width) {
                box(face, sprite, width, end);
            }
        }

        /** A box behind the slope's top edge, up to the upstand's height, between two places along the panel. */
        private void box(Face face, TextureAtlasSprite sprite, float x0, float x1) {
            float back = -CockpitDisplayBlock.DEPTH;
            float bottom = panel.bottom();
            float height = UPSTAND - bottom;
            face.tiled(sprite, new Vector3f(x0, bottom, 0), new Vector3f(x1 - x0, 0, 0), new Vector3f(0, height, 0));
            face.tiled(sprite, new Vector3f(x1, bottom, back), new Vector3f(x0 - x1, 0, 0), new Vector3f(0, height, 0));
            face.tiled(sprite, new Vector3f(x0, UPSTAND, 0), new Vector3f(x1 - x0, 0, 0), new Vector3f(0, 0, back));
            face.tiled(sprite, new Vector3f(x0, bottom, back), new Vector3f(0, 0, -back), new Vector3f(0, height, 0));
            face.tiled(sprite, new Vector3f(x1, bottom, 0), new Vector3f(0, 0, back), new Vector3f(0, height, 0));
        }

        /**
         * The sloped top, a block's width at a time, cut where it is mitred, its shading blended at either end toward
         * the slope beside it. Along the slope a tile's height of the texture is a block, so the slope, shorter, shows
         * the bottom of it.
         */
        void slope(Face face, TextureAtlasSprite sprite) {
            Vector3f up = slopeNormal(0);
            Vector3f leftUp = blended(up, slopeNormal(leftTurn));
            Vector3f rightUp = blended(up, slopeNormal(-rightTurn));
            float top = 1 - panel.slope();
            for (int tile = 0; tile < Math.ceil(end - start - 1e-4); tile++) {
                float x0 = start + tile;
                float frontLeft = Math.max(x0, left);
                float frontRight = Math.max(frontLeft, Math.min(x0 + 1, right));
                float backRight = Math.min(x0 + 1, end);
                Vector3f atLeft = tile == 0 ? leftUp : up;
                Vector3f atRight = backRight == end ? rightUp : up;
                face.quad(sprite, new Vector3f(frontLeft, front(), reach()), new Vector3f(frontRight, front(), reach()),
                    new Vector3f(backRight, panel.bottom(), 0), new Vector3f(x0, panel.bottom(), 0),
                    new float[]{frontLeft - x0, 1, frontRight - x0, 1, backRight - x0, top, 0, top}, new Vector3f[]{atLeft, atRight, atRight, atLeft});
            }
        }

        /**
         * The trim along the slope's edges: a band up from its front edge, between the mitres, and a band down from its
         * top edge along the screen, end to end, each lifted a little off the slope.
         */
        void trim(Face face, TextureAtlasSprite sprite) {
            float share = TRIM_WIDTH / panel.slope();
            band(face, sprite, 0, share);
            band(face, sprite, 1 - share, 1);
        }

        /**
         * A band of the trim across the slope, between two shares of the way up it from its front edge, a block's
         * width of the texture to each block along it, as the slope is, so it looks the same on every panel.
         */
        private void band(Face face, TextureAtlasSprite sprite, float from, float to) {
            Vector3f lift = slopeNormal(0).mul(TRIM_LIFT);
            float lowLeft = left + (start - left) * from;
            float lowRight = right + (end - right) * from;
            float highLeft = left + (start - left) * to;
            float highRight = right + (end - right) * to;
            for (int tile = 0; tile < Math.ceil(end - start - 1e-4); tile++) {
                float x0 = start + tile;
                float low0 = Math.max(x0, lowLeft);
                float low1 = Math.max(low0, Math.min(x0 + 1, lowRight));
                float high0 = Math.max(x0, highLeft);
                float high1 = Math.max(high0, Math.min(x0 + 1, highRight));
                if (low1 - low0 < 1e-4 && high1 - high0 < 1e-4) {
                    continue;
                }
                face.quad(sprite, onSlope(low0, from).add(lift), onSlope(low1, from).add(lift), onSlope(high1, to).add(lift),
                    onSlope(high0, to).add(lift), new float[]{low0 - x0, 1, low1 - x0, 1, high1 - x0, 0, high0 - x0, 0});
            }
        }

        /** A point of the slope: so far along it, a share of the way up it from its front edge. */
        private Vector3f onSlope(float x, float share) {
            return new Vector3f(x, front() + reach() * share, reach() * (1 - share));
        }

        /** Which way a slope faces, turned about the upright by an angle from this one's: up and out at 45 degrees. */
        private static Vector3f slopeNormal(double turn) {
            return new Vector3f((float) Math.sin(turn), 1, (float) Math.cos(turn)).normalize();
        }

        /** Halfway between two ways of facing, where two faces meet, for shading that runs on across the join. */
        private static Vector3f blended(Vector3f own, Vector3f neighbour) {
            return new Vector3f(own).add(neighbour).normalize();
        }
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
            quad(sprite, a, b, c, d, uv, new Vector3f[]{normal, normal, normal, normal});
        }

        /** A quad as above, each corner lit as facing its own way, so the shading runs on smoothly across it. */
        void quad(TextureAtlasSprite sprite, Vector3f a, Vector3f b, Vector3f c, Vector3f d, float[] uv, Vector3f[] normals) {
            Vector3f[] corners = {a, b, c, d};
            for (int k = 0; k < 4; k++) {
                float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * uv[2 * k];
                float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * uv[2 * k + 1];
                consumer.addVertex(pose, corners[k].x, corners[k].y, corners[k].z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(pose, normals[k].x, normals[k].y, normals[k].z);
            }
        }
    }
}
