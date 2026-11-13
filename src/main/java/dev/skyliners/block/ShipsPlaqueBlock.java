package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ship's brass plaque, on the wall of a zeppelin's control car: blank until the zeppelin is christened, then
 * engraved with the name she was given and her crest, the ship's colours flown when the bottle broke (see the
 * {@code Christening}), which it keeps for her, so her flanks are painted with them (see the client's
 * {@code ChristeningPaint}). FACING is the way its face looks.
 */
public class ShipsPlaqueBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<ShipsPlaqueBlock> CODEC = simpleCodec(ShipsPlaqueBlock::new);
    /** How thick the plaque is, in sixteenths of a block, standing at the back of its block. */
    public static final int THICKNESS = 1;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(1, 3, 16 - THICKNESS, 15, 13, 16),
        Direction.SOUTH, Block.box(1, 3, 0, 15, 13, THICKNESS),
        Direction.WEST, Block.box(16 - THICKNESS, 3, 1, 16, 13, 15),
        Direction.EAST, Block.box(0, 3, 1, THICKNESS, 13, 15)
    );

    public ShipsPlaqueBlock(Properties properties) {
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

    /** Put up by hand only on a wall, its face looking out from it: set down on a floor, it stood in the air. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction wall = Mounting.support(context, List.of(Mount.WALL));
        return wall == null ? null : defaultBlockState().setValue(FACING, wall.getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShipsPlaqueBlockEntity(pos, state);
    }
}
