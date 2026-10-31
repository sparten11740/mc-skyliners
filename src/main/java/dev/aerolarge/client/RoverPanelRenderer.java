package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.RoverPanelBlock;
import dev.aerolarge.block.RoverPanelBlockEntity;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.joml.Matrix4f;

/**
 * Draws the rover panel's screen (see {@link RoverPanelBlock}) over its face: the lamp across its top in the colour of
 * how the bay stands, blinking while the door or the winch works, and its three picture buttons under it, each in a
 * colour of its own with its picture in white: blue to send the rover down, green to call it home, violet for a remote.
 * A button that can do nothing now, the rover being out or in or the bay busy, is dimmed; the one the player's
 * crosshair rests on lights up, a bold white frame round it and its colour lightened, as on the lift's picture board,
 * and the game's own outline round the block is left off it. Players who cannot read go by the pictures and colours.
 */
public class RoverPanelRenderer implements BlockEntityRenderer<RoverPanelBlockEntity> {
    /** The face, from the middle of the block toward the way it looks, lifted just off the panel's front. */
    private static final float FACE = -0.5F + RoverPanelBlock.THICKNESS / 16F + 0.002F;
    /** The gap round each button and the lamp, and the bright frame round the button looked at, in blocks. */
    private static final double GAP = 0.02;
    private static final double LOOKED_FRAME = 0.03;
    /** How far the button looked at is lightened toward white, and how much of its colour a dimmed one keeps. */
    private static final double LOOKED_LIGHTER = 0.3;
    private static final double DIMMED = 0.35;
    /** How far out each way from its middle a button's picture reaches, as a share of the button's width. */
    private static final double PICTURE = 0.4;
    /** How often the lamp blinks while the bay works, in ticks a blink. */
    private static final int BLINK = 8;
    /** Lifts each layer off the one below it. */
    private static final float LAYER = 0.001F;
    private static final int CASING = 0xFF15171B;
    private static final int WHITE = 0xFFF2F6FB;
    private static final Map<RoverPanelBlock.Status, Integer> LAMPS = Map.of(
        RoverPanelBlock.Status.READY, 0xFF40E060, RoverPanelBlock.Status.BUSY, 0xFFFFB020,
        RoverPanelBlock.Status.OUT, 0xFF4090FF, RoverPanelBlock.Status.REFUSED, 0xFFE83030);
    private static final Map<RoverPanelBlock.Button, Integer> COLOURS = Map.of(
        RoverPanelBlock.Button.DEPLOY, 0xFF2F6FC0, RoverPanelBlock.Button.HOME, 0xFF2E9A4A, RoverPanelBlock.Button.REMOTE, 0xFF7A4FB0);

    @Override
    public void render(RoverPanelBlockEntity panel, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = panel.getBlockState();
        if (!(state.getBlock() instanceof RoverPanelBlock) || panel.getLevel() == null) {
            return;
        }
        RoverPanelBlock.Status status = state.getValue(RoverPanelBlock.STATUS);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(RoverPanelBlock.FACING).toYRot()));
        pose.translate(-0.5, -0.5, FACE);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, 0, 0, 1, 1, 0, CASING);
        boolean blinkedOff = status == RoverPanelBlock.Status.BUSY && panel.getLevel().getGameTime() / BLINK % 2 == 1;
        RoverPanelBlock.Area lamp = RoverPanelBlock.lamp();
        ScreenDrawing.rect(shapes, matrix, lamp.left() + GAP, lamp.bottom() + GAP, lamp.right() - GAP, lamp.top() - GAP, LAYER,
            blinkedOff ? dimmed(LAMPS.get(status)) : LAMPS.get(status));
        RoverPanelBlock.Button looked = lookedAt(panel);
        for (RoverPanelBlock.Button button : RoverPanelBlock.Button.values()) {
            button(shapes, matrix, RoverPanelBlock.area(button), colour(button, status, looked), button == looked);
        }
        for (RoverPanelBlock.Button button : RoverPanelBlock.Button.values()) {
            RoverPanelBlock.Area area = RoverPanelBlock.area(button);
            ResourceLocation picture = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID,
                "textures/gui/rover/" + button.name().toLowerCase(java.util.Locale.ROOT) + ".png");
            ScreenDrawing.picture(buffers, matrix, picture, (area.left() + area.right()) / 2, (area.bottom() + area.top()) / 2,
                (area.right() - area.left()) * PICTURE, 4 * LAYER, WHITE);
        }
        pose.popPose();
    }

    /** A button's cell, and round it a bold white frame while it is looked at. */
    private static void button(VertexConsumer shapes, Matrix4f matrix, RoverPanelBlock.Area area, int colour, boolean looked) {
        if (looked) {
            ScreenDrawing.rect(shapes, matrix, area.left() + GAP - LOOKED_FRAME, area.bottom() + GAP - LOOKED_FRAME, area.right() - GAP + LOOKED_FRAME,
                area.top() - GAP + LOOKED_FRAME, 2 * LAYER, WHITE);
        }
        ScreenDrawing.rect(shapes, matrix, area.left() + GAP, area.bottom() + GAP, area.right() - GAP, area.top() - GAP, 3 * LAYER, colour);
    }

    /** A button's colour: lightened while looked at, dimmed while it can do nothing, its own otherwise. */
    private static int colour(RoverPanelBlock.Button button, RoverPanelBlock.Status status, @Nullable RoverPanelBlock.Button looked) {
        int colour = COLOURS.get(button);
        if (button == looked) {
            return lightened(colour);
        }
        return works(button, status) ? colour : dimmed(colour);
    }

    /** Whether a button does anything as the bay stands: sending the rover down with it home, calling it home with it out. */
    private static boolean works(RoverPanelBlock.Button button, RoverPanelBlock.Status status) {
        return switch (button) {
            case DEPLOY -> status == RoverPanelBlock.Status.READY || status == RoverPanelBlock.Status.REFUSED;
            case HOME -> status == RoverPanelBlock.Status.OUT;
            case REMOTE -> true;
        };
    }

    /** The button the player's crosshair rests on, on this panel's face; null for none. */
    @Nullable
    private static RoverPanelBlock.Button lookedAt(RoverPanelBlockEntity panel) {
        Level level = panel.getLevel();
        if (level == null || !(Minecraft.getInstance().hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
            || !hit.getBlockPos().equals(panel.getBlockPos())) {
            return null;
        }
        Direction facing = panel.getBlockState().getValue(RoverPanelBlock.FACING);
        return hit.getDirection() == facing ? RoverPanelBlock.button(facing, hit.getLocation().subtract(Vec3.atCenterOf(hit.getBlockPos()))) : null;
    }

    /** No outline round a rover panel's block: the button looked at lights up on the panel instead. */
    public static void hideOutline(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(event.getTarget().getBlockPos()).getBlock() instanceof RoverPanelBlock) {
            event.setCanceled(true);
        }
    }

    private static int lightened(int argb) {
        return 0xFF000000 | mix(argb >> 16 & 0xFF, 255, LOOKED_LIGHTER) << 16 | mix(argb >> 8 & 0xFF, 255, LOOKED_LIGHTER) << 8
            | mix(argb & 0xFF, 255, LOOKED_LIGHTER);
    }

    private static int dimmed(int argb) {
        return 0xFF000000 | mix(0, argb >> 16 & 0xFF, DIMMED) << 16 | mix(0, argb >> 8 & 0xFF, DIMMED) << 8 | mix(0, argb & 0xFF, DIMMED);
    }

    /** A channel so far from one value toward another. */
    private static int mix(int from, int to, double share) {
        return (int) Math.round(from + (to - from) * share);
    }
}
