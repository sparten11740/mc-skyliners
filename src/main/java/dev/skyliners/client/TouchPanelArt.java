package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.block.InstrumentPanelBlock;
import dev.skyliners.block.TouchPanel;
import dev.skyliners.block.TouchPanelBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.joml.Matrix4f;

/**
 * How every touch panel draws its tiles (see {@link TouchPanel}), so they all answer a player the same way, with no
 * frames: the tile under the crosshair brightens and stands out a hair larger; the tile that is picked now (the floor
 * you are on, the livery worn, the camera shown) has a white border round the inside of its edge, flush with it, so
 * any number of tiles side by side can be picked and each still be told; a tile that can do nothing now, or is not the
 * current one, is dimmed. The game's own outline is left off a touch panel's blocks, the tile looked at
 * lighting up in its place. Each panel lays out its own tiles and chooses their colours and pictures; one whose
 * buttons are painted on its block's model has them lit alike over the paint (see {@link #painted}).
 */
public final class TouchPanelArt {
    /**
     * The tile looked at: how far its colour is brightened toward white, as a share, and how far out it stands on every
     * side, in the face's units.
     */
    private static final double LOOKED_LIGHTER = 0.25;
    private static final double LOOKED_GROW = 0.008;
    /**
     * The border of the tile picked now: how wide it is and the dark line just inside it, which keeps it apart from a
     * tile or picture as pale as it is, each a share of the tile's shorter side; and how far it is lifted over the
     * tile's picture, in the face's units.
     */
    private static final double BORDER_WIDTH = 0.05;
    private static final double BORDER_LINE = 0.02;
    private static final float BORDER_LIFT = 0.012F;
    /** How far the copy of the border laid on the tile's cell is lifted from the layer given, just over the cell. */
    private static final float BORDER_ON_CELL = 0.0015F;
    private static final int BORDER_DARK = 0xFF0B0D10;
    /** How much of its colour a dimmed tile keeps. */
    private static final double DIMMED = 0.4;
    /** Lifts each layer of a tile off the one under it. */
    private static final float LAYER = 0.001F;
    private static final int WHITE = 0xFFF2F6FB;
    private static final int BLACK = 0xFF000000;

    /**
     * How a tile stands: looked at under the crosshair; picked now; and lit, in its full colour, or dimmed as one that
     * can do nothing now or is not the current one.
     */
    public record Look(boolean looked, boolean selected, boolean lit) {
        /** A tile's look by its place, given the tile looked at and the one picked now (-1 for none). */
        public static Look of(int tile, int looked, int selected, boolean lit) {
            return new Look(tile == looked, tile == selected, lit);
        }
    }

    private TouchPanelArt() {
    }

    /**
     * A tile's cell in its colour as its look shows it, standing out a hair while looked at, and, while picked, the
     * border round the inside of its edge, over whatever picture is drawn on it. The cell is drawn just over the layer
     * given.
     */
    public static void tile(VertexConsumer shapes, Matrix4f matrix, TouchPanel.Tile tile, int colour, Look look, float z) {
        TouchPanel.Tile cell = look.looked() ? tile.inset(-LOOKED_GROW) : tile;
        ScreenDrawing.rect(shapes, matrix, cell.left(), cell.bottom(), cell.right(), cell.top(), z + LAYER, colour(colour, look));
        if (look.selected()) {
            border(shapes, matrix, cell, z);
        }
    }

    /**
     * The border of a tile picked now, over its picture. Its white is laid twice: lifted, over the picture, and right on
     * the cell, which from a low angle shows past the lifted one, so no sliver of the cell is ever left outside it.
     */
    private static void border(VertexConsumer shapes, Matrix4f matrix, TouchPanel.Tile cell, float z) {
        for (TouchPanel.Tile side : borderSides(cell)) {
            ScreenDrawing.rect(shapes, matrix, side.left(), side.bottom(), side.right(), side.top(), z + BORDER_ON_CELL, WHITE);
            ScreenDrawing.rect(shapes, matrix, side.left(), side.bottom(), side.right(), side.top(), z + BORDER_LIFT + LAYER, WHITE);
        }
        for (TouchPanel.Tile side : borderLine(cell)) {
            ScreenDrawing.rect(shapes, matrix, side.left(), side.bottom(), side.right(), side.top(), z + BORDER_LIFT, BORDER_DARK);
        }
    }

    /** The picked border's four sides, the outer edge of each on the cell's own. */
    private static TouchPanel.Tile[] borderSides(TouchPanel.Tile cell) {
        return sides(cell, BORDER_WIDTH * cell.side());
    }

    /** The four sides of the dark line just inside a picked border. */
    private static TouchPanel.Tile[] borderLine(TouchPanel.Tile cell) {
        return sides(cell.inset(BORDER_WIDTH * cell.side()), BORDER_LINE * cell.side());
    }

