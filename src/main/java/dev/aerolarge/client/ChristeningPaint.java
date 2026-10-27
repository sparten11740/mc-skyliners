package dev.aerolarge.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.aerolarge.airframe.FinArt;
import dev.aerolarge.airframe.FlankArt;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.block.ShipsPlaqueBlockEntity;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

/**
 * A christened zeppelin's crest and name as this client paints them on her flanks (see {@link FlankArt}), from what
 * her plaque keeps (see {@link ShipsPlaqueBlockEntity}): as the doves fly off, the paint sweeps back from the crest
 * along her name a letter at a time on both flanks, so the hull is meshed again a letter at a time. The letters are
 * the game's own, read from its font's sheet of glyphs, two blocks to a pixel where the name is short enough and one
 * where it is long, dark on a light livery and white on a dark one, whatever the livery under them is.
 */
public final class ChristeningPaint {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * How long after the bottle breaks the paint starts, in ticks, and how fast it sweeps back along her, in blocks a
     * tick.
     */
    private static final int PAINT_DELAY = 10;
    private static final double SWEEP = 3;
    private static final int GOLD = 0xD4AF37;
    private static final int DARK_LETTERS = 0x1C1C22;
    private static final int LIGHT_LETTERS = 0xF4F4F0;
    /** How light a livery must be, 0 to 1, for the letters on it to be dark. */
    private static final double LIGHT_LIVERY = 0.4;
    /** The game's own lettering: a sheet of 16 by 16 glyphs, each 8 pixels square, the first row of each on top. */
    private static final ResourceLocation FONT = ResourceLocation.withDefaultNamespace("textures/font/ascii.png");
    private static final int GLYPHS_ACROSS = 16;
    private static final int GLYPH_SIZE = 8;
    /** The glyph row the letters stand on, a space's width and the gap between letters, in pixels. */
    private static final int BASELINE = 7;
    private static final int SPACE = 3;
    private static final int GAP = 1;
    /** The glyphs read, for the characters from a space to a tilde; anything else is drawn as a question mark. */
    private static final char FIRST = ' ';
    private static final char LAST = '~';
    /** How many names' layouts are kept. */
    private static final int KEPT = 16;
    private static final boolean[] SIDES = {false, true};

