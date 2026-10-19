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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The nose wheel tiller, a handwheel on the flight deck's centre console between the pilots' seats. Use it to turn a
 * notch to the right, sneak and use it to turn a notch to the left; the nose gear's wheel mounts steer by a redstone
 * signal at their sides, which the tiller's relays give. FACING is the way the nose points.
 */
public class TillerBlock extends HorizontalDirectionalBlock implements EntityBlock, LinkedControlBlockEntity.Driver {
    private static final MapCodec<TillerBlock> CODEC = simpleCodec(TillerBlock::new);
    /** From hard left (0) through straight ahead (2) to hard right (4). */
    public static final IntegerProperty STEER = IntegerProperty.create("steer", 0, 4);
    public static final String LEFT = "left";
    public static final String RIGHT = "right";
    public static final int CENTRE = 2;
    /** Signal per notch off centre. */
    private static final int SIGNAL_PER_NOTCH = 7;
    /** A module of the console with the tiller's handwheel on it. */
    private static final VoxelShape SHAPE = ConsoleModule.with(3, 4.5);

    public TillerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STEER, CENTRE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STEER);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public int signal(BlockState state, String group) {
        int steer = state.getValue(STEER) - CENTRE;
        return group.equals(RIGHT) ? Math.max(0, steer) * SIGNAL_PER_NOTCH : Math.max(0, -steer) * SIGNAL_PER_NOTCH;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int steer = Math.clamp(state.getValue(STEER) + (player.isShiftKeyDown() ? -1 : 1), 0, 4);
        if (!level.isClientSide && steer != state.getValue(STEER)) {
            level.setBlock(pos, state.setValue(STEER, steer), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4F, 0.9F);
            if (level.getBlockEntity(pos) instanceof Control control) {
                control.resync();
            }
            String way = steer == CENTRE ? "centre" : steer < CENTRE ? "left" : "right";
            player.displayClientMessage(Component.translatable("message.aerolarge.tiller." + way), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkedControlBlockEntity(pos, state);
    }
}
