package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The wave machine's waves, drawn on the client while it runs: each tick a few spots round it are tried, a row under
 * its foot, where the pool's water stands open to the air, and each of them splashes and bubbles, so the whole channel
 * churns. Only so many are tried a tick, wherever the pool is, and none while it stands idle.
 */
public class WaveMachineBlockEntity extends BlockEntity {
    /** How far round it the waves reach, in blocks: past the far side of the widest rocket's pool. */
    private static final int REACH = 24;
    /** How many spots round it are tried each tick while it runs. */
    private static final int TRIES = 16;
    /** How many splashes a spot of open water throws up, how high they jump, in blocks a tick, and how far they spread. */
    private static final int SPLASHES = 3;
    private static final double JUMP = 0.12;
    private static final double SPREAD = 0.08;
    /** One in how many spots of open water also wakes, the white wash a boat leaves. */
    private static final int WAKE_EVERY = 3;

    public WaveMachineBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.WAVE_MACHINE_ENTITY.get(), pos, state);
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, WaveMachineBlockEntity machine) {
        if (!state.getValue(WaveMachineBlock.RUNNING)) {
            return;
        }
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < TRIES; attempt++) {
            at.set(pos.getX() + random.nextIntBetweenInclusive(-REACH, REACH), pos.getY() - 1, pos.getZ() + random.nextIntBetweenInclusive(-REACH, REACH));
            if (level.getFluidState(at).is(FluidTags.WATER) && level.getBlockState(at.above()).isAir()) {
                splash(level, at, random);
            }
        }
    }

    /** Splashes jumping off the water's surface at a block of it, bubbles rising under them, now and then a wake. */
    private static void splash(Level level, BlockPos water, RandomSource random) {
        double x = water.getX() + random.nextDouble();
        double z = water.getZ() + random.nextDouble();
        double surface = water.getY() + 0.9;
        for (int splash = 0; splash < SPLASHES; splash++) {
            level.addParticle(ParticleTypes.SPLASH, x, surface, z, random.triangle(0, SPREAD), JUMP * random.nextDouble(), random.triangle(0, SPREAD));
        }
        level.addParticle(ParticleTypes.BUBBLE, x, water.getY() + 0.4, z, 0, JUMP, 0);
        if (random.nextInt(WAKE_EVERY) == 0) {
            level.addParticle(ParticleTypes.FISHING, x, surface, z, random.triangle(0, SPREAD), 0, random.triangle(0, SPREAD));
        }
    }
}
