package dev.aerolarge.compat.blockpilot;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.airframe.AirframeBuild;
import dev.aerolarge.airframe.AirframeLayout;
import dev.aerolarge.airframe.Layout;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.airframe.RocketLayout;
import dev.aerolarge.airframe.ZeppelinLayout;
import dev.aerolarge.airframe.ZeppelinOptions;
import dev.aerolarge.airframe.ZeppelinSize;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.autopilot.RocketPilot;
import dev.aerolarge.autopilot.SetAutopilot;
import dev.aerolarge.autopilot.ZeppelinAssembly;
import dev.aerolarge.autopilot.ZeppelinPilot;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.compat.SableFlight;
import dev.blockpilot.api.Action;
import dev.blockpilot.api.ArgType;
import dev.blockpilot.api.Args;
import dev.blockpilot.api.BlockpilotApi;
import dev.blockpilot.api.CommandContext;
import dev.blockpilot.api.CommandException;
import dev.blockpilot.api.CommandSpec;
import dev.blockpilot.api.Fields;
import dev.blockpilot.api.Result;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Commands Aerolarge gives Blockpilot, for an agent play-testing its airframes, each run on the integrated server for
 * the player: what a zeppelin's autopilot is doing ({@code aerolarge:zeppelin}, to sample through a flight), setting
 * the nearest autopilot as its settings screen would ({@code aerolarge:autopilot}), and laying an airframe down in
 * front of the player and waiting until it stands ({@code aerolarge:build}), which of a zeppelin's blocks its
 * assembly would leave behind ({@code aerolarge:assembly}), and a fingerprint of an airframe's layout, to show a change
 * to how it is laid out moves no block ({@code aerolarge:layout}), and repainting the nearest zeppelin
 * ({@code aerolarge:livery}). Only loaded when Blockpilot is installed (see
 * {@link dev.aerolarge.compat.Blockpilot}).
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

    private BlockpilotCommands() {
    }

    /** Gives Blockpilot the commands, while the mod loads (see {@link dev.aerolarge.compat.Blockpilot#register}). */
    public static void register() {
        BlockpilotApi.register(Aerolarge.MOD_ID, CommandSpec.query("zeppelin", (context, args) -> zeppelin())
            .onServer()
            .group("aerolarge")
            .help("what the nearest zeppelin's autopilot is doing: phase, height, climb, speed, pitch, heading, mass, targets, burners, engines")
            .field("phase", "the autopilot's phase")
            .field("cells", "each gas cell's burner signal, bow first")
            .field("mass", "the zeppelin's mass in Sable's units"));
        BlockpilotApi.register(Aerolarge.MOD_ID, CommandSpec.query("rocket", (context, args) -> rocket())
            .onServer()
            .group("aerolarge")
            .help("what the nearest rocket's autopilot is doing: phase, height, climb, speed across, tilt, mass, thrust, trim, countdown, side jets")
            .field("phase", "the autopilot's phase")
            .field("thrust", "the share of full thrust its engines are set to")
            .field("jets", "each side jet's signal, near the nose ahead, right, back and left, then near the bottom")
            .field("mass", "the rocket's mass in Sable's units"));
        BlockpilotApi.register(Aerolarge.MOD_ID, CommandSpec.query("autopilot", BlockpilotCommands::autopilot)
            .onServer()
            .group("aerolarge")
            .keyword("op", "fly", "land", "off")
            .option("altitude", "the height to hold, in world blocks", ArgType.INT)
            .option("speed", "the speed to hold, in blocks a second", ArgType.INT)
            .option("heading", "the compass bearing to hold, 0 north", ArgType.INT)
            .option("pin", "fly to a pin at x,z (two whole numbers)")
            .option("land", "at the pin, land (true) or circle (false)", List.of("true", "false"))
            .help("sets the nearest autopilot as its settings screen would, assembling its airframe first if need be")
            .example("aerolarge:autopilot fly altitude=120 speed=15"));
        BlockpilotApi.register(Aerolarge.MOD_ID, CommandSpec.query("assembly", (context, args) -> assembly())
            .onServer()
            .group("aerolarge")
            .help("before the nearest zeppelin flies: how many of its blocks its assembly takes along, and which of its own it would leave behind")
            .field("assembled", "the blocks its assembly takes along")
            .field("left", "its own blocks it would leave behind in the world")
            .field("first", "where the first few left behind stand")
            .field("stuck", "other blocks in its frame it would leave behind pressed against it, holding it as it flies off")
            .field("first_stuck", "where the first few of those stand, and what they are"));
        BlockpilotApi.register(Aerolarge.MOD_ID, CommandSpec.query("livery", (context, args) -> livery(args))
            .onServer()
            .group("aerolarge")
            .keyword("livery", Arrays.stream(Livery.values()).map(livery -> livery.name().toLowerCase(Locale.ROOT)).toArray(String[]::new))
            .help("repaints the nearest zeppelin, the new livery sweeping along it from the nose over a minute, as its livery board does")
            .field("livery", "what it is painted now")
            .example("aerolarge:livery cream"));
        BlockpilotApi.register(Aerolarge.MOD_ID, CommandSpec.query("layout", (context, args) -> layout(args))
            .onServer()
            .group("aerolarge")
            .keyword("what", "airliner", "furnished", "zeppelin", "furnished_zeppelin", "festive_zeppelin", "rocket")
            .option("size", "how big a zeppelin's hull is, full size if left out", SIZES)
            .help("lays an airframe out without building it: how many blocks it has and a fingerprint of them, the same however they are laid out")
            .field("blocks", "its blocks, the air inside its hull among them")
            .field("fingerprint", "of every block's position and state, and of where its block entities are set up")
            .example("aerolarge:layout zeppelin"));
        BlockpilotApi.register(Aerolarge.MOD_ID, CommandSpec.action("build", BlockpilotCommands::build)
            .onServer()
            .group("aerolarge")
            .keyword("what", "airliner", "furnished", "zeppelin", "furnished_zeppelin", "festive_zeppelin", "rocket")
            .option("size", "how big a zeppelin's hull is, full size if left out", SIZES)
            .help("lays an airframe down with its nose a few blocks ahead of the player, its tail away the way they face, and waits until it stands")
            .example("aerolarge:build zeppelin"));
    }

    private static Result zeppelin() throws CommandException {
        ServerPlayer player = player();
        AutopilotBlockEntity autopilot = nearest(player);
        ZeppelinPilot.Report report = autopilot == null ? null : autopilot.zeppelinReport();
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
        RocketPilot.Report report = autopilot == null ? null : autopilot.rocketReport();
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
            case "furnished" -> AirframeLayout.create(AerolargeConfig.SCALE.get(), true);
            default -> AirframeLayout.create(AerolargeConfig.SCALE.get(), false);
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
