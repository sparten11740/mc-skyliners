package dev.aerolarge.autopilot;

import dev.aerolarge.AerolargeRegistry;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * What is heard of a rocket as she flies, worked out on the server for her autopilot (see {@link AutopilotBlockEntity}
 * and {@link RocketPilot}). Each side jet firing from rest puffs, never so often that a jet flickering on and off
 * rattles. And how hard her engines fire is shown to the clients, who play their roar from it, their ignition as they
 * light and their cut-off as they stop, and her touchdown (see the client's {@code RocketRoar}).
 */
final class RocketSounds {
    /**
     * How many steps of her engines' thrust the clients are shown, from none to full, and how often at most a change
     * of step is sent, in ticks: often enough for her roar to follow it, seldom enough to cost nothing. Lighting and
     * stopping are sent at once.
     */
    private static final int THRUST_STEPS = 20;
    private static final int THRUST_EVERY = 10;
    /** Her side jets, four in each of her two rings, as her pilot reports them. */
    private static final int JETS = 8;
    /**
     * A side jet's puff, so that jets starting and stopping several times a second as they hold her upright never
     * chatter: the least signal a jet puffs at, a trimming flicker below it going unheard; how long it must have rested
     * to puff as it fires again, how long after its own last puff before another, and how long after any jet's, in
     * ticks; how loud, and how far its pitch strays either way, as shares.
     */
    private static final int JET_LEAST = 3;
    private static final int JET_RESTED = 10;
    private static final int JET_AGAIN = 40;
    private static final int PUFF_AGAIN = 10;
    private static final float PUFF_VOLUME = 0.35F;
    private static final float PUFF_STRAY = 0.1F;
    /** Long before any game time: never. */
    private static final long NEVER = Long.MIN_VALUE / 2;

    /** Her engines' thrust as the clients were last shown it, a share of full, 0 at rest; and when it was sent. */
    private float shown;
    private long shownAt = NEVER;
    /** When each side jet last fired and last puffed, in her pilot's report's order, and when any jet last puffed. */
    private final long[] jetFired = new long[JETS];
    private final long[] jetPuffed = new long[JETS];
    private long puffed = NEVER;

    RocketSounds() {
        Arrays.fill(jetFired, NEVER);
        Arrays.fill(jetPuffed, NEVER);
    }

    /**
     * Each tick of hers, once her pilot has flown: a puff for each side jet firing from rest. Whether the clients are to
     * be shown how hard her engines fire afresh.
     */
    boolean tick(ServerLevel level, BlockPos origin, RocketPilot pilot) {
        RocketPilot.Report report = pilot.report();
        List<BlockPos> relays = pilot.jetRelays();
        for (int jet = 0; jet < JETS; jet++) {
            if (report.jets().get(jet) > 0 && relays.get(jet) != null) {
                fired(level, origin.offset(relays.get(jet)), jet, report.jets().get(jet));
            }
        }
        return show(report.thrust(), level.getGameTime());
    }

    /**
     * A side jet fires at a block at a signal: it puffs if it had rested and pushes hard enough to be heard, and neither
     * it nor any other jet puffed too lately.
     */
    private void fired(ServerLevel level, BlockPos at, int jet, int signal) {
        long now = level.getGameTime();
        boolean fromRest = now - jetFired[jet] > JET_RESTED;
        jetFired[jet] = now;
        if (!fromRest || signal < JET_LEAST || now - jetPuffed[jet] < JET_AGAIN || now - puffed < PUFF_AGAIN) {
            return;
        }
        jetPuffed[jet] = now;
        puffed = now;
        float pitch = 1 + (level.getRandom().nextFloat() * 2 - 1) * PUFF_STRAY;
        level.playSound(null, at, AerolargeRegistry.ROCKET_JET_PUFF.get(), SoundSource.BLOCKS, PUFF_VOLUME, pitch);
    }

    /**
     * Whether the clients are to be shown her engines' thrust afresh, a share of full: at once as they light or stop,
     * otherwise once it is a step off what they were shown, and not too often.
     */
    private boolean show(double thrust, long now) {
        int steps = thrust <= 0 ? 0 : Math.max(1, (int) Math.round(thrust * THRUST_STEPS));
        int was = Math.round(shown * THRUST_STEPS);
        boolean lighting = (steps > 0) != (was > 0);
        if (steps == was || !lighting && now - shownAt < THRUST_EVERY) {
            return false;
        }
        shown = (float) steps / THRUST_STEPS;
        shownAt = now;
        return true;
    }

    /** How hard her engines fire as the clients are shown it, a share of full thrust; 0 at rest. */
    float thrust() {
        return shown;
    }

    /** Her engines' thrust, in the update sent to the clients. */
    void saveShown(CompoundTag tag) {
        tag.putFloat("RocketThrust", shown);
    }

    /** Her engines' thrust from the update the server sent; never in a save, which leaves her at rest. */
    void loadShown(CompoundTag tag) {
        shown = Mth.clamp(tag.getFloat("RocketThrust"), 0, 1);
    }
}
