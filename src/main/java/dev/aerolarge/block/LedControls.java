package dev.aerolarge.block;

import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Where the controls of the rocket's LED strips lie on the faces that set them, for the server to tell what a touch
 * landed on and the client to draw: on a story's panel (see {@link LedPanelBlock}) and on the cockpit's console (see
 * {@link LedConsoleBlock}). Every control is a picture or a colour, never a word: the ten colours as swatches, the four
 * brightnesses as a sun from dark to blazing, the four modes as little strips doing what they do; and on the console,
 * her silhouette laid on her side, each story a band of its own colour, and a button with all of her lit for all of
 * them. Positions are on the face drawn a row tall (see {@link WideScreenBlock}), from its bottom left corner as the
 * viewer sees it: in blocks on a panel, and in rows' heights on the console, which is two rows tall.
 */
public final class LedControls {
    /** What a touch landed on. */
    public sealed interface Control permits Swatch, Glow, Mode, Story, WholeRocket {
    }

    /** One of the colours' swatches. */
    public record Swatch(LedColour colour) implements Control {
    }

    /** One of the brightnesses, off ({@code 0}) to full. */
    public record Glow(int brightness) implements Control {
    }

    /** One of the modes. */
    public record Mode(LedMode mode) implements Control {
    }

    /** One of the stories on the console's silhouette, counted from the entry story up. */
    public record Story(int story) implements Control {
    }

    /** The console's button for the whole of her, every story at once. */
    public record WholeRocket() implements Control {
    }

    /**
     * The two faces the controls are on: a story's panel, a block square, the swatches in two rows of five over the
     * brightnesses and the modes; and the console, two of its rows wide to one tall, her silhouette and the
     * whole-rocket button along its top, the swatches in a row under them and the brightnesses and the modes side by
     * side at the bottom.
     */
    public enum Face {
        PANEL(1, new Box(0.07, 0.55, 0.93, 0.93), 5, 2, new Box(0.07, 0.30, 0.93, 0.49), new Box(0.07, 0.06, 0.93, 0.25)),
        CONSOLE(2, new Box(0.05, 0.29, 1.95, 0.47), 10, 1, new Box(0.05, 0.05, 0.97, 0.23), new Box(1.03, 0.05, 1.95, 0.23));

        /** How wide the face is, in its rows' heights. */
        private final double width;
        private final Box swatches;
        private final int swatchColumns;
        private final int swatchRows;
        private final Box glows;
        private final Box modes;

        Face(double width, Box swatches, int swatchColumns, int swatchRows, Box glows, Box modes) {
            this.width = width;
            this.swatches = swatches;
            this.swatchColumns = swatchColumns;
            this.swatchRows = swatchRows;
            this.glows = glows;
            this.modes = modes;
        }

        public double width() {
            return width;
        }

        /** Where a colour's swatch is: in rows of so many from the top left, in the order of {@link LedColour}. */
        public Box swatch(LedColour colour) {
            return cell(swatches, swatchColumns, swatchRows, colour.ordinal());
        }

        /** Where a brightness's button is, off at the left. */
        public Box glow(int brightness) {
            return cell(glows, LedStripBlock.BRIGHTEST + 1, 1, brightness);
        }

        /** Where a mode's button is, in the order of {@link LedMode}. */
        public Box mode(LedMode mode) {
            return cell(modes, LedMode.values().length, 1, mode.ordinal());
        }

        /** The swatch, brightness or mode at a place on the face; null between them. */
        @Nullable
        Control at(double across, double up) {
            for (LedColour colour : LedColour.values()) {
                if (swatch(colour).contains(across, up)) {
                    return new Swatch(colour);
                }
            }
            for (int brightness = 0; brightness <= LedStripBlock.BRIGHTEST; brightness++) {
                if (glow(brightness).contains(across, up)) {
                    return new Glow(brightness);
                }
            }
            for (LedMode mode : LedMode.values()) {
                if (mode(mode).contains(across, up)) {
                    return new Mode(mode);
                }
            }
            return null;
        }
    }

