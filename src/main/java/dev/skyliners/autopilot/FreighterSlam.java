package dev.skyliners.autopilot;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LegBayBlockEntity;
import dev.skyliners.cabin.TurbulencePayload;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.impact.ImpactJolt;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The freighter's slam landing (see {@link FreighterFlight#slam}), asked for from her console's slam button while she
 * flies along with the ground in reach: her lift thrusters cut back and she drives straight down where she is, legs
 * out, far faster than she ever lands, exhaust streaming up over her as if her thrusters fired the other way, then
 * brakes hard on her lift at the last and hits the ground with a deep boom and a crunch of metal heard well off, a
 * ring of dust racing out round her and earth thrown up at her feet, everyone aboard shaken. Then she settles on her
 * legs and switches off, as she does after any landing.
 *
 * <p>All of that is show: her pilot (see {@link FreighterPilot}) still sets her down at a safe touchdown speed, under
 * the speed at which Sable's ship breaks the fragile blocks it hits, which can freeze the server for good, and nobody
 * aboard falls far enough to be hurt. It holds off while something would hold her take-off (her ramp not all away), so
 * her ramp stays up throughout; none with the server config's {@code freighter.slamLanding} off.
 */
public final class FreighterSlam {
    /** The highest her feet may be over the ground for it to fire, in blocks: higher, the ground is out of reach. */
    static final double REACH = 160;
    /** How near the ground her feet must come to have struck it, in blocks, as her pilot counts touching. */
    private static final double STRUCK = 0.6;
    /** The longest a slam goes on, in ticks (a minute), should she never come down. */
    private static final int LONGEST_TICKS = 1200;
    /**
     * Her thrusters' roar as it begins, heard at her middle: how loud, and how much lower than her boost's, as her
     * thrusters fire the other way.
     */
    private static final float ROAR_VOLUME = 3;
    private static final float ROAR_PITCH = 0.7F;
    /**
     * The exhaust streaming up over her as she drives down, out of her lift clusters as if they fired the other way:
     * how fast she must sink for it to show, in blocks a second; how far over her top it starts, over a cluster, in
     * blocks; and how many puffs a tick, how far round the cluster's middle they spread, in blocks, and how fast they
     * scatter, in blocks a tick. A tick's puffs go to one cluster in turn, in one message.
     */
    private static final double EXHAUST_FROM_SINK = 4;
    private static final double EXHAUST_ABOVE = 1;
    private static final int EXHAUST_PUFFS = 4;
    private static final double EXHAUST_SPREAD = 1;
    private static final double EXHAUST_SCATTER = 0.08;
    /** How loud her crash is, so heard sixteen blocks off for each, at full loudness near her. */
    private static final float CRASH_VOLUME = 10;
    /**
     * Everyone aboard shaken as she strikes: the view's kick, as a share of the hardest; and the rough shake after it,
     * as a share of the roughest air of clear skies (twice as rough), for so many ticks.
     */
    private static final float JOLT = 1;
    private static final float SHAKE = 2;
    private static final int SHAKE_TICKS = 25;
    /** How far off those not aboard still feel the ground shake, in blocks, the less the farther. */
    private static final double FELT_WITHIN = 64;
    /**
     * The ring of dust racing out over the ground round her as she strikes: how long it rolls, in ticks, how far out it
     * starts from her middle and how far its front gets, in blocks.
     */
    private static final int RING_TICKS = 24;
    private static final double RING_FROM = 12;
    private static final double RING_TO = 48;
    /**
     * The earth thrown up at each of her feet: how many bits, how far round the foot, in blocks, and how fast, in
     * blocks a tick.
     */
    private static final int DEBRIS = 40;
    private static final double DEBRIS_SPREAD = 1.2;
    private static final double DEBRIS_SPEED = 0.35;
    /** The smoke puffed out at each foot, how many puffs. */
    private static final int PUFFS = 6;

    /** What asking her to slam down comes to, each with its own word in the hotbar. */
    public enum Answer implements StringRepresentable {
        /** Her thrusters roar and down she goes. */
        SLAMMING,
        /** She is slamming down already. */
        BUSY,
        /** She is not flying along: on the ground, lifting off, coming down or switched off. */
        NOT_FLYING,
        /** The ground is too far below her for it. */
        TOO_HIGH,
        /** Something would hold her take-off: her ramp not all away. */
        HELD,
        /** The server's config has it switched off. */
        SWITCHED_OFF,
        /** There is no freighter to slam down. */
        NO_SHIP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where her slam landing stands as her console shows it: not to be had, ready or under way. */
    public enum Stage {
        UNAVAILABLE, READY, FIRING
    }

    /**
     * On the server and as the clients are shown it: whether it is under way, and whether she could slam down now,
     * flying along with the ground in reach and the server letting her.
     */
    private boolean firing;
    private boolean ready;
    /**
     * On the server: whether she has struck the ground, how many ticks it has gone on, how many since she struck, and
     * where her middle was then, over the ground.
     */
    private boolean struck;
    private int ticks;
    private int sinceStruck;
    private Vec3 struckAt = Vec3.ZERO;
    /**
     * On the server: whether the slam under way was begun since she was loaded. One saved under way is not taken up
     * again, as what it counted is lost: she lands as she would have anyway.
     */
    private boolean begun;

