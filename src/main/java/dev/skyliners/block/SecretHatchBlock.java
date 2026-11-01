package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A secret hatch in a zeppelin's gangway: a length of grating just like the rest of it, but used it swings open, and
 * a rope leads down through it to the secret techno room under the gangway (see {@code ZeppelinTechnoRoom}); used
 * again it shuts. Now and then a musical note drifts up out of it, for whoever looks closely. OPEN is whether it
 * stands open, the way through.
 */
public class SecretHatchBlock extends Block {
    private static final MapCodec<SecretHatchBlock> CODEC = simpleCodec(SecretHatchBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** About one in how many of its display ticks a note drifts up out of it. */
    private static final int NOTE_ONE_IN = 6;
    /** Open, only its frame is left round the edge, nothing to stand on. */
    private static final VoxelShape FRAME = Shapes.or(Block.box(0, 14, 0, 16, 16, 1), Block.box(0, 14, 15, 16, 16, 16),
        Block.box(0, 14, 0, 1, 16, 16), Block.box(15, 14, 0, 16, 16, 16));

    public SecretHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? FRAME : Shapes.block();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean open = !state.getValue(OPEN);
            level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.9F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A note drifting up out of it now and then, a hint of what is below. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(NOTE_ONE_IN) == 0) {
            level.addParticle(ParticleTypes.NOTE, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                random.nextDouble(), 0, 0);
        }
    }
}
