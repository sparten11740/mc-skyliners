package dev.skyliners.spaceweather;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.cabin.TurbulencePayload;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.impact.ImpactJolt;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Space weather, as a rocket or a freighter meets it far past the black of space, from {@link #FROM} up: now and then,
 * at random, a meteor shower, an asteroid storm or a solar flare blows through (see {@link Kind}). Each is told first:
 * an alarm whoops through her, her flight deck says what is coming after the chime (the freighter's computer in words
 * of its own, see {@code CraftFlight#says}), and everyone aboard sees its picture flash on their screen, and on a
 * freighter's scanner, while the first streaks or rocks show far off on the horizon the way it comes from. Then it is
 * on her a while: meteors and rocks stream past, and a few strike her, each seen flying in first, landing with a ping
 * or a crunch, sparks, debris and smoke where it struck, the view of everyone aboard jolted, and she is shoved and set
 * turning there, an asteroid hard enough to throw her well off her spot and swing her round, a light ship harder than a
 * heavy one, and a tall one tipped by a strike high on her nose; or the flare's glare floods the view and its wind
 * pushes her steadily aside. Her autopilot fights her back onto her course and her height. Then she says it is all
 * clear, a green tick showing.
 *
 * <p>No strike breaks a block of hers, nor anyone aboard: she is only shoved, through Sable, no harder than her
 * autopilot can win back. Nothing is saved: weather blowing as the ship is unloaded is simply over. One for each rocket
 * and freighter (see {@code StandingFlight}), worked by her autopilot's block entity each tick; it costs nothing while
 * none blows but a roll of the dice, and only while someone is aboard. Off with the server setting
 * "spaceWeather.enabled"; how often it blows and how hard it throws her are set there too.
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

    /** What blows: how long it is on her, how many strike her and how hard, and how each strike looks and sounds. */
    public enum Kind implements StringRepresentable {
        /**
         * Many small glowing streaks across the sky; a few strike her, each a sharp jolt, a ping off her plates and a
         * spray of sparks.
         */
        METEOR_SHOWER(500, 6, 9, 3.0, 12, 0.5F, 0, 12, SkylinersRegistry.ANNOUNCEMENT_METEORS, SkylinersRegistry.SPACE_METEOR_STRIKE, 64,
            1.2F),
        /**
         * Fewer, big tumbling rocks; one striking her shoves her hard, swings her round, crunches heavily through her,
         * and shakes the view of everyone aboard a good while.
         */
        ASTEROID_STORM(600, 3, 5, 9.0, 30, 1.0F, 2.2F, 30, SkylinersRegistry.ANNOUNCEMENT_ASTEROIDS, SkylinersRegistry.SPACE_ASTEROID_STRIKE,
            192, 1.0F),
        /** A glare over the view, crackling speakers, flickering screens, and a wind that pushes her aside. */
        SOLAR_FLARE(300, 0, 0, 0, 0, 0, 0, 0, SkylinersRegistry.ANNOUNCEMENT_FLARE, SkylinersRegistry.SPACE_METEOR_STRIKE, 0, 1);

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
        /** How long before a strike lands it is seen flying in, in ticks. */
        final int lead;
        /**
         * What her flight deck announces of it, and how a strike sounds, how far off it is heard, in blocks, and its
         * pitch.
         */
        final Holder<SoundEvent> warning;
        final Supplier<SoundEvent> struck;
        final double heard;
        final float pitch;

        Kind(int stormTicks, int fewest, int most, double shove, double turn, float jolt, float rumble, int lead, Holder<SoundEvent> warning,
            Supplier<SoundEvent> struck, double heard, float pitch) {
            this.stormTicks = stormTicks;
            this.fewest = fewest;
            this.most = most;
            this.shove = shove;
            this.turn = turn;
            this.jolt = jolt;
            this.rumble = rumble;
            this.lead = lead;
            this.warning = warning;
            this.struck = struck;
            this.heard = heard;
            this.pitch = pitch;
        }

        /** A kind by its place in the list, as sent; the first for one this build does not know. */
        public static Kind of(int index) {
            Kind[] kinds = values();
            return index >= 0 && index < kinds.length ? kinds[index] : kinds[0];
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
     * A strike planned for a moment of the bout, and once it is seen flying in, where on her it lands and from where.
     */
    private static final class Strike {
        final long at;
        @Nullable
        BlockPos block;
        @Nullable
        Vec3 onHer;
        @Nullable
        Vec3 way;

        Strike(long at) {
            this.at = at;
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
    private float from;
    private long seed;
    /** Its strikes still to come, in order. */
    private final List<Strike> strikes = new ArrayList<>();
    /** Not before this game time does weather blow again. */
    private long quietUntil;
    /**
     * How many strikes have landed on her since she was loaded, and when the last did, in game time, for the play-test.
     */
    private int landed;
    private long lastLanded = -1;

    /**
     * Each tick of an assembled rocket's or freighter's: weather under way goes on; with none, flying above
     * {@link #FROM} with someone aboard, one may begin, about as often as the server's config says.
     */
    public void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        long now = level.getGameTime();
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
            Kind[] kinds = Kind.values();
            begin(level, autopilot, kinds[level.getRandom().nextInt(kinds.length)], now);
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

    /**
     * Weather told: its strikes planned, a slot of the time it is on her each, and everyone aboard and near shown it,
     * the alarm whooping through her for those aboard, whose cockpit holds its floor for the warning to come.
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
        strikes.clear();
        int count = told.fewest + random.nextInt(told.most - told.fewest + 1);
        double slot = (double) told.stormTicks / Math.max(1, count);
        for (int i = 0; i < count; i++) {
            double within = SLOT_MARGIN + random.nextDouble() * (1 - 2 * SLOT_MARGIN);
            strikes.add(new Strike(now + WARNING_TICKS + (long) ((i + within) * slot)));
        }
        show(level, body, new SpaceWeatherShown(told, startedAt, WARNING_TICKS, told.stormTicks, seed, from, body.centre(), body.velocity(), false));
        List<ServerPlayer> aboard = Aboard.players(autopilot);
        say(level, aboard, SkylinersRegistry.SPACE_WEATHER_ALARM, SoundSource.BLOCKS);
        autopilot.holdFloor(WARNING_TICKS);
    }

    /**
     * A tick of the weather under way: her flight deck's warning after the alarm; each strike seen flying in, then
     * landing; a flare's wind and crackle while it is on her; and the all-clear once it has passed, or at once should
     * she come down out of it. Gone quiet with nobody left aboard.
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
        }
        strikes.removeIf(strike -> strikeTick(level, autopilot, strike, now));
        if (height < FROM - LEFT_BY || into >= WARNING_TICKS + kind.stormTicks + LINGER_TICKS) {
            end(level, autopilot, now, true);
        }
    }

    /**
     * A strike's tick: seen flying in so long before it lands, then landing, or missing her if it found no hull.
     * Whether it is done with.
     */
    private boolean strikeTick(ServerLevel level, AutopilotBlockEntity autopilot, Strike strike, long now) {
        if (now == strike.at - kind.lead) {
            aim(level, autopilot, strike);
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
     * Where a strike lands, worked out as it is first seen flying in: from the way the bout comes, give or take, at a
     * spot across her hull, traced in until it meets one of her blocks; everyone near shown it flying in to there.
     */
    private void aim(ServerLevel level, AutopilotBlockEntity autopilot, Strike strike) {
        SableFlight.Body body = SableFlight.body(autopilot);
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
        if (strike.block != null) {
            show(level, body, new IncomingStrike(kind, strike.onHer, way, strike.at, seed ^ strike.at));
        }
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
     * A strike landing where it was aimed: she is shoved along its way and set turning there, as hard as the server's
     * config says; it rings out, sparks and debris burst there among her blocks, and the view of everyone aboard is
     * jolted, an asteroid's shaking it a while after.
     */
    private void land(ServerLevel level, AutopilotBlockEntity autopilot, Strike strike) {
        Vec3 inWorld = SableFlight.toWorld(level, strike.onHer);
        double mass = SableFlight.mass(autopilot);
        double weight = mass > 0 ? Mth.clamp(Math.sqrt(REFERENCE_MASS / mass), HEAVIEST_SHARE, LIGHTEST_SHARE) : 1;
        double strength = SkylinersConfig.SPACE_WEATHER_STRENGTH.get() / FULL_STRENGTH * weight;
        if (strength > 0) {
            SableFlight.strike(level, strike.block, inWorld, strike.way.scale(kind.shove * strength), Math.toRadians(kind.turn * strength));
        }
        landed++;
        lastLanded = level.getGameTime();
        float pitch = kind.pitch * (1 + (level.getRandom().nextFloat() * 2 - 1) * PITCH_WANDER);
        Vec3 at = strike.onHer;
        level.playSound(null, at.x, at.y, at.z, kind.struck.get(), SoundSource.BLOCKS, (float) (kind.heard / SENT_PER_VOLUME), pitch);
        burst(level, at, inWorld);
        for (ServerPlayer player : Aboard.players(autopilot)) {
            double away = player.getEyePosition().distanceTo(inWorld);
            float fade = (float) Mth.lerp(Mth.clamp((away - JOLT_FULL_WITHIN) / JOLT_FADES_OVER, 0, 1), 1, JOLT_LEAST);
            PacketDistributor.sendToPlayer(player, new ImpactJolt(kind.jolt * fade));
            if (kind.rumble > 0) {
                PacketDistributor.sendToPlayer(player, new TurbulencePayload(kind.rumble, RUMBLE_TICKS));
            }
        }
    }

    /**
     * Sparks, embers, debris and smoke bursting where a strike landed, among her blocks so they ride along with her,
     * seen by everyone within {@link #STRIKE_SEEN}: a meteor's a spray of sparks and a flash; an asteroid's a blast of
     * fire, shattered rock and a cloud of smoke.
     */
    private void burst(ServerLevel level, Vec3 at, Vec3 inWorld) {
        boolean rock = kind == Kind.ASTEROID_STORM;
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(inWorld) > STRIKE_SEEN) {
                continue;
            }
            particles(level, player, ParticleTypes.FLASH, at, 1, 0, 0);
            particles(level, player, ParticleTypes.ELECTRIC_SPARK, at, rock ? 30 : 24, SPREAD, SPARK_SPEED);
            particles(level, player, ParticleTypes.LAVA, at, rock ? 16 : 5, SPREAD, 0);
            particles(level, player, rock ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE, at, rock ? 24 : 10, SPREAD, SMOKE_SPEED);
            if (rock) {
                particles(level, player, ParticleTypes.EXPLOSION, at, 3, SPREAD, 0);
                particles(level, player, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TUFF.defaultBlockState()), at, 40, SPREAD * 2, SPARK_SPEED);
            }
        }
    }

    private static void particles(ServerLevel level, ServerPlayer player, ParticleOptions kind, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(player, kind, true, at.x, at.y, at.z, count, spread, spread, spread, speed);
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
     * The weather over: everyone shown so, and, when it passed by rather than was left, her flight deck saying it is
     * all clear to everyone aboard; none blows again for a while.
     */
    private void end(ServerLevel level, AutopilotBlockEntity autopilot, long now, boolean allClear) {
        SableFlight.Body body = SableFlight.body(autopilot);
        if (body != null) {
            show(level, body, new SpaceWeatherShown(kind, startedAt, WARNING_TICKS, kind.stormTicks, seed, from, body.centre(), body.velocity(), true));
        }
        if (allClear) {
            say(level, Aboard.players(autopilot), autopilot.says(SkylinersRegistry.ANNOUNCEMENT_ALL_CLEAR), SoundSource.VOICE);
        }
        kind = null;
        strikes.clear();
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
