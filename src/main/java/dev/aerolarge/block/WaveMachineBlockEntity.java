package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The wave machine's waves, drawn on the client while it runs: each tick a few spots of the pool's water round it, a
 * row under its foot where it stands open to the air, splash and bubble, so the whole channel churns. The open water is
 * looked for once as the waves start on this client, within so many blocks of it, and none of it while it stands idle.
 * On the server it sees that the waves die down in time, even where their scheduled end was lost (see
 * {@link LostTicks}).
 */
public class WaveMachineBlockEntity extends BlockEntity {
    /** How far round it the waves reach, in blocks: past the far side of the widest rocket's pool. */
    private static final int REACH = 24;
    /** How many spots of its open water splash each tick while it runs. */
    private static final int SPOTS = 6;
    /**
     * How many splashes a spot of open water throws up, how high they jump, in blocks a tick, and how far they spread.
     */
    private static final int SPLASHES = 3;
    private static final double JUMP = 0.12;
    private static final double SPREAD = 0.08;
    /** One in how many spots of open water also wakes, the white wash a boat leaves. */
    private static final int WAKE_EVERY = 3;

    /** The open water round it, found as its waves start on this client; null while it stands idle. */
    @Nullable
    private List<BlockPos> water;

    public WaveMachineBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.WAVE_MACHINE_ENTITY.get(), pos, state);
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, WaveMachineBlockEntity machine) {
        if (!state.getValue(WaveMachineBlock.RUNNING)) {
            machine.water = null;
            return;
        }
        if (machine.water == null) {
            machine.water = openWater(level, pos);
        }
        RandomSource random = level.getRandom();
        for (int spot = 0; spot < SPOTS && !machine.water.isEmpty(); spot++) {
            splash(level, machine.water.get(random.nextInt(machine.water.size())), random);
        }
    }

    /** The server's tick: its waves die down in time, even where their scheduled end was lost. */
    static void serverTick(Level level, BlockPos pos, BlockState state, WaveMachineBlockEntity machine) {
        if (state.getValue(WaveMachineBlock.RUNNING)) {
            LostTicks.resume(level, pos, state);
        }
    }

    /** The blocks of water a row under its foot, within its reach, that stand open to the air. */
    private static List<BlockPos> openWater(Level level, BlockPos pos) {
        List<BlockPos> water = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-REACH, -1, -REACH), pos.offset(REACH, -1, REACH))) {
            if (level.getFluidState(at).is(FluidTags.WATER) && level.getBlockState(at.above()).isAir()) {
                water.add(at.immutable());
            }
        }
        return water;
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
