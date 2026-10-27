package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The picture board on a zeppelin's upper deck, choosing what the glowing screens down her flanks show (see the
 * client's {@code FlankScreenRenderer}): over its buttons a little copy of the screens, as they cannot be seen from
 * inside; and its six buttons, each painted with what it shows. Posters, one travel poster or advert after another;
 * Colours, the ship's colours flown from her flagstaff; Map, a filled map handed to the board, so a player's own map
 * art is shown, handed back at a second press; Faces, the faces of everyone aboard; Flight, her height and speed in
 * huge figures; and Off. FACING is the way its face looks.
 */
public class PictureBoardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<PictureBoardBlock> CODEC = simpleCodec(PictureBoardBlock::new);
    /** How thick the board is, in sixteenths of a block, standing at the back of its block. */
    public static final int THICKNESS = 3;
    /** The buttons, in their two rows under the little screen, top row first, each row from the left as it is seen. */
    public static final PictureBoardBlockEntity.Picture[][] BUTTONS = {
        {PictureBoardBlockEntity.Picture.POSTERS, PictureBoardBlockEntity.Picture.COLOURS, PictureBoardBlockEntity.Picture.MAP},
        {PictureBoardBlockEntity.Picture.FACES, PictureBoardBlockEntity.Picture.FLIGHT, PictureBoardBlockEntity.Picture.OFF}
    };
    /**
     * How high up its face the buttons reach, and how far up from its bottom they start, shares of it: the little
     * screen is over them, a frame round them all.
     */
    public static final double BUTTONS_TOP = 9 / 16.0;
    public static final double BUTTONS_BOTTOM = 1 / 16.0;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(0, 0, 16 - THICKNESS, 16, 16, 16),
        Direction.SOUTH, Block.box(0, 0, 0, 16, 16, THICKNESS),
        Direction.WEST, Block.box(16 - THICKNESS, 0, 0, 16, 16, 16),
        Direction.EAST, Block.box(0, 0, 0, THICKNESS, 16, 16)
    );

    public PictureBoardBlock(Properties properties) {
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

    /** Put down by hand, its face looks at whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** A filled map handed to the board is hung in it and shown; the one it held before, handed back. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.FILLED_MAP) || hit.getDirection() != state.getValue(FACING) || !(level.getBlockEntity(pos) instanceof PictureBoardBlockEntity board)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            ItemStack before = board.hang(stack);
            stack.consume(1, player);
            give(player, before);
            click(level, pos);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        PictureBoardBlockEntity.Picture button = hit.getDirection() == facing ? button(facing, hit.getLocation().subtract(Vec3.atCenterOf(pos))) : null;
        if (button == null || !(level.getBlockEntity(pos) instanceof PictureBoardBlockEntity board)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            press(level, pos, board, button, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A button pressed: what it shows on the screens, unless there is nothing for it to show yet. */
    private static void press(Level level, BlockPos pos, PictureBoardBlockEntity board, PictureBoardBlockEntity.Picture button, Player player) {
        if (button == PictureBoardBlockEntity.Picture.MAP && board.map().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.aerolarge.picture_board.no_map"), true);
            return;
        }
        if (button == PictureBoardBlockEntity.Picture.COLOURS && !colours(level, pos)) {
            player.displayClientMessage(Component.translatable("message.aerolarge.picture_board.no_colours"), true);
            return;
        }
        if (button == PictureBoardBlockEntity.Picture.MAP && board.picture() == PictureBoardBlockEntity.Picture.MAP) {
            give(player, board.unhang());
        } else {
            board.show(button);
        }
        click(level, pos);
    }

    /** Whether the ship's colours fly from a flagstaff on the zeppelin a board is in. */
    private static boolean colours(Level level, BlockPos pos) {
        AutopilotBlockEntity zeppelin = AutopilotBlockEntity.zeppelinFraming(level, pos);
        BoundingBox frame = zeppelin == null ? null : zeppelin.frame();
        if (frame == null) {
            return false;
        }
        for (FlagHalyardBlockEntity halyard : FlagHalyardBlockEntity.loaded(level)) {
            if (frame.isInside(halyard.getBlockPos()) && halyard.flying()) {
                return true;
            }
        }
        return false;
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void click(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    /**
     * The button at a place on the board's face, from the middle of its block; null on the little screen or between
     * buttons. Seen from the front, the viewer's right is the board's counterclockwise side.
     */
    @Nullable
    public static PictureBoardBlockEntity.Picture button(Direction facing, Vec3 touch) {
        Direction right = facing.getCounterClockWise();
        double across = touch.x * right.getStepX() + touch.z * right.getStepZ() + 0.5;
        double up = touch.y + 0.5;
        int column = (int) Math.floor(across * BUTTONS[0].length);
        int row = (int) Math.floor((BUTTONS_TOP - up) / ((BUTTONS_TOP - BUTTONS_BOTTOM) / BUTTONS.length));
        return up < BUTTONS_TOP && column >= 0 && column < BUTTONS[0].length && row >= 0 && row < BUTTONS.length ? BUTTONS[row][column] : null;
    }

    /** Broken, it drops the map it held. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PictureBoardBlockEntity board && !board.map().isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, board.unhang());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PictureBoardBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == AerolargeRegistry.PICTURE_BOARD_ENTITY.get()
            ? (tickLevel, pos, tickState, board) -> ((PictureBoardBlockEntity) board).serverTick()
            : null;
    }
}
