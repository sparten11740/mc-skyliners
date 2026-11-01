package dev.skyliners.autopilot;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersRegistry;
import java.util.Arrays;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What is heard of a rocket as she flies, worked out on the server for her autopilot (see {@link AutopilotBlockEntity}
 * and {@link RocketPilot}). Launch control counts her down over the radio, "ten" to "one" on the seconds of her
 * countdown and "lift-off" as her engines light, heard by everyone near her, aboard or watching from the ground; it
 * begins only once the cabin has heard its take-off announcement, as a zeppelin's ground officer waits for her control
 * car to fall quiet, and holds the flight deck's floor while it counts; on the same radio it calls her flight's
 * milestones as she passes them (see {@link LaunchCallouts}). Each side jet firing from rest puffs, never so
 * often that a jet flickering on and off rattles. Down on her fins, the flight deck's voice says "Touchdown." And how
 * hard her engines fire is shown to the clients, who play their roar from it, their ignition as they light and their
 * cut-off as they stop, and her touchdown (see the client's {@code RocketRoar}).
 */
final class RocketSounds {
    /**
     * How long her countdown waits at most for the flight deck to fall quiet before it begins, in ticks: as long as the
     * take-off announcement may run (tools/generate_sounds.mjs's TAKEOFF_LONGEST), and a little more.
     */
    private static final int HOLD_MOST = 9 * SharedConstants.TICKS_PER_SECOND;
    /** Everyone this near her middle hears launch control, in blocks: all of her, and those watching round her feet. */
    private static final double LAUNCH_HEARD = 128;
    /**
     * How long launch control's "lift-off" and the flight deck's "Touchdown." are each given if their sounds cannot be
     * read, in ticks: tools/generate_sounds.mjs keeps them shorter.
     */
    private static final int WORD_LENGTH = 24;
    private static final int CALLOUT_LENGTH = 40;
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

    private final Airtime airtime;
    /** How long her countdown has waited for the flight deck to fall quiet, in ticks. */
    private int held;
    /** Her engines' thrust as the clients were last shown it, a share of full, 0 at rest; and when it was sent. */
    private float shown;
    private long shownAt = NEVER;
    /** Whether her engines fired last tick, and whether "Touchdown." has been said since her landing burn began. */
    private boolean lit;
    private boolean landed;
    /** When each side jet last fired and last puffed, in her pilot's report's order, and when any jet last puffed. */
    private final long[] jetFired = new long[JETS];
    private final long[] jetPuffed = new long[JETS];
    private long puffed = NEVER;

    RocketSounds(Airtime airtime) {
        this.airtime = airtime;
        Arrays.fill(jetFired, NEVER);
        Arrays.fill(jetPuffed, NEVER);
    }

    /**
     * Whether her countdown waits this tick before it begins: set to take off and not yet counting while the flight
     * deck is not quiet, the cabin hearing its take-off announcement, for {@link #HOLD_MOST} ticks at most.
     */
    boolean holdsCountdown(AutopilotBlockEntity.Phase phase, int counted, boolean quiet) {
        if (phase != AutopilotBlockEntity.Phase.TAKEOFF || counted > 0 || quiet || held >= HOLD_MOST) {
            held = 0;
            return false;
        }
        held++;
        return true;
    }

    /**
     * Launch control's word for the second of her countdown it has come to, so many ticks in: "ten" as it begins, a
     * number each whole second after, and "lift-off" as it ends. From "ten" on it holds the flight deck's floor until
     * "lift-off" has been said, cutting short whatever was being said there as it starts.
     */
    void count(ServerLevel level, Vec3 middle, int counted) {
        int left = Mth.clamp(Mth.positiveCeilDiv(RocketPilot.COUNTDOWN - counted, SharedConstants.TICKS_PER_SECOND), 0,
            SkylinersRegistry.LAUNCH_COUNT.size());
        long now = level.getGameTime();
        if (left == SkylinersRegistry.LAUNCH_COUNT.size()) {
            airtime.interrupt(now);
        }
        airtime.take(now, RocketPilot.COUNTDOWN - counted + VoiceLength.ticks(SkylinersRegistry.LAUNCH_LIFTOFF, WORD_LENGTH));
        overTheRadio(level, middle, left > 0 ? SkylinersRegistry.LAUNCH_COUNT.get(left - 1) : SkylinersRegistry.LAUNCH_LIFTOFF);
    }

    /**
     * Something launch control says over the radio, played to each one who hears it, near her middle, aboard or
     * watching from the ground, as the flight deck's voice is played, wherever they stand (see {@link #LAUNCH_HEARD}).
     */
    static void overTheRadio(ServerLevel level, Vec3 middle, Holder<SoundEvent> said) {
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(middle) < LAUNCH_HEARD * LAUNCH_HEARD) {
                player.connection.send(new ClientboundSoundEntityPacket(said, SoundSource.VOICE, player, 1.0F, 1.0F, seed));
            }
        }
    }

    /**
     * Each tick of hers, once her pilot has flown: a puff for each side jet firing from rest, and "Touchdown." as her
     * engines stop in her landing burn, down on her fins. Whether the clients are to be shown how hard her engines
     * fire afresh.
     */
    boolean tick(ServerLevel level, BlockPos origin, RocketPilot pilot, AutopilotBlockEntity.Phase phase, BlockEntity autopilot) {
        RocketPilot.Report report = pilot.report();
        List<BlockPos> relays = pilot.jetRelays();
        for (int jet = 0; jet < JETS; jet++) {
            if (report.jets().get(jet) > 0 && relays.get(jet) != null) {
                fired(level, origin.offset(relays.get(jet)), jet, report.jets().get(jet));
            }
        }
        boolean firing = report.thrust() > 0;
        if (lit && !firing && phase == AutopilotBlockEntity.Phase.LAND && !landed) {
            landed = true;
            touchdown(level, autopilot);
        }
        landed = landed && phase == AutopilotBlockEntity.Phase.LAND;
        lit = firing;
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
        level.playSound(null, at, SkylinersRegistry.ROCKET_JET_PUFF.get(), SoundSource.BLOCKS, PUFF_VOLUME, pitch);
    }

    /** "Touchdown.", in the flight deck's voice to everyone aboard, cutting short whatever is being said there. */
    private void touchdown(ServerLevel level, BlockEntity autopilot) {
        long now = level.getGameTime();
        airtime.interrupt(now);
        airtime.take(now, VoiceLength.ticks(SkylinersRegistry.CALLOUT_TOUCHDOWN, CALLOUT_LENGTH));
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : Aboard.players(autopilot)) {
            player.connection.send(new ClientboundSoundEntityPacket(SkylinersRegistry.CALLOUT_TOUCHDOWN, SoundSource.VOICE, player, 1.0F, 1.0F, seed));
        }
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
