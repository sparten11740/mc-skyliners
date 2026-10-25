package dev.aerolarge.compat;

import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.content.contraptions.IDisplayAssemblyExceptions;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import dev.aerolarge.AerolargeConfig;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The airliner's Physics Assembler, worked the way a Create deployer works it: for a deployer it assembles at once,
 * where a player gets its lever to hold, so the autopilot can assemble its airliner when asked to fly it. The assembler
 * is found by its registry id from the server config ({@code assemblerBlock}); Create is compiled against, Create
 * Aeronautics' Simulated, which makes the assembler, is not.
 */
public final class PhysicsAssembler {
    private PhysicsAssembler() {
    }

    /** Whether the block is the Physics Assembler the config names. */
    public static boolean is(BlockState state) {
        ResourceLocation id = ResourceLocation.tryParse(AerolargeConfig.ASSEMBLER_BLOCK.get());
        return id != null && !state.isAir() && BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
    }

    /**
     * Uses the Physics Assembler at a position as a deployer of the player's would. Null once it has assembled, when it
     * stands among the new airliner's blocks and no longer there; otherwise why not, as the assembler says it.
     */
    @Nullable
    public static Component assemble(ServerLevel level, BlockPos at, ServerPlayer player) {
        DeployerFakePlayer deployer = new DeployerFakePlayer(level, player.getUUID());
        level.getBlockState(at).useWithoutItem(level, deployer, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        if (!is(level.getBlockState(at))) {
            return null;
        }
        AssemblyException reason = level.getBlockEntity(at) instanceof IDisplayAssemblyExceptions shown ? shown.getLastAssemblyException() : null;
        return reason != null ? reason.component : Component.translatable("message.aerolarge.autopilot.assembly_unknown");
    }
}
