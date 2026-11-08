package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.airframe.RocketSilhouette;
import dev.skyliners.compat.SableFlight;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A rocket's flight as it looks round her from outside, beyond the plume of fire each client draws from under her
 * while her engines fire (see the client's RocketExhaust): as she lifts off or comes in to land, her engines lit near
 * the ground, great white clouds billow out over it from under her, roll away and pile up round her, and closer still
 * flames and embers burst from the ground under her; and falling back from space fast, her base glows and a trail of
 * flame streams up behind it, as a heat shield's does coming back in. It is not as hot as that, but it looks the part.
 * The fire is sent as particles, the clouds and the falling flames as one message a tick that each client draws them
 * from (see {@link RocketPlume}), to everyone near enough to see her, long-distance, so that those aboard, high over
 * her base, see it too. The server config's {@code rocket.plumes} leaves them out, and her plume with them.
 */
final class RocketPlumes {
    /** How high over the ground her foot may be for her lit engines to raise clouds from it, in blocks. */
    private static final double SMOKE_UNDER = 40;
    /**
     * How far out from under her middle her clouds start, as a share of her body's widest radius, and how high over the
     * ground, in blocks.
     */
    private static final double SMOKE_FROM = 0.8;
    private static final double SMOKE_OVER = 0.5;
    /**
     * Close over the ground, her engines blazing on it: how high her foot may be, in blocks; how many flames a tick
     * burst from the ground under her, how far round under her they spread on a rocket built at scale 1, in blocks,
     * and how fast they fly, in blocks a tick; and how many embers of lava a tick leap from among them. No flash
     * lights it: one every few ticks, her landing burn's last ten seconds long, would be a strobe.
     */
    private static final double BLAZE_UNDER = 15;
    private static final int BLAZE_FLAMES = 90;
    private static final double BLAZE_SPREAD = 1.25;
    private static final double BLAZE_SPEED = 0.15;
    private static final int EMBERS = 6;
    /** How high she must be and how fast falling for her base to glow, in world blocks and blocks a second. */
    private static final double GLOW_FROM = 500;
    private static final double GLOW_FALL = 20;
    /** How far outside her skin her falling flames start, in metres: they stand in the air there as she falls. */
    private static final double FLAMES_CLEAR = 1;
    /** How far off those who see the particles may be, in blocks: the length of her and more, so all aboard do. */
    private static final double SEEN_WITHIN = 256;

    private RocketPlumes() {
    }

    /**
     * Each tick of an assembled rocket's: clouds at the ground under her lit engines near it, fire on it closer still,
     * and flame streaming from her base as she falls back from space, given her radio altitude, how far under her
     * middle her foot is, in blocks, and her engines' share of full thrust.
     */
    static void tick(ServerLevel level, SableFlight.State flight, double radio, int footBelow, double thrust) {
        if (!SkylinersConfig.ROCKET_PLUMES.get()) {
            return;
        }
        Vec3 foot = flight.position().subtract(flight.up().scale(footBelow));
        if (thrust > 0 && radio < SMOKE_UNDER) {
            smoke(level, foot, footBelow);
        }
        if (thrust > 0 && radio < BLAZE_UNDER) {
            blaze(level, foot, footBelow);
        }
        if (flight.position().y > GLOW_FROM && flight.velocity().y < -GLOW_FALL) {
            flames(level, foot, footBelow);
        }
    }

    /**
     * Great white clouds rolling out every way over the ground from anywhere under her, and smoke lingering among
     * them.
     */
    private static void smoke(ServerLevel level, Vec3 foot, int footBelow) {
        Vec3 under = ground(level, foot);
        double from = RocketSilhouette.body(RocketSilhouette.height() / 2) * RocketSilhouette.scaleOf(footBelow) * SMOKE_FROM;
        tell(level, under, new RocketPlume(under.x, under.y, under.z, (float) from, false));
    }

    /** Flames bursting from the ground under her in one packet, spread as wide as she is built, and embers leaping. */
    private static void blaze(ServerLevel level, Vec3 foot, int footBelow) {
        Vec3 under = ground(level, foot);
        double spread = BLAZE_SPREAD * RocketSilhouette.scaleOf(footBelow);
        send(level, ParticleTypes.FLAME, under, BLAZE_FLAMES, spread, BLAZE_SPEED);
        send(level, ParticleTypes.LAVA, under, EMBERS, spread, 0);
    }

    /** The spot just over the ground straight under her foot, where her smoke and her fire start. */
    private static Vec3 ground(ServerLevel level, Vec3 foot) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(foot.x), Mth.floor(foot.z));
        return new Vec3(foot.x, ground + SMOKE_OVER, foot.z);
    }

    /**
     * Flames round her foot, just outside her skin, left standing in the air as she falls, streaming up her sides, and
     * sparks among them; her radius from how far her foot is under her control head, which is as she was built.
     */
    private static void flames(ServerLevel level, Vec3 foot, int footBelow) {
        double out = (RocketSilhouette.body(RocketSilhouette.height() / 2) + FLAMES_CLEAR) * RocketSilhouette.scaleOf(footBelow);
        tell(level, foot, new RocketPlume(foot.x, foot.y, foot.z, (float) out, true));
    }

    /** A message for what each client draws from it, to everyone near enough to see a spot, however far that is. */
    static void tell(ServerLevel level, Vec3 at, CustomPacketPayload message) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < SEEN_WITHIN * SEEN_WITHIN) {
                PacketDistributor.sendToPlayer(player, message);
            }
        }
    }

    /**
     * So many particles about a spot, spread round it so far, in blocks, and moving at so much, in blocks a tick, every
     * way: one packet to everyone near enough, however far that is. A freighter slamming down puffs her exhaust so too
     * (see {@link FreighterSlam}).
     */
    static void send(ServerLevel level, ParticleOptions kind, Vec3 at, int count, double spread, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < SEEN_WITHIN * SEEN_WITHIN) {
                level.sendParticles(player, kind, true, at.x, at.y, at.z, count, spread, spread / 4, spread, speed);
            }
        }
    }
}
