package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An evacuation slide's control, set into the cabin wall beside a passenger door: a red panel in a yellow frame showing
 * someone sliding down a slide, so it needs no reading, with a red handle on it, up while the slide is packed and
 * pulled down once it is out, a green lamp lit beside it. It faces into the cabin (FACING); only that face works it, so
 * from outside it is plain skin. Pulling the handle brings the slide out, the door opening first; pulling it again
 * sends it away (see {@link SlideControlBlockEntity}). Broken, it takes its slide away with it. Put up by hand next to a
 * plug door, it serves that door.
 */
public class SlideControlBlock extends BaseEntityBlock {
    private static final MapCodec<SlideControlBlock> CODEC = simpleCodec(SlideControlBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Whether the handle is pulled: the slide out or coming out. */
    public static final BooleanProperty DEPLOYED = BooleanProperty.create("deployed");

    public SlideControlBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DEPLOYED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DEPLOYED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Its panel faces whoever puts it up. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Put up by hand, it serves the plug door nearest it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SlideControlBlockEntity slide) {
            slide.findDoor();
        }
    }

    /** Only its panel, inside the cabin, works it; from outside it is the hull's skin. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SlideControlBlockEntity slide) {
            slide.toggle(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Taken apart on purpose, it takes its slide away at once, steps and all; not when it is only moved, as Sable moves
     * it into an assembled airliner's plot and back.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !movedByPiston && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SlideControlBlockEntity slide) {
            slide.stowAway();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SlideControlBlockEntity(pos, state);
    }

    /** The server runs the slide; every client runs the same clock, to draw it moving in between. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AerolargeRegistry.SLIDE_CONTROL_ENTITY.get(),
            level.isClientSide ? SlideControlBlockEntity::clientTick : SlideControlBlockEntity::serverTick);
    }
}
