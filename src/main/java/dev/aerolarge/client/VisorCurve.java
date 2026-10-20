package dev.aerolarge.client;

import dev.aerolarge.client.HudLayout.Box;

/**
 * The curve of the pilot helmet's visor, which bends the head-up display drawn on it ({@link HelmetVisor}), on a screen
 * this big in GUI pixels, with the HUD's units this many GUI pixels each. The visor bulges out in front of the eyes,
 * so what is on it is drawn in toward the middle of the view, the more the further out it is, as a bubble shows it:
 * along the top it arches, its ends curving down and away; along the bottom it sags, its ends curving up; down the
 * sides it bows out; and toward the edges it narrows, as the visor curves away round the face. The middle stays as it
 * is, so the horizon round the crosshair stays straight and true.
 *
 * <p>Each part of the HUD is bent as its place on the screen bends it, tilting and narrowing with the visor there, and
 * curved within itself a little more than that, as though its own patch of the visor were rounder: a part is small,
 * so the curve of the whole visor alone would hardly show on it.
 */
record VisorCurve(float width, float height, float unit) {
    /** How far the visor draws a point in toward the middle at the screen's corners, as a share of the way there. */
    private static final float BULGE = 0.1F;
    /**
     * How round a part's own patch of the visor is: the radius its edges curve round at the screen's corners, in units.
     * Further in, it is flatter, and in the middle flat.
     */
    private static final float PATCH_RADIUS = 400;
    /** How many points along each edge of a part's area its bent bounds are found from. */
    private static final int BOUNDS_STEPS = 4;

    /** Where a point of a part laid flat in the given area lands on the visor, put into the given pair. */
    void bend(float x, float y, Box area, float[] out) {
        float middleX = width / 2;
        float middleY = height / 2;
        float reachSquared = middleX * middleX + middleY * middleY;
        float outX = x - middleX;
        float outY = y - middleY;
        float drawnIn = 1 - BULGE * (outX * outX + outY * outY) / reachSquared;
        // Its own patch: what is further from the part's middle drawn in further, the more as the part is further out.
        float partX = (area.left() + area.right()) / 2 - middleX;
        float partY = (area.top() + area.bottom()) / 2 - middleY;
        float offX = outX - partX;
        float offY = outY - partY;
        float patch = (offX * offX + offY * offY) / (unit * PATCH_RADIUS * (float) Math.sqrt(reachSquared));
        out[0] = middleX + outX * drawnIn - partX * patch;
        out[1] = middleY + outY * drawnIn - partY * patch;
    }

    /** The bounds of an area once bent onto the visor, found from points along its edges, which bow. */
    Box bent(Box area) {
        float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        float[] point = new float[2];
        for (int step = 0; step <= BOUNDS_STEPS; step++) {
            float across = area.left() + (area.right() - area.left()) * step / BOUNDS_STEPS;
            float down = area.top() + (area.bottom() - area.top()) * step / BOUNDS_STEPS;
            include(bounds, across, area.top(), area, point);
            include(bounds, across, area.bottom(), area, point);
            include(bounds, area.left(), down, area, point);
            include(bounds, area.right(), down, area, point);
        }
        return new Box(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    /** Grows bounds (left, top, right, bottom) to take in where a point of the area lands. */
    private void include(float[] bounds, float x, float y, Box area, float[] point) {
        bend(x, y, area, point);
        bounds[0] = Math.min(bounds[0], point[0]);
        bounds[1] = Math.min(bounds[1], point[1]);
        bounds[2] = Math.max(bounds[2], point[0]);
        bounds[3] = Math.max(bounds[3], point[1]);
    }
}
