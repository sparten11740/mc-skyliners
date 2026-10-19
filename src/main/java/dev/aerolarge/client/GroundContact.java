package dev.aerolarge.client;

/**
 * Whether an assembled airliner stands on its wheels, judged from how high its middle is above the ground: the height
 * its middle rests at is learnt whenever it stands still, and it counts as on the ground while it is within a block of
 * that. Until it has been seen standing still once, it counts as in the air. One for each thing watching an airliner:
 * the camera shake for the one the player rides, and each passenger screen for its own.
 */
final class GroundContact {
    /** Slower than this across the ground and than this up or down, in blocks a second, the airliner stands still. */
    private static final double STILL_SPEED = 0.5;
    private static final double STILL_CLIMB = 0.3;
    /** The airliner is on its wheels while it sits within this many blocks of the height it rests at on the ground. */
    private static final double ON_GROUND = 1.0;

    /** How far above the ground the airliner's middle sits when it stands on its wheels; not known until it has. */
    private double restHeight = Double.NaN;

    /** Takes in where the airliner is now: whether it is on the ground. */
    boolean onGround(SableClient.Ride ride) {
        double speed = Math.hypot(ride.velocity().x, ride.velocity().z);
        if (speed < STILL_SPEED && Math.abs(ride.velocity().y) < STILL_CLIMB) {
            restHeight = ride.heightAboveGround();
        }
        return !Double.isNaN(restHeight) && ride.heightAboveGround() < restHeight + ON_GROUND;
    }

    /** How high the airliner's wheels are above the ground: as high as its middle is above where it rests, once known. */
    double wheelHeight(SableClient.Ride ride) {
        return Double.isNaN(restHeight) ? ride.heightAboveGround() : Math.max(0, ride.heightAboveGround() - restHeight);
    }

    /** Forgets the height it rests at, for when the airliner being watched is gone. */
    void forget() {
        restHeight = Double.NaN;
    }
}
