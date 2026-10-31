package dev.aerolarge.autopilot;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.airframe.RocketSilhouette;
import dev.aerolarge.compat.SableFlight;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A rocket's engines scorching the ground under her: while they blaze close over it, lifting off or coming in to land,
 * the grass round under her is burnt off a spot at a time, bare coarse earth left close under her engines and plain
 * earth farther out, as wide as she is built and a little wider, and the plants and snow on it go with it. So a pad of
 * scorched earth is left where she stood. Only grassy ground is scorched (grass, mycelium, podzol, moss and paths),
 * never stone, sand or anything built; the server config's {@code rocket.scorch} leaves it out. In the world, not
 * aboard her: her own blocks are in her plot.
 */
final class RocketScorch {
    /** How high over the ground her foot may be for her lit engines to scorch it, in blocks. */
    private static final double SCORCH_UNDER = 12;
    /** How many spots under her a tick are scorched: enough to bare the whole pad over a landing's last few seconds. */
    private static final int TRIES = 8;
    /** How far out from under her middle the ground is scorched, as a share of her body's widest radius. */
    private static final double SPREAD = 1.5;
    /** How far out the ground is burnt to coarse earth, as a share of how far it is scorched at all: plain beyond. */
    private static final double CHARRED = 0.6;
    /** The grassy ground her engines burn bare. */
    private static final Set<Block> GRASSY = Set.of(Blocks.GRASS_BLOCK, Blocks.MYCELIUM, Blocks.PODZOL, Blocks.MOSS_BLOCK, Blocks.DIRT_PATH);

    private RocketScorch() {
    }

    /**
     * Each tick of an assembled rocket's: a few spots of the ground round under her scorched while her lit engines are
     * close over it, given her radio altitude, how far under her middle her foot is, in blocks, and her engines' share
     * of full thrust.
     */
    static void tick(ServerLevel level, SableFlight.State flight, double radio, int footBelow, double thrust) {
        if (!AerolargeConfig.ROCKET_SCORCH.get() || thrust <= 0 || radio >= SCORCH_UNDER) {
            return;
        }
        Vec3 middle = flight.position();
        double reach = RocketSilhouette.body(RocketSilhouette.height() / 2) * RocketSilhouette.scaleOf(footBelow) * SPREAD;
        for (int spot = 0; spot < TRIES; spot++) {
            double way = level.getRandom().nextDouble() * Mth.TWO_PI;
            double out = reach * Math.sqrt(level.getRandom().nextDouble());
            int x = Mth.floor(middle.x + Math.cos(way) * out);
            int z = Mth.floor(middle.z + Math.sin(way) * out);
            scorch(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z), out < reach * CHARRED);
        }
    }

    /**
     * The ground scorched at a spot, given the open block over its top: a plant or snow there burnt away, and grassy
     * ground under it burnt to earth, coarse if charred, with a puff of smoke.
     */
    private static void scorch(ServerLevel level, BlockPos over, boolean charred) {
        BlockState on = level.getBlockState(over);
        if (on.getBlock() instanceof BushBlock || on.getBlock() instanceof SnowLayerBlock) {
            level.setBlock(over, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        BlockPos ground = over.below();
        if (!GRASSY.contains(level.getBlockState(ground).getBlock())) {
            return;
        }
        level.setBlock(ground, (charred ? Blocks.COARSE_DIRT : Blocks.DIRT).defaultBlockState(), Block.UPDATE_ALL);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, ground.getX() + 0.5, ground.getY() + 1.1, ground.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.02);
    }
}
