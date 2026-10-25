package dev.aerolarge.airframe;

/**
 * What the engines show behind them while they run, as the server config's {@code engineFlames} says: a flame each
 * client draws itself from the engine, at next to no cost, with Fancy or Fabulous graphics (see the client's
 * {@code ExhaustFlame}); Create Propulsion's own plume, the plasma flames its creative thrusters trail, every puff of
 * which the server sends to every player near and each client draws, a good share of its frame in flight; or nothing,
 * as on a real airliner, whose engines only glow and leave contrails. Only the airliner's own thrusters are set, never
 * Create Propulsion's settings or thrusters built anywhere else.
 */
public enum EngineFlames {
    LIGHTWEIGHT_FLAME, PROPULSION_PLUME, NONE;

    /** Create Propulsion's creative thruster's plumes, as its data keeps them: plasma first, then ion, smoke and none. */
    private static final int PLASMA = 0;
    private static final int NO_PLUME = 3;

    /** The plume the engines' creative thrusters are built with, as their data keeps it (under "plumeType"). */
    int thrusterPlume() {
        return this == PROPULSION_PLUME ? PLASMA : NO_PLUME;
    }
}
