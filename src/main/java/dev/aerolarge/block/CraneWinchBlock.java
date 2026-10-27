package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The sky crane's winch on the glass floor of a zeppelin's observation lounge: an iron frame round a wooden drum of
 * rope, the rope running down through the floor to the hook swinging under her (see the {@code CraneHook} entity), a
 * lever with a red knob at its side and the load dial over the drum, a needle on a green, amber and red arc. Used, the
 * lever goes up a notch, and used sneaking, down one: up it hoists, level it holds, down it lowers, the drum turning
 * and ratcheting either way, and it comes back level by itself once the hook catches or lets go of a load or the rope
 * runs as short or as long as it goes. NOTCH is the lever's notch; FACING is the way whoever works it looks.
 */
public class CraneWinchBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<CraneWinchBlock> CODEC = simpleCodec(CraneWinchBlock::new);
    /** The lever's notches: down lowers the hook, level holds it, up hoists it. */
    public static final int LOWER = 0;
    public static final int HOLD = 1;
    public static final int HOIST = 2;
    public static final IntegerProperty NOTCH = IntegerProperty.create("notch", LOWER, HOIST);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);
    /** The lever's click, higher for a notch further up; and the low note it gives with the crane switched off. */
    private static final float CLICK_LOW = 0.8F;
    private static final float CLICK_STEP = 0.15F;
    private static final float OFF_PITCH = 0.5F;

    public CraneWinchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(NOTCH, HOLD));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, NOTCH);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The lever up a notch, or down one sneaking, with a click; with the {@code skyCrane} switch off, a low note. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int notch = Math.clamp(state.getValue(NOTCH) + (player.isShiftKeyDown() ? -1 : 1), LOWER, HOIST);
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!AerolargeConfig.SKY_CRANE.get()) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 1.0F, OFF_PITCH);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof CraneWinchBlockEntity winch) {
            winch.workedBy(player);
        }
        if (notch != state.getValue(NOTCH)) {
            level.setBlock(pos, state.setValue(NOTCH, notch), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, CLICK_LOW + CLICK_STEP * notch);
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CraneWinchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AerolargeRegistry.CRANE_WINCH_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, winch) -> ((CraneWinchBlockEntity) winch).tick((ServerLevel) world);
    }
}
