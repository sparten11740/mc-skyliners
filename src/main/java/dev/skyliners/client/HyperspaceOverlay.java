package dev.skyliners.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.HyperspaceJump;
import java.lang.ref.WeakReference;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector4f;

/**
 * A freighter's hyperspace jump as everyone aboard sees and hears it (see {@link HyperspaceJump}), our own take on the
 * old idea of stars stretching into light: as her drive spools up, stars come out over the view and stretch into
 * streaks rushing out from its middle toward the viewer, longer and faster, the view widening a little and deepening
 * to blue; as she lets go they race at full length; she jumps in a white flash, and coming out at the pin the streaks
 * fall back to points and are gone. Called off, they fade. Her computer says each step, in her own words, with her
 * drive's whine and its whump as she goes; so the picture and the sound both tell it, and nobody needs to read.
 *
 * <p>The streaks and the blue are drawn out past her glass, not over the whole view: on a sheet across the view {@link
 * #SHEET} blocks ahead, after the world's solid blocks and before its glass, never hiding anything nearer, so her
 * cockpit's struts and console and whoever sits there stand in front of them and they are seen only through her glass,
 * covering the land beyond it. Only the flash as she jumps covers the whole view.
 */
public final class HyperspaceOverlay {
    /** How many stars stretch over the view. */
    private static final int STARS = 220;
    /** How far ahead of the eye the sheet the streaks are drawn on lies, in blocks: out past her cockpit's glass. */
    private static final float SHEET = 8;
    /** How far out from the view's middle the stars lie, as shares of its half diagonal, nearest and furthest. */
    private static final float NEAREST = 0.04F;
    private static final float FURTHEST = 1.1F;
    /** How long a streak grows as her drive spools up and as she lets go, as a share of how far out its star lies. */
    private static final float SPOOL_STRETCH = 0.9F;
    private static final float GO_STRETCH = 3.2F;
    /** How fast the stars drift out from the middle as they stretch, as a share of how far out they lie each tick. */
    private static final float DRIFT = 0.006F;
    /** A streak's thickness, in GUI pixels, thinnest and thickest. */
    private static final float THINNEST = 0.6F;
    private static final float THICKEST = 1.8F;
    /** How long the flash and the streaks falling back take coming out of the jump, and a call off's fade, in ticks. */
    private static final int ARRIVE_TICKS = 18;
    private static final int CANCEL_TICKS = 12;
    /** How long after she comes out her computer says so, in ticks: once the whump has died down. */
    private static final int ARRIVED_LINE_AFTER = 30;
    /** How much the view widens at the streaks' longest, as a share of its width. */
    private static final float WIDENING = 0.22F;
    /** How deep the blue over the view is at the streaks' longest, its opacity from 0 to 1. */
    private static final float DEEPEST_BLUE = 0.45F;
    private static final int BLUE = 0x0A1A4A;
    private static final int STREAK = 0xD8ECFF;
    private static final int STREAK_CYAN = 0x8CE6FF;
    /** How loud the jump's sounds are, heard wherever aboard she is. */
    private static final float VOLUME = 1.0F;

    /** The step showing, when it began on this client's drawing clock, how long it lasts at the least, and its stars. */
    private static HyperspaceJump.Stage stage;
    private static double began;
    private static int lasts;
    private static long seed;
    /** The tick to say she is out of the jump on, -1 for none due. */
    private static long arrivedLineAt = -1;
    /** The level the jump showing began in: changing world or dimension forgets it. */
    private static WeakReference<ClientLevel> shownIn = new WeakReference<>(null);

    private HyperspaceOverlay() {
    }

