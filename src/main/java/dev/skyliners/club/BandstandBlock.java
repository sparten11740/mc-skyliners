package dev.skyliners.club;

import com.mojang.serialization.MapCodec;
import dev.skyliners.block.TurnedShapes;
import java.util.Map;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The bandstand in the freighter's Spacers' Club (see {@link BandstandBlockEntity}): a low wooden stage, its block's
 * own model, on which a jazz trio of robots of the deck robots' family, drawn by the client's
 * {@code BandstandRenderer}, play "Long Haul Swing" on upright bass, keyboard and drums, swaying to its beat, for
 * anyone who comes near (see the client's {@code ClubBand}). FACING is the way the band faces, out over the club.
 */
public class BandstandBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<BandstandBlock> CODEC = simpleCodec(BandstandBlock::new);
    /** How brightly its footlights light the band and the room round it, as a block's light level. */
    public static final int FOOTLIGHTS = 9;
    /**
     * Its stage, to stand on, and the band on it, to bump into, facing north: a slab as wide as the stage is drawn,
     * three blocks across, its own block's and one either side, and a player's body over the middle of each third,
     * as far apart as they are drawn (see the client's {@code BandstandRenderer}).
     */
    private static final VoxelShape NORTH = Shapes.or(Block.box(-16, 0, 0, 32, 3, 16), Block.box(4, 3, 4, 12, 14, 12),
        Block.box(-11.2, 3, 4, -3.2, 14, 12), Block.box(19.2, 3, 4, 27.2, 14, 12));
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(NORTH);

    public BandstandBlock(Properties properties) {
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

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BandstandBlockEntity(pos, state);
    }
}
