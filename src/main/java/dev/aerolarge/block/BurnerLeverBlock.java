package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The burner lever in a zeppelin's control car: a lever on a stand with a flame painted beside it, from a little blue
 * flame at the bottom to a big red one at the top, so how hard the burners burn is seen at a glance. Pushed up (used)
 * the burners burn harder and the zeppelin rises; pulled down (used while sneaking) they burn lower and it sinks; in
 * the middle they float it where it is. HEAT is the notch it stands at, {@link #FLOAT} in the middle. The autopilot
 * reads it while it is switched off (see {@code ZeppelinPilot}), and moves it itself while it flies. FACING is the way
 * whoever works it looks.
 */
public class BurnerLeverBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<BurnerLeverBlock> CODEC = simpleCodec(BurnerLeverBlock::new);
    /** The lever's notches: two either side of the middle, where the burners float the zeppelin. */
    public static final int FLOAT = 2;
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 2 * FLOAT);
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 13, 12);

    public BurnerLeverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HEAT, FLOAT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HEAT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int heat = Math.clamp(state.getValue(HEAT) + (player.isShiftKeyDown() ? -1 : 1), 0, 2 * FLOAT);
        if (heat == state.getValue(HEAT)) {
            EndStop.thud(level, pos);
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(HEAT, heat), Block.UPDATE_ALL);
            // The burners roar up as it is pushed, and die down with a soft hiss as it is pulled.
            level.playSound(null, pos, heat > state.getValue(HEAT) ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS,
                0.4F, 0.8F + 0.1F * heat);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** How far the lever is from floating the zeppelin, from -1 (burners low) to 1 (burners high). */
    public static double heat(BlockState state) {
        return (state.getValue(HEAT) - FLOAT) / (double) FLOAT;
    }
}
