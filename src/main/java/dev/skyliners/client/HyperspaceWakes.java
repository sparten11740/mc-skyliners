package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.HyperspaceWake;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A freighter's hyperspace jump as those outside her see and hear it, on the ground or aboard another ship (see
 * {@link HyperspaceWake}): the tardigrade's spore-drive jump, its streaks of light and its whump, grown to a ship her
 * size. As she lets go, short streaks of light rush in to her hull from all round, more and more, keeping up with her
 * as she flies on, over a deep hum rising. Then she is gone in a white flash: long streaks shoot off ahead of where she
 * was, the way she pointed, glitter hangs in the air there and a ring of light closes in on the hole she left, with a
 * whoomph heard far off. Where she comes out, streaks rush in from behind her over a swelling rush and are gone into
 * the spot, and she is there in a flash, a ring of streaks bursting out round her and glitter, with a boom. The nearer
 * the onlooker, the brighter a faint white flash over their view as she goes and as she comes out.
 *
 * <p>One message from the server a step, every particle made here; the streaks heed the player's particle setting by
 * themselves, and the flashes and the glitter are thinned out here, as they are shown past the game's own limit on
 * particles.
 */
public final class HyperspaceWakes {
    /**
     * Light gathering on her as she lets go: how many streaks rush in to her a tick at the end, fewer at first; how far
     * out from her middle they start and how far in toward it they end, as shares of how far her hull reaches; and how
     * flat the shell they start on is, as a share of its width.
     */
    private static final int GATHER_STREAKS = 14;
    private static final double GATHER_FROM = 1.6;
    private static final double GATHER_TO = 0.5;
    private static final double GATHER_FLAT = 0.35;
    /**
     * Going: how many long streaks shoot off ahead, how fast at least and at most, in blocks a tick, and how far they
     * stray from straight ahead; how thick the slice of her they start from is, as a share of how far her hull reaches.
     */
    private static final int OUT_STREAKS = 120;
    private static final double OUT_SLOWEST = 7;
    private static final double OUT_FASTEST = 11;
    private static final double OUT_STRAY = 0.08;
    private static final double HULL_THICK = 0.2;
    /**
     * Coming out: how many streaks rush in to where she comes out, from behind her and round, how fast, in blocks a
     * tick, and how far they stray from her way, so they close in from a wide cone; and over how many ticks they are
     * sent, each to be gone into the spot as she is there.
     */
    private static final int IN_STREAKS = 80;
    private static final double IN_SPEED = 9;
    private static final double IN_STRAY = 0.6;
    private static final int IN_SPREAD = 6;
    /**
     * The ring of streaks in her plane, closing in on where she was as she goes and bursting out round her as she comes
     * out: how many; how far out the closing ring starts, as a share of how far her hull reaches, to close on her middle
     * as its streaks' heads stop; and how fast the bursting ring races out from her rim, in blocks a tick.
     */
    private static final int RING_STREAKS = 72;
    private static final double RING_CLOSES_FROM = 1.5;
    private static final double RING_SPEED = 3.5;
    /**
     * The flash where she goes and comes out: one bright glow at her middle and so many round it, so far out, as a
     * share of how far her hull reaches.
     */
    private static final int FLASHES_ROUND = 6;
    private static final double FLASH_OUT = 0.6;
    /** The glitter hanging where she was and bursting round where she is: how many specks, and how fast, in blocks a tick. */
    private static final int GLITTER = 100;
    private static final double GLITTER_SPEED = 0.35;
    /**
     * The faint white flash over an onlooker's view: how opaque at most right by her, falling off to nothing at the edge
     * of where a jump is seen, and how long it takes to clear, in ticks.
     */
    private static final float FLASH_NEAREST = 0.45F;
    private static final int FLASH_TICKS = 14;
    /** The share of the flashes and the glitter shown with the particle setting at Decreased: half; at Minimal, none. */
    private static final float DECREASED_SHARE = 0.5F;
    /** How loud her jump is heard, as far as its sound reaches (see its {@code heard}, in blocks, in sounds.json). */
    private static final float VOLUME = 1.0F;
    /**
     * How much later than the server a client shows a ship where she is, in ticks, as Sable smooths her flight between
     * the places it is sent: the flash where she goes and comes out and the streaks rushing in wait as long, so she is
     * gone inside the flash and comes out as the streaks are gone into the spot.
     */
    private static final int SHOWN_LATER = 4;

    /** The steps leading up to a jump or a coming out under way on this client, and the level they are in. */
    private static final List<Wake> WAKES = new ArrayList<>();
    private static WeakReference<ClientLevel> shownIn = new WeakReference<>(null);
    /** When the last flash over the view began on this client's drawing clock, and how opaque it is at most. */
    private static double flashBegan;
    private static float flashStrength;

    /** A step told by the server, the tick it began, and where she was shown last tick, for how fast she flies. */
    private static final class Wake {
        private final HyperspaceWake told;
        private final long began;
        @Nullable
        private Vec3 last;

