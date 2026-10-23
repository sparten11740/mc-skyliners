package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.function.Function;
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
 * Windows stacked one on another join into one tall window (UP and DOWN say which way it goes on), with one shade;
 * one standing a block in or out from the one under it joins it too, so no frame runs across the middle of the window
 * there, though their shades stay apart (see {@link #goesOn}).
 * Where the hull curves, the same window is cut to its curve ({@link CabinWindowShellBlock}); the two join alike.
 */
public class CabinWindowBlock extends TransparentBlock {
    private static final MapCodec<CabinWindowBlock> CODEC = simpleCodec(CabinWindowBlock::new);
    public static final BooleanProperty SHADED = BooleanProperty.create("shaded");
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    /** The most panes one shade is pulled over at once: a whole wall of windows stacked up by hand. */
    private static final int MOST_PANES = 64;

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
        return defaultBlockState().setValue(UP, goesOn(level, pos, Direction.UP)).setValue(DOWN, goesOn(level, pos, Direction.DOWN));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return join(state, direction, level, pos);
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

    /** A pane's join toward a neighbour above or below that has just changed: there while the window goes on that way. */
    static BlockState join(BlockState state, Direction direction, BlockGetter level, BlockPos pos) {
        if (direction.getAxis().isVertical()) {
            return state.setValue(direction == Direction.UP ? UP : DOWN, goesOn(level, pos, direction));
        }
        return state;
    }

    private static boolean goesOn(BlockGetter level, BlockPos pos, Direction way) {
        return goesOn(level::getBlockState, pos, way);
    }

    /**
     * Whether the window goes on from the pane at the position into the row above or below, among the given blocks: a
     * pane right there, or a block to the side of there, where the hull's curve steps the window in or out a block
     * between its rows (toward the tail, where the fuselage narrows), so no frame is drawn across the middle of it.
     */
    public static boolean goesOn(Function<BlockPos, BlockState> blocks, BlockPos pos, Direction way) {
        BlockPos row = pos.relative(way);
        if (isPane(blocks.apply(row))) {
            return true;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (isPane(blocks.apply(row.relative(side)))) {
                return true;
            }
        }
        return false;
    }

    /** Pulls the shade of the whole window the pane is part of, every pane it is joined into, up or down. */
    static void pullShade(Level level, BlockPos pos, BlockState state) {
        boolean shaded = !state.getValue(SHADED);
        for (BlockPos at : JoinedBlocks.from(level, pos, MOST_PANES, CabinWindowBlock::carriesOn)) {
            level.setBlock(at, level.getBlockState(at).setValue(SHADED, shaded), Block.UPDATE_ALL);
        }
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6F, shaded ? 0.9F : 1.2F);
    }

    /** Whether a pane carries on into its neighbour: another pane above or below it, the way it is joined. */
    private static boolean carriesOn(BlockState pane, Direction way, BlockState neighbor) {
        return isPane(neighbor) && (way == Direction.UP && pane.getValue(UP) || way == Direction.DOWN && pane.getValue(DOWN));
    }
}
