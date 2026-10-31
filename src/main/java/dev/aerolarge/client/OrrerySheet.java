package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import net.minecraft.resources.ResourceLocation;

/**
 * The sheet the solid parts of the rocket's orrery and its dome are drawn from (see {@code orrerySheetTexture} in
 * {@code generate_textures.mjs}), each picture at its place on it.
 */
final class OrrerySheet {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/misc/orrery_sheet.png");
    /** How big the sheet is, in pixels. */
    private static final int WIDTH = 256;
    private static final int HEIGHT = 128;
    /** Polished brass, brushed along its width. */
    static final OrreryBrush.Patch BRASS = OrreryBrush.Patch.of(160, 32, 32, 32, WIDTH, HEIGHT);
    /** Dark gunmetal, fine lines turned along its width. */
    static final OrreryBrush.Patch GUNMETAL = OrreryBrush.Patch.of(224, 32, 32, 32, WIDTH, HEIGHT);
    /** A lamp's window: smoked glass in a bright bezel. */
    static final OrreryBrush.Patch LAMP_WINDOW = OrreryBrush.Patch.of(128, 64, 16, 16, WIDTH, HEIGHT);

    private OrrerySheet() {
    }
}
