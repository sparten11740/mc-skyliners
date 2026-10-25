package dev.aerolarge.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The windscreen: tinted glass cut to the curve of the nose like the skin around it, so the flight deck looks out
 * through the hull's own facets. Light passes through it, and neighbouring panes join without a seam. The moon
 * pool's clear belly glass is the same block with a clearer face.
 */
public class CockpitGlassBlock extends ShellBlock {
    public CockpitGlassBlock(Properties properties, Supplier<? extends ItemLike> part) {
        super(properties, part);
    }

    /**
     * A pane's side is left out against the next pane only where that pane covers it, so the panes join without a
     * seam; where the next pane is cut away from part of it, as where the windscreen's facets turn, it shows, or the
     * hull would have a hole there.
     */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.is(this) && covers(adjacent, state, direction) || super.skipRendering(state, adjacent, direction);
    }

    /** Whether a pane covers the whole of the side of the next one that faces it, the way it faces from that one. */
    private static boolean covers(BlockState pane, BlockState next, Direction direction) {
        VoxelShape side = ShellGeometry.shape(shape(next)).getFaceShape(direction);
        VoxelShape facing = ShellGeometry.shape(shape(pane)).getFaceShape(direction.getOpposite());
        return !Shapes.joinIsNotEmpty(side, facing, BooleanOp.ONLY_FIRST);
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
        return true;
    }
}
