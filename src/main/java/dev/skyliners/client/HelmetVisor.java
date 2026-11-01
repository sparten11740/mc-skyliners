package dev.skyliners.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyliners.client.HudLayout.Box;
import dev.skyliners.client.HudLayout.Point;
import dev.skyliners.item.PilotHelmetItem;
import dev.skyliners.item.SpaceHelmetItem;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * A helmet's visor, seen from inside. While a pilot helmet or a space helmet is worn and the player looks out through
 * their own eyes, the visor tints the view a touch toward its gold, darkens it faintly toward its rim round the edges
 * and catches a little light along its top: kept light, so the world stays clear, and lighter still in the space
 * helmet's big bubble, its rim only faint. And on the pilot helmet's the pilots' head-up display ({@link PilotHud}) is
 * shown, in parts round the screen: drawn first into a picture of their own, the size of the window, then each
 * part laid over the view from it, bent onto the visor's curve ({@link VisorCurve}) as it lies at its place on the
 * screen, edged dark, so it reads against bright sky, and with a soft glow. That picture is drawn anew each frame, a
 * few hundred shapes and some text, and laid on in a handful of draws, so it costs next to nothing; the mesh it is
 * laid on through, of many small bent cells, is kept on the graphics card and only cut anew when the screen, the HUD's
 * size or where its parts go changes.
 */
public final class HelmetVisor {
    /** How far round each part of a HUD its picture is laid on, in GUI pixels, so that none of its edge is cut off. */
    private static final float PADDING = 2;
    /** The most a part's area is laid on in one piece, in GUI pixels each way: small enough that its curve is smooth. */
    private static final float CELL = 6;
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

    /** The pilot helmet's visor: smoked a touch toward its gold, darkening toward the edges. */
    private static final Visor PILOT = new Visor(0x3A2A10, 0.06F, 0.55F, 1.35F, 0.32F, 0.16F, 0x12FFF4DC);
    /** The space helmet's: a lighter, brighter touch of gold, the rim of its big bubble only faint at the edges. */
    private static final Visor SPACE = new Visor(0x6A4A14, 0.05F, 0.8F, 1.5F, 0.2F, 0.12F, 0x10FFF4DC);
    /** The mesh the darkening is shaded across. */
    private static final int VIGNETTE_COLUMNS = 16;
    private static final int VIGNETTE_ROWS = 10;

    /** The picture the HUD is drawn into before it is laid onto the visor; made when first needed. */
    @Nullable
    private static TextureTarget picture;
    /** What the mesh the picture is laid on through was last cut from, and that mesh (see {@link #recut}). */
    @Nullable
    private static Cut cut;
    private static float[] mesh = new float[0];
    /** The mesh on the graphics card in the colours of the picture's dark edge, its glow and the picture itself. */
    private static final Coat OUTLINE_COAT = new Coat();
    private static final Coat GLOW_COAT = new Coat();
    private static final Coat FACE_COAT = new Coat();

    private HelmetVisor() {
    }

    /**
     * How a helmet's visor looks from inside: its tint's colour, and how much of it is laid over the whole view; the
     * darkening toward its rim, how far out from the middle it starts and where it is full, as shares of the way to the
     * screen's edge (the corners lie further out still), and how dark it is there; and the light caught along its top,
     * how far down it fades out, as a share of the screen, and its colour with its opacity.
     */
    private record Visor(int tint, float tintAlpha, float rimFrom, float rimTo, float rimAlpha, float glintDepth, int glint) {
    }

    /** Draws a part of a HUD flat, through the GUI's pose as set up for it: in its own units round its origin. */
    @FunctionalInterface
    interface Painter {
        void paint();
    }

    /**
     * A part of a HUD, laid onto the visor on its own: where its origin is on the screen, in GUI pixels; how far it
     * reaches round its origin, in its units; and how it is drawn.
     */
    record Part(Point at, Box reach, Painter painter) {
    }

    /**
     * What the mesh is cut from: the parts' areas on the screen, the curve they are bent onto, and the size of the
     * picture in GUI pixels. They change only with the window's size, the GUI scale, the HUD's size or where its parts
     * go (moved for the chat, the subtitles or a boss bar), so the mesh is kept from one frame to the next.
     */
    private record Cut(List<Box> areas, VisorCurve curve, float width, float height) {
    }

