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
import net.minecraft.util.RandomSource;
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
 * past what gravity and drag account for, and from then on it goes wherever it was knocked, keeping that speed.
 * A rocket cracks a rock as heavy as a giant's right through (see {@link #crack}): the piece broken off flies on as a
 * body of its own, a rock in its own right under a number of its own, and lands on her for its share of the whole
 * should it still meet her.
 * Once its weather is over a rock flies on until it is burst or out of sight. Nothing of them is saved: every body
 * left is taken out as its weather's rocks are let go, and any found with no weather flying it is swept away (see
 * {@link SpaceRocks}).
 */
final class RockBodies {
    /**
     * How near her middle a rock becomes a body, in blocks, for her laser cannon to shoot at, or nearer where Sable
     * shows bodies less far; how far past that a rock is let go; and at most how many of a bout's rocks are bodies at
     * once, the rest going on being drawn.
     */
    private static final double REAL_WITHIN = 240;
    private static final double LET_GO_PAST = 50;
    private static final int MOST_AT_ONCE = 24;
    /**
     * How far inside the distance Sable shows bodies to players a rock's body is let go of, in blocks: room for those
     * aboard, spread over her, and for the rock's own half width.
     */
    private static final double SHOWN_MARGIN = 30;
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
     * How fast a rock knocked near her drifts off her once its weather is over, in blocks a second: no faster than the
     * slowest rocks sweep past, out of her way and on out of sight within a minute and a half.
     */
    private static final double DRIFTS_OFF = 8;
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
    /**
     * A rocket cracking a rock: how heavy it must be at least, in Sable's units, a rock some 16 blocks across and more,
     * so only a giant and the big pieces of one crack; at most how far off its middle the crack runs, as a share of its
     * half width, for the piece broken off to be a quarter of it at the least; and how near its middle line a rocket
     * must meet it, in blocks, for the crack to run any way through its middle.
     */
    private static final double CRACKS_FROM = 500;
    private static final double CRACK_OFF_MIDDLE = 0.35;
    private static final double CRACK_CENTRED = 1;
    /**
     * How wide the crack through a cracked rock is, in blocks, its stone gone: wide enough for the two sides to part
     * clear of each other, as Sable holds two bodies pressed face to face together.
     */
    private static final double CRACK_WIDE = 3;
    /**
     * How fast the two sides of a cracked rock fly apart, in blocks a second, across the way the rocket went, the
     * lighter the faster: enough to send a giant's halves off to either side of her when it is cracked early; and how
     * fast each is set turning away from the other, in degrees a second.
     */
    private static final double APART = 20;
    private static final double TUMBLE = 25;
    /**
     * How long the two sides of a cracked rock are given to fly apart, in ticks, before any change of speed counts as
     * having met something.
     */
    private static final int CRACK_SETTLES = 5;
    /**
     * How far above a cracked rock the bodies of its two sides are laid, out of everyone's sight, in blocks, past
     * Sable's reach for showing bodies to players; and how far apart from each other, for them not to meet there.
     */
    private static final double LAID_ABOVE = 1000;
    private static final double LAID_APART = 100;
    /**
     * The number of a bout's first piece cracked off a rock: past every number of its rocks streaming by and of its
     * strikes, which {@code SpaceWeather} numbers from 10,000.
     */
    private static final int PIECE_NUMBERS = 20_000;

    /** A rock as it stands this tick, for it to be made a body of if it is near enough. */
    record Flight(int id, long seed, double size, Vec3 at, Vec3 velocity, Quaternionf turned, Vec3 spin) {
    }

    /** What is told of a rock's body by what owns the bout of weather. */
    interface Told {
        /** Something about a rock's body, for everyone shown the weather. */
        void real(RockReal told);

        /**
         * A rock's body shot through, by its number and the number of the rock it is or was cracked off, burst where it
         * was, along the way the last shot went.
         */
        void wornThrough(ServerLevel level, int id, int rock, Vec3 at, Vec3 way);
    }

