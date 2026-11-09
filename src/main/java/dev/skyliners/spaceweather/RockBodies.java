package dev.skyliners.spaceweather;

import com.mojang.logging.LogUtils;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.slf4j.Logger;

/**
 * The rocks of one bout of space weather that have come close enough to her to be real: each a flying body of blocks of
 * its own (see {@link RockCells}), through Sable, so it stops at her hull, breaks where it is shot, and sheds whatever
 * the shots leave no longer joined on. Far off a rock is only drawn, along a way every client works out for itself (see
 * {@link RockField}); it becomes a body as it comes within {@link #REAL_WITHIN} of her, its shape worked out beforehand
 * away from the server's thread, and goes back to being drawn once well past her.
 *
 * <p>Out there nothing would slow a rock, so each tick it is given back the speed it had. What strikes one jolts it
 * past what gravity and drag account for, and from then on it goes wherever it was knocked. Nothing of them
 * is saved: every body is taken out as its weather ends, and any found with no weather flying it is swept away (see
 * {@link SpaceRocks}).
 */
final class RockBodies {
    /**
     * How near her middle a rock becomes a body, in blocks, or nearer where the server shows less of the world; how far
     * past it a rock is let go; and at most how many of a bout's rocks are bodies at once, the rest going on being
     * drawn.
     */
    private static final double REAL_WITHIN = 150;
    private static final double LET_GO_BEYOND = 230;
    private static final int MOST_AT_ONCE = 24;
    /** How far inside what the server shows a rock's body must lie, in blocks: a little under its view distance. */
    private static final double SHOWN_MARGIN = 24;
    /**
     * How many seconds of its flight ahead of becoming a body a rock's shape starts being worked out: a giant's can
     * take a few.
     */
    private static final double SHAPED_AHEAD = 5;
    /** At most how many blocks are laid into bodies being made in a tick, in all: a giant's take a good few ticks. */
    private static final int LAID_A_TICK = 5000;
    /** How far clear of her a rock must be able to be made, in blocks past how far she spreads from her middle. */
    private static final double ROOM_ROUND_HER = 30;
    /** How often the rocks that are bodies are told again, for those who have come near since, in ticks. */
    private static final int RETOLD_EVERY = 40;
    /**
     * How much a rock's speed must change between ticks to count as having been struck, in blocks a second: more than
     * Sable's gravity and drag alone take off it in a tick, about a block a second at the fastest.
     */
    private static final double STRUCK_BY = 2.5;
    /** How near her box a struck rock must be for what it met to be her, in blocks, past its own half width. */
    private static final double MET_HER_WITHIN = 8;
    /**
     * A rock worn down by shots: how much of its weight must be gone before the rest is taken to burst.
     */
    private static final double WORN_THROUGH_SHARE = 0.2;
    /**
     * The pieces split off a rock: how heavy at least one must be, in Sable's units (a stone block a quarter), or it is
     * only dust; and how long one goes on for, in ticks.
     */
    private static final double LIGHTEST_PIECE = 1.5;
    private static final int PIECE_LASTS = 300;

    /** A rock as it stands this tick, for it to be made a body of if it is near enough. */
    record Flight(int id, long seed, double size, Vec3 at, Vec3 velocity, Quaternionf turned, Vec3 spin) {
    }

    /** What is told of a rock's body by what owns the bout of weather. */
    interface Told {
        /** Something about a rock's body, for everyone shown the weather. */
        void real(RockReal told);

        /** A rock shot through, burst where it was, along the way the last shot went. */
        void wornThrough(ServerLevel level, int id, Vec3 at, Vec3 way);
    }

    /** One rock as a body. */
    private static final class Body {
        final int id;
        final UUID body;
        final double size;
        double firstMass;
        Vec3 velocity;
        /** Whether it has been shot, or knocked by anything. Once it has, it goes where it was knocked. */
        boolean struck;
        /** Whether it met her since last asked. */
        boolean metHer;
        /** The way the last shot at it went. */
        Vec3 shotWay = Vec3.ZERO;
        /** Where its middle was when last looked at, for bursting there should it have gone since. */
        Vec3 lastCentre;

        Body(int id, UUID body, double size, Vec3 velocity) {
            this.id = id;
            this.body = body;
            this.size = size;
            this.velocity = velocity;
        }
    }

    /** A rock's body being laid, block by block, until it is ready to fly. */
    private static final class Making {
        final SableFlight.RockMaking making;
        Flight flight;

