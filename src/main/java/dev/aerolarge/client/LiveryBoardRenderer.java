package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.block.LiveryBoardBlock;
import dev.aerolarge.block.LiveryBoardBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws over a livery board (see {@link LiveryBoardBlock}): its painted swatches lit as every touch panel's tiles are
 * (see {@link TouchPanelArt#painted}), the one its zeppelin wears marked as picked and the one the crosshair rests on
 * lit up; and, while the zeppelin is being repainted, the painters' cradles riding along her (see
 * {@link PaintersCradles}). Drawn as far off as the whole zeppelin is seen.
 */
public class LiveryBoardRenderer implements BlockEntityRenderer<LiveryBoardBlockEntity> {
    /** How far off the board's face the swatches' light is drawn, in blocks. */
    private static final float LIFT = 0.004F;

    @Override
    public void render(LiveryBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = board.getLevel();
        AutopilotBlockEntity autopilot = level == null ? null : LiveryBoardBlock.painted(level, board.getBlockPos());
        swatches(board, autopilot == null ? null : autopilot.livery(), pose, buffers);
        if (autopilot != null) {
            PaintersCradles.draw(level, board.getBlockPos(), autopilot, partialTick, pose, buffers);
        }
    }

    /** The swatches' light just off the board's face, which faces the board's way, the livery worn picked (or none). */
    private static void swatches(LiveryBoardBlockEntity board, @Nullable Livery worn, PoseStack pose, MultiBufferSource buffers) {
        Direction facing = board.getBlockState().getValue(LiveryBoardBlock.FACING);
        int looked = TouchPanelArt.lookedAt(board);
        int picked = Livery.swatchPlace(Craft.ZEPPELIN, worn);
        pose.pushPose();
        // Onto the painted face from its bottom left corner, x to the viewer's right and y up, z out of the face: the
        // board stands at the back of its block, so its face is short of the block's middle by half a block less the
        // board's thickness.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5F, -0.5F, LiveryBoardBlock.THICKNESS / 16F - 0.5F);
        for (int swatch = 0; swatch < LiveryBoardBlock.SWATCH_TILES.size(); swatch++) {
            TouchPanelArt.painted(buffers, pose.last(), LiveryBoardBlock.SWATCH_TILES.get(swatch), TouchPanelArt.Look.of(swatch, looked, picked, true),
                LIFT);
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(LiveryBoardBlockEntity board) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }

    /** The whole zeppelin's box, which the cradles ride along. */
    @Override
    public AABB getRenderBoundingBox(LiveryBoardBlockEntity board) {
        return PaintersCradles.box(board.getLevel(), board.getBlockPos());
    }
}
