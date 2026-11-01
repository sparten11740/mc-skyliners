package dev.skyliners.rover;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The way the rocket's rover goes between its cradle in her cargo bay and the ground, and back, and the bay's door and
 * winch it goes by (see {@code entity.Rover} and {@code block.RoverBayBlockEntity}). Everything here is measured from
 * the door's hinge, the middle of its bottom edge on the skin's outer face: so many blocks out of her (along the way
 * the door faces) and up from the bay's floor. Both sides use it: the server moves the rover along it, and the client
 * draws the door, the boom and the winch's rope where the server has them.
 *
 * <p>Deploying, the door folds down outward into a platform level with the floor and a boom slides out over it from
 * inside the bay; the rover rolls out onto the platform, is hooked to the boom's trolley, lifted clear, run out past
 * the platform's end and lowered on the rope to the ground, where the hook lets go and is wound back up. Coming home,
 * it is the same the other way round, from wherever the rover stands when it is hooked, lifted straight up first and
 * then swung in, so it never drags through the ground.
 */
public final class RoverRoute {
    /** Half the rover's length, in blocks: how far its wheels reach ahead of and behind its middle. */
    public static final double ROVER_HALF = 1.5;
    /** How far inside the hinge the rover's middle stands in its cradle, in blocks: a block clear of the door. */
    public static final double CRADLE_IN = 3;
    /** How far out the door reaches folded down, in blocks: as far as it is tall shut, four rows. */
    public static final double PLATFORM = 4;
    /** How thick the door is, in blocks, which is how high its deck stands over the floor once it is folded down. */
    public static final double DECK = 0.25;
    /** How far the door folds down open, in degrees: a quarter turn, flat and level with the floor. */
    private static final double FOLDED = 90;
    /** Where the rover stands on the platform, out from the hinge, and how far it is lifted clear of it, in blocks. */
    public static final double ON_PLATFORM = 2;
    public static final double LIFT = 0.6;
    /** Where the rover hangs past the platform's end to be lowered, out from the hinge: its whole length clear. */
    public static final double OVERHANG = 5.5;
    /**
     * The boom: the height of its underside and its top over the floor, how far its tip reaches out with the door open,
     * and how far in it is drawn with the door shut, into its housing on the wall over the doorway inside, whose back
     * is at its root, in blocks; the door's opening is four rows tall, so it slides out under the doorway's top.
     */
    public static final double BOOM_BOTTOM = 3.55;
    public static final double BOOM_TOP = 3.85;
    public static final double BOOM_OUT = 6;
    public static final double BOOM_IN = -DECK;
    public static final double BOOM_ROOT = -0.9;
    /**
     * How far through the door's opening the boom waits before it slides out, as a share: until the door's top has
     * swung down clear under it.
     */
    private static final double BOOM_WAITS = 0.4;
    /** How high the trolley's pulley is over the floor, under the boom, where the rope comes down from, in blocks. */
    public static final double PULLEY = 3.2;
    /** How high the rover's slings meet over its feet, in blocks: a little over its deck. */
    public static final double SLINGS = 1.9;

    /**
     * Each stage of the way, in order: deploying, from waiting for the door to letting go of the rover on the ground;
     * coming home, from fetching it to rolling it back into its cradle. Those with a fixed length give it in ticks;
     * the others last as long as the door, the drop or the rope takes.
     */
    public enum Leg {
        WAIT_DOOR(0), ROLL_OUT(60), HOOK_ON(12), LIFT(15), TROLLEY_OUT(45), LOWER(0), UNHOOK(15),
        FETCH(0), HOIST(0), TROLLEY_IN(45), SET_DOWN(15), LET_GO(12), ROLL_IN(60);

        /** How many ticks it lasts; none for a leg that lasts as long as something else takes. */
        public final int ticks;

        Leg(int ticks) {
            this.ticks = ticks;
        }

