package dev.aerolarge.block;

import dev.aerolarge.airframe.CameraSpot;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The buttons along the bottom left of the camera screens (see {@link CameraScreen}), one for each of its airframe's
 * outside cameras in a row, each a little airliner or zeppelin seen from the side with a dot where its camera is; the
 * one the display shows is the one picked. Touching one shows its camera. They are a touch panel's tiles (see {@link
 * TouchPanel}) on the display's face, in blocks from its bottom left corner, the cameras in their airframe's order: the
 * server finds the button touched by the same layout its renderer draws them by, and the one looked at lights up as
 * every touch panel's tiles do.
 */
public final class CameraButtons {
    /** From the face's bottom left corner to the first button's, in blocks: past the bezel, and a margin inside it. */
    private static final double INSET = 0.08;
    /** Each button's size, in blocks, and the gap between two, which a touch on picks no camera. */
    private static final double WIDTH = 0.2;
    private static final double HEIGHT = 0.09;
    private static final double GAP = 0.02;

    private CameraButtons() {
    }

    /** The buttons of an airframe with so many cameras, side by side from the left, the first camera's first. */
    public static TouchPanel.Layout layout(int cameras) {
        List<TouchPanel.Tile> buttons = new ArrayList<>();
        for (int camera = 0; camera < cameras; camera++) {
            double left = INSET + camera * (WIDTH + GAP);
            buttons.add(new TouchPanel.Tile(left, INSET, left + WIDTH, INSET + HEIGHT));
        }
        return new TouchPanel.Layout(buttons);
    }

    /**
     * The camera among these, one airframe's in order, whose button is at a place on the face, in blocks from its
     * bottom left corner; null for none.
     */
    @Nullable
    public static <T extends CameraSpot> T at(List<T> cameras, double across, double up) {
        int button = layout(cameras.size()).at(across, up);
        return button < 0 ? null : cameras.get(button);
    }
}
