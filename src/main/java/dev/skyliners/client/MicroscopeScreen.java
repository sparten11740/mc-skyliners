package dev.skyliners.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.block.LookIntoMicroscope;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * What a player sees looking into one of the lab's microscopes (see {@link LookIntoMicroscope}): the round field of its
 * eyepiece in the dark, a slide magnified in it, drifting a little and turning as a slide does under a nudged stage,
 * its creature or cells stirring, a fine crosshair and scale bar over it. It opens out of focus and sharpens, as a
 * microscope does when its knob is turned. Any click, or Escape, looks up again; the game goes on behind it.
 */
public class MicroscopeScreen extends Screen {
    /** How far in from the screen's nearest edges the eyepiece's field is, in GUI pixels. */
    private static final int MARGIN = 12;
    /** How much bigger than the field the slide is drawn, so it can drift without its edge showing. */
    private static final float SLIDE_OVER = 1.3F;
    /** How far the slide drifts, as a share of the field, how far it turns, in degrees, and how slowly, in ms. */
    private static final float DRIFT = 0.06F;
    private static final float TURN = 4F;
    private static final float DRIFT_PERIOD = 9000;
    /** How long it takes to come into focus, in ms, and how far apart its blurred copies start, as a share of it. */
    private static final float FOCUS_MS = 700;
    private static final float BLUR = 0.025F;
    /** How many blurred copies are drawn while out of focus, and each one's opacity. */
    private static final int BLUR_COPIES = 4;
    private static final float BLUR_ALPHA = 0.4F;
    /** A slide's picture: its size in pixels, two frames stacked, and how long each frame shows, in ms. */
    private static final int PICTURE = 128;
    private static final int FRAMES = 2;
    private static final long FRAME_MS = 450;
    /** The eyepiece's mask over the field: its size in pixels. */
    private static final int MASK = 256;
    private static final ResourceLocation EYEPIECE = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/gui/microscope/eyepiece.png");

    private final ResourceLocation picture;
    /** When it was opened, in ms, from which it comes into focus and its slide drifts. */
    private final long opened = Util.getMillis();

    private MicroscopeScreen(LookIntoMicroscope.Slide slide) {
        super(Component.translatable("screen.skyliners.microscope"));
        this.picture = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/gui/microscope/" + slide.picture() + ".png");
    }

    /** Shows the slide given, over whatever else is open. */
    public static void open(LookIntoMicroscope.Slide slide) {
        Minecraft.getInstance().setScreen(new MicroscopeScreen(slide));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        onClose();
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int field = Math.min(width, height) - 2 * MARGIN;
        int left = (width - field) / 2;
        int top = (height - field) / 2;
        long age = Util.getMillis() - opened;
        float focus = Mth.clamp(age / FOCUS_MS, 0F, 1F);
        graphics.enableScissor(left, top, left + field, top + field);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (focus < 1F) {
            float spread = (1F - focus) * BLUR * field;
            for (int copy = 0; copy < BLUR_COPIES; copy++) {
                double angle = copy * Math.PI * 2 / BLUR_COPIES;
                graphics.setColor(1F, 1F, 1F, BLUR_ALPHA);
                drawSlide(graphics, field, (float) Math.cos(angle) * spread, (float) Math.sin(angle) * spread, age);
            }
        }
        graphics.setColor(1F, 1F, 1F, focus);
        drawSlide(graphics, field, 0, 0, age);
        graphics.setColor(1F, 1F, 1F, 1F);
        graphics.blit(EYEPIECE, left, top, field, field, 0, 0, MASK, MASK, MASK, MASK);
        RenderSystem.disableBlend();
        graphics.disableScissor();
    }

    /** The slide, drifting and turning about the field's middle, nudged by so much more, its frame by the time. */
    private void drawSlide(GuiGraphics graphics, int field, float nudgeX, float nudgeY, long age) {
        double phase = age / DRIFT_PERIOD * Math.PI * 2;
        float size = field * SLIDE_OVER;
        graphics.pose().pushPose();
        graphics.pose().translate(width / 2F + (float) Math.sin(phase) * DRIFT * field + nudgeX,
            height / 2F + (float) Math.sin(phase * 0.7 + 1) * DRIFT * field + nudgeY, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) Math.sin(phase * 0.5) * TURN));
        int frame = (int) (age / FRAME_MS % FRAMES);
        int drawn = Math.round(size);
        graphics.blit(picture, -drawn / 2, -drawn / 2, drawn, drawn, 0, frame * PICTURE, PICTURE, PICTURE, PICTURE, PICTURE * FRAMES);
        graphics.pose().popPose();
    }
}
