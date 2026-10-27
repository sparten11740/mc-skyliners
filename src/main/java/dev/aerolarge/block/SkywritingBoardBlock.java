package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.skywriting.SkyShape;
import java.util.Map;
import javax.annotation.Nullable;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The skywriting board in a zeppelin's control car: a board on the wall with four picture buttons, a circle, a heart,
 * a figure of eight and a wave, each drawn in smoke-white on blue (see {@link SkywritingBoardBlockEntity}). Pressing
 * one has her autopilot fly that shape across the sky trailing smoke, its button lit while she does; pressing it again
 * gives it up. FACING is the way its face looks, its back to the wall.
 */
public class SkywritingBoardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<SkywritingBoardBlock> CODEC = simpleCodec(SkywritingBoardBlock::new);
    /** How thick the board is, in sixteenths of a block. */
    public static final int THICKNESS = 2;
    /** The buttons as they are laid out on its face, top row first, left to right as seen from the front. */
    public static final SkyShape[][] BUTTONS = {{SkyShape.CIRCLE, SkyShape.HEART}, {SkyShape.EIGHT, SkyShape.WAVE}};
    /** How far in from the face's edges the buttons are, as a share of the block. */
    public static final double MARGIN = 1 / 16.0;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(0, 0, 16 - THICKNESS, 16, 16, 16),
        Direction.SOUTH, Block.box(0, 0, 0, 16, 16, THICKNESS),
        Direction.WEST, Block.box(16 - THICKNESS, 0, 0, 16, 16, 16),
        Direction.EAST, Block.box(0, 0, 0, THICKNESS, 16, 16)
    );

    public SkywritingBoardBlock(Properties properties) {
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

    /** Put down by hand, it faces whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** A button pressed, on the server: the shape begun or given up. Nothing with the server's switch off. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        SkyShape button = hit.getDirection() == facing ? button(facing, hit.getLocation().subtract(Vec3.atCenterOf(pos))) : null;
        if (button == null || !AerolargeConfig.SKYWRITING.get() || !(level.getBlockEntity(pos) instanceof SkywritingBoardBlockEntity board)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            board.press(server, button);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The button at a place on the board's face, from the middle of its block; null at its edge. Seen from the front,
     * the viewer's right is the board's counterclockwise side.
     */
    @Nullable
    public static SkyShape button(Direction facing, Vec3 touch) {
        Direction right = facing.getCounterClockWise();
        double across = (touch.x * right.getStepX() + touch.z * right.getStepZ() + 0.5 - MARGIN) / (1 - 2 * MARGIN);
        double down = (0.5 - touch.y - MARGIN) / (1 - 2 * MARGIN);
        int column = (int) Math.floor(across * BUTTONS[0].length);
        int row = (int) Math.floor(down * BUTTONS.length);
        return column >= 0 && column < BUTTONS[0].length && row >= 0 && row < BUTTONS.length ? BUTTONS[row][column] : null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkywritingBoardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == AerolargeRegistry.SKYWRITING_BOARD_ENTITY.get()
            ? (tickLevel, pos, tickState, board) -> ((SkywritingBoardBlockEntity) board).serverTick((ServerLevel) tickLevel)
            : null;
    }
}
