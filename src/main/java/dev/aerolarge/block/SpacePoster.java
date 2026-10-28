package dev.aerolarge.block;

import java.util.Arrays;
import net.minecraft.util.StringRepresentable;

/**
 * The pictures a space poster shows (see {@link SpacePosterBlock}), each so many blocks wide and tall: a black hole,
 * its disk bent round it; the solar system, half the sun and the planets in a row; a quasar, its jets running up and
 * down it; and Earthrise, the Earth over the Moon with the rocket standing there. Kept in a block's state by name.
 */
public enum SpacePoster implements StringRepresentable {
    BLACK_HOLE("black_hole", 4, 3),
    SOLAR_SYSTEM("solar_system", 5, 2),
    QUASAR("quasar", 3, 4),
    EARTHRISE("earthrise", 4, 3);

    private final String name;
    private final int columns;
    private final int rows;

    SpacePoster(String name, int columns, int rows) {
        this.name = name;
        this.columns = columns;
        this.rows = rows;
    }

    /** How many blocks wide the poster is. */
    public int columns() {
        return columns;
    }

    /** How many blocks tall the poster is. */
    public int rows() {
        return rows;
    }

    /** How many blocks wide the widest poster is. */
    static int mostColumns() {
        return Arrays.stream(values()).mapToInt(SpacePoster::columns).max().orElse(1);
    }

    /** How many blocks tall the tallest poster is. */
    static int mostRows() {
        return Arrays.stream(values()).mapToInt(SpacePoster::rows).max().orElse(1);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
