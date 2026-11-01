package dev.skyliners;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.skyliners.airframe.AirframeBuild;
import dev.skyliners.airframe.AirframeLayout;
import dev.skyliners.airframe.Layout;
import dev.skyliners.airframe.RocketLayout;
import dev.skyliners.airport.LaunchPad;
import dev.skyliners.airframe.ZeppelinLayout;
import dev.skyliners.airframe.ZeppelinOptions;
import dev.skyliners.airframe.ZeppelinSize;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /skyliners build <pos> <north|east|south|west> [furnished|zeppelin [furnished]]}: builds an airliner, or a
 * zeppelin, for server operators without a blueprint in hand, its nose tip at pos and its tail stretching away the
 * given way. Unlike the blueprint it builds through whatever is in the way, and reports how much that was.
 */
final class SkylinersCommand {
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
                    .then(Commands.literal("launch_pad").executes(context -> buildLaunchPad(context, along)))));
        }
        dispatcher.register(Commands.literal(Skyliners.MOD_ID).requires(source -> source.hasPermission(2)).then(build));
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