    /**
     * Draws a HUD's parts onto the visor: each painter draws its part flat in the HUD's units round where its part
     * goes; then the parts are bent onto the visor's curve and laid over the view this opaque, edged dark this opaque,
     * and with their glow.
     */
    static void draw(GuiGraphics graphics, VisorCurve curve, float opacity, float outline, List<Part> parts) {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        if (window.getWidth() == 0 || window.getHeight() == 0) {
            // A window with no room, as while minimised: a picture cannot be made that small.
            return;
        }
        float guiScale = (float) window.getGuiScale();
        float unit = curve.unit();
        Box[] areas = new Box[parts.size()];

        graphics.flush();
        TextureTarget target = picture(window.getWidth(), window.getHeight());
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        PoseStack pose = graphics.pose();
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            // Its origin on a whole screen pixel, so that its lines and text land on whole pixels too.
            Point at = new Point(Math.round(part.at().x() * guiScale) / guiScale, Math.round(part.at().y() * guiScale) / guiScale);
            areas[i] = part.reach().scaled(unit).at(at).grown(PADDING);
            pose.pushPose();
            pose.translate(at.x(), at.y(), 0);
            pose.scale(unit, unit, 1);
            part.painter().paint();
            graphics.flush();
            pose.popPose();
        }
        minecraft.getMainRenderTarget().bindWrite(true);

        Cut current = recut(new Cut(List.of(areas), curve, window.getWidth() / guiScale, window.getHeight() / guiScale));
        Matrix4f matrix = graphics.pose().last().pose();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.enableBlend();
        // The picture's colours are already weighted by their alpha, as drawn over a clear picture.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        OUTLINE_COAT.colour(current, 0, outline);
        float outlineOffset = OUTLINE_OFFSET / guiScale;
        for (float[] direction : OUTLINE_DIRECTIONS) {
            OUTLINE_COAT.lay(matrix, direction[0] * outlineOffset, direction[1] * outlineOffset);
        }
        GLOW_COAT.colour(current, 1, opacity * GLOW);
        float glowOffset = GLOW_OFFSET / guiScale;
        for (float[] direction : GLOW_DIRECTIONS) {
            GLOW_COAT.lay(matrix, direction[0] * glowOffset, direction[1] * glowOffset);
        }
        FACE_COAT.colour(current, 1, opacity);
        FACE_COAT.lay(matrix, 0, 0);
        VertexBuffer.unbind();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Cuts the mesh from what is wanted, unless it already is: gives what the mesh is now cut from, the same one for as
     * long as nothing it is cut from changes.
     */
    private static Cut recut(Cut wanted) {
        if (!wanted.equals(cut)) {
            mesh = mesh(wanted.areas(), wanted.curve(), wanted.width(), wanted.height());
            cut = wanted;
        }
        return cut;
    }

    /** The picture to draw the HUD into, made or resized to the size wanted; its pixels blended as it is laid on. */
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
     * The parts' areas, cut into small cells and bent onto the visor: each cell's corners, taken round the way the GUI's
     * own shapes go (the other way is culled), as where each lands on the screen and where it is in the picture, four
     * figures a corner. The picture is the size of the screen, given in GUI pixels, its bottom row its first, as OpenGL
     * keeps it.
     */
    private static float[] mesh(List<Box> areas, VisorCurve curve, float width, float height) {
        int[] columns = new int[areas.size()];
        int[] rows = new int[areas.size()];
        int cells = 0;
        for (int i = 0; i < areas.size(); i++) {
            Box area = areas.get(i);
            columns[i] = Math.max(1, Mth.ceil((area.right() - area.left()) / CELL));
            rows[i] = Math.max(1, Mth.ceil((area.bottom() - area.top()) / CELL));
            cells += columns[i] * rows[i];
        }
        float[] mesh = new float[cells * 16];
        float[] point = new float[2];
        int at = 0;
        for (int i = 0; i < areas.size(); i++) {
            Box area = areas.get(i);
            float cellWidth = (area.right() - area.left()) / columns[i];
            float cellHeight = (area.bottom() - area.top()) / rows[i];
            for (int row = 0; row < rows[i]; row++) {
                float top = area.top() + row * cellHeight;
                for (int column = 0; column < columns[i]; column++) {
                    float left = area.left() + column * cellWidth;
                    at = corner(mesh, at, curve, area, left, top, width, height, point);
                    at = corner(mesh, at, curve, area, left, top + cellHeight, width, height, point);
                    at = corner(mesh, at, curve, area, left + cellWidth, top + cellHeight, width, height, point);
                    at = corner(mesh, at, curve, area, left + cellWidth, top, width, height, point);
                }
            }
        }
        return mesh;
    }

    /** Puts a corner of a cell of a part's area into the mesh at the given place, and gives the place after it. */
    private static int corner(float[] mesh, int at, VisorCurve curve, Box area, float x, float y, float width, float height, float[] point) {
        curve.bend(x, y, area, point);
        mesh[at] = point[0];
        mesh[at + 1] = point[1];
        mesh[at + 2] = x / width;
        mesh[at + 3] = 1 - y / height;
        return at + 4;
    }

