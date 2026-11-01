package dev.skyliners.client;

import dev.skyliners.Skyliners;
import net.minecraft.resources.ResourceLocation;

/**
 * The sheet of light the orrery's renderer adds over its dome and its sun (see {@code orreryGlowTexture} in
 * {@code generate_textures.mjs}): each picture white on black, smoothed, for the renderer to tint as it adds it on.
 */
final class OrreryGlow {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/misc/orrery_glow.png");
    /** How big the sheet is, in pixels. */
    private static final int WIDTH = 256;
    private static final int HEIGHT = 128;
    /** The sun's corona, bright at its edge and fading out in soft streamers, 2.4 times as wide as the sun. */
    static final OrreryBrush.Patch CORONA = patch(0, 0, 64, 64);
    /** The wide soft glow round the sun. */
    static final OrreryBrush.Patch HALO = patch(64, 0, 64, 64);
    /** The sun's eight rays, long and short by turns. */
    static final OrreryBrush.Patch RAYS = patch(128, 0, 64, 64);
    /** A twinkling star's sparkle: a soft core with four thin points. */
    static final OrreryBrush.Patch SPARKLE = patch(192, 0, 32, 32);
    /** The light in one of the oculus ring's lamps: a soft-edged rounded oblong. */
    static final OrreryBrush.Patch LAMP = patch(224, 0, 32, 32);
    /** Plain white, well inside its square, for the orbits' tracks and the Earth's air. */
    static final OrreryBrush.Patch WHITE = patch(200, 40, 16, 16);
    /** The sun's boiling skin, once round its globe. */
    static final OrreryBrush.Patch BOILING = patch(0, 64, 128, 64);

    private OrreryGlow() {
    }

    private static OrreryBrush.Patch patch(int x, int y, int width, int height) {
        return OrreryBrush.Patch.of(x, y, width, height, WIDTH, HEIGHT);
    }
}
