package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.horn.ShipHorn;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The horn cord in the zeppelin's control car: a rope hanging from the ceiling with a wooden handle on its end. Pulled,
 * it hangs lower for a moment, the handle drawn down, and the zeppelin's horn sounds (see {@link ShipHorn}); it springs
 * back up as the blast dies away, and can be pulled again. PULLED is whether it is drawn down.
 */
public class HornCordBlock extends Block {
    private static final MapCodec<HornCordBlock> CODEC = simpleCodec(HornCordBlock::new);
    public static final BooleanProperty PULLED = BlockStateProperties.POWERED;
    /** How long it stays drawn down, in ticks: as long as the blast lasts. */
    private static final int HELD = 40;
    private static final VoxelShape HANGING = Shapes.or(Block.box(7.5, 5, 7.5, 8.5, 16, 8.5), Block.box(5, 2, 7, 11, 5, 9));
    private static final VoxelShape DRAWN = Shapes.or(Block.box(7.5, 3, 7.5, 8.5, 16, 8.5), Block.box(5, 0, 7, 11, 3, 9));

    public HornCordBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PULLED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PULLED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PULLED) ? DRAWN : HANGING;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PULLED)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            server.setBlock(pos, state.setValue(PULLED, true), Block.UPDATE_ALL);
            server.scheduleTick(pos, this, HELD);
            server.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.6F);
            ShipHorn.sound(server, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Let go, it springs back up. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PULLED)) {
            level.setBlock(pos, state.setValue(PULLED, false), Block.UPDATE_ALL);
        }
    }
}
