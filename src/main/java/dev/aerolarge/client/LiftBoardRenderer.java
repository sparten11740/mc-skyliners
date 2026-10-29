package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.block.LiftBoardBlock;
import dev.aerolarge.block.LiftBoardBlockEntity;
import dev.aerolarge.block.LiftMachineBlockEntity;
import dev.aerolarge.block.WideScreenBlock;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.joml.Matrix4f;

/**
 * Draws a lift's picture board (see {@link LiftBoardBlock}) over the whole of its face (see {@link WideScreenFace}):
 * the ship's floors as buttons two to a row, the lowest at the bottom, each in its story's colour with its floor's
 * icon, dimmed but for the floor the car is at, which is lit; the board's own floor framed in white; while the lift is
 * on its way, an arrow blinking on the floor it is going to; and while it stands parked for the ship's flight,
 * an amber bar across its top and, in place of the floors, how high she flies (see {@link HeightGauge}). Players who
 * cannot read find their floor by its icon and colour. What the player's crosshair rests on lights up, a bold white
 * frame round it and its colour lightened: on the board inside the car the floor they would pick, on one outside the
 * doors, as older ships have, its own floor, the lift it calls there; the game's own outline round the block is left
 * off it.
 */
public class LiftBoardRenderer implements BlockEntityRenderer<LiftBoardBlockEntity> {
    /** The gap between two floors' cells and the white frame round the board's own, in blocks. */
    private static final double GAP = 0.015;
    private static final double FRAME = 0.02;
    /**
     * The floor the player looks at: how thick its bright frame is, in blocks, and how far its colour is lightened
     * toward white, as a share.
     */
    private static final double LOOKED_FRAME = 0.035;
    private static final double LOOKED_LIGHTER = 0.25;
    /**
     * How far out each way from its middle a floor's icon reaches, as a share of its button's shorter side, and the
     * arrow in the corner of the floor the lift is going to; and how far in from that corner the arrow sits, in blocks.
     */
    private static final double ICON = 0.34;
    private static final double ARROW = 0.16;
    private static final double ARROW_INSET = 0.01;
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
            floors(matrix, shapes, buffers, width, height, board.landing(), lookedAt(board, lift), lift);
        }
        pose.popPose();
    }

    /**
     * The floor the player's crosshair picks out on a board, as touching it would: inside the car the floor under it,
     * outside the doors the board's own floor wherever on the board it rests; -1 while it rests on none of the board.
     */
    private static int lookedAt(LiftBoardBlockEntity board, LiftMachineBlockEntity lift) {
        Level level = board.getLevel();
        if (level == null || !(Minecraft.getInstance().hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return -1;
        }
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof LiftBoardBlock) || !WideScreenBlock.origin(state, hit.getBlockPos()).equals(board.getBlockPos())) {
            return -1;
        }
        WideScreenBlock.Touch touch = WideScreenBlock.touched(state, hit.getBlockPos(), hit, Vec3.ZERO);
        if (touch == null) {
            return -1;
        }
        if (!lift.inside(board.getBlockPos(), board.landing())) {
            return board.landing();
        }
        return LiftBoardBlock.floorAt(touch, lift.landings().size());
    }

    /** No outline round a lift board's block: the floor looked at lights up on the board instead. */
    public static void hideOutline(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(event.getTarget().getBlockPos()).getBlock() instanceof LiftBoardBlock) {
            event.setCanceled(true);
        }
    }

    /**
     * The lift's floors on a face this wide and tall, the board standing at one of them and the player looking at one
     * (or none, -1): their cells first, all in the shapes' buffer, then their icons, each picture taking a buffer of
     * its own; parked, how high she flies instead.
     */
    private static void floors(Matrix4f matrix, VertexConsumer shapes, MultiBufferSource buffers, double width, double height, int here,
                               int looked, LiftMachineBlockEntity lift) {
        List<LiftMachineBlockEntity.Landing> landings = lift.landings();
        double left = LiftBoardBlock.MARGIN;
        double right = width - LiftBoardBlock.MARGIN;
        boolean parked = lift.phase() == LiftMachineBlockEntity.Phase.PARKED;
        if (parked) {
            ScreenDrawing.rect(shapes, matrix, 0, height - PARKED_BAR, width, height, 4 * LAYER, AMBER);
        }
        if (parked && AerolargeClientConfig.HEIGHT_GAUGE.get() && lift.getLevel() != null) {
            double flying = SableClient.toWorld(lift.getLevel(), Vec3.atCenterOf(lift.getBlockPos())).y;
            HeightGauge.draw(matrix, shapes, buffers, new HeightGauge.Column(left, LiftBoardBlock.MARGIN, right, height - PARKED_BAR - GAP),
                lift.getLevel(), flying);
            return;
        }
        for (int floor = 0; floor < landings.size(); floor++) {
            LiftBoardBlock.Button button = LiftBoardBlock.button(floor, landings.size(), width, height);
            double frame = floor == looked ? LOOKED_FRAME : floor == here ? FRAME : 0;
            if (frame > 0) {
                ScreenDrawing.rect(shapes, matrix, button.left() + GAP - frame, button.bottom() + GAP - frame, button.right() - GAP + frame,
                    button.top() - GAP + frame, LAYER, WHITE);
            }
            ScreenDrawing.rect(shapes, matrix, button.left() + GAP, button.bottom() + GAP, button.right() - GAP, button.top() - GAP, 2 * LAYER,
                cellColour(landings.get(floor).colour(), floor, looked, lift));
        }
        for (int floor = 0; floor < landings.size(); floor++) {
            LiftBoardBlock.Button button = LiftBoardBlock.button(floor, landings.size(), width, height);
            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/lift/" + landings.get(floor).icon() + ".png");
            ScreenDrawing.picture(buffers, matrix, icon, (button.left() + button.right()) / 2, (button.bottom() + button.top()) / 2, side(button) * ICON,
                3 * LAYER, WHITE);
        }
        heading(matrix, buffers, lift, landings.size(), width, height);
    }

    /** A button's shorter side, in blocks, which its icon is drawn to. */
    private static double side(LiftBoardBlock.Button button) {
        return Math.min(button.right() - button.left(), button.top() - button.bottom());
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
        LiftBoardBlock.Button button = LiftBoardBlock.button(to, floors, width, height);
        double half = side(button) * ARROW;
        double in = GAP + ARROW_INSET + half;
        ScreenDrawing.picture(buffers, matrix, lift.goingUp() ? UP : DOWN, button.right() - in, button.top() - in, half, 5 * LAYER, WHITE);
    }

    /** A floor's cell's colour: lit and lightened while looked at, lit where the car is, dimmed elsewhere. */
    private static int cellColour(int colour, int floor, int looked, LiftMachineBlockEntity lift) {
        if (floor == looked) {
            return lightened(colour);
        }
        return floor == lift.at() ? colour : dimmed(colour);
    }

    /** A story's colour lightened toward white, as the floor looked at shows it. */
    private static int lightened(int argb) {
        int red = lighter(argb >> 16 & 0xFF);
        int green = lighter(argb >> 8 & 0xFF);
        int blue = lighter(argb & 0xFF);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int lighter(int channel) {
        return (int) Math.round(channel + (255 - channel) * LOOKED_LIGHTER);
    }

    /** A story's colour as a floor the car is not at shows it: darker. */
    private static int dimmed(int argb) {
        int red = (int) ((argb >> 16 & 0xFF) * DIMMED);
        int green = (int) ((argb >> 8 & 0xFF) * DIMMED);
        int blue = (int) ((argb & 0xFF) * DIMMED);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