    /** The four sides of a frame so wide just inside the edge of an area, leaving no gap at its corners. */
    private static TouchPanel.Tile[] sides(TouchPanel.Tile area, double width) {
        TouchPanel.Tile hole = area.inset(width);
        return new TouchPanel.Tile[]{
            new TouchPanel.Tile(area.left(), area.bottom(), area.right(), hole.bottom()),
            new TouchPanel.Tile(area.left(), hole.top(), area.right(), area.top()),
            new TouchPanel.Tile(area.left(), hole.bottom(), hole.left(), hole.top()),
            new TouchPanel.Tile(hole.right(), hole.bottom(), area.right(), hole.top())};
    }

    /** A tile's colour as its look shows it: brightened while looked at, its own while lit, dimmed otherwise. */
    public static int colour(int colour, Look look) {
        if (look.looked()) {
            return mix(colour, WHITE, LOOKED_LIGHTER);
        }
        return look.lit() ? colour : mix(0xFF000000, colour, DIMMED);
    }

    /**
     * The tile of a panel the player's crosshair rests on, as touching it there would pick it, the look turned into the
     * panel's frame as the server turns it; -1 while it rests on none of the panel drawn by this block entity.
     */
    public static int lookedAt(BlockEntity panel) {
        Level level = panel.getLevel();
        Minecraft minecraft = Minecraft.getInstance();
        if (level == null || minecraft.player == null || !(minecraft.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) {
            return -1;
        }
        BlockPos at = hit.getBlockPos();
        BlockState state = level.getBlockState(at);
        if (!(state.getBlock() instanceof TouchPanelBlock touchPanel) || !touchPanel.panelOrigin(state, at).equals(panel.getBlockPos())) {
            return -1;
        }
        return touchPanel.tileAt(level, state, at, hit, SableClient.turnToPlot(at, minecraft.player.getLookAngle()));
    }

    /**
     * No outline round a touch panel's blocks, as the tile looked at lights up on the panel instead, nor round a flight
     * deck's instrument panels, its casing and its flight displays, which only show things and nothing can be done to.
     */
    public static void hideOutline(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Block block = level.getBlockState(event.getTarget().getBlockPos()).getBlock();
        if (block instanceof TouchPanelBlock || block instanceof InstrumentPanelBlock) {
            event.setCanceled(true);
        }
    }

    /**
     * Lights a tile painted on a block's model as {@link #tile} lights one it draws, with flat translucent quads over
     * the paint: a white wash over it, reaching a hair past its edges, while looked at; the white border round the
     * inside of its edge while picked; a dark wash over it while it can do nothing now. Nothing for a tile that is none
     * of these. The pose
     * is on the painted face, x to the viewer's right and y up, in the tile's units, the quads at the depth given, just
     * off the paint.
     */
    public static void painted(MultiBufferSource buffers, PoseStack.Pose pose, TouchPanel.Tile tile, Look look, float z) {
        if (!look.looked() && !look.selected() && look.lit()) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(FlatQuads.WHITE));
        TouchPanel.Tile cell = look.looked() ? tile.inset(-LOOKED_GROW) : tile;
        if (look.looked()) {
            paintedQuad(consumer, pose, cell, z + LAYER, wash(WHITE, LOOKED_LIGHTER));
        } else if (!look.lit()) {
            paintedQuad(consumer, pose, cell, z + LAYER, wash(BLACK, 1 - DIMMED));
        }
        if (look.selected()) {
            paintedBorder(consumer, pose, cell, z);
        }
    }

    /** The white border round the inside of a painted tile picked now, as {@link #tile} lays it on a drawn one. */
    private static void paintedBorder(VertexConsumer consumer, PoseStack.Pose pose, TouchPanel.Tile cell, float z) {
        for (TouchPanel.Tile side : borderSides(cell)) {
            paintedQuad(consumer, pose, side, z + 3 * LAYER, WHITE);
        }
        for (TouchPanel.Tile side : borderLine(cell)) {
            paintedQuad(consumer, pose, side, z + 2 * LAYER, BORDER_DARK);
        }
    }

    /**
     * A flat quad over a painted face, lit at full brightness and facing up in the entity shader's light, which leaves
     * it its full colour whichever way the face looks.
     */
    private static void paintedQuad(VertexConsumer consumer, PoseStack.Pose pose, TouchPanel.Tile area, float z, int colour) {
        float x0 = (float) area.left();
        float y0 = (float) area.bottom();
        float x1 = (float) area.right();
        float y1 = (float) area.top();
        FlatQuads.quad(consumer, pose, LightTexture.FULL_BRIGHT, colour, 0, 1, 0, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z);
    }

    /**
     * A colour as a translucent wash laid over others, so much of it showing: laid over a colour, the colour so far
     * toward it, as {@link #mix} would make it.
     */
    private static int wash(int colour, double share) {
        return (int) Math.round(share * 0xFF) << 24 | colour & 0xFFFFFF;
    }

    /** A colour so far from one to another, a share of the way, opaque. */
    private static int mix(int from, int to, double share) {
        int colour = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int a = from >> shift & 0xFF;
            int b = to >> shift & 0xFF;
            colour |= (int) Math.round(a + (b - a) * share) << shift;
        }
        return colour;
    }
}
