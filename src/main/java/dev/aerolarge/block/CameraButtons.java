package dev.aerolarge.block;

import dev.aerolarge.airframe.CameraSpot;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The buttons along the bottom left of the camera screens (see {@link CameraScreen}), one for each of its airframe's
 * outside cameras in a row, each a little airliner or zeppelin seen from the side with a dot where its camera is; the
 * one the display shows is lit. Touching one shows its camera. Laid out on the display's face, in blocks from its
 * bottom left corner: the server finds the button touched by the same layout its renderer draws them by.
 */
public final class CameraButtons {
    /** From the face's bottom left corner to the first button's, in blocks: past the bezel, and a margin inside it. */
    public static final double INSET = 0.08;
    /** Each button's size, in blocks, and the gap between two. */
    public static final double WIDTH = 0.2;
    public static final double HEIGHT = 0.09;
    public static final double GAP = 0.02;

    private CameraButtons() {
    }

    /** A camera's button's left edge, in blocks from the face's left edge, in the row of its airframe's cameras. */
    public static double left(CameraSpot camera) {
        return INSET + camera.alongside().indexOf(camera) * (WIDTH + GAP);
    }

    /**
     * The camera among these, one airframe's in order, whose button is at a place on the face, in blocks from its
     * bottom left corner; null for none.
     */
    @Nullable
    public static <T extends CameraSpot> T at(List<T> cameras, double across, double up) {
        if (up < INSET || up > INSET + HEIGHT) {
            return null;
        }
        for (T camera : cameras) {
            double left = left(camera);
            if (across >= left && across <= left + WIDTH) {
                return camera;
            }
        }
        return null;
    }
}
