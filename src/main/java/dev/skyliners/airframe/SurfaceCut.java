package dev.skyliners.airframe;

import dev.skyliners.block.ShellGeometry;

/**
 * A curved hull's skin cut block by block in the snapped style (see {@link ShellGeometry#snapped}), as the zeppelin's
 * cover and the rocket's skin are: from where a block's eight corners lie against the hull's surface, inside, on or
 * outside it, the shape the block is cut to; where no snapped shape fits those corners, a cut along the surface through
 * the block's middle. A hull gives its surface as the depth of a point under it and the way out of it there, in blocks
 * in its layout's own frame.
 */
public final class SurfaceCut {
    private SurfaceCut() {
    }

    /** A hull's surface, in blocks in its layout's frame. */
    public interface Surface {
        /** How far a point lies outside the surface, in blocks, negative inside. */
        double depth(double x, double y, double z);

        /** The way out of the surface near a point, a unit vector, as its x, y and z. */
        double[] outward(double x, double y, double z);
    }

    /**
     * The shape the surface cuts a block to, from the block's lowest corner, or {@link ShellGeometry#NONE} for a block
     * wholly inside or outside it. Its corners (corner {@code x + 2y + 4z} of the block, as {@link ShellGeometry} counts
     * them) lie inside, on or outside the surface; a block with all of them inside is the hull's open space.
     */
    public static int shape(Surface surface, double x0, double y0, double z0) {
        int[] corners = new int[8];
        boolean within = false;
        boolean inside = true;
        for (int corner = 0; corner < 8; corner++) {
            corners[corner] = corner(surface, x0 + (corner & 1), y0 + (corner >> 1 & 1), z0 + (corner >> 2 & 1));
            within |= corners[corner] != ShellGeometry.OUTSIDE;
            inside &= corners[corner] == ShellGeometry.INSIDE;
        }
        if (!within || inside) {
            return ShellGeometry.NONE;
        }
        int shape = ShellGeometry.snapped(corners);
        if (shape != ShellGeometry.UNMATCHED) {
            return shape;
        }
        double[] out = surface.outward(x0 + 0.5, y0 + 0.5, z0 + 0.5);
        double depth = surface.depth(x0 + 0.5, y0 + 0.5, z0 + 0.5);
        return ShellGeometry.cut(out[0], out[1], out[2], 0.5 - out[0] * depth, 0.5 - out[1] * depth, 0.5 - out[2] * depth);
    }

    /** Where a block's corner lies against the surface. */
    private static int corner(Surface surface, double x, double y, double z) {
        double depth = surface.depth(x, y, z);
        if (depth < -ShellGeometry.ON_SURFACE) {
            return ShellGeometry.INSIDE;
        }
        return depth > ShellGeometry.ON_SURFACE ? ShellGeometry.OUTSIDE : ShellGeometry.ON;
    }
}
