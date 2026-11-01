package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.airframe.Livery;
import dev.skyliners.airframe.ZeppelinSize;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.block.LiveryBoardBlock;
import dev.skyliners.block.LiverySelectorBlock;
import dev.skyliners.block.LiverySelectorBlockEntity;
import dev.skyliners.block.TouchPanel;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

/**
 * Draws a livery selector (see {@link LiverySelectorBlock}) over the whole of its face (see {@link WideScreenFace}):
 * dark wood in a lighter frame, and on it the six liveries of the ship it paints, each a picture of her side on, her
 * nose to the right, painted in it as she would be (see {@link LiveryPreview}), on a patch of sky. The patches light
 * up as every touch panel's tiles do (see {@link TouchPanelArt}): the one she wears marked as picked, and the one the
 * crosshair rests on lit up. While a zeppelin is being repainted it draws her painters' cradles too (see
 * {@link PaintersCradles}), so it is drawn as far off as the whole ship is seen. Away from a ship its pictures are of
 * a full-size zeppelin.
 */
public class LiverySelectorRenderer implements BlockEntityRenderer<LiverySelectorBlockEntity> {
    /** The lighter frame round the face, and the sky round a picture inside its patch, in blocks. */
    private static final double FRAME = 0.04;
    private static final double SKY_ROUND = 0.03;
    /** Lifts each layer off the one below it. */
    private static final float LAYER = 0.001F;
    /**
     * How far off the face a picture's patch of sky is, and its picture over it, in layers: the picture well clear of
     * the patch and whatever lights the patch up round it.
     */
    private static final int SKY_LAYER = 2;
    private static final int PICTURE_LAYER = 6;
    private static final int FRAME_WOOD = 0xFF9A6A3C;
    private static final int BOARD = 0xFF5E3B1C;
    private static final int SKY = 0xFFBFDDF5;

    @Override
    public void render(LiverySelectorBlockEntity selector, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = selector.getLevel();
        AutopilotBlockEntity autopilot = level == null ? null : LiveryBoardBlock.painted(level, selector.getBlockPos());
        BlockState state = selector.getBlockState();
        pose.pushPose();
        WideScreenFace.onto(pose, state);
        face(pose.last().pose(), ScreenDrawing.shapes(buffers), state, autopilot, TouchPanelArt.lookedAt(selector));
        pose.popPose();
        if (autopilot != null) {
            PaintersCradles.draw(level, selector.getBlockPos(), autopilot, partialTick, pose, buffers);
        }
    }

    /**
     * The face of a selector in this state, from its bottom left corner: the board in its frame and the liveries on it,
     * the player looking at one of them (or none, -1).
     */
    private static void face(Matrix4f matrix, VertexConsumer shapes, BlockState state, @Nullable AutopilotBlockEntity autopilot, int looked) {
        double width = WideScreenFace.width(state);
        double height = WideScreenFace.height(state);
        ScreenDrawing.rect(shapes, matrix, 0, 0, width, height, 0, FRAME_WOOD);
        ScreenDrawing.rect(shapes, matrix, FRAME, FRAME, width - FRAME, height - FRAME, LAYER, BOARD);
        ZeppelinSize size = autopilot != null ? autopilot.zeppelinSize() : ZeppelinSize.FULL;
        Craft craft = autopilot != null ? autopilot.craft() : Craft.ZEPPELIN;
        TouchPanel.Layout pictures = LiverySelectorBlock.pictures(height);
        int worn = Livery.swatchPlace(craft, autopilot != null ? autopilot.livery() : null);
        for (int picture = 0; picture < pictures.size(); picture++) {
            TouchPanel.Tile patch = pictures.get(picture);
            TouchPanelArt.tile(shapes, matrix, patch, SKY, TouchPanelArt.Look.of(picture, looked, worn, true), SKY_LAYER * LAYER);
            preview(matrix, shapes, patch, Livery.swatch(craft, picture), craft, size);
        }
    }

    /** A livery's picture of her on its patch of sky, as big as fits. */
    private static void preview(Matrix4f matrix, VertexConsumer shapes, TouchPanel.Tile patch, Livery livery, Craft craft, ZeppelinSize size) {
        boolean rocket = craft == Craft.ROCKET;
        double aspect = rocket ? LiveryPreview.rocketAspect() : LiveryPreview.aspect(size);
        double width = Math.min(patch.width() - 2 * SKY_ROUND, (patch.height() - 2 * SKY_ROUND) * aspect);
        double height = width / aspect;
        double left = patch.middleX() - width / 2;
        double bottom = patch.middleY() - height / 2;
        for (LiveryPreview.Run run : rocket ? LiveryPreview.ofRocket(livery) : LiveryPreview.of(livery, size)) {
            ScreenDrawing.rect(shapes, matrix, left + run.x0() * width, bottom + run.y0() * height, left + run.x1() * width, bottom + run.y1() * height,
                PICTURE_LAYER * LAYER, run.colour());
        }
    }

    @Override
    public boolean shouldRenderOffScreen(LiverySelectorBlockEntity selector) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }

    /** Its own blocks, and the whole zeppelin's box, which the cradles ride along. */
    @Override
    public AABB getRenderBoundingBox(LiverySelectorBlockEntity selector) {
        AABB own = new AABB(selector.getBlockPos()).inflate(LiverySelectorBlock.SELECTOR_COLUMNS);
        return PaintersCradles.box(selector.getLevel(), selector.getBlockPos()).minmax(own);
    }
}
