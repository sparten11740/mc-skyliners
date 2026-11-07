package dev.skyliners.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.HyperspaceJump;
import java.lang.ref.WeakReference;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import org.joml.Matrix4f;

/**
 * A freighter's hyperspace jump as everyone aboard sees and hears it (see {@link HyperspaceJump}), our own take on the
 * old idea of stars stretching into light: as her drive spools up, stars come out over the view and stretch into
 * streaks rushing out from its middle toward the viewer, longer and faster, the view widening a little and deepening
 * to blue; as she lets go they race at full length; she jumps in a white flash, and coming out at the pin the streaks
 * fall back to points and are gone. Called off, they fade. Her computer says each step, in her own words, with her
 * drive's whine and its whump as she goes; so the picture and the sound both tell it, and nobody needs to read.
 */
public final class HyperspaceOverlay {
    /** How many stars stretch over the view. */
    private static final int STARS = 220;
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

    /** The jump over the whole view while it lasts: the blue, the streaks and the flash. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ClientLevel level = Minecraft.getInstance().level;
        if (stage == null || level == null || level != shownIn.get()) {
            return;
        }
        double now = DrawingClock.ticks(deltaTracker.getGameTimeDeltaPartialTick(false));
        float stretch = stretch(now);
        float shown = shown(now);
        if (shown <= 0) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float blue = DEEPEST_BLUE * shown * Math.min(1, stretch / SPOOL_STRETCH);
        graphics.fill(0, 0, width, height, Math.round(blue * 255) << 24 | BLUE);
        streaks(graphics, width, height, (float) (now - began), stretch, shown);
        float flash = flash(now);
        if (flash > 0) {
            graphics.fill(0, 0, width, height, Math.round(flash * 255) << 24 | 0xFFFFFF);
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
    private static void streaks(GuiGraphics graphics, int width, int height, float age, float stretch, float shown) {
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = graphics.pose().last().pose();
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
            streak(buffer, matrix, middleX, middleY, angle, from, Math.max(to, from + thick), thick, alpha << 24 | (cyan ? STREAK_CYAN : STREAK));
        }
        graphics.flush();
    }

    /** One streak out from the middle along an angle, between two distances from it, so thick. */
    private static void streak(VertexConsumer buffer, Matrix4f matrix, float middleX, float middleY, float angle, float from, float to, float thick,
                               int colour) {
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float halfX = -sin * thick / 2;
        float halfY = cos * thick / 2;
        float x0 = middleX + cos * from;
        float y0 = middleY + sin * from;
        float x1 = middleX + cos * to;
        float y1 = middleY + sin * to;
        // Round the same way as the game's own filled rectangles, so none is culled.
        buffer.addVertex(matrix, x0 + halfX, y0 + halfY, 0).setColor(colour);
        buffer.addVertex(matrix, x1 + halfX, y1 + halfY, 0).setColor(colour);
        buffer.addVertex(matrix, x1 - halfX, y1 - halfY, 0).setColor(colour);
        buffer.addVertex(matrix, x0 - halfX, y0 - halfY, 0).setColor(colour);
    }
}
