package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
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
 * the way it points and slowing as it spreads, and a big puff of fog rolling after it (see the client's
 * {@code BlastPuffParticle}), fanned out over the floor, which drifts out across the room, slides along its walls and
 * hangs there for about twenty seconds, slowly rising: a hundred big puffs to a press fill the room as thousands of
 * small ones would, for next to nothing. On the server it sees that the machine stops pumping and heats up in time,
 * even where its scheduled next step was lost (see {@link LostTicks}).
 */
public class FogMachineBlockEntity extends BlockEntity {
    /** Where its nozzle's mouth is, in pixels in the model drawn facing north: across, up and along. */
    private static final double NOZZLE_X = 8;
    private static final double NOZZLE_Y = 3.5;
    private static final double NOZZLE_Z = 1;
    /** How many puffs of jet leave the nozzle a tick, and how fast the jet shoots out and spreads, in blocks a tick. */
    private static final int JETS = 2;
    private static final double JET_SPEED = 0.25;
    private static final double SPREAD = 0.04;
    /**
     * How fast the rolling fog drifts out at the slowest and the fastest, in blocks a tick: the air takes the speed off
     * it slowly, so the fastest crosses a big room. How wide its fan is either way of where the nozzle points, in
     * radians, most of it going ahead and some right round the sides. How high over the nozzle it starts, in blocks, so
     * a big puff is not half under the floor.
     */
    private static final double ROLL_SLOWEST = 0.08;
    private static final double ROLL_FASTEST = 0.4;
    private static final double FAN = 2.6;
    private static final double ROLL_LIFT = 0.6;
    /** How fast the rolling fog rises, in blocks a tick: hardly at all, so it hangs round the dancers' knees and up. */
    private static final double RISE = 0.008;

    public FogMachineBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.FOG_MACHINE_ENTITY.get(), pos, state);
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
        double round = random.triangle(0, FAN);
        double speed = ROLL_SLOWEST + random.nextDouble() * (ROLL_FASTEST - ROLL_SLOWEST);
        double ahead = Math.cos(round) * speed;
        double aside = Math.sin(round) * speed;
        level.addParticle(SkylinersRegistry.FOG_PUFF.get(), nozzle.x, nozzle.y + ROLL_LIFT, nozzle.z, facing.getStepX() * ahead - facing.getStepZ() * aside, RISE,
            facing.getStepZ() * ahead + facing.getStepX() * aside);
    }

    /** The server's tick: it stops pumping and heats up in time, even where its scheduled next step was lost. */
    static void serverTick(Level level, BlockPos pos, BlockState state, FogMachineBlockEntity machine) {
        if (state.getValue(FogMachineBlock.FOG) != FogMachineBlock.Fog.READY) {
            LostTicks.resume(level, pos, state);
        }
    }
}
