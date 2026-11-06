package dev.skyliners.autopilot;

import java.util.function.Function;

/**
 * What an autopilot flies: the airliner, on its wings and jets, the zeppelin, on its hot air and propellers (see
 * {@link ZeppelinPilot}), the rocket, standing on its engines (see {@link RocketPilot}), or the freighter, held up and
 * pushed along on her thrusters (see {@link FreighterPilot}). Each flies within speeds of its own, in blocks a second:
 * the airliner falls out of the sky below its slowest, while the others can stop and hover. Its speed dial shows green
 * up to where it cruises comfortably, amber faster and red near its fastest. What else it adds to its autopilot is its
 * {@link CraftFlight}.
 */
public enum Craft {
    AIRLINER(AutopilotBlockEntity.MIN_SPEED, AutopilotBlockEntity.MAX_SPEED, 60, 100, AirlinerFlight.class,
        airtime -> new AirlinerFlight()),
    ZEPPELIN(0, 25, 20, 24, ZeppelinFlight.class, airtime -> new ZeppelinFlight()),
    ROCKET(0, 30, 24, 28, RocketFlight.class, RocketFlight::new),
    FREIGHTER(0, 40, 30, 36, FreighterFlight.class, airtime -> new FreighterFlight());

    public final int minSpeed;
    public final int maxSpeed;
    /** Where its speed dial turns amber and where it turns red, in blocks a second. */
    public final int amberFrom;
    public final int redFrom;
    /**
     * What it adds to its autopilot (see {@link CraftFlight}): its kind, and how a fresh one is made, given the flight
     * deck's air time that its voices take their turns on.
     */
    private final Class<? extends CraftFlight> kind;
    private final Function<Airtime, CraftFlight> flight;

    Craft(int minSpeed, int maxSpeed, int amberFrom, int redFrom, Class<? extends CraftFlight> kind,
          Function<Airtime, CraftFlight> flight) {
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.amberFrom = amberFrom;
        this.redFrom = redFrom;
        this.kind = kind;
        this.flight = flight;
    }

    /** A fresh flight of this kind, wired to nothing yet, its voices taking their turns on the air time given. */
    CraftFlight flight(Airtime airtime) {
        return flight.apply(airtime);
    }

    /** The craft a kind of flight flies. */
    static Craft flying(Class<? extends CraftFlight> kind) {
        for (Craft craft : values()) {
            if (craft.kind == kind) {
                return craft;
            }
        }
        throw new IllegalArgumentException("No craft flies " + kind.getSimpleName());
    }
}
