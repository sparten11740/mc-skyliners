package dev.skyliners;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.skyliners.airframe.AirframeBuild;
import dev.skyliners.airframe.AirframeLayout;
import dev.skyliners.airframe.FreighterLayout;
import dev.skyliners.airframe.Layout;
import dev.skyliners.airframe.RocketLayout;
import dev.skyliners.airport.LaunchPad;
import dev.skyliners.airframe.ZeppelinLayout;
import dev.skyliners.airframe.ZeppelinOptions;
import dev.skyliners.airframe.ZeppelinSize;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.FreighterFlight;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.spaceweather.SpaceWeather;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /skyliners build <pos> <north|east|south|west> [furnished|zeppelin [furnished]|rocket|freighter|launch_pad]}:
 * builds an airliner, a zeppelin, a rocket, a freighter or a launch pad, for server operators without a blueprint in
 * hand, its nose tip at pos and its tail stretching away the given way. Unlike the blueprint it builds through
 * whatever is in the way, and reports how much that was. {@code /skyliners spaceweather
 * <meteor_shower|asteroid_storm|solar_flare>} brings space weather over the nearest freighter at once (see
 * {@link SpaceWeather}), to try it without waiting for it to blow up there.
 */
final class SkylinersCommand {
    /** How far from whoever runs it a freighter is looked for to bring space weather over, in blocks. */
    private static final double WEATHER_REACH = 256;

    private SkylinersCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> build = Commands.literal("build");
        for (Direction along : Direction.Plane.HORIZONTAL) {
            build.then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.literal(along.getSerializedName())
                    .executes(context -> build(context, along, false))
                    .then(Commands.literal("furnished").executes(context -> build(context, along, true)))
                    .then(sized(Commands.literal("zeppelin"), along, ZeppelinOptions.PLAIN)
                        .then(sized(Commands.literal("furnished"), along, ZeppelinOptions.FURNISHED))
                        .then(sized(Commands.literal("festive"), along, ZeppelinOptions.FESTIVE)))
                    .then(Commands.literal("rocket").executes(context -> build(context, along, RocketLayout.create(), "commands.skyliners.build_rocket")))
                    .then(Commands.literal("freighter").executes(context -> build(context, along, FreighterLayout.create(), "commands.skyliners.build_freighter")))
                    .then(Commands.literal("launch_pad").executes(context -> buildLaunchPad(context, along)))));
        }
        LiteralArgumentBuilder<CommandSourceStack> weather = Commands.literal("spaceweather");
        for (SpaceWeather.Kind kind : SpaceWeather.Kind.values()) {
            weather.then(Commands.literal(kind.getSerializedName()).executes(context -> spaceWeather(context, kind)));
        }
        dispatcher.register(Commands.literal(Skyliners.MOD_ID).requires(source -> source.hasPermission(2)).then(build).then(weather));
    }

    /**
     * Space weather of a kind over the freighter nearest whoever runs it, at once, as long as she flies from y 1,500 up
     * with someone aboard; or why not.
     */
    private static int spaceWeather(CommandContext<CommandSourceStack> context, SpaceWeather.Kind kind) {
        CommandSourceStack source = context.getSource();
        AutopilotBlockEntity nearest = null;
        double best = WEATHER_REACH * WEATHER_REACH;
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(source.getLevel())) {
            double away = SableFlight.toWorld(source.getLevel(), Vec3.atCenterOf(autopilot.getBlockPos())).distanceToSqr(source.getPosition());
            if (away < best && autopilot.craft() == Craft.FREIGHTER) {
                best = away;
                nearest = autopilot;
            }
        }
        SpaceWeather.Answer answer = FreighterFlight.spaceWeather(source.getLevel(), nearest, kind);
        Component said = Component.translatable("commands.skyliners.space_weather." + answer.name().toLowerCase(Locale.ROOT),
            Component.translatable("commands.skyliners.space_weather.kind." + kind.getSerializedName()));
        if (answer != SpaceWeather.Answer.COMING) {
            source.sendFailure(said);
            return 0;
        }
        source.sendSuccess(() -> said, true);
        return 1;
    }

    private static int build(CommandContext<CommandSourceStack> context, Direction along, boolean furnished) throws CommandSyntaxException {
        return build(context, along, AirframeLayout.create(SkylinersConfig.SCALE.get(), furnished), "commands.skyliners.build");
    }

    /** A zeppelin's word in the command, built at full size, or at the size named after it. */
    private static LiteralArgumentBuilder<CommandSourceStack> sized(LiteralArgumentBuilder<CommandSourceStack> word, Direction along, ZeppelinOptions options) {
        word.executes(context -> buildZeppelin(context, along, options));
        for (ZeppelinSize size : ZeppelinSize.values()) {
            word.then(Commands.literal(size.name().toLowerCase(Locale.ROOT)).executes(context -> buildZeppelin(context, along, options.sized(size))));
        }
        return word;
    }

    private static int buildZeppelin(CommandContext<CommandSourceStack> context, Direction along, ZeppelinOptions options) throws CommandSyntaxException {
        return build(context, along, ZeppelinLayout.create(options), "commands.skyliners.build_zeppelin");
    }

    private static int buildLaunchPad(CommandContext<CommandSourceStack> context, Direction along) throws CommandSyntaxException {
        int deck = BlockPosArgument.getLoadedBlockPos(context, "pos").getY();
        int roomUnder = deck - context.getSource().getLevel().getMinBuildHeight();
        return build(context, along, LaunchPad.create(roomUnder), "commands.skyliners.build_launch_pad");
    }

    private static int build(CommandContext<CommandSourceStack> context, Direction along, Layout layout, String said) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos nose = BlockPosArgument.getLoadedBlockPos(context, "pos");
        AirframeBuild.Placement placement = AirframeBuild.Placement.of(level, layout, nose, along);
        if (placement.top() >= level.getMaxBuildHeight()) {
            source.sendFailure(Component.translatable(layout.message("too_high")));
            return 0;
        }
        AirframeBuild.start(level, placement, source.getPlayer());
        source.sendSuccess(() -> Component.translatable(said, placement.positions().length, placement.blocked()), true);
        return placement.positions().length;
    }
}
