package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The seatbelt switch in the instrument panel: each use turns the airliner's "fasten seatbelts" signs on or off,
 * through the relays beside them, and while they are on, nobody seated aboard can get up (see
 * {@link dev.aerolarge.cabin.Seatbelts}). Turned on, it opens every curtain aboard (see
 * {@link SeatbeltSwitchBlockEntity}). FACING is the way its face looks, toward the pilots.
 */
public class SeatbeltSwitchBlock extends HorizontalDirectionalBlock implements EntityBlock, LinkedControlBlockEntity.Driver {
    private static final MapCodec<SeatbeltSwitchBlock> CODEC = simpleCodec(SeatbeltSwitchBlock::new);
    public static final BooleanProperty ON = BlockStateProperties.POWERED;
    public static final String SIGNS = "signs";

    public SeatbeltSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(ON, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON);
    }

    @Override
    public int signal(BlockState state, String group) {
        return state.getValue(ON) ? 15 : 0;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean on = !state.getValue(ON);
            level.setBlock(pos, state.setValue(ON, on), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, on ? 1.3F : 1.0F);
            if (level.getBlockEntity(pos) instanceof Control control) {
                control.resync();
            }
            if (on && level.getBlockEntity(pos) instanceof SeatbeltSwitchBlockEntity seatbelts) {
                seatbelts.openCurtains();
            }
            player.displayClientMessage(Component.translatable(on ? "message.aerolarge.seatbelts.on" : "message.aerolarge.seatbelts.off"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SeatbeltSwitchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof SeatbeltSwitchBlockEntity seatbelts) {
                seatbelts.tick();
            }
        };
    }
}
