package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.block.RoverPanelBlock;
import dev.skyliners.block.RoverPanelBlockEntity;
import dev.skyliners.block.TouchPanel;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Draws the rover panel's screen (see {@link RoverPanelBlock}) over its face: the lamp across its top in the colour of
 * how the bay stands, blinking while the door or the winch works, and its three picture buttons under it, each in a
 * colour of its own with its picture in white: blue to send the rover down, green to call it home, violet for a remote.
 * The buttons light up as every touch panel's do (see {@link TouchPanelArt}): the one the crosshair rests on
 * brightened, and one that can do nothing now, the rover being out or in or the bay busy, dimmed. Players who cannot
 * read go by the pictures and colours.
 */
public class RoverPanelRenderer implements BlockEntityRenderer<RoverPanelBlockEntity> {
    /** The face, from the middle of the block toward the way it looks, lifted just off the panel's front. */
    private static final float FACE = -0.5F + RoverPanelBlock.THICKNESS / 16F + 0.002F;
    /** The gap round each button and the lamp, in blocks. */
    private static final double GAP = 0.02;
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
        if (!Sight.ahead(pose.last())) {
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
        boolean blinkedOff = status == RoverPanelBlock.Status.BUSY && DrawingClock.wholeTicks() / BLINK % 2 == 1;
        TouchPanelArt.tile(shapes, matrix, RoverPanelBlock.LAMP_TILE.inset(GAP), LAMPS.get(status), new TouchPanelArt.Look(false, false, !blinkedOff),
            LAYER);
        int looked = TouchPanelArt.lookedAt(panel);
        RoverPanelBlock.Button[] buttons = RoverPanelBlock.Button.values();
        for (int button = 0; button < buttons.length; button++) {
            TouchPanelArt.tile(shapes, matrix, RoverPanelBlock.BUTTONS.get(button).inset(GAP), COLOURS.get(buttons[button]),
                TouchPanelArt.Look.of(button, looked, -1, works(buttons[button], status)), LAYER);
        }
        for (int button = 0; button < buttons.length; button++) {
            TouchPanel.Tile tile = RoverPanelBlock.BUTTONS.get(button);
            ResourceLocation picture = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID,
                "textures/gui/rover/" + buttons[button].name().toLowerCase(Locale.ROOT) + ".png");
            ScreenDrawing.picture(buffers, matrix, picture, tile.middleX(), tile.middleY(), tile.width() * PICTURE, 4 * LAYER, WHITE);
        }
        pose.popPose();
    }

    /** Whether a button does anything as the bay stands: sending the rover down with it home, calling it home with it out. */
    private static boolean works(RoverPanelBlock.Button button, RoverPanelBlock.Status status) {
        return switch (button) {
            case DEPLOY -> status == RoverPanelBlock.Status.READY || status == RoverPanelBlock.Status.REFUSED;
            case HOME -> status == RoverPanelBlock.Status.OUT;
            case REMOTE -> true;
        };
    }
}
