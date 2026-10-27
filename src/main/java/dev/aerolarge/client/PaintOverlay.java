package dev.aerolarge.client;

import dev.aerolarge.airframe.Livery;

/**
 * Something a feature paints over a zeppelin's livery on this client (see {@link ZeppelinPaint}): the colour it gives
 * a block of the cover or the fabric at a spot on the hull, and how far along the hull it reaches, so the zeppelin's
 * sections there are meshed again as it comes and goes. One that is unchanged is equal to what it was, so nothing is
 * meshed again for it.
 */
interface PaintOverlay {
    /**
     * The colour a block is painted at a spot on the hull, over the livery it is painted in there, as 0xRRGGBB; less
     * than nothing where the livery shows.
     */
    int colour(Livery.Spot spot, boolean fabric, Livery under);

    /** How far aft of the nose's tip its paint starts, in blocks. */
    double from();

    /** How far aft of the nose's tip its paint ends, in blocks. */
    double to();
}
