package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.compat.SableFlight;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The airliner in her autopilot's hands (see {@link CraftFlight}): the craft the autopilot was first made for, whose
 * flying, from her take-off roll to her landing on a runway near a pin, is still the autopilot's own (see
 * {@link AutopilotBlockEntity#airlinerTick}). She is assembled by her own Physics Assembler, and her wheels count her
 * height over the ground only as far as her gear is down.
 */
final class AirlinerFlight extends CraftFlight {
    /** Flown by hand, her engines wind up to what her thrust levers ask (see {@link AutopilotBlockEntity#spools}). */
    @Override
    boolean followsLevers() {
        return true;
    }

    @Override
    public Craft craft() {
        return Craft.AIRLINER;
    }

    @Override
    boolean assembledThroughSable() {
        return false;
    }

    /** Never said: an airliner is always wired, its relays and controls laid with it. */
    @Override
    String unwiredKey() {
        return "message.skyliners.autopilot.not_assembled";
    }

    @Override
    String assemblingKey() {
        return "message.skyliners.autopilot.assembling";
    }

    @Override
    String offKey() {
        return "message.skyliners.autopilot.off";
    }

    @Override
    void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        autopilot.airlinerTick(level, flight);
    }

    @Override
    int climb() {
        return SkylinersConfig.AUTOPILOT_CLIMB.get();
    }

    @Override
    double cruise() {
        return SkylinersConfig.AUTOPILOT_CRUISE_SPEED.get();
    }

    @Override
    double radio(Level level, AutopilotBlockEntity autopilot, double radio) {
        return autopilot.gearRadio(level, radio);
    }
}
