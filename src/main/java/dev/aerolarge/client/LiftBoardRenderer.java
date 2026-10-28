package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.LiftBoardBlock;
import dev.aerolarge.block.LiftBoardBlockEntity;
import dev.aerolarge.block.LiftMachineBlockEntity;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Draws a lift's picture board (see {@link LiftBoardBlock}) over the whole of its face (see {@link WideScreenFace}):
 * the ship's floors stacked as she stands, the lowest at the bottom, each a cell in its story's colour with its
 * floor's icon, dimmed but for the floor the car is at, which is lit; the board's own floor framed in white; while the
 * lift is on its way, an arrow blinking by the floor it is going to; and while it stands parked for the ship's flight,
 * an amber bar across its top. Players who cannot read find their floor by its icon and colour.
 */
public class LiftBoardRenderer implements BlockEntityRenderer<LiftBoardBlockEntity> {
    /** The gap between two floors' cells and the white frame round the board's own, in blocks. */
    private static final double GAP = 0.015;
    private static final double FRAME = 0.02;
    /** How big each floor's icon is, as a share of its cell's height, and of the arrow beside it. */
    private static final double ICON = 0.38;
    private static final double ARROW = 0.22;
    /** How much of its story's colour a floor the car is not at keeps. */
    private static final double DIMMED = 0.45;
    /** How often the arrow blinks, in ticks a blink. */
    private static final int BLINK = 10;
    /** How tall the parked bar across the top is, in blocks. */
    private static final double PARKED_BAR = 0.05;
    /** Lifts each layer off the one below it. */
    private static final float LAYER = 0.001F;
    private static final int CASING = 0xFF15171B;
    private static final int WHITE = 0xFFF2F6FB;
    private static final int AMBER = 0xFFFFB020;
    private static final ResourceLocation UP = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/lift/up.png");
    private static final ResourceLocation DOWN = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/lift/down.png");

    @Override
    public void render(LiftBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = board.getBlockState();
        pose.pushPose();
        WideScreenFace.onto(pose, state);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        double width = WideScreenFace.width(state);
        double height = WideScreenFace.height(state);
        ScreenDrawing.rect(shapes, matrix, 0, 0, width, height, 0, CASING);
        LiftMachineBlockEntity lift = board.lift();
        if (lift != null && !lift.landings().isEmpty()) {
            floors(matrix, shapes, buffers, width, height, board.landing(), lift);
        }
        pose.popPose();
    }

    /**
     * The lift's floors on a face this wide and tall, the board standing at one of them: their cells first, all in the
     * shapes' buffer, then their icons, each picture taking a buffer of its own.
     */
    private static void floors(Matrix4f matrix, VertexConsumer shapes, MultiBufferSource buffers, double width, double height, int here,
                               LiftMachineBlockEntity lift) {
        List<LiftMachineBlockEntity.Landing> landings = lift.landings();
        double cell = LiftBoardBlock.cell(height, landings.size());
        double left = LiftBoardBlock.MARGIN;
        double right = width - LiftBoardBlock.MARGIN;
        for (int floor = 0; floor < landings.size(); floor++) {
            double bottom = LiftBoardBlock.MARGIN + floor * cell;
            if (floor == here) {
                ScreenDrawing.rect(shapes, matrix, left - FRAME, bottom - FRAME / 2, right + FRAME, bottom + cell + FRAME / 2, LAYER, WHITE);
            }
            int colour = landings.get(floor).colour();
            ScreenDrawing.rect(shapes, matrix, left, bottom + GAP, right, bottom + cell - GAP, 2 * LAYER, floor == lift.at() ? colour : dimmed(colour));
        }
        if (lift.phase() == LiftMachineBlockEntity.Phase.PARKED) {
            ScreenDrawing.rect(shapes, matrix, 0, height - PARKED_BAR, width, height, 4 * LAYER, AMBER);
        }
        for (int floor = 0; floor < landings.size(); floor++) {
            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/lift/" + landings.get(floor).icon() + ".png");
            ScreenDrawing.picture(buffers, matrix, icon, (left + right) / 2, LiftBoardBlock.MARGIN + (floor + 0.5) * cell, cell * ICON, 3 * LAYER, WHITE);
        }
        heading(matrix, buffers, lift, cell, right);
    }

    /** While the lift is on its way, an arrow blinking by the floor it is going to, pointing the way it goes. */
    private static void heading(Matrix4f matrix, MultiBufferSource buffers, LiftMachineBlockEntity lift, double cell, double right) {
        LiftMachineBlockEntity.Phase phase = lift.phase();
        boolean going = phase == LiftMachineBlockEntity.Phase.RIDING || phase == LiftMachineBlockEntity.Phase.FETCHING;
        long now = lift.getLevel() == null ? 0 : lift.getLevel().getGameTime();
        if (!going || now / BLINK % 2 == 1) {
            return;
        }
        int to = phase == LiftMachineBlockEntity.Phase.RIDING && lift.target() >= 0 ? lift.target() : lift.at();
        double y = LiftBoardBlock.MARGIN + (to + 0.5) * cell;
        double x = right - cell * ARROW;
        ScreenDrawing.picture(buffers, matrix, lift.goingUp() ? UP : DOWN, x, y, cell * ARROW, 5 * LAYER, WHITE);
    }

    /** A story's colour as a floor the car is not at shows it: darker. */
    private static int dimmed(int argb) {
        int red = (int) ((argb >> 16 & 0xFF) * DIMMED);
        int green = (int) ((argb >> 8 & 0xFF) * DIMMED);
        int blue = (int) ((argb & 0xFF) * DIMMED);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
