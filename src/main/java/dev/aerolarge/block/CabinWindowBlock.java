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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A cabin window with its shade: open, it lets the daylight in; pulled down, it shuts it out. The captain works every
 * shade at once from the flight deck (see {@link WindowShadesBlock}); anyone can open or close a window's own by hand.
 * Windows stacked one on another join into one tall window (UP and DOWN say which way it goes on), with one shade.
 * Where the hull curves, the same window is cut to its curve ({@link CabinWindowShellBlock}); the two join alike.
 */
public class CabinWindowBlock extends TransparentBlock {
    private static final MapCodec<CabinWindowBlock> CODEC = simpleCodec(CabinWindowBlock::new);
    public static final BooleanProperty SHADED = BooleanProperty.create("shaded");
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public CabinWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SHADED, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHADED, UP, DOWN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return defaultBlockState().setValue(UP, isPane(level.getBlockState(pos.above()))).setValue(DOWN, isPane(level.getBlockState(pos.below())));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return join(state, direction, neighbor);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            pullShade(level, pos, state);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(SHADED);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(SHADED) ? level.getMaxLightLevel() : super.getLightBlock(state, level, pos);
    }

    /** Whether the state is a pane of a cabin window, flat or cut to the hull's curve. */
    public static boolean isPane(BlockState state) {
        return state.getBlock() instanceof CabinWindowBlock || state.getBlock() instanceof CabinWindowShellBlock;
    }

    /** A pane's join toward a neighbour above or below that has just changed: there while that is a pane too. */
    static BlockState join(BlockState state, Direction direction, BlockState neighbor) {
        if (direction.getAxis().isVertical()) {
            return state.setValue(direction == Direction.UP ? UP : DOWN, isPane(neighbor));
        }
        return state;
    }

    /** Pulls the shade of the whole window the pane is part of, every pane it is joined into, up or down. */
    static void pullShade(Level level, BlockPos pos, BlockState state) {
        boolean shaded = !state.getValue(SHADED);
        BlockPos at = pos;
        while (isPane(level.getBlockState(at)) && level.getBlockState(at).getValue(DOWN)) {
            at = at.below();
        }
        for (BlockState pane = level.getBlockState(at); isPane(pane); pane = level.getBlockState(at)) {
            level.setBlock(at, pane.setValue(SHADED, shaded), Block.UPDATE_ALL);
            if (!pane.getValue(UP)) {
                break;
            }
            at = at.above();
        }
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6F, shaded ? 0.9F : 1.2F);
    }
}
