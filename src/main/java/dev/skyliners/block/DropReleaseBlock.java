package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The drop release on the flight deck's centre console, beside the first officer, wired to the drop bay in the belly:
 * the first use opens the bay doors and arms it, each use after that drops the next load, and using it sneaking closes
 * the doors again. FACING is the way its face looks.
 */
public class DropReleaseBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<DropReleaseBlock> CODEC = simpleCodec(DropReleaseBlock::new);
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    /** A module of the console with the release's guarded switch on it. */
    private static final VoxelShape SHAPE = ConsoleModule.with(3, 4);

    public DropReleaseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(ARMED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ARMED);
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
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof DropReleaseBlockEntity release)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        DropBayBlockEntity bay = release.bay();
        if (bay == null) {
            player.displayClientMessage(Component.translatable("message.skyliners.drop_release.no_bay"), true);
            return InteractionResult.SUCCESS;
        }
        boolean armed = state.getValue(ARMED);
        Component message;
        if (player.isShiftKeyDown() || !armed) {
            armed = !player.isShiftKeyDown();
            level.setBlock(pos, state.setValue(ARMED, armed), Block.UPDATE_ALL);
            bay.setOpen(armed);
            message = Component.translatable(armed ? "message.skyliners.drop_release.armed" : "message.skyliners.drop_release.safe");
        } else {
            message = bay.release(player);
        }
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, armed ? 1.4F : 1.0F);
        player.displayClientMessage(message, true);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DropReleaseBlockEntity(pos, state);
    }
}
