package dev.skyliners.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skyliners.Skyliners;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Lines of type set for the flank screens, as textures: white letters in a bold typeface, smooth-edged, on nothing,
 * drawn off screen once for each line and kept, a few at a time; and lines that change as they are read, such as a
 * zeppelin's height, each kept in one texture drawn over as its words change.
 */
final class ScreenType {
    /** How tall a line of type is drawn, in pixels, and how much room is left round it. */
    private static final int TYPE_SIZE = 64;
    private static final int MARGIN = 8;
    /** How many set lines are kept. */
    private static final int KEPT = 24;

    /** A line set as a texture: where it is, and how wide it is for how tall. */
    record Line(ResourceLocation texture, float aspect) {
    }

    private static final Map<String, Line> SET = new LinkedHashMap<>(KEPT, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Line> eldest) {
            boolean gone = size() > KEPT;
            if (gone) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue().texture());
            }
            return gone;
        }
    };
    /** The lines that change, by what they show, and what each last said. */
    private static final Map<String, Line> CHANGING = new HashMap<>();
    private static final Map<String, String> SAYING = new HashMap<>();
    private static int made;

    private ScreenType() {
    }

    /** A line of type, set once. */
    static Line line(String words) {
        return SET.computeIfAbsent(words, ScreenType::set);
    }

    /** A line of type that changes, named for what it shows: set again only when its words have changed. */
    static Line changing(String name, String words) {
        if (words.equals(SAYING.get(name))) {
            return CHANGING.get(name);
        }
        Line before = CHANGING.remove(name);
        if (before != null) {
            Minecraft.getInstance().getTextureManager().release(before.texture());
        }
        Line line = set(words);
        CHANGING.put(name, line);
        SAYING.put(name, words);
        return line;
    }

    private static Line set(String words) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, TYPE_SIZE);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measure = probe.createGraphics();
        FontMetrics metrics = measure.getFontMetrics(font);
        measure.dispose();
        int width = Math.max(1, metrics.stringWidth(words)) + 2 * MARGIN;
        int height = metrics.getAscent() + metrics.getDescent() + 2 * MARGIN;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        graphics.drawString(words, MARGIN, MARGIN + metrics.getAscent());
        graphics.dispose();
        NativeImage pixels = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                pixels.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF);
            }
        }
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "screen_type/" + made++);
        Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(pixels));
        return new Line(texture, width / (float) height);
    }
}
