package dev.skyliners.turret;

import net.minecraft.util.Mth;

/**
 * A heavy turret's turn, drawn on a client, as a laser cannon's barrels swing after its gunner's aim (see
 * {@link LaserCannonBlockEntity}): unlike a light head's easing it has weight, gathering speed a little at a time to
 * its fastest, slowing again as it nears the aim, and on a hard swing running a little past it before it settles back,
 * as heavy guns on hydraulics do. Kept in degrees, a tick at a time.
 */
final class HeavyTurn {
    /** How far past what it could stop in it lets itself run, as a share, for the little overshoot of a hard swing. */
    private static final float OVERRUN = 1.15F;

    private final float fastest;
    private final float gather;
    private final boolean allRound;
    /** Its turn now and a tick ago, in degrees, and how fast it is turning, in degrees a tick. */
    private float now;
    private float before;
    private float speed;

    /**
     * A turn as fast as so many degrees a tick at most, gathering or shedding at most so many degrees a tick each tick,
     * going the short way round to its aim if it may turn all the way round.
     */
    HeavyTurn(float fastest, float gather, boolean allRound) {
        this.fastest = fastest;
        this.gather = gather;
        this.allRound = allRound;
    }

    /** Each client tick, before it is turned: its turn now becomes its turn a tick ago. */
    void tick() {
        before = now;
    }

    /**
     * Turns it a tick's way toward an aim, in degrees: as fast as it could still stop in the way left, a little over,
     * never faster than its fastest nor than the whole way left in one tick, its speed changing at most so much a tick.
     */
    void toward(float aim) {
        float off = allRound ? Mth.wrapDegrees(aim - now) : aim - now;
        float stoppable = (float) Math.sqrt(2 * gather * Math.abs(off)) * OVERRUN;
        float wanted = Math.copySign(Math.min(fastest, Math.min(stoppable, Math.abs(off))), off);
        speed += Mth.clamp(wanted - speed, -gather, gather);
        now += speed;
    }

    /** Puts it at a turn, at rest, without turning there: where it stands as it is first drawn. */
    void snap(float turn) {
        now = turn;
        before = turn;
        speed = 0;
    }

    /** Its turn at a partial tick, in degrees. */
    float at(float partialTick) {
        return Mth.lerp(partialTick, before, now);
    }
}
