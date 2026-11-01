package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.skywriting.SkyShape;
import java.util.List;
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
 * one has her autopilot fly that shape across the sky trailing smoke, its button marked as picked while she does;
 * pressing it again gives it up. It is a touch panel (see {@link TouchPanel}), its buttons its tiles, lit over their
 * paint by the client's {@code SkywritingBoardRenderer}. FACING is the way its face looks, its back to the wall.
 */
public class SkywritingBoardBlock extends HorizontalDirectionalBlock implements EntityBlock, TouchPanelBlock {
    private static final MapCodec<SkywritingBoardBlock> CODEC = simpleCodec(SkywritingBoardBlock::new);
    /** How thick the board is, in sixteenths of a block. */
    public static final int THICKNESS = 2;
    /** The buttons as they are laid out on its face, in two rows of two, top row first, each from the left as seen. */
    public static final List<SkyShape> BUTTONS = List.of(SkyShape.CIRCLE, SkyShape.HEART, SkyShape.EIGHT, SkyShape.WAVE);
    /**
     * Where the buttons are painted on its face, in its texture's thirty-two pixels to a block: their columns' left and
     * right edges, and their rows' bottom and top edges, the top row first.
     */
    private static final double[][] BUTTON_COLUMNS = {{2, 15}, {17, 30}};
    private static final double[][] BUTTON_ROWS = {{17, 30}, {2, 15}};
    private static final double PIXELS = 32;
    /** The buttons as the tiles of a touch panel, in {@link #BUTTONS}' order (see {@link TouchPanel}). */
    public static final TouchPanel.Layout BUTTON_TILES = TouchPanel.Layout.crossings(BUTTON_COLUMNS, BUTTON_ROWS, PIXELS);
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
        int button = tileAt(level, state, pos, hit, player.getLookAngle());
        if (button < 0 || !SkylinersConfig.SKYWRITING.get() || !(level.getBlockEntity(pos) instanceof SkywritingBoardBlockEntity board)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            board.press(server, BUTTONS.get(button));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The button a hit on the board's face lands on, by its place in {@link #BUTTONS}; -1 off them. */
    @Override
    public int tileAt(BlockGetter level, BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look) {
        return BUTTON_TILES.at(TouchPanel.touched(state.getValue(FACING), pos, hit));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SkywritingBoardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == SkylinersRegistry.SKYWRITING_BOARD_ENTITY.get()
            ? (tickLevel, pos, tickState, board) -> ((SkywritingBoardBlockEntity) board).serverTick((ServerLevel) tickLevel)
            : null;
    }
}
