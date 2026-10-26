package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Setting up an airframe's creative thrusters as they are built: each turned up to the configured setting, the way
 * its value box would (Create keeps a block entity's scroll value under "ScrollValue"), and its plume set for the
 * flames given, as its own setting would (Create Propulsion keeps it under "plumeType"): plasma only for its own
 * plume, as the server config may ask of the airliner's engines, since a plume's every puff is sent to every player
 * near, many times a second in flight, and costs each client dearly to draw; the airliner's control jets, which stand
 * in for control surfaces, and the zeppelin's engines, which turn propellers, show none. Other thrusters have no such
 * settings and keep the values unread.
 */
final class Thrusters {
    private Thrusters() {
    }

    static Fitting fullThrust(EngineFlames flames) {
        int setting = AerolargeConfig.THRUST_SETTING.get();
        return (blockEntity, rotation) -> set(blockEntity, setting, flames);
    }

    private static void set(BlockEntity blockEntity, int setting, EngineFlames flames) {
        if (blockEntity.getLevel() == null) {
            return;
        }
        HolderLookup.Provider registries = blockEntity.getLevel().registryAccess();
        CompoundTag data = blockEntity.saveWithoutMetadata(registries);
        data.putInt("ScrollValue", setting);
        data.putInt("plumeType", flames.thrusterPlume());
        blockEntity.loadWithComponents(data, registries);
    }
}
