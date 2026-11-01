package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.skyliners.block.Lift;
import dev.skyliners.block.LiftPanelBlock;
import dev.skyliners.block.LiftPanelBlockEntity;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a lift panel's screen over its face, as the digital panel by a real lift's door: amber and green light on
 * black. Across the top, in the indicator, the floor the cabin is at in big amber figures, the name of that floor under
 * them, and an arrow beside them while the cabin is on its way up or down; under it a row for each of the lift's floors,
 * the top one first, its short name and its long name in green, and the floor the cabin was last sent to lit up in
 * amber. With no lift in reach it shows dashes. It reads the lift live, as the client has it (see {@link Lift}): beside
 * a landing, the lift nearest it; carried in a cabin, that cabin, as Create draws the panel among the cabin's blocks.
 *
 * <p>Beside a landing it is only drawn with the camera in front of it, as its face is drawn from the front only. Its
 * text fades out as it gets too small on the screen to be read, a few pixels tall, and is left out beyond that.
 */
public class LiftPanelRenderer implements BlockEntityRenderer<LiftPanelBlockEntity> {
    /** The panel's face, from the middle of its block toward the way it faces, lifted just off the panel. */
    private static final float FACE = -0.5F + LiftPanelBlock.DEPTH / 16F + 0.002F;
    private static final double HALF_WIDTH = LiftPanelBlock.HALF_WIDTH;
    private static final double HALF_HEIGHT = LiftPanelBlock.HALF_HEIGHT;
    /** The margin round the indicator and inside the rows, and the line under each row, in blocks. */
    private static final double MARGIN = 0.02;
    private static final double LINE = 0.004;
    /** Where the floor's figures sit in the indicator, and its name under them, up from the indicator's bottom. */
    private static final double FIGURE_Y = 0.17;
    private static final double NAME_Y = 0.045;
    /** Where the arrow sits across, in from the left of the glass; and where the long names start, after the short. */
    private static final double ARROW_X = 0.05;
    private static final double LONG_X = 0.13;
    /** Lifts the shapes off the face, and the text off the shapes. */
    private static final float LAYER = 0.001F;
    private static final float TEXT_LIFT = 0.003F;
    /** Text sizes, in blocks per font pixel: the floor's figures (at most), the arrow, the names (at most). */
    private static final float FIGURE = 1 / 40F;
    private static final float ARROW = 1 / 70F;
    private static final float NAME = 1 / 130F;
    /** How much of a row's height its text stands, and how tall a digit is, in font pixels. */
    private static final double ROW_TEXT = 0.5;
    private static final int DIGIT = 7;
    /** How many pixels tall the names stand on the screen while drawn whole, and by when they have faded out. */
    private static final float SHARP = 3;
    private static final float GONE = 2;
    private static final float FAINTEST = 0.1F;
    private static final String KEY = "screen.skyliners.lift_panel.";

    private static final int GLASS = 0xFF040604;
    private static final int INDICATOR = 0xFF0A0D07;
    private static final int AMBER = 0xFFFFB020;
    private static final int LIT_ROW = 0xFF3A2804;
    private static final int GREEN = 0xFF3CF07A;
    private static final int DIM_GREEN = 0xFF1F8A45;
    private static final int RULE = 0xFF0E2614;

    /** Only with the camera in front of the panel beside a landing; Create draws one in a cabin without asking. */
    @Override
    public boolean shouldRender(LiftPanelBlockEntity panel, Vec3 camera) {
        Direction facing = panel.getBlockState().getValue(LiftPanelBlock.FACING);
        return Sight.facing(panel.getBlockPos(), facing, FACE, camera, getViewDistance());
    }

