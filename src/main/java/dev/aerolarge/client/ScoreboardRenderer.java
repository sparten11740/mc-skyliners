package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aerolarge.block.HoopBlock;
import dev.aerolarge.block.ScoreboardBlock;
import dev.aerolarge.block.ScoreboardBlockEntity;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * The scoreboard's face, lit: the sides' names along the top, each score in big amber digits under its name, a colon
 * between them. During hide and seek's countdown, one huge number fills it instead, so it reads from across the hall
 * without reading words: amber, red for the last few seconds, green at nought.
 */
public class ScoreboardRenderer implements BlockEntityRenderer<ScoreboardBlockEntity> {
    /** Just off the face, so the text never flickers into it. */
    private static final float SURFACE = 1.002F;
    /** Text sizes, in blocks per font pixel: the names, the digits, and the countdown's number. */
    private static final float NAME = 1 / 96F;
    private static final float DIGITS = 1 / 40F;
    private static final float COUNTDOWN = 1 / 14F;
    private static final int NAME_COLOUR = 0xFFE8ECF0;
    private static final int DIGIT_COLOUR = 0xFFFFB020;
    /** The countdown's last seconds, drawn red, and its nought, green: go and find them. */
    private static final int LAST_SECONDS = 3;
    private static final int LAST_COLOUR = 0xFFFF4030;
    private static final int GO_COLOUR = 0xFF40E060;

    @Override
    public void render(ScoreboardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Direction facing = board.getBlockState().getValue(ScoreboardBlock.FACING);
        Font font = Minecraft.getInstance().font;
        pose.pushPose();
        // Onto the face, x to the viewer's right and y up, from its bottom left corner.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(-0.5, -0.5, SURFACE - 0.5);
        OptionalInt countdown = board.countdown();
        if (countdown.isPresent()) {
            int number = countdown.getAsInt();
            int colour = number == 0 ? GO_COLOUR : number <= LAST_SECONDS ? LAST_COLOUR : DIGIT_COLOUR;
            text(font, pose, buffers, Component.literal(String.valueOf(number)), 0.5, 0.5, COUNTDOWN, colour);
        } else {
            text(font, pose, buffers, HoopBlock.Team.HOME.label(), 0.27, 0.8, NAME, NAME_COLOUR);
            text(font, pose, buffers, HoopBlock.Team.GUEST.label(), 0.73, 0.8, NAME, NAME_COLOUR);
            text(font, pose, buffers, Component.literal(String.valueOf(board.home())), 0.27, 0.42, DIGITS, DIGIT_COLOUR);
            text(font, pose, buffers, Component.literal(":"), 0.5, 0.42, DIGITS, DIGIT_COLOUR);
            text(font, pose, buffers, Component.literal(String.valueOf(board.guest())), 0.73, 0.42, DIGITS, DIGIT_COLOUR);
        }
        pose.popPose();
    }

    /** Text centred on the point, at the size given. */
    private static void text(Font font, PoseStack pose, MultiBufferSource buffers, Component text, double x, double y, float size, int colour) {
        ScreenDrawing.text(font, pose, buffers, text, x, y, 0, size, colour, ScreenDrawing.Align.CENTRE);
    }
}
