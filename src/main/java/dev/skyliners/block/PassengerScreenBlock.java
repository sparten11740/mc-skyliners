package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A passenger screen: the in-flight entertainment display in a seat back or on a bulkhead, a thin screen fixed to
 * the back of its block. Once the airliner is assembled it shows those aboard how high, how fast and which way it
 * flies, and how far it has come since it took off; its renderer draws all of it live. Using the screen looks through
 * the airliner's outside cameras, one after the other, on the client of whoever used it (see
 * {@link PassengerScreenBlockEntity#clientUse}). FACING is the way the screen faces, its back to whatever it is fixed
 * to.
 */
public class PassengerScreenBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<PassengerScreenBlock> CODEC = simpleCodec(PassengerScreenBlock::new);
    /** The screen's size in pixels of its block, across and up (centred on its face) and how thick it is. */
    public static final int WIDTH = 14;
    public static final int HEIGHT = 10;
    public static final int DEPTH = 2;
    private static final int SIDE = (16 - WIDTH) / 2;
    private static final int BOTTOM = (16 - HEIGHT) / 2;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(SIDE, BOTTOM, 16 - DEPTH, 16 - SIDE, 16 - BOTTOM, 16),
        Direction.SOUTH, Block.box(SIDE, BOTTOM, 0, 16 - SIDE, 16 - BOTTOM, DEPTH),
        Direction.WEST, Block.box(16 - DEPTH, BOTTOM, SIDE, 16, 16 - BOTTOM, 16 - SIDE),
        Direction.EAST, Block.box(0, BOTTOM, SIDE, DEPTH, 16 - BOTTOM, 16 - SIDE)
    );

    public PassengerScreenBlock(Properties properties) {
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

    /** Against the side of a block it faces out from it; put anywhere else it faces whoever puts it there. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        return defaultBlockState().setValue(FACING, clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Touching the screen itself; nothing changes in the world, so the server only has the click to answer. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide && level.getBlockEntity(pos) instanceof PassengerScreenBlockEntity screen) {
            PassengerScreenBlockEntity.clientUse.accept(screen);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PassengerScreenBlockEntity(pos, state);
    }
}
