package dev.aerolarge.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.aerolarge.item.PilotHelmetItem;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The pilot helmet's visor, seen from inside. While a pilot helmet is worn and the player looks out through their own
 * eyes, the visor tints the view a touch toward its gold, darkens it faintly toward the edges and catches a little
 * light along its top: kept light, so the world stays clear. And the pilots' head-up display ({@link PilotHud}) is
 * drawn onto its curve rather than flat on the screen: drawn first into a small picture of its own, then laid onto a
 * mesh that bends it as the visor bends round the face, its rows lifting toward the ends and its ends drawn in, so its
 * lines curve the way a visor's would; edged dark, so it reads against bright sky, and with a soft glow. That picture
 * is drawn anew each frame, a few hundred shapes and some text, and laid on in a handful of draws, so it costs next to
 * nothing.
 */
public final class HelmetVisor {
    /**
     * The visor's curve, as shares of the HUD's half height and half width: how far its rows lift at its left and right
     * ends, how much narrower its ends are drawn, and how much shorter, as the visor curves away round the face.
     */
    private static final float BEND = 0.2F;
    private static final float SQUEEZE = 0.1F;
    private static final float TAPER = 0.08F;
    /** The mesh the HUD is bent onto: columns and rows, enough that its curve looks smooth. */
    private static final int COLUMNS = 32;
    private static final int ROWS = 16;
    /**
     * The glow round the symbology: faint copies of the picture laid round it, each this far off in screen pixels and
     * this opaque as a share of the HUD's own opacity.
     */
    private static final float GLOW_OFFSET = 1.5F;
    private static final float GLOW = 0.18F;
    private static final float[][] GLOW_DIRECTIONS = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
    /**
     * The dark edge round the symbology, so it reads against bright sky and white skin without a panel behind it: the
     * picture's shape laid on dark, a screen pixel off each way along the axes, under the picture itself.
     */
    private static final float OUTLINE_OFFSET = 1;
    private static final float[][] OUTLINE_DIRECTIONS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    /** The GUI's nearest depth, as {@link GameRenderer} sets it, so the HUD's picture is drawn as the GUI is. */
    private static final float GUI_NEAR = 1000;

    /** The visor's tint: its colour, and how much of it is laid over the whole view. */
    private static final int TINT = 0x3A2A10;
    private static final float TINT_ALPHA = 0.06F;
    /**
     * The darkening toward the edges: how far out from the middle it starts and where it is full, as shares of the way
     * to the screen's edge (the corners lie further out still), and how dark it is there.
     */
    private static final float VIGNETTE_FROM = 0.55F;
    private static final float VIGNETTE_TO = 1.35F;
    private static final float VIGNETTE_ALPHA = 0.32F;
    /** The mesh the darkening is shaded across. */
    private static final int VIGNETTE_COLUMNS = 16;
    private static final int VIGNETTE_ROWS = 10;
    /** The light caught along the visor's top: how far down it fades out, as a share of the screen, and how bright. */
    private static final float GLINT_DEPTH = 0.16F;
    private static final int GLINT = 0x12FFF4DC;

    /** The picture the HUD is drawn into before it is bent onto the visor; made when first needed. */
    @Nullable
    private static TextureTarget picture;

    private HelmetVisor() {
    }

    /** Draws a HUD flat, through the GUI's pose as set up for it: in its own units, its middle at the origin. */
    @FunctionalInterface
    interface Painter {
        void paint();
    }

    /**
     * Draws a HUD onto the visor round the middle of the screen: the painter draws it flat in its own units, half the
     * given width and height either side of the middle, each unit the given number of screen pixels; then it is bent
     * onto the visor's curve and laid over the view this opaque, edged dark this opaque, and with its glow.
     */
    static void drawCurved(GuiGraphics graphics, float halfWidth, float halfHeight, float pixelsPerUnit, float opacity, float outline,
                           Painter painter) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        float guiScale = (float) window.getGuiScale();
        // An even number of pixels across and down, so the middle falls between two and whole units on whole pixels.
        int width = 2 * Math.round(halfWidth * pixelsPerUnit);
        int height = 2 * Math.round(halfHeight * pixelsPerUnit);
        if (width == 0 || height == 0) {
            // A window with no room, as while minimised: a picture cannot be made that small.
            return;
        }
        float middleX = window.getWidth() / guiScale / 2;
        float middleY = window.getHeight() / guiScale / 2;
        float spanX = width / 2F / guiScale;
        float spanY = height / 2F / guiScale;

