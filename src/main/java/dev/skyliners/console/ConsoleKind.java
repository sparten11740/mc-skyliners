package dev.skyliners.console;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * What a console module of the freighter's cockpit shows and works (see {@link ConsoleBlock}): each is a block of its
 * own, so a player can set any of them up anywhere, and side by side round her pilots' seats they make her console.
 */
public enum ConsoleKind implements StringRepresentable {
    /** Her speed, height, climb and heading, each with its own picture and a bar; lamps along its desk. */
    READOUTS(5),
    /** A round scope with its beam sweeping round, her pin and whoever is near as blips; knobs along its desk. */
    SCANNER(3),
    /** Gauges of her lift thrusters, her mains, her boost and her jump drive; levers along its desk. */
    ENGINES(4),
    /** A bank of chunky toggles, each with its lamp, that click up and down; its screen a grid of lit squares. */
    SWITCHES(5),
    /** A big red push-button under a flame picture that fires her mains flat out a while (see her autopilot's boost). */
    BOOST(1);

    /** How many controls stand in a row along its desk: toggles, knobs, levers, or its one big button. */
    private final int controls;

    ConsoleKind(int controls) {
        this.controls = controls;
    }

    /** How many controls stand in a row along its desk. */
    public int controls() {
        return controls;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
