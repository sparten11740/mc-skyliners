package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Announcement;
import dev.skyliners.compat.SableFlight;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * The rocket in her autopilot's hands (see {@link CraftFlight}): a countdown, lift-off and a hop flown by
 * {@link RocketPilot}, launch control calling her milestones (see {@link LaunchCallouts}), everyone aboard welcomed to
 * space at its edge (see {@link EdgeOfSpace}) and floating there until her gravity is announced (see
 * {@link SpaceGravity}), her plumes, ground fire, scorch and shockwave, and the controls on her centre console (see
 * {@link RocketControls}). Her stories' floors and whether they were furnished go with her, for the clients to paint
 * and play her rooms by.
 */
public final class RocketFlight extends StandingFlight {
    /**
     * The pitch her countdown notes start at (1 the note as it is): they rise from it each second, the last before
     * lift-off one whole higher.
     */
    private static final float COUNTDOWN_LOWEST = 0.5F;
    /** How loud the countdown's notes are, quietly under launch control's voice, as a share of full. */
    private static final float COUNTDOWN_VOLUME = 0.4F;
    /** How loud her abort handle's ratchet and her launch key's click in its lock are. */
    private static final float RATCHET_VOLUME = 0.9F;
    private static final float KEY_VOLUME = 0.9F;
    /**
     * How long launch control's "Boost!" is given to play if its sound cannot be read, in ticks:
     * tools/generate_sounds.mjs keeps it shorter (its MILESTONE_LONGEST).
     */
    private static final int BOOST_CALL_LONGEST = 50;
    /**
     * The controls on a centre console as they stand untouched, shown for any craft but a rocket; only ever read, by
     * whatever draws a craft's flame or roar, never worked.
     */
    private static final RocketControls UNTOUCHED = new RocketControls();

    private final RocketPilot pilot = new RocketPilot();
    /** Her shockwave as her engines light on the ground, racing out over it. */
    private final RocketLaunchBlast launchBlast = new RocketLaunchBlast();
    /** How far into her countdown it last sounded it, in ticks, so each second is heard once. */
    private int countHeard;
    /** What is heard of her as she flies, and how hard her engines fire as the clients are shown it. */
    private final RocketSounds rocketSounds;
    /** Launch control calling out her milestones over the radio as she passes them. */
    private final LaunchCallouts launchCallouts = new LaunchCallouts();
    /**
     * Her stories' floors, rows up from her fins' feet, from the entry story up, the cockpit's floor last, as she was
     * built, on the clients as on the server, so her livery can follow them (see {@link
     * dev.skyliners.airframe.Livery.RocketSpot}); none for one built before they were kept.
     */
    private int[] stories = new int[0];
    /**
     * Whether her stories were furnished as her rooms when she was built, on the clients as on the server, so they hear
     * her greenhouse and her pool only where they are (see the client's {@code RoomAmbience}); one built before it was
     * kept was furnished, as one is by default.
     */
    private boolean rooms = true;

    /** Her flight, its sounds taking their turn on the flight deck's air time. */
    RocketFlight(Airtime airtime) {
        rocketSounds = new RocketSounds(airtime);
    }

    @Override
    public Craft craft() {
        return Craft.ROCKET;
    }

    /**
     * Wired when she is built: her engine bells' relays and her two rings of side jets as offsets from the autopilot,
     * her stories' floors, as rows over her fins' feet, and whether they were furnished as her rooms.
     */
    public void configure(List<List<BlockPos>> bells, RocketPilot.Jets top, RocketPilot.Jets bottom, int[] stories,
                          boolean rooms) {
        this.stories = stories.clone();
        this.rooms = rooms;
        pilot.configure(bells, top, bottom);
    }

    /** Her stories' floors as she was built, rows up from her fins' feet; none if they are not known. */
    public int[] stories() {
        return stories.clone();
    }

    /** Whether her stories were furnished as her rooms when she was built. */
    public boolean rooms() {
        return rooms;
    }

    /** What her pilot is doing, for play-testing. */
    public RocketPilot.Report report() {
        return pilot.report();
    }

    /** How hard the engines of the rocket an autopilot flies fire, as it shows it; none for any other craft's. */
    public static float thrustOf(AutopilotBlockEntity autopilot) {
        RocketFlight rocket = autopilot.flight(RocketFlight.class);
        return rocket == null ? 0 : rocket.thrust();
    }

    /**
     * The controls on the centre console of the rocket an autopilot flies; as they stand untouched for any other
     * craft's.
     */
    public static RocketControls controlsOf(AutopilotBlockEntity autopilot) {
        RocketFlight rocket = autopilot.flight(RocketFlight.class);
        return rocket == null ? UNTOUCHED : rocket.controls();
    }

