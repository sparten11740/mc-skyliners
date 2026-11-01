package dev.skyliners.client;

import dev.skyliners.SkylinersClientConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A zeppelin's hull as whoever is aboard her hears it (see {@code tools/generate_sounds.mjs}): the hollow boom of the
 * wind over her cover ringing through the great space inside, played round the listener wherever they are aboard and
 * louder the faster she flies, fading in as they come aboard and out as they leave; and now and then a girder groaning
 * from a spot of its own somewhere round them, echoing through the hull. Without the {@code hullAmbience} client
 * setting, none of it.
 */
public final class HullAmbience {
    /** How often the player is looked for aboard a zeppelin, in ticks: finding her autopilot looks through her chunks. */
    private static final int LOOK_TICKS = 20;
    /**
     * How loud the boom is with her standing still and flying {@link #FAST} or faster, shares of full, and that speed,
     * in blocks a second.
     */
    private static final float STILL_VOLUME = 0.35F;
    private static final float FAST_VOLUME = 0.8F;
    private static final double FAST = 12;
    /** How fast the boom swells and fades toward how loud it should be, a share of full a tick. */
    private static final float FADE = 0.02F;
    /** How long between two girders' groans, at least and at most, in ticks. */
    private static final int GROAN_LEAST = 120;
    private static final int GROAN_MOST = 400;
    /**
     * Where a groan is heard from: how far from the listener, at least and at most, and how far above or below them at
     * most, in blocks.
     */
    private static final double GROAN_NEAR = 6;
    private static final double GROAN_FAR = 22;
    private static final double GROAN_RISE = 6;
    /** How loud a groan is, and how far its pitch strays either way, a share. */
    private static final float GROAN_VOLUME = 0.9F;
    private static final float GROAN_STRAY = 0.12F;

    private static final RandomSource RANDOM = RandomSource.create();
    @Nullable
    private static Boom boom;
    /** Whether the player was aboard a zeppelin at the last look, and the game time the next groan is due at. */
    private static boolean aboard;
    private static long nextGroan = Long.MIN_VALUE;

    private HullAmbience() {
    }

    /** The boom, played round the listener, swelling and fading toward how loud it should be. */
    private static final class Boom extends AbstractTickableSoundInstance {
        private float wanted;

        Boom() {
            super(SkylinersRegistry.ZEPPELIN_HULL.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
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

    /** Once a tick: the boom swelled, faded, begun or let go, and a groan when one is due. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            aboard = false;
            return;
        }
        if (level.getGameTime() % LOOK_TICKS == 0) {
            aboard = SkylinersClientConfig.HULL_AMBIENCE.get() && aboardZeppelin(player);
        }
        if (boom != null && boom.isStopped()) {
            boom = null;
        }
        if (aboard && boom == null) {
            boom = new Boom();
            minecraft.getSoundManager().play(boom);
        }
        if (boom != null) {
            boom.wanted = aboard ? loudness(player) : 0;
        }
        if (aboard) {
            groan(level, player);
        }
    }

    private static boolean aboardZeppelin(LocalPlayer player) {
        AutopilotBlockEntity autopilot = SableClient.autopilot(player);
        return autopilot != null && autopilot.craft() == Craft.ZEPPELIN;
    }

    /** How loud the boom should be: louder the faster the ship the player rides flies. */
    private static float loudness(LocalPlayer player) {
        SableClient.Ride ride = SableClient.ride(player);
        double speed = ride == null ? 0 : ride.velocity().length();
        return Mth.lerp((float) Math.min(1, speed / FAST), STILL_VOLUME, FAST_VOLUME);
    }

    /** A girder groaning somewhere round the player, once one is due, and the next one's time. */
    private static void groan(ClientLevel level, LocalPlayer player) {
        long now = level.getGameTime();
        if (now < nextGroan) {
            return;
        }
        if (nextGroan != Long.MIN_VALUE) {
            double away = Mth.lerp(RANDOM.nextDouble(), GROAN_NEAR, GROAN_FAR);
            double round = RANDOM.nextDouble() * 2 * Math.PI;
            float pitch = 1 + (RANDOM.nextFloat() * 2 - 1) * GROAN_STRAY;
            level.playLocalSound(player.getX() + Math.cos(round) * away, player.getEyeY() + (RANDOM.nextDouble() * 2 - 1) * GROAN_RISE,
                player.getZ() + Math.sin(round) * away, SkylinersRegistry.ZEPPELIN_CREAK.get(), SoundSource.AMBIENT, GROAN_VOLUME, pitch, false);
        }
        nextGroan = now + GROAN_LEAST + RANDOM.nextInt(GROAN_MOST - GROAN_LEAST);
    }
}
