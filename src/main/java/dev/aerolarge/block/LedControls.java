package dev.aerolarge.block;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Where the controls of the rocket's LED strips lie on the faces that set them, as touch-panel tiles (see
 * {@link TouchPanel}), so that the server makes out what a touch landed on and a client lights up the tile under the
 * crosshair by the same tiles: on a story's panel (see {@link LedPanelBlock}) and on the cockpit's console (see
 * {@link LedConsoleBlock}). Every control is a picture or a colour, never a word: the ten colours as swatches, the four
 * brightnesses as a sun from dark to blazing, the four modes as little strips doing what they do; and on the console,
 * her silhouette laid on her side, each story a band of its own colour, a button with all of her lit for all of them,
 * and a little oval window for each darkness of her windows' glass. Each tile is known by one number, the same for a
 * touch, for the tile lit under the crosshair and for what is picked: the swatches first, then the brightnesses and the
 * modes, and on the console the whole-rocket button after them, then the windows and the stories' bands last, the entry
 * story's first. Positions are on the face drawn a row tall (see
 * {@link WideScreenBlock}), from its bottom left corner as the viewer sees it: in blocks on a panel, and in rows'
 * heights on the console, which is two rows tall.
 */
public final class LedControls {
    /** What a touch landed on. */
    public sealed interface Control permits Swatch, Glow, Mode, Story, WholeRocket, Windows {
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

    /** One of the darknesses of her windows' glass on the console, clear ({@code 0}) to darkest. */
    public record Windows(int dim) implements Control {
    }

    /** How many swatches, brightnesses and modes there are: the tiles both faces have, in that order. */
    private static final int SWATCHES = LedColour.values().length;
    private static final int GLOWS = LedStripBlock.BRIGHTEST + 1;
    private static final int MODES = LedMode.values().length;
    private static final int CONTROLS = SWATCHES + GLOWS + MODES;
    /** The console's whole-rocket button's tile, after the controls both faces have; its windows and bands follow. */
    public static final int WHOLE_ROCKET_TILE = CONTROLS;
    /** How many darknesses of her windows' glass the console has tiles for. */
    private static final int WINDOW_STEPS = OvalWindowPieceBlock.DARKEST + 1;
    /**
     * How far in from its tile each button is drawn, on the face: half the gap between two buttons, the tiles meeting
     * in the middle of it, so a touch in the gap still picks the nearer button.
     */
    public static final double INSET = 0.015;

    /**
     * The two faces the controls are on: a story's panel, a block square, the swatches in two rows of five over the
     * brightnesses and the modes; and the console, two of its rows wide to one tall, her silhouette and the
     * whole-rocket button along its top, the swatches in a row under them and the brightnesses and the modes side by
     * side at the bottom, leaving room there for her windows' tiles (see {@link #WINDOWS}). Each area is given from the
     * outer edges of the buttons drawn in it.
     */
    public enum Face {
        PANEL(1, new TouchPanel.Tile(0.07, 0.55, 0.93, 0.93), 5, new TouchPanel.Tile(0.07, 0.30, 0.93, 0.49),
            new TouchPanel.Tile(0.07, 0.06, 0.93, 0.25)),
        CONSOLE(2, new TouchPanel.Tile(0.05, 0.29, 1.95, 0.47), 10, new TouchPanel.Tile(0.05, 0.05, 0.64, 0.23),
            new TouchPanel.Tile(0.71, 0.05, 1.29, 0.23));

        /** How wide the face is, in its rows' heights. */
        private final double width;
        private final TouchPanel.Layout controls;

        /**
         * A face so wide, its swatches in rows of so many from the top left over the first area, its brightnesses side
         * by side over the second and its modes over the third, each area grown by the half gap round its buttons.
         */
        Face(double width, TouchPanel.Tile swatches, int swatchColumns, TouchPanel.Tile glows, TouchPanel.Tile modes) {
            this.width = width;
            List<TouchPanel.Tile> tiles = new ArrayList<>(TouchPanel.Layout.grid(SWATCHES, swatchColumns, swatches.inset(-INSET), true).tiles());
            tiles.addAll(TouchPanel.Layout.row(GLOWS, glows.inset(-INSET)).tiles());
            tiles.addAll(TouchPanel.Layout.row(MODES, modes.inset(-INSET)).tiles());
            this.controls = new TouchPanel.Layout(tiles);
        }

        public double width() {
            return width;
        }

        /**
         * Its swatches, brightnesses and modes as tiles: the swatches in the order of {@link LedColour}, the
         * brightnesses from off at the left, the modes in the order of {@link LedMode}.
         */
        public TouchPanel.Layout controls() {
            return controls;
        }
    }

