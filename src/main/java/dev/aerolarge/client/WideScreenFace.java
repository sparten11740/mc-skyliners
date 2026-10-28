package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aerolarge.block.WideScreenBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The face of a wide screen (see {@link WideScreenBlock}) that its renderer draws on, from the block holding it:
 * upright, on the front of the slim screen; tilted, on the screen plate across the blocks' diagonal, which makes the
 * face taller. A screen more than a row tall is drawn as one a row tall at its rows' scale, its picture, words and
 * buttons all grown with it: in the pose {@link #onto} leaves, it is {@link #width} wide and {@link #height} tall.
 */
final class WideScreenFace {
    /** A row of the tilted face, in blocks: as tall as its diagonal. */
    static final double TILTED_HEIGHT = WideScreenBlock.TILTED_HEIGHT;
    /** The upright face, from the middle of the block toward the way it faces, lifted just off the slim screen. */
    private static final float UPRIGHT_FACE = -0.5F + WideScreenBlock.DEPTH / 16F + 0.002F;
    /** The tilted face, from the middle of the block, lifted just off the screen plate across its diagonal. */
    private static final float TILTED_FACE = 0.03F;
    /** How far the tilted face is turned from upright, in degrees, its top edge out toward the viewer so it looks down. */
    private static final float TILT = 45;

    private WideScreenFace() {
    }

    /** How wide the face of a screen in this state is, at its rows' scale: its columns over its rows. */
    static double width(BlockState state) {
        return (double) WideScreenBlock.columns(state) / WideScreenBlock.rows(state);
    }

    /** How tall the face of a screen in this state is, at its rows' scale: a row's height. */
    static double height(BlockState state) {
        return WideScreenBlock.rowHeight(state);
    }

    /**
     * Moves the pose from the block holding the screen onto its face: x to the viewer's right and y up, from its bottom
     * left corner, at its rows' scale.
     */
    static void onto(PoseStack pose, BlockState state) {
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(WideScreenBlock.FACING).toYRot()));
        if (state.getValue(WideScreenBlock.TILTED)) {
            // Across the diagonal through the block's middle, from the bottom edge of its back to the top edge of the
            // side it faces: the face looks down and out that way.
            pose.mulPose(Axis.XP.rotationDegrees(TILT));
            pose.translate(-0.5, -height(state) / 2, TILTED_FACE);
        } else {
            pose.translate(-0.5, -0.5, UPRIGHT_FACE);
        }
        float scale = WideScreenBlock.rows(state);
        pose.scale(scale, scale, 1);
    }
}
