package dev.aerolarge.autopilot;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity.Phase;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.item.PilotHelmetItem;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Air traffic control on the flight deck's radio: a man's voice through a crackling VHF receiver, so it is never taken
 * for the flight deck's own, calling the airliner "Aerolarge three eight zero" (tools/generate_sounds.mjs says every
 * line). Set to take off, the autopilot holds the brakes until the tower has cleared it (see {@link #requestTakeOff});
 * climbing away from the ground, flown by the autopilot or by hand, the tower hands it to departure, which has it on
 * radar; set to land, the tower clears it to land, or after a go-around has it climb straight ahead; down and stopped,
 * ground welcomes it in. In flight every few minutes there is chatter: first the hand-over to the next controller, then
 * traffic, the ride ahead, the odd surprise to look out of the window for and other aircraft on the frequency; and now
 * and then the crackle of someone pressing the button.
 *
 * <p>Heard on the flight deck, as far from the autopilot as it tells the flight deck what it does, and anywhere aboard
 * by whoever wears a pilot helmet, its ear cups a headset. Never over anything else said there: it waits for the flight
 * deck's {@link Airtime}, with a pause after it, and never starts where a callout is due before it would end (see
 * {@link Callouts#quietFor}). What waits too long, or no longer fits what the airliner is doing, is not said. Off
 * altogether with {@code atcChatter} in the server config.
 */
final class AtcRadio {
    /**
     * How long each kind of transmission is given, in ticks: tools/generate_sounds.mjs keeps every one shorter (its
     * ATC_CLEARANCE_LONGEST, ATC_LONGEST, ATC_EXCHANGE_LONGEST and ATC_CARRIER_LONGEST). The take-off clearance, which
     * the autopilot holds the brakes for; any other single call; a call and its answer; and a carrier's crackle.
     */
    private static final int CLEARANCE = 5 * 20;
    private static final int LINE = 8 * 20;
    private static final int EXCHANGE = 13 * 20;
    private static final int CARRIER = 2 * 20;
    /** Ticks of quiet on the flight deck before air traffic control talks, after anything else said there. */
    private static final int PAUSE = 2 * 20;
    /** Ticks between a hand-over and the next controller's call: the crew changing frequency and checking in. */
    private static final int CHANGE_OVER = 6 * 20;
    /** The most the autopilot holds the brakes for the take-off clearance to start, in ticks; then it goes anyway. */
    private static final int CLEARANCE_WAIT = 10 * 20;
    /** Ticks a transmission may wait for its turn before it is dropped. */
    private static final int WAIT = 45 * 20;
    /**
     * Blocks above the ground: climbing past the first after lifting off, departure takes over; higher than the second,
     * it is flying, so stopping on the ground after it is an arrival; higher than the third, it is high enough to chat.
     */
    private static final double DEPARTURE_HEIGHT = 20;
    private static final double FLOWN = 10;
    private static final double CHATTER_HEIGHT = 30;
    /** Slower than this, in blocks a second, it has stopped. */
    private static final double STOPPED = 0.5;
    /**
     * Ticks before the chatter in flight: the first, the hand-over, so long after departure has taken over; then each
     * next so long after the last; and between crackles.
     */
    private static final int HAND_OVER_MIN = 45 * 20;
    private static final int HAND_OVER_MAX = 90 * 20;
    private static final int CHATTER_MIN = 2 * 60 * 20;
    private static final int CHATTER_MAX = 5 * 60 * 20;
    private static final int CRACKLE_MIN = 90 * 20;
    private static final int CRACKLE_MAX = 4 * 60 * 20;

    /** What is said: its sound, the ticks it is given, and when it fits. */
    private enum Transmission {
        CLEARED_FOR_TAKEOFF(AerolargeRegistry.ATC_CLEARED_FOR_TAKEOFF, CLEARANCE, Moment.TAKE_OFF),
        CONTACT_DEPARTURE(AerolargeRegistry.ATC_CONTACT_DEPARTURE, LINE, Moment.CLIMB_OUT),
        RADAR_CONTACT(AerolargeRegistry.ATC_RADAR_CONTACT, LINE, Moment.CLIMB_OUT),
        CLEARED_TO_LAND(AerolargeRegistry.ATC_CLEARED_TO_LAND, LINE, Moment.APPROACH),
        GO_AROUND(AerolargeRegistry.ATC_GO_AROUND, LINE, Moment.CLIMB_OUT),
        TAXI_TO_GATE(AerolargeRegistry.ATC_TAXI_TO_GATE, LINE, Moment.ARRIVAL),
        CONTACT_CENTER(AerolargeRegistry.ATC_CONTACT_CENTER, LINE, Moment.EN_ROUTE),
        CENTER_CHECK_IN(AerolargeRegistry.ATC_CENTER_CHECK_IN, LINE, Moment.EN_ROUTE),
        TRAFFIC_TWO_OCLOCK(AerolargeRegistry.ATC_TRAFFIC_TWO_OCLOCK, LINE, Moment.EN_ROUTE),
        TRAFFIC_TEN_OCLOCK(AerolargeRegistry.ATC_TRAFFIC_TEN_OCLOCK, LINE, Moment.EN_ROUTE),
        LIGHT_TURBULENCE(AerolargeRegistry.ATC_LIGHT_TURBULENCE, LINE, Moment.EN_ROUTE),
        SMOOTH_RIDE(AerolargeRegistry.ATC_SMOOTH_RIDE, LINE, Moment.EN_ROUTE),
        GEESE(AerolargeRegistry.ATC_GEESE, LINE, Moment.EN_ROUTE),
        BALLOON(AerolargeRegistry.ATC_BALLOON, LINE, Moment.EN_ROUTE),
        SKYHOPPER_HELLO(AerolargeRegistry.ATC_SKYHOPPER_HELLO, EXCHANGE, Moment.EN_ROUTE),
        CARGO_CLIMB(AerolargeRegistry.ATC_CARGO_CLIMB, EXCHANGE, Moment.EN_ROUTE),
        STATIC_OPEN(AerolargeRegistry.ATC_STATIC_OPEN, CARRIER, Moment.EN_ROUTE),
        STATIC_CLICKS(AerolargeRegistry.ATC_STATIC_CLICKS, CARRIER, Moment.EN_ROUTE);

        private final Holder<SoundEvent> sound;
        private final int ticks;
        private final Moment moment;

        Transmission(Holder<SoundEvent> sound, int ticks, Moment moment) {
            this.sound = sound;
            this.ticks = ticks;
            this.moment = moment;
        }
    }

    /** When a transmission fits what the airliner is doing. */
    private enum Moment {
        /** Set to take off, holding on the runway. */
        TAKE_OFF,
        /** In the air, not coming down to land. */
        CLIMB_OUT,
        /** Coming down to land, not down yet. */
        APPROACH,
        /** On the ground. */
        ARRIVAL,
        /** Flying high enough to chat, not taking off or landing. */
        EN_ROUTE;

        boolean fits(Phase phase, double radio) {
            boolean landing = phase == Phase.LAND || phase == Phase.ROLLOUT;
            return switch (this) {
                case TAKE_OFF -> phase == Phase.TAKEOFF;
                case CLIMB_OUT -> radio > Callouts.ON_GROUND && !landing;
                case APPROACH -> radio > Callouts.ON_GROUND && phase == Phase.LAND;
                case ARRIVAL -> radio < Callouts.ON_GROUND;
                case EN_ROUTE -> radio > CHATTER_HEIGHT && !landing && phase != Phase.TAKEOFF;
            };
        }
    }

    /** The chatter in flight, each one call, or a hand-over and the next controller's; the hand-over comes first. */
    private static final List<List<Transmission>> CHATTER = List.of(
        List.of(Transmission.CONTACT_CENTER, Transmission.CENTER_CHECK_IN),
        List.of(Transmission.TRAFFIC_TWO_OCLOCK),
        List.of(Transmission.TRAFFIC_TEN_OCLOCK),
        List.of(Transmission.LIGHT_TURBULENCE),
        List.of(Transmission.SMOOTH_RIDE),
        List.of(Transmission.GEESE),
        List.of(Transmission.BALLOON),
        List.of(Transmission.SKYHOPPER_HELLO),
        List.of(Transmission.CARGO_CLIMB)
    );
    private static final List<Transmission> CRACKLES = List.of(Transmission.STATIC_OPEN, Transmission.STATIC_CLICKS);

    /** A transmission waiting its turn: after so many ticks of quiet, and not after a game time. */
    private record Waiting(Transmission transmission, int after, long until) {
    }

    private final Airtime airtime;
    private final Callouts callouts;
    private final Deque<Waiting> waiting = new ArrayDeque<>();
    /** The chatter still to come before it all comes round again, in the order it comes. */
    private final List<List<Transmission>> chatter = new ArrayList<>();
    @Nullable
    private List<Transmission> lastChatter;
    /** When the next chatter and the next crackle are due, in game time; -1 while not flying. */
    private long nextChatter = -1;
    private long nextCrackle = -1;
    /** Until when the autopilot holds the brakes for the take-off clearance, in game time. */
    private long clearingUntil;
    /** Whether it has been seen on the ground since departure last took it over, and flying since it last arrived. */
    private boolean grounded;
    private boolean flown;

    AtcRadio(Airtime airtime, Callouts callouts) {
        this.airtime = airtime;
        this.callouts = callouts;
    }

    /**
     * The autopilot is set to take off: the tower is asked to clear it, which it does once nothing else is being said.
     * Whether the autopilot is to hold the brakes while {@link #clearing}; not with air traffic control turned off.
     */
    boolean requestTakeOff(long now) {
        if (!AerolargeConfig.ATC_CHATTER.get()) {
            return false;
        }
        waiting.clear();
        waiting.add(new Waiting(Transmission.CLEARED_FOR_TAKEOFF, PAUSE, now + CLEARANCE_WAIT));
        clearingUntil = now + CLEARANCE_WAIT;
        return true;
    }

    /** Whether the tower has yet to clear the take-off, or is still clearing it. */
    boolean clearing(long now) {
        return now < clearingUntil;
    }

    /** The autopilot is set to land: the tower clears it to land, at once if nothing else is being said. */
    void approach(ServerLevel level, BlockEntity autopilot, SableFlight.State state, double radio) {
        call(level, autopilot, state, radio, Phase.LAND, Transmission.CLEARED_TO_LAND);
    }

    /** The autopilot goes around: the tower has it climb straight ahead, at once if nothing else is being said. */
    void goAround(ServerLevel level, BlockEntity autopilot, SableFlight.State state, double radio) {
        call(level, autopilot, state, radio, Phase.CLIMB, Transmission.GO_AROUND);
    }

    /** Puts a call answering what the crew has just done first in line, and makes it now if nothing else is said. */
    private void call(ServerLevel level, BlockEntity autopilot, SableFlight.State state, double radio, Phase phase, Transmission transmission) {
        if (AerolargeConfig.ATC_CHATTER.get()) {
            waiting.addFirst(new Waiting(transmission, 0, level.getGameTime() + WAIT));
            speak(level, autopilot, state.position(), phase, radio);
        }
    }

    /**
     * Each tick while the airliner is assembled, after the autopilot has flown it: sees it depart, arrive and fly on,
     * queueing what each calls for, and says what is next in line once it may.
     */
    void tick(ServerLevel level, BlockEntity autopilot, SableFlight.State state, double radio, Phase phase) {
        if (!AerolargeConfig.ATC_CHATTER.get()) {
            waiting.clear();
            clearingUntil = 0;
            return;
        }
        long now = level.getGameTime();
        RandomSource random = level.getRandom();
        if (radio > FLOWN) {
            flown = true;
        }
        if (radio < Callouts.ON_GROUND) {
            grounded = true;
            nextChatter = -1;
            nextCrackle = -1;
            if (flown && state.velocity().length() < STOPPED) {
                flown = false;
                waiting.add(new Waiting(Transmission.TAXI_TO_GATE, PAUSE, now + WAIT));
            }
        } else if (grounded && radio > DEPARTURE_HEIGHT && state.velocity().y > 0) {
            grounded = false;
            waiting.add(new Waiting(Transmission.CONTACT_DEPARTURE, PAUSE, now + WAIT));
            waiting.add(new Waiting(Transmission.RADAR_CONTACT, CHANGE_OVER, now + WAIT));
            chatter.clear();
            chatter.add(CHATTER.getFirst());
            nextChatter = now + between(random, HAND_OVER_MIN, HAND_OVER_MAX);
        }
        if (Moment.EN_ROUTE.fits(phase, radio)) {
            chatter(now, random);
        }
        speak(level, autopilot, state.position(), phase, radio);
    }

    /** In flight: queues the next chatter, or a crackle, when it is due and nothing else is waiting. */
    private void chatter(long now, RandomSource random) {
        if (nextChatter < 0) {
            nextChatter = now + between(random, CHATTER_MIN, CHATTER_MAX);
        }
        if (nextCrackle < 0) {
            nextCrackle = now + between(random, CRACKLE_MIN, CRACKLE_MAX);
        }
        if (!waiting.isEmpty()) {
            return;
        }
        if (now >= nextChatter) {
            nextChatter = now + between(random, CHATTER_MIN, CHATTER_MAX);
            List<Transmission> next = nextChatter(random);
            for (int i = 0; i < next.size(); i++) {
                waiting.add(new Waiting(next.get(i), i == 0 ? PAUSE : CHANGE_OVER, now + WAIT));
            }
        } else if (now >= nextCrackle) {
            nextCrackle = now + between(random, CRACKLE_MIN, CRACKLE_MAX);
            waiting.add(new Waiting(CRACKLES.get(random.nextInt(CRACKLES.size())), PAUSE, now + WAIT));
        }
    }

    /** The next chatter, all of it shuffled anew once it has all been said, never the same twice running. */
    private List<Transmission> nextChatter(RandomSource random) {
        if (chatter.isEmpty()) {
            chatter.addAll(CHATTER);
            Util.shuffle(chatter, random);
            if (chatter.getFirst() == lastChatter) {
                chatter.add(chatter.removeFirst());
            }
        }
        lastChatter = chatter.removeFirst();
        return lastChatter;
    }

    /**
     * Says what is next in line once the flight deck has been quiet long enough and no callout is due before it would
     * end; first drops whatever has waited too long or no longer fits.
     */
    private void speak(ServerLevel level, BlockEntity autopilot, Vec3 flightDeck, Phase phase, double radio) {
        long now = level.getGameTime();
        while (!waiting.isEmpty()) {
            Waiting next = waiting.peekFirst();
            Transmission transmission = next.transmission();
            if (now > next.until() || !transmission.moment.fits(phase, radio)) {
                waiting.removeFirst();
                continue;
            }
            if (airtime.quietFor(now, next.after()) && callouts.quietFor(transmission.ticks)) {
                waiting.removeFirst();
                transmit(level, autopilot, flightDeck, transmission);
            }
            return;
        }
    }

    private void transmit(ServerLevel level, BlockEntity autopilot, Vec3 flightDeck, Transmission transmission) {
        long now = level.getGameTime();
        airtime.take(now, transmission.ticks);
        if (transmission == Transmission.CLEARED_FOR_TAKEOFF) {
            clearingUntil = now + transmission.ticks;
        }
        float volume = AerolargeConfig.ATC_VOLUME.get().floatValue();
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : level.players()) {
            if (hears(player, autopilot, flightDeck)) {
                player.connection.send(new ClientboundSoundEntityPacket(transmission.sound, SoundSource.VOICE, player, volume, 1.0F, seed));
            }
        }
    }

    /** Whether a player hears the radio: on the flight deck, or aboard wearing a pilot helmet's headset. */
    private static boolean hears(ServerPlayer player, BlockEntity autopilot, Vec3 flightDeck) {
        return player.position().distanceToSqr(flightDeck) < AutopilotBlockEntity.FLIGHT_DECK * AutopilotBlockEntity.FLIGHT_DECK
            || PilotHelmetItem.worn(player) && Aboard.isAboard(player, autopilot);
    }

    private static long between(RandomSource random, int least, int most) {
        return least + random.nextInt(most - least + 1);
    }
}
