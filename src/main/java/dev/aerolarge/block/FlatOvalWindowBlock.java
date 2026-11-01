package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The oval window lying flat in a floor or a roof, as the Oval Window sets it there by hand (see
 * {@link OvalWindowBlock#getStateForPlacement}): a skylight looking up out of a roof, or a window in a floor looking
 * down at what is under it, or either the other way up. FACING is the way it looks out, its glass bulging that way, up
 * or down, toward whoever set it; ALONG is the way its top row lies from its middle, away from them. Each piece is the
 * half of its block away from the way it looks out, so a window looking up out of a floor leaves a dip half a block
 * deep to step into over it, and one looking down out of a ceiling sits flush with it underneath. It looks like the
 * floor or roof it is set into, is only ever whole and gives back an Oval Window, as one set into a wall does (see
 * {@link OvalWindowPieceBlock}).
 */
public class FlatOvalWindowBlock extends OvalWindowPieceBlock {
    private static final MapCodec<FlatOvalWindowBlock> CODEC = simpleCodec(FlatOvalWindowBlock::new);
    /** The way the window looks out: up or down. */
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.UP, Direction.DOWN);
    /** The way its top row lies from its middle. */
    public static final DirectionProperty ALONG = DirectionProperty.create("along", Direction.Plane.HORIZONTAL);
    /** The pieces' shapes, the half of the block away from the way they look out. */
    private static final VoxelShape LOOKING_UP = Block.box(0, 0, 0, 16, 8, 16);
    private static final VoxelShape LOOKING_DOWN = Block.box(0, 8, 0, 16, 16, 16);

    public FlatOvalWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(ALONG, Direction.NORTH).setValue(COLUMN, 0)
            .setValue(ROW, 0).setValue(DIM, 0));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ALONG, COLUMN, ROW, DIM);
    }

    /**
     * A window lying flat set by hand: looking up toward whoever sets it from above, or down toward them from below,
     * its top away from them.
     */
    public BlockState lying(BlockPlaceContext context) {
        Player player = context.getPlayer();
        boolean above = player != null ? player.getEyeY() > context.getClickedPos().getY() + 0.5 : context.getNearestLookingVerticalDirection() == Direction.DOWN;
        return defaultBlockState().setValue(FACING, above ? Direction.UP : Direction.DOWN).setValue(ALONG, context.getHorizontalDirection());
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

    /** Picked in creative mode, it gives the Oval Window that sets it. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(AerolargeRegistry.OVAL_WINDOW_ITEM.get());
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
        return state.setValue(ALONG, mirror.mirror(state.getValue(ALONG))).setValue(COLUMN, COLUMNS - 1 - state.getValue(COLUMN));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING) == Direction.UP ? LOOKING_UP : LOOKING_DOWN;
    }
}
