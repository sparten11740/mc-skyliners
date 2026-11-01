package dev.skyliners.selfdestruct;

import dev.skyliners.SkylinersRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * An airliner blowing up as its self-destruct button sets it off: the blast of Ultrasplosive's nuke (the Fat Man, from
 * the mod of that name, which need not be installed: its blast is copied here), scaled down to the airliner. It is made
 * only of particles, sounds and what each player near it sees and feels, so nothing round it is touched: no block
 * broken, nothing set alight, nobody hurt or thrown. In the order it plays out:
 * <ul>
 * <li>a white flash and a shake of the view for every player near (see {@link BlastPayload}), harder the nearer, the
 * flash brighter for those who saw it go;</li>
 * <li>the fireball: fire bursting from spots all over the airliner's blocks where it was, so it takes the airliner's
 * shape, and a bigger ball of it in the middle, each puff white-hot and cooling through red to soot, sparks and
 * lava spitting out of it, and burning embers thrown out of it, falling as they cool;</li>
 * <li>the blast wave: a ring of pale dust racing out from it, along the ground where the ground is near, or else
 * through the sky;</li>
 * <li>a column of smoke rising out of the fireball and rolling out into a small mushroom cloud, which hangs on a
 * while;</li>
 * <li>the bang, reaching a listener the later the further off they are, and the long rumble rolling in after it.</li>
 * </ul>
 * Each puff is one of a few thousand big, slow particles (see {@link dev.skyliners.client.BlastPuffParticle}), each
 * tick's sent to every player near as one bundle. Lengths are in metres of the real airliner, scaled by the blocks to
 * a metre the airliner was built at; times are in ticks.
 */
