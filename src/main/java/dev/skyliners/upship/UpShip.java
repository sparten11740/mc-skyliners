package dev.skyliners.upship;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.block.BallastValveBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;

/**
 * "Up ship!": a zeppelin taking off from the ground is held a moment on her handlers' lines, as the Hindenburg was at
 * Lakehurst, while her bow's water ballast pours out, until her ground officer calls "Up ship!" and they let her go
 * together (see {@link BallastValveBlockEntity}). Her pilot asks it before she rises (see
 * {@link dev.skyliners.autopilot.ZeppelinPilot}); a zeppelin already floating, or one built without a valve, takes off
 * at once.
 */
public final class UpShip {
    /** How high over the ground she may float and still be held on the lines, in blocks: resting on her wheels. */
    private static final double ON_GROUND = 4;

    private UpShip() {
    }

    /**
     * Whether her handlers hold her on their lines now: from the first tick of a take-off from the ground, through the
     * wait for the server to tick steadily and the beats after, until they let her go.
     */
    public static boolean holds(ServerLevel level, AutopilotBlockEntity autopilot, double radio, boolean first) {
        BallastValveBlockEntity valve = valve(level, autopilot);
        if (valve == null) {
            return false;
        }
        long now = level.getGameTime();
        if (first) {
            if (!SkylinersConfig.UP_SHIP.get() || radio > ON_GROUND) {
                return false;
            }
            valve.ask(now);
            return true;
        }
        return valve.hold(now);
    }

    /** The zeppelin's own ballast valve, rigged with its handlers' stations; null for none. */
    @Nullable
    private static BallastValveBlockEntity valve(ServerLevel level, AutopilotBlockEntity autopilot) {
        for (BallastValveBlockEntity valve : BallastValveBlockEntity.loaded(level)) {
            if (valve.rigged() && AutopilotBlockEntity.zeppelinFraming(level, valve.getBlockPos()) == autopilot) {
                return valve;
            }
        }
        return null;
    }
}