    @Nullable
    private static Glyph[] glyphs;
    private static final Map<String, Name> NAMES = new LinkedHashMap<>(KEPT, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Name> eldest) {
            return size() > KEPT;
        }
    };

    private ChristeningPaint() {
    }

    /** One of the font's glyphs: its pixels, row by row from the top, and how wide it is. */
    private record Glyph(boolean[] pixels, int width) {
        boolean on(int column, int row) {
            return pixels[row * width + column];
        }
    }

    /**
     * A name laid out in glyphs, where each starts and how wide it all is, in pixels, and how many blocks to a pixel.
     */
    private record Name(List<Glyph> glyphs, int[] starts, int width, int scale) {
    }

    /** Her crest on both flanks: a banner's picture in a gold frame. */
    private record Crest(int[] picture) implements PaintOverlay {
        @Override
        public int colour(Livery.Spot spot, boolean fabric, Livery under) {
            int along = (int) Math.floor(spot.along()) - FlankArt.CREST_FRONT;
            if (fabric || along < 0 || along >= FlankArt.CREST_WIDTH || !FlankArt.onFlank(spot)) {
                return -1;
            }
            int row = (int) Math.floor(FlankArt.CREST_HEIGHT / 2.0 - FlankArt.rise(spot));
            if (row < 0 || row >= FlankArt.CREST_HEIGHT) {
                return -1;
            }
            int column = spot.across() > 0 ? FlankArt.CREST_WIDTH - 1 - along : along;
            if (column == 0 || column == FlankArt.CREST_WIDTH - 1 || row == 0 || row == FlankArt.CREST_HEIGHT - 1) {
                return GOLD;
            }
            return picture[(row - 1) * FinArt.BANNER_WIDTH + column - 1];
        }

        @Override
        public double from() {
            return FlankArt.CREST_FRONT;
        }

        @Override
        public double to() {
            return FlankArt.CREST_FRONT + FlankArt.CREST_WIDTH;
        }
    }

    /** A letter of her name on one flank, read the right way round from that side. */
    private record Letter(Name name, int index, boolean starboard) implements PaintOverlay {
        @Override
        public int colour(Livery.Spot spot, boolean fabric, Livery under) {
            if (fabric || spot.along() < from() || spot.along() >= to() || spot.across() > 0 != starboard || !FlankArt.onFlank(spot)) {
                return -1;
            }
            Glyph glyph = name.glyphs().get(index);
            int scale = name.scale();
            int row = (int) Math.floor((BASELINE * scale - FlankArt.rise(spot)) / scale);
            int across = (int) Math.floor((spot.along() - from()) / scale);
            int column = starboard ? glyph.width() - 1 - across : across;
            if (row < 0 || row >= GLYPH_SIZE || !glyph.on(column, row)) {
                return -1;
            }
            return lightness(under.colour(spot, false)) >= LIGHT_LIVERY ? DARK_LETTERS : LIGHT_LETTERS;
        }

        @Override
        public double from() {
            int start = name.starts()[index];
            int along = starboard ? name.width() - start - name.glyphs().get(index).width() : start;
            return FlankArt.NAME_FRONT + along * name.scale();
        }

        @Override
        public double to() {
            return from() + name.glyphs().get(index).width() * name.scale();
        }
    }

    /**
     * What a zeppelin's flanks are painted with, once she is christened: her crest and the letters of her name, as
     * far as the paint has swept along her since.
     */
    static List<PaintOverlay> flanks(ClientLevel level, BoundingBox box) {
        ShipsPlaqueBlockEntity plaque = plaque(level, box);
        if (plaque == null || !plaque.christened()) {
            return List.of();
        }
        double reached = FlankArt.CREST_FRONT + (level.getGameTime() - plaque.when() - PAINT_DELAY) * SWEEP;
        List<PaintOverlay> overlays = new ArrayList<>();
        if (plaque.hasCrest() && reached >= FlankArt.CREST_FRONT) {
            overlays.add(new Crest(ShipsColours.picture(plaque.crestBase(), plaque.crestPatterns())));
        }
        Name name = NAMES.computeIfAbsent(plaque.name(), ChristeningPaint::layOut);
        for (int index = 0; index < name.glyphs().size(); index++) {
            letter(overlays, name, index, reached);
        }
        return overlays;
    }

    /** A letter of a name on each flank, where the paint has reached it. */
    private static void letter(List<PaintOverlay> overlays, Name name, int index, double reached) {
        for (boolean starboard : SIDES) {
            Letter letter = new Letter(name, index, starboard);
            if (letter.from() <= reached) {
                overlays.add(letter);
            }
        }
    }

    @Nullable
    private static ShipsPlaqueBlockEntity plaque(ClientLevel level, BoundingBox box) {
        for (ShipsPlaqueBlockEntity plaque : ShipsPlaqueBlockEntity.loaded(level)) {
            if (box.isInside(plaque.getBlockPos())) {
                return plaque;
            }
        }
        return null;
    }

    /** A name laid out in the font's glyphs, cut short where it would run past the longest a name runs. */
    private static Name layOut(String text) {
        List<Glyph> laid = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        int x = 0;
        for (char character : text.toCharArray()) {
            Glyph glyph = glyph(character);
            if (x + glyph.width() > FlankArt.NAME_LONGEST) {
                break;
            }
            laid.add(glyph);
            starts.add(x);
            x += glyph.width() + GAP;
        }
        int width = Math.max(0, x - GAP);
        return new Name(laid, starts.stream().mapToInt(Integer::intValue).toArray(), width, width * 2 <= FlankArt.NAME_LONGEST ? 2 : 1);
    }

    /** A character's glyph, the sheet read the first time one is wanted. */
    private static Glyph glyph(char character) {
        if (glyphs == null) {
            glyphs = readGlyphs();
        }
        return character >= FIRST && character <= LAST ? glyphs[character - FIRST] : glyphs['?' - FIRST];
    }

    /** Every glyph from a space to a tilde, from the font's sheet; blank ones a space wide where it cannot be read. */
    private static Glyph[] readGlyphs() {
        Glyph[] read = new Glyph[LAST - FIRST + 1];
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FONT);
        try (InputStream in = resource.orElseThrow(() -> new IOException("missing")).open(); NativeImage sheet = NativeImage.read(in)) {
            for (char character = FIRST; character <= LAST; character++) {
                read[character - FIRST] = character == ' ' ? blank() : glyphOf(sheet, character);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read the font's glyphs for a zeppelin's name", e);
            Arrays.fill(read, blank());
        }
        return read;
    }

    private static Glyph blank() {
        return new Glyph(new boolean[GLYPH_SIZE * SPACE], SPACE);
    }

    /** A character's glyph on the sheet, as wide as its rightmost pixel. */
    private static Glyph glyphOf(NativeImage sheet, char character) {
        int cell = sheet.getWidth() / GLYPHS_ACROSS;
        int step = Math.max(1, cell / GLYPH_SIZE);
        int left = character % GLYPHS_ACROSS * cell;
        int top = character / GLYPHS_ACROSS * cell;
        boolean[] square = new boolean[GLYPH_SIZE * GLYPH_SIZE];
        int width = 0;
        for (int pixel = 0; pixel < square.length; pixel++) {
            square[pixel] = (sheet.getPixelRGBA(left + pixel % GLYPH_SIZE * step, top + pixel / GLYPH_SIZE * step) >>> 24) > 0;
            width = square[pixel] ? Math.max(width, pixel % GLYPH_SIZE + 1) : width;
        }
        boolean[] pixels = new boolean[GLYPH_SIZE * Math.max(1, width)];
        for (int pixel = 0; pixel < pixels.length; pixel++) {
            pixels[pixel] = square[pixel / Math.max(1, width) * GLYPH_SIZE + pixel % Math.max(1, width)];
        }
        return new Glyph(pixels, Math.max(1, width));
    }

    /** How light a colour is, 0 to 1, as the eye sees it. */
    private static double lightness(int colour) {
        return (0.2126 * (colour >> 16 & 0xFF) + 0.7152 * (colour >> 8 & 0xFF) + 0.0722 * (colour & 0xFF)) / 255;
    }
}
