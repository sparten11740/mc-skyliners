package dev.aerolarge.skywriting;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Course;
import dev.aerolarge.block.SkywritingBoardBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Skywriting: a shape picked on the skywriting board in a zeppelin's control car, which her autopilot flies across the
 * sky at her height while smoke in her cells' colours streams from under her belly (see {@link SkyShape} and
 * {@link SkywritingBoardBlockEntity}). Her pilot asks it how to steer her each tick she flies level; with the server's
 * switch off it steers nothing, and her board gives the shape up unsteered.
 */
public final class Skywriting {
    private Skywriting() {
    }

    /** How her board steers her this tick, writing a shape; null for none, or with the server's switch off. */
    @Nullable
    public static Course.Steer steer(ServerLevel level, AutopilotBlockEntity autopilot, Vec3 middle, double heading) {
        if (!AerolargeConfig.SKYWRITING.get()) {
            return null;
        }
        for (SkywritingBoardBlockEntity board : SkywritingBoardBlockEntity.loaded(level)) {
            if (board.shape() != null && AutopilotBlockEntity.zeppelinFraming(level, board.getBlockPos()) == autopilot) {
                return board.steer(level, autopilot, middle, heading);
            }
        }
        return null;
    }
}
