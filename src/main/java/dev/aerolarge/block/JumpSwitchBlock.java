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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The jump light switch in the instrument panel: each use turns every jump light by the doors from dark to red (stand
 * by), to green (go), and dark again, through the relays behind them. FACING is the way its face looks, toward the
 * pilots.
 */
public class JumpSwitchBlock extends HorizontalDirectionalBlock implements EntityBlock, LinkedControlBlockEntity.Driver {
    private static final MapCodec<JumpSwitchBlock> CODEC = simpleCodec(JumpSwitchBlock::new);
    public static final EnumProperty<JumpLightBlock.Signal> SIGNAL = JumpLightBlock.SIGNAL;
    public static final String LIGHTS = "lights";

    public JumpSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(SIGNAL, JumpLightBlock.Signal.OFF));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIGNAL);
    }

    @Override
    public int signal(BlockState state, String group) {
        return state.getValue(SIGNAL).power();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            JumpLightBlock.Signal signal = state.getValue(SIGNAL).next();
            level.setBlock(pos, state.setValue(SIGNAL, signal), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.2F);
            if (level.getBlockEntity(pos) instanceof Control control) {
                control.resync();
            }
            player.displayClientMessage(Component.translatable("message.aerolarge.jump_switch." + signal.getSerializedName()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkedControlBlockEntity(pos, state);
    }
}
