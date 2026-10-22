package dev.aerolarge.airport;

import net.minecraft.core.Direction;

/**
 * What a runway's surface looks like at each spot, as an airport's is painted: grey asphalt between lighter shoulders,
 * white edge lines and a dashed centre line, and at either end, toward the middle from the threshold, the piano keys,
 * the runway's number (its heading in tens of degrees, the end it is painted at read by an airliner landing there) and
 * the aiming point's two bars. Everything is measured in a unit that grows with the runway's width, so the markings
 * keep their proportions at any scale. A spot is given by how far along the runway it is from its start ({@code along},
 * 0 to the length) and how far right of its centre line, as seen heading down it from the start ({@code across}).
 */
final class RunwayMarkings {
    /** What is laid at a spot of the surface. */
    enum Paint {
        ASPHALT, WHITE, SHOULDER, CENTRE_LIGHT
    }

    /** How wide the shoulders either side of the paved runway are, in blocks. */
    static final int SHOULDER = 4;
    /** How far apart the lights set into the centre line are, in blocks, and where the dashes start and stop. */
    private static final int DASH = 12;
    private static final int GAP = 8;
    /** The digits, three pixels wide and five tall, each row a string read left to right, top to bottom. */
    private static final String[][] DIGITS = {
        {"###", "#.#", "#.#", "#.#", "###"}, {".#.", "##.", ".#.", ".#.", "###"}, {"###", "..#", "###", "#..", "###"},
        {"###", "..#", "###", "..#", "###"}, {"#.#", "#.#", "###", "..#", "..#"}, {"###", "#..", "###", "..#", "###"},
        {"###", "#..", "###", "#.#", "###"}, {"###", "..#", "..#", "..#", "..#"}, {"###", "#.#", "###", "#.#", "###"},
        {"###", "#.#", "###", "..#", "###"},
    };

    private final int length;
    /** How far out from the centre line the paved runway reaches, its edge line the outermost row of it. */
    private final int paved;
    /** The markings' unit, in blocks. */
    private final int unit;
    /** The runway's number at its start and at its far end, as two digits each. */
    private final String startNumber;
    private final String endNumber;

    RunwayMarkings(int length, int halfWidth, Direction along) {
        this.length = length;
        this.paved = halfWidth - SHOULDER;
        this.unit = Math.max(1, paved / 20);
        this.startNumber = number(along);
        this.endNumber = number(along.getOpposite());
    }

    /** A runway's number heading this way: its compass heading in tens of degrees, north 36. */
    private static String number(Direction heading) {
        return switch (heading) {
            case EAST -> "09";
            case SOUTH -> "18";
            case WEST -> "27";
            default -> "36";
        };
    }

    Paint at(int along, int across) {
        if (Math.abs(across) > paved) {
            return Paint.SHOULDER;
        }
        if (Math.abs(across) == paved) {
            return Paint.WHITE;
        }
        // Each end painted alike, the far one turned round to be read landing from that side.
        boolean farHalf = along >= length / 2;
        int fromEnd = farHalf ? length - 1 - along : along;
        int right = farHalf ? -across : across;
        Paint end = endMarking(fromEnd, right, farHalf ? endNumber : startNumber);
        return end != null ? end : centreLine(along, across);
    }

    /** The markings at one end, counted from its threshold; null where there are none. */
    private Paint endMarking(int fromEnd, int right, String number) {
        int keysFrom = 2 * unit;
        int keysTo = keysFrom + 10 * unit;
        if (fromEnd >= keysFrom && fromEnd < keysTo) {
            return pianoKey(right) ? Paint.WHITE : Paint.ASPHALT;
        }
        int numberFrom = keysTo + 4 * unit;
        int numberTo = numberFrom + 10 * unit;
        if (fromEnd >= numberFrom && fromEnd < numberTo) {
            return numberPixel(number, (numberTo - 1 - fromEnd) / (2 * unit), right) ? Paint.WHITE : Paint.ASPHALT;
        }
        int aimFrom = numberTo + 16 * unit;
        if (fromEnd >= aimFrom && fromEnd < aimFrom + 12 * unit) {
            int out = Math.abs(right);
            return out >= 3 * unit && out < 5 * unit ? Paint.WHITE : Paint.ASPHALT;
        }
        return fromEnd < numberTo ? Paint.ASPHALT : null;
    }

    /** The piano keys: white stripes a unit wide a unit apart across the runway, but for a lane down the middle. */
    private boolean pianoKey(int right) {
        int out = Math.abs(right);
        return out > 2 * unit && out < paved - unit && (out / unit) % 2 == 1;
    }

    /**
     * Whether a spot is part of the runway's number: its two digits side by side, a pixel apart, each pixel a unit
     * across and two along, its top row furthest from the threshold.
     */
    private boolean numberPixel(String number, int row, int right) {
        int width = 7 * unit;
        int column = right + width / 2;
        if (column < 0 || column >= width || row < 0 || row >= 5) {
            return false;
        }
        int pixel = column / unit;
        if (pixel == 3) {
            return false;
        }
        String[] digit = DIGITS[number.charAt(pixel < 3 ? 0 : 1) - '0'];
        return digit[row].charAt(pixel < 3 ? pixel : pixel - 4) == '#';
    }

    /** The dashed centre line down the middle, with a light set into it in each gap between dashes. */
    private Paint centreLine(int along, int across) {
        if (Math.abs(across) > unit / 2) {
            return Paint.ASPHALT;
        }
        int phase = along % (DASH + GAP);
        if (phase < DASH) {
            return Paint.WHITE;
        }
        return across == 0 && phase == DASH + GAP / 2 ? Paint.CENTRE_LIGHT : Paint.ASPHALT;
    }
}
