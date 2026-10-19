package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds nothing: the screen reads the airliner live, on the client, as its renderer draws it. Looking through the
 * outside cameras lives in the client package too and is reached through {@link #clientUse}, which the client sets,
 * so this class stays safe to load on a dedicated server.
 */
public class PassengerScreenBlockEntity extends BlockEntity {
    /** Set by the client: the local player has used the screen. */
    public static Consumer<PassengerScreenBlockEntity> clientUse = screen -> { };

    public PassengerScreenBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.PASSENGER_SCREEN_ENTITY.get(), pos, state);
    }
}
