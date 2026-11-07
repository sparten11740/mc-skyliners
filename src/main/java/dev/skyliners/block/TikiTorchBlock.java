package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tiki torch, for the freighter's beach in her lounge: a bamboo pole with a woven head and a flame flickering on top
 * of it, warm and bright, sparks and a wisp of smoke rising from it, where its block is in the world as she flies.
 */
public class TikiTorchBlock extends Block {
    private static final MapCodec<TikiTorchBlock> CODEC = simpleCodec(TikiTorchBlock::new);
    /** Its pole and head. */
    private static final VoxelShape SHAPE = Block.box(6, 0, 6, 10, 16, 10);
    /** Where its flame burns, over its block's foot, in blocks; and how far its flames stray from the middle. */
    private static final double FLAME_UP = 19.5 / 16;
    private static final double FLAME_SPREAD = 0.08;
    /** How likely a wisp of smoke is with each flicker. */
    private static final float SMOKE_CHANCE = 0.3F;

    public TikiTorchBlock(Properties properties) {
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

    /** On the client: its flame flickering, now and then a wisp of smoke over it. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Vec3 flame = new Vec3(pos.getX() + 0.5 + random.nextGaussian() * FLAME_SPREAD, pos.getY() + FLAME_UP,
            pos.getZ() + 0.5 + random.nextGaussian() * FLAME_SPREAD);
        Vec3 at = SableFlight.toWorld(level, flame);
        level.addParticle(ParticleTypes.FLAME, at.x, at.y, at.z, 0, 0.01, 0);
        if (random.nextFloat() < SMOKE_CHANCE) {
            level.addParticle(ParticleTypes.SMOKE, at.x, at.y + 0.15, at.z, 0, 0.02, 0);
        }
    }
}