        private Wake(HyperspaceWake told, long began) {
            this.told = told;
            this.began = began;
        }
    }

    private HyperspaceWakes() {
    }

    /** A step of a jump seen from outside: its sound now, and what is seen of it from now on (see {@link #tick}). */
    public static void seen(HyperspaceWake wake) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (level != shownIn.get()) {
            WAKES.clear();
            shownIn = new WeakReference<>(level);
        }
        WAKES.add(new Wake(wake, DrawingClock.wholeTicks()));
        if (wake.stage() == HyperspaceWake.Stage.GATHER) {
            hear(level, SkylinersRegistry.FREIGHTER_HYPERSPACE_OUT.get(), wake.at());
        } else if (wake.stage() == HyperspaceWake.Stage.INBOUND) {
            hear(level, SkylinersRegistry.FREIGHTER_HYPERSPACE_IN.get(), wake.at());
        }
    }

    /** Once a client tick: the steps under way played on, those over let go. */
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != shownIn.get()) {
            WAKES.clear();
            return;
        }
        long now = DrawingClock.wholeTicks();
        WAKES.removeIf(wake -> now - wake.began > wake.told.ticks() + SHOWN_LATER);
        for (Wake wake : WAKES) {
            int age = (int) (now - wake.began);
            switch (wake.told.stage()) {
                case GATHER -> gather(level, wake, age);
                case INBOUND -> rushIn(level, wake.told, age - SHOWN_LATER);
                case DEPART -> burstOnce(level, wake.told, age, false);
                case ARRIVE -> burstOnce(level, wake.told, age, true);
            }
        }
    }

    /** Where she goes or comes out, once, as long after the server says so as the client shows her later. */
    private static void burstOnce(ClientLevel level, HyperspaceWake told, int age, boolean arriving) {
        if (age != SHOWN_LATER) {
            return;
        }
        burst(level, told);
        if (arriving) {
            arrive(level, told);
        } else {
            depart(level, told);
        }
    }

    /** The faint flash over the view as she goes or comes out, clearing. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ClientLevel level = Minecraft.getInstance().level;
        if (flashStrength <= 0 || level == null || level != shownIn.get()) {
            return;
        }
        float fade = Math.max(0, 1 - (float) (DrawingClock.ticks(deltaTracker.getGameTimeDeltaPartialTick(false)) - flashBegan) / FLASH_TICKS);
        float opacity = flashStrength * fade * fade;
        if (opacity > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(opacity * 255) << 24 | 0xFFFFFF);
        }
    }

    /**
     * A tick of light gathering on her as she lets go: streaks from a flattened shell round her rushing in toward her
     * hull, more each tick, carried on as fast as she flies so they keep up with her. Where the client has her, she is
     * followed among her blocks; elsewhere they gather where she was as she let go.
     */
    private static void gather(ClientLevel level, Wake wake, int age) {
        HyperspaceWake told = wake.told;
        if (age > told.ticks()) {
            return;
        }
        Vec3 shown = SableClient.toWorld(level, told.aboard());
        Vec3 middle = shown.distanceToSqr(told.aboard()) < 1 ? told.at() : shown;
        Vec3 flying = wake.last == null ? Vec3.ZERO : middle.subtract(wake.last);
        wake.last = middle;
        RandomSource random = level.getRandom();
        int streaks = Math.round(GATHER_STREAKS * (age + 1) / (float) Math.max(1, told.ticks()));
        for (int streak = 0; streak < streaks; streak++) {
            Vec3 from = middle.add(round(random, told.reach() * GATHER_FROM, GATHER_FLAT));
            Vec3 to = middle.add(round(random, told.reach() * GATHER_TO * random.nextDouble(), GATHER_FLAT));
            Vec3 speed = to.subtract(from).scale(1.0 / HyperspaceStreakParticle.MOVE_TICKS).add(flying);
            spawn(level, SkylinersRegistry.HYPERSPACE_STREAK.get(), from, speed);
        }
    }

    /**
     * A tick of the streaks rushing in to where she is to come out, from behind her and round: those sent this tick
     * shrink into the spot as their lives end, as she comes out there.
     */
    private static void rushIn(ClientLevel level, HyperspaceWake told, int age) {
        int lives = HyperspaceStreakParticle.MOVE_TICKS + HyperspaceStreakParticle.LAG;
        int first = told.ticks() - lives - IN_SPREAD;
        if (age < first || age > told.ticks() - lives) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int streak = 0; streak < IN_STREAKS / (IN_SPREAD + 1); streak++) {
            Vec3 to = told.at().add(round(random, told.reach() * FLASH_OUT * random.nextDouble(), HULL_THICK));
            Vec3 way = strayed(random, told.ahead(), IN_STRAY);
            Vec3 speed = way.scale(IN_SPEED);
            spawn(level, SkylinersRegistry.HYPERSPACE_STREAK.get(), to.subtract(speed.scale(HyperspaceStreakParticle.MOVE_TICKS)), speed);
        }
    }

    /** Where she goes or comes out: the flash at her middle and round it, the glitter, and over the view, faintly. */
    private static void burst(ClientLevel level, HyperspaceWake told) {
        RandomSource random = level.getRandom();
        float share = share();
        if (share > 0) {
            spawn(level, ParticleTypes.FLASH, told.at(), Vec3.ZERO);
        }
        for (int flash = 0; flash < Math.round(FLASHES_ROUND * share); flash++) {
            double way = (flash + random.nextDouble() * 0.5) * Mth.TWO_PI / FLASHES_ROUND;
            Vec3 at = told.at().add(Math.cos(way) * told.reach() * FLASH_OUT, 0, Math.sin(way) * told.reach() * FLASH_OUT);
            spawn(level, ParticleTypes.FLASH, at, Vec3.ZERO);
        }
        for (int speck = 0; speck < Math.round(GLITTER * share); speck++) {
            Vec3 at = told.at().add(round(random, told.reach() * random.nextDouble(), HULL_THICK));
            Vec3 speed = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(GLITTER_SPEED);
            spawn(level, ParticleTypes.END_ROD, at, speed);
        }
        Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float near = (float) Math.max(0, 1 - eye.distanceTo(told.at()) / HyperspaceWake.SEEN_WITHIN);
        flashStrength = FLASH_NEAREST * near * near;
        flashBegan = DrawingClock.ticks(0);
    }

    /** Gone: long streaks shooting off ahead from all through her hull, and a ring of light closing in where she was. */
    private static void depart(ClientLevel level, HyperspaceWake told) {
        RandomSource random = level.getRandom();
        for (int streak = 0; streak < OUT_STREAKS; streak++) {
            Vec3 from = told.at().add(round(random, told.reach() * Math.sqrt(random.nextDouble()), HULL_THICK));
            Vec3 speed = strayed(random, told.ahead(), OUT_STRAY).scale(Mth.lerp(random.nextDouble(), OUT_SLOWEST, OUT_FASTEST));
            spawn(level, SkylinersRegistry.HYPERSPACE_STREAK.get(), from, speed);
        }
        ring(level, told, false);
    }

    /** Out: a ring of streaks bursting out round her. */
    private static void arrive(ClientLevel level, HyperspaceWake told) {
        ring(level, told, true);
    }

    /**
     * A ring of streaks in her plane: bursting out from her rim, or closing in from further out on her middle, gone into
     * it as they stop.
     */
    private static void ring(ClientLevel level, HyperspaceWake told, boolean bursting) {
        RandomSource random = level.getRandom();
        for (int streak = 0; streak < RING_STREAKS; streak++) {
            double way = (streak + random.nextDouble()) * Mth.TWO_PI / RING_STREAKS;
            Vec3 out = new Vec3(Math.cos(way), (random.nextDouble() - 0.5) * HULL_THICK, Math.sin(way));
            double from = told.reach() * (bursting ? 1 : RING_CLOSES_FROM);
            Vec3 speed = bursting ? out.scale(RING_SPEED) : out.scale(-from / HyperspaceStreakParticle.MOVE_TICKS);
            spawn(level, SkylinersRegistry.HYPERSPACE_STREAK.get(), told.at().add(out.scale(from)), speed);
        }
    }

    /** A point at random so far out round a middle, in a shell flattened to so much of its width up and down. */
    private static Vec3 round(RandomSource random, double out, double flat) {
        Vec3 way = new Vec3(random.nextGaussian(), random.nextGaussian() * flat, random.nextGaussian());
        return way.lengthSqr() < 1e-6 ? Vec3.ZERO : way.normalize().scale(out);
    }

    /** A way strayed from another at random by up to so much, as a share of its length, made a unit long again. */
    private static Vec3 strayed(RandomSource random, Vec3 way, double stray) {
        Vec3 off = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).scale(2 * stray);
        Vec3 strayed = way.add(off);
        return strayed.lengthSqr() < 1e-6 ? way : strayed.normalize();
    }

    /** One particle, shown however far off it is: those that heed the particle setting do so by themselves. */
    private static void spawn(ClientLevel level, ParticleOptions kind, Vec3 at, Vec3 speed) {
        level.addParticle(kind, true, at.x, at.y, at.z, speed.x, speed.y, speed.z);
    }

    /** The share of the flashes and glitter shown, by the player's particle setting. */
    private static float share() {
        return switch (Minecraft.getInstance().options.particles().get()) {
            case ALL -> 1;
            case DECREASED -> DECREASED_SHARE;
            case MINIMAL -> 0;
        };
    }

    /** One of her jump's sounds, heard from where it happens, as far as it reaches. */
    private static void hear(ClientLevel level, SoundEvent sound, Vec3 at) {
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound, SoundSource.BLOCKS, VOLUME, 1.0F, level.getRandom(), at.x, at.y, at.z));
    }
}
