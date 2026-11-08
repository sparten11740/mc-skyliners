package dev.skyliners.bowling;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import dev.skyliners.block.Mounting;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A bowling lane's boards, for the freighter's bowling lane (see {@code airframe.FreighterBowling}): polished maple
 * strips laid over a floor as thin as a carpet, so they go down on any deck without cutting into it, the boards running
 * along the lane (FACING). A ball rolls up onto them from the floor round them (see {@link BowlingBall}). A lane is a
 * floor, so the boards go down only on one.
 */
public class BowlingLaneBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<BowlingLaneBlock> CODEC = simpleCodec(BowlingLaneBlock::new);
    /** The boards, a pixel thick over the floor under them. */
    private static final VoxelShape BOARDS = Block.box(0, 0, 0, 16, 1, 16);
    /** The mounts its boards take: a floor alone. */
    private static final List<Mount> MOUNTS = List.of(Mount.FLOOR);

    public BowlingLaneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Laid on a floor, its boards running the way the player looks; nowhere that no floor holds it. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (Mounting.support(context, MOUNTS) == null) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BOARDS;
    }

    /** A bowling pin set down on the boards takes their place, standing on a square of them, as a lane's rack does. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return context.getItemInHand().is(SkylinersRegistry.BOWLING_PIN_ITEM.get());
    }
}
