package dev.skyliners.compat.blockpilot;

import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.airframe.AirframeBuild;
import dev.skyliners.airframe.AirframeLayout;
import dev.skyliners.airframe.FreighterLayout;
import dev.skyliners.airframe.Layout;
import dev.skyliners.airframe.Livery;
import dev.skyliners.airframe.RocketLayout;
import dev.skyliners.airport.LaunchPad;
import dev.skyliners.airframe.ZeppelinLayout;
import dev.skyliners.airframe.ZeppelinOptions;
import dev.skyliners.airframe.ZeppelinSize;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.FreighterFlight;
import dev.skyliners.autopilot.FreighterPilot;
import dev.skyliners.autopilot.HyperspaceWake;
import dev.skyliners.autopilot.RocketFlight;
import dev.skyliners.autopilot.RocketPilot;
import dev.skyliners.autopilot.SetAutopilot;
import dev.skyliners.autopilot.ZeppelinAssembly;
import dev.skyliners.autopilot.ZeppelinFlight;
import dev.skyliners.autopilot.ZeppelinPilot;
import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.block.BoardingRamp;
import dev.skyliners.block.LaserBolt;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.repairs.RepairBoardBlockEntity;
import dev.skyliners.spaceweather.SpaceRocks;
import dev.skyliners.spaceweather.SpaceWeather;
import dev.skyliners.turret.LaserCannonBlockEntity;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Commands Skyliners gives Blockpilot, for an agent play-testing its airframes, each run on the integrated server for
 * the player: what a zeppelin's autopilot is doing ({@code skyliners:zeppelin}, to sample through a flight), setting
 * the nearest autopilot as its settings screen would ({@code skyliners:autopilot}), and laying an airframe down in
 * front of the player and waiting until it stands ({@code skyliners:build}), which of a zeppelin's blocks its
 * assembly would leave behind ({@code skyliners:assembly}), and a fingerprint of an airframe's layout, to show a change
 * to how it is laid out moves no block ({@code skyliners:layout}), repainting the nearest zeppelin
 * ({@code skyliners:livery}), and the space weather over the nearest rocket or freighter, or bringing some over her
 * ({@code skyliners:spaceweather}); and, on the client, whether the player sees from a laser cannon's sight
 * ({@code skyliners:gunner}), what it last drew of a laser bolt ({@code skyliners:bolts}) and of a freighter
 * stretching into a hyperspace jump and out of it ({@code skyliners:stretch}), and whether a flare has its
 * ship's screens dark ({@code skyliners:screens}). Only loaded when
 * Blockpilot is installed (see {@link dev.skyliners.compat.Blockpilot}).
 */
public final class BlockpilotCommands {
    /** How far from the player an autopilot is looked for, in blocks: past a zeppelin's length. */
    private static final double REACH = 600;
    /** How many of the blocks an assembly would leave behind are named, to find them by. */
    private static final int FIRST_LEFT = 5;
    /**
     * The sizes a zeppelin can be built at, as the build and layout commands name them, in the order of
     * {@link ZeppelinSize}'s: "full" is a word Blockpilot keeps for itself.
     */
    private static final List<String> SIZES = List.of("half", "whole");
    /**
     * Looking for dark spots: how far round the player across, below and above, in blocks; the least block light a
     * spot should have; and the sky light from which a spot is out under the sky, even in a ship's shade, rather than
     * shut in inside her, in light levels.
     */
    private static final int DARK_RADIUS = 48;
    private static final int DARK_BELOW = 16;
    private static final int DARK_ABOVE = 24;
    private static final int DARK_LEAST = 4;
    private static final int DARK_SKY = 1;
    /** The kinds of space weather, as the space weather command names them. */
    private static final List<String> WEATHER_KINDS = Arrays.stream(SpaceWeather.Kind.values()).map(SpaceWeather.Kind::getSerializedName).toList();

    private BlockpilotCommands() {
    }