        graphics.flush();
        TextureTarget target = picture(width, height);
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(middleX - spanX, middleX + spanX, middleY + spanY, middleY - spanY, GUI_NEAR,
            ClientHooks.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(middleX, middleY, 0);
        pose.scale(pixelsPerUnit / guiScale, pixelsPerUnit / guiScale, 1);
        painter.paint();
        graphics.flush();
        pose.popPose();
        RenderSystem.setProjectionMatrix(projection, sorting);
        minecraft.getMainRenderTarget().bindWrite(true);

        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.enableBlend();
        // The picture's colours are already weighted by their alpha, as drawn over a clear picture.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        float outlineOffset = OUTLINE_OFFSET / guiScale;
        for (float[] direction : OUTLINE_DIRECTIONS) {
            bend(matrix, middleX + direction[0] * outlineOffset, middleY + direction[1] * outlineOffset, spanX, spanY, 0, outline);
        }
        float glowOffset = GLOW_OFFSET / guiScale;
        for (float[] direction : GLOW_DIRECTIONS) {
            bend(matrix, middleX + direction[0] * glowOffset, middleY + direction[1] * glowOffset, spanX, spanY, 1, opacity * GLOW);
        }
        bend(matrix, middleX, middleY, spanX, spanY, 1, opacity);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /** The picture to draw the HUD into, made or resized to the size wanted; its pixels blended as it is bent. */
    private static TextureTarget picture(int width, int height) {
        TextureTarget target = picture;
        if (target == null) {
            target = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            target.setFilterMode(GL11.GL_LINEAR);
            picture = target;
        } else if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
        return target;
    }

    /**
     * Lays the picture onto the visor's curve round a middle, reaching the given spans either side of it before it is
     * bent, this opaque: in its own colours at a light of 1, or only its shape in black at 0. The picture's bottom row
     * is its first, as OpenGL keeps it.
     */
    private static void bend(Matrix4f matrix, float middleX, float middleY, float spanX, float spanY, float light, float opacity) {
        // The picture's colours are weighted by its alpha, so they are scaled by the opacity as well as its alpha.
        float shade = light * opacity;
        BufferBuilder mesh = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int row = 0; row < ROWS; row++) {
            float top = (float) row / ROWS;
            float bottom = (float) (row + 1) / ROWS;
            for (int column = 0; column < COLUMNS; column++) {
                float left = (float) column / COLUMNS;
                float right = (float) (column + 1) / COLUMNS;
                // Round the way the GUI's own shapes go, as the other way is culled.
                corner(mesh, matrix, left, top, middleX, middleY, spanX, spanY).setColor(shade, shade, shade, opacity);
                corner(mesh, matrix, left, bottom, middleX, middleY, spanX, spanY).setColor(shade, shade, shade, opacity);
                corner(mesh, matrix, right, bottom, middleX, middleY, spanX, spanY).setColor(shade, shade, shade, opacity);
                corner(mesh, matrix, right, top, middleX, middleY, spanX, spanY).setColor(shade, shade, shade, opacity);
            }
        }
        BufferUploader.drawWithShader(mesh.buildOrThrow());
    }

    /** A corner of the mesh, at a share of the way across and down the flat picture, moved onto the curve. */
    private static VertexConsumer corner(BufferBuilder mesh, Matrix4f matrix, float across, float down, float middleX, float middleY,
                                         float spanX, float spanY) {
        float x = 2 * across - 1;
        float y = 2 * down - 1;
        float edge = x * x;
        float bentX = x * (1 - SQUEEZE * edge);
        float bentY = y * (1 - TAPER * edge) - BEND * edge;
        return mesh.addVertex(matrix, middleX + bentX * spanX, middleY + bentY * spanY, 0).setUv(across, 1 - down);
    }

    /** Draws the visor over the view while a pilot helmet is worn and the player looks out through their own eyes. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !PilotHelmetItem.worn(player) || !minecraft.options.getCameraType().isFirstPerson()
            || minecraft.getCameraEntity() != player || PassengerCameras.looking()) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        VertexConsumer shade = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = graphics.pose().last().pose();
        for (int row = 0; row < VIGNETTE_ROWS; row++) {
            for (int column = 0; column < VIGNETTE_COLUMNS; column++) {
                shade(shade, matrix, column, row, width, height);
                shade(shade, matrix, column, row + 1, width, height);
                shade(shade, matrix, column + 1, row + 1, width, height);
                shade(shade, matrix, column + 1, row, width, height);
            }
        }
        graphics.fillGradient(0, 0, width, Math.round(height * GLINT_DEPTH), GLINT, GLINT & 0xFFFFFF);
        graphics.flush();
    }

    /** A corner of the darkening's mesh: the tint everywhere, darkening further from the middle. */
    private static void shade(VertexConsumer shade, Matrix4f matrix, int column, int row, int width, int height) {
        float x = (float) column / VIGNETTE_COLUMNS;
        float y = (float) row / VIGNETTE_ROWS;
        float out = (float) Math.hypot(2 * x - 1, 2 * y - 1);
        float darkening = Mth.clamp((out - VIGNETTE_FROM) / (VIGNETTE_TO - VIGNETTE_FROM), 0, 1);
        float alpha = TINT_ALPHA + VIGNETTE_ALPHA * darkening * darkening * (3 - 2 * darkening);
        shade.addVertex(matrix, x * width, y * height, 0).setColor(Math.round(alpha * 255) << 24 | TINT);
    }

    /** Lets go of the HUD's picture on leaving a world. */
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (picture != null) {
            picture.destroyBuffers();
            picture = null;
        }
    }
}