    /**
     * The mesh held on the graphics card in the colour of one of the ways the picture is laid on: its dark edge, its
     * glow or itself. It is made again only when the mesh is cut anew or the colour changes, as it does while the HUD
     * fades in; the edge's and the glow's copies of the picture, laid on a little off each way, are this one moved.
     * The colour goes with each corner of the mesh rather than being set over the whole draw, since the shader drops
     * what comes out less than a tenth opaque from the picture times the corners' colour before it applies a colour set
     * over the whole: a faint copy coloured that way would show parts of the picture its corners' colour leaves out.
     */
    private static final class Coat {
        /** Where a copy is laid, moved as wanted: one matrix set anew for each copy rather than a new one each time. */
        private static final Matrix4f MOVED = new Matrix4f();

        @Nullable
        private VertexBuffer buffer;
        /** What the mesh was cut from and the colour it was made in, for as long as it has been made. */
        @Nullable
        private Cut cut;
        private float light;
        private float opacity;

        /**
         * Readies the mesh as cut in the colour of the picture laid on this opaque: in its own colours at a light of 1,
         * or only its shape in black at 0.
         */
        void colour(Cut cut, float light, float opacity) {
            if (buffer != null && cut == this.cut && light == this.light && opacity == this.opacity) {
                return;
            }
            // The picture's colours are weighted by its alpha, so they are scaled by the opacity as well as its alpha.
            float shade = light * opacity;
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int at = 0; at < mesh.length; at += 4) {
                builder.addVertex(mesh[at], mesh[at + 1], 0).setUv(mesh[at + 2], mesh[at + 3]).setColor(shade, shade, shade, opacity);
            }
            if (buffer == null) {
                buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            }
            buffer.bind();
            buffer.upload(builder.buildOrThrow());
            this.cut = cut;
            this.light = light;
            this.opacity = opacity;
        }

        /** Lays the picture over the view through the mesh as readied, through the GUI's pose and moved this far. */
        void lay(Matrix4f pose, float offsetX, float offsetY) {
            VertexBuffer ready = Objects.requireNonNull(buffer);
            ready.bind();
            MOVED.set(RenderSystem.getModelViewMatrix()).mul(pose).translate(offsetX, offsetY, 0);
            ready.drawWithShader(MOVED, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
        }

        void close() {
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
            cut = null;
        }
    }

    /** Draws the visor over the view while a helmet is worn and the player looks out through their own eyes. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Visor visor = player == null ? null : worn(player);
        if (visor == null || !minecraft.options.getCameraType().isFirstPerson() || minecraft.getCameraEntity() != player
            || PassengerCameras.looking()) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        VertexConsumer shade = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = graphics.pose().last().pose();
        for (int row = 0; row < VIGNETTE_ROWS; row++) {
            for (int column = 0; column < VIGNETTE_COLUMNS; column++) {
                shade(shade, matrix, visor, column, row, width, height);
                shade(shade, matrix, visor, column, row + 1, width, height);
                shade(shade, matrix, visor, column + 1, row + 1, width, height);
                shade(shade, matrix, visor, column + 1, row, width, height);
            }
        }
        graphics.fillGradient(0, 0, width, Math.round(height * visor.glintDepth()), visor.glint(), visor.glint() & 0xFFFFFF);
        graphics.flush();
    }

    /** The visor of the helmet the player has on, or null with none on. */
    @Nullable
    private static Visor worn(LocalPlayer player) {
        if (PilotHelmetItem.worn(player)) {
            return PILOT;
        }
        return SpaceHelmetItem.worn(player) ? SPACE : null;
    }

    /** A corner of the darkening's mesh: the visor's tint everywhere, darkening further from the middle. */
    private static void shade(VertexConsumer shade, Matrix4f matrix, Visor visor, int column, int row, int width, int height) {
        float x = (float) column / VIGNETTE_COLUMNS;
        float y = (float) row / VIGNETTE_ROWS;
        float out = (float) Math.hypot(2 * x - 1, 2 * y - 1);
        float darkening = Mth.clamp((out - visor.rimFrom()) / (visor.rimTo() - visor.rimFrom()), 0, 1);
        float alpha = visor.tintAlpha() + visor.rimAlpha() * darkening * darkening * (3 - 2 * darkening);
        shade.addVertex(matrix, x * width, y * height, 0).setColor(Math.round(alpha * 255) << 24 | visor.tint());
    }

    /** Lets go of the HUD's picture and its mesh on leaving a world. */
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (picture != null) {
            picture.destroyBuffers();
            picture = null;
        }
        OUTLINE_COAT.close();
        GLOW_COAT.close();
        FACE_COAT.close();
        cut = null;
        mesh = new float[0];
    }
}