    /** One rock as a body, or a piece cracked off one. */
    private static final class Body {
        final int id;
        /** The number of the rock it is, or was cracked off. */
        final int rock;
        final UUID body;
        /** How big it is, in blocks across; smaller once cracked. */
        double size;
        /** How heavy its whole rock was as it was made, in Sable's units, for how much of that it is. */
        double wholeMass;
        /**
         * How heavy it was as it was made or cracked off, in Sable's units, for how much of it shots have worn away.
         */
        double ownMass;
        Vec3 velocity;
        /** Whether it has been shot, or knocked by anything. Once it has, it goes where it was knocked. */
        boolean struck;
        /**
         * Whether its two sides are being laid as bodies of their own, to crack it; and how many ticks are left of its
         * settling after a crack, its speed changing by that rather than by anything it met.
         */
        boolean cracking;
        int settling;
        /** Whether it met her since last asked. */
        boolean metHer;
        /** How fast it was moving just before it met her, in blocks a second. */
        Vec3 metAt = Vec3.ZERO;
        /** Whether it has landed on her, which it does but once. */
        boolean landed;
        /** The way the last shot at it went. */
        Vec3 shotWay = Vec3.ZERO;
        /** Where its middle was when last looked at, for bursting there should it have gone since. */
        Vec3 lastCentre;

        Body(int id, int rock, UUID body, double size, Vec3 velocity) {
            this.id = id;
            this.rock = rock;
            this.body = body;
            this.size = size;
            this.velocity = velocity;
        }

        /** What its clients are told of it, as it stands now. */
        RockReal told(long startedAt, RockReal.Stage stage) {
            return new RockReal(startedAt, id, body, stage, (float) size);
        }

        /**
         * One side of it once cracked, as a body of its own under a number: so big across, so heavy and moving so fast,
         * still of the same rock, knocked, and settling after the crack.
         */
        Body side(int number, UUID side, double across, double mass, Vec3 moving) {
            Body made = new Body(number, rock, side, across, moving);
            made.wholeMass = wholeMass;
            made.ownMass = mass;
            made.struck = true;
            made.landed = landed;
            made.shotWay = shotWay;
            made.settling = CRACK_SETTLES;
            return made;
        }
    }

