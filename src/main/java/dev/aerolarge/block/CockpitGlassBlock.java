package dev.aerolarge.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
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
     * A pane's sides against the next pane are left out, as glass's are; its model draws the rest so that a look
     * through the glass, however thick it runs, is tinted once (see the windscreen's single-pane model in
     * {@code ShellModel}).
     */
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
        return true;
    }
}
