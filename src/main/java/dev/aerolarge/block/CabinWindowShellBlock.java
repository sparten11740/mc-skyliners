package dev.aerolarge.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cabin window cut to the curve of the hull like the skin around it, where the fuselage's side curves: the same
 * window as {@link CabinWindowBlock}, with its shade and its joins into tall windows, in a block the shape of the
 * skin there. Light comes through it unless its shade is down, and neighbouring panes join without a seam. Only the
 * blueprints place it; broken, it gives back a cabin window.
 */
public class CabinWindowShellBlock extends ShellBlock {
    /**
     * Only the shapes a window in the cabin's side can take ({@link ShellGeometry#WINDOW_COUNT}). The window has
     * eight states of its own for each shape, and the game keeps a table of every state's neighbours as large as all
     * of its property values together, which every further shape would make that much larger again.
     */
    public static final Cuts PANE_CUTS = Cuts.of(ShellGeometry.WINDOW_COUNT);

    public CabinWindowShellBlock(Properties properties, Supplier<? extends ItemLike> part) {
        super(properties, part);
        registerDefaultState(defaultBlockState()
            .setValue(CabinWindowBlock.SHADED, false).setValue(CabinWindowBlock.UP, false).setValue(CabinWindowBlock.DOWN, false));
    }

    @Override
    protected Cuts cuts() {
        return PANE_CUTS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CabinWindowBlock.SHADED, CabinWindowBlock.UP, CabinWindowBlock.DOWN);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return CabinWindowBlock.join(state, direction, neighbor);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            CabinWindowBlock.pullShade(level, pos, state);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(CabinWindowBlock.SHADED);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(CabinWindowBlock.SHADED) ? level.getMaxLightLevel() : super.getLightBlock(state, level, pos);
    }
}