    /** A step of a jump the server tells: shown from now, with its sounds. */
    public static void told(HyperspaceJump step) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (step.stage() == HyperspaceJump.Stage.SPOOL) {
            seed = minecraft.level.getGameTime();
        }
        shownIn = new WeakReference<>(minecraft.level);
        stage = step.stage();
        began = DrawingClock.ticks(0);
        lasts = Math.max(1, step.ticks());
        switch (step.stage()) {
            case SPOOL -> {
                play(SkylinersRegistry.JUMP_DRIVE_SPOOL.get());
                play(SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_SPOOLING.get());
            }
            case GO -> play(SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_GO.get());
            case ARRIVE -> {
                play(SkylinersRegistry.JUMP_DRIVE_JUMP.get());
                arrivedLineAt = DrawingClock.wholeTicks() + ARRIVED_LINE_AFTER;
            }
            case CANCEL -> play(SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_FAULT.get());
        }
    }

    /** Once a client tick: her computer says she is out of the jump a moment after she is. */
    public static void tick(ClientTickEvent.Post event) {
        if (arrivedLineAt >= 0 && DrawingClock.wholeTicks() >= arrivedLineAt) {
            arrivedLineAt = -1;
            play(SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_ARRIVED.get());
        }
    }

    private static void play(SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, VOLUME));
    }

    /** The view widened a little as the streaks stretch. */
    public static void widen(ComputeFovModifierEvent event) {
        float stretch = stretch(DrawingClock.ticks(0));
        if (stretch > 0) {
            event.setNewFovModifier(event.getNewFovModifier() * (1 + WIDENING * stretch / GO_STRETCH));
        }
    }

    /** The flash over the whole view as she jumps, clearing as she comes out. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ClientLevel level = Minecraft.getInstance().level;
        if (stage == null || level == null || level != shownIn.get()) {
            return;
        }
        float flash = flash(DrawingClock.ticks(deltaTracker.getGameTimeDeltaPartialTick(false)));
        if (flash > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(flash * 255) << 24 | 0xFFFFFF);
        }
    }

    /**
     * After the world's solid blocks, before its glass: the blue and the streaks on a sheet {@link #SHEET} blocks
     * ahead, behind whatever is nearer.
     */
    public static void world(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || stage == null || level == null || level != shownIn.get()) {
            return;
        }
        double now = DrawingClock.ticks(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        float shown = shown(now);
        if (shown <= 0) {
            return;
        }
        float stretch = stretch(now);
        Sheet sheet = new Sheet(new Matrix4f(RenderSystem.getProjectionMatrix()));
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float blue = DEEPEST_BLUE * shown * Math.min(1, stretch / SPOOL_STRETCH);
        sheet.quad(buffer, -1, -1, 1, 1, Math.round(blue * 255) << 24 | BLUE);
        streaks(buffer, sheet, Minecraft.getInstance().getWindow(), (float) (now - began), stretch, shown);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        view.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /**
     * The sheet the streaks are drawn on, {@link #SHEET} blocks ahead of the eye, onto which a point of the screen,
     * given as the view's own coordinates from -1 to 1 across and up, is put in the eye's own frame. Anything nearer
     * than the sheet hides the streaks; anything farther, a long corridor of her own or the land outside, is drawn over.
     */
    private static final class Sheet {
        private final Matrix4f unproject;
        private final float depth;
        private final Vector4f point = new Vector4f();

        Sheet(Matrix4f projection) {
            unproject = projection.invert(new Matrix4f());
            Vector4f ahead = projection.transform(new Vector4f(0, 0, -SHEET, 1));
            depth = ahead.z / ahead.w;
        }

        /** Adds the corner of a quad at a point on the screen, so coloured. */
        void corner(BufferBuilder buffer, float across, float up, int colour) {
            unproject.transform(point.set(across, up, depth, 1));
            buffer.addVertex(point.x / point.w, point.y / point.w, point.z / point.w).setColor(colour);
        }

        /** Adds a quad across the screen from one corner to the other, so coloured. */
        void quad(BufferBuilder buffer, float left, float bottom, float right, float top, int colour) {
            corner(buffer, left, bottom, colour);
            corner(buffer, right, bottom, colour);
            corner(buffer, right, top, colour);
            corner(buffer, left, top, colour);
        }
    }

    /** How long the streaks are now, as a share of how far out their stars lie: growing, racing, falling back. */
    private static float stretch(double now) {
        if (stage == null) {
            return 0;
        }
        float t = (float) (now - began);
        return switch (stage) {
            case SPOOL -> SPOOL_STRETCH * eased(Math.min(1, t / lasts));
            case GO -> Mth.lerp(eased(Math.min(1, t / lasts)), SPOOL_STRETCH, GO_STRETCH);
            case ARRIVE -> GO_STRETCH * Math.max(0, 1 - t / ARRIVE_TICKS);
            case CANCEL -> SPOOL_STRETCH * Math.max(0, 1 - t / CANCEL_TICKS);
        };
    }

    /** How strongly the jump shows now, from 0 to 1: coming in as the spool begins, fading as it ends or is called off. */
    private static float shown(double now) {
        float t = (float) (now - began);
        return switch (stage) {
            case SPOOL -> Math.min(1, t / (lasts * 0.3F));
            case GO -> 1;
            case ARRIVE -> Math.max(0, 1 - t / ARRIVE_TICKS);
            case CANCEL -> Math.max(0, 1 - t / CANCEL_TICKS);
        };
    }

    /** The white flash as she jumps, its opacity from 0 to 1, clearing as she comes out. */
    private static float flash(double now) {
        if (stage != HyperspaceJump.Stage.ARRIVE) {
            return 0;
        }
        float fade = Math.max(0, 1 - (float) (now - began) / ARRIVE_TICKS);
        return fade * fade;
    }

    /** Eased in and out, from 0 to 1. */
    private static float eased(float share) {
        return share * share * (3 - 2 * share);
    }

    /**
     * The stars as streaks out from the view's middle, each from where its star lies, drifting out, to so far beyond
     * it, the nearer the middle the thinner and dimmer, as if far ahead.
     */
    private static void streaks(BufferBuilder buffer, Sheet sheet, Window window, float age, float stretch, float shown) {
        float width = window.getGuiScaledWidth();
        float height = window.getGuiScaledHeight();
        float middleX = width / 2F;
        float middleY = height / 2F;
        float reach = (float) Math.hypot(middleX, middleY);
        RandomSource random = RandomSource.create(seed);
        for (int star = 0; star < STARS; star++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float out = Mth.lerp(random.nextFloat(), NEAREST, FURTHEST);
            boolean cyan = random.nextInt(5) == 0;
            float from = out * reach * (1 + DRIFT * age * (1 + stretch));
            float to = from * (1 + stretch * out);
            float thick = Mth.lerp(out, THINNEST, THICKEST);
            int alpha = Math.round(255 * shown * Math.min(1, 0.35F + out));
            int colour = alpha << 24 | (cyan ? STREAK_CYAN : STREAK);
            streak(buffer, sheet, width, height, angle, from, Math.max(to, from + thick), thick, colour);
        }
    }

    /**
     * One streak out from the view's middle along an angle, between two distances from it, so thick, measured as the
     * screen's own scaled pixels are.
     */
    private static void streak(BufferBuilder buffer, Sheet sheet, float width, float height, float angle, float from, float to, float thick,
                               int colour) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float halfX = -sin * thick / 2;
        float halfY = cos * thick / 2;
        float toX = 2 / width;
        float toY = -2 / height;
        sheet.corner(buffer, (cos * from + halfX) * toX, (sin * from + halfY) * toY, colour);
        sheet.corner(buffer, (cos * to + halfX) * toX, (sin * to + halfY) * toY, colour);
        sheet.corner(buffer, (cos * to - halfX) * toX, (sin * to - halfY) * toY, colour);
        sheet.corner(buffer, (cos * from - halfX) * toX, (sin * from - halfY) * toY, colour);
    }
}
