package dev.skyliners.spaceweather;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.cabin.TurbulencePayload;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.impact.ImpactJolt;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Space weather, as a rocket or a freighter meets it far past the black of space, from {@link #FROM} up: now and then,
 * at random, a meteor shower, an asteroid storm, a solar flare or, rarely, a giant asteroid blows through (see
 * {@link Kind}). Each is told first: an alarm whoops through her, her flight deck says what is coming after the chime
 * (the freighter's computer in words of its own, see {@code CraftFlight#says}), and everyone aboard sees its picture
 * flash on their screen, and on a freighter's scanner, while the first streaks or rocks show far off on the horizon the
 * way it comes from. Then it is on her a while: meteors and rocks stream past, and a few strike her, each seen flying
 * in from far beyond sight first, landing with a ping or a crunch, sparks, debris and smoke where it struck, the view
 * of everyone aboard jolted, and she is shoved and set turning there, an asteroid hard enough to throw her well off her
 * spot and swing her round, a light ship harder than a heavy one, and a tall one tipped by a strike high on her nose; a
 * giant throws her far and spins her past what her autopilot can stop; or the flare's glare floods the view and its
 * wind pushes her steadily aside. Her autopilot fights her back onto her course and her height. Then she says it is all
 * clear, a green tick showing.
 *
 * <p>A rock coming close is a real flying body of blocks (see {@link RockBodies}): one that meets her stops at her hull
 * and shoves her as hard as it hits her, breaking up where it struck, and a freighter's laser cannon can shoot it to
 * pieces block by block (see {@link SpaceRocks}), a rock shot at knocked off the way it was going and a rock worn away
 * landing the softer. Far off, a rock is only drawn, worked out from the weather's seed on every client alike (see
 * {@link RockField}).
 *
 * <p>No strike breaks a block of hers, nor harms anyone aboard: she is only shoved, through Sable, and those aboard as a
 * rock lands are kept a while from falls and walls, their safety canopy opening should they be thrown clear (see
 * {@link Braced}). Nothing is saved: weather blowing as the ship is unloaded is simply over. One for each rocket and
 * freighter (see {@code StandingFlight}), worked by her autopilot's block entity each tick; it costs nothing while none
 * blows but a roll of the dice, and only while someone is aboard. Off with the server setting "spaceWeather.enabled";
 * how often it blows and how hard it throws her are set there too.
 */
public final class SpaceWeather {
    /** The height space weather blows from, in world blocks, at her control head: well past the sky's black. */
    public static final int FROM = 1500;
    /** How far under {@link #FROM} she must come down for weather under way to be over early, in blocks. */
    private static final int LEFT_BY = 100;
    /**
     * How long each bout is told before it is on her, in ticks: the alarm, then her flight deck's warning after
     * {@link #VOICE_AFTER}, while the first of it shows on the horizon.
     */
    private static final int WARNING_TICKS = 200;
    private static final int VOICE_AFTER = 50;
    /**
     * How long after its last minute on her a bout lingers before the all-clear, in ticks, for the last to fly past.
     */
    private static final int LINGER_TICKS = 40;
    /** The least time between one bout's all-clear and the next one's alarm, in ticks: a minute. */
    private static final int QUIET_TICKS = 60 * 20;
    /** How many ticks a minute has, for how often weather blows, set in minutes. */
    private static final double TICKS_PER_MINUTE = 60 * 20;
    /** How often a bout under way looks whether anyone is still aboard, in ticks. */
    private static final int ABOARD_LOOK = 20;
    /** How far off a bout is shown to those not aboard her, in blocks, from her middle. */
    private static final double SHOWN_WITHIN = 256;
    /**
     * How far off a strike's sparks and debris are seen, in blocks; how far they spread, in blocks; how fast sparks and
     * debris fly out and smoke drifts, in blocks a tick.
     */
    private static final double STRIKE_SEEN = 128;
    private static final double SPREAD = 0.6;
    private static final double SPARK_SPEED = 0.35;
    private static final double SMOKE_SPEED = 0.04;
    /**
     * How far off the server sends a sound for each of its volume, in blocks, as for any sound of the mod's: a strike
     * is played as loud as reaches as far as it is heard (see {@link Kind}).
     */
    private static final double SENT_PER_VOLUME = 16;
    /** How far a strike's pitch wanders either way from its own, as a share of it. */
    private static final float PITCH_WANDER = 0.1F;
    /**
     * How far from a strike the view of those aboard is jolted its fullest, in blocks, and over how far it fades from
     * there to {@link #JOLT_LEAST}, a share of its fullest: an asteroid shakes her whole hull.
     */
    private static final double JOLT_FULL_WITHIN = 8;
    private static final double JOLT_FADES_OVER = 50;
    private static final float JOLT_LEAST = 0.5F;
    /**
     * Where a strike lands: how far round from the way the bout comes from a rock may come, in degrees, and how steeply
     * from over or under her at most, as the up of its way; how much of her hull's half width and half height across
     * its way it may come in at, on a ship wider than she is tall, as the freighter is, and on one taller than she is
     * wide, as the rocket is, struck near her middle line and anywhere up her, so a strike high on her nose tips her
     * rather than spinning her about her length, which nothing aboard her turns back; and how many tries a strike gets
     * to find her hull before it misses her.
     */
    private static final double WAY_WANDER = 30;
    private static final double MOST_STEEP = 0.35;
    private static final double ACROSS_SHARE = 0.8;
    private static final double UPRIGHT_SHARE = 0.4;
    private static final double TALL_ACROSS_SHARE = 0.1;
    private static final double TALL_UPRIGHT_SHARE = 0.85;
    private static final int AIM_TRIES = 4;
    /**
     * A strike's planned moments: the least share of the time between two strikes taken off either end of each strike's
     * slot, so no two come together.
     */
    private static final double SLOT_MARGIN = 0.15;
    /**
     * A solar flare's wind: how much faster it pushes her each tick, in blocks a second (0.8 blocks a second each
     * second), from the way it comes from; and how often its crackle is heard through her speakers, in ticks.
     */
    private static final double FLARE_WIND = 0.04;
    /**
     * A solar flare's blackout, its pulse knocking out a ship's systems as a real one's can: how long after it reaches
     * her it begins, in ticks, lasting as long as the server's config says (see {@link #blackoutTicks}); and how long
     * after it ends her all-clear can come, in ticks, for her computer to be heard saying her systems are back first.
     */
    public static final int BLACKOUT_AFTER = 30;
    private static final int ALL_CLEAR_AFTER_BLACKOUT = 40;
    /** Whether this client's screens are dark with a flare's blackout, for the play-test; set on the client. */
    public static BooleanSupplier clientBlackedOut = () -> false;
    private static final int CRACKLE_EVERY = 30;
    /** How long the view of everyone aboard goes on shaking after a strike that leaves them shaking, in ticks. */
    private static final int RUMBLE_TICKS = 30;
    /**
     * How heavy a ship is, in Sable's units (a plain block weighs one), that a strike shoves exactly as hard as its
     * kind says: a freighter's weight. A lighter ship is shoved and turned harder by the square root of how much
     * lighter, a heavier one less, but never more than {@link #LIGHTEST_SHARE} or less than {@link #HEAVIEST_SHARE} of
     * it.
     */
    private static final double REFERENCE_MASS = 2500;
    private static final double LIGHTEST_SHARE = 1.5;
    private static final double HEAVIEST_SHARE = 0.5;
    /** A strike's shove and turn at the default strength, in percent: the setting is read as a share of it. */
    private static final double FULL_STRENGTH = 100;
    /**
     * How far off what strikes her is first seen flying in, in blocks: past the farthest anyone draws anything, so it
     * flies into view rather than showing out of nowhere; and how long after a bout is told its first strike is seen
     * flying in at the soonest, in ticks.
     */
    private static final double SEEN_FROM = 820;
    private static final long AIM_MARGIN = 1;
    /** The number of a bout's first strike among its rocks, past every number of its rocks streaming by. */
    private static final int STRIKE_NUMBERS = 10_000;
    /**
     * A giant asteroid's giant: how fast it comes, in blocks a second; how big it is, at least and at most, in blocks
     * across, many times an asteroid; and how far into the time the bout is on her it lands, as a share.
     */
    private static final double GIANT_SPEED = 35;
    private static final double GIANT_SMALLEST = 30;
    private static final double GIANT_BIGGEST = 40;
    private static final double GIANT_COMES = 0.7;
    /**
     * A giant's strike, never held back by how heavy she is nor by how fast an asteroid may turn her: how much faster
     * it makes her move, in blocks a second, and how fast it sets her turning at most, in degrees a second, far past
     * what her autopilot can stop soon; how far off it is heard, in blocks, at what pitch; how many times an asteroid's
     * debris bursts there; and how hard the view aboard goes on shaking after it, and for how long, in ticks.
     */
    private static final double GIANT_SHOVE = 30;
    private static final double GIANT_TURN = 150;
    /**
     * How fast a giant's strike sends her spinning besides, in degrees a second: round about her upright, either way,
     * far faster than her autopilot can stop for a good while; and tipped over about the level line across its way, a
     * lurch she rights again, her lift kept under her.
     */
    private static final double GIANT_SPIN = 110;
    private static final double GIANT_TIP = 30;
    private static final double GIANT_HEARD = 400;
    private static final float GIANT_PITCH = 0.55F;
    private static final int GIANT_DEBRIS = 4;
    private static final float GIANT_RUMBLE = 5F;
    private static final int GIANT_RUMBLE_TICKS = 120;
    /**
     * How long everyone aboard as a rock lands is kept from coming to harm by it, in ticks (see {@link Braced}): a few
     * seconds for an asteroid, half a minute for a giant, as long as she may tumble.
     */
    private static final int BRACED_TICKS = 100;
    private static final int GIANT_BRACED_TICKS = 600;
    /**
     * How big a hole a rock makes in itself as it meets her, as a share of how big it is across, in blocks around where
     * it struck: it breaks up as it lands.
     */
    private static final double CRATER_AS_IT_LANDS = 0.3;
    /**
     * How long a rock that failed to meet her flies on past her after it would have landed, in ticks, before it is
     * given up on.
     */
    private static final int FLIES_ON_TICKS = 100;
    /**
     * The least share of its jolt a rock that lands softer for having been worn away still jolts the view aboard with.
     */
    private static final float JOLT_SHOT_LEAST = 0.25F;
    /**
     * The chips of stone and sparks flying off a rock where a shot strikes it: how far back along the shot they burst,
     * in blocks, clear of it; how far off they are seen, past where a cannon reaches a rock; how many sparks and chips;
     * and how far they spread, in blocks.
     */
    private static final double CHIPS_BACK = 0.5;
    private static final double CHIPS_SEEN = 320;
    private static final int CHIP_SPARKS = 14;
    private static final int CHIP_STONES = 10;
    private static final double CHIPS_SPREAD = 0.4;
    /** A chip of stone off a rock that has landed. */
    private static final ParticleOptions STONE_CHIP = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState());
    /** Ticks a second. */
    private static final double TICKS = 20;

    /**
     * What blows: how long it is on her, how many strike her and how hard, how they come and how big a rock is, how
     * each strike looks and sounds, and how often it blows against the others.
     */
    public enum Kind implements StringRepresentable {
        /**
         * Many small glowing streaks across the sky; a few strike her, each a sharp jolt, a ping off her plates and a
         * spray of sparks.
         */
        METEOR_SHOWER(500, 6, 9, 3.0, 12, 0.5F, 0, 160, 0, 0, SkylinersRegistry.ANNOUNCEMENT_METEORS, SkylinersRegistry.SPACE_METEOR_STRIKE, 64,
            1.2F, 3),
        /**
         * Fewer, big tumbling rocks; one striking her shoves her hard, swings her round, crunches heavily through her,
         * and shakes the view of everyone aboard a good while. Her laser cannon can shoot them down.
         */
        ASTEROID_STORM(600, 3, 5, 9.0, 30, 1.0F, 2.2F, 55, 3, 6, SkylinersRegistry.ANNOUNCEMENT_ASTEROIDS, SkylinersRegistry.SPACE_ASTEROID_STRIKE,
            192, 1.0F, 3),
        /** A glare over the view, crackling speakers, flickering screens, and a wind that pushes her aside. */
        SOLAR_FLARE(300, 0, 0, 0, 0, 0, 0, 0, 0, 0, SkylinersRegistry.ANNOUNCEMENT_FLARE, SkylinersRegistry.SPACE_METEOR_STRIKE, 0, 1, 3),
        /**
         * Now and then, an asteroid storm's few rocks round one giant many times bigger, seen coming from far off: its
         * strike, never held back, throws her far and spins her past what her autopilot can stop. Shot at enough on its
         * way in, it strikes softer, misses her or breaks up.
         */
        GIANT_ASTEROID(700, 2, 3, 9.0, 30, 1.0F, 2.2F, 55, 3, 6, SkylinersRegistry.ANNOUNCEMENT_ASTEROIDS,
            SkylinersRegistry.SPACE_ASTEROID_STRIKE, 192, 1.0F, 1);

        /** How long it is on her, in ticks, after it is told. */
        final int stormTicks;
        /** How many strike her, at least and at most. */
        final int fewest;
        final int most;
        /**
         * How much faster a strike makes her move, in blocks a second, and how fast it sets her turning at most, in
         * degrees a second.
         */
        final double shove;
        final double turn;
        /**
         * How hard a strike jolts the view aboard, from 0 to 1 (see {@link ImpactJolt}), and how rough the shaking left
         * after it is (see {@link TurbulencePayload}), none for none.
         */
        final float jolt;
        final float rumble;
        /**
         * How fast what strikes her comes, in blocks a second; and how big each rock is, at least and at most, in
         * blocks across, none for a meteor.
         */
        final double speed;
        final double smallest;
        final double biggest;
        /**
         * What her flight deck announces of it, and how a strike sounds, how far off it is heard, in blocks, and its
         * pitch.
         */
        final Holder<SoundEvent> warning;
        final Supplier<SoundEvent> struck;
        final double heard;
        final float pitch;
        /** How often it blows against the others, as a weight among theirs. */
        final int weight;

        Kind(int stormTicks, int fewest, int most, double shove, double turn, float jolt, float rumble, double speed, double smallest, double biggest,
            Holder<SoundEvent> warning, Supplier<SoundEvent> struck, double heard, float pitch, int weight) {
            this.stormTicks = stormTicks;
            this.fewest = fewest;
            this.most = most;
            this.shove = shove;
            this.turn = turn;
            this.jolt = jolt;
            this.rumble = rumble;
            this.speed = speed;
            this.smallest = smallest;
            this.biggest = biggest;
            this.warning = warning;
            this.struck = struck;
            this.heard = heard;
            this.pitch = pitch;
            this.weight = weight;
        }

        /** A kind by its place in the list, as sent; the first for one this build does not know. */
        public static Kind of(int index) {
            Kind[] kinds = values();
            return index >= 0 && index < kinds.length ? kinds[index] : kinds[0];
        }

        /** A kind picked at random, each as often as its weight says. */
        static Kind random(RandomSource random) {
            int total = 0;
            for (Kind kind : values()) {
                total += kind.weight;
            }
            int picked = random.nextInt(total);
            for (Kind kind : values()) {
                picked -= kind.weight;
                if (picked < 0) {
                    return kind;
                }
            }
            return METEOR_SHOWER;
        }

        /** Whether it brings rocks, an asteroid storm's or a giant asteroid's, which her cannon can shoot at. */
        public boolean rocky() {
            return this == ASTEROID_STORM || this == GIANT_ASTEROID;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What asking for weather comes to, for the command that asks. */
    public enum Answer {
        /** It is on its way, told now. */
        COMING,
        /** Weather blows already. */
        BUSY,
        /** She is not flying, or not assembled. */
        NOT_FLYING,
        /** She flies under {@link #FROM}, where space weather never blows. */
        TOO_LOW,
        /** Nobody is aboard to meet it. */
        NOBODY_ABOARD,
        /** The server's config has it switched off. */
        SWITCHED_OFF,
        /** There is no rocket or freighter there. */
        NO_SHIP
    }

    /**
     * A strike planned for a moment of the bout, and once it is seen flying in, where on her it lands and from where;
     * for a rock, also how big it is (see {@link IncomingStrike}, which tells the clients the same).
     */
    private static final class Strike {
        /** Its number among the weather's rocks, past every number of its rocks streaming by. */
        final int id;
        /** Whether it is the giant asteroid's giant. */
        final boolean giant;
        /** When it lands, in game time. */
        long at;
        @Nullable
        BlockPos block;
        @Nullable
        Vec3 onHer;
        @Nullable
        Vec3 way;
        /**
         * How big it is, in blocks across, none for a meteor; its seed, for its shape; how fast it comes, in blocks a
         * second.
         */
        double size;
        long seed;
        double speed;
        /** Whether it has been aimed at her, seen flying in; and whether it has been shot through. */
        boolean aimed;
        boolean burst;

        Strike(int id, long at, boolean giant) {
            this.id = id;
            this.at = at;
            this.giant = giant;
        }

        /** Whether it is on its way in, seen flying, and not yet burst. */
        boolean flying() {
            return onHer != null && !burst;
        }

        /** Where it is at a game time, from where the spot on her it comes at is then, in the world. */
        Vec3 position(Vec3 target, double now) {
            return target.subtract(way.scale((at - now) / TICKS * speed + size / 2));
        }
    }

    /** What blows now; null while none does. */
    @Nullable
    private Kind kind;
    /**
     * When it was told, in game time; the side of the sky it comes out of, as a bearing in degrees as the game turns a
     * look (see {@link #toward}); its own seed, for the clients.
     */
    private long startedAt;
    /** When a solar flare's blackout ends, in game time; long past with none under way. Weather is not saved. */
    private long blackoutEnds = Long.MIN_VALUE;
    private float from;
    private long seed;
    /** What the clients were told of it, from which its rocks stream past (see {@link RockField}); and its rocks. */
    @Nullable
    private SpaceWeatherShown told;
    private List<RockField.Rock> rocks = List.of();
    /** Those of its rocks streaming past that have been shot through, by number. */
    private final BitSet burst = new BitSet();
    /** How fast she was going as of the last tick of its rocks, in blocks a second. */
    private Vec3 herLastVelocity = Vec3.ZERO;
    /** Those of its rocks close enough to be real bodies of blocks now. */
    private RockBodies bodies = new RockBodies(new NothingTold(), 0);
    /** The autopilot of the ship it blows round, while it blows, for the rocks shot at to be shown to those near. */
    @Nullable
    private AutopilotBlockEntity autopilot;
    /** Its strikes still to come, in order. */
    private final List<Strike> strikes = new ArrayList<>();
    /** Not before this game time does weather blow again. */
    private long quietUntil;
    /** Those aboard as a rock struck her, kept from coming to harm by it until a game time (see {@link Braced}). */
    private final List<ServerPlayer> braced = new ArrayList<>();
    private long bracedUntil;
    /**
     * For the play-test, since she was loaded: how many strikes have landed on her, and when the last did, in game
     * time; how much of its rock was left of the last to land, as a share; how many rocks her cannon has shot through,
     * and how many rocks coming at her flew on past without meeting her.
     */
    private int landed;
    private long lastLanded = -1;
    private double lastShare = 1;
    private int shotDown;
    private int missed;

    /**
     * Each tick of an assembled rocket's or freighter's: those kept from harm after a strike looked after; weather under
     * way goes on; with none, flying above {@link #FROM} with someone aboard, one may begin, about as often as the
     * server's config says.
     */
    public void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        if (level.getGameTime() == blackoutEnds) {
            sayBlackout(level, autopilot, SkylinersRegistry.FREIGHTER_COMPUTER_SYSTEMS_BACK);
        }
        long now = level.getGameTime();
        brace(autopilot, now);
        double height = flight.position().y;
        if (kind != null) {
            blow(level, autopilot, height, now);
            return;
        }
        if (!SkylinersConfig.SPACE_WEATHER.get() || height < FROM || now < quietUntil) {
            return;
        }
        double every = SkylinersConfig.SPACE_WEATHER_EVERY.get() * TICKS_PER_MINUTE;
        if (level.getRandom().nextDouble() * every < 1 && !Aboard.players(autopilot).isEmpty()) {
            begin(level, autopilot, Kind.random(level.getRandom()), now);
        }
    }

    /**
     * Asks for weather of a kind at once over the ship an autopilot flies, as an operator's command and the play-test's
     * do (see {@link #ask(ServerLevel, AutopilotBlockEntity, Kind, double)}): what it came to; no ship for an autopilot
     * flying what never goes that high, or none.
     */
    public static Answer ask(ServerLevel level, @Nullable AutopilotBlockEntity autopilot, Kind asked) {
        SpaceWeather weather = autopilot == null ? null : autopilot.spaceWeather();
        if (weather == null) {
            return Answer.NO_SHIP;
        }
        SableFlight.State state = SableFlight.read(autopilot, autopilot.getBlockState().getValue(AutopilotBlock.FACING));
        return state == null ? Answer.NOT_FLYING : weather.ask(level, autopilot, asked, state.position().y);
    }

    /**
     * Asked for weather of a kind at once, by a command, her control head so high, in world blocks: it is told now,
     * without waiting for the dice, while she flies above {@link #FROM} with someone aboard; or why not.
     */
    private Answer ask(ServerLevel level, AutopilotBlockEntity autopilot, Kind asked, double height) {
        if (!SkylinersConfig.SPACE_WEATHER.get()) {
            return Answer.SWITCHED_OFF;
        }
        if (kind != null) {
            return Answer.BUSY;
        }
        if (SableFlight.body(autopilot) == null) {
            return Answer.NOT_FLYING;
        }
        if (height < FROM) {
            return Answer.TOO_LOW;
        }
        if (Aboard.players(autopilot).isEmpty()) {
            return Answer.NOBODY_ABOARD;
        }
        begin(level, autopilot, asked, level.getGameTime());
        return Answer.COMING;
    }

    /** Whether any blows now, told or on her: the rocket waits it out up there before starting down. */
    public boolean blowing() {
        return kind != null;
    }

    /** What blows now, by name, or "none"; for the play-test. */
    public String blowingNamed() {
        return kind == null ? "none" : kind.getSerializedName();
    }

    /**
     * The side of the sky what blows comes out of, as a bearing in degrees (see {@link #toward}); for the play-test.
     */
    public float from() {
        return from;
    }

    /** How many strikes have landed on her since she was loaded; for the play-test. */
    public int landed() {
        return landed;
    }

    /** When the last strike landed on her, in game time, -1 before any; for the play-test. */
    public long lastLanded() {
        return lastLanded;
    }

    /** How much of a whole strike the last to land on her landed, as a share; for the play-test. */
    public double lastShare() {
        return lastShare;
    }

    /** How many rocks her cannon has burst since she was loaded; for the play-test. */
    public int shotDown() {
        return shotDown;
    }

    /**
     * How many rocks coming at her flew on past without meeting her since she was loaded; for the play-test.
     */
    public int missed() {
        return missed;
    }

    /**
     * The rocks coming at her now, seen flying in and not yet burst, for the play-test: each one's number, where it is
     * in the world and how fast it moves, how big it is in blocks across, when it lands, in game time, whether it is
     * the giant, and where on her hull it lands, in the world; the soonest to land first.
     */
    public List<Coming> coming(ServerLevel level) {
        List<Coming> coming = new ArrayList<>();
        long now = level.getGameTime();
        for (Strike strike : strikes) {
            if (strike.flying() && strike.size > 0 && strike.at > now) {
                Vec3 target = SableFlight.toWorld(level, strike.onHer);
                Vec3 at = strike.position(target, now);
                coming.add(new Coming(strike.id, at, strike.way.scale(strike.speed), strike.size, strike.at, strike.giant, target));
            }
        }
        coming.sort(Comparator.comparingLong(Coming::lands));
        return coming;
    }

    /**
     * The rocks that are real bodies now, for the play-test: each one's number, where it is in the world and how fast
     * it moves, how heavy it is, how big it is and whether it is coming at her; those coming at her first, then the
     * heaviest.
     */
    public List<Real> realRocks(ServerLevel level) {
        List<Real> real = new ArrayList<>();
        for (int id : bodies.numbers()) {
            Vec3 at = bodies.centre(level, id);
            if (at != null) {
                real.add(new Real(id, at, bodies.velocity(id), SableFlight.massOf(level, bodies.body(id)), bodies.size(id), id >= STRIKE_NUMBERS));
            }
        }
        real.sort(Comparator.comparing(Real::coming, Comparator.reverseOrder()).thenComparing(Real::mass, Comparator.reverseOrder()));
        return real;
    }

    /** A rock that is a real body, for the play-test (see {@link #realRocks}). */
    public record Real(int id, Vec3 at, Vec3 velocity, double mass, double size, boolean coming) {
    }

    /** A rock coming at her, for the play-test (see {@link #coming}), how fast it moves too, in blocks a second. */
    public record Coming(int id, Vec3 at, Vec3 velocity, double size, long lands, boolean giant, Vec3 target) {
    }

    /**
     * Weather told: its strikes planned, a slot of the time it is on her each, each seen flying in from far beyond
     * sight, the giant asteroid's giant late in it; everyone aboard and near shown it, the alarm whooping through her
     * for those aboard, whose cockpit holds its floor for the warning to come; and its rocks there to be shot at.
     */
    private void begin(ServerLevel level, AutopilotBlockEntity autopilot, Kind told, long now) {
        SableFlight.Body body = SableFlight.body(autopilot);
        if (body == null) {
            return;
        }
        RandomSource random = level.getRandom();
        kind = told;
        startedAt = now;
        from = random.nextFloat() * 360;
        seed = random.nextLong();
        this.autopilot = autopilot;
        strikes.clear();
        int count = told.fewest + (told.most > told.fewest ? random.nextInt(told.most - told.fewest + 1) : 0);
        long first = Math.max(WARNING_TICKS, lead(told.speed) + AIM_MARGIN);
        double slot = Math.max(0, WARNING_TICKS + told.stormTicks - first) / (double) Math.max(1, count);
        for (int i = 0; i < count; i++) {
            double within = SLOT_MARGIN + random.nextDouble() * (1 - 2 * SLOT_MARGIN);
            strikes.add(new Strike(STRIKE_NUMBERS + i, now + first + (long) ((i + within) * slot), false));
        }
        if (told == Kind.GIANT_ASTEROID) {
            long giantAt = Math.max(lead(GIANT_SPEED) + AIM_MARGIN, WARNING_TICKS + (long) (told.stormTicks * GIANT_COMES));
            strikes.add(new Strike(STRIKE_NUMBERS + count, now + giantAt, true));
            strikes.sort(Comparator.comparingLong(strike -> strike.at));
        }
        this.told = new SpaceWeatherShown(told, startedAt, WARNING_TICKS, told.stormTicks, seed, from, body.centre(), body.velocity(), false);
        rocks = RockField.of(this.told);
        burst.clear();
        bodies = new RockBodies(new WeatherTold(level), startedAt);
        SpaceRocks.blowing(this);
        show(level, body, this.told);
        List<ServerPlayer> aboard = Aboard.players(autopilot);
        say(level, aboard, SkylinersRegistry.SPACE_WEATHER_ALARM, SoundSource.BLOCKS);
        autopilot.holdFloor(WARNING_TICKS);
    }

    /** How long before it lands something coming so fast, in blocks a second, is seen flying in, in ticks. */
    private static long lead(double speed) {
        return speed <= 0 ? 0 : (long) Math.ceil(SEEN_FROM / speed * TICKS);
    }

    /**
     * A tick of the weather under way: her flight deck's warning after the alarm; each strike seen flying in, then
     * landing; a flare's wind and crackle while it is on her; and the all-clear once it has passed and the last of it
     * has flown in, or at once should she come down out of it; but never before a flare's blackout is over, as she
     * falls out of the weather through a long one and nothing aboard is clear while she is still dark. Gone quiet with
     * nobody left aboard.
     */
    private void blow(ServerLevel level, AutopilotBlockEntity autopilot, double height, long now) {
        long into = now - startedAt;
        if (into % ABOARD_LOOK == 0 && Aboard.players(autopilot).isEmpty()) {
            end(level, autopilot, now, false);
            return;
        }
        if (into == VOICE_AFTER) {
            say(level, Aboard.players(autopilot), autopilot.says(kind.warning), SoundSource.VOICE);
        }
        boolean on = into >= WARNING_TICKS && into < WARNING_TICKS + kind.stormTicks;
        if (on && kind == Kind.SOLAR_FLARE) {
            flare(level, autopilot, into);
            blackout(level, autopilot, into);
        }
        if (kind.rocky()) {
            rocksTick(level, autopilot, now);
        }
        strikes.removeIf(strike -> strikeTick(level, autopilot, strike, now));
        herLastVelocity = herVelocity(autopilot);
        boolean passed = into >= WARNING_TICKS + kind.stormTicks + LINGER_TICKS && strikes.isEmpty();
        boolean systemsBack = now >= blackoutEnds + ALL_CLEAR_AFTER_BLACKOUT;
        if (systemsBack && (height < FROM - LEFT_BY || passed)) {
            end(level, autopilot, now, true);
        }
    }

    /**
     * A strike's tick: seen flying in so long before it lands, then landing, on the moment it should for one still only
     * drawn, as a meteor always is; one a real body lands as it meets her, or gives up flying on past if it never does.
     * Done with once burst. Whether it is done with.
     */
    private boolean strikeTick(ServerLevel level, AutopilotBlockEntity autopilot, Strike strike, long now) {
        if (strike.burst) {
            return true;
        }
        if (!strike.aimed && now >= strike.at - lead(strike.giant ? GIANT_SPEED : kind.speed)) {
            strike.aimed = true;
            aim(level, autopilot, strike, now);
        }
        if (bodies.real(strike.id)) {
            return realStrikeTick(level, autopilot, strike, now);
        }
        if (now < strike.at) {
            return false;
        }
        if (strike.block != null) {
            land(level, autopilot, strike);
        }
        return true;
    }

    /**
     * A real rock's strike: landing as it meets her, or given up on some time after it should have, having flown past.
     */
    private boolean realStrikeTick(ServerLevel level, AutopilotBlockEntity autopilot, Strike strike, long now) {
        if (bodies.tookHer(strike.id)) {
            land(level, autopilot, strike);
            return true;
        }
        boolean flownPast = now >= strike.at + FLIES_ON_TICKS;
        missed += flownPast ? 1 : 0;
        return flownPast;
    }

    /**
     * A tick of a bout's rocks: those that are bodies coasting on, and those near enough made bodies; the bodies coming
     * at her kept on course.
     */
    private void rocksTick(ServerLevel level, AutopilotBlockEntity autopilot, long now) {
        SableFlight.Body ship = SableFlight.body(autopilot);
        if (ship == null) {
            return;
        }
        bodies.tick(level, ship.bounds(), ship.centre(), now);
        double since = now - startedAt;
        Vec3 anchor = told.anchor(since);
        for (RockField.Rock rock : rocks) {
            streamingBy(level, ship, rock, anchor, since);
        }
        for (Strike strike : strikes) {
            comingIn(level, ship, strike, now);
        }
    }

    /** How much faster she is going than as of the last tick, in blocks a second: what meeting a real rock gave her. */
    private double herSpeedGained(AutopilotBlockEntity autopilot) {
        return herVelocity(autopilot).subtract(herLastVelocity).length();
    }

    /** How fast she is going now, in blocks a second; still for a ship no longer there. */
    private static Vec3 herVelocity(AutopilotBlockEntity autopilot) {
        SableFlight.Body ship = SableFlight.body(autopilot);
        return ship == null ? Vec3.ZERO : ship.velocity();
    }

    /** How far she spreads from her middle, in blocks. */
    private static double herReach(SableFlight.Body ship) {
        return Math.max(ship.bounds().getXsize(), ship.bounds().getZsize()) / 2;
    }

    /** One of its rocks streaming past, made a body if near enough. */
    private void streamingBy(ServerLevel level, SableFlight.Body ship, RockField.Rock rock, Vec3 anchor, double since) {
        if (burst.get(rock.id()) || !rock.flying(since) || !bodies.wanted(rock.id(), rock.at(anchor, since), rock.speed(), ship.centre())) {
            return;
        }
        RockShape.Look look = RockShape.look(rock.seed());
        bodies.approach(level, ship.centre(), herReach(ship), new RockBodies.Flight(rock.id(), rock.seed(), rock.size(), rock.at(anchor, since),
            rock.way().scale(rock.speed()).add(told.velocity()), look.turned(since / TICKS), look.spin()));
    }

    /**
     * One coming at her: made a body if near enough, and if it is one, kept coming at the spot on her it should strike.
     */
    private void comingIn(ServerLevel level, SableFlight.Body ship, Strike strike, long now) {
        if (!strike.flying() || strike.size <= 0) {
            return;
        }
        Vec3 target = SableFlight.toWorld(level, strike.onHer);
        if (bodies.real(strike.id)) {
            bodies.aimAt(level, strike.id, target, strike.speed, ship.velocity());
            return;
        }
        Vec3 at = strike.position(target, now);
        if (bodies.wanted(strike.id, at, strike.speed, ship.centre())) {
            RockShape.Look look = RockShape.look(strike.seed);
            bodies.approach(level, ship.centre(), herReach(ship), new RockBodies.Flight(strike.id, strike.seed, strike.size, at, strike.way.scale(strike.speed).add(ship.velocity()),
                look.turned((strike.at - now) / TICKS), look.spin()));
        }
    }

    /**
     * Where a strike lands, worked out as it is first seen flying in: from the way the bout comes, give or take, at a
     * spot across her hull, traced in until it meets one of her blocks; for a rock, how big it is; everyone near shown
     * it flying in to there.
     */
    private void aim(ServerLevel level, AutopilotBlockEntity autopilot, Strike strike, long now) {
        SableFlight.Body body = SableFlight.body(autopilot);
        strike.at = Math.max(strike.at, now + 1);
        if (body == null) {
            return;
        }
        RandomSource random = level.getRandom();
        Vec3 out = toward(from + (float) ((random.nextDouble() * 2 - 1) * WAY_WANDER));
        Vec3 way = new Vec3(-out.x, (random.nextDouble() * 2 - 1) * MOST_STEEP, -out.z).normalize();
        UUID her = SableFlight.bodyAt(level, autopilot.getBlockPos());
        for (int i = 0; i < AIM_TRIES && strike.block == null && her != null; i++) {
            trace(level, body, her, way, random, strike);
        }
        if (strike.block == null) {
            return;
        }
        strike.seed = seed ^ strike.at;
        strike.speed = strike.giant ? GIANT_SPEED : kind.speed;
        double smallest = strike.giant ? GIANT_SMALLEST : kind.smallest;
        double biggest = strike.giant ? GIANT_BIGGEST : kind.biggest;
        strike.size = Mth.lerp(random.nextDouble(), smallest, biggest);
        show(level, body, incoming(strike));
    }

    /** What the clients are told of a strike flying in, as it is now. */
    private IncomingStrike incoming(Strike strike) {
        return new IncomingStrike(kind, strike.id, strike.onHer, strike.way, strike.at, strike.seed, (float) strike.size, (float) strike.speed);
    }

    /**
     * One try at finding her hull along a way: a line along it through a spot picked across the box round her, traced
     * from outside the box in; what it meets, if one of her blocks (Sable's body of that id), kept as where the strike
     * lands.
     */
    private static void trace(ServerLevel level, SableFlight.Body body, UUID her, Vec3 way, RandomSource random, Strike strike) {
        AABB box = body.bounds();
        Vec3 across = new Vec3(-way.z, 0, way.x).normalize();
        boolean tall = box.getYsize() > Math.max(box.getXsize(), box.getZsize());
        double halfWide = Math.min(box.getXsize(), box.getZsize()) / 2 * (tall ? TALL_ACROSS_SHARE : ACROSS_SHARE);
        double halfHigh = box.getYsize() / 2 * (tall ? TALL_UPRIGHT_SHARE : UPRIGHT_SHARE);
        Vec3 through = body.centre().add(across.scale((random.nextDouble() * 2 - 1) * halfWide)).add(0, (random.nextDouble() * 2 - 1) * halfHigh, 0);
        double reach = box.getSize() + box.getXsize() + box.getZsize();
        Vec3 start = through.subtract(way.scale(reach));
        BlockHitResult hit = level.clip(new ClipContext(start, through.add(way.scale(reach)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
            CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK || !her.equals(SableFlight.bodyAt(level, hit.getBlockPos()))) {
            return;
        }
        strike.block = hit.getBlockPos();
        strike.onHer = SableFlight.toPlot(level, hit.getBlockPos(), SableFlight.toWorld(level, hit.getLocation()), false);
        strike.way = way;
    }

    /**
     * A strike landing: it shoves her along its way and sets her turning there as hard as the server's config says, a
     * rock that is a real body the less for what has been shot off it, and breaking up where it struck; a real giant
     * makes up only what its own blow fell short of, for one that only glances her throws her less by itself, and it
     * spins her besides; it rings out, sparks and debris burst there among her blocks, and the view of everyone aboard
     * is jolted, an asteroid's shaking it a while after. Everyone aboard as a rock lands is kept from coming to harm by
     * it a while.
     */
    private void land(ServerLevel level, AutopilotBlockEntity autopilot, Strike strike) {
        Vec3 inWorld = SableFlight.toWorld(level, strike.onHer);
        double mass = SableFlight.mass(autopilot);
        double heavy = mass > 0 ? Math.sqrt(REFERENCE_MASS / mass) : 1;
        double weight = strike.giant ? heavy : Mth.clamp(heavy, HEAVIEST_SHARE, LIGHTEST_SHARE);
        boolean real = bodies.real(strike.id);
        double share = bodies.massShare(level, strike.id);
        double strength = SkylinersConfig.SPACE_WEATHER_STRENGTH.get() / FULL_STRENGTH * weight * share;
        double shove = strike.giant ? GIANT_SHOVE : kind.shove;
        double turn = strike.giant ? GIANT_TURN : kind.turn;
        double given = real && strike.giant ? herSpeedGained(autopilot) : 0;
        double shoveLeft = Math.max(0, shove * strength - given);
        if (strength > 0 && shoveLeft > 0) {
            SableFlight.strike(level, strike.block, inWorld, strike.way.scale(shoveLeft), Math.toRadians(turn * strength));
        }
        if (real) {
            SpaceRocks.crater(level, bodies.body(strike.id), inWorld, strike.size * CRATER_AS_IT_LANDS);
        }
        if (strength > 0 && strike.giant) {
            SableFlight.spin(level, strike.block, giantSpin(strike.way, level.getRandom()).scale(strength));
        }
        landed++;
        lastLanded = level.getGameTime();
        lastShare = share;
        float pitch = (strike.giant ? GIANT_PITCH : kind.pitch) * (1 + (level.getRandom().nextFloat() * 2 - 1) * PITCH_WANDER);
        Vec3 at = strike.onHer;
        double heard = strike.giant ? GIANT_HEARD : kind.heard;
        level.playSound(null, at.x, at.y, at.z, kind.struck.get(), SoundSource.BLOCKS, (float) (heard / SENT_PER_VOLUME), pitch);
        burst(level, at, inWorld, strike.giant ? GIANT_DEBRIS : 1);
        List<ServerPlayer> aboard = Aboard.players(autopilot);
        float jolt = strike.giant ? 1 : kind.jolt * (float) Math.max(share, JOLT_SHOT_LEAST);
        for (ServerPlayer player : aboard) {
            double away = player.getEyePosition().distanceTo(inWorld);
            float fade = (float) Mth.lerp(Mth.clamp((away - JOLT_FULL_WITHIN) / JOLT_FADES_OVER, 0, 1), 1, JOLT_LEAST);
            PacketDistributor.sendToPlayer(player, new ImpactJolt(jolt * fade));
            if (kind.rumble > 0) {
                float rumble = strike.giant ? GIANT_RUMBLE : kind.rumble;
                PacketDistributor.sendToPlayer(player, new TurbulencePayload(rumble, strike.giant ? GIANT_RUMBLE_TICKS : RUMBLE_TICKS));
            }
        }
        if (strike.size > 0) {
            braceAll(level, aboard, strike.giant ? GIANT_BRACED_TICKS : BRACED_TICKS);
        }
    }

    /**
     * How a giant striking along a way sends her spinning, in radians a second about an axis in the world: round about
     * the upright, one way or the other, and tipped over about the level line across its way.
     */
    private static Vec3 giantSpin(Vec3 way, RandomSource random) {
        Vec3 across = new Vec3(way.z, 0, -way.x).normalize();
        double round = Math.toRadians(GIANT_SPIN) * (random.nextBoolean() ? 1 : -1);
        return new Vec3(0, round, 0).add(across.scale(Math.toRadians(GIANT_TIP)));
    }

    /**
     * Everyone aboard kept from coming to harm by a strike for so many ticks: no fall, no wall they are thrown into,
     * and their safety canopy opening over them should they be thrown clear of her (see {@link Braced}).
     */
    private void braceAll(ServerLevel level, List<ServerPlayer> aboard, int ticks) {
        long until = level.getGameTime() + ticks;
        bracedUntil = Math.max(bracedUntil, until);
        for (ServerPlayer player : aboard) {
            Braced.brace(player, until);
            if (!braced.contains(player)) {
                braced.add(player);
            }
        }
    }

    /**
     * Each tick, those kept from harm after a strike: their fall forgotten while they are still aboard her, and their
     * safety canopy ready to open should they be thrown clear; let go of once the time is up.
     */
    private void brace(AutopilotBlockEntity autopilot, long now) {
        if (braced.isEmpty()) {
            return;
        }
        braced.removeIf(Entity::isRemoved);
        if (now > bracedUntil) {
            braced.clear();
            return;
        }
        for (ServerPlayer player : braced) {
            Braced.keep(player, Aboard.isAboard(player, autopilot));
        }
    }

    /**
     * Sparks, embers, debris and smoke bursting where a strike landed, among her blocks so they ride along with her,
     * seen by everyone within {@link #STRIKE_SEEN}, so many times over: a meteor's a spray of sparks and a flash; an
     * asteroid's a blast of fire, shattered rock and a cloud of smoke, a giant's many times bigger.
     */
    private void burst(ServerLevel level, Vec3 at, Vec3 inWorld, int times) {
        boolean rock = kind.rocky();
        double spread = SPREAD * times;
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(inWorld) > STRIKE_SEEN) {
                continue;
            }
            particles(level, player, ParticleTypes.FLASH, at, times, spread, 0);
            particles(level, player, ParticleTypes.ELECTRIC_SPARK, at, (rock ? 30 : 24) * times, spread, SPARK_SPEED);
            particles(level, player, ParticleTypes.LAVA, at, (rock ? 16 : 5) * times, spread, 0);
            particles(level, player, rock ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, at, (rock ? 24 : 10) * times, spread, SMOKE_SPEED);
            if (rock) {
                particles(level, player, ParticleTypes.EXPLOSION, at, 3 * times, spread, 0);
                particles(level, player, STONE_CHIP, at, 40 * times, spread * 2, SPARK_SPEED);
            }
        }
    }

    private static void particles(ServerLevel level, ServerPlayer player, ParticleOptions kind, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(player, kind, true, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    /**
     * The nearest of its rocks a shot from a point along a way of length 1 meets within so many blocks, of those that
     * are real bodies; none for none (see {@link SpaceRocks#along}).
     */
    @Nullable
    SpaceRocks.Hit along(ServerLevel level, Vec3 from, Vec3 unit, double reach) {
        if (told == null || autopilot == null || autopilot.getLevel() != level) {
            return null;
        }
        SpaceRocks.Hit nearest = null;
        for (int id : bodies.numbers()) {
            nearest = nearer(level, id, from, unit, nearest == null ? reach : nearest.distance(), nearest);
        }
        return nearest;
    }

    /**
     * One of its rocks, by its number, if a shot from a point along a way of length 1 meets it within so many blocks;
     * the nearest found so far otherwise.
     */
    @Nullable
    private SpaceRocks.Hit nearer(ServerLevel level, int id, Vec3 from, Vec3 unit, double within, @Nullable SpaceRocks.Hit nearest) {
        Vec3 middle = rockCentre(level, id);
        double met = middle == null ? -1 : SpaceRocks.meets(from, unit, middle, rockSize(id) / 2 + SpaceRocks.GRACE, within);
        return met < 0 ? nearest : new SpaceRocks.Hit(this, id, from.add(unit.scale(met)), met);
    }

    /** Whether any of its rocks is a real body now, in a level, for a shot to reach out to. */
    boolean hasBodies(ServerLevel level) {
        return autopilot != null && autopilot.getLevel() == level && bodies.count() > 0;
    }

    /** The bodies of its rocks and of the pieces they have shed. */
    Set<UUID> bodies() {
        return bodies.ids();
    }

    /** Whether the ship it blew round is gone, unloaded while it blew, so its rocks are gone too. */
    boolean stale() {
        return autopilot == null || autopilot.isRemoved();
    }

    /**
     * Where the middle of one of its rocks is now, in the world, by its number, if it is a real body; null otherwise.
     */
    @Nullable
    Vec3 rockCentre(ServerLevel level, int id) {
        return told == null ? null : bodies.centre(level, id);
    }

    /** How big one of its rocks that is a real body is, by its number, in blocks across; none for none. */
    double rockSize(int id) {
        return bodies.size(id);
    }

    @Nullable
    private Strike strike(int id) {
        for (Strike strike : strikes) {
            if (strike.id == id) {
                return strike;
            }
        }
        return null;
    }

    /** The body of one of its rocks, by its number; none for none. */
    @Nullable
    UUID rockBody(int id) {
        return bodies.body(id);
    }

    /** How big a rock's body is, in blocks across, by the body's id; none if it is not one of its. */
    double sizeOf(@Nullable UUID rock) {
        for (int id : bodies.numbers()) {
            if (rock != null && rock.equals(bodies.body(id))) {
                return bodies.size(id);
            }
        }
        return 0;
    }

    /** A shot at a rock's body, travelling along a way, for it to go where it is knocked. */
    void shotAt(UUID rock, Vec3 way) {
        bodies.shot(rock, way);
    }

    /** One of its rocks shot through: done with, all that is left of it bursting where it was. */
    private void wornThrough(ServerLevel level, int id, Vec3 at, Vec3 way) {
        Strike strike = strike(id);
        if (strike != null) {
            strike.burst = true;
        } else if (id >= 0 && id < rocks.size()) {
            burst.set(id);
        }
        bursts(level, id, at, way);
    }

    /** Sparks and chips of its own stone flying off a rock where a shot met it, back along the shot, seen far off. */
    static void chips(ServerLevel level, Vec3 at, Vec3 way, BlockState stone) {
        Vec3 back = at.subtract(way.scale(CHIPS_BACK));
        ParticleOptions chip = new BlockParticleOption(ParticleTypes.BLOCK, stone);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(at) <= CHIPS_SEEN) {
                particles(level, player, ParticleTypes.ELECTRIC_SPARK, back, CHIP_SPARKS, CHIPS_SPREAD, SPARK_SPEED);
                particles(level, player, chip, back, CHIP_STONES, CHIPS_SPREAD, SPARK_SPEED);
            }
        }
    }

    /** A rock burst to pieces: everyone shown the weather told it is gone, once, to burst it where it was. */
    private void bursts(ServerLevel level, int id, Vec3 at, Vec3 way) {
        shotDown++;
        SableFlight.Body body = autopilot == null ? null : SableFlight.body(autopilot);
        if (body != null) {
            show(level, body, new RockBurst(startedAt, id, at, way));
        }
    }

    /**
     * A solar flare's tick: its wind pushing her steadily from the way it comes from, and its crackle heard through her
     * speakers now and then by everyone aboard.
     */
    private void flare(ServerLevel level, AutopilotBlockEntity autopilot, long into) {
        double strength = SkylinersConfig.SPACE_WEATHER_STRENGTH.get() / FULL_STRENGTH;
        SableFlight.jolt(autopilot, toward(from).reverse().scale(FLARE_WIND * strength));
        if (into % CRACKLE_EVERY == 0) {
            say(level, Aboard.players(autopilot), SkylinersRegistry.RADIO_CRACKLE, SoundSource.BLOCKS);
        }
    }

    /**
     * A solar flare's blackout beginning: every light aboard her goes out and comes back by itself, her computer saying
     * so, its thrusters and screens seeing to themselves (see {@link #blackedOut}). Its end is kept apart from the
     * weather's, which a long one outlasts.
     */
    private void blackout(ServerLevel level, AutopilotBlockEntity autopilot, long into) {
        int ticks = blackoutTicks();
        if (into - WARNING_TICKS - BLACKOUT_AFTER == 0 && ticks > 0) {
            blackoutEnds = level.getGameTime() + ticks;
            lightsOut(level, autopilot, ticks);
            sayBlackout(level, autopilot, SkylinersRegistry.FREIGHTER_COMPUTER_BLACKOUT);
        }
    }

    /** What is heard aboard as her systems go or come back: her computer on a freighter, static on anything else. */
    private static void sayBlackout(ServerLevel level, AutopilotBlockEntity autopilot, Holder<SoundEvent> line) {
        Holder<SoundEvent> sound = autopilot.craft() == Craft.FREIGHTER ? line : SkylinersRegistry.RADIO_CRACKLE;
        say(level, Aboard.players(autopilot), sound, SoundSource.VOICE);
    }

    /**
     * Every light aboard her out at once, each told to come back as it was once the blackout is over: a change its
     * panel keeps and saves, so it comes back even if the world is left meanwhile.
     */
    private static void lightsOut(ServerLevel level, AutopilotBlockEntity autopilot, int ticks) {
        BoundingBox frame = autopilot.frame();
        for (LedPanelBlockEntity panel : LedPanelBlockEntity.loaded(level)) {
            if (frame != null && frame.isInside(panel.getBlockPos())) {
                int was = panel.next().brightness();
                panel.change(setting -> setting.withBrightness(0), 0);
                panel.change(setting -> setting.withBrightness(was), ticks);
            }
        }
    }

    /** How long a solar flare's blackout lasts, in ticks, as the server's config says; none if set to nothing. */
    public static int blackoutTicks() {
        return (int) (SkylinersConfig.SPACE_WEATHER_BLACKOUT.get() * TICKS);
    }

    /** Whether a solar flare has her systems out now, for her autopilot to let go of her thrusters. */
    public boolean blackedOut(ServerLevel level) {
        return level.getGameTime() < blackoutEnds;
    }

    /**
     * The weather over: everyone shown so, and, when it passed by rather than was left, her flight deck saying it is
     * all clear to everyone aboard; none blows again for a while, and its rocks are gone.
     */
    private void end(ServerLevel level, AutopilotBlockEntity autopilot, long now, boolean allClear) {
        SableFlight.Body body = SableFlight.body(autopilot);
        if (body != null) {
            show(level, body, new SpaceWeatherShown(kind, startedAt, WARNING_TICKS, kind.stormTicks, seed, from, body.centre(), body.velocity(), true));
        }
        if (allClear) {
            say(level, Aboard.players(autopilot), autopilot.says(SkylinersRegistry.ANNOUNCEMENT_ALL_CLEAR), SoundSource.VOICE);
        }
        bodies.clear(level);
        kind = null;
        told = null;
        rocks = List.of();
        this.autopilot = null;
        strikes.clear();
        SpaceRocks.over(this);
        quietUntil = now + QUIET_TICKS;
    }

    /**
     * The way out across the sky to a bearing, in degrees as the game turns a look: south at 0, west at 90; level, of
     * length 1. Weather comes out of the sky that way and travels the other.
     */
    public static Vec3 toward(float bearing) {
        double turned = Math.toRadians(bearing);
        return new Vec3(-Math.sin(turned), 0, Math.cos(turned));
    }

    /** Nothing told, for the time before any weather blows. */
    private static final class NothingTold implements RockBodies.Told {
        @Override
        public void real(RockReal told) {
        }

        @Override
        public void wornThrough(ServerLevel level, int id, Vec3 at, Vec3 way) {
        }
    }

    /** What its rocks' bodies have to say, passed on. */
    private final class WeatherTold implements RockBodies.Told {
        private final ServerLevel level;

        WeatherTold(ServerLevel level) {
            this.level = level;
        }

        @Override
        public void real(RockReal told) {
            SableFlight.Body body = autopilot == null ? null : SableFlight.body(autopilot);
            if (body != null) {
                show(level, body, told);
            }
        }

        @Override
        public void wornThrough(ServerLevel level, int id, Vec3 at, Vec3 way) {
            SpaceWeather.this.wornThrough(level, id, at, way);
        }
    }

    /** Sends a step of the weather to everyone within {@link #SHOWN_WITHIN} of her, aboard or not. */
    private static void show(ServerLevel level, SableFlight.Body body, CustomPacketPayload shown) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(body.centre()) <= SHOWN_WITHIN) {
                PacketDistributor.sendToPlayer(player, shown);
            }
        }
    }

    /** A sound played to each of those aboard wherever they are in her, as her speakers carry it. */
    private static void say(ServerLevel level, List<ServerPlayer> aboard, Holder<SoundEvent> sound, SoundSource source) {
        long soundSeed = level.getRandom().nextLong();
        for (ServerPlayer player : aboard) {
            player.connection.send(new ClientboundSoundEntityPacket(sound, source, player, 1.0F, 1.0F, soundSeed));
        }
    }
}
