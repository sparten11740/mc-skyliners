package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: the display's picture is drawn from the fin camera live, on the client, as its renderer draws it. */
public class TailCameraDisplayBlockEntity extends BlockEntity {
    public TailCameraDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.TAIL_CAMERA_DISPLAY_ENTITY.get(), pos, state);
    }
}