        Making(SableFlight.RockMaking making, Flight flight) {
            this.making = making;
            this.flight = flight;
        }
    }

    /** A piece split off a rock, to last a while and go. */
    private record Piece(UUID body, long lastsUntil) {
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Told told;
    private final long startedAt;
    private final Map<Integer, Body> bodies = new HashMap<>();
    private final Map<Integer, CompletableFuture<Map<BlockPos, BlockState>>> shaping = new HashMap<>();
    private final Map<Integer, Making> making = new HashMap<>();
    /** Rocks that were bodies and are let go of, not to be made bodies again. */
    private final Set<Integer> done = new HashSet<>();
    private final List<Piece> pieces = new ArrayList<>();
    /** The bodies found to be no piece of a rock, its ship among them, not to be looked over again. */
    private final Set<UUID> notRock = new HashSet<>();
    /** How many blocks may still be laid this tick. */
    private int laidLeft;

    RockBodies(Told told, long startedAt) {
        this.told = told;
        this.startedAt = startedAt;
    }

    /** How near her a rock must be for its shape to start being worked out, at a speed in blocks a second. */
    private static double shapedWithin(double speed) {
        return REAL_WITHIN + speed * SHAPED_AHEAD;
    }

    /**
     * How near her a rock becomes a body, in blocks from her middle; none where the server shows too little of the
     * world for a rock to be made clear of her, as it then goes on being drawn. Her reach is how far she spreads from
     * her middle.
     */
    private static double realWithin(ServerLevel level, double herReach) {
        double limit = Math.min(REAL_WITHIN, level.getServer().getPlayerList().getViewDistance() * 16 - SHOWN_MARGIN);
        return limit > herReach + ROOM_ROUND_HER ? limit : 0;
    }

    /**
     * Whether a rock, by its number, is near enough to want looking at, so what is costly about it is worked out only
     * then.
     */
    boolean wanted(int id, Vec3 at, double speed, Vec3 herMiddle) {
        return !done.contains(id) && !bodies.containsKey(id) && at.distanceTo(herMiddle) < shapedWithin(speed);
    }

    /**
     * A rock on its way in: shaped if near enough, started as a body if nearer still, and kept up with while being
     * laid.
     */
    void approach(ServerLevel level, Vec3 herMiddle, double herReach, Flight flight) {
        Making laying = making.get(flight.id());
        if (laying != null) {
            laying.flight = flight;
            return;
        }
        if (!shaping.containsKey(flight.id())) {
            shaping.put(flight.id(), CompletableFuture.supplyAsync(() -> RockCells.of(flight.size(), flight.seed()), Util.backgroundExecutor()));
        }
        if (flight.at().distanceTo(herMiddle) >= realWithin(level, herReach) || bodies.size() + making.size() >= MOST_AT_ONCE) {
            return;
        }
        CompletableFuture<Map<BlockPos, BlockState>> shape = shaping.get(flight.id());
        if (shape.isCompletedExceptionally()) {
            LOGGER.warn("A space rock's shape could not be worked out, so it is only drawn");
            shaping.remove(flight.id());
            done.add(flight.id());
        } else if (shape.isDone()) {
            start(level, flight, shape.join());
        }
    }

    /** A rock's body started, its first blocks laid at once: Sable would take a body with none away next tick. */
    private void start(ServerLevel level, Flight flight, Map<BlockPos, BlockState> blocks) {
        shaping.remove(flight.id());
        SableFlight.RockMaking laying = SableFlight.makeRock(level, flight.at(), blocks);
        if (laying == null) {
            done.add(flight.id());
            return;
        }
        Making started = new Making(laying, flight);
        laidLeft -= laying.lay(Math.max(1, laidLeft));
        if (laying.laid()) {
            release(level, started);
        } else {
            making.put(flight.id(), started);
        }
    }

    /**
     * A tick: the bodies being laid laid on, and those done set flying; each flying one coasting; pieces split off them
     * taken up; and those well past her let go.
     */
    void tick(ServerLevel level, AABB herBox, Vec3 herMiddle, long now) {
        pieces(level, herMiddle, now);
        lay(level);
        Iterator<Body> each = bodies.values().iterator();
        while (each.hasNext()) {
            Body body = each.next();
            if (!coast(level, herBox, body) || !wearing(level, body) || letGo(level, herMiddle, body)) {
                each.remove();
            }
        }
        if (now % RETOLD_EVERY == 0) {
            retell();
        }
    }