    /** Where the console's silhouette of her lies, and the whole-rocket button beside it. */
    public static final TouchPanel.Tile PICTURE = new TouchPanel.Tile(0.05, 0.53, 1.63, 0.95);
    public static final TouchPanel.Tile WHOLE_ROCKET = new TouchPanel.Tile(1.69, 0.53, 1.95, 0.95);
    /** Where the console's tiles for her windows' glass lie, side by side at the end of its bottom row. */
    public static final TouchPanel.Tile WINDOWS = new TouchPanel.Tile(1.36, 0.05, 1.95, 0.23);
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

    /** A colour's swatch's tile. */
    public static int swatch(LedColour colour) {
        return colour.ordinal();
    }

    /** A brightness's tile. */
    public static int glow(int brightness) {
        return SWATCHES + brightness;
    }

    /** A mode's tile. */
    public static int mode(LedMode mode) {
        return SWATCHES + GLOWS + mode.ordinal();
    }

    /** The tile of a darkness of her windows' glass on the console. */
    public static int window(int dim) {
        return WHOLE_ROCKET_TILE + 1 + dim;
    }

    /** The tile of a story's band on the console, counted from the entry story up. */
    public static int story(int story) {
        return window(WINDOW_STEPS) + story;
    }

    /** The swatch, brightness or mode a tile is, on either face; null for none or for one of the console's own. */
    @Nullable
    public static Control control(int tile) {
        if (tile < 0 || tile >= CONTROLS) {
            return null;
        }
        if (tile < SWATCHES) {
            return new Swatch(LedColour.values()[tile]);
        }
        return tile < SWATCHES + GLOWS ? new Glow(tile - SWATCHES) : new Mode(LedMode.values()[tile - SWATCHES - GLOWS]);
    }

    /** The control a tile of the console is (see {@link #console}); null for none. */
    @Nullable
    public static Control onConsole(int tile) {
        if (tile == WHOLE_ROCKET_TILE) {
            return new WholeRocket();
        }
        if (tile >= story(0)) {
            return new Story(tile - story(0));
        }
        return tile > WHOLE_ROCKET_TILE ? new Windows(tile - window(0)) : control(tile);
    }

    /**
     * The console's tiles: the controls, the whole-rocket button, her windows' tiles, and the bands of so many of her
     * stories along her silhouette, laid by their floors and ceilings (see {@link #band}).
     */
    public static TouchPanel.Layout console(int[] spans, int stories) {
        List<TouchPanel.Tile> tiles = new ArrayList<>(Face.CONSOLE.controls().tiles());
        tiles.add(WHOLE_ROCKET);
        tiles.addAll(TouchPanel.Layout.row(WINDOW_STEPS, WINDOWS.inset(-INSET)).tiles());
        for (int story = 0; story < stories; story++) {
            tiles.add(band(spans, story));
        }
        return new TouchPanel.Layout(tiles);
    }

    /**
     * Where a story lies along the console's silhouette, the whole height of it, so a touch anywhere over its band
     * picks it: its stories' floors and ceilings in pairs, rows up from her foot, the entry story's first, each story
     * as long as it is tall.
     */
    private static TouchPanel.Tile band(int[] spans, int story) {
        double first = spans[0];
        double span = Math.max(1, spans[spans.length - 1] - first);
        double from = STORIES_FROM + (STORIES_TO - STORIES_FROM) * (spans[2 * story] - first) / span;
        double to = STORIES_FROM + (STORIES_TO - STORIES_FROM) * (spans[2 * story + 1] - first) / span;
        return new TouchPanel.Tile(PICTURE.left() + PICTURE.width() * from, PICTURE.bottom(), PICTURE.left() + PICTURE.width() * to,
            PICTURE.top());
    }

    /**
     * Sounds a control touched, so a player hears what it did: a swatch chimes a note of a scale rising along the
     * colours, a brightness clicks higher the brighter it is, a mode and a story blip, and a window's tile chimes lower
     * the darker it is.
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
            case Windows windows -> level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, VOLUME,
                OvalWindowPieceBlock.chimePitch(windows.dim()));
        }
    }

    /** A setting as a control changes it; a story, the whole-rocket button or a window's tile changes none. */
    static LedSetting changed(LedSetting setting, Control control) {
        return switch (control) {
            case Swatch swatch -> setting.withColour(swatch.colour());
            case Glow glow -> setting.withBrightness(glow.brightness());
            case Mode mode -> setting.withMode(mode.mode());
            case Story story -> setting;
            case WholeRocket whole -> setting;
            case Windows windows -> setting;
        };
    }
}
