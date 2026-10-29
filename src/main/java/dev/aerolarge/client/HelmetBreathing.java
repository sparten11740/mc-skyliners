package dev.aerolarge.client;

import dev.aerolarge.AerolargeClientConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.autopilot.EdgeOfSpace;
import dev.aerolarge.item.SpaceHelmetItem;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The space helmet's air as its wearer hears it (see {@code tools/generate_sounds.mjs}): while the player wears one
 * aboard a rocket at the edge of space or over it, a calm, slow breath loops softly in the helmet, the hiss of air
 * drawn in through its valve and let out again, played round the listener. It fades in as she gets there and out as
 * the helmet comes off, the player leaves her or she comes back down under the edge, which is reckoned by her middle,
 * as the server reckons it for her welcome to space ({@link EdgeOfSpace#reached}), so everyone aboard hears it from
 * the same moment wherever they stand in her. Without the {@code helmetBreathing} client setting, none of it.
 */
public final class HelmetBreathing {
    /** How loud the breathing is, a share of full: quiet, under everything else to be heard aboard. */
    private static final float VOLUME = 0.3F;
    /** How fast it fades in and out, a share of full a tick: from silent to {@link #VOLUME} in a second and a half. */
    private static final float FADE = 0.01F;

    @Nullable
    private static Breath breath;

    private HelmetBreathing() {
    }

    /** The breathing, played round the listener, fading toward how loud it should be. */
    private static final class Breath extends AbstractTickableSoundInstance {
        private float wanted;

        Breath() {
            super(AerolargeRegistry.SPACE_HELMET_BREATH.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            looping = true;
            delay = 0;
            relative = true;
            attenuation = Attenuation.NONE;
            volume = 0;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            volume = volume < wanted ? Math.min(wanted, volume + FADE) : Math.max(wanted, volume - FADE);
            if (wanted <= 0 && volume <= 0) {
                stop();
            }
        }
    }

    /**
     * Once a tick: the breathing begun, faded or let go. A breath the sound engine has dropped, as it drops every sound
     * on leaving a world, is let go too, so it can begin again.
     */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean breathing = player != null && AerolargeClientConfig.HELMET_BREATHING.get() && SpaceHelmetItem.worn(player) && inSpace(player);
        if (breath != null && (breath.isStopped() || !minecraft.getSoundManager().isActive(breath))) {
            breath = null;
        }
        if (breathing && breath == null) {
            breath = new Breath();
            minecraft.getSoundManager().play(breath);
        }
        if (breath != null) {
            breath.wanted = breathing ? VOLUME : 0;
        }
    }

    /** Whether the player is aboard a rocket at the edge of space or over it. */
    private static boolean inSpace(LocalPlayer player) {
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(player.level())) {
            if (autopilot.craft() == Craft.ROCKET && SableClient.isAboard(player, autopilot)) {
                SableClient.Ride ride = SableClient.ride(autopilot);
                return ride != null && EdgeOfSpace.reached(ride.position().y);
            }
        }
        return false;
    }
}
