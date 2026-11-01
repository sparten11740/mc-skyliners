package dev.skyliners.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.skyliners.Skyliners;
import dev.skyliners.client.PanelArt.Icon;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * A little radio panel in the top left corner of the screen while air traffic control talks to the player: a pilot's
 * headset with the radio's green lamp lit, in the autopilot panels' dark casing, pulsing gently while it talks. Whoever
 * hears it sees it, on the flight deck or in a pilot helmet anywhere aboard, so that a player who cannot read the
 * subtitles still knows the radio is talking. It shows for as long as the transmission plays, fading in as it starts
 * and out as it ends; hidden with the rest of the HUD (F1) and under the debug screen.
 */
public final class AtcHeadset {
    /** Every air traffic control sound's name starts with this (see SkylinersRegistry). */
    private static final String SOUND_PREFIX = "atc_";
    private static final Icon RADIO = Icon.of("autopilot/radio");
    /** How far in from the screen's corner the panel is, and its border round the picture, in GUI pixels. */
    private static final int MARGIN = 4;
    private static final int PADDING = 2;
    /** How long it takes to fade in and out, in milliseconds. */
    private static final float FADE_IN_MILLIS = 150;
    private static final float FADE_OUT_MILLIS = 400;
    /** The lamp's pulse while the radio talks: how often a second, and how faint it gets, as a share of fully lit. */
    private static final float PULSE_HZ = 1.5F;
    private static final float PULSE_LOW = 0.7F;
    /** How opaque the casing is, see-through a little, as the head-up display's lines are. */
    private static final float CASING_OPACITY = 0.85F;

    /** The transmission heard last, while it plays or fades out. */
    @Nullable
    private static SoundInstance transmission;
    /** When it started, and when it was seen to have ended (-1 while it plays), in milliseconds. */
    private static long startedAt;
    private static long endedAt = -1;

    private AtcHeadset() {
    }

    /** A sound is about to play: an air traffic control transmission is the one to show the panel for. */
    public static void heard(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound != null && sound.getLocation().getNamespace().equals(Skyliners.MOD_ID) && sound.getLocation().getPath().startsWith(SOUND_PREFIX)) {
            transmission = sound;
            startedAt = Util.getMillis();
            endedAt = -1;
        }
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        transmission = null;
    }

    /** Draws the panel while a transmission plays, and as it fades out after. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        SoundInstance heard = transmission;
        if (heard == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = Util.getMillis();
        boolean talking = minecraft.getSoundManager().isActive(heard);
        if (!talking && endedAt < 0) {
            endedAt = now;
        }
        float shown = talking ? Math.min(1, (now - startedAt) / FADE_IN_MILLIS) : 1 - (now - endedAt) / FADE_OUT_MILLIS;
        if (shown <= 0) {
            transmission = null;
            return;
        }
        if (minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }
        int size = Icon.SIZE + 2 * PADDING;
        graphics.fill(MARGIN, MARGIN, MARGIN + size, MARGIN + size, faded(AutopilotScreenRenderer.BUTTON_EDGE, shown * CASING_OPACITY));
        graphics.fill(MARGIN + 1, MARGIN + 1, MARGIN + size - 1, MARGIN + size - 1, faded(AutopilotScreenRenderer.CASING, shown * CASING_OPACITY));
        float pulse = talking ? PULSE_LOW + (1 - PULSE_LOW) * (0.5F + 0.5F * Mth.sin(now / 1000F * Mth.TWO_PI * PULSE_HZ)) : 1;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1F, 1F, 1F, shown * pulse);
        graphics.blit(RADIO.texture(), MARGIN + PADDING, MARGIN + PADDING, 0, 0, Icon.SIZE, Icon.SIZE, Icon.SIZE, Icon.SIZE);
        graphics.setColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    /** A colour with its alpha scaled by a share. */
    private static int faded(int colour, float share) {
        return Math.round((colour >>> 24) * Mth.clamp(share, 0, 1)) << 24 | colour & 0xFFFFFF;
    }
}