public final class Blast {
    /** The most blasts going on at once in a level, so a row of airliners blown up together costs no more than this. */
    private static final int MOST = 4;
    /** How far from the blast its particles are sent, in blocks: about as far as a client draws them. */
    private static final double VIEW_DISTANCE = 512;
    /** How long the fire bursts out of the airliner, and how far round each spot of it the fire reaches at the full. */
    private static final int FIREBALL_TICKS = 30;
    private static final double SPOT_FIRE = 5;
    /** The ball of fire in the middle: its radius as it starts and at the full. */
    private static final double CORE_START = 3;
    private static final double CORE = 13;
    /** Fire puffs a tick while the fireball burns, and the share of them in the ball in the middle. */
    private static final int FIRE_PUFFS = 40;
    private static final double CORE_SHARE = 0.3;
    /** How many of the spots vanilla's explosions burst at besides the middle, the first instant. */
    private static final int BANGS = 4;
    /** Vanilla's flames spat out of the ball every other tick, and its lava every fourth. */
    private static final int FLAMES = 20;
    private static final int LAVA = 6;
    /** How fast the fire puffs fly out of the fireball and rise, in blocks a tick. */
    private static final double FIRE_OUT = 0.08;
    private static final double FIRE_RISE = 0.05;
    /** How long embers are thrown out, how many a tick, and how fast, in blocks a tick per block to a metre. */
    private static final int EMBER_TICKS = 40;
    private static final int EMBERS = 8;
    private static final double EMBER_SPEED = 0.2;
    /** The blast wave: how long it takes to race out, how far it reaches, and its puffs a tick round the ring. */
    private static final int WAVE_TICKS = 40;
    private static final double WAVE_RADIUS = 70;
    private static final int WAVE_PUFFS = 32;
    /** How near under the blast the ground has to be for the wave to race along it, rather than through the sky. */
    private static final double GROUND_NEAR = 12;
    /** How high the wave's dust rolls along the ground, and how thick its ring is in the sky, in blocks. */
    private static final double GROUND_DUST = 4;
    private static final double SKY_RING = 3;
    /** How far round the ring's slices turn each tick, in radians, so its puffs never line up into spokes. */
    private static final double WAVE_TURN = 0.37;
    /** How fast the ring's puffs rise, in blocks a tick: most of them gently, and every third high, to roll its top. */
    private static final double WAVE_LIFT = 0.08;
    private static final double WAVE_TOSS = 0.28;
    /** How long smoke rises, the last part of that thinning out, and its puffs a tick at the full. */
    private static final int SMOKE_TICKS = 200;
    private static final int SMOKE_FADE = 60;
    private static final double SMOKE_PUFFS = 10;
    /** How long the column takes to rise to its full height, and how high that is over the fireball. */
    private static final int RISE_TICKS = 160;
    private static final double STEM_HEIGHT = 40;
    /** The column's radius at its foot and at its top, the cap's at the full, and how tall the cap is for its width. */
    private static final double STEM_FOOT = 4;
    private static final double STEM_TOP = 6;
    private static final double CAP_RADIUS = 20;
    private static final double CAP_HEIGHT = 0.42;
    /** When the cap starts to spread, and how long the column's foot glows with fire before it chokes into smoke. */
    private static final int CAP_START = 20;
    private static final int GLOW_TICKS = 3 * FIREBALL_TICKS;
    /**
     * How wide the cap has to be to show, in blocks, and once it shows the share of the smoke in it; of that the share
     * in its rounded crown, the rest in the roll under its rim; and the share of the column, from its foot, that glows.
     */
    private static final double CAP_SHOWS = 3;
    private static final double CAP_SHARE = 0.55;
    private static final double CROWN_SHARE = 0.6;
    private static final double GLOWING_FOOT = 0.4;
    /** How fast the column's smoke spreads out from it and swirls round it, in blocks a tick. */
    private static final double STEM_SPREAD = 0.006;
    private static final double STEM_SWIRL = 0.012;
    /** How far the view shakes and the flash is seen, in metres. */
    private static final double SHAKE_RADIUS = 90;
    private static final double FLASH_RADIUS = 130;
    /** The cosine of the half angle within which a player looking that way sees the blast: about 70 degrees. */
    private static final double LOOK = 0.35;
    /** The share of the flash seen by a player looking away, and by one with something solid in the way. */
    private static final float LOOKING_AWAY = 0.45F;
    private static final float HIDDEN = 0.3F;
    /** How far the bang and the rumble are heard, in metres (sounds.json lets the bang carry 640 blocks at most). */
    private static final double BANG_HEARD = 300;
    private static final double RUMBLE_HEARD = 200;
    /**
     * How fast the bang travels, in blocks a tick (sped up from the real seventeen), and how much later the rumble
     * rolls in, in ticks.
     */
    private static final double SOUND_SPEED = 8;
    private static final int RUMBLE_LAG = 25;
    /** How loud the rumble is for the nearest listener and for the furthest. */
    private static final float RUMBLE_NEAR = 0.45F;
    private static final float RUMBLE_FAR = 1.2F;

    private static final Map<ServerLevel, List<Blast>> GOING_ON = new HashMap<>();

    /** Where the blast is: the middle of the airliner, in the world, as it blew up. */
    private final Vec3 centre;
    /** Spots on the airliner's blocks, where they were in the world: where its fire bursts from. */
    private final List<Vec3> spots;
    /** The airliner's size as it was built, in blocks to a metre. */
    private final double size;
    /** The sounds still to reach their listeners. */
    private final List<Heard> heard = new ArrayList<>();
    /** This tick's particles, sent as one bundle to each player near (see {@link #send}). */
    private final List<ClientboundLevelParticlesPacket> outgoing = new ArrayList<>();
    private int age;
    /** The smoke puffs owed and not yet sent, so a share of one a tick is not lost. */
    private double smokeOwed;

    /** A sound reaching a listener this many ticks after the blast, at the blast or at their own ear. */
    private record Heard(ServerPlayer player, int tick, Holder<SoundEvent> sound, float volume, float pitch, boolean atEar) {
    }

    private Blast(Vec3 centre, List<Vec3> spots, double size) {
        this.centre = centre;
        this.spots = spots.isEmpty() ? List.of(centre) : List.copyOf(spots);
        this.size = size;
    }

