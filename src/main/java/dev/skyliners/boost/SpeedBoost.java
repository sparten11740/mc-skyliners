package dev.skyliners.boost;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Course;
import dev.skyliners.block.BoostButtonBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A zeppelin's speed boost: pressed on the button in her control car while her autopilot flies her along, her engines
 * run full ahead a while on the heading she had, her height held as ever (see {@link BoostButtonBlockEntity}). Her
 * pilot asks it how to steer her each tick she flies level; with the server's switch off it steers nothing.
 */
public final class SpeedBoost {
    private SpeedBoost() {
    }

    /** How her boost steers her this tick; null for none under way, or with the server's switch off. */
    @Nullable
    public static Course.Steer steer(ServerLevel level, AutopilotBlockEntity autopilot, Vec3 middle, double heading) {
        if (!SkylinersConfig.SPEED_BOOST.get()) {
            return null;
        }
        for (BoostButtonBlockEntity button : BoostButtonBlockEntity.loaded(level)) {
            Course.Steer steer = AutopilotBlockEntity.zeppelinFraming(level, button.getBlockPos()) == autopilot ? button.steer(level, autopilot, heading) : null;
            if (steer != null) {
                return steer;
            }
        }
        return null;
    }
}
