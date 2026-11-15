package dev.skyliners.block;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A round or oval window standing in a wall (see {@link OvalWindowPieceBlock}), looking out the way FACING says, its
 * top up: the rocket's big oval window in her skin, or any window of its kind set into a wall by hand, from its item or
 * picked from one in creative mode, looking out away from whoever sets it, its inside toward them, as they stand in
 * the room it looks out of. The same item lays it in a
 * floor or a roof, as the kind of window lying flat it pairs with (see {@link FlatWindowBlock}). Set by hand, each
 * piece fills its whole block, flush with the wall on both sides, its glass standing out of the wall's face in its
 * collar as the rocket's stands out of her skin; in her skin, each piece is the inner half of its block (see {@link
 * OvalWindowBlock}). It turns with the ship it is set in as she is built facing any way.
 */
public abstract class StandingWindowBlock extends OvalWindowPieceBlock {
    /** The way the window looks out. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** A piece in the rocket's skin, the inner half of the block, by the way it looks out. */
    private static final VoxelShape LOOKING_NORTH = Block.box(0, 0, 8, 16, 16, 16);
    private static final VoxelShape LOOKING_SOUTH = Block.box(0, 0, 0, 16, 16, 8);
    private static final VoxelShape LOOKING_EAST = Block.box(0, 0, 0, 8, 16, 16);
    private static final VoxelShape LOOKING_WEST = Block.box(8, 0, 0, 16, 16, 16);

    protected StandingWindowBlock(Properties properties, Size size) {
        super(properties, size);
    }

    /** The same window lying flat, which the same item lays in a floor or a roof. */
    protected abstract FlatWindowBlock lyingFlat();

    /** A piece as it is set by hand, from one as it stands in no particular wall. */
    protected abstract BlockState handSet(BlockState piece);

    /** A piece of a window looking out one way, by its column from the left as seen from outside and its row. */
    public BlockState piece(Direction facing, int column, int row) {
        return at(defaultBlockState().setValue(FACING, facing), column, row);
    }

    @Override
    public Direction out(BlockState piece) {
        return piece.getValue(FACING);
    }

    @Override
    public Direction up(BlockState piece) {
        return Direction.UP;
    }

    /**
     * Set by hand: the piece for the spot that fits the window best into an opening round it, standing in a wall
     * looking out away from whoever sets it, its inside toward them, or lying flat in a floor or a roof (see {@link
     * FlatWindowBlock#lying}), standing first unless they look up or down; null where the window has no room either
     * way.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState standing = handSet(defaultBlockState().setValue(FACING, context.getHorizontalDirection()));
        BlockState flat = lyingFlat().lying(context);
        boolean steep = context.getNearestLookingDirection().getAxis().isVertical();
        return bestFit(context, steep ? List.of(flat, standing) : List.of(standing, flat));
    }

    /** A window turns with the ship it is set in, as she is built facing another way. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrored, a window looks out the mirrored way, and its columns from the left run the other way round. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return mirrored(state.setValue(FACING, mirror.mirror(state.getValue(FACING))));
    }

    /** One in the rocket's skin the inner half of the block; one set by hand the whole block. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (setByHand(state)) {
            return Shapes.block();
        }
        return switch (state.getValue(FACING)) {
            case SOUTH -> LOOKING_SOUTH;
            case EAST -> LOOKING_EAST;
            case WEST -> LOOKING_WEST;
            default -> LOOKING_NORTH;
        };
    }

    /** One set by hand is solid over the glass standing out of it too. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return setByHand(state) ? solidWithDome(state) : getShape(state, level, pos, context);
    }
}