    /**
     * An airliner of this size, in blocks to a metre, blows up round the middle given, its fire bursting from the spots
     * given: the flash and the shake for everyone near at once, the rest over the next ten seconds.
     */
    public static void start(ServerLevel level, Vec3 centre, List<Vec3> spots, double size) {
        List<Blast> blasts = GOING_ON.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (blasts.size() >= MOST) {
            return;
        }
        Blast blast = new Blast(centre, spots, size);
        blast.flash(level);
        blast.listen(level);
        blasts.add(blast);
    }

    /** Every tick, on the server: each blast going on in the level plays on, and is forgotten once it is over. */
    static void tick(ServerLevel level) {
        List<Blast> blasts = GOING_ON.get(level);
        if (blasts != null && blasts.removeIf(blast -> blast.playOn(level)) && blasts.isEmpty()) {
            GOING_ON.remove(level);
        }
    }

    /** Forgets the blasts of a level going away. */
    static void drop(ServerLevel level) {
        GOING_ON.remove(level);
    }

    /** The flash and the shake, for every player near, by how near they are and whether they saw it. */
    private void flash(ServerLevel level) {
        double shakeRadius = SHAKE_RADIUS * size;
        double flashRadius = FLASH_RADIUS * size;
        for (ServerPlayer player : level.players()) {
            Vec3 eyes = player.getEyePosition();
            double distance = eyes.distanceTo(centre);
            if (player.isSpectator() || distance >= Math.max(shakeRadius, flashRadius)) {
                continue;
            }
            float shake = (float) Math.max(0, 1 - distance / shakeRadius);
            float flash = (float) Math.max(0, 1 - distance / flashRadius) * seen(level, player, eyes, distance);
            PacketDistributor.sendToPlayer(player, new BlastPayload(shake, flash));
        }
    }

    /**
     * How much of the flash a player sees: all of it looking at the blast with nothing in the way, less looking away
     * or with something solid between (the light flooding the sky and the ground still reaches them).
     */
    private float seen(ServerLevel level, ServerPlayer player, Vec3 eyes, double distance) {
        if (distance < 1) {
            return 1;
        }
        if (player.getLookAngle().dot(centre.subtract(eyes).scale(1 / distance)) < LOOK) {
            return LOOKING_AWAY;
        }
        return canSee(level, eyes, centre) ? 1 : HIDDEN;
    }

    /**
     * Whether nothing solid lies between two points, looking only into loaded chunks: one not loaded is taken as clear,
     * never loaded to be looked into.
     */
    private static boolean canSee(ServerLevel level, Vec3 from, Vec3 to) {
        Boolean blocked = BlockGetter.traverseBlocks(from, to, null, (ignored, pos) -> solid(level, pos), ignored -> Boolean.FALSE);
        return !blocked;
    }

    /** True where a loaded block is solid, for {@link #canSee}; null to look on. */
    private static Boolean solid(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() ? Boolean.TRUE : null;
    }

    /** The bang and the rumble, for every player near enough to hear them, each when the sound reaches them. */
    private void listen(ServerLevel level) {
        RandomSource random = level.getRandom();
        double bangHeard = BANG_HEARD * size;
        double rumbleHeard = RUMBLE_HEARD * size;
        for (ServerPlayer player : level.players()) {
            double distance = player.position().distanceTo(centre);
            int delay = (int) Math.ceil(distance / SOUND_SPEED);
            if (distance <= bangHeard) {
                heard.add(new Heard(player, delay, SkylinersRegistry.SELF_DESTRUCT_BLAST, 1.0F, 0.9F + random.nextFloat() * 0.2F, false));
            }
            if (distance <= rumbleHeard) {
                float volume = Mth.lerp((float) (distance / rumbleHeard), RUMBLE_NEAR, RUMBLE_FAR);
                heard.add(new Heard(player, delay + RUMBLE_LAG, SkylinersRegistry.SELF_DESTRUCT_RUMBLE, volume, 0.9F + random.nextFloat() * 0.2F, true));
            }
        }
    }

