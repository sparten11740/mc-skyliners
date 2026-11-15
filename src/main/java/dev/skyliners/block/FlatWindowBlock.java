package dev.skyliners.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A round or oval window lying flat in a floor or a roof, as its item sets it there by hand (see {@link
 * StandingWindowBlock#getStateForPlacement}): a skylight looking up out of a roof, or a window in a floor looking down
 * at what is under it, or either the other way up. FACING is the way it looks out, its glass bulging that way, up or
 * down, away from whoever set it, its inside toward them; ALONG is the way its top row lies from its middle, away from
 * them. Each piece fills its
 * whole block, flush with the floor or roof on both sides, its glass standing out of it in its collar; in a floor it is
 * solid over the glass too, so it is walked over, dome and all. It looks like the floor or roof it is set into, is only
 * ever whole and gives back the window standing in a wall it pairs with, as one set into a wall does (see {@link
 * OvalWindowPieceBlock}).
 */
public abstract class FlatWindowBlock extends OvalWindowPieceBlock {
    /** The way the window looks out: up or down. */
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.UP, Direction.DOWN);
    /** The way its top row lies from its middle. */
    public static final DirectionProperty ALONG = DirectionProperty.create("along", Direction.Plane.HORIZONTAL);

    protected FlatWindowBlock(Properties properties, Size size) {
        super(properties, size);
    }

    /** The same window standing in a wall, whose item lays this one. */
    protected abstract StandingWindowBlock standing();

    /**
     * A window lying flat set by hand: looking down away from whoever sets it from above, or up away from them from
     * below, its inside toward them, its top away from them.
     */
    public BlockState lying(BlockPlaceContext context) {
        Player player = context.getPlayer();
        boolean above = player != null ? player.getEyeY() > context.getClickedPos().getY() + 0.5 : context.getNearestLookingVerticalDirection() == Direction.DOWN;
        return defaultBlockState().setValue(FACING, above ? Direction.DOWN : Direction.UP).setValue(ALONG, context.getHorizontalDirection());
    }

    @Override
    public Direction out(BlockState piece) {
        return piece.getValue(FACING);
    }

    @Override
    public Direction up(BlockState piece) {
        return piece.getValue(ALONG);
    }

    @Override
    public boolean setByHand(BlockState piece) {
        return true;
    }

    /**
     * Picked in creative mode, it gives the window's item that sets it: the item the standing window is registered
     * under as its block's item, which lays this one too.
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(standing());
    }

    /** A window lying flat turns its top with the ship it is set in, as she is built facing another way. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(ALONG, rotation.rotate(state.getValue(ALONG)));
    }

    /** Mirrored, its top lies the mirrored way, and its columns from the left run the other way round. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return mirrored(state.setValue(ALONG, mirror.mirror(state.getValue(ALONG))));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    /**
     * Solid over its dome too in a floor, to be walked over, but only through its block in a ceiling, so its dome,
     * hanging into a room two blocks high, keeps nobody from walking under it.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING) == Direction.UP ? solidWithDome(state) : Shapes.block();
    }
}
