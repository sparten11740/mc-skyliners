package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of the freighter's beach in her lounge that stands on the floor and reaches up past its own block, drawn by
 * its model: a potted palm, its fronds spreading over whoever passes under them; or a parasol, its striped canopy wide
 * over a lounger. What can be bumped into is only its foot and the trunk or pole up from it, so the fronds and the
 * canopy overhead are walked under. FACING turns it, so no two stand alike.
 */
public class TropicalPieceBlock extends HorizontalDirectionalBlock {
    /** A palm in its pot: the pot, and its trunk up to the top of its block. */
    public static final VoxelShape PALM = Shapes.or(Block.box(4.5, 0, 4.5, 11.5, 5.5, 11.5), Block.box(7, 5.5, 7, 9, 16, 9));
    /** A parasol: its weighted foot, and its pole up to the top of its block. */
    public static final VoxelShape PARASOL = Shapes.or(Block.box(5, 0, 5, 11, 1, 11), Block.box(7.5, 1, 7.5, 8.5, 16, 8.5));
    private final VoxelShape shape;
    private final Map<Direction, VoxelShape> shapes;

    public TropicalPieceBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
        this.shapes = TurnedShapes.of(shape);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new TropicalPieceBlock(properties, shape));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Turned as whoever puts it there faces. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }
}