    /** One tick of the blast; whether it is over. */
    private boolean playOn(ServerLevel level) {
        age++;
        heard.removeIf(sound -> sound.tick() <= age && play(level, sound));
        List<ServerPlayer> viewers = level.players().stream().filter(player -> player.position().closerThan(centre, VIEW_DISTANCE)).toList();
        if (!viewers.isEmpty()) {
            emit(level, level.getRandom());
            send(viewers);
        }
        return age >= SMOKE_TICKS && heard.isEmpty();
    }

    /** Plays a sound that has reached its listener, if they are still in the level; done with it either way. */
    private boolean play(ServerLevel level, Heard sound) {
        ServerPlayer player = sound.player();
        if (player.level() == level && !player.isRemoved()) {
            Vec3 at = sound.atEar() ? player.getEyePosition() : centre;
            player.connection.send(new ClientboundSoundPacket(sound.sound(), SoundSource.BLOCKS, at.x, at.y, at.z, sound.volume(), sound.pitch(),
                level.getRandom().nextLong()));
        }
        return true;
    }

    /** This tick's particles, by what of the blast is still going on. */
    private void emit(ServerLevel level, RandomSource random) {
        if (age == 1) {
            burst();
        }
        if (age <= FIREBALL_TICKS) {
            for (int i = 0; i < FIRE_PUFFS; i++) {
                fire(random);
            }
            sparks();
        }
        if (age <= EMBER_TICKS) {
            for (int i = 0; i < EMBERS; i++) {
                ember(random);
            }
        }
        if (age <= WAVE_TICKS) {
            wave(level, random);
        }
        smokeOwed += SMOKE_PUFFS * Math.min(1, (SMOKE_TICKS - age) / (double) SMOKE_FADE);
        for (; smokeOwed >= 1; smokeOwed--) {
            smoke(random);
        }
    }

    /** The first instant: the flash of light and vanilla's explosions, in the middle and at a few of the spots. */
    private void burst() {
        batch(ParticleTypes.FLASH, centre, 1, 0, 0, 0);
        batch(ParticleTypes.EXPLOSION_EMITTER, centre, 1, 0, 0, 0);
        for (int i = 0; i < spots.size(); i += Math.max(1, spots.size() / BANGS)) {
            batch(ParticleTypes.EXPLOSION_EMITTER, spots.get(i), 1, 0, 0, 0);
        }
    }

    /** A puff of the fireball: from one of the spots, or the ball in the middle, both swelling as it burns. */
    private void fire(RandomSource random) {
        double grow = age / (double) FIREBALL_TICKS;
        Vec3 way = direction(random);
        if (random.nextDouble() < CORE_SHARE) {
            // The ball swells upward from the middle rather than down.
            Vec3 up = new Vec3(way.x, Math.abs(way.y), way.z);
            double radius = Mth.lerp(grow, CORE_START, CORE) * size * (0.85 + 0.15 * random.nextDouble());
            puff(SkylinersRegistry.BLAST_FIRE.get(), centre.add(up.scale(radius)), up.scale(FIRE_OUT).add(0, FIRE_RISE, 0));
            return;
        }
        Vec3 spot = spots.get(random.nextInt(spots.size()));
        double radius = SPOT_FIRE * size * (0.3 + 0.7 * grow) * (0.6 + 0.4 * random.nextDouble());
        puff(SkylinersRegistry.BLAST_FIRE.get(), spot.add(way.scale(radius)), way.scale(FIRE_OUT).add(0, FIRE_RISE, 0));
    }

    /** Sparks and lava spitting out of the ball in the middle, every other tick while it burns. */
    private void sparks() {
        if (age % 2 != 0) {
            return;
        }
        double radius = Mth.lerp(age / (double) FIREBALL_TICKS, CORE_START, CORE) * size;
        batch(ParticleTypes.FLAME, centre.add(0, radius * 0.4, 0), FLAMES, radius * 0.5, radius * 0.35, 0.1);
        if (age % 4 == 0) {
            batch(ParticleTypes.LAVA, centre, LAVA, radius * 0.4, 1, 0);
        }
    }

