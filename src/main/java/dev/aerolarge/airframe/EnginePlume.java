package dev.aerolarge.airframe;

/**
 * What the engines leave behind them, as Create Propulsion's creative thruster draws it: plasma flames, an ion glow, a
 * smoke plume, or nothing (an airliner's engines show none; the engine's own glow and its contrail are drawn anyway).
 * In the thruster's own order, which its data keeps as a number (under "plumeType").
 */
public enum EnginePlume {
    PLASMA, ION, PLUME, NONE;

    /** The number the thruster's data keeps this plume as. */
    int saved() {
        return ordinal();
    }
}
