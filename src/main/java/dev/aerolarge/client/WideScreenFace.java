package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aerolarge.block.WideScreenBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The face of a wide screen (see {@link WideScreenBlock}) that its renderer draws on, from the block holding it:
 * upright, on the front of the slim screen; tilted, on the screen plate across the blocks' diagonal, which makes the
 * face taller.
 */
final class WideScreenFace {
    /** The whole face, in blocks: as wide as the screen, as tall as its rows upright or its diagonal tilted. */
    static final double WIDTH = WideScreenBlock.COLUMNS;
    static final double UPRIGHT_HEIGHT = WideScreenBlock.UPRIGHT_HEIGHT;
    static final double TILTED_HEIGHT = WideScreenBlock.TILTED_HEIGHT;
    /** The upright face, from the middle of the block toward the way it faces, lifted just off the slim screen. */
    private static final float UPRIGHT_FACE = -0.5F + WideScreenBlock.DEPTH / 16F + 0.002F;
    /** The tilted face, from the middle of the block, lifted just off the screen plate across its diagonal. */
    private static final float TILTED_FACE = 0.03F;
    /** How far the tilted face is turned from upright, in degrees, its top edge out toward the viewer so it looks down. */
    private static final float TILT = 45;

    private WideScreenFace() {
    }

    /** How tall the face of a screen in this state is, in blocks. */
    static double height(BlockState state) {
        return state.getValue(WideScreenBlock.TILTED) ? TILTED_HEIGHT : UPRIGHT_HEIGHT;
    }

    /** Moves the pose from the block holding the screen onto its face: x to the viewer's right and y up, from its bottom left corner. */
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
    }
}
