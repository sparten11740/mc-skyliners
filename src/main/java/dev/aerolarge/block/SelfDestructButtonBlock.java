package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The self-destruct button, on the flight deck's back wall: a big red button on a yellow-and-black striped plate under
 * a striped flip cover, so that it reads as the button not to press without a word. It takes three presses on purpose
 * to set off (see {@link SelfDestructButtonBlockEntity}): the first flips the cover up (OPEN), the second arms it, the
 * button lit and flashing red (ARMED) while the alarm wails through the airliner, and a third while it is armed blows
 * the airliner up. Sneak and use it with an empty hand to snap the cover shut and disarm it. Put up by hand against the
 * side of a block, it faces whoever put it there. FACING is the way whoever looks at it faces, toward the wall.
 */
public class SelfDestructButtonBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<SelfDestructButtonBlock> CODEC = simpleCodec(SelfDestructButtonBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    /** How far the plate, the button and the cover over it stand out from the wall, in pixels. */
    private static final int DEPTH = 8;
    /** How far in from the plate's edges to the edges of the block, in pixels: the plate is twelve pixels square. */
    private static final int MARGIN = 2;
    /** The plate on the wall, by the way its viewer faces: against the side of the block they look at. */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(MARGIN, MARGIN, 0, 16 - MARGIN, 16 - MARGIN, DEPTH),
        Direction.SOUTH, Block.box(MARGIN, MARGIN, 16 - DEPTH, 16 - MARGIN, 16 - MARGIN, 16),
        Direction.WEST, Block.box(0, MARGIN, MARGIN, DEPTH, 16 - MARGIN, 16 - MARGIN),
        Direction.EAST, Block.box(16 - DEPTH, MARGIN, MARGIN, 16, 16 - MARGIN, 16 - MARGIN)
    );

    public SelfDestructButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(ARMED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, ARMED);
    }

    /** Only against the side of a block, facing whoever puts it there, its cover shut. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        return clicked.getAxis().isHorizontal() ? defaultBlockState().setValue(FACING, clicked.getOpposite()) : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Pressed, or sneaked at, on the server (see {@link SelfDestructButtonBlockEntity#press}). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof SelfDestructButtonBlockEntity button) {
            button.press(serverLevel, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Taken away while armed, the alarm stops for everyone aboard. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SelfDestructButtonBlockEntity button) {
            button.taken();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SelfDestructButtonBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != AerolargeRegistry.SELF_DESTRUCT_BUTTON_ENTITY.get() ? null : SelfDestructButtonBlock::tick;
    }

    private static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
        if (blockEntity instanceof SelfDestructButtonBlockEntity button) {
            button.tick();
        }
    }
}
