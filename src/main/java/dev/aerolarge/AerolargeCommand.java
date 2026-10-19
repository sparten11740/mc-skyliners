package dev.aerolarge;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aerolarge.airframe.AirframeBuild;
import dev.aerolarge.airframe.AirframeLayout;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /aerolarge build <pos> <north|east|south|west> [furnished]}: builds an airliner for server operators
 * without a blueprint in hand, its nose tip at pos and its tail stretching away the given way. Unlike the blueprint
 * it builds through whatever is in the way, and reports how much that was.
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
                    .then(Commands.literal("furnished").executes(context -> build(context, along, true)))));
        }
        dispatcher.register(Commands.literal(Aerolarge.MOD_ID).requires(source -> source.hasPermission(2)).then(build));
    }

    private static int build(CommandContext<CommandSourceStack> context, Direction along, boolean furnished) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos nose = BlockPosArgument.getLoadedBlockPos(context, "pos");
        AirframeLayout layout = AirframeLayout.create(AerolargeConfig.SCALE.get(), furnished);
        AirframeBuild.Placement placement = AirframeBuild.Placement.of(level, layout, nose, along);
        if (placement.top() >= level.getMaxBuildHeight()) {
            source.sendFailure(Component.translatable("message.aerolarge.too_high"));
            return 0;
        }
        AirframeBuild.start(level, placement, source.getPlayer());
        source.sendSuccess(() -> Component.translatable("commands.aerolarge.build", placement.positions().size(), placement.blocked()), true);
        return placement.positions().size();
    }
}
