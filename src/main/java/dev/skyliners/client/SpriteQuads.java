package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Quads textured from block textures, for a renderer that builds its parts out of textured boxes and eight-sided
 * prisms, as the freighter's laser cannon does (see {@link LaserCannonRenderer}). Each face takes its texture at one
 * fineness, {@link #PIXELS_A_BLOCK}, from the texture's top left corner at the face's own corner, so the plates' seams
 * along their top and left edges run along the faces' and a small face shows as much detail as a big one; a face too
 * big for its texture squeezes all of it in rather than reading past it into its neighbours on the atlas. A few faces
 * take a whole texture whatever their size instead: a lens, a tube's mouth, a rocket's nose. Drawn with the light and
 * the tint last given, so a glowing part is drawn at full brightness in its glow's colour.
 */
final class SpriteQuads {
    /** How many of a texture's pixels go to a block along a face: as many as on the freighter's 32-pixel plates. */
    static final float PIXELS_A_BLOCK = 32;
    /** An eighth of a turn's half, the angle from an eight-sided prism's flat to its corner, in radians. */
    private static final double EIGHTH_HALF = Math.PI / 8;
    /**
     * Where an eight-sided prism's corners lie round it, as shares of how far out they are: across and up, counted from
     * the one just above the flat to the right, and so on round.
     */
    private static final float[] CORNER_ACROSS = new float[9];
    private static final float[] CORNER_UP = new float[9];
    /** Which way each of its sides faces, across and up, in the same order, each between the corner and the next. */
    private static final float[] SIDE_ACROSS = new float[8];
    private static final float[] SIDE_UP = new float[8];

    static {
        for (int k = 0; k <= 8; k++) {
            CORNER_ACROSS[k] = (float) Math.cos(EIGHTH_HALF + k * Math.PI / 4);
            CORNER_UP[k] = (float) Math.sin(EIGHTH_HALF + k * Math.PI / 4);
        }
        for (int k = 0; k < 8; k++) {
            SIDE_ACROSS[k] = (float) Math.cos((k + 1) * Math.PI / 4);
            SIDE_UP[k] = (float) Math.sin((k + 1) * Math.PI / 4);
        }
    }
    /** An untinted colour, the texture's own. */
    private static final int UNTINTED = 0xFFFFFFFF;

    private final VertexConsumer consumer;
    private final PoseStack poses;
    private int light;
    private int colour = UNTINTED;

    /** Quads into a buffer, each drawn in the pose on top of the stack as it is drawn, in this light, untinted. */
    SpriteQuads(VertexConsumer consumer, PoseStack poses, int light) {
        this.consumer = consumer;
        this.poses = poses;
        this.light = light;
    }

    /** A block texture on the blocks' atlas, by its name under the mod's block textures. */
    static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
    }

    /** Draws what follows in this light and tinted this colour, as an opaque colour's red, green and blue. */
    SpriteQuads lit(int light, int colour) {
        this.light = light;
        this.colour = colour;
        return this;
    }

    /**
     * The textures a box's faces take: its top, its bottom, its sides square to x and its ends square to z, the ends
     * and sides read upright, the top and bottom with their top row toward negative z.
     */
    record Faces(TextureAtlasSprite top, TextureAtlasSprite bottom, TextureAtlasSprite sides, TextureAtlasSprite ends) {
        /** Every face the same texture. */
        static Faces all(TextureAtlasSprite sprite) {
            return new Faces(sprite, sprite, sprite, sprite);
        }
    }

    /** A box square to the pose, from one corner to the other, each face textured as the faces say. */
    void box(Faces faces, float x0, float y0, float z0, float x1, float y1, float z1) {
        float width = x1 - x0;
        float height = y1 - y0;
        float depth = z1 - z0;
        Window top = Window.of(faces.top, width, depth);
        Window bottom = Window.of(faces.bottom, width, depth);
        Window side = Window.of(faces.sides, depth, height);
        Window end = Window.of(faces.ends, width, height);
        quad(top, 0, 1, 0, x0, y1, z0, 0, 0, x0, y1, z1, 0, 1, x1, y1, z1, 1, 1, x1, y1, z0, 1, 0);
        quad(bottom, 0, -1, 0, x0, y0, z0, 0, 0, x1, y0, z0, 1, 0, x1, y0, z1, 1, 1, x0, y0, z1, 0, 1);
        quad(end, 0, 0, -1, x0, y0, z0, 1, 1, x0, y1, z0, 1, 0, x1, y1, z0, 0, 0, x1, y0, z0, 0, 1);
        quad(end, 0, 0, 1, x0, y0, z1, 0, 1, x1, y0, z1, 1, 1, x1, y1, z1, 1, 0, x0, y1, z1, 0, 0);
        quad(side, -1, 0, 0, x0, y0, z0, 0, 1, x0, y0, z1, 1, 1, x0, y1, z1, 1, 0, x0, y1, z0, 0, 0);
        quad(side, 1, 0, 0, x1, y0, z0, 1, 1, x1, y1, z0, 1, 0, x1, y1, z1, 0, 0, x1, y0, z1, 0, 1);
    }

    /**
     * The eight sides of a prism along z, round a line through x and y, so far from it to the middle of each flat,
     * flats up, down and to either side, from one end to the other: each side reads along the prism, all of them alike.
     */
    void prism(TextureAtlasSprite sprite, float x, float y, float apothem, float z0, float z1) {
        float corner = (float) (apothem / Math.cos(EIGHTH_HALF));
        Window side = Window.of(sprite, z1 - z0, (float) (2 * apothem * Math.tan(EIGHTH_HALF)));
        for (int k = 0; k < 8; k++) {
            float ax = x + corner * CORNER_ACROSS[k];
            float ay = y + corner * CORNER_UP[k];
            float bx = x + corner * CORNER_ACROSS[k + 1];
            float by = y + corner * CORNER_UP[k + 1];
            quad(side, SIDE_ACROSS[k], SIDE_UP[k], 0, ax, ay, z0, 0, 0, bx, by, z0, 0, 1, bx, by, z1, 1, 1, ax, ay, z1, 1, 0);
        }
    }

    /**
     * An end of such a prism at z, facing along the prism (forward) or back down it: the texture laid on at the set
     * fineness, or the whole of it over the end (fitted), as a lens's is.
     */
    void end(TextureAtlasSprite sprite, float x, float y, float apothem, float z, boolean forward, boolean fitted) {
        float corner = (float) (apothem / Math.cos(EIGHTH_HALF));
        float across = 2 * corner;
        Window window = fitted ? Window.whole(sprite) : Window.of(sprite, across, across);
        float facing = forward ? 1 : -1;
        for (int k = 0; k < 8; k += 2) {
            int next = k + 1;
            int first = forward ? k : (k + 2) % 8;
            int last = forward ? (k + 2) % 8 : k;
            quad(window, 0, 0, facing, x, y, z, 0.5F, 0.5F,
                x + corner * CORNER_ACROSS[first], y + corner * CORNER_UP[first], z, (1 + CORNER_ACROSS[first]) / 2, (1 - CORNER_UP[first]) / 2,
                x + corner * CORNER_ACROSS[next], y + corner * CORNER_UP[next], z, (1 + CORNER_ACROSS[next]) / 2, (1 - CORNER_UP[next]) / 2,
                x + corner * CORNER_ACROSS[last], y + corner * CORNER_UP[last], z, (1 + CORNER_ACROSS[last]) / 2, (1 - CORNER_UP[last]) / 2);
        }
    }

    /** A square facing forward along z, from one corner to the other, the whole texture over it, upright. */
    void square(TextureAtlasSprite sprite, float x0, float y0, float x1, float y1, float z) {
        quad(Window.whole(sprite), 0, 0, 1, x0, y0, z, 0, 1, x1, y0, z, 1, 1, x1, y1, z, 1, 0, x0, y1, z, 0, 0);
    }

    /** A strip lying flat facing up, along z from one end to the other, so wide across x, its texture read along it. */
    void strip(TextureAtlasSprite sprite, float x, float y, float halfWidth, float z0, float z1) {
        Window window = Window.of(sprite, z1 - z0, 2 * halfWidth);
        float x0 = x - halfWidth;
        float x1 = x + halfWidth;
        quad(window, 0, 1, 0, x0, y, z0, 0, 0, x0, y, z1, 1, 0, x1, y, z1, 1, 1, x1, y, z0, 0, 1);
    }

    /**
     * A pyramid pointing forward along z from a square base at z round a point, so far out from it to the base's
     * edges, its tip so far ahead: each face takes the whole texture, its top row at the tip.
     */
    void pyramid(TextureAtlasSprite sprite, float x, float y, float half, float z, float tip) {
        Window window = Window.whole(sprite);
        float left = x - half;
        float right = x + half;
        float low = y - half;
        float high = y + half;
        float top = z + tip;
        quad(window, 0, -1, 0, left, low, z, 0, 1, right, low, z, 1, 1, x, y, top, 0.5F, 0, x, y, top, 0.5F, 0);
        quad(window, 1, 0, 0, right, low, z, 0, 1, right, high, z, 1, 1, x, y, top, 0.5F, 0, x, y, top, 0.5F, 0);
        quad(window, 0, 1, 0, right, high, z, 0, 1, left, high, z, 1, 1, x, y, top, 0.5F, 0, x, y, top, 0.5F, 0);
        quad(window, -1, 0, 0, left, high, z, 0, 1, left, low, z, 1, 1, x, y, top, 0.5F, 0, x, y, top, 0.5F, 0);
    }

    /**
     * A quad of four corners, each with where it reads its window as shares of the window across and down, the window
     * the part of its texture the face takes.
     */
    private void quad(Window window, float nx, float ny, float nz, float x1, float y1, float z1, float u1, float v1, float x2, float y2, float z2,
                      float u2, float v2, float x3, float y3, float z3, float u3, float v3, float x4, float y4, float z4, float u4, float v4) {
        vertex(window, x1, y1, z1, u1, v1, nx, ny, nz);
        vertex(window, x2, y2, z2, u2, v2, nx, ny, nz);
        vertex(window, x3, y3, z3, u3, v3, nx, ny, nz);
        vertex(window, x4, y4, z4, u4, v4, nx, ny, nz);
    }

    private void vertex(Window window, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        PoseStack.Pose pose = poses.last();
        consumer.addVertex(pose, x, y, z)
            .setColor(colour)
            .setUv(window.sprite.getU(u * window.across), window.sprite.getV(v * window.down))
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    /**
     * The part of a texture a face takes, from its top left corner, as shares of the texture across and down: as much
     * as the face covers at the set fineness, all of it at most.
     */
    private record Window(TextureAtlasSprite sprite, float across, float down) {
        static Window of(TextureAtlasSprite sprite, float width, float height) {
            return new Window(sprite, Math.min(1, width * PIXELS_A_BLOCK / sprite.contents().width()),
                Math.min(1, height * PIXELS_A_BLOCK / sprite.contents().height()));
        }

        static Window whole(TextureAtlasSprite sprite) {
            return new Window(sprite, 1, 1);
        }
    }
}
