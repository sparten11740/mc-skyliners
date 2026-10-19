package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Holds nothing: it is only there so the light's renderer can draw its glow. */
public class NavLightBlockEntity extends BlockEntity {
    public NavLightBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.NAV_LIGHT_ENTITY.get(), pos, state);
    }
}
