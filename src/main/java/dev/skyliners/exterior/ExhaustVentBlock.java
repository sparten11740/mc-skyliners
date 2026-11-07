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
 * A vent on a ship's outside (see {@link HullFittingBlock}), now and then letting a wisp of white vapour out of its
 * mouth, up, out from the wall or down, where its block is in the world as she flies: the exhaust vent, a stack with a
 * rain cap on a floor, a louvred box on a wall, or a nozzle pointing down under a ceiling; and the vent grille, flush
 * with what holds it, a slatted deck grating on a floor, a hooded louvred grille on a wall, or an intake with a hub
 * on its struts under a ceiling.
 */
public class ExhaustVentBlock extends HullFittingBlock {
    /** How far out from the middle of its block a stack's mouth is, the way it points, in blocks: its block's face. */
    public static final double STACK_MOUTH = 0.5;
    /** How far out from the middle of its block a grille's mouth is, in blocks: two pixels off what holds it. */
    public static final double GRILLE_MOUTH = -0.375;
    /** How likely a wisp is each time the client looks the block over, a share. */
    private static final float WISP_CHANCE = 0.35F;
    /** How fast a wisp drifts out of the vent's mouth, in blocks a tick. */
    private static final double WISP_SPEED = 0.03;
    /** How far a wisp strays from the middle of the mouth, in blocks. */
    private static final double WISP_SPREAD = 0.12;

    /** How far out from the middle of its block its mouth is, the way it points, in blocks. */
    private final double mouth;

    public ExhaustVentBlock(Properties properties, Outline outline, double mouth) {
        super(properties, outline);
        this.mouth = mouth;
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
        Vec3 from = Vec3.atCenterOf(pos).add(way.scale(mouth))
            .add(random.nextGaussian() * WISP_SPREAD, random.nextGaussian() * WISP_SPREAD, random.nextGaussian() * WISP_SPREAD);
        Vec3 at = SableFlight.toWorld(level, from);
        Vec3 drift = SableFlight.toWorld(level, from.add(way)).subtract(at).scale(WISP_SPEED);
        level.addParticle(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, drift.x, drift.y, drift.z);
    }
}
