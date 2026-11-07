package dev.skyliners.floorwindow;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The round floor window's panel (see {@link FloorWindowBlock}): a steel post with a sloping face and on it a big round
 * button painted with the window and an eye looking down through it. Pressed, or given a redstone pulse, as from a
 * button on it, it opens the nearest floor window within reach, or shuts it if it is open, with a click and the iris's
 * whirr; its lamp round the button shows green while the window it last worked is open and amber while it is shut.
 * With no window in reach it only gives a low thud. It works any window wherever both stand: on a ship, both are in
 * her plot. FACING is the way its face looks; OPEN its lamp; POWERED whether it has a redstone signal.
 */
public class FloorWindowPanelBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<FloorWindowPanelBlock> CODEC = simpleCodec(FloorWindowPanelBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** How far round the panel a window is looked for: across, below and above it, in blocks. */
    private static final int REACH = 12;
    private static final int REACH_DOWN = 4;
    private static final int REACH_UP = 2;
    /** How loud its click is, and its thud where no window is in reach, and the thud's pitch. */
    private static final float CLICK_VOLUME = 0.5F;
    private static final float THUD_VOLUME = 0.8F;
    private static final float THUD_PITCH = 0.5F;
    /** A post with its sloping head. */
    private static final VoxelShape SHAPE = Block.box(3.5, 0, 3.5, 12.5, 14, 12.5);

    public FloorWindowPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Put down facing whoever puts it there, its lamp showing whether the window nearest it is open. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockPos middle = nearestWindow(level, pos);
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(OPEN, middle != null && FloorWindowBlock.isOpen(level, middle)).setValue(POWERED, level.hasNeighborSignal(pos));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && !player.isSpectator()) {
            press(server, pos, state);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A redstone pulse presses it as a hand does, once as its signal comes on. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from, boolean moved) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean powered = server.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        BlockState now = state.setValue(POWERED, powered);
        server.setBlock(pos, now, Block.UPDATE_CLIENTS);
        if (powered) {
            press(server, pos, now);
        }
    }

    /** Opens the nearest window in reach, or shuts it if open, its lamp showing which; a thud for none. */
    private static void press(ServerLevel level, BlockPos pos, BlockState state) {
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, CLICK_VOLUME, 1.0F);
        BlockPos middle = nearestWindow(level, pos);
        if (middle == null) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, THUD_VOLUME, THUD_PITCH);
            return;
        }
        boolean open = !FloorWindowBlock.isOpen(level, middle);
        FloorWindowBlock.setOpen(level, middle, open);
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_CLIENTS);
    }

    /** The middle block of the window with a block nearest the panel, within its reach; null for none. */
    @Nullable
    private static BlockPos nearestWindow(Level level, BlockPos pos) {
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-REACH, -REACH_DOWN, -REACH), pos.offset(REACH, REACH_UP, REACH))) {
            double distance = at.distSqr(pos);
            if (distance < nearestDistance && level.getBlockEntity(at) instanceof FloorWindowBlockEntity window) {
                nearest = window.middle();
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
