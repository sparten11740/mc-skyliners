package dev.skyliners.exterior;

import dev.skyliners.block.Mount;
import dev.skyliners.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * An exhaust vent (see {@link HullFittingBlock}): a stack with a rain cap on a floor, a louvred box on a wall, or a
 * nozzle pointing down under a ceiling, each now and then letting a wisp of white vapour out of its mouth, up, out from
 * the wall or down, where its block is in the world as she flies.
 */
public class ExhaustVentBlock extends HullFittingBlock {
    /** How likely a wisp is each time the client looks the block over, a share. */
    private static final float WISP_CHANCE = 0.35F;
    /** How fast a wisp drifts out of the vent's mouth, in blocks a tick. */
    private static final double WISP_SPEED = 0.03;
    /** How far a wisp strays from the middle of the mouth, in blocks. */
    private static final double WISP_SPREAD = 0.12;

    public ExhaustVentBlock(Properties properties) {
        super(properties, HullFittings.EXHAUST_VENT);
    }

    /** On the client: now and then a wisp out of its mouth, the way its mount points it. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() > WISP_CHANCE) {
            return;
        }
        Mount mount = state.getValue(MOUNT);
        Direction out = mount == Mount.WALL ? state.getValue(FACING) : mount == Mount.FLOOR ? Direction.UP : Direction.DOWN;
        Vec3 way = Vec3.atLowerCornerOf(out.getNormal());
        Vec3 mouth = Vec3.atCenterOf(pos).add(way.scale(0.5))
            .add(random.nextGaussian() * WISP_SPREAD, random.nextGaussian() * WISP_SPREAD, random.nextGaussian() * WISP_SPREAD);
        Vec3 at = SableFlight.toWorld(level, mouth);
        Vec3 drift = SableFlight.toWorld(level, mouth.add(way)).subtract(at).scale(WISP_SPEED);
        level.addParticle(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, drift.x, drift.y, drift.z);
    }
}
