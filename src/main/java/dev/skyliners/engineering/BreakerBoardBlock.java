package dev.skyliners.engineering;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The freighter's breaker board in engineering (see {@link BreakerBoardBlockEntity}): a steel panel on the wall with
 * six big toggle switches in two rows, one for each of her rooms, each under a band of its room's LED colour, its lamp
 * lit while the room's strips are. Flip a switch and that room's lights go out with a heavy clack, a spark and a power
 * hum falling away; flip it back and they come on again with the hum rising. The client's
 * {@code BreakerBoardRenderer} draws the switches. FACING is the way it faces out from the wall.
 */
public class BreakerBoardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<BreakerBoardBlock> CODEC = simpleCodec(BreakerBoardBlock::new);
    /** How many switches it has across and down, one for each of her rooms. */
    public static final int ACROSS = 3;
    public static final int DOWN = 2;
    /**
     * Where the switches lie on its face, as shares of it: from so far in from its left and right, and so far up from
     * its foot to so far.
     */
    public static final double SIDE_IN = 0.1;
    public static final double FOOT = 0.12;
    public static final double TOP = 0.88;
    /** How deep its panel stands out from the wall, in pixels. */
    public static final double DEPTH = 2;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, Block.box(0, 0, 16 - DEPTH, 16, 16, 16));
        SHAPES.put(Direction.SOUTH, Block.box(0, 0, 0, 16, 16, DEPTH));
        SHAPES.put(Direction.WEST, Block.box(16 - DEPTH, 0, 0, 16, 16, 16));
        SHAPES.put(Direction.EAST, Block.box(0, 0, 0, DEPTH, 16, 16));
    }

    public BreakerBoardBlock(Properties properties) {
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

    /** Flips the switch that was pressed, if one was. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int pressed = switchAt(state.getValue(FACING), hit.getLocation().subtract(Vec3.atLowerCornerOf(pos)));
        if (pressed < 0) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof BreakerBoardBlockEntity board) {
            board.flip(server, pressed);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The switch at a point on its face, from its block's corner, counted along the top row from the viewer's left and
     * then the bottom row; -1 for none.
     */
    public static int switchAt(Direction facing, Vec3 hit) {
        Direction right = facing.getCounterClockWise();
        double along = right.getStepX() != 0 ? hit.x : hit.z;
        double across = right.getStepX() + right.getStepZ() > 0 ? along : 1 - along;
        double width = (1 - 2 * SIDE_IN) / ACROSS;
        double height = (TOP - FOOT) / DOWN;
        int column = (int) Math.floor((across - SIDE_IN) / width);
        int row = (int) Math.floor((TOP - hit.y) / height);
        boolean on = column >= 0 && column < ACROSS && row >= 0 && row < DOWN;
        return on ? row * ACROSS + column : -1;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BreakerBoardBlockEntity(pos, state);
    }

    /** Sparks fly from the switch flipped, on the client (see {@link BreakerBoardBlockEntity#triggerEvent}). */
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        BlockEntity board = level.getBlockEntity(pos);
        return board != null && board.triggerEvent(id, param);
    }
}
