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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One engine's thrust lever on the flight deck's throttle quadrant. Using it pushes it a notch forward (more
 * thrust), using it while sneaking pulls it a notch back; its engine's thrusters follow through the relays in the
 * nacelle. FACING is the way the nose points, and the lever pushes toward it.
 */
public class ThrustLeverBlock extends BaseEntityBlock {
    private static final MapCodec<ThrustLeverBlock> CODEC = simpleCodec(ThrustLeverBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty THRUST = IntegerProperty.create("thrust", 0, 4);
    /** The redstone signal each notch sends the thrusters: idle, then quarters up to full. */
    private static final int[] POWER = {0, 4, 8, 12, 15};
    private static final VoxelShape SHAPE = Block.box(3, 0, 2, 13, 10, 14);

    public ThrustLeverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(THRUST, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, THRUST);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int thrust = Math.clamp(state.getValue(THRUST) + (player.isShiftKeyDown() ? -1 : 1), 0, 4);
        if (thrust == state.getValue(THRUST)) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(THRUST, thrust), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 0.6F + thrust * 0.1F);
            if (level.getBlockEntity(pos) instanceof ThrustLeverBlockEntity lever) {
                lever.drive(power(state.setValue(THRUST, thrust)));
                player.displayClientMessage(Component.translatable("message.aerolarge.thrust", lever.engine(), thrust * 25), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The signal a lever sends its engine's thrusters in this state. */
    static int power(BlockState state) {
        return POWER[state.getValue(THRUST)];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThrustLeverBlockEntity(pos, state);
    }
}