    /** Every rock that is a body told again, for those who have come near since it was. */
    private void retell() {
        for (Body body : bodies.values()) {
            told.real(new RockReal(startedAt, body.id, body.body, RockReal.Stage.BODY));
        }
    }

    /**
     * The bodies being laid laid on, up to a tick's worth in all; those done set flying where they should be by now.
     */
    private void lay(ServerLevel level) {
        laidLeft = LAID_A_TICK;
        Iterator<Making> each = making.values().iterator();
        while (each.hasNext() && laidLeft > 0) {
            Making laying = each.next();
            laidLeft -= laying.making.lay(laidLeft);
            if (laying.making.laid()) {
                each.remove();
                release(level, laying);
            }
        }
    }

    private void release(ServerLevel level, Making laying) {
        Flight flight = laying.flight;
        UUID body = laying.making.release(flight.at(), new Quaterniond(flight.turned()), flight.velocity(), flight.spin());
        if (body == null) {
            done.add(flight.id());
            return;
        }
        Body made = new Body(flight.id(), body, flight.size(), flight.velocity());
        made.firstMass = SableFlight.massOf(level, body);
        bodies.put(flight.id(), made);
        told.real(new RockReal(startedAt, flight.id(), body, RockReal.Stage.BODY));
    }

    /**
     * A rock coasts a tick, as nothing out there would slow it; whether it is still there. While nothing has struck it,
     * it is simply given back the speed it had, whatever Sable's gravity and drag took off it; once it has been struck,
     * it goes where it was knocked, held up against gravity alone. It may have met her since last it was looked at.
     */
    private boolean coast(ServerLevel level, AABB herBox, Body body) {
        Vec3 middle = SableFlight.rockCentre(level, body.body);
        if (middle == null) {
            vanished(level, body);
            return false;
        }
        body.lastCentre = middle;
        Vec3 velocity = SableFlight.rockVelocity(level, body.body);
        if (velocity.distanceTo(body.velocity) > STRUCK_BY) {
            body.struck = true;
            body.metHer |= herBox.inflate(body.size / 2 + MET_HER_WITHIN).contains(middle);
        }
        if (body.struck) {
            body.velocity = velocity;
            return SableFlight.holdUpRock(level, body.body);
        }
        SableFlight.setRockVelocity(level, body.body, body.velocity);
        return true;
    }

    /**
     * A rock's body found gone, emptied at a stroke by a shot or taken away by Sable: never made again, and burst where
     * it last was if it had been shot, or simply no longer drawn.
     */
    private void vanished(ServerLevel level, Body body) {
        done.add(body.id);
        if (body.struck && body.lastCentre != null) {
            told.wornThrough(level, body.id, body.lastCentre, body.shotWay);
        } else {
            told.real(new RockReal(startedAt, body.id, body.body, RockReal.Stage.GONE));
        }
    }

    /**
     * Whether a rock is still there after what the shots have left of it; one shot through bursts, its last blocks with
     * it.
     */
    private boolean wearing(ServerLevel level, Body body) {
        double mass = SableFlight.massOf(level, body.body);
        if (!body.struck || !(body.firstMass > 0) || Double.isNaN(mass) || mass > body.firstMass * WORN_THROUGH_SHARE) {
            return true;
        }
        Vec3 middle = SableFlight.rockCentre(level, body.body);
        SableFlight.removeRock(level, body.body);
        done.add(body.id);
        if (middle != null) {
            told.wornThrough(level, body.id, middle, body.shotWay);
        }
        return false;
    }

    /** Whether a rock is well past her and let go of: drawn again along its way if nothing has touched it, or gone. */
    private boolean letGo(ServerLevel level, Vec3 herMiddle, Body body) {
        Vec3 middle = SableFlight.rockCentre(level, body.body);
        if (middle != null && middle.distanceTo(herMiddle) < LET_GO_BEYOND) {
            return false;
        }
        SableFlight.removeRock(level, body.body);
        done.add(body.id);
        told.real(new RockReal(startedAt, body.id, body.body, body.struck ? RockReal.Stage.GONE : RockReal.Stage.DRAWN));
        return true;
    }

