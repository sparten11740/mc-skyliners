package dev.skyliners.airframe;

import java.util.EnumMap;
import java.util.Map;

/**
 * A zeppelin's outline in blocks, as seen from outside her, at her size: how far round her axis the hull reaches at
 * each station back from the nose's tip, and how far her four fins reach, for drawing her from afar without her blocks
 * (see the client's {@code GreatShadow}). Her stations run from the nose's tip, her axis stands over the bottom of her
 * wheels, both as {@link ZeppelinLayout} lays her out. Both reaches are kept in a table a little apart along her and
 * read between its entries, as they are asked for many thousands of times a second.
 */
public final class ZeppelinSilhouette {
    /** How far apart along her the table's entries are, in blocks. */
    private static final double STEP = 0.5;
    private static final Map<ZeppelinSize, ZeppelinSilhouette> OF = new EnumMap<>(ZeppelinSize.class);

    private final ZeppelinSize size;
    private final double[] hull;
    private final double[] fin;

    private ZeppelinSilhouette(ZeppelinSize size) {
        this.size = size;
        int entries = (int) Math.ceil(size.length() / STEP) + 2;
        this.hull = new double[entries];
        this.fin = new double[entries];
        for (int entry = 0; entry < entries; entry++) {
            hull[entry] = size.radiusAt(entry * STEP);
            fin[entry] = ZeppelinShape.finReach(entry * STEP / size.scale()) * size.scale();
        }
    }

    /** The outline of a zeppelin of a size, worked out once. */
    public static synchronized ZeppelinSilhouette of(ZeppelinSize size) {
        return OF.computeIfAbsent(size, ZeppelinSilhouette::new);
    }

    /** How long the hull is, nose to stern, in blocks. */
    public double length() {
        return size.length();
    }

    /** How high her axis stands over the bottom of her wheels, in blocks. */
    public double axis() {
        return size.axis();
    }

    /** How far round her axis she reaches at most, her fins' tips included, in blocks. */
    public double reach() {
        return (ZeppelinShape.RADIUS + ZeppelinShape.FIN_SPAN) * size.scale();
    }

    /** The hull's radius so many blocks back from the nose's tip, in blocks; 0 past either end. */
    public double hull(double aft) {
        return between(hull, aft);
    }

    /**
     * How far out from her axis each of her four fins reaches so many blocks back from the nose's tip, measured along
     * the fin, in blocks; 0 where there is no fin. They stand in a cross: up, down and out to either side.
     */
    public double fin(double aft) {
        return between(fin, aft);
    }

    /** A table's reading so far along her, between the entries either side; 0 past either end. */
    private static double between(double[] table, double aft) {
        double at = aft / STEP;
        if (at < 0 || at >= table.length - 1) {
            return 0;
        }
        int entry = (int) at;
        double share = at - entry;
        return table[entry] * (1 - share) + table[entry + 1] * share;
    }
}