    /** The stories' floors of the rocket an autopilot flies, as she was built; none for any other craft's. */
    public static int[] storiesOf(AutopilotBlockEntity autopilot) {
        RocketFlight rocket = autopilot.flight(RocketFlight.class);
        return rocket == null ? new int[0] : rocket.stories();
    }

    /**
     * Whether the stories of the rocket an autopilot flies were furnished as her rooms; as they are by default for any
     * other's.
     */
    public static boolean roomsOf(AutopilotBlockEntity autopilot) {
        RocketFlight rocket = autopilot.flight(RocketFlight.class);
        return rocket == null || rocket.rooms();
    }

    /** How hard her engines fire, on the clients as on the server, whose roar follows it: a share of full. */
    public float thrust() {
        return rocketSounds.thrust();
    }

    /**
     * The controls on her centre console as they are set, on the clients as on the server, whose displays, roar and
     * plume show them (see {@link RocketControls}).
     */
    public RocketControls controls() {
        return pilot.controls();
    }

    /** What her centre console's controls show, as each reads it every few ticks (see {@link RocketControls.Panel}). */
    public RocketControls.Panel panel(AutopilotBlockEntity autopilot) {
        Level level = autopilot.getLevel();
        if (level == null) {
            return RocketControls.Panel.NONE;
        }
        AutopilotBlockEntity.Phase phase = autopilot.phase;
        boolean abortable = phase != AutopilotBlockEntity.Phase.OFF && RocketControls.abortable(phase, pilot.radio());
        boolean on = phase != AutopilotBlockEntity.Phase.OFF;
        return pilot.controls().panel(level.getGameTime(), phase, pilot.counted(), light(), abortable, on);
    }

    /**
     * One of the controls on a rocket's centre console worked by a player on the autopilot it is linked to, on the
     * server: her flight works it (see {@link #work}); on any other craft's autopilot it only thuds, saying so. Whether
     * it was taken.
     */
    public static boolean control(AutopilotBlockEntity autopilot, RocketControls.Control control, ServerPlayer player) {
        RocketFlight rocket = autopilot.flight(RocketFlight.class);
        if (!(autopilot.getLevel() instanceof ServerLevel server) || rocket == null) {
            autopilot.thud();
            player.displayClientMessage(Component.translatable("message.skyliners.rocket_controls.not_rocket"), true);
            return false;
        }
        return rocket.work(server, autopilot, control, player);
    }

    /**
     * One of the controls on her centre console worked by a player (see {@link RocketControls}): the boost button, the
     * stability switch and the flight mode knob are worked by her controls themselves; the abort handle calls off her
     * landing or stops her where she is (see {@link #abort}), and the launch key launches her or scrubs her countdown
     * (see {@link #key}). Each answers the player with a sound and a word in their hotbar; a boost, launch control
     * calls too (see {@link #callBoost}). Whether it was taken.
     */
    private boolean work(ServerLevel server, AutopilotBlockEntity autopilot, RocketControls.Control control, ServerPlayer player) {
        AutopilotBlockEntity.Phase phase = autopilot.phase;
        BlockPos origin = autopilot.getBlockPos();
        SableFlight.State state = SableFlight.read(autopilot, autopilot.nose());
        boolean taken = switch (control) {
            case BOOST -> pilot.controls().boost(server, origin, player, phase, pilot.counted(), light());
            case STABILITY -> pilot.controls().stability(server, origin, player, phase, pilot.radio(), RocketPilot.across(state));
            case MODE -> pilot.controls().turn(server, origin, player, phase);
            case ABORT -> abort(server, autopilot, player, state);
            case KEY -> key(server, autopilot, player, state);
        };
        if (taken && control == RocketControls.Control.BOOST) {
            callBoost(server, autopilot, state);
        }
        if (pilot.controls().takeChanged()) {
            autopilot.changed();
        }
        return taken;
    }

    /**
     * Launch control calls "Boost!" over her radio as her boost begins, as its milestones are (see
     * {@link LaunchCallouts}): only into a quiet cockpit, holding the floor while it lasts, and not with the server
     * config's {@code pilot.callouts} off.
     */
    private void callBoost(ServerLevel level, AutopilotBlockEntity autopilot, @Nullable SableFlight.State state) {
        if (state != null && autopilot.quiet() && SkylinersConfig.ROCKET_CALLOUTS.get()) {
            autopilot.holdFloor(VoiceLength.ticks(SkylinersRegistry.LAUNCH_BOOST, BOOST_CALL_LONGEST));
            RocketSounds.overTheRadio(level, state.position(), SkylinersRegistry.LAUNCH_BOOST);
        }
    }

