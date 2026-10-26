package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Airframe skin with its outer edge cut away at 45 degrees, laid on the outer steps of the hull, nacelles and wings
 * so their stepped curves read as smooth facets. FACING is the side the cut faces and HALF whether it is the top or
 * the bottom edge; the cut runs along the block, across FACING. The two inner faces are whole, so from inside the
 * cabin it is an ordinary wall. Put down by hand it sits as stairs do (see {@link #getStateForPlacement}).
 */
public class FairingBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<FairingBlock> CODEC = simpleCodec(FairingBlock::new);
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    /** Steps under the slope, for collision: four, each a quarter block high, stepping in toward the cut. */
    private static final Map<Direction, VoxelShape> TOP = shapes(false);
    private static final Map<Direction, VoxelShape> BOTTOM = shapes(true);

    public FairingBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.TOP));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /**
     * Put down the way stairs are. Its cut faces whoever puts it down, as the airliner's face out of the skin toward
     * whoever stands beside it, so skinning a wing or a nacelle from outside cuts the edges facing out. It rests cut
     * along its top edge, put on top of a block or against the lower half of a block's side, and hangs cut along its
     * bottom edge, put against the underside of a block or the upper half of a block's side, for the underside of a
     * curve.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        boolean upperHalf = context.getClickLocation().y - context.getClickedPos().getY() > 0.5;
        boolean hangs = clicked == Direction.DOWN || clicked != Direction.UP && upperHalf;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(HALF, hangs ? Half.BOTTOM : Half.TOP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == Half.TOP ? TOP : BOTTOM).get(state.getValue(FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    private static Map<Direction, VoxelShape> shapes(boolean bottom) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (int k = 0; k < 4; k++) {
                // How far toward the cut this step reaches, and which quarter of the height it fills.
                double reach = 14 - 4 * k;
                double y0 = bottom ? 12 - 4 * k : 4 * k;
                shape = Shapes.or(shape, switch (facing) {
                    case EAST -> Block.box(0, y0, 0, reach, y0 + 4, 16);
                    case WEST -> Block.box(16 - reach, y0, 0, 16, y0 + 4, 16);
                    case SOUTH -> Block.box(0, y0, 0, 16, y0 + 4, reach);
                    default -> Block.box(0, y0, 16 - reach, 16, y0 + 4, 16);
                });
            }
            shapes.put(facing, shape.optimize());
        }
        return shapes;
    }
}
