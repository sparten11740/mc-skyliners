package dev.skyliners.block;

import net.minecraft.util.Mth;

/**
 * A head turning toward where it is to look, about one axis, a tick's way at a time: a good part of the way left,
 * slowing as it comes round, and never faster than its motor turns; the turn a tick ago kept for drawing between ticks.
 * Kept on the client by a block whose head follows whoever is near, as the rocket's bartender's and the freighter's
 * deck robots' do. One that may turn all the way round goes the short way to where it looks.
 */
public final class HeadTurn {
    private final float ease;
    private final float step;
    private final boolean allRound;
    /** Its turn now and a tick ago, in degrees. */
    private float now;
    private float before;

    /**
     * A head turning so much of the way left each tick and at most so many degrees, and going the short way round to
     * where it looks if it may turn all the way round.
     */
    public HeadTurn(float ease, float step, boolean allRound) {
        this.ease = ease;
        this.step = step;
        this.allRound = allRound;
    }

    /** Each client tick, before it is turned: its turn now becomes its turn a tick ago. */
    public void tick() {
        before = now;
    }

    /** Turns it a tick's way toward a turn, in degrees. */
    public void toward(float wanted) {
        float off = allRound ? Mth.wrapDegrees(wanted - now) : wanted - now;
        now += Mth.clamp(off * ease, -step, step);
    }

    /** Puts it at a turn, in degrees, without turning there: where it stands as it is first drawn. */
    public void snap(float turn) {
        now = turn;
        before = turn;
    }

    /** Its turn at a partial tick, in degrees. */
    public float at(float partialTick) {
        return Mth.lerp(partialTick, before, now);
    }
}