    /** Gives Blockpilot the commands, while the mod loads (see {@link dev.skyliners.compat.Blockpilot#register}). */
    public static void register() {
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("zeppelin", (context, args) -> zeppelin())
            .onServer()
            .group("skyliners")
            .help("what the nearest zeppelin's autopilot is doing: phase, height, climb, speed, pitch, heading, mass, targets, burners, engines")
            .field("phase", "the autopilot's phase")
            .field("cells", "each gas cell's burner signal, bow first")
            .field("mass", "the zeppelin's mass in Sable's units"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("rocket", (context, args) -> rocket())
            .onServer()
            .group("skyliners")
            .help("what the nearest rocket's autopilot is doing: phase, height, climb, speed across, tilt, mass, thrust, trim, countdown, side jets")
            .field("phase", "the autopilot's phase")
            .field("thrust", "the share of full thrust its engines are set to")
            .field("jets", "each side jet's signal, near the nose ahead, right, back and left, then near the bottom")
            .field("mass", "the rocket's mass in Sable's units"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("freighter", (context, args) -> freighter())
            .onServer()
            .group("skyliners")
            .help("what the nearest freighter's autopilot is doing: phase, height, climb, speed ahead and across, heading, tilt, mass, lift, trims, thrusters, "
                + "legs, what holds her take-off, where her slam landing stands, and how many ticks after its button her last hyperspace jump found the land "
                + "round her pin loaded (-1 before one)")
            .field("phase", "the autopilot's phase")
            .field("lift", "the share of full lift her lift thrusters are set to")
            .field("clusters", "each lift cluster's total signal: ahead right, ahead left, astern right, astern left")
            .field("jets", "each jet's signal: nose right, nose left, tail right, tail left")
            .field("mass", "the freighter's mass in Sable's units")
            .field("legs", "where her landing legs are: down, raising, up or lowering, moving apart, or fixed")
            .field("held", "what holds her take-off on her legs, as her console shows it (ramp), or none")
            .field("slam", "her slam landing as her console shows it: unavailable, ready or firing"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("gunner", (context, args) -> gunner())
            .needsWorld()
            .group("skyliners")
            .help("whether this client sees from a laser cannon's sight, sitting at it: where the sight is and looks, whether it is locked on to a "
                + "hostile mob or a space rock, and its rockets as the sight shows them")
            .field("on", "whether the view is the sight's")
            .field("at", "where the sight is in the world")
            .field("look", "the way the sight looks in the world, x,y,z of length 1")
            .field("locked", "whether the sight is locked on to a hostile mob or a space rock in reach")
            .field("rockets", "how many rockets its pods hold, as the sight shows them")
            .field("loading", "how far the next rocket is loaded, from 0 to 1, as the sight shows it filling")
            .field("bursts_seen", "how many space rocks this client has seen burst in the weather it shows"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("autopilot", BlockpilotCommands::autopilot)
            .onServer()
            .group("skyliners")
            .keyword("op", "fly", "land", "off")
            .option("altitude", "the height to hold, in world blocks", ArgType.INT)
            .option("speed", "the speed to hold, in blocks a second", ArgType.INT)
            .option("heading", "the compass bearing to hold, 0 north", ArgType.INT)
            .option("pin", "fly to a pin at x,z (two whole numbers)")
            .option("land", "at the pin, land (true) or circle (false)", List.of("true", "false"))
            .help("sets the nearest autopilot as its settings screen would, assembling its airframe first if need be")
            .example("skyliners:autopilot fly altitude=120 speed=15"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("assembly", (context, args) -> assembly())
            .onServer()
            .group("skyliners")
            .help("before the nearest zeppelin flies: how many of its blocks its assembly takes along, and which of its own it would leave behind")
            .field("assembled", "the blocks its assembly takes along")
            .field("left", "its own blocks it would leave behind in the world")
            .field("first", "where the first few left behind stand")
            .field("stuck", "other blocks in its frame it would leave behind pressed against it, holding it as it flies off")
            .field("first_stuck", "where the first few of those stand, and what they are"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("dark", (context, args) -> dark(args))
            .onServer()
            .group("skyliners")
            .option("radius", "how far round the player to look across, in blocks (default " + DARK_RADIUS + ")", ArgType.INT)
            .option("least", "the least block light a spot should have (default " + DARK_LEAST + ")", ArgType.INT)
            .help("spots inside the mod's ships round the player someone could stand in, lit less than the least: how many, how many unlit")
            .field("dark", "spots lit less than the least")
            .field("unlit", "spots with no block light at all, where monsters can spawn")
            .field("first", "where the first few of the darkest stand, lowest first"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("livery", (context, args) -> livery(args))
            .onServer()
            .group("skyliners")
            .keyword("livery", Arrays.stream(Livery.values()).map(livery -> livery.name().toLowerCase(Locale.ROOT)).toArray(String[]::new))
            .help("repaints the nearest zeppelin, the new livery sweeping along it from the nose over a minute, as its livery board does")
            .field("livery", "what it is painted now")
            .example("skyliners:livery cream"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("layout", (context, args) -> layout(args))
            .onServer()
            .group("skyliners")
            .keyword("what", "airliner", "furnished", "zeppelin", "furnished_zeppelin", "festive_zeppelin", "rocket", "launch_pad", "freighter")
            .option("size", "how big a zeppelin's hull is, full size if left out", SIZES)
            .help("lays an airframe out without building it: how many blocks it has and a fingerprint of them, the same however they are laid out")
            .field("blocks", "its blocks, the air inside its hull among them")
            .field("fingerprint", "of every block's position and state, and of where its block entities are set up")
            .example("skyliners:layout zeppelin"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("bolts", (context, args) -> bolts())
            .needsWorld()
            .group("skyliners")
            .help("what this client last drew of the newest laser bolt it was sent: whose weapon it left, in how many frames, and from where")
            .field("received", "how many laser bolts this client has been sent")
            .field("holder", "the entity id of whoever held the weapon it left, -1 for a mounted gun")
            .field("frames", "how many frames it has been drawn in")
            .field("shown", "for how many ticks after it came it has been drawn")
            .field("from_muzzle", "whether it was drawn leaving that weapon's muzzle as drawn, rather than where the server fired it from")
            .field("from_camera", "where it was drawn leaving from, from the camera: blocks right, up and ahead as it looks")
            .field("flown", "for a bolt from a weapon in hand, how far its tail had flown from where it left, in blocks, when last drawn")
            .field("streak", "for a bolt from a weapon in hand, how long it was drawn, head to tail, in blocks, when last drawn"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("ramp", (context, args) -> ramp())
            .needsWorld()
            .group("skyliners")
            .help("what this client drew of a boarding ramp's latest swing: in how many frames it moved, and the worst it went back or leapt on from one frame to the next")
            .field("frames", "in how many frames it moved, 0 before one has swung")
            .field("at", "how far through coming down it was last drawn, in ticks")
            .field("back", "the most it went back between one frame and the next, in ticks")
            .field("jump", "the most it leapt on between one frame and the next, in ticks"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("stretch", (context, args) -> stretch())
            .needsWorld()
            .group("skyliners")
            .help("what this client last drew of a freighter stretching into a hyperspace jump and snapping back out of it, watched from outside")
            .field("jumps", "how many jumps this client has been shown from outside")
            .field("going", "how many times her own length she was drawn at most as she went")
            .field("going_frames", "in how many frames she was drawn stretched as she went")
            .field("coming", "how many times her own length she was drawn at most as she came out")
            .field("coming_frames", "in how many frames she was drawn stretched as she came out"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("repairs", (context, args) -> repairs(args))
            .onServer()
            .group("skyliners")
            .option("every", "for play-testing, how long a ship flies between faults, in ticks; 0 for the usual few minutes", ArgType.INT)
            .help("the nearest repair board's running repairs: faults waiting and where, faults fixed, and her flight counted toward the next")
            .field("waiting", "how many of its fault spots are at fault")
            .field("faulty", "where those at fault are, as plot positions to use")
            .field("repairs", "how many faults have been fixed near it, ever")
            .field("flying", "whether she flies along, when faults may go off")
            .field("flown", "the ticks she has flown since the last fault went off")
            .field("next", "the ticks of flight after which the next goes off, -1 before she first flies")
            .field("spots", "how many fault spots it looks after")
            .example("skyliners:repairs every=100"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("screens", (context, args) -> Result.ok(new Fields().tag("dark", SpaceWeather.clientBlackedOut.getAsBoolean())))
            .needsWorld()
            .group("skyliners")
            .help("whether a solar flare's blackout has this client's ship's screens dark")
            .field("dark", "whether they are dark with it"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.query("spaceweather", (context, args) -> spaceWeather(args))
            .onServer()
            .group("skyliners")
            .option("start", "brings space weather of a kind over the nearest rocket or freighter at once, from y 1,500 up", WEATHER_KINDS)
            .help("the space weather over the nearest rocket or freighter: what blows, how many strikes have landed on her and the tick the last did")
            .field("blowing", "what blows now: meteor_shower, asteroid_storm, solar_flare, giant_asteroid or none")
            .field("from", "the side of the sky the weather blowing comes out of, as a bearing in degrees as the game turns a look")
            .field("landed", "how many strikes have landed on her since she was loaded")
            .field("last_landed", "the game tick the last strike landed on her, -1 before any")
            .field("last_share", "how much of its rock was left of the last to land on her, as a share")
            .field("last_giant", "whether the last to land on her was a giant asteroid's giant or a piece cracked off it")
            .field("shot_down", "how many rocks a laser cannon has burst since she was loaded")
            .field("missed", "how many rocks coming at her flew on past without meeting her since she was loaded")
            .field("real", "how many rocks are real bodies of blocks now")
            .field("tick", "the game time of this report")
            .field("real_id", "the first of those, one coming at her if any, the heaviest otherwise: its number among the weather's rocks")
            .field("real_at", "where it is, in the world, x,y,z")
            .field("real_vel", "how fast it moves, in blocks a second, x,y,z")
            .field("real_mass", "how heavy it is, in Sable's units, a quarter for each of its blocks")
            .field("real_size", "how big it is, in blocks across")
            .field("real_coming", "whether it is coming at her to strike her")
            .field("pieces_coming", "how many real bodies are coming at her, the rocks flying in and the pieces rockets cracked off them")
            .field("pieces_vel", "how fast each of those moves, in blocks a second, x,y,z, separated by ;")
            .field("pieces_mass", "how heavy each of those is, in Sable's units, separated by ;")
            .field("coming", "how many rocks are flying in to strike her now")
            .field("rock_at", "where the first rock flying in to strike her is, in the world, x,y,z")
            .field("rock_vel", "how fast that rock moves, in blocks a second, x,y,z")
            .field("rock_size", "how big that rock is, in blocks across")
            .field("rock_lands", "the game tick that rock lands")
            .field("rock_giant", "whether that rock is a giant asteroid's giant")
            .field("rock_target", "where on her hull that rock lands, in the world, x,y,z")
            .field("giant_lands", "the game tick a giant asteroid's giant flying in lands, -1 for none")
            .field("giant_size", "how big that giant is, in blocks across")
            .field("started", "what asking for weather came to, when asked: coming, busy, not_flying, too_low, nobody_aboard, switched_off or no_ship")
            .example("skyliners:spaceweather start=asteroid_storm"));
        BlockpilotApi.register(Skyliners.MOD_ID, CommandSpec.action("build", BlockpilotCommands::build)
            .onServer()
            .group("skyliners")
            .keyword("what", "airliner", "furnished", "zeppelin", "furnished_zeppelin", "festive_zeppelin", "rocket", "freighter")
            .option("size", "how big a zeppelin's hull is, full size if left out", SIZES)
            .help("lays an airframe down with its nose a few blocks ahead of the player, its tail away the way they face, and waits until it stands")
            .example("skyliners:build zeppelin"));
    }

    /** What this client's player sees from a laser cannon's sight, on the client, as its gunner's view tells it. */
    private static Result gunner() {
        LaserCannonBlockEntity.Sighting sighting = LaserCannonBlockEntity.clientSighting.get();
        if (sighting == null) {
            return Result.ok(new Fields().tag("on", false));
        }
        Vec3 eye = sighting.eye();
        Vec3 look = sighting.look();
        return Result.ok(new Fields().tag("on", true).tag("at", round(eye.x) + "," + round(eye.y) + "," + round(eye.z))
            .tag("look", round(look.x) + "," + round(look.y) + "," + round(look.z))
            .tag("locked", sighting.lockedOn()).tag("rockets", sighting.rockets()).tag("loading", round(sighting.loading()))
            .tag("bursts_seen", SpaceRocks.clientBurstsSeen.getAsInt()));
    }

    private static Result zeppelin() throws CommandException {
        ServerPlayer player = player();
        AutopilotBlockEntity autopilot = nearest(player);
        ZeppelinFlight zeppelin = autopilot == null ? null : autopilot.flight(ZeppelinFlight.class);
        ZeppelinPilot.Report report = zeppelin == null ? null : zeppelin.report();
        if (report == null) {
            return Result.error("no_zeppelin");
        }
        SableFlight.State state = SableFlight.read(autopilot, autopilot.getBlockState().getValue(AutopilotBlock.FACING));
        Fields fields = new Fields()
            .tag("phase", autopilot.phase().name().toLowerCase(Locale.ROOT));
        if (state != null) {
            fields.tag("altitude", round(state.position().y)).tag("climb", round(state.velocity().y)).tag("speed", round(state.airspeed()))
                .tag("pitch", round(state.pitch())).tag("heading", AutopilotBlockEntity.bearing(state.heading()));
        }
        fields
            .tag("target_altitude", Math.round(autopilot.targetAltitude()))
            .tag("target_speed", Math.round(autopilot.targetSpeed()))
            .tag("mass", Math.round(SableFlight.mass(autopilot)))
            .tag("cells", report.cells())
            .tag("ahead", report.ahead())
            .tag("astern", report.astern())
            .tag("lift_trim", round(report.liftTrim()))
            .tag("pitch_trim", round(report.pitchTrim()))
            .tag("thrust_trim", round(report.thrustTrim()));
        return Result.ok(fields);
    }

    private static Result rocket() throws CommandException {
        ServerPlayer player = player();
        AutopilotBlockEntity autopilot = nearest(player);
        RocketFlight rocket = autopilot == null ? null : autopilot.flight(RocketFlight.class);
        RocketPilot.Report report = rocket == null ? null : rocket.report();
        if (report == null) {
            return Result.error("no_rocket");
        }
        SableFlight.State state = SableFlight.read(autopilot, autopilot.getBlockState().getValue(AutopilotBlock.FACING));
        Fields fields = new Fields().tag("phase", autopilot.phase().name().toLowerCase(Locale.ROOT));
        if (state != null) {
            fields.tag("altitude", round(state.position().y)).tag("climb", round(state.velocity().y))
                .tag("across", round(Math.hypot(state.velocity().x, state.velocity().z)));
        }
        return Result.ok(fields
            .tag("climb_wanted", round(report.climbWanted()))
            .tag("tilt_front", round(report.tiltFront()))
            .tag("tilt_right", round(report.tiltRight()))
            .tag("target_altitude", Math.round(autopilot.targetAltitude()))
            .tag("mass", Math.round(SableFlight.mass(autopilot)))
            .tag("thrust", round(report.thrust()))
            .tag("trim", round(report.trim()))
            .tag("countdown", report.counted())
            .tag("jets", report.jets()));
    }

    private static Result freighter() throws CommandException {
        ServerPlayer player = player();
        AutopilotBlockEntity autopilot = nearest(player);
        FreighterFlight freighter = autopilot == null ? null : autopilot.flight(FreighterFlight.class);
        FreighterPilot.Report report = freighter == null ? null : freighter.report();
        if (report == null) {
            return Result.error("no_freighter");
        }
        SableFlight.State state = SableFlight.read(autopilot, autopilot.getBlockState().getValue(AutopilotBlock.FACING));
        Fields fields = new Fields().tag("phase", autopilot.phase().name().toLowerCase(Locale.ROOT));
        if (state != null) {
            fields.tag("altitude", round(state.position().y)).tag("heading", AutopilotBlockEntity.bearing(state.heading()));
        }
        return Result.ok(fields
            .tag("climb", round(report.climb()))
            .tag("ahead", round(report.ahead()))
            .tag("across", round(report.across()))
            .tag("pad", report.pad() == null ? "none" : report.pad().x() + "," + report.pad().z())
            .tag("middle", Math.round(report.middle().x) + "," + Math.round(report.middle().z))
            .tag("climb_wanted", round(report.climbWanted()))
            .tag("ahead_wanted", round(report.aheadWanted()))
            .tag("across_wanted", round(report.acrossWanted()))
            .tag("heading_wanted", AutopilotBlockEntity.bearing(report.headingWanted()))
            .tag("tilt_front", round(report.tiltFront()))
            .tag("tilt_right", round(report.tiltRight()))
            .tag("target_altitude", Math.round(autopilot.targetAltitude()))
            .tag("mass", Math.round(SableFlight.mass(autopilot)))
            .tag("lift", round(report.lift()))
            .tag("trim", round(report.trim()))
            .tag("pitch_trim", round(report.pitchTrim()))
            .tag("roll_trim", round(report.rollTrim()))
            .tag("drag", round(report.drag()))
            .tag("clusters", report.clusters())
            .tag("mains", report.mains())
            .tag("retros", report.retros())
            .tag("jets", report.jets())
            .tag("legs", freighter.legsShown(autopilot))
            .tag("held", Objects.requireNonNullElse(FreighterFlight.heldBy(autopilot), "none"))
            .tag("slam", FreighterFlight.slamOf(autopilot).name().toLowerCase(Locale.ROOT))
            .tag("jump_loaded_after", freighter.jumpLoadedAfter())
            .tag("jumped_at", freighter.jumpedAt()));
    }

    private static Result spaceWeather(Args args) throws CommandException {
        ServerPlayer player = player();
        AutopilotBlockEntity autopilot = nearest(player);
        Fields fields = new Fields();
        if (args.hasOption("start")) {
            SpaceWeather.Kind kind = SpaceWeather.Kind.of(WEATHER_KINDS.indexOf(args.option("start")));
            fields.tag("started", SpaceWeather.ask(player.serverLevel(), autopilot, kind).name().toLowerCase(Locale.ROOT));
        }
        SpaceWeather weather = autopilot == null ? null : autopilot.spaceWeather();
        if (weather == null) {
            return Result.error("no_ship");
        }
        List<SpaceWeather.Coming> coming = weather.coming(player.serverLevel());
        fields.tag("blowing", weather.blowingNamed()).tag("from", Math.round(weather.from())).tag("landed", weather.landed())
            .tag("last_landed", weather.lastLanded()).tag("last_share", round(weather.lastShare())).tag("last_giant", weather.lastGiant()).tag("shot_down", weather.shotDown())
            .tag("missed", weather.missed()).tag("coming", coming.size());
        List<SpaceWeather.Real> real = weather.realRocks(player.serverLevel());
        fields.tag("real", real.size()).tag("tick", player.serverLevel().getGameTime());
        if (!real.isEmpty()) {
            SpaceWeather.Real first = real.getFirst();
            fields.tag("real_id", first.id()).tag("real_at", round(first.at().x) + "," + round(first.at().y) + "," + round(first.at().z))
                .tag("real_vel", round(first.velocity().x) + "," + round(first.velocity().y) + "," + round(first.velocity().z))
                .tag("real_mass", round(first.mass())).tag("real_size", round(first.size())).tag("real_coming", first.coming());
        }
        List<SpaceWeather.Real> pieces = real.stream().filter(SpaceWeather.Real::coming).toList();
        fields.tag("pieces_coming", pieces.size())
            .tag("pieces_vel", String.join(";", pieces.stream().map(piece -> round(piece.velocity().x) + "," + round(piece.velocity().y) + "," + round(piece.velocity().z)).toList()))
            .tag("pieces_mass", String.join(";", pieces.stream().map(piece -> String.valueOf(round(piece.mass()))).toList()));
        if (!coming.isEmpty()) {
            SpaceWeather.Coming first = coming.getFirst();
            fields.tag("rock_at", round(first.at().x) + "," + round(first.at().y) + "," + round(first.at().z))
                .tag("rock_vel", round(first.velocity().x) + "," + round(first.velocity().y) + "," + round(first.velocity().z))
                .tag("rock_size", round(first.size())).tag("rock_lands", first.lands()).tag("rock_giant", first.giant())
                .tag("rock_target", round(first.target().x) + "," + round(first.target().y) + "," + round(first.target().z));
        }
        SpaceWeather.Coming giant = coming.stream().filter(SpaceWeather.Coming::giant).findFirst().orElse(null);
        fields.tag("giant_lands", giant == null ? -1 : giant.lands()).tag("giant_size", giant == null ? 0 : round(giant.size()));
        return Result.ok(fields);
    }

    private static Result assembly() throws CommandException {
        ServerPlayer player = player();
        AutopilotBlockEntity autopilot = nearest(player);
        ServerLevel level = player.serverLevel();
        BoundingBox frame = autopilot == null || SableFlight.aboard(level, Vec3.atCenterOf(autopilot.getBlockPos())) ? null : autopilot.frame();
        if (frame == null) {
            return Result.error("no_zeppelin_standing");
        }
        List<BlockPos> left = ZeppelinAssembly.leftBehind(level, autopilot.getBlockPos(), frame);
        String first = left.stream().limit(FIRST_LEFT).map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ()).collect(Collectors.joining(" "));
        List<BlockPos> stuck = ZeppelinAssembly.stuckTo(level, autopilot.getBlockPos(), frame);
        String firstStuck = stuck.stream().limit(FIRST_LEFT)
            .map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ() + "=" + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()))
            .collect(Collectors.joining(" "));
        return Result.ok(new Fields()
            .tag("assembled", ZeppelinAssembly.blocks(level, autopilot.getBlockPos(), frame).size())
            .tag("left", left.size())
            .tag("first", first)
            .tag("stuck", stuck.size())
            .tag("first_stuck", firstStuck));
    }

    /**
     * The spots round the player inside the mod's ships someone could stand in, a floor of the mod's under them that
     * holds them, room for their head and out of the sky's light, lit by lamps less than the least asked for.
     */
    private static Result dark(Args args) throws CommandException {
        ServerPlayer player = player();
        ServerLevel level = player.serverLevel();
        int radius = args.intOption("radius", DARK_RADIUS);
        int least = args.intOption("least", DARK_LEAST);
        BlockPos middle = player.blockPosition();
        List<BlockPos> dark = new ArrayList<>();
        int unlit = 0;
        for (BlockPos at : BlockPos.betweenClosed(middle.offset(-radius, -DARK_BELOW, -radius), middle.offset(radius, DARK_ABOVE, radius))) {
            int light = standableLight(level, at);
            if (light >= 0 && light < least) {
                dark.add(at.immutable());
                unlit += light == 0 ? 1 : 0;
            }
        }
        String first = dark.stream().limit(FIRST_LEFT).map(pos -> pos.getX() + "," + pos.getY() + "," + pos.getZ()).collect(Collectors.joining(" "));
        return Result.ok(new Fields().tag("dark", dark.size()).tag("unlit", unlit).tag("first", first));
    }

    /**
     * The block light at a spot someone could stand in inside one of the mod's ships, out of the sky's light; -1 for any
     * other spot.
     */
    private static int standableLight(ServerLevel level, BlockPos at) {
        BlockPos below = at.below();
        boolean room = level.getBlockState(at).getCollisionShape(level, at).isEmpty()
            && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
        boolean ours = BuiltInRegistries.BLOCK.getKey(level.getBlockState(below).getBlock()).getNamespace().equals(Skyliners.MOD_ID);
        if (!room || !ours || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
            || level.getBrightness(LightLayer.SKY, at) >= DARK_SKY) {
            return -1;
        }
        return level.getBrightness(LightLayer.BLOCK, at);
    }

    private static Result autopilot(CommandContext context, Args args) throws CommandException {
        ServerPlayer player = player();
        AutopilotBlockEntity autopilot = nearest(player);
        if (autopilot == null) {
            return Result.error("no_autopilot");
        }
        SetAutopilot.Action action = SetAutopilot.Action.valueOf(args.word("op").toUpperCase(Locale.ROOT));
        AutopilotBlockEntity.Settings settings = settings(autopilot.offered(), args);
        Component assembling = action == SetAutopilot.Action.FLY ? autopilot.assembleToFly(player, settings) : null;
        Component said = assembling != null ? assembling : autopilot.set(action, settings);
        return Result.ok(new Fields().put("said", said.getString(), said.getString()));
    }

    /** The settings the screen offers, with what the command sets in place of them. */
    private static AutopilotBlockEntity.Settings settings(AutopilotBlockEntity.Settings offered, Args args) throws CommandException {
        int altitude = args.intOption("altitude", offered.altitude());
        int speed = args.intOption("speed", offered.speed());
        AutopilotBlockEntity.Lateral lateral = args.hasOption("heading") ? AutopilotBlockEntity.Lateral.HEADING : offered.lateral();
        int heading = args.intOption("heading", offered.heading());
        BlockPos pin = offered.destination();
        if (args.hasOption("pin")) {
            pin = pin(args.option("pin"));
            lateral = AutopilotBlockEntity.Lateral.DESTINATION;
        }
        boolean land = args.hasOption("land") ? Boolean.parseBoolean(args.option("land")) : offered.landAtPin();
        return new AutopilotBlockEntity.Settings(speed, altitude, heading, lateral, offered.circle(), pin, land);
    }

    private static BlockPos pin(String text) throws CommandException {
        String[] parts = text.split(",");
        try {
            return new BlockPos(Integer.parseInt(parts[0].trim()), 0, Integer.parseInt(parts[1].trim()));
        } catch (RuntimeException e) {
            throw new CommandException("bad_pin", text);
        }
    }

    private static Result livery(Args args) throws CommandException {
        AutopilotBlockEntity autopilot = nearest(player());
        if (autopilot == null || autopilot.craft() != Craft.ZEPPELIN) {
            return Result.error("no_zeppelin");
        }
        autopilot.repaint(Livery.valueOf(args.word("livery").toUpperCase(Locale.ROOT)));
        return Result.ok(new Fields().tag("livery", autopilot.livery().name().toLowerCase(Locale.ROOT)));
    }

    private static Result layout(Args args) throws CommandException {
        Layout layout = layout(args.word("what"), size(args));
        long[] sum = {0};
        layout.inOrder((pos, state) -> sum[0] += mix(pos.asLong() ^ mix(state.toString().hashCode())));
        layout.fittings().keySet().forEach(pos -> sum[0] += mix(~pos.asLong()));
        return Result.ok(new Fields().tag("blocks", layout.size()).tag("fingerprint", Long.toHexString(sum[0])));
    }

    /** Scrambles a number's bits (SplitMix64's finaliser), so a sum of scrambled numbers fingerprints a set of them. */
    private static long mix(long value) {
        long z = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** The size a command asks a zeppelin to be built at: full size unless it says. */
    private static ZeppelinSize size(Args args) {
        int named = args.hasOption("size") ? SIZES.indexOf(args.option("size")) : -1;
        return named < 0 ? ZeppelinSize.FULL : ZeppelinSize.values()[named];
    }

    private static Layout layout(String what, ZeppelinSize size) {
        return switch (what) {
            case "zeppelin" -> ZeppelinLayout.create(ZeppelinOptions.PLAIN.sized(size));
            case "furnished_zeppelin" -> ZeppelinLayout.create(ZeppelinOptions.FURNISHED.sized(size));
            case "festive_zeppelin" -> ZeppelinLayout.create(ZeppelinOptions.FESTIVE.sized(size));
            case "rocket" -> RocketLayout.create();
            case "launch_pad" -> LaunchPad.create(Integer.MAX_VALUE);
            case "freighter" -> FreighterLayout.create();
            case "furnished" -> AirframeLayout.create(SkylinersConfig.SCALE.get(), true);
            default -> AirframeLayout.create(SkylinersConfig.SCALE.get(), false);
        };
    }

    private static Action build(CommandContext context, Args args) throws CommandException {
        ServerPlayer player = player();
        ServerLevel level = player.serverLevel();
        if (AirframeBuild.isRunning(level)) {
            throw new CommandException("busy");
        }
        Layout layout = layout(args.word("what"), size(args));
        Direction along = player.getDirection();
        BlockPos nose = player.blockPosition().relative(along, 3);
        AirframeBuild.Placement placement = AirframeBuild.Placement.of(level, layout, nose, along);
        if (placement.top() >= level.getMaxBuildHeight()) {
            throw new CommandException("too_high");
        }
        AirframeBuild.start(level, placement, player);
        int blocks = placement.positions().length;
        return () -> AirframeBuild.isRunning(level) ? null
            : Result.ok(new Fields().tag("blocks", blocks).tag("nose", nose.getX() + "," + nose.getY() + "," + nose.getZ()));
    }

    /** What this client last made of the newest laser bolt it was sent, on the client, as its drawing tells it. */
    private static Result bolts() {
        LaserBolt.Seen seen = LaserBolt.clientSeen.get();
        if (seen == null) {
            return Result.ok(new Fields().tag("received", 0));
        }
        Fields fields = new Fields().tag("received", seen.received()).tag("holder", seen.holder()).tag("frames", seen.frames())
            .tag("shown", seen.shown()).tag("from_muzzle", seen.fromMuzzle()).tag("flown", round(seen.flown())).tag("streak", round(seen.streak()));
        Vec3 at = seen.fromCamera();
        return Result.ok(at == null ? fields : fields.tag("from_camera", round(at.x) + "," + round(at.y) + "," + round(at.z)));
    }

    /** What this client drew of a boarding ramp's latest swing. */
    private static Result ramp() {
        BoardingRamp.Drawn drawn = BoardingRamp.clientDrawn.get();
        if (drawn == null) {
            return Result.ok(new Fields().tag("frames", 0));
        }
        return Result.ok(new Fields().tag("frames", drawn.frames()).tag("at", round(drawn.at()))
            .tag("back", round(drawn.back())).tag("jump", round(drawn.jump())));
    }

    /** What this client last drew of a freighter stretching into a jump and out of it, watched from outside. */
    private static Result stretch() {
        HyperspaceWake.Stretched seen = HyperspaceWake.clientStretched.get();
        if (seen == null) {
            return Result.ok(new Fields().tag("jumps", 0));
        }
        return Result.ok(new Fields().tag("jumps", seen.jumps()).tag("going", round(seen.going())).tag("going_frames", seen.goingFrames())
            .tag("coming", round(seen.coming())).tag("coming_frames", seen.comingFrames()));
    }

    /**
     * The repair board nearest the player, where it is in the world, and how it stands; given {@code every}, how long
     * every ship flies between faults from now on, for play-testing, first.
     */
    private static Result repairs(Args args) throws CommandException {
        ServerPlayer player = player();
        if (args.hasOption("every")) {
            RepairBoardBlockEntity.testEvery = Math.max(0, args.intOption("every", 0));
        }
        RepairBoardBlockEntity nearest = null;
        double best = REACH * REACH;
        for (RepairBoardBlockEntity board : RepairBoardBlockEntity.loaded(player.serverLevel())) {
            double distance = SableFlight.toWorld(player.level(), Vec3.atCenterOf(board.getBlockPos())).distanceToSqr(player.position());
            if (distance < best) {
                nearest = board;
                best = distance;
            }
        }
        if (nearest == null) {
            return Result.error("no_repair_board");
        }
        RepairBoardBlockEntity.Report report = nearest.report(player.serverLevel());
        String faulty = report.faulty().stream().map(at -> at.getX() + "," + at.getY() + "," + at.getZ()).collect(Collectors.joining(" "));
        return Result.ok(new Fields().tag("waiting", report.waiting()).tag("faulty", faulty).tag("repairs", report.repairs())
            .tag("flying", report.flying()).tag("flown", report.flown()).tag("next", report.nextAfter()).tag("spots", report.spots()));
    }

    /** The player hosting the single player game Blockpilot drives. */
    private static ServerPlayer player() throws CommandException {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerPlayer host = server == null || server.getSingleplayerProfile() == null ? null
            : server.getPlayerList().getPlayer(server.getSingleplayerProfile().getId());
        if (host == null) {
            throw new CommandException("no_player");
        }
        return host;
    }

    /** The autopilot nearest the player, where it is in the world, within {@link #REACH}; null for none. */
    @Nullable
    private static AutopilotBlockEntity nearest(ServerPlayer player) {
        AutopilotBlockEntity nearest = null;
        double best = REACH * REACH;
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(player.level())) {
            Vec3 at = SableFlight.toWorld(player.level(), Vec3.atCenterOf(autopilot.getBlockPos()));
            double distance = at.distanceToSqr(player.position());
            if (distance < best) {
                best = distance;
                nearest = autopilot;
            }
        }
        return nearest;
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
