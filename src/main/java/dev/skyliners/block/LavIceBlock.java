package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lump of blue lav ice on the ground: the frozen blue toilet water an airliner's vacuum toilet drops (see
 * {@link dev.skyliners.entity.BlueIceChunk}), light blue, see-through and glinting. A chunk only leaves one where there
 * was nothing at all, on solid ground, and it never stays, nor does one a player sets down: it melts away a few minutes
 * after it lands, or at once when the ground under it goes. Broken, it gives nothing back.
 */
public class LavIceBlock extends Block {
    private static final MapCodec<LavIceBlock> CODEC = simpleCodec(LavIceBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 8, 13);
    /** How long it lies before it has melted away, in ticks: three minutes, and up to one more, so a pile thins out. */
    private static final int MELT = 3 * 60 * 20;
    private static final int MELT_SPREAD = 60 * 20;
    /** One in so many of its display ticks it glints. */
    private static final int GLINT = 4;
    /** Melting, the drips it leaves: how many and how far they spread. */
    private static final int DRIPS = 8;
    private static final double DRIP_SPREAD = 0.25;

    public LavIceBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    /** Set down, it starts to melt. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, MELT + level.getRandom().nextInt(MELT_SPREAD));
        }
    }

    /** Melted: gone, with a last drip. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, DRIPS, DRIP_SPREAD, 0, DRIP_SPREAD, 0);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(GLINT) == 0) {
            level.addParticle(ParticleTypes.WAX_OFF, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.1 + random.nextDouble() * 0.4,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0, 0);
        }
    }
}