    /** A burning ember thrown out of the airliner from one of its spots, mostly outward and up, to fall as it cools. */
    private void ember(RandomSource random) {
        Vec3 spot = spots.get(random.nextInt(spots.size()));
        Vec3 way = direction(random);
        Vec3 thrown = new Vec3(way.x, Math.abs(way.y) * 0.6 + 0.2, way.z).scale(EMBER_SPEED * size * (0.5 + random.nextDouble()));
        puff(SkylinersRegistry.BLAST_EMBER.get(), spot, thrown);
    }

    /**
     * The blast wave's ring of dust: puffs spread evenly round it (one to each slice, at random within it, so it has no
     * gaps and no spokes), each sent out as fast as the front is moving and slowing with it.
     */
    private void wave(ServerLevel level, RandomSource random) {
        double radius = WAVE_RADIUS * size * reach(age);
        double speed = WAVE_RADIUS * size * reachPerTick(age);
        double turn = age * WAVE_TURN;
        for (int i = 0; i < WAVE_PUFFS; i++) {
            double angle = (i + random.nextDouble()) * Mth.TWO_PI / WAVE_PUFFS + turn;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double distance = radius * (0.95 + 0.07 * random.nextDouble());
            double x = centre.x + cos * distance;
            double z = centre.z + sin * distance;
            double outward = speed * (0.85 + 0.3 * random.nextDouble());
            // Every third puff is thrown higher, so the ring's top edge rolls and tumbles.
            double lift = i % 3 == 0 ? WAVE_TOSS : WAVE_LIFT * (1 + random.nextDouble());
            puff(SkylinersRegistry.BLAST_WAVE.get(), new Vec3(x, ringHeight(level, x, z, random), z),
                new Vec3(cos * outward - sin * 0.02, lift, sin * outward + cos * 0.02));
        }
    }

