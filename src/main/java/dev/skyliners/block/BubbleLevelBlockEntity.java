package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A bubble level's block entity: it holds nothing, but its renderer draws the bubble where the ship's pitch puts it
 * (see the client's {@code BubbleLevelRenderer}).
 */
public class BubbleLevelBlockEntity extends BlockEntity {
    public BubbleLevelBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.BUBBLE_LEVEL_ENTITY.get(), pos, state);
    }
}
