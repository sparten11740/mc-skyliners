package dev.aerolarge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aerolarge.airframe.AirframeBuild;
import dev.aerolarge.airframe.AirframeLayout;
import dev.aerolarge.airframe.Layout;
import dev.aerolarge.airframe.ZeppelinLayout;
import dev.aerolarge.airframe.ZeppelinOptions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /aerolarge build <pos> <north|east|south|west> [furnished|zeppelin [furnished]]}: builds an airliner, or a
 * zeppelin, for server operators without a blueprint in hand, its nose tip at pos and its tail stretching away the
 * given way. Unlike the blueprint it builds through whatever is in the way, and reports how much that was.
 */
final class AerolargeCommand {
    private AerolargeCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> build = Commands.literal("build");
        for (Direction along : Direction.Plane.HORIZONTAL) {
            build.then(Commands.argument("pos", BlockPosArgument.blockPos())
                .then(Commands.literal(along.getSerializedName())
                    .executes(context -> build(context, along, false))
                    .then(Commands.literal("furnished").executes(context -> build(context, along, true)))
                    .then(Commands.literal("zeppelin").executes(context -> buildZeppelin(context, along, ZeppelinOptions.PLAIN))
                        .then(Commands.literal("furnished").executes(context -> buildZeppelin(context, along, ZeppelinOptions.FURNISHED)))
                        .then(Commands.literal("festive").executes(context -> buildZeppelin(context, along, ZeppelinOptions.FESTIVE))))));
        }
        dispatcher.register(Commands.literal(Aerolarge.MOD_ID).requires(source -> source.hasPermission(2)).then(build));
    }

    private static int build(CommandContext<CommandSourceStack> context, Direction along, boolean furnished) throws CommandSyntaxException {
        return build(context, along, AirframeLayout.create(AerolargeConfig.SCALE.get(), furnished), "commands.aerolarge.build");
    }

    private static int buildZeppelin(CommandContext<CommandSourceStack> context, Direction along, ZeppelinOptions options) throws CommandSyntaxException {
        return build(context, along, ZeppelinLayout.create(options), "commands.aerolarge.build_zeppelin");
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
