package dev.aerolarge.autopilot;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import dev.aerolarge.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/**
 * The edge of space, as the rocket meets it: from y 1,000 up, far over the top of the world, the sky has gone all but
 * black and the stars are out by day (see the client's {@code SpaceSky}). Her altitude dial shows space from there up.
 *
 * <p>Climbing past it, her flight deck welcomes everyone aboard to space, over the cabin's loudspeakers as a cabin
 * announcement is made, the chime and then the voice: once she is there, and once nothing else is said in her cockpit
 * (see {@link AutopilotBlockEntity#quiet}), holding the floor while it lasts, never over anything else. It is said once
 * a climb: she has to come well back down under the edge before it is said again, so hovering at it says it once.
 * Everyone aboard as she gets there earns the Edge of Space advancement (see {@link FirstFlight#EDGE_OF_SPACE}), said
 * or not. One for each rocket, worked by her autopilot's block entity.
 */
public final class EdgeOfSpace {
    /** The height space begins at, in world blocks. */
    public static final int HEIGHT = 1000;
    /**
     * How far under the edge she is at it, in blocks: set to hold y 1,000 itself, she closes on it from under it
     * without quite getting there.
     */
    static final double AT_EDGE = 2;
    /** How far under the edge she must come back down before she is welcomed to space again, in blocks. */
    private static final double BACK_DOWN = 100;
    /**
     * How long the welcome waits for her cockpit to fall quiet, at most, in ticks: a minute, then it is let go unsaid.
     */
    private static final int WAIT_MOST = 60 * 20;
    /**
     * How long the welcome is given to play, chime and all, in ticks, if its sound cannot be read: as long as a cabin
     * announcement, which tools/generate_sounds.mjs keeps it shorter than (its ANNOUNCEMENT_LONGEST).
     */
    private static final int LONGEST = 15 * 20;

    /**
     * Whether she has been welcomed to space since she was last well under its edge; unknown until she is first seen,
     * when a rocket already up there, loaded again, counts as welcomed.
     */
    @Nullable
    private Boolean welcomed;
    /** How long the welcome has waited for her cockpit to fall quiet, in ticks. */
    private int waited;

    /** Each tick of an assembled rocket's: welcomes everyone aboard to space as she climbs to its edge. */
    void watch(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        double height = flight.position().y;
        if (welcomed == null) {
            welcomed = height >= HEIGHT - AT_EDGE;
        }
        if (height < HEIGHT - BACK_DOWN) {
            welcomed = false;
            waited = 0;
            return;
        }
        if (welcomed || height < HEIGHT - AT_EDGE) {
            return;
        }
        if (waited == 0) {
            Aboard.players(autopilot).forEach(FirstFlight.EDGE_OF_SPACE::award);
        }
        if (autopilot.quiet()) {
            welcome(level, autopilot);
            welcomed = true;
        } else {
            waited++;
            welcomed = waited >= WAIT_MOST;
        }
    }

    /** The welcome, to everyone aboard wherever they are in her, as long as it lasts holding her cockpit's floor. */
    private static void welcome(ServerLevel level, AutopilotBlockEntity autopilot) {
        autopilot.holdFloor(VoiceLength.ticks(AerolargeRegistry.ANNOUNCEMENT_SPACE, LONGEST));
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : Aboard.players(autopilot)) {
            player.connection.send(new ClientboundSoundEntityPacket(AerolargeRegistry.ANNOUNCEMENT_SPACE, SoundSource.VOICE, player, 1.0F, 1.0F, seed));
        }
    }
}