    /** The gap between two buttons in a row or a column, in blocks on the face. */
    private static final double GAP = 0.03;
    /** Where the console's silhouette of her lies, and the whole-rocket button beside it. */
    public static final Box PICTURE = new Box(0.05, 0.53, 1.63, 0.95);
    public static final Box WHOLE_ROCKET = new Box(1.69, 0.53, 1.95, 0.95);
    /**
     * How far along the silhouette her stories run, as shares of its length from her foot at the left: the engine room
     * and her fins under them, her cockpit and her nose over them, about as she is built.
     */
    public static final double STORIES_FROM = 0.12;
    public static final double STORIES_TO = 0.8;
    /** The notes a swatch chimes, in semitones from the lowest, a rising pentatonic scale along the colours. */
    private static final int[] SWATCH_NOTES = {0, 2, 4, 7, 9, 12, 14, 16, 19, 21};
    /** The lowest swatch's note, as a pitch; and how much higher a brightness's click is than the one before. */
    private static final float LOWEST_NOTE = 0.5F;
    private static final float CLICK_PITCH = 0.7F;
    private static final float CLICK_STEP = 0.2F;
    /** How loud the panels' sounds are. */
    private static final float VOLUME = 0.5F;

    private LedControls() {
    }

    /** The k-th cell of a grid of so many columns and rows over an area, counting along the top row first. */
    private static Box cell(Box area, int columns, int rows, int k) {
        double width = (area.x1() - area.x0() - (columns - 1) * GAP) / columns;
        double tall = (area.y1() - area.y0() - (rows - 1) * GAP) / rows;
        double left = area.x0() + k % columns * (width + GAP);
        double top = area.y1() - k / columns * (tall + GAP);
        return new Box(left, top - tall, left + width, top);
    }

    /** The control on a story's panel at a place on its face; null for none. */
    @Nullable
    public static Control onPanel(double across, double up) {
        return Face.PANEL.at(across, up);
    }

    /**
     * The control on the console at a place on its face, its stories' floors and ceilings given in pairs (see
     * {@link #band}); null for none.
     */
    @Nullable
    public static Control onConsole(double across, double up, int[] spans) {
        if (WHOLE_ROCKET.contains(across, up)) {
            return new WholeRocket();
        }
        for (int story = 0; story < spans.length / 2; story++) {
            if (band(spans, story).contains(across, up)) {
                return new Story(story);
            }
        }
        return Face.CONSOLE.at(across, up);
    }

    /**
     * Where a story lies along the console's silhouette, the whole height of it, so a touch anywhere over its band
     * picks it: its stories' floors and ceilings in pairs, rows up from her foot, the entry story's first, each story
     * as long as it is tall.
     */
    public static Box band(int[] spans, int story) {
        double first = spans[0];
        double span = Math.max(1, spans[spans.length - 1] - first);
        double length = PICTURE.x1() - PICTURE.x0();
        double from = STORIES_FROM + (STORIES_TO - STORIES_FROM) * (spans[2 * story] - first) / span;
        double to = STORIES_FROM + (STORIES_TO - STORIES_FROM) * (spans[2 * story + 1] - first) / span;
        return new Box(PICTURE.x0() + length * from, PICTURE.y0(), PICTURE.x0() + length * to, PICTURE.y1());
    }

    /**
     * Sounds a control touched, so a player hears what it did: a swatch chimes a note of a scale rising along the
     * colours, a brightness clicks higher the brighter it is, a mode and a story blip.
     */
    static void sound(Level level, BlockPos pos, Control control) {
        switch (control) {
            case Swatch swatch -> level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, VOLUME,
                LOWEST_NOTE * (float) Math.pow(2, SWATCH_NOTES[swatch.colour().ordinal()] / 12.0));
            case Glow glow -> level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, VOLUME,
                CLICK_PITCH + CLICK_STEP * glow.brightness());
            case Mode mode -> level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, VOLUME,
                1 + CLICK_STEP * mode.mode().ordinal());
            case Story story -> level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, VOLUME, 1);
            case WholeRocket whole -> level.playSound(null, pos, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.BLOCKS, VOLUME, 1.5F);
        }
    }

    /** A setting as a control changes it; a story or the whole-rocket button changes none. */
    static LedSetting changed(LedSetting setting, Control control) {
        return switch (control) {
            case Swatch swatch -> setting.withColour(swatch.colour());
            case Glow glow -> setting.withBrightness(glow.brightness());
            case Mode mode -> setting.withMode(mode.mode());
            case Story story -> setting;
            case WholeRocket whole -> setting;
        };
    }
}
