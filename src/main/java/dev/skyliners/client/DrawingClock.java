package dev.skyliners.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The clocks what moves is drawn by. The level's game time will not do for that: the server sets a client's copy of it
 * about once a second, so wherever the server runs behind, anything turned or slid by it snaps back. Held in a float, a
 * world's long-running time also moves in steps, and the older the world the bigger.
 *
 * <p>What only moves for show goes by {@link #ticks}, or for what flips or blinks {@link #wholeTicks}: the client's own
 * count, in which the server has no part, and which starts small enough for a float. What has to keep time with a
 * moment the server named, or look the same to everyone, goes by {@link #gameTime}: the game's time eased, keeping pace
 * with the server's and never stepping back. A check at a whole tick against a server's moment, which nobody sees move,
 * can go on reading the level's. All of them stop when the game is paused or frozen.
 */
public final class DrawingClock {
    /** What share of the way to the server's time the eased game time makes up each tick, on top of the tick itself. */
    private static final double CATCH_UP = 0.05;
    /**
     * How far from the server's time it may get before it is simply set right, in ticks: a new world, or time changed by
     * a command. A minute, past any stall of the server's: short of it the time waits or hurries, and never steps back.
     */
    private static final double SET_RIGHT_AT = 1200;

    private static long ticks;
    private static double gameBefore;
    private static double gameNow;

    private DrawingClock() {
    }

    /** Once a client tick: both clocks on a tick, the eased one gaining or losing on it but never running backward. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || !level.tickRateManager().runsNormally()) {
            return;
        }
        ticks++;
        double told = level.getGameTime();
        double behind = told - gameNow;
        if (Math.abs(behind) >= SET_RIGHT_AT) {
            gameBefore = told;
            gameNow = told;
            return;
        }
        gameBefore = gameNow;
        gameNow += 1 + Math.max(-1, behind * CATCH_UP);
    }

    /** The client's own count of ticks so far through this one, for what only moves for show. */
    public static double ticks(float partialTick) {
        return ticks + partialTick;
    }

    /** The client's own count of whole ticks, for what flips or blinks for show. */
    public static long wholeTicks() {
        return ticks;
    }

    /**
     * The game's time so far through this tick, eased so that it never steps back, for what keeps time with a moment
     * the server named or looks the same to everyone. Take the moment from it in a double: the result fits a float, the
     * time itself does not.
     */
    public static double gameTime(float partialTick) {
        return gameBefore + (gameNow - gameBefore) * partialTick;
    }
}
