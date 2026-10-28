package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.airframe.ZeppelinSize;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.block.AutopilotScreenBlockEntity.Box;
import dev.aerolarge.block.LiveryBoardBlock;
import dev.aerolarge.block.LiverySelectorBlock;
import dev.aerolarge.block.LiverySelectorBlockEntity;
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
 * nose to the right, painted in it as she would be (see {@link LiveryPreview}), on a patch of sky; the one she wears is
 * framed in gold. While a zeppelin is being repainted it draws her painters' cradles too (see {@link PaintersCradles}),
 * so it is drawn as far off as the whole ship is seen. Away from a ship its pictures are of a full-size zeppelin.
 */
public class LiverySelectorRenderer implements BlockEntityRenderer<LiverySelectorBlockEntity> {
    /** The lighter frame round the face, and the sky round a picture inside its patch, in blocks. */
    private static final double FRAME = 0.04;
    private static final double SKY_ROUND = 0.03;
    /** How far the gold frame round the worn livery reaches out past its patch of sky, in blocks. */
    private static final double GOLD_RIM = 0.025;
    /** Lifts each layer off the one below it. */
    private static final float LAYER = 0.001F;
    private static final int FRAME_WOOD = 0xFF9A6A3C;
    private static final int BOARD = 0xFF5E3B1C;
    private static final int SKY = 0xFFBFDDF5;
    private static final int GOLD = 0xFFE8B83A;

    @Override
    public void render(LiverySelectorBlockEntity selector, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = selector.getLevel();
        AutopilotBlockEntity autopilot = level == null ? null : LiveryBoardBlock.painted(level, selector.getBlockPos());
        BlockState state = selector.getBlockState();
        pose.pushPose();
        WideScreenFace.onto(pose, state);
        face(pose.last().pose(), ScreenDrawing.shapes(buffers), WideScreenFace.width(state), WideScreenFace.height(state), autopilot);
        pose.popPose();
        if (autopilot != null) {
            PaintersCradles.draw(level, selector.getBlockPos(), autopilot, partialTick, pose, buffers);
        }
    }

    /** The face, this wide and tall, from its bottom left corner: the board in its frame and the liveries on it. */
    private static void face(Matrix4f matrix, VertexConsumer shapes, double width, double height, @Nullable AutopilotBlockEntity autopilot) {
        ScreenDrawing.rect(shapes, matrix, 0, 0, width, height, 0, FRAME_WOOD);
        ScreenDrawing.rect(shapes, matrix, FRAME, FRAME, width - FRAME, height - FRAME, LAYER, BOARD);
        ZeppelinSize size = autopilot != null ? autopilot.zeppelinSize() : ZeppelinSize.FULL;
        Craft craft = autopilot != null ? autopilot.craft() : Craft.ZEPPELIN;
        Livery worn = autopilot != null ? autopilot.livery() : null;
        Livery[][] swatches = Livery.swatches(craft);
        int columns = Livery.SWATCH_COLUMNS;
        for (int k = 0; k < Livery.SWATCH_ROWS * columns; k++) {
            Livery livery = swatches[k / columns][k % columns];
            swatch(matrix, shapes, LiverySelectorBlock.cell(k / columns, k % columns, height), livery, craft, size, livery == worn);
        }
    }

    /** A livery's picture: a patch of sky, gold round it if she wears the livery, and her on it, as big as fits. */
    private static void swatch(Matrix4f matrix, VertexConsumer shapes, Box cell, Livery livery, Craft craft, ZeppelinSize size, boolean worn) {
        if (worn) {
            ScreenDrawing.rect(shapes, matrix, cell.x0() - GOLD_RIM, cell.y0() - GOLD_RIM, cell.x1() + GOLD_RIM, cell.y1() + GOLD_RIM, 2 * LAYER, GOLD);
        }
        ScreenDrawing.rect(shapes, matrix, cell.x0(), cell.y0(), cell.x1(), cell.y1(), 3 * LAYER, SKY);
        boolean rocket = craft == Craft.ROCKET;
        double aspect = rocket ? LiveryPreview.rocketAspect() : LiveryPreview.aspect(size);
        double width = Math.min(cell.x1() - cell.x0() - 2 * SKY_ROUND, (cell.y1() - cell.y0() - 2 * SKY_ROUND) * aspect);
        double height = width / aspect;
        double left = (cell.x0() + cell.x1() - width) / 2;
        double bottom = (cell.y0() + cell.y1() - height) / 2;
        for (LiveryPreview.Run run : rocket ? LiveryPreview.ofRocket(livery) : LiveryPreview.of(livery, size)) {
            ScreenDrawing.rect(shapes, matrix, left + run.x0() * width, bottom + run.y0() * height, left + run.x1() * width, bottom + run.y1() * height,
                4 * LAYER, run.colour());
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
