package dev.aerolarge.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A control module on the flight deck's centre console that sets something all through the cabin: each use moves it
 * to its next setting, which then sweeps from the front of the cabin to the back (see {@link CabinControlBlockEntity}).
 * FACING is the way its face looks, toward the pilots.
 */
public abstract class CabinControlBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** A module of the console with a knob or a switch on it. */
    private static final VoxelShape SHAPE = ConsoleModule.with(2, 3);

    protected CabinControlBlock(Properties properties) {
        super(properties);
    }

    /** The control at its next setting. */
    protected abstract BlockState next(BlockState control);

    /** What the control says when set, for the one who set it. */
    protected abstract Component describe(BlockState control);

    /** Whether the control works the target: whether its setting sets anything of it. */
    public abstract boolean works(BlockState target);

    /** The target as the control's setting has it; the target itself if the control does not work it. */
    public abstract BlockState applyTo(BlockState control, BlockState target);

    /** Heard now and then along the cabin as a setting sweeps through it; null for none. */
    @Nullable
    public SoundEvent sweepSound() {
        return null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next = next(state);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.2F);
            if (level.getBlockEntity(pos) instanceof CabinControlBlockEntity control) {
                control.sweep();
            }
            player.displayClientMessage(describe(next), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CabinControlBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CabinControlBlockEntity control) {
                control.tick();
            }
        };
    }
}