    /** The height the ring's dust rolls at in a column: along the ground where that is near, else at the blast's. */
    private double ringHeight(ServerLevel level, double x, double z, RandomSource random) {
        BlockPos column = BlockPos.containing(x, centre.y, z);
        if (level.isLoaded(column)) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            if (centre.y - ground <= GROUND_NEAR * size) {
                return ground + 0.5 + random.nextDouble() * GROUND_DUST;
            }
        }
        return centre.y + (random.nextDouble() - 0.5) * SKY_RING;
    }

    /** How far out the wave's front is at an age, as a share of its reach: fast at first, slowing as it spreads. */
    private static double reach(int age) {
        double t = Math.min(1, age / (double) WAVE_TICKS);
        return 1 - (1 - t) * (1 - t);
    }

    /** How fast the wave's front is moving at an age, as a share of its reach a tick. */
    private static double reachPerTick(int age) {
        double t = Math.min(1, age / (double) WAVE_TICKS);
        return 2 * (1 - t) / WAVE_TICKS;
    }

    /**
     * A puff of the smoke rising out of the fireball: up the column, half of them at its rising top so it climbs and
     * the rest along it, its foot glowing with the fire a while; or, once the cap starts to spread, in the cap, its
     * rounded crown and the roll curling out and down under its rim.
     */
    private void smoke(RandomSource random) {
        double rise = Math.min(1, age / (double) RISE_TICKS);
        double top = STEM_HEIGHT * size * (1 - (1 - rise) * (1 - rise));
        double cap = CAP_RADIUS * size * spread();
        if (cap >= CAP_SHOWS && random.nextDouble() < CAP_SHARE) {
            capPuff(random, top, cap);
            return;
        }
        double along = random.nextBoolean() ? 0.82 + 0.18 * random.nextDouble() : random.nextDouble();
        double radius = Mth.lerp(along, STEM_FOOT, STEM_TOP) * size * (0.7 + 0.3 * random.nextDouble());
        double angle = random.nextDouble() * Mth.TWO_PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        boolean glowing = age < GLOW_TICKS && along < GLOWING_FOOT;
        puff(glowing ? SkylinersRegistry.BLAST_FIRE.get() : SkylinersRegistry.BLAST_SMOKE.get(),
            new Vec3(centre.x + cos * radius, centre.y + top * along, centre.z + sin * radius),
            new Vec3(cos * STEM_SPREAD - sin * STEM_SWIRL, 0.02 + 0.09 * along, sin * STEM_SPREAD + cos * STEM_SWIRL));
    }

    /** How far the cap has spread by now, as a share of its full radius: quickly at first, slowing to the full. */
    private double spread() {
        double t = Mth.clamp((age - CAP_START) / (double) (SMOKE_TICKS - CAP_START), 0, 1);
        return t * (2 - t);
    }

    /** A puff of the cap over the column's top: in its crown, or in the roll under its rim. */
    private void capPuff(RandomSource random, double top, double radius) {
        double angle = random.nextDouble() * Mth.TWO_PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double base = centre.y + top;
        // Riding up as fast as the column's top rises, so the cap stays on it.
        double lift = STEM_HEIGHT * size * 2 * (1 - Math.min(1, age / (double) RISE_TICKS)) / RISE_TICKS * 0.9;
        double distance;
        double y;
        double outward;
        double up;
        if (random.nextDouble() < CROWN_SHARE) {
            // Over the whole cap, evenly, its dome highest in the middle.
            distance = radius * Math.sqrt(random.nextDouble());
            double out = distance / radius;
            y = base + radius * CAP_HEIGHT * Math.pow(Math.max(0, 1 - out * out), 0.8);
            outward = 0.015 + 0.03 * out;
            up = lift + 0.012;
        } else {
            // The lower half of a ring round the rim, curling out and down as it drifts.
            double phi = Math.PI + random.nextDouble() * Math.PI;
            distance = radius * 0.62 + radius * 0.38 * Math.cos(phi);
            y = base + radius * 0.38 * Math.sin(phi) * 0.7;
            outward = 0.03;
            up = lift - 0.012;
        }
        puff(SkylinersRegistry.BLAST_SMOKE.get(), new Vec3(centre.x + cos * distance, y, centre.z + sin * distance),
            new Vec3(cos * outward - sin * 0.008, up, sin * outward + cos * 0.008));
    }

    /** A way at random, evenly over the sphere. */
    private static Vec3 direction(RandomSource random) {
        double y = random.nextDouble() * 2 - 1;
        double across = Math.sqrt(1 - y * y);
        double angle = random.nextDouble() * Mth.TWO_PI;
        return new Vec3(Math.cos(angle) * across, y, Math.sin(angle) * across);
    }

    /** One puff, moving as given in blocks a tick: vanilla's one particle at exactly that velocity. */
    private void puff(ParticleOptions type, Vec3 at, Vec3 velocity) {
        outgoing.add(new ClientboundLevelParticlesPacket(type, true, at.x, at.y, at.z, (float) velocity.x, (float) velocity.y, (float) velocity.z, 1, 0));
    }

    /** A batch of vanilla's particles, scattered round a point as far as given and flying off as fast as given. */
    private void batch(ParticleOptions type, Vec3 at, int count, double across, double upDown, double speed) {
        outgoing.add(new ClientboundLevelParticlesPacket(type, true, at.x, at.y, at.z, (float) across, (float) upDown, (float) across, (float) speed, count));
    }

    /**
     * Sends this tick's particles: each built once, and every player near sent the ones close enough to them to draw
     * as one bundle, rather than a packet of their own for each.
     */
    private void send(List<ServerPlayer> viewers) {
        for (ServerPlayer player : viewers) {
            List<Packet<? super ClientGamePacketListener>> near = near(player);
            if (!near.isEmpty()) {
                player.connection.send(new ClientboundBundlePacket(near));
            }
        }
        outgoing.clear();
    }

    /** This tick's particles close enough to the player for their client to draw. */
    private List<Packet<? super ClientGamePacketListener>> near(ServerPlayer player) {
        List<Packet<? super ClientGamePacketListener>> near = new ArrayList<>(outgoing.size());
        for (ClientboundLevelParticlesPacket packet : outgoing) {
            if (player.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) <= VIEW_DISTANCE * VIEW_DISTANCE) {
                near.add(packet);
            }
        }
        return near;
    }
}
