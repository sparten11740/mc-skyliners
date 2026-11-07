package dev.skyliners.impact;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What is heard as a ship runs into something: the ground, a mountainside, another ship. Each tick of an assembled
 * ship her autopilot hands this her body as a whole (see {@link SableFlight#body}). A change in how fast she moves
 * greater in one tick than her engines, her lift and gravity ever make is something pushing back on her, and that
 * change summed over the ticks it lasts is how hard she hit. A hard hit crunches with a boom, louder and lower the
 * harder, jolts the view of everyone aboard and leaves her hull groaning a moment after; a lighter one thuds; and
 * while she slides on along what she hit, slowed hard across the ground, she grinds. Each is heard where she struck,
 * by everyone near, and by everyone aboard from that way however far along her they stand. At most one hit is heard
 * every {@link #QUIET_TICKS}, so a ship bouncing does not rattle them off. Off with the server setting "impactSounds".
 */
public final class Impacts {
    private static final double TICKS = SharedConstants.TICKS_PER_SECOND;
    /**
     * The least change in her speed in one tick that something pushing back on her makes, in blocks a second, gravity
     * taken out: more than her lift and engines ever change it by, an air pocket's drop or an airliner's brakes on her
     * wheels, the hardest of them, at up to two blocks a second a tick rolling out.
     */
    private static final double CONTACT = 2.5;
    /**
     * How hard a hit is, as all the speed it took off her or turned aside, in blocks a second: from a bump, which
     * thuds; from a hard hit, which crunches; and the hardest, heard loudest and lowest. A landing, set down at a block
     * or two a second, is neither.
     */
    private static final double BUMP = 4;
    private static final double HARD = 10;
    private static final double SMASH = 30;
    /** The longest one hit is summed over before it is heard, in ticks: grinding on, she is heard hitting again. */
    private static final int HIT_LONGEST = 4;
    /** After a hit is heard, how long before another is, in ticks. */
    private static final int QUIET_TICKS = 10;
    /**
     * How far each sound carries at its plainest, in blocks, as tools/generate_resources.mjs sets it (IMPACT_SOUNDS); a
     * hit played louder than 1 carries that much farther.
     */
    private static final double CRUNCH_HEARD = 96;
    private static final double THUD_HEARD = 32;
    private static final double SCRAPE_HEARD = 64;
    private static final double GROAN_HEARD = 48;
    /** A hard hit's crunch: how loud from {@link #HARD} to {@link #SMASH}, past 1 carrying farther, and its pitch. */
    private static final float CRUNCH_LOUD = 1;
    private static final float CRUNCH_LOUDEST = 2.5F;
    private static final float CRUNCH_HIGH = 1;
    private static final float CRUNCH_LOW = 0.7F;
    /** A bump's thud: how loud from {@link #BUMP} to {@link #HARD}, and its pitch. */
    private static final float THUD_SOFT = 0.5F;
    private static final float THUD_LOUD = 1;
    private static final float THUD_HIGH = 1.15F;
    private static final float THUD_LOW = 0.95F;
    /**
     * How long after a hard hit her hull groans, in ticks, as loud as its crunch and carrying as far for it, and the
     * groan's pitch at most and at least.
     */
    private static final int GROAN_AFTER = 15;
    private static final float GROAN_HIGH = 0.9F;
    private static final float GROAN_LOW = 0.7F;
    /**
     * Sliding along: how fast across the ground she must move, in blocks a second, and how much speed she must lose a
     * tick, in blocks a second, smoothed over a few ticks (the share of each tick taken in), and for how many ticks, to
     * be grinding on something; and how soon after something last pushed back on her she must begin to, in ticks. A
     * freighter on her legs shoved along the ground loses about a third of a block a second each tick; an airliner's
     * brakes slow her harder still, but touch nothing.
     */
    private static final double SCRAPE_SPEED = 3;
    private static final double SCRAPE_DRAG = 0.25;
    private static final double DRAG_SMOOTHING = 0.3;
    private static final int SCRAPE_TICKS = 4;
    private static final int SCRAPE_WITHIN = 10;
    /**
     * How often the grind is played again while she slides, in ticks: its length less the fade it overlaps the next by,
     * in tools/generate_sounds.mjs.
     */
    private static final int SCRAPE_EVERY = 20;
    /** The grind's loudness and pitch at {@link #SCRAPE_SPEED} and from this speed up, in blocks a second. */
    private static final double SCRAPE_FASTEST = 25;
    private static final float SCRAPE_SOFT = 0.5F;
    private static final float SCRAPE_LOUD = 1;
    private static final float SCRAPE_LOW = 0.85F;
    private static final float SCRAPE_HIGH = 1.1F;
    /** How near anyone aboard hears what she struck at most, in blocks, from the way it was. */
    private static final double BY_EAR = 6;
    /** Moved this much farther in a tick than her speed takes her, in blocks, she was moved, not flown: a jump. */
    private static final double JUMPED = 4;
    /** Long after any game time: never. */
    private static final long NEVER = Long.MAX_VALUE;

    /** Her velocity and centre last tick; null before she has been seen. */
    private Vec3 lastVelocity;
    private Vec3 lastCentre;
    /** The hit she is taking, all the speed it has changed so far, and for how many ticks. */
    private Vec3 hit = Vec3.ZERO;
    private int hitTicks;
    /** Ticks before another hit is heard. */
    private int quiet;
    /**
     * Speed lost a tick across the ground, smoothed; the push she slides against, smoothed; how long she has slid; and
     * how long she may still begin to, after something last pushed back on her.
     */
    private double drag;
    private Vec3 push = Vec3.ZERO;
    private int sliding;
    private int touched;
    /** When her hull groans after a hard hit, and how loud. */
    private long groanAt = NEVER;
    private float groanVolume;

    /** One tick of a ship's control head: whether the ship has hit something, and whether she is sliding along it. */
    public void tick(ServerLevel level, BlockEntity anchor) {
        SableFlight.Body body = SkylinersConfig.IMPACT_SOUNDS.get() ? SableFlight.body(anchor) : null;
        if (body == null) {
            forget();
            return;
        }
        if (lastVelocity == null || jumped(body)) {
            remember(body);
            return;
        }
        Vec3 pushed = body.velocity().subtract(lastVelocity).add(0, SableFlight.gravity(level, body.centre()) / TICKS, 0);
        quiet = Math.max(0, quiet - 1);
        touched = Math.max(0, touched - 1);
        feelHit(level, anchor, body, pushed);
        feelSlide(level, anchor, body, pushed);
        if (level.getGameTime() >= groanAt) {
            groanAt = NEVER;
            float pitch = Mth.lerp(level.getRandom().nextFloat(), GROAN_LOW, GROAN_HIGH);
            play(level, SkylinersRegistry.IMPACT_GROAN, body.centre(), groanVolume, pitch, GROAN_HEARD, Aboard.players(anchor));
        }
        remember(body);
    }

    /**
     * A push harder than her own is summed as one hit until it lets up, or has gone on {@link #HIT_LONGEST}, and heard
     * by how hard it was.
     */
    private void feelHit(ServerLevel level, BlockEntity anchor, SableFlight.Body body, Vec3 pushed) {
        boolean pushedBack = pushed.length() >= CONTACT;
        if (pushedBack) {
            touched = SCRAPE_WITHIN;
        }
        if (hitTicks > 0 && (!pushedBack || hitTicks >= HIT_LONGEST)) {
            struck(level, anchor, body, hit);
            hit = Vec3.ZERO;
            hitTicks = 0;
        }
        if (pushedBack) {
            hit = hit.add(pushed);
            hitTicks++;
        }
    }

    /**
     * A hit over, all the speed it changed: crunched or thudded where she struck, by how hard. One coming while another
     * was heard just now goes unheard, not saved for later, and so does a bump while she grinds along, part of the grind.
     */
    private void struck(ServerLevel level, BlockEntity anchor, SableFlight.Body body, Vec3 blow) {
        double hard = blow.length();
        if (quiet > 0 || hard < BUMP || (hard < HARD && sliding >= SCRAPE_TICKS)) {
            return;
        }
        quiet = QUIET_TICKS;
        Vec3 at = where(body, blow);
        List<ServerPlayer> aboard = Aboard.players(anchor);
        if (hard >= HARD) {
            float share = (float) Mth.clamp((hard - HARD) / (SMASH - HARD), 0, 1);
            groanVolume = Mth.lerp(share, CRUNCH_LOUD, CRUNCH_LOUDEST);
            play(level, SkylinersRegistry.IMPACT_CRUNCH, at, groanVolume, Mth.lerp(share, CRUNCH_HIGH, CRUNCH_LOW), CRUNCH_HEARD, aboard);
            groanAt = level.getGameTime() + GROAN_AFTER;
        } else {
            float share = (float) Mth.clamp((hard - BUMP) / (HARD - BUMP), 0, 1);
            play(level, SkylinersRegistry.IMPACT_THUD, at, Mth.lerp(share, THUD_SOFT, THUD_LOUD), Mth.lerp(share, THUD_HIGH, THUD_LOW), THUD_HEARD, aboard);
        }
        ImpactJolt jolt = new ImpactJolt((float) Math.min(1, hard / SMASH));
        aboard.forEach(player -> PacketDistributor.sendToPlayer(player, jolt));
    }

    /**
     * Slowed across the ground hard a while, just after something pushed back on her or sliding already, she grinds
     * along what slows her.
     */
    private void feelSlide(ServerLevel level, BlockEntity anchor, SableFlight.Body body, Vec3 pushed) {
        drag += DRAG_SMOOTHING * (slowed(body.velocity()) - drag);
        push = push.lerp(pushed, DRAG_SMOOTHING);
        double speed = body.velocity().horizontalDistance();
        sliding = speed >= SCRAPE_SPEED && drag >= SCRAPE_DRAG && (sliding > 0 || touched > 0) ? sliding + 1 : 0;
        if (sliding < SCRAPE_TICKS || (sliding - SCRAPE_TICKS) % SCRAPE_EVERY != 0) {
            return;
        }
        float share = (float) Mth.clamp((speed - SCRAPE_SPEED) / (SCRAPE_FASTEST - SCRAPE_SPEED), 0, 1);
        play(level, SkylinersRegistry.IMPACT_SCRAPE, where(body, push), Mth.lerp(share, SCRAPE_SOFT, SCRAPE_LOUD), Mth.lerp(share, SCRAPE_LOW, SCRAPE_HIGH),
            SCRAPE_HEARD, Aboard.players(anchor));
    }

    /** How much speed across the ground she lost since last tick, in blocks a second; none slower than a slide. */
    private double slowed(Vec3 velocity) {
        double speed = lastVelocity.horizontalDistance();
        if (speed < SCRAPE_SPEED) {
            return 0;
        }
        return ((lastVelocity.x - velocity.x) * lastVelocity.x + (lastVelocity.z - velocity.z) * lastVelocity.z) / speed;
    }

    /** Whether she moved farther since last tick than her speed would take her: moved, as a jump moves her. */
    private boolean jumped(SableFlight.Body body) {
        double fastest = Math.max(body.velocity().length(), lastVelocity.length());
        return body.centre().distanceTo(lastCentre) > fastest / TICKS + JUMPED;
    }

    private void remember(SableFlight.Body body) {
        lastVelocity = body.velocity();
        lastCentre = body.centre();
    }

    /** Not assembled, or not to be heard: everything she was feeling let go. */
    private void forget() {
        lastVelocity = null;
        lastCentre = null;
        hit = Vec3.ZERO;
        hitTicks = 0;
        quiet = 0;
        drag = 0;
        push = Vec3.ZERO;
        sliding = 0;
        touched = 0;
        groanAt = NEVER;
    }

    /**
     * Where on her she was struck, pushed back this way: from her centre against the push, out to the box round her.
     * Her middle if nothing pushed.
     */
    private static Vec3 where(SableFlight.Body body, Vec3 pushed) {
        if (pushed.lengthSqr() < 1e-6) {
            return body.centre();
        }
        Vec3 way = pushed.normalize().reverse();
        Vec3 from = body.centre();
        AABB box = body.bounds();
        double out = Math.min(outTo(from.x, way.x, box.minX, box.maxX), Math.min(outTo(from.y, way.y, box.minY, box.maxY), outTo(from.z, way.z, box.minZ, box.maxZ)));
        return from.add(way.scale(out));
    }

    /** How far along a way from a point the side of a box is, along one axis; never when the way runs along it. */
    private static double outTo(double from, double way, double min, double max) {
        if (Math.abs(way) < 1e-6) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0, ((way > 0 ? max : min) - from) / way);
    }

    /**
     * Plays a sound of her striking something at a spot in the world, as loud and high as given, heard so far off at
     * its plainest and farther played louder than 1: by everyone that near it, and by everyone aboard her from its way,
     * no farther than {@link #BY_EAR} off, however far along her they stand.
     */
    private static void play(ServerLevel level, Holder<SoundEvent> sound, Vec3 at, float volume, float pitch, double heard, List<ServerPlayer> aboard) {
        double reach = heard * Math.max(1, volume);
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : level.players()) {
            Vec3 ear = player.getEyePosition();
            double away = ear.distanceTo(at);
            boolean broughtNear = aboard.contains(player) && away > BY_EAR;
            if (!broughtNear && away > reach) {
                continue;
            }
            Vec3 from = broughtNear ? ear.add(at.subtract(ear).scale(BY_EAR / away)) : at;
            player.connection.send(new ClientboundSoundPacket(sound, SoundSource.BLOCKS, from.x, from.y, from.z, volume, pitch, seed));
        }
    }
}