    @Override
    public void render(LiftPanelBlockEntity panel, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        AbstractContraptionEntity cabin = panel.getLevel() == level ? Lift.beside(level, panel.getBlockPos()) : Lift.aboard(level, panel);
        Lift.View view = cabin != null ? Lift.view(cabin, partialTick) : null;
        Direction facing = panel.getBlockState().getValue(LiftPanelBlock.FACING);

        pose.pushPose();
        // Onto the panel's face, x to the viewer's right and y up, from its middle.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, 0, FACE);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH, HALF_HEIGHT, 0, GLASS);
        double bottom = HALF_HEIGHT - LiftPanelBlock.INDICATOR;
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH + MARGIN, bottom, HALF_WIDTH - MARGIN, HALF_HEIGHT - MARGIN, LAYER, INDICATOR);
        ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH + MARGIN, bottom, HALF_WIDTH - MARGIN, bottom + LINE, 2 * LAYER, AMBER);
        if (view != null) {
            rows(shapes, matrix, view);
        }
        // The text over it all: the figures always, the names as clear as they can still be made out from here.
        Font font = Minecraft.getInstance().font;
        indicator(font, pose, buffers, view, bottom);
        float names = view != null ? (float) Math.min(NAME * 2, rowText(view)) : NAME;
        float strength = Math.clamp((Sight.pixels(pose.last(), DIGIT * names) - GONE) / (SHARP - GONE), 0F, 1F);
        if (strength >= FAINTEST && view != null) {
            floorNames(font, pose, buffers, view, Math.round(255 * strength));
        }
        pose.popPose();
    }

    /** The row of the floor the cabin was sent to lit up, and a line under every row. */
    private static void rows(VertexConsumer shapes, Matrix4f matrix, Lift.View view) {
        double height = LiftPanelBlock.rowHeight(view.floors().size());
        for (int i = 0; i < view.floors().size(); i++) {
            double top = LiftPanelBlock.ROWS_TOP - i * height;
            if (i == view.target()) {
                ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH + MARGIN, top - height + LINE, HALF_WIDTH - MARGIN, top, LAYER, LIT_ROW);
            }
            ScreenDrawing.rect(shapes, matrix, -HALF_WIDTH + MARGIN, top - height, HALF_WIDTH - MARGIN, top - height + LINE, LAYER, RULE);
        }
    }

    /** The indicator's text: the floor the cabin is at, big, its name under it, and the arrow while it moves. */
    private static void indicator(Font font, PoseStack pose, MultiBufferSource buffers, @Nullable Lift.View view, double bottom) {
        Lift.Floor at = view != null && view.at() >= 0 ? view.floors().get(view.at()) : null;
        Component figure = Component.literal(at != null ? at.shortName() : "--");
        double room = 2 * (HALF_WIDTH - ARROW_X - MARGIN);
        float size = (float) Math.min(FIGURE, room / Math.max(1, font.width(figure)));
        ScreenDrawing.text(font, pose, buffers, figure, 0, bottom + FIGURE_Y, TEXT_LIFT, size, AMBER, ScreenDrawing.Align.CENTRE);
        Component name = at != null ? Component.literal(at.longName().toUpperCase(Locale.ROOT)) : Component.translatable(KEY + "none");
        float nameSize = (float) Math.min(NAME, 2 * (HALF_WIDTH - MARGIN) / Math.max(1, font.width(name)));
        ScreenDrawing.text(font, pose, buffers, name, 0, bottom + NAME_Y, TEXT_LIFT, nameSize, DIM_GREEN, ScreenDrawing.Align.CENTRE);
        if (view != null && view.heading() != 0) {
            Component arrow = Component.literal(view.heading() > 0 ? "▲" : "▼");
            ScreenDrawing.text(font, pose, buffers, arrow, -HALF_WIDTH + ARROW_X, bottom + FIGURE_Y, TEXT_LIFT, ARROW, GREEN, ScreenDrawing.Align.CENTRE);
        }
    }

    /** Each floor's row: its short name, and its long name after it, made smaller where it would not otherwise fit. */
    private static void floorNames(Font font, PoseStack pose, MultiBufferSource buffers, Lift.View view, int alpha) {
        double height = LiftPanelBlock.rowHeight(view.floors().size());
        float size = rowText(view);
        for (int i = 0; i < view.floors().size(); i++) {
            Lift.Floor floor = view.floors().get(i);
            int colour = i == view.target() ? AMBER : GREEN;
            double y = LiftPanelBlock.ROWS_TOP - (i + 0.5) * height + LINE / 2;
            double left = -HALF_WIDTH + 2 * MARGIN;
            ScreenDrawing.Label.of(font, Component.literal(floor.shortName()), left, y, size, colour, ScreenDrawing.Align.LEFT)
                .draw(font, pose, buffers, TEXT_LIFT, alpha);
            Component name = Component.literal(floor.longName().toUpperCase(Locale.ROOT));
            double room = HALF_WIDTH - MARGIN - (left + LONG_X);
            float nameSize = (float) Math.min(size, room / Math.max(1, font.width(name)));
            ScreenDrawing.Label.of(font, name, left + LONG_X, y, nameSize, colour, ScreenDrawing.Align.LEFT).draw(font, pose, buffers, TEXT_LIFT, alpha);
        }
    }

    /** How big the rows' text is, in blocks per font pixel: as much of a row's height as it stands, up to a most. */
    private static float rowText(Lift.View view) {
        return (float) Math.min(NAME * 2, LiftPanelBlock.rowHeight(view.floors().size()) * ROW_TEXT / DIGIT);
    }
}
