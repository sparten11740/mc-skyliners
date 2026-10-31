package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.block.TouchPanel;
import dev.aerolarge.block.TouchPanelBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.joml.Matrix4f;

/**
 * How every touch panel draws its tiles (see {@link TouchPanel}), so they all answer a player the same way: the tile
 * under the crosshair lights up, a bold white frame round it and its colour lightened toward white; the tile that is
 * picked now (the floor you are on, the livery worn, the camera shown) has a green frame; a tile that can do nothing
 * now, or is not the current one, is dimmed. The game's own outline is left off a touch panel's blocks, the tile
 * looked at lighting up in its place. Each panel lays out its own tiles and chooses their colours and pictures.
 */
public final class TouchPanelArt {
    /** How thick the bright frame round the tile looked at is, and how far its colour is lightened toward white. */
    public static final double LOOKED_FRAME = 0.03;
    private static final double LOOKED_LIGHTER = 0.3;
    /** How thick the green frame round the tile picked now is. */
    public static final double SELECTED_FRAME = 0.02;
    /** How much of its colour a dimmed tile keeps. */
    private static final double DIMMED = 0.4;
    /** Lifts a tile's cell off its frame. */
    private static final float LAYER = 0.001F;
    private static final int WHITE = 0xFFF2F6FB;
    /** The frame round the tile picked now: a bright green, the colour of what is on, standing out on any casing. */
    private static final int PICKED = 0xFF3FE070;

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
     * A tile's cell in its colour, and round it the frame its look asks for: the bright frame while looked at, else the
     * green one while picked. The frame is drawn at the layer given, the cell just over it.
     */
    public static void tile(VertexConsumer shapes, Matrix4f matrix, TouchPanel.Tile tile, int colour, Look look, float z) {
        double frame = look.looked() ? LOOKED_FRAME : look.selected() ? SELECTED_FRAME : 0;
        if (frame > 0) {
            ScreenDrawing.rect(shapes, matrix, tile.left() - frame, tile.bottom() - frame, tile.right() + frame, tile.top() + frame, z,
                look.looked() ? WHITE : PICKED);
        }
        ScreenDrawing.rect(shapes, matrix, tile.left(), tile.bottom(), tile.right(), tile.top(), z + LAYER, colour(colour, look));
    }

    /** A tile's colour as its look shows it: lightened while looked at, its own while lit, dimmed otherwise. */
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
