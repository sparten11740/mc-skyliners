package dev.aerolarge.airframe;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Setting up a zeppelin's hot air burners as they are built: each turned down to give no more hot air at full signal
 * than its share of what its gas cell holds, the way its value box would (Create keeps a block entity's scroll value
 * under "ScrollValue", Create Aeronautics' burners their most hot air in m³). A small hull's cells hold less than a
 * single burner gives; the autopilot works its burners from what each cell's give in all (see the pilot's cells).
 */
final class Burners {
    private Burners() {
    }

    /** A burner turned down to give so much hot air at full signal, in whole m³. */
    static Fitting turnedDown(int gas) {
        return (blockEntity, rotation) -> set(blockEntity, gas);
    }

    private static void set(BlockEntity blockEntity, int gas) {
        if (blockEntity.getLevel() == null) {
            return;
        }
        HolderLookup.Provider registries = blockEntity.getLevel().registryAccess();
        CompoundTag data = blockEntity.saveWithoutMetadata(registries);
        data.putInt("ScrollValue", gas);
        blockEntity.loadWithComponents(data, registries);
    }
}
