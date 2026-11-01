package dev.skyliners.autopilot;

import net.minecraft.server.level.ServerLevel;

/**
 * Something that holds a zeppelin on the ground a moment as she takes off, before she rises (see
 * {@link ZeppelinPilot}): her burners rest and her engines stay stopped while it holds her.
 */
@FunctionalInterface
public interface TakeoffHold {
    /**
     * Whether it holds her now, asked each tick of her take-off from its first ({@code first} then) until none of
     * what may hold her does, with how high she floats over the ground, in blocks.
     */
    boolean holds(ServerLevel level, AutopilotBlockEntity autopilot, double radio, boolean first);
}
