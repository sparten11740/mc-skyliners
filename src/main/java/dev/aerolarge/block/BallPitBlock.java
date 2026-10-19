package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pit of coloured plastic balls, sunk into like powder snow without the cold: whatever walks or falls in sinks
 * slowly to the bottom with a soft plop and a spray of balls, a little slowed while it wades. A fall into it never
 * hurts (one from high up lands on the balls first, as on powder snow, and only then sinks in), nobody with their head
 * under is smothered, and a jump always carries a player up and out, however deep the balls are.
 */
public class BallPitBlock extends Block {
    private static final MapCodec<BallPitBlock> CODEC = simpleCodec(BallPitBlock::new);
    /** What a long fall lands on before sinking in, like powder snow's: a little below the top. */
    private static final VoxelShape CATCH = Block.box(0, 0, 0, 16, 14.4, 16);
    /** A fall longer than this, in blocks, is caught on the surface; shorter ones sink straight in. */
    private static final float CATCH_FALL = 2.5F;
    /** How much of its speed across the pit something wading through keeps each tick. */
    private static final double WADE = 0.8;
    /** How much of its speed down something sinking keeps each tick: the balls hold it up. */
    private static final double SINK = 0.5;
    /** The least speed up of something jumping out, in blocks a tick: enough to rise out of any depth of balls. */
    private static final double CLIMB = 0.3;
    /** The spray of balls as something plops in, and the share of ticks something wading kicks up a ball. */
    private static final int PLOP_BALLS = 12;
    private static final float RUSTLE_CHANCE = 0.25F;

    public BallPitBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /** Nothing to bump into, but a long fall is caught on top, which is what keeps it from hurting. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext && entityContext.getEntity() != null
            && entityContext.getEntity().fallDistance > CATCH_FALL) {
            return CATCH;
        }
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Caught by the balls: the fall does no harm, and the plop comes as it sinks in. */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
    }

    /**
     * Sinking, wading and jumping out, worked by the ball at the entity's feet alone, so a deep pit holds it no harder
     * than a shallow one. Run on both sides: a player moves themselves, so their own client has to feel it too.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!pos.equals(entity.blockPosition())) {
            return;
        }
        Vec3 motion = entity.getDeltaMovement();
        double up = motion.y < 0 ? motion.y * SINK : motion.y > 0 ? Math.max(motion.y, CLIMB) : 0;
        entity.setDeltaMovement(motion.x * WADE, up, motion.z * WADE);
        entity.resetFallDistance();
        RandomSource random = level.getRandom();
        boolean entering = !level.getBlockState(BlockPos.containing(entity.xOld, entity.yOld, entity.zOld)).is(this);
        if (level instanceof ServerLevel server && entering) {
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), AerolargeRegistry.BALL_PIT_PLOP.get(), SoundSource.BLOCKS,
                1.0F, 0.8F + random.nextFloat() * 0.4F);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), entity.getX(), pos.getY() + 1, entity.getZ(),
                PLOP_BALLS, 0.3, 0.1, 0.3, 0.15);
        } else if (level.isClientSide && (entity.xOld != entity.getX() || entity.zOld != entity.getZ()) && random.nextFloat() < RUSTLE_CHANCE) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), entity.getX(), pos.getY() + 1, entity.getZ(),
                random.nextDouble() - 0.5, 0.2, random.nextDouble() - 0.5);
        }
    }
}
