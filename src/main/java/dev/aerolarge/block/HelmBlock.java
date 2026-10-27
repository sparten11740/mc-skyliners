package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ship's wheel in a zeppelin's control car, a big wooden wheel on a pedestal, as the Hindenburg had two: the rudder
 * wheel, facing forward, which turns the zeppelin, and the elevator wheel, facing to port, which tips its nose up or
 * down. Using it turns it a notch clockwise (to starboard, or nose down), using it while sneaking a notch the other
 * way; it stops at {@link #MOST} notches either way and turns back to the middle a notch at a time from there. TURN
 * is how far it is turned, {@link #MIDDLE} straight. The zeppelin's autopilot reads it while it is switched off and
 * flies as it says (see {@code ZeppelinPilot}), and turns it itself, for all to see, while it flies.
 *
 * <p>FACING is the way whoever steers with it looks: the wheel stands in front of them, its face toward them.
 */
public class HelmBlock extends BaseEntityBlock {
    private static final MapCodec<HelmBlock> CODEC = simpleCodec(HelmBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** How many notches the wheel turns either way from the middle. */
    public static final int MOST = 4;
    public static final int MIDDLE = MOST;
    public static final IntegerProperty TURN = IntegerProperty.create("turn", 0, 2 * MOST);
    /** The pedestal and the wheel on it, as solid as a post to walk against. */
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public HelmBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TURN, MIDDLE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TURN);
    }

    /** Put down by hand, it faces whoever puts it down, to steer from where they stand. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int turn = Math.clamp(state.getValue(TURN) + (player.isShiftKeyDown() ? -1 : 1), 0, 2 * MOST);
        if (turn == state.getValue(TURN)) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(TURN, turn), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 0.7F + 0.05F * turn);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** How far the wheel is turned, from -1 (all the way anticlockwise) to 1 (all the way clockwise). */
    public static double turn(BlockState state) {
        return (state.getValue(TURN) - MIDDLE) / (double) MOST;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HelmBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == AerolargeRegistry.HELM_ENTITY.get()
            ? (tickLevel, pos, tickState, helm) -> ((HelmBlockEntity) helm).clientTick()
            : null;
    }
}
