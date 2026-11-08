package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/**
 * The freighter's climb boost (see {@link FreighterFlight#climbBoost}), asked for from her console's climb button while
 * she flies along: her lift thrusters fire flat out and she climbs straight up far faster than she ever flies along,
 * holding her spot and her heading (see {@link FreighterPilot}), until she has gained {@link #GAIN} or reached the
 * highest her autopilot may be set to hold (see {@link AutopilotBlockEntity#ceiling}), whichever is nearer, so a few
 * presses take her up past the edge of space to where space weather blows. Her autopilot takes that height as the one
 * it holds, so it never pulls her back down,
 * and the boost rests a while before it fires again. Climbing straight up brings no new land into view, as flying along
 * does, so the server keeps up with her however fast she climbs. Her lift roars as it fires and her computer says so;
 * none with the server config's {@code freighter.climbBoost} off.
 */
public final class FreighterClimbBoost {
    /** How much height a climb boost gains, in blocks. */
    static final double GAIN = 300;
    /** The least height a climb boost must have left to gain to fire, in blocks: with less, she is at the top. */
    private static final double LEAST_GAIN = 20;
    /** How near the height it climbs to counts as there, in blocks. */
    private static final double THERE = 3;
    /** The longest a climb boost fires, in ticks, should she never get there; and how long it rests after, in ticks. */
    private static final int LONGEST_TICKS = 1200;
    public static final int REST_TICKS = 300;
    /** No rest under way. */
    private static final long NO_REST = Long.MIN_VALUE;
    /** How loud her lift's roar as it fires and its cutting out are, heard at her middle. */
    private static final float ROAR_VOLUME = 3;

    /** What asking her to climb comes to, each with its own word in the hotbar. */
    public enum Answer implements StringRepresentable {
        /** Her lift roars into it. */
        CLIMBING,
        /** It fires already. */
        BUSY,
        /** It is still resting after the last. */
        RESTING,
        /** She is not flying along: on the ground, lifting off, coming down or switched off. */
        NOT_FLYING,
        /** She is as high as her autopilot may hold her, as high as it takes her. */
        AT_TOP,
        /** The server's config has it switched off. */
        SWITCHED_OFF,
        /** There is no freighter to climb. */
        NO_SHIP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where her climb boost stands as her console shows it: not to be had, ready, firing or resting. */
    public enum Stage {
        UNAVAILABLE, READY, FIRING, RESTING
    }

    /** Her climb boost as a console shows it, and the share of its rest still to come, from 1 down to 0. */
    public record Shown(Stage stage, double left) {
        public static final Shown UNAVAILABLE = new Shown(Stage.UNAVAILABLE, 0);
    }

    /**
     * On the server and as the clients are shown it: whether it fires, when its rest ends, in game time, and whether
     * the server lets her climb so at all.
     */
    private boolean firing;
    private long restUntil = NO_REST;
    private boolean allowed;
    /** On the server: the height it climbs to, at her control head, and how many ticks it has fired. */
    private double to;
    private int ticks;

    /** Whether it fires, for her pilot to climb by. */
    boolean firing() {
        return firing;
    }

    /**
     * Asked to climb, on the server, her control head at a height: her lift roars into it and her autopilot is set to
     * hold the height it climbs to, or why not.
     */
    Answer start(ServerLevel level, AutopilotBlockEntity autopilot, double height) {
        if (!SkylinersConfig.FREIGHTER_CLIMB_BOOST.get()) {
            return Answer.SWITCHED_OFF;
        }
        if (firing) {
            return Answer.BUSY;
        }
        if (!FreighterJump.flyingAlong(autopilot)) {
            return Answer.NOT_FLYING;
        }
        if (restUntil != NO_REST && level.getGameTime() < restUntil) {
            return Answer.RESTING;
        }
        double top = Math.min(height + GAIN, autopilot.ceiling(level));
        if (top - height < LEAST_GAIN) {
            return Answer.AT_TOP;
        }
        firing = true;
        to = top;
        ticks = 0;
        autopilot.targetAltitude = Math.max(autopilot.targetAltitude, top);
        roar(level, autopilot, SkylinersRegistry.SPEED_BOOST.get());
        autopilot.changed();
        return Answer.CLIMBING;
    }

    /**
     * Once a tick of hers, on the server, her control head at a height: firing until she is there, has stopped flying
     * along, or has fired as long as it may, when her lift is heard cutting out and it rests; fired that long, her
     * autopilot holds where she got to. Whether the server lets her climb so is kept for her console.
     */
    void tick(ServerLevel level, AutopilotBlockEntity autopilot, double height) {
        boolean lets = SkylinersConfig.FREIGHTER_CLIMB_BOOST.get();
        if (lets != allowed) {
            allowed = lets;
            autopilot.changed();
        }
        if (!firing) {
            return;
        }
        ticks++;
        boolean there = height >= to - THERE;
        boolean tooLong = ticks > LONGEST_TICKS;
        if (!there && !tooLong && FreighterJump.flyingAlong(autopilot)) {
            return;
        }
        if (tooLong) {
            autopilot.targetAltitude = height;
        }
        firing = false;
        restUntil = level.getGameTime() + REST_TICKS;
        roar(level, autopilot, SkylinersRegistry.BOOSTER_CUTOUT.get());
        autopilot.changed();
    }

    /**
     * Where it stands at a game time, a client's eased one to draw it by, on either side: ready only while she flies
     * along, as only then does it fire.
     */
    Shown shown(double now, boolean flyingAlong) {
        if (!allowed) {
            return Shown.UNAVAILABLE;
        }
        if (firing) {
            return new Shown(Stage.FIRING, 1);
        }
        if (restUntil != NO_REST && now < restUntil) {
            return new Shown(Stage.RESTING, (restUntil - now) / REST_TICKS);
        }
        return flyingAlong ? new Shown(Stage.READY, 0) : Shown.UNAVAILABLE;
    }

    /** A sound of her lift, heard at her middle, or at her control head where that is not known. */
    private static void roar(ServerLevel level, AutopilotBlockEntity autopilot, SoundEvent sound) {
        Vec3 middle = FreighterFlight.middleOf(autopilot);
        Vec3 at = middle == null ? Vec3.atCenterOf(autopilot.getBlockPos()) : middle;
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, ROAR_VOLUME, 1);
    }

    /** What the clients are shown of it, with her autopilot's other readings. */
    void saveShown(CompoundTag tag) {
        tag.putBoolean("FreighterClimbFiring", firing);
        tag.putBoolean("FreighterClimbs", allowed);
        if (restUntil != NO_REST) {
            tag.putLong("FreighterClimbRestUntil", restUntil);
        }
    }

    /** What the clients are shown of it, read back; on a server, what was last shown, a missing key as never fired. */
    void loadShown(CompoundTag tag) {
        firing = tag.getBoolean("FreighterClimbFiring");
        allowed = tag.getBoolean("FreighterClimbs");
        restUntil = tag.contains("FreighterClimbRestUntil") ? tag.getLong("FreighterClimbRestUntil") : NO_REST;
    }
}