        /** The leg after it on the way, or null after the last of the way out or home. */
        public Leg next() {
            return this == UNHOOK || this == ROLL_IN ? null : values()[ordinal() + 1];
        }

        /**
         * Whether the rover's own rope and trolley are drawn with it in this leg: from the moment it is hooked until
         * the hook is wound back up; otherwise the bay draws its trolley where it waits (see {@link #trolleyAtTip}).
         */
        public boolean hooked() {
            return this != WAIT_DOOR && this != ROLL_OUT && this != ROLL_IN;
        }

        /** Whether the trolley stands at the boom's tip in this leg, over the drop, rather than over the rover. */
        public boolean trolleyAtTip() {
            return this == LOWER || this == UNHOOK || this == FETCH || this == HOIST;
        }

        /** The leg saved under a name, or the first one for a name not known. */
        public static Leg named(String name, Leg fallback) {
            for (Leg leg : values()) {
                if (leg.name().equals(name)) {
                    return leg;
                }
            }
            return fallback;
        }
    }

    private RoverRoute() {
    }

    /** The door's hinge, the middle of its bottom edge on the skin's outer face, as its block stands. */
    public static Vec3 hinge(BlockPos door) {
        return Vec3.atBottomCenterOf(door);
    }

    /** A point so many blocks out of her and up from the floor, in front of the door: in her blocks, as the door is. */
    public static Vec3 at(BlockPos door, Direction out, double along, double up) {
        return hinge(door).add(out.getStepX() * along, up, out.getStepZ() * along);
    }

    /** Where the rover's feet stand in its cradle. */
    public static Vec3 cradle(BlockPos door, Direction out) {
        return at(door, out, -CRADLE_IN, 0);
    }

    /** How far out the boom's tip reaches with the door so far open, from 0 shut to 1 open, in blocks. */
    public static double boomTip(double open) {
        return Mth.lerp(Mth.clamp((open - BOOM_WAITS) / (1 - BOOM_WAITS), 0, 1), BOOM_IN, BOOM_OUT);
    }

    /** How far the door has folded down, in degrees from shut, with it so far open: eased at both ends. */
    public static double doorAngle(double open) {
        return FOLDED * ease(open);
    }

    /** A smooth start and stop over a share of the way, 0 to 1. */
    public static double ease(double share) {
        double t = Mth.clamp(share, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * Where the rover's feet are, so far through a fixed leg on the platform or in the bay (out from the hinge and up
     * from the floor), as a pair; nothing for the legs out over the drop, which go by the world (see the rover).
     */
    public static double[] onBoard(Leg leg, double share) {
        double t = ease(share);
        double lifted = DECK + LIFT;
        return switch (leg) {
            case ROLL_OUT -> new double[]{Mth.lerp(t, -CRADLE_IN, ON_PLATFORM), sill(Mth.lerp(t, -CRADLE_IN, ON_PLATFORM))};
            case HOOK_ON, LET_GO -> new double[]{ON_PLATFORM, DECK};
            case LIFT -> new double[]{ON_PLATFORM, Mth.lerp(t, DECK, lifted)};
            case SET_DOWN -> new double[]{ON_PLATFORM, Mth.lerp(t, lifted, DECK)};
            case TROLLEY_OUT -> new double[]{Mth.lerp(t, ON_PLATFORM, OVERHANG), lifted};
            case TROLLEY_IN -> new double[]{Mth.lerp(t, OVERHANG, ON_PLATFORM), lifted};
            case ROLL_IN -> new double[]{Mth.lerp(t, ON_PLATFORM, -CRADLE_IN), sill(Mth.lerp(t, ON_PLATFORM, -CRADLE_IN))};
            default -> null;
        };
    }

    /**
     * How high the rover's feet are rolling over the door's sill, at so far out from the hinge: on the floor inside,
     * up over the lip of the deck as its front wheels reach the door, on the deck outside.
     */
    private static double sill(double along) {
        return DECK * ease((along + ROVER_HALF) / (2 * ROVER_HALF));
    }
}
