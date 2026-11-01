package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.block.SkywritingBoardBlock;
import dev.skyliners.block.SkywritingBoardBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;

/**
 * Lights the skywriting board's painted buttons (see {@link SkywritingBoardBlock}) as every touch panel's tiles are lit
 * (see {@link TouchPanelArt#painted}): the button for the shape its zeppelin is writing marked as picked, so whoever
 * looks knows from the picture alone which shape she is flying; the one the crosshair rests on lit up; and all of them
 * dimmed while the server's switch leaves the board doing nothing.
 */
public class SkywritingBoardRenderer implements BlockEntityRenderer<SkywritingBoardBlockEntity> {
    /** How far off the board's face the buttons' light is drawn, in blocks. */
    private static final float LIFT = 0.003F;

    @Override
    public void render(SkywritingBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!Sight.ahead(pose.last())) {
            return;
        }
        Direction facing = board.getBlockState().getValue(SkywritingBoardBlock.FACING);
        int looked = TouchPanelArt.lookedAt(board);
        int writing = board.shape() == null ? -1 : SkywritingBoardBlock.BUTTONS.indexOf(board.shape());
        boolean works = SkylinersConfig.SKYWRITING.get();
        pose.pushPose();
        // Onto the board's face from its bottom left corner as whoever reads it sees it: x to their right, y up, z
        // toward them.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5F, -0.5F, SkywritingBoardBlock.THICKNESS / 16F - 0.5F);
        for (int button = 0; button < SkywritingBoardBlock.BUTTON_TILES.size(); button++) {
            TouchPanelArt.painted(buffers, pose.last(), SkywritingBoardBlock.BUTTON_TILES.get(button), TouchPanelArt.Look.of(button, looked, writing, works),
                LIFT);
        }
        pose.popPose();
    }
}