    /** Whether it is under way, for her pilot to drive her down hard by. */
    boolean firing() {
        return firing;
    }

    /**
     * How long her legs should take yet to come down and lock, in seconds, counted from the button, which set them
     * coming down: her pilot paces her fall by it, so she never has to stop and wait for them.
     */
    double legsDueIn() {
        return Math.max(0, LegBayBlockEntity.LOWER_TICKS - ticks) / (double) SharedConstants.TICKS_PER_SECOND;
    }

    /**
     * Asked to slam down, on the server, her feet so high over the ground, in blocks, and whatever would hold her
     * take-off named, or null for nothing: under way, for her flight to bring her down where she is, or why not.
     */
    Answer start(ServerLevel level, AutopilotBlockEntity autopilot, double radio, @Nullable String held) {
        Answer refused = refusal(autopilot, radio, held);
        if (refused != null) {
            return refused;
        }
        firing = true;
        begun = true;
        struck = false;
        ticks = 0;
        Vec3 at = heardAt(autopilot);
        level.playSound(null, at.x, at.y, at.z, SkylinersRegistry.SPEED_BOOST.get(), SoundSource.BLOCKS, ROAR_VOLUME,
            ROAR_PITCH);
        autopilot.changed();
        return Answer.SLAMMING;
    }

    /**
     * Once a tick of hers, on the server, after her pilot has flown: under way, exhaust streams up over her as she
     * drives down and nobody aboard counts as falling, until her feet strike the ground, when her crash goes off; then
     * the dust ring rolls out until she has settled and switched off, or her autopilot has been set to something else.
     * Whether she could slam down now is kept for her console.
     */
    void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight, Vec3 middle, double radio,
              List<List<BlockPos>> clusters, List<BlockPos> feet, @Nullable String held) {
        boolean could = refusal(autopilot, radio, held) == null;
        if (could != ready) {
            ready = could;
            autopilot.changed();
        }
        if (firing && !begun) {
            firing = false;
            autopilot.changed();
        }
        if (!firing) {
            return;
        }
        ticks++;
        if (!struck) {
            driveDown(level, autopilot, flight, clusters);
        }
        if (!struck && radio < STRUCK) {
            strike(level, autopilot, middle, radio, feet);
        }
        if (struck && sinceStruck++ < RING_TICKS) {
            rollDust(level);
        }
        if (!comingDown(autopilot) || ticks > LONGEST_TICKS) {
            firing = false;
            begun = false;
            autopilot.changed();
        }
    }

    /**
     * Why she may not slam down now, her feet so high over the ground, in blocks, and whatever would hold her take-off
     * named, or null for nothing; null when she may.
     */
    @Nullable
    private Answer refusal(AutopilotBlockEntity autopilot, double radio, @Nullable String held) {
        if (!SkylinersConfig.FREIGHTER_SLAM_LANDING.get()) {
            return Answer.SWITCHED_OFF;
        }
        if (firing) {
            return Answer.BUSY;
        }
        if (!FreighterJump.flyingAlong(autopilot)) {
            return Answer.NOT_FLYING;
        }
        if (radio > REACH) {
            return Answer.TOO_HIGH;
        }
        return held != null ? Answer.HELD : null;
    }

    /** Whether her autopilot brings her down, coming down or in the last of it, as a slam keeps it doing. */
    private static boolean comingDown(AutopilotBlockEntity autopilot) {
        return autopilot.phase == AutopilotBlockEntity.Phase.DESCENT || autopilot.phase == AutopilotBlockEntity.Phase.LAND;
    }

    /**
     * Driving down: the rocket's white-hot exhaust puffed out over her top above one of her lift clusters a tick, in
     * turn, left standing in the air as she sinks fast, so it streams up over her as her thrusters' would fired the
     * other way; and nobody aboard counts as falling, however fast she drops under them.
     */
    private void driveDown(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight,
                           List<List<BlockPos>> clusters) {
        BoundingBox frame = autopilot.frame();
        if (flight.velocity().y < -EXHAUST_FROM_SINK && !clusters.isEmpty() && frame != null) {
            List<BlockPos> cluster = clusters.get(ticks % clusters.size());
            Vec3 under = Vec3.atBottomCenterOf(autopilot.getBlockPos()).add(middleOf(cluster));
            Vec3 world = SableFlight.toWorld(level, new Vec3(under.x, frame.maxY() + 1 + EXHAUST_ABOVE, under.z));
            RocketPlumes.send(level, SkylinersRegistry.ROCKET_EXHAUST.get(), world, EXHAUST_PUFFS, EXHAUST_SPREAD,
                EXHAUST_SCATTER);
        }
        Aboard.players(autopilot).forEach(ServerPlayer::resetFallDistance);
    }

