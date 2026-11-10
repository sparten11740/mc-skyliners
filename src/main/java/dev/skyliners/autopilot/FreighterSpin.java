package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/**
 * The freighter's spin, from the spin button on her console: flying along high over the ground, she rolls right over
 * about her length, a whole turn, her lift roaring as it turns her, and comes back level, her autopilot flying on as it
 * was (her pilot rolls her: see {@link FreighterPilot#spin}). A roll costs her height, her lift pointing down while she
 * is upside down, so near the ground the button only says no. Pressed while she is knocked far over, she is righting
 * herself already, rolling back up the shortest way, and the button says so. What the button is lit by is kept here,
 * on the server and as the clients are shown it.
 */
public final class FreighterSpin {
    /**
     * How high her feet must stand over the ground for her to spin, in blocks: nearly twice the 133 a roll was seen to
     * take her down before her lift caught her.
     */
    static final double LEAST_HEIGHT = 250;
    /** How loud and how high her lift's roar is as she begins to roll, a little lower than her boost's. */
    private static final float ROAR_VOLUME = 2.5F;
    private static final float ROAR_PITCH = 0.8F;

    /** What asking her to spin comes to, each with its own word in the hotbar. */
    public enum Answer implements StringRepresentable {
        /** Her lift roars and over she goes. */
        SPINNING,
        /** She is rolling already, spinning or righting herself. */
        BUSY,
        /** She is not flying along: on the ground, lifting off, coming down or switched off. */
        NOT_FLYING,
        /** She is too near the ground for the height a roll takes. */
        TOO_LOW,
        /** The server's config has it switched off. */
        SWITCHED_OFF,
        /** There is no freighter to spin. */
        NO_SHIP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where her spin stands as her console shows it: not to be had, ready, or rolling. */
    public enum Stage {
        UNAVAILABLE, READY, ROLLING
    }

    /** On the server and as the clients are shown it: whether she rolls, and whether she could spin now. */
    private boolean rolling;
    private boolean ready;

    /**
     * Asked to spin, on the server, her feet so high over the ground, in blocks: her pilot set rolling, or why not.
     */
    Answer start(ServerLevel level, AutopilotBlockEntity autopilot, FreighterPilot pilot, double radio) {
        Answer refused = refusal(autopilot, pilot, radio);
        if (refused != null) {
            return refused;
        }
        pilot.spin();
        rolling = true;
        Vec3 at = heardAt(autopilot);
        level.playSound(null, at.x, at.y, at.z, SkylinersRegistry.SPEED_BOOST.get(), SoundSource.BLOCKS, ROAR_VOLUME, ROAR_PITCH);
        autopilot.changed();
        return Answer.SPINNING;
    }

    /**
     * Once a tick of hers, on the server, after her pilot has flown: whether she rolls and whether she could spin now
     * kept for her console, told to the clients when either changes.
     */
    void tick(AutopilotBlockEntity autopilot, FreighterPilot pilot, double radio) {
        boolean could = refusal(autopilot, pilot, radio) == null;
        boolean now = pilot.rolling();
        if (could != ready || now != rolling) {
            ready = could;
            rolling = now;
            autopilot.changed();
        }
    }

    /** Why she may not spin now, her feet so high over the ground, in blocks; null when she may. */
    @Nullable
    private static Answer refusal(AutopilotBlockEntity autopilot, FreighterPilot pilot, double radio) {
        if (!SkylinersConfig.FREIGHTER_SPIN.get()) {
            return Answer.SWITCHED_OFF;
        }
        if (pilot.rolling()) {
            return Answer.BUSY;
        }
        if (!FreighterJump.flyingAlong(autopilot)) {
            return Answer.NOT_FLYING;
        }
        return radio < LEAST_HEIGHT ? Answer.TOO_LOW : null;
    }

    /** Where it stands as her console shows it, on either side. */
    Stage stage() {
        if (rolling) {
            return Stage.ROLLING;
        }
        return ready ? Stage.READY : Stage.UNAVAILABLE;
    }

    /** Where her roar is heard: at her middle, or at her control head where that is not known. */
    private static Vec3 heardAt(AutopilotBlockEntity autopilot) {
        Vec3 middle = FreighterFlight.middleOf(autopilot);
        return middle == null ? Vec3.atCenterOf(autopilot.getBlockPos()) : middle;
    }

    /** What the clients are shown of it, with her autopilot's other readings. */
    void saveShown(CompoundTag tag) {
        tag.putBoolean("FreighterSpinRolling", rolling);
        tag.putBoolean("FreighterSpinReady", ready);
    }

    /** What the clients are shown of it, read back; a missing key as never rolled. */
    void loadShown(CompoundTag tag) {
        rolling = tag.getBoolean("FreighterSpinRolling");
        ready = tag.getBoolean("FreighterSpinReady");
    }
}
