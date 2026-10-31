package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import net.minecraft.resources.ResourceLocation;

/**
 * The sheet the solid parts of the rocket's orrery and its dome are drawn from (see {@code orrerySheetTexture} in
 * {@code generate_textures.mjs}), each picture at its place on it. A globe's picture goes once round it from west to
 * east and from its north pole down.
 */
final class OrrerySheet {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/misc/orrery_sheet.png");
    /** How big the sheet is, in pixels. */
    private static final int WIDTH = 256;
    private static final int HEIGHT = 128;
    /** The sun's boiling granules, with a few sunspots. */
    static final OrreryBrush.Patch SUN = patch(0, 0, 64, 32);
    /** Jupiter's bands and his Great Red Spot. */
    static final OrreryBrush.Patch JUPITER = patch(64, 0, 64, 32);
    /** Saturn's pale gold bands. */
    static final OrreryBrush.Patch SATURN = patch(128, 0, 64, 32);
    /** The Earth's seas, continents, poles and clouds. */
    static final OrreryBrush.Patch EARTH = patch(192, 0, 32, 16);
    /** Venus's swirled cream clouds. */
    static final OrreryBrush.Patch VENUS = patch(224, 0, 32, 16);
    /** Rusty Mars with his polar caps. */
    static final OrreryBrush.Patch MARS = patch(192, 16, 32, 16);
    /** Cratered grey Mercury. */
    static final OrreryBrush.Patch MERCURY = patch(224, 16, 32, 16);
    /** The Moon's grey, its dark seas and bright craters. */
    static final OrreryBrush.Patch MOON = patch(0, 32, 32, 16);
    /** Saturn's ring, from its inner edge across to its outer, clear in its gaps. */
    static final OrreryBrush.Patch RING = patch(32, 32, 128, 8);
    /** Polished brass, brushed along its width. */
    static final OrreryBrush.Patch BRASS = patch(160, 32, 32, 32);
    /** Cool brushed steel. */
    static final OrreryBrush.Patch STEEL = patch(192, 32, 32, 32);
    /** Dark gunmetal, fine lines turned along its width. */
    static final OrreryBrush.Patch GUNMETAL = patch(224, 32, 32, 32);
    /** The orrery's gears seen face on, clear round their teeth: the big one and the small one. */
    static final OrreryBrush.Patch BIG_GEAR = patch(0, 64, 64, 64);
    static final OrreryBrush.Patch SMALL_GEAR = patch(64, 64, 64, 64);
    /** A lamp's window: smoked glass in a bright bezel. */
    static final OrreryBrush.Patch LAMP_WINDOW = patch(128, 64, 16, 16);

    private OrrerySheet() {
    }

    private static OrreryBrush.Patch patch(int x, int y, int width, int height) {
        return OrreryBrush.Patch.of(x, y, width, height, WIDTH, HEIGHT);
    }
}
