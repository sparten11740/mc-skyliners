package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.block.LiftBoardBlock;
import dev.aerolarge.block.LiftBoardBlockEntity;
import dev.aerolarge.block.LiftMachineBlockEntity;
import dev.aerolarge.block.TouchPanel;
import dev.aerolarge.block.WideScreenBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a lift's picture board (see {@link LiftBoardBlock}) over the whole of its face (see {@link WideScreenFace}):
 * the ship's floors as buttons two to a row, the lowest at the bottom, each in its story's colour with its floor's
 * icon, dimmed but for the floor the car is at, which is lit; the board's own floor framed in white; while the lift is
 * on its way, an arrow blinking on the floor it is going to; and while the ship flies, but where the car stands, in
 * place of the floors how high she flies (see {@link HeightGauge}). Players who cannot read find their floor by its
 * icon and colour. Its floors light up as every touch panel's tiles do (see {@link TouchPanelArt}): the board's own
 * floor glowing green as the one picked, and what the crosshair rests on brightened, on the board inside the car the
 * floor they would pick, on one outside the doors, as older ships have, its own floor, the lift it calls there.
 */
public class LiftBoardRenderer implements BlockEntityRenderer<LiftBoardBlockEntity> {
    /** The gap between two floors' cells, in blocks. */
    private static final double GAP = 0.015;
    /**
     * How far out each way from its middle a floor's icon reaches, as a share of its button's shorter side, and the
     * arrow in the corner of the floor the lift is going to; and how far in from that corner the arrow sits, in blocks.
     */
    private static final double ICON = 0.34;
    private static final double ARROW = 0.16;
    private static final double ARROW_INSET = 0.01;
    /** How often the arrow blinks, in ticks a blink. */
    private static final int BLINK = 10;
    /** Lifts each layer off the one below it. */
    private static final float LAYER = 0.001F;
    /**
     * How far off a board is drawn, in blocks, and how far from its block any of it can reach. Sable draws an assembled
     * ship's block entities every frame unless told otherwise: past a landing or two a board cannot be read, and with
     * every floor's icon a buffer of its own, a ship's boards together take longer than anything else she draws.
     */
    private static final int VIEW_DISTANCE = 48;
    private static final float BOARD_REACH = 6;
    /** How far back of its block's middle the camera must be to see nothing of the board: at the back of the block. */
    private static final double BEHIND_FACE = -0.5;
    /** Each floor's icon by name, so that it is not looked up anew on every board every frame. */
    private static final Map<String, ResourceLocation> ICONS = new HashMap<>();
    private static final int CASING = 0xFF15171B;
    private static final int WHITE = 0xFFF2F6FB;
    private static final ResourceLocation UP = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/lift/up.png");
    private static final ResourceLocation DOWN = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/lift/down.png");

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/lift/" + name + ".png");
    }

    /** Drawn from the front only: from behind, its casing is all there is to see. */
    @Override
    public boolean shouldRender(LiftBoardBlockEntity board, Vec3 camera) {
        Direction facing = board.getBlockState().getValue(WideScreenBlock.FACING);
        return Sight.facing(board.getBlockPos(), facing, BEHIND_FACE, camera, getViewDistance());
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public void render(LiftBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!Sight.ahead(pose.last(), BOARD_REACH)) {
            return;
        }
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
            floors(matrix, shapes, buffers, width, height, board.landing(), TouchPanelArt.lookedAt(board), lift);
        }
        pose.popPose();
    }

    /**
     * The lift's floors on a face this wide and tall, the board standing at one of them and the player looking at one
     * (or none, -1): their cells first, all in the shapes' buffer, then their icons, each picture taking a buffer of
     * its own; while the ship flies, how high she flies instead, but on the board where the car stands, for whoever is
     * in it or waits for it to pick their floor on.
     */
    private static void floors(Matrix4f matrix, VertexConsumer shapes, MultiBufferSource buffers, double width, double height, int here,
                               int looked, LiftMachineBlockEntity lift) {
        List<LiftMachineBlockEntity.Landing> landings = lift.landings();
        double left = LiftBoardBlock.MARGIN;
        double right = width - LiftBoardBlock.MARGIN;
        LiftMachineBlockEntity.Phase phase = lift.phase();
        boolean going = phase == LiftMachineBlockEntity.Phase.RIDING || phase == LiftMachineBlockEntity.Phase.FETCHING;
        boolean gaugeWanted = (going || lift.at() != here) && AerolargeClientConfig.HEIGHT_GAUGE.get();
        AutopilotBlockEntity rocket = !gaugeWanted || lift.getLevel() == null ? null : AutopilotBlockEntity.framedBy(lift.getLevel(), lift.getBlockPos(), Craft.ROCKET);
        boolean flying = rocket != null && rocket.phase() != AutopilotBlockEntity.Phase.OFF;
        if (flying) {
            double high = SableClient.toWorld(lift.getLevel(), Vec3.atCenterOf(lift.getBlockPos())).y;
            boolean boosting = rocket.rocketControls().boosting(lift.getLevel().getGameTime());
            HeightGauge.draw(matrix, shapes, buffers, new HeightGauge.Column(left, LiftBoardBlock.MARGIN, right, height - LiftBoardBlock.MARGIN),
                lift.getLevel(), high, boosting);
            return;
        }
        TouchPanel.Layout buttons = LiftBoardBlock.buttons(landings.size(), width, height);
        for (int floor = 0; floor < landings.size(); floor++) {
            TouchPanelArt.tile(shapes, matrix, buttons.get(floor).inset(GAP), landings.get(floor).colour(),
                TouchPanelArt.Look.of(floor, looked, here, floor == lift.at()), LAYER);
        }
        for (int floor = 0; floor < landings.size(); floor++) {
            TouchPanel.Tile button = buttons.get(floor);
            ResourceLocation icon = ICONS.computeIfAbsent(landings.get(floor).icon(), LiftBoardRenderer::icon);
            ScreenDrawing.picture(buffers, matrix, icon, button.middleX(), button.middleY(), button.side() * ICON, 3 * LAYER, WHITE);
        }
        heading(matrix, buffers, lift, landings.size(), width, height);
    }

    /** While the lift is on its way, an arrow blinking on the floor it is going to, pointing the way it goes. */
    private static void heading(Matrix4f matrix, MultiBufferSource buffers, LiftMachineBlockEntity lift, int floors, double width, double height) {
        LiftMachineBlockEntity.Phase phase = lift.phase();
        boolean going = phase == LiftMachineBlockEntity.Phase.RIDING || phase == LiftMachineBlockEntity.Phase.FETCHING;
        long now = lift.getLevel() == null ? 0 : lift.getLevel().getGameTime();
        if (!going || now / BLINK % 2 == 1) {
            return;
        }
        int to = phase == LiftMachineBlockEntity.Phase.RIDING && lift.target() >= 0 ? lift.target() : lift.at();
        TouchPanel.Tile button = LiftBoardBlock.buttons(floors, width, height).get(to);
        double half = button.side() * ARROW;
        double in = GAP + ARROW_INSET + half;
        ScreenDrawing.picture(buffers, matrix, lift.goingUp() ? UP : DOWN, button.right() - in, button.top() - in, half, 5 * LAYER, WHITE);
    }
}
