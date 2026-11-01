package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Setting up an airframe's creative thrusters as they are built: each turned up to the configured setting, the way
 * its value box would (Create keeps a block entity's scroll value under "ScrollValue"), and its plume set for the
 * flames given, as its own setting would (Create Propulsion keeps it under "plumeType"): plasma only for its own
 * plume, as the server config may ask of the airliner's engines, since a plume's every puff is sent to every player
 * near, many times a second in flight, and costs each client dearly to draw; the airliner's control jets, which stand
 * in for control surfaces, the rocket's side jets, which are heard instead, and the zeppelin's engines, which turn
 * propellers, show none. Other thrusters have no such settings and keep the values unread.
 */
public final class Thrusters {
    private Thrusters() {
    }

    static Fitting fullThrust(EngineFlames flames) {
        return thrust(flames, 1);
    }

    /** A thruster turned up to a share of the configured setting, at least its least. */
    static Fitting thrust(EngineFlames flames, double share) {
        int setting = Math.max(1, (int) Math.round(SkylinersConfig.THRUST_SETTING.get() * share));
        return (blockEntity, rotation) -> set(blockEntity, setting, flames);
    }

    /**
     * Leaves out the plume of the thruster a relay drives, if it was built with one: nothing else reaches a rocket
     * built before her side jets went without. A thruster pushes toward its relay, which tells it among the relay's
     * neighbours.
     */
    public static void leavePlumeOut(Level level, BlockPos relay) {
        for (Direction pushes : Direction.Plane.HORIZONTAL) {
            BlockPos at = relay.relative(pushes.getOpposite());
            BlockState state = level.getBlockState(at);
            if (state.hasProperty(BlockStateProperties.FACING) && state.getValue(BlockStateProperties.FACING) == pushes) {
                leavePlumeOut(level, at, state);
            }
        }
    }

    private static void leavePlumeOut(Level level, BlockPos at, BlockState state) {
        BlockEntity thruster = level.getBlockEntity(at);
        if (thruster == null) {
            return;
        }
        HolderLookup.Provider registries = level.registryAccess();
        CompoundTag data = thruster.saveWithoutMetadata(registries);
        int none = EngineFlames.NONE.thrusterPlume();
        if (data.getInt("plumeType") != none) {
            data.putInt("plumeType", none);
            thruster.loadWithComponents(data, registries);
            thruster.setChanged();
            level.sendBlockUpdated(at, state, state, Block.UPDATE_CLIENTS);
        }
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
