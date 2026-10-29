package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The fog machine's fog, drawn on the client while it pumps: each tick a jet of cloud out of its nozzle, shooting out
 * the way it points and slowing as it spreads, and thick smoke rolling after it, which drifts and hangs a long while,
 * slowly rising, over the floor.
 */
public class FogMachineBlockEntity extends BlockEntity {
    /** Where its nozzle's mouth is, in pixels in the model drawn facing north: across, up and along. */
    private static final double NOZZLE_X = 8;
    private static final double NOZZLE_Y = 3.5;
    private static final double NOZZLE_Z = 1;
    /** How many puffs of jet and of rolling fog leave the nozzle a tick. */
    private static final int JETS = 2;
    private static final int ROLLS = 1;
    /** How fast the jet shoots out and the rolling fog drifts, and how far either spreads, in blocks a tick. */
    private static final double JET_SPEED = 0.25;
    private static final double ROLL_SPEED = 0.06;
    private static final double SPREAD = 0.04;
    /** How fast the rolling fog rises, in blocks a tick: hardly at all, so it hangs over the floor. */
    private static final double RISE = 0.004;

    public FogMachineBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.FOG_MACHINE_ENTITY.get(), pos, state);
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, FogMachineBlockEntity machine) {
        if (state.getValue(FogMachineBlock.FOG) != FogMachineBlock.Fog.FOGGING) {
            return;
        }
        RandomSource random = level.getRandom();
        Direction facing = state.getValue(FogMachineBlock.FACING);
        Vec3 nozzle = Vec3.atLowerCornerOf(pos).add(TurnedShapes.point(facing, NOZZLE_X, NOZZLE_Z)).add(0, NOZZLE_Y / 16, 0);
        for (int jet = 0; jet < JETS; jet++) {
            level.addParticle(ParticleTypes.CLOUD, nozzle.x, nozzle.y, nozzle.z, facing.getStepX() * JET_SPEED + random.triangle(0, SPREAD),
                random.triangle(0, SPREAD), facing.getStepZ() * JET_SPEED + random.triangle(0, SPREAD));
        }
        for (int roll = 0; roll < ROLLS; roll++) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, nozzle.x, nozzle.y, nozzle.z, facing.getStepX() * ROLL_SPEED + random.triangle(0, SPREAD),
                RISE, facing.getStepZ() * ROLL_SPEED + random.triangle(0, SPREAD));
        }
    }

    /** The server's tick: it stops pumping and heats up in time, even where its scheduled next step was lost. */
    static void serverTick(Level level, BlockPos pos, BlockState state, FogMachineBlockEntity machine) {
        if (state.getValue(FogMachineBlock.FOG) != FogMachineBlock.Fog.READY) {
            LostTicks.resume(level, pos, state);
        }
    }
}
