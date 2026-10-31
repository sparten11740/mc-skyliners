package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import net.minecraft.resources.ResourceLocation;

/**
 * The sheet of light the orrery's renderer adds over its dome and its planets (see {@code orreryGlowTexture} in
 * {@code generate_textures.mjs}): each picture white on black, smoothed, for the renderer to tint as it adds it on.
 */
final class OrreryGlow {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/misc/orrery_glow.png");
    /** How big the sheet is, in pixels. */
    private static final int WIDTH = 256;
    private static final int HEIGHT = 128;
    /** A twinkling star's sparkle: a soft core with four thin points. */
    static final OrreryBrush.Patch SPARKLE = OrreryBrush.Patch.of(192, 0, 32, 32, WIDTH, HEIGHT);

    private OrreryGlow() {
    }
}