    /**
     * A rock being cracked: its body, and its two sides being laid as bodies of their own, to take its place at once
     * when both are laid; which way across the rocket's way the piece broken off goes, and the way the rocket went.
     */
    private record Crack(Body body, SableFlight.Halves halves, SableFlight.RockMaking kept, SableFlight.RockMaking broken, Vec3 out,
                         Vec3 along) {
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
    private final List<Crack> cracks = new ArrayList<>();
    /** The bodies found to be no piece of a rock, its ship among them, not to be looked over again. */
    private final Set<UUID> notRock = new HashSet<>();
    /** How many blocks may still be laid this tick. */
    private int laidLeft;
    /** The number the next piece cracked off a rock is given. */
    private int nextPiece = PIECE_NUMBERS;
    /** For the play-test: how far from her middle the farthest rock made a body was as it began to be made. */
    private double farthestMade;
    /** Whether its weather is over: from then on a rock knocked near her drifts off her rather than being held there. */
    private boolean over;

    RockBodies(Told told, long startedAt) {
        this.told = told;
        this.startedAt = startedAt;
    }

    /** How near her a rock must be for its shape to start being worked out, at a speed in blocks a second. */
    private static double shapedWithin(double speed) {
        return REAL_WITHIN + speed * SHAPED_AHEAD;
    }

    /**
     * How far from her middle a rock is let go of, in blocks: where everyone near her can still be shown its body,
     * whose force-load ticket keeps it flying wherever it goes, however little of the world the server shows.
     */
    private static double letGoBeyond() {
        return Math.min(REAL_WITHIN + LET_GO_PAST, SableFlight.bodiesShownWithin() - SHOWN_MARGIN);
    }

    /**
     * How near her a rock becomes a body, in blocks from her middle; none where Sable shows bodies so short a way off
     * that a rock could not be made clear of her, as it then goes on being drawn. Her reach is how far she spreads from
     * her middle.
     */
    private static double realWithin(double herReach) {
        double limit = letGoBeyond() - LET_GO_PAST;
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
        if (flight.at().distanceTo(herMiddle) >= realWithin(herReach) || bodies.size() + making.size() >= MOST_AT_ONCE) {
            return;
        }
        CompletableFuture<Map<BlockPos, BlockState>> shape = shaping.get(flight.id());
        if (shape.isCompletedExceptionally()) {
            LOGGER.warn("A space rock's shape could not be worked out, so it is only drawn");
            shaping.remove(flight.id());
            done.add(flight.id());
        } else if (shape.isDone()) {
            farthestMade = Math.max(farthestMade, flight.at().distanceTo(herMiddle));
            start(level, flight, shape.join());
        }
    }

    /** How far from her middle the farthest of the rocks made bodies was as it began to be made, in blocks. */
    double farthestMade() {
        return farthestMade;
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
        crackOn(level);
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
            told.real(body.told(startedAt, RockReal.Stage.BODY));
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
        Body made = new Body(flight.id(), flight.id(), body, flight.size(), flight.velocity());
        made.wholeMass = SableFlight.massOf(level, body);
        made.ownMass = made.wholeMass;
        bodies.put(flight.id(), made);
        told.real(made.told(startedAt, RockReal.Stage.BODY));
    }

    /**
     * A rock coasts a tick, as nothing out there would slow it; whether it is still there. It is given back the speed
     * it had, whatever Sable's gravity and drag took off it; what jolts it past that knocks it, and from then on it
     * keeps the speed it was knocked to. Knocked near her, it met her, unless it was just cracked; and once knocked,
     * near her it is only held up against gravity, as given back its speed pressed against her it would push her
     * along like a thruster, until its weather is over, when it drifts off her instead, to fly on out of sight.
     */
    private boolean coast(ServerLevel level, AABB herBox, Body body) {
        Vec3 middle = SableFlight.rockCentre(level, body.body);
        if (middle == null) {
            vanished(level, body);
            return false;
        }
        body.lastCentre = middle;
        Vec3 velocity = SableFlight.rockVelocity(level, body.body);
        if (body.settling > 0) {
            body.settling--;
            body.velocity = velocity;
        } else if (velocity.distanceTo(body.velocity) > STRUCK_BY) {
            body.struck = true;
            met(body, herBox, middle);
            body.velocity = velocity;
        }
        if (body.struck && nearHer(herBox, body, middle)) {
            if (!over) {
                body.velocity = velocity;
                return SableFlight.holdUpRock(level, body.body);
            }
            body.velocity = offHer(herBox, middle);
        }
        SableFlight.setRockVelocity(level, body.body, body.velocity);
        return true;
    }

    /** How a rock near her drifts off her, its middle somewhere in the world: straight away from hers, slowly. */
    private static Vec3 offHer(AABB herBox, Vec3 middle) {
        Vec3 away = middle.subtract(herBox.getCenter());
        return (away.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : away.normalize()).scale(DRIFTS_OFF);
    }

    /** Whether a rock, its middle somewhere in the world, is near enough her box to be touching her. */
    private static boolean nearHer(AABB herBox, Body body, Vec3 middle) {
        return herBox.inflate(body.size / 2 + MET_HER_WITHIN).contains(middle);
    }

    /** A struck rock that has not landed taken to have met her if it was struck near her, moving as it was before. */
    private static void met(Body body, AABB herBox, Vec3 middle) {
        if (!body.landed && !body.metHer && nearHer(herBox, body, middle)) {
            body.metHer = true;
            body.metAt = body.velocity;
        }
    }

    /**
     * A rock's body found gone, emptied at a stroke by a shot or taken away by Sable: never made again, and burst where
     * it last was if it had been shot, or simply no longer drawn.
     */
    private void vanished(ServerLevel level, Body body) {
        done.add(body.id);
        if (body.struck && body.lastCentre != null) {
            told.wornThrough(level, body.id, body.rock, body.lastCentre, body.shotWay);
        } else {
            told.real(body.told(startedAt, RockReal.Stage.GONE));
        }
    }

    /**
     * Whether a rock is still there after what the shots have left of it; one shot through bursts, its last blocks with
     * it.
     */
    private boolean wearing(ServerLevel level, Body body) {
        double mass = SableFlight.massOf(level, body.body);
        if (!body.struck || !(body.ownMass > 0) || Double.isNaN(mass) || mass > body.ownMass * WORN_THROUGH_SHARE) {
            return true;
        }
        Vec3 middle = SableFlight.rockCentre(level, body.body);
        SableFlight.removeRock(level, body.body);
        done.add(body.id);
        if (middle != null) {
            told.wornThrough(level, body.id, body.rock, middle, body.shotWay);
        }
        return false;
    }

    /** Whether a rock is well past her and let go of: drawn again along its way if nothing has touched it, or gone. */
    private boolean letGo(ServerLevel level, Vec3 herMiddle, Body body) {
        Vec3 middle = SableFlight.rockCentre(level, body.body);
        if (middle != null && middle.distanceTo(herMiddle) < letGoBeyond()) {
            return false;
        }
        SableFlight.removeRock(level, body.body);
        done.add(body.id);
        told.real(body.told(startedAt, body.struck ? RockReal.Stage.GONE : RockReal.Stage.DRAWN));
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
        boolean over = middle == null || now >= piece.lastsUntil() || middle.distanceTo(herMiddle) > letGoBeyond();
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
        for (Crack crack : cracks) {
            all.add(crack.kept().id());
            all.add(crack.broken().id());
        }
        return all;
    }

    /** Whether a rock, by its number, is a body now, or any piece cracked off it is. */
    boolean real(int rock) {
        for (Body body : bodies.values()) {
            if (body.rock == rock) {
                return true;
            }
        }
        return false;
    }

    /**
     * A body of a rock, by the rock's number, the rock itself or a piece cracked off it, that met her since last asked
     * and has not landed on her before: it lands now. Its number; none for none.
     */
    @Nullable
    Integer landing(int rock) {
        for (Body body : bodies.values()) {
            if (body.rock == rock && body.metHer && !body.landed) {
                body.metHer = false;
                body.landed = true;
                return body.id;
            }
        }
        return null;
    }

    /** How fast a body, by its number, was moving just before it last met her, in blocks a second; still for none. */
    Vec3 metAt(int id) {
        Body body = bodies.get(id);
        return body == null ? Vec3.ZERO : body.metAt;
    }

    /** Whether every body of a rock, by the rock's number, has landed on her. */
    boolean allLanded(int rock) {
        for (Body body : bodies.values()) {
            if (body.rock == rock && !body.landed) {
                return false;
            }
        }
        return true;
    }

    /** The number of the rock a body is, or was cracked off, by the body's number; its own for none. */
    int rockOf(int id) {
        Body body = bodies.get(id);
        return body == null ? id : body.rock;
    }

    /**
     * How much of its whole rock a body, by its number, is now, as a share, what shots have worn off it and the pieces
     * cracked off it gone; all of it for one that is not a body.
     */
    double massShare(ServerLevel level, int id) {
        Body body = bodies.get(id);
        double mass = body == null ? Double.NaN : SableFlight.massOf(level, body.body);
        return Double.isNaN(mass) || !(body.wholeMass > 0) ? 1 : Mth.clamp(mass / body.wholeMass, 0, 1);
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

    /**
     * A rocket going off against a rock's body at a point in the world, flying along a way: a rock heavy enough cracks
     * right through, along the rocket's way and through where it met it, though never too far off its middle. Its two
     * sides are laid as bodies of their own out of sight, a tick's worth of blocks at a time, and take its place the
     * moment both are laid (see {@link #split}): moving its own blocks would stall the server, a giant's for most of a
     * second. Whether it began cracking.
     */
    boolean crack(ServerLevel level, UUID rock, Vec3 at, Vec3 way, RandomSource random) {
        Body body = byBody(rock);
        Vec3 middle = SableFlight.rockCentre(level, rock);
        double mass = SableFlight.massOf(level, rock);
        if (body == null || body.cracking || middle == null || !(mass >= CRACKS_FROM)) {
            return false;
        }
        Vec3 along = way.normalize();
        Vec3 off = at.subtract(middle);
        Vec3 aside = off.subtract(along.scale(off.dot(along)));
        Vec3 across = aside.length() > CRACK_CENTRED ? aside.normalize() : anyAcross(along, random);
        Vec3 through = middle.add(across.scale(Math.min(aside.length(), body.size / 2 * CRACK_OFF_MIDDLE)));
        SableFlight.Halves halves = SableFlight.rockHalves(level, rock, through, across, CRACK_WIDE);
        Vec3 laidAt = middle.add(0, LAID_ABOVE, 0);
        SableFlight.RockMaking kept = halves == null ? null : SableFlight.makeRock(level, laidAt, halves.kept());
        SableFlight.RockMaking broken = kept == null ? null : SableFlight.makeRock(level, laidAt.add(LAID_APART, 0, 0), halves.broken());
        if (broken == null) {
            if (kept != null) {
                SableFlight.removeRock(level, kept.id());
            }
            return false;
        }
        kept.lay(1);
        broken.lay(1);
        body.cracking = true;
        cracks.add(new Crack(body, halves, kept, broken, across.scale(halves.side()), along));
        return true;
    }

    /** The rocks being cracked laid on, out of what is left of a tick's worth of blocks; those laid split. */
    private void crackOn(ServerLevel level) {
        Iterator<Crack> each = cracks.iterator();
        while (each.hasNext()) {
            Crack crack = each.next();
            laidLeft -= crack.kept().lay(Math.max(0, laidLeft));
            laidLeft -= crack.broken().lay(Math.max(0, laidLeft));
            if (crack.kept().laid() && crack.broken().laid()) {
                each.remove();
                split(level, crack);
            }
        }
    }

    /**
     * A rock cracked: its body taken away, and its two sides set flying in its place, each where its blocks were,
     * turned and moving as it did, then sent apart across the rocket's way, the lighter the faster, each turning away
     * from the other. Should the rock have gone meanwhile, its sides go too.
     */
    private void split(ServerLevel level, Crack crack) {
        Body body = crack.body();
        Vec3 middle = SableFlight.rockCentre(level, body.body);
        Quaterniond turned = SableFlight.rockTurned(level, body.body);
        if (bodies.get(body.id) != body || middle == null || turned == null) {
            SableFlight.removeRock(level, crack.kept().id());
            SableFlight.removeRock(level, crack.broken().id());
            return;
        }
        Vec3 moving = SableFlight.rockVelocity(level, body.body);
        Vec3 spin = SableFlight.rockSpin(level, body.body);
        Vec3 keptAt = SableFlight.toWorld(level, Vec3.atLowerCornerOf(crack.halves().keptMiddle()));
        Vec3 brokenAt = SableFlight.toWorld(level, Vec3.atLowerCornerOf(crack.halves().brokenMiddle()));
        double brokenShare = crack.halves().broken().size() / (double) (crack.halves().broken().size() + crack.halves().kept().size());
        Vec3 tumble = crack.out().cross(crack.along()).normalize().scale(Math.toRadians(TUMBLE));
        Vec3 keptMoving = moving.add(spin.cross(keptAt.subtract(middle))).add(crack.out().scale(-APART * brokenShare));
        Vec3 brokenMoving = moving.add(spin.cross(brokenAt.subtract(middle))).add(crack.out().scale(APART * (1 - brokenShare)));
        double mass = SableFlight.massOf(level, body.body);
        SableFlight.removeRock(level, body.body);
        UUID kept = crack.kept().release(keptAt, turned, keptMoving, spin.subtract(tumble));
        UUID broken = crack.broken().release(brokenAt, turned, brokenMoving, spin.add(tumble));
        bodies.remove(body.id);
        if (kept == null && broken == null) {
            done.add(body.id);
            told.real(body.told(startedAt, RockReal.Stage.GONE));
        }
        if (kept != null) {
            sideMade(body.side(body.id, kept, crack.halves().keptSize(), mass * (1 - brokenShare), keptMoving));
        }
        if (broken != null) {
            sideMade(body.side(nextPiece++, broken, crack.halves().brokenSize(), mass * brokenShare, brokenMoving));
        }
    }

    /** A side of a cracked rock flying as a body of its own, its clients told so. */
    private void sideMade(Body side) {
        bodies.put(side.id, side);
        told.real(side.told(startedAt, RockReal.Stage.BODY));
    }

    /** Some way square to a way of length 1, picked at random, of length 1. */
    private static Vec3 anyAcross(Vec3 along, RandomSource random) {
        Vec3 side = Math.abs(along.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 first = along.cross(side).normalize();
        Vec3 second = along.cross(first);
        double turned = random.nextDouble() * Mth.TWO_PI;
        return first.scale(Math.cos(turned)).add(second.scale(Math.sin(turned)));
    }

    /** The rock, or piece of one, that is a body by its id; none for none. */
    @Nullable
    private Body byBody(UUID rock) {
        for (Body body : bodies.values()) {
            if (body.body.equals(rock)) {
                return body;
            }
        }
        return null;
    }

    /** A shot at a rock's body, along a way: it goes where it is knocked from now on. */
    void shot(UUID rock, Vec3 way) {
        Body body = byBody(rock);
        if (body != null) {
            body.struck = true;
            body.shotWay = way;
        }
    }

    /** Its weather over: a rock knocked near her drifts off her from now on. */
    void over() {
        over = true;
    }

    /** Whether any of it is left: a rock's body flying or being laid or cracked, or a piece split off one. */
    boolean any() {
        return !bodies.isEmpty() || !making.isEmpty() || !cracks.isEmpty() || !pieces.isEmpty();
    }

    /**
     * All of it out of the world: the weather's rocks are let go of, each body still flying told gone, for its clients
     * to draw it on as it went rather than lose it where it is.
     */
    void clear(ServerLevel level) {
        bodies.values().forEach(body -> told.real(body.told(startedAt, RockReal.Stage.GONE)));
        SableFlight.rockPieces(level, ids(), notRock).forEach(piece -> SableFlight.removeRock(level, piece));
        ids().forEach(rock -> SableFlight.removeRock(level, rock));
        shaping.values().forEach(shape -> shape.cancel(false));
        bodies.clear();
        pieces.clear();
        making.clear();
        cracks.clear();
        shaping.clear();
        done.clear();
    }
}
