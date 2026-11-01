package dev.skyliners.client;

import java.lang.ref.WeakReference;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

/**
 * An airliner blowing up near the player, as they see it (see {@link dev.skyliners.selfdestruct.Blast}): the white
 * flash of Ultrasplosive's nuke over everything, a hard hit that clears into a glare, then the fireball blazing again a
 * moment later as the shock front passes; and the view shaking (see {@link CameraShake#blast}). The server says when,
 * and how hard for this player, by how near they were and whether they saw it.
 */
public final class BlastFlash {
    /** How long the first flash takes to clear, and when and for how long the second blazes, in ticks. */
    private static final int FLASH_TICKS = 45;
    private static final int SECOND_FROM = 22;
    private static final int SECOND_TICKS = 30;
    /** The second flash's strength, as a share of the first's. */
    private static final float SECOND_SHARE = 0.6F;
    /**
     * How opaque the flash is at its strongest, for a blast seen from the edge of its reach and from right by it: short
     * of opaque, so the fireball still shows through it.
     */
    private static final float FAINTEST = 0.5F;
    private static final float BRIGHTEST = 0.9F;

    /** When the last blast went off, in the level's game time, and how bright its flash is for this player. */
    private static long started;
    private static float strength;
    /** The level the last blast went off in: changing world or dimension forgets it, and it does not keep a world left. */
    private static WeakReference<ClientLevel> shownIn = new WeakReference<>(null);

    private BlastFlash() {
    }

    /** An airliner has blown up near the player: their view shakes this hard and flashes this bright, each 0 to 1. */
    public static void blast(float shake, float flash) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        shownIn = new WeakReference<>(level);
        started = DrawingClock.wholeTicks();
        strength = Mth.clamp(flash, 0, 1);
        CameraShake.blast(Mth.clamp(shake, 0, 1));
    }

    /** The flash over the whole screen while it lasts. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != shownIn.get() || strength <= 0) {
            return;
        }
        float t = (float) (DrawingClock.ticks(deltaTracker.getGameTimeDeltaPartialTick(false)) - started);
        float whiteness = whiteness(t);
        if (whiteness > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(whiteness * 255) << 24 | 0xFFFFFF);
        }
    }

    /** How white the screen is this many ticks after the blast, as the overlay's opacity from 0 to 1. */
    private static float whiteness(float t) {
        float peak = Mth.lerp(strength, FAINTEST, BRIGHTEST);
        float whiteness = 0;
        if (t >= 0 && t < FLASH_TICKS) {
            // A hard hit that holds a few ticks, then clears.
            float fade = 1 - t / FLASH_TICKS;
            whiteness = peak * fade * Mth.sqrt(fade);
        }
        if (t >= SECOND_FROM && t < SECOND_FROM + SECOND_TICKS) {
            float pulse = Mth.sin((t - SECOND_FROM) / SECOND_TICKS * Mth.PI);
            whiteness = Math.max(whiteness, peak * SECOND_SHARE * pulse * pulse);
        }
        return whiteness;
    }
}