    /**
     * Her abort handle pulled, with a ratchet's clatter: counting down, her countdown is scrubbed; coming down (in her
     * landing burn only while still {@link RocketControls#ABORT_LOWEST} up), her landing is called off where she is,
     * and she climbs back to her height set and holds it there (see {@link AutopilotBlockEntity#goAround}); climbing or
     * cruising, she stops where she is and holds the height she coasts up to, until given a pin or told to land;
     * otherwise a thud. Whether it did anything.
     */
    private boolean abort(ServerLevel level, AutopilotBlockEntity autopilot, ServerPlayer player, @Nullable SableFlight.State state) {
        AutopilotBlockEntity.Phase phase = autopilot.phase;
        if (state == null || phase == AutopilotBlockEntity.Phase.OFF || !RocketControls.abortable(phase, pilot.radio())) {
            autopilot.thud();
            player.displayClientMessage(Component.translatable("message.skyliners.rocket_controls.abort_nothing"), true);
            return false;
        }
        if (phase == AutopilotBlockEntity.Phase.TAKEOFF && pilot.counted() < RocketPilot.COUNTDOWN) {
            return scrub(autopilot, player);
        }
        level.playSound(null, autopilot.getBlockPos(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.BLOCKS, RATCHET_VOLUME, 1.0F);
        boolean landing = phase == AutopilotBlockEntity.Phase.DESCENT || phase == AutopilotBlockEntity.Phase.LAND;
        // Where she is, rather than where she was going: no pin, and back over here should she drift.
        pilot.landHere(state);
        autopilot.lateral = AutopilotBlockEntity.Lateral.HEADING;
        autopilot.landAtPin = false;
        if (landing) {
            autopilot.goAround(level);
        } else {
            pilot.holdHere();
            double floor = AutopilotBlockEntity.lowestAltitude(autopilot.runwayAltitude);
            autopilot.targetAltitude = Mth.clamp(RocketPilot.coastsTo(state), floor, Math.max(floor, autopilot.ceiling(level)));
            level.playSound(null, autopilot.getBlockPos(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, AutopilotBlockEntity.NOTE_VOLUME,
                AutopilotBlockEntity.NOTE_HIGH);
        }
        autopilot.changed();
        String said = landing ? "message.skyliners.rocket_controls.abort_go_around" : "message.skyliners.rocket_controls.abort_hold";
        player.displayClientMessage(Component.translatable(said, Math.round(autopilot.targetAltitude)), true);
        return true;
    }

    /**
     * Her launch key turned, with the click of a key in its lock: counting down (or waiting to), her countdown is
     * scrubbed; on the ground with the autopilot off, she flies what it was last set to (see {@link #keyed}), assembled
     * first if she stands as blocks, the usual holds on her countdown holding it as ever; up and flying, it is stuck,
     * and only thuds. Whether it did anything.
     */
    private boolean key(ServerLevel level, AutopilotBlockEntity autopilot, ServerPlayer player, @Nullable SableFlight.State state) {
        AutopilotBlockEntity.Phase phase = autopilot.phase;
        if (phase == AutopilotBlockEntity.Phase.TAKEOFF && pilot.counted() < RocketPilot.COUNTDOWN) {
            return scrub(autopilot, player);
        }
        boolean grounded = state == null || autopilot.radioAltitude(level, state) <= AutopilotBlockEntity.AIRBORNE;
        if (phase != AutopilotBlockEntity.Phase.OFF || !grounded) {
            autopilot.thud();
            player.displayClientMessage(Component.translatable("message.skyliners.rocket_controls.key_stuck"), true);
            return false;
        }
        level.playSound(null, autopilot.getBlockPos(), SoundEvents.VAULT_INSERT_ITEM, SoundSource.BLOCKS, KEY_VOLUME, 1.0F);
        AutopilotBlockEntity.Settings settings = keyed(level, autopilot, state);
        Component assembling = autopilot.assembleToFly(player, settings);
        player.displayClientMessage(assembling != null ? assembling : autopilot.set(SetAutopilot.Action.FLY, settings), true);
        return autopilot.phase != AutopilotBlockEntity.Phase.OFF || autopilot.assembling();
    }

    /** Her countdown scrubbed: switched off, with falling notes rather than the autopilot's own chime. */
    private boolean scrub(AutopilotBlockEntity autopilot, ServerPlayer player) {
        autopilot.disengage(SkylinersRegistry.ROCKET_SCRUB.get());
        player.displayClientMessage(Component.translatable("message.skyliners.rocket_controls.scrubbed"), true);
        return true;
    }

    /**
     * What her launch key flies: what her autopilot was last set to, her height and her pin, from where she stands;
     * what engaging would fly where no height was set, or the one set is under the lowest she may hold here.
     */
    private static AutopilotBlockEntity.Settings keyed(Level level, AutopilotBlockEntity autopilot, @Nullable SableFlight.State state) {
        AutopilotBlockEntity.Plan plan = autopilot.plan(level, state);
        AutopilotBlockEntity.Settings fresh = autopilot.offered(plan);
        boolean pinned = autopilot.lateral == AutopilotBlockEntity.Lateral.DESTINATION;
        int altitude = autopilot.targetAltitude >= AutopilotBlockEntity.lowestAltitude(plan.runway()) ? (int) Math.round(autopilot.targetAltitude)
            : fresh.altitude();
        return new AutopilotBlockEntity.Settings(fresh.speed(), altitude, fresh.heading(),
            pinned ? AutopilotBlockEntity.Lateral.DESTINATION : AutopilotBlockEntity.Lateral.HEADING, autopilot.circle,
            pinned ? BlockPos.containing(autopilot.orbitX, 0, autopilot.orbitZ) : fresh.destination(), false);
    }

    @Override
    boolean wired() {
        return pilot.wired();
    }

    @Override
    boolean assembledThroughSable() {
        return true;
    }

    @Override
    String unwiredKey() {
        return "message.skyliners.autopilot.no_engines";
    }

    @Override
    String assemblingKey() {
        return "message.skyliners.autopilot.assembling_rocket";
    }

    @Override
    String offKey() {
        return "message.skyliners.autopilot.off_rocket";
    }

    @Override
    List<BlockPos> links() {
        return pilot.links();
    }

    @Override
    void turn(Rotation turn) {
        pilot.turn(turn);
    }

    @Override
    void save(CompoundTag tag) {
        tag.put("Rocket", pilot.save());
    }

    @Override
    void load(CompoundTag tag, Rotation turn) {
        pilot.load(tag.getCompound("Rocket"), turn);
    }

    @Override
    void saveShared(CompoundTag tag) {
        if (stories.length > 0) {
            tag.putIntArray("RocketStories", stories);
            tag.putBoolean("RocketRooms", rooms);
        }
    }

    @Override
    void loadShared(CompoundTag tag) {
        stories = tag.getIntArray("RocketStories");
        rooms = !tag.contains("RocketRooms") || tag.getBoolean("RocketRooms");
    }

    @Override
    void saveShown(CompoundTag tag) {
        rocketSounds.saveShown(tag);
        pilot.controls().saveShown(tag);
    }

    @Override
    void loadShown(CompoundTag tag) {
        rocketSounds.loadShown(tag);
        pilot.controls().loadShown(tag);
    }

    @Override
    void engage(AutopilotBlockEntity autopilot, SableFlight.State state) {
        pilot.begin(state);
        countHeard = -1;
        launchCallouts.begin();
    }

    /**
     * Each tick of hers assembled: she is kept loaded, as a zeppelin is; lightning strikes her in a storm and she notes
     * the villages below; everyone aboard is welcomed to space as she climbs past its edge (see {@link EdgeOfSpace});
     * engaged, she flies (see {@link RocketPilot}), launch control calling out her milestones (see
     * {@link LaunchCallouts}), until she has come down on her fins and stood there a moment, switching herself off with
     * a bell.
     */
    @Override
    void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        BlockPos origin = autopilot.getBlockPos();
        int wheelsBelow = autopilot.wheelsBelow;
        double radio = aloft(level, autopilot, flight);
        RocketPlumes.tick(level, flight, radio, wheelsBelow, pilot.report().thrust());
        RocketGroundFire.tick(level, flight, radio, wheelsBelow, pilot.report().thrust());
        RocketScorch.tick(level, flight, radio, wheelsBelow, pilot.report().thrust());
        launchBlast.tick(level, autopilot, flight, radio, wheelsBelow, pilot.report().thrust());
        AutopilotBlockEntity.Phase phase = autopilot.phase;
        // Launch control counts her down only once the cabin has heard its take-off announcement (see RocketSounds),
        // and nothing else holds her countdown, as her rover out of its bay does.
        boolean held = rocketSounds.holdsCountdown(phase, pilot.counted(), autopilot.quiet())
            || RocketPilot.countdownHeld(level, origin, phase, pilot.counted());
        if (phase != AutopilotBlockEntity.Phase.OFF && !held) {
            boolean toPin = autopilot.lateral == AutopilotBlockEntity.Lateral.DESTINATION;
            RocketPilot.Orders orders = new RocketPilot.Orders(phase, autopilot.targetAltitude, toPin, autopilot.orbitX, autopilot.orbitZ,
                spaceWeather().blowing());
            AutopilotBlockEntity.Phase next = pilot.fly(level, origin, orders, flight, radio, SableFlight.mass(autopilot));
            countDown(level, autopilot, flight);
            // Before the cabin is told she lands, so that its announcement waits for launch control's "Landing burn.".
            launchCallouts.watch(level, autopilot, phase, next, flight, radio);
            if (next == AutopilotBlockEntity.Phase.OFF) {
                pilot.stop(level, origin);
                autopilot.settle(level);
            } else {
                autopilot.advance(next, AutopilotBlockEntity.Phase.LAND);
            }
        }
        // Her side jets' puffs and "Touchdown.", and her engines' thrust and her controls shown to the clients, who
        // play her roar and draw her plume and displays by them.
        boolean shown = rocketSounds.tick(level, origin, pilot, autopilot.phase, autopilot);
        if (pilot.controls().takeChanged() || shown) {
            autopilot.changed();
        }
        watchArrival(autopilot, flight, radio);
    }

    /**
     * The countdown heard as it begins and then each second: launch control's word (see {@link RocketSounds}), in the
     * cockpit a note quietly under it rising as lift-off nears, and a bell as the engines light.
     */
    private void countDown(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        int counted = pilot.counted();
        boolean second = counted == 1 || counted % SharedConstants.TICKS_PER_SECOND == 0;
        if (autopilot.phase != AutopilotBlockEntity.Phase.TAKEOFF || counted == countHeard || !second) {
            return;
        }
        countHeard = counted;
        if (counted == 1) {
            autopilot.announcements.letGo(Announcement.TAKEOFF);
        }
        rocketSounds.count(level, flight.position(), counted);
        float rising = (float) counted / RocketPilot.COUNTDOWN;
        BlockPos origin = autopilot.getBlockPos();
        if (counted >= RocketPilot.COUNTDOWN) {
            level.playSound(null, origin, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        } else if (counted > 0) {
            level.playSound(null, origin, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, COUNTDOWN_VOLUME, COUNTDOWN_LOWEST + rising);
        }
    }

    @Override
    void stop(ServerLevel level, AutopilotBlockEntity autopilot) {
        pilot.stop(level, autopilot.getBlockPos());
    }

    /**
     * Told to land, or switched off in the air: she falls back where she is now, catching herself on a landing burn and
     * settling there on her fins (see {@link RocketPilot}).
     */
    @Override
    void landHere(AutopilotBlockEntity autopilot, SableFlight.State state) {
        pilot.landHere(state);
    }

    @Override
    String comingDownKey() {
        return "message.skyliners.autopilot.coming_down_rocket";
    }

    @Override
    String offOnceDownKey() {
        return "message.skyliners.autopilot.off_once_down_rocket";
    }

    /** Her landing called off: she holds her height where she climbs back to, until told to land or given a pin. */
    @Override
    void goAround() {
        pilot.holdHere();
    }

    /** She arrives as a spacecraft, not as an aircraft; her flight deck's other announcements are the airliner's. */
    @Override
    Holder<SoundEvent> says(Holder<SoundEvent> announcement) {
        boolean arrival = announcement == SkylinersRegistry.ANNOUNCEMENT_ARRIVAL;
        return arrival ? SkylinersRegistry.ANNOUNCEMENT_ARRIVAL_ROCKET : announcement;
    }

    /**
     * She comes down on the pin, or without one back where she rose, whatever the mode; or holds, her landing called
     * off.
     */
    @Override
    Component modeSaid(AutopilotBlockEntity autopilot) {
        if (autopilot.lateral != AutopilotBlockEntity.Lateral.DESTINATION && pilot.holding()) {
            return Component.translatable("message.skyliners.autopilot.mode.rocket_hold");
        }
        return Component.translatable(autopilot.lateral == AutopilotBlockEntity.Lateral.DESTINATION ? "message.skyliners.autopilot.mode.rocket_pin"
            : "message.skyliners.autopilot.mode.rocket_back");
    }

    @Override
    int climb() {
        return SkylinersConfig.AUTOPILOT_CLIMB.get();
    }

    @Override
    double cruise() {
        return SkylinersConfig.AUTOPILOT_CRUISE_SPEED.get();
    }

    @Override
    double feetOverGround(Level level, AutopilotBlockEntity autopilot) {
        return RocketPilot.feetOverGround(level, autopilot.frame(), autopilot.wheelsBelow);
    }
}
