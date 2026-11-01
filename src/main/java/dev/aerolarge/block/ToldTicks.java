package dev.aerolarge.block;

import javax.annotation.Nullable;
import net.minecraft.world.level.Level;

/**
 * What a client makes of the server's count of how far a moving part has come: a door's swing, a flight of stairs. It
 * counts the ticks itself as they pass, to draw the part moving evenly, and wherever the server runs behind it is ahead
 * of the server by the time word comes. Taking the server's count then would jump the part back, so a count that is
 * merely ahead is kept. The server's count is taken where the client has fallen behind, the part has turned round, or
 * on the server itself, loading it from the world.
 */
final class ToldTicks {
    private ToldTicks() {
    }

    /** The count to go on with, given the count so far, what the server says, and whether the count is going up. */
    static int count(@Nullable Level level, int own, int told, boolean rising) {
        boolean ahead = rising ? own >= told : own <= told;
        return level != null && level.isClientSide && ahead ? own : told;
    }
}
