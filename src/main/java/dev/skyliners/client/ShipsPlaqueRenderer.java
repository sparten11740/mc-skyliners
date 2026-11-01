package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.block.ShipsPlaqueBlock;
import dev.skyliners.block.ShipsPlaqueBlockEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/**
 * Draws what a ship's plaque is engraved with once she is christened (see {@link ShipsPlaqueBlock}): her crest at its
 * left, small, as the banner it was made from; and beside it her name, or, christened without one, who christened
 * her, in letters cut dark into the brass, as big as they fit.
 */
public class ShipsPlaqueRenderer implements BlockEntityRenderer<ShipsPlaqueBlockEntity> {
    /** How far off the plaque's face its engraving is drawn, in blocks, so it never flickers into the brass. */
    private static final float LIFT = 0.002F;
    /** The plaque's face, from its block's middle, in sixteenths: its left and its right. */
    private static final float LEFT = -6;
    private static final float RIGHT = 6;
    /** The crest's size on it, in sixteenths: half as wide as it is tall, as a banner is. */
    private static final float CREST_HEIGHT = 7;
    /** The gap between the crest and the name, and the tallest the letters are, in sixteenths. */
    private static final float GAP = 1;
    private static final float LETTERS = 2.5F;
    private static final int ENGRAVED = 0xFF3B2A12;

    @Override
    public void render(ShipsPlaqueBlockEntity plaque, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!plaque.christened()) {
            return;
        }
        if (!Sight.ahead(pose.last())) {
            return;
        }
        Direction facing = plaque.getBlockState().getValue(ShipsPlaqueBlock.FACING);
        pose.pushPose();
        // Onto the face, x to the viewer's right and y up, z out of it: the plaque stands at the back of its block, so
        // its face is short of the block's middle by half a block less its thickness.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, 0, ShipsPlaqueBlock.THICKNESS / 16F - 0.5F + LIFT);
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        float textLeft = LEFT + GAP;
        if (plaque.hasCrest()) {
            float crestRight = LEFT + GAP + CREST_HEIGHT / 2;
            BannerQuads.flat(pose.last(), buffers, plaque.crestBase(), plaque.crestPatterns(), LEFT + GAP, CREST_HEIGHT / 2, crestRight, -CREST_HEIGHT / 2, light);
            textLeft = crestRight + GAP;
        }
        engrave(plaque, pose, buffers, textLeft, light);
        pose.popPose();
    }

    /**
     * Her name, or who christened her, across the rest of the face, as big as it fits: on one line, or broken in two at
     * the space nearest its middle where that makes it bigger; its middle on the face's.
     */
    private static void engrave(ShipsPlaqueBlockEntity plaque, PoseStack pose, MultiBufferSource buffers, float left, int light) {
        Font font = Minecraft.getInstance().font;
        String text = plaque.name().isEmpty() ? Component.translatable("block.skyliners.ships_plaque.christened_by", plaque.by()).getString()
            : plaque.name();
        float room = RIGHT - GAP - left;
        List<String> lines = List.of(text);
        float scale = scale(font, lines, room);
        int space = text.lastIndexOf(' ', text.length() / 2);
        int after = text.indexOf(' ', text.length() / 2);
        int split = after < 0 || space >= 0 && text.length() / 2 - space <= after - text.length() / 2 ? space : after;
        List<String> broken = split <= 0 ? lines : List.of(text.substring(0, split), text.substring(split + 1));
        if (scale(font, broken, room) > scale) {
            lines = broken;
            scale = scale(font, broken, room);
        }
        for (int line = 0; line < lines.size(); line++) {
            String words = lines.get(line);
            pose.pushPose();
            pose.translate(left + (room - font.width(words) * scale) / 2, font.lineHeight * scale * (lines.size() / 2F - line), 0);
            pose.scale(scale, -scale, scale);
            font.drawInBatch(words, 0, 0, ENGRAVED, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
            pose.popPose();
        }
    }

    /** How big lines of letters can be to fit across so much room and down the face, in sixteenths to a font pixel. */
    private static float scale(Font font, List<String> lines, float room) {
        int widest = lines.stream().mapToInt(font::width).max().orElse(1);
        return Math.min(room / Math.max(1, widest), Math.min(LETTERS, CREST_HEIGHT / lines.size()) / font.lineHeight);
    }
}
