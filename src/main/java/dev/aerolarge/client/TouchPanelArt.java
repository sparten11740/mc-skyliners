package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.block.TouchPanel;
import dev.aerolarge.block.TouchPanelBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.joml.Matrix4f;

/**
 * How every touch panel draws its tiles (see {@link TouchPanel}), so they all answer a player the same way, with no
 * frames: the tile under the crosshair brightens and stands out a hair larger; the tile that is picked now (the floor
 * you are on, the livery worn, the camera shown) glows softly green round its edge; a tile that can do nothing now, or
 * is not the current one, is dimmed. The game's own outline is left off a touch panel's blocks, the tile looked at
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
     * The glow round the tile picked now: how far out it reaches, in the face's units, and how strong it is at the
     * tile's edge (0 to 255), fading smoothly to nothing at its reach.
     */
    private static final double GLOW_REACH = 0.05;
    private static final int GLOW_ALPHA = 0xC0;
    /** How much of its colour a dimmed tile keeps. */
    private static final double DIMMED = 0.4;
    /** Lifts a tile's cell off its glow. */
    private static final float LAYER = 0.001F;
    private static final int WHITE = 0xFFF2F6FB;
    /** The glow round the tile picked now: a bright green, the colour of what is on, standing out on any casing. */
    private static final int PICKED = 0x3FE070;
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
     * green glow round it. The glow is drawn at the layer given, the cell just over it.
     */
    public static void tile(VertexConsumer shapes, Matrix4f matrix, TouchPanel.Tile tile, int colour, Look look, float z) {
        if (look.selected()) {
            glow(shapes, matrix, tile, z);
        }
        TouchPanel.Tile cell = look.looked() ? tile.inset(-LOOKED_GROW) : tile;
        ScreenDrawing.rect(shapes, matrix, cell.left(), cell.bottom(), cell.right(), cell.top(), z + LAYER, colour(colour, look));
    }

    /**
     * The soft green glow round a tile picked now: a band along each side, as strong as {@link #GLOW_ALPHA} at the
     * tile's edge and fading smoothly to nothing at its reach, the bands meeting on the diagonals at the corners.
     */
    public static void glow(VertexConsumer shapes, Matrix4f matrix, TouchPanel.Tile tile, float z) {
        TouchPanel.Tile out = tile.inset(-GLOW_REACH);
        int edge = GLOW_ALPHA << 24 | PICKED;
        int faded = PICKED;
        band(shapes, matrix, z, tile.left(), tile.top(), edge, tile.right(), tile.top(), edge, out.right(), out.top(), faded, out.left(), out.top(),
            faded);
        band(shapes, matrix, z, out.left(), out.bottom(), faded, out.right(), out.bottom(), faded, tile.right(), tile.bottom(), edge, tile.left(),
            tile.bottom(), edge);
        band(shapes, matrix, z, out.left(), out.bottom(), faded, tile.left(), tile.bottom(), edge, tile.left(), tile.top(), edge, out.left(),
            out.top(), faded);
        band(shapes, matrix, z, tile.right(), tile.bottom(), edge, out.right(), out.bottom(), faded, out.right(), out.top(), faded, tile.right(),
            tile.top(), edge);
    }

    /** A quad from four corners going round, each in its own colour, blended smoothly between them. */
    private static void band(VertexConsumer shapes, Matrix4f matrix, float z, double x0, double y0, int c0, double x1, double y1, int c1, double x2,
                             double y2, int c2, double x3, double y3, int c3) {
        ScreenDrawing.vertex(shapes, matrix, x0, y0, z, c0);
        ScreenDrawing.vertex(shapes, matrix, x1, y1, z, c1);
        ScreenDrawing.vertex(shapes, matrix, x2, y2, z, c2);
        ScreenDrawing.vertex(shapes, matrix, x3, y3, z, c3);
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

    /** No outline round a touch panel's blocks: the tile looked at lights up on the panel instead. */
    public static void hideOutline(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        if (level != null && level.getBlockState(event.getTarget().getBlockPos()).getBlock() instanceof TouchPanelBlock) {
            event.setCanceled(true);
        }
    }

    /**
     * Lights a tile painted on a block's model as {@link #tile} lights one it draws, with flat translucent quads over
     * the paint: a white wash over it, reaching a hair past its edges, while looked at; the soft green glow round it
     * while picked; a dark wash over it while it can do nothing now. Nothing for a tile that is none of these. The pose
     * is on the painted face, x to the viewer's right and y up, in the tile's units, the quads at the depth given, just
     * off the paint.
     */
    public static void painted(MultiBufferSource buffers, PoseStack.Pose pose, TouchPanel.Tile tile, Look look, float z) {
        if (!look.looked() && !look.selected() && look.lit()) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucent(FlatQuads.WHITE));
        if (look.selected()) {
            paintedGlow(consumer, pose, tile, z);
        }
        if (look.looked()) {
            paintedQuad(consumer, pose, tile.inset(-LOOKED_GROW), z + LAYER, wash(WHITE, LOOKED_LIGHTER));
        } else if (!look.lit()) {
            paintedQuad(consumer, pose, tile, z + LAYER, wash(BLACK, 1 - DIMMED));
        }
    }

    /**
     * The soft green glow round a painted tile picked now, as {@link #glow} lays it round a drawn one: a band along each
     * side, strongest at the tile's edge and fading smoothly out to its reach. The entity shader leaves out what is all
     * but clear, so the glow ends a little short of nothing at its very edge.
     */
    private static void paintedGlow(VertexConsumer consumer, PoseStack.Pose pose, TouchPanel.Tile tile, float z) {
        TouchPanel.Tile out = tile.inset(-GLOW_REACH);
        int edge = GLOW_ALPHA << 24 | PICKED;
        int faded = PICKED;
        paintedBand(consumer, pose, z, tile.left(), tile.top(), edge, tile.right(), tile.top(), edge, out.right(), out.top(), faded, out.left(),
            out.top(), faded);
        paintedBand(consumer, pose, z, out.left(), out.bottom(), faded, out.right(), out.bottom(), faded, tile.right(), tile.bottom(), edge,
            tile.left(), tile.bottom(), edge);
        paintedBand(consumer, pose, z, out.left(), out.bottom(), faded, tile.left(), tile.bottom(), edge, tile.left(), tile.top(), edge, out.left(),
            out.top(), faded);
        paintedBand(consumer, pose, z, tile.right(), tile.bottom(), edge, out.right(), out.bottom(), faded, out.right(), out.top(), faded,
            tile.right(), tile.top(), edge);
    }

    /**
     * A quad over a painted face from four corners going round, each in its own colour, blended between them; lit at
     * full brightness and facing up in the entity shader's light, which leaves it its full colour whichever way the face
     * looks.
     */
    private static void paintedBand(VertexConsumer consumer, PoseStack.Pose pose, float z, double x0, double y0, int c0, double x1, double y1, int c1,
                                    double x2, double y2, int c2, double x3, double y3, int c3) {
        paintedVertex(consumer, pose, x0, y0, z, c0);
        paintedVertex(consumer, pose, x1, y1, z, c1);
        paintedVertex(consumer, pose, x2, y2, z, c2);
        paintedVertex(consumer, pose, x3, y3, z, c3);
    }

    private static void paintedVertex(VertexConsumer consumer, PoseStack.Pose pose, double x, double y, float z, int colour) {
        consumer.addVertex(pose, (float) x, (float) y, z).setColor(colour).setUv(0, 0).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
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