    /** The pieces split off this tick taken up, or thrown away for dust if tiny; those long gone or far off dropped. */
    private void pieces(ServerLevel level, Vec3 herMiddle, long now) {
        for (UUID piece : SableFlight.rockPieces(level, ids(), notRock)) {
            if (SableFlight.massOf(level, piece) < LIGHTEST_PIECE) {
                SableFlight.removeRock(level, piece);
            } else {
                SableFlight.markRock(level, piece);
                pieces.add(new Piece(piece, now + PIECE_LASTS));
            }
        }
        pieces.removeIf(piece -> pieceOver(level, herMiddle, piece, now));
    }

    private boolean pieceOver(ServerLevel level, Vec3 herMiddle, Piece piece, long now) {
        Vec3 middle = SableFlight.rockCentre(level, piece.body());
        boolean over = middle == null || now >= piece.lastsUntil() || middle.distanceTo(herMiddle) > LET_GO_BEYOND;
        if (over) {
            SableFlight.removeRock(level, piece.body());
            return true;
        }
        SableFlight.holdUpRock(level, piece.body());
        return false;
    }

    /** Every body and piece this bout has. */
    Set<UUID> ids() {
        Set<UUID> all = new HashSet<>();
        for (Body body : bodies.values()) {
            all.add(body.body);
        }
        for (Piece piece : pieces) {
            all.add(piece.body());
        }
        for (Making laying : making.values()) {
            all.add(laying.making.id());
        }
        return all;
    }

    /** Whether a rock, by its number, is a body now. */
    boolean real(int id) {
        return bodies.containsKey(id);
    }

    /** Whether a rock met her since last asked. */
    boolean tookHer(int id) {
        Body body = bodies.get(id);
        if (body == null || !body.metHer) {
            return false;
        }
        body.metHer = false;
        return true;
    }

    /** How much of a rock's weight is left, as a share; all of it for one that is not a body. */
    double massShare(ServerLevel level, int id) {
        Body body = bodies.get(id);
        double mass = body == null ? Double.NaN : SableFlight.massOf(level, body.body);
        return Double.isNaN(mass) || !(body.firstMass > 0) ? 1 : Mth.clamp(mass / body.firstMass, 0, 1);
    }

    /** Where a rock's body is now, by its number, in the world; none for none. */
    @Nullable
    Vec3 centre(ServerLevel level, int id) {
        Body body = bodies.get(id);
        return body == null ? null : SableFlight.rockCentre(level, body.body);
    }

    /** How big a rock's body is, in blocks across, by its number; none for none. */
    double size(int id) {
        Body body = bodies.get(id);
        return body == null ? 0 : body.size;
    }

    /** How fast a rock's body moves, by its number, in blocks a second; still for none. */
    Vec3 velocity(int id) {
        Body body = bodies.get(id);
        return body == null ? Vec3.ZERO : body.velocity;
    }

    /** How many rocks are bodies now. */
    int count() {
        return bodies.size();
    }

    /** The numbers of the rocks that are bodies now. */
    Set<Integer> numbers() {
        return Set.copyOf(bodies.keySet());
    }

    /** The body of a rock, by its number; none for none. */
    @Nullable
    UUID body(int id) {
        Body body = bodies.get(id);
        return body == null ? null : body.body;
    }

    /**
     * A rock on its way in, kept coming at where it should strike her while nothing has knocked it: so fast, in blocks
     * a second, plus her own speed.
     */
    void aimAt(ServerLevel level, int id, Vec3 spot, double speed, Vec3 herVelocity) {
        Body body = bodies.get(id);
        if (body == null || body.struck) {
            return;
        }
        Vec3 middle = SableFlight.rockCentre(level, body.body);
        if (middle == null || spot.distanceToSqr(middle) < 1) {
            return;
        }
        Vec3 velocity = spot.subtract(middle).normalize().scale(speed).add(herVelocity);
        SableFlight.setRockVelocity(level, body.body, velocity);
        body.velocity = velocity;
    }

    /** A shot at a rock's body, along a way: it goes where it is knocked from now on. */
    void shot(UUID rock, Vec3 way) {
        for (Body body : bodies.values()) {
            if (body.body.equals(rock)) {
                body.struck = true;
                body.shotWay = way;
                return;
            }
        }
    }

    /** All of it out of the world: the weather is over. */
    void clear(ServerLevel level) {
        SableFlight.rockPieces(level, ids(), notRock).forEach(piece -> SableFlight.removeRock(level, piece));
        ids().forEach(rock -> SableFlight.removeRock(level, rock));
        shaping.values().forEach(shape -> shape.cancel(false));
        bodies.clear();
        pieces.clear();
        making.clear();
        shaping.clear();
        done.clear();
    }
}