    /** The middle of a cluster's relays, as an offset from her control head, in blocks; none for an empty one. */
    private static Vec3 middleOf(List<BlockPos> cluster) {
        Vec3 sum = Vec3.ZERO;
        for (BlockPos relay : cluster) {
            sum = sum.add(relay.getX(), relay.getY(), relay.getZ());
        }
        return cluster.isEmpty() ? Vec3.ZERO : sum.scale(1.0 / cluster.size());
    }

    /**
     * Her feet striking the ground: her crash heard well off, from her middle in the world, by those standing round her
     * as by everyone aboard; everyone aboard shaken and those standing near feeling it, earth and smoke thrown up at
     * each foot, and the dust ring set rolling from round her middle on the ground.
     */
    private void strike(ServerLevel level, AutopilotBlockEntity autopilot, Vec3 middle, double radio, List<BlockPos> feet) {
        struck = true;
        sinceStruck = 0;
        List<ServerPlayer> aboard = Aboard.players(autopilot);
        level.playSound(null, middle.x, middle.y, middle.z, SkylinersRegistry.FREIGHTER_SLAM.get(), SoundSource.BLOCKS,
            CRASH_VOLUME, 1);
        for (ServerPlayer player : level.players()) {
            double away = player.position().distanceTo(middle);
            if (aboard.contains(player)) {
                PacketDistributor.sendToPlayer(player, new ImpactJolt(JOLT));
                PacketDistributor.sendToPlayer(player, new TurbulencePayload(SHAKE, SHAKE_TICKS));
            } else if (away < FELT_WITHIN) {
                PacketDistributor.sendToPlayer(player, new ImpactJolt((float) (JOLT * (1 - away / FELT_WITHIN))));
            }
        }
        double ground = Double.MAX_VALUE;
        for (BlockPos foot : feet) {
            BlockPos plot = autopilot.getBlockPos().offset(foot);
            Vec3 world = SableFlight.toWorld(level, new Vec3(plot.getX() + 0.5, plot.getY(), plot.getZ() + 0.5));
            throwEarth(level, world.subtract(0, radio, 0));
            ground = Math.min(ground, world.y - radio);
        }
        struckAt = new Vec3(middle.x, ground == Double.MAX_VALUE ? middle.y : ground, middle.z);
    }

    /**
     * Earth thrown up and smoke puffed out where a foot meets the ground, of whatever ground it is, dirt over none,
     * seen from as far as her exhaust is.
     */
    private static void throwEarth(ServerLevel level, Vec3 foot) {
        BlockState ground = level.getBlockState(BlockPos.containing(foot.x, foot.y - 0.5, foot.z));
        BlockState thrown = ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground;
        Vec3 over = foot.add(0, DEBRIS_SPREAD / 2, 0);
        RocketPlumes.send(level, new BlockParticleOption(ParticleTypes.BLOCK, thrown), over, DEBRIS, DEBRIS_SPREAD, DEBRIS_SPEED);
        RocketPlumes.send(level, ParticleTypes.EXPLOSION, over, PUFFS, DEBRIS_SPREAD, 0);
    }

    /** A tick of the dust ring racing out over the ground from round her, slowing as it spreads. */
    private void rollDust(ServerLevel level) {
        double front = frontAt(sinceStruck);
        double speed = front - frontAt(sinceStruck - 1);
        RocketPlumes.tell(level, struckAt, new RocketDustRing(struckAt.x, struckAt.y, struckAt.z, (float) front, (float) speed));
    }

    /** How far out the dust ring's front is so many ticks after she struck, in blocks: fast at first, then slowing. */
    private static double frontAt(int age) {
        double share = Math.min(1, Math.max(0, (double) age / RING_TICKS));
        return RING_FROM + (RING_TO - RING_FROM) * (1 - (1 - share) * (1 - share));
    }

    /**
     * Where it stands as her console shows it, on either side: under way, ready while she could slam down, or not to be
     * had.
     */
    Stage stage() {
        if (firing) {
            return Stage.FIRING;
        }
        return ready ? Stage.READY : Stage.UNAVAILABLE;
    }

    /** Where her roar is heard: at her middle, or at her control head where that is not known. */
    private static Vec3 heardAt(AutopilotBlockEntity autopilot) {
        Vec3 middle = FreighterFlight.middleOf(autopilot);
        return middle == null ? Vec3.atCenterOf(autopilot.getBlockPos()) : middle;
    }

    /** What the clients are shown of it, with her autopilot's other readings. */
    void saveShown(CompoundTag tag) {
        tag.putBoolean("FreighterSlamFiring", firing);
        tag.putBoolean("FreighterSlamReady", ready);
    }

    /** What the clients are shown of it, read back; on a server, what was last shown, a missing key as never fired. */
    void loadShown(CompoundTag tag) {
        firing = tag.getBoolean("FreighterSlamFiring");
        ready = tag.getBoolean("FreighterSlamReady");
    }
}
