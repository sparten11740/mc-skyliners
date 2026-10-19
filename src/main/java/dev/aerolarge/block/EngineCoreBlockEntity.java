package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An engine's state on the client: how far it has spooled up toward the thrust its lever asks for. The engine's sound
 * and contrail live in the client package and are driven through {@link #clientTicker}, which the client sets, so
 * this class stays safe to load on a dedicated server.
 */
public class EngineCoreBlockEntity extends BlockEntity {
    /** Set by the client: plays the engine and lays its contrail each tick. */
    public static Consumer<EngineCoreBlockEntity> clientTicker = engine -> { };
    /** Share of full thrust gained or lost per tick: a turbofan takes a few seconds to spool up. */
    private static final float SPOOL_RATE = 0.012F;

    private float spool;

    public EngineCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ENGINE_CORE_ENTITY.get(), pos, state);
    }

    /** How far the engine has spooled up, 0 at rest to 1 at full thrust. */
    public float spool() {
        return spool;
    }

    static void clientTick(EngineCoreBlockEntity engine) {
        float target = engine.getBlockState().getValue(ThrustRelayBlock.POWER) / 15F;
        engine.spool += Math.clamp(target - engine.spool, -SPOOL_RATE, SPOOL_RATE);
        clientTicker.accept(engine);
    }
}
