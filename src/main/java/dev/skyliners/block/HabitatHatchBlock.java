package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.entity.Tardigrade;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The feeding hatch in the glass of the tardigrade's tank, at chest height (see {@link HabitatHatchBlockEntity}, which
 * keeps the creature): a pane of the tank's glass with a small steel hatch in it. Use it with moss or a babycino in
 * hand and the hatch slides open, the food goes in and the creature comes to eat it; with an empty hand, to reach in
 * and touch it.
 * Open, a hand fits through it, so the creature can be fed or touched straight through it when it stands there. FACING
 * is the way out of the tank. Broken, it gives itself back. Set up by hand it keeps no creature, having no tank, and
 * only opens and shuts.
 */
public class HabitatHatchBlock extends BaseEntityBlock {
    private static final MapCodec<HabitatHatchBlock> CODEC = simpleCodec(HabitatHatchBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** How long the hatch stands open once used, in ticks. */
    private static final int OPEN_TICKS = 40;
    /** How thick its pane is, and the opening's edges across and up, in pixels, as drawn facing north. */
    private static final double PANE = 1;
    private static final double OPENING_FROM = 5;
    private static final double OPENING_TO = 11;
    private static final Map<Direction, VoxelShape> SHUT = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> OPENED = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape pane = box(facing, 0, 0, 0, 16, 16, PANE);
            SHUT.put(facing, pane);
            OPENED.put(facing, Shapes.join(pane, box(facing, OPENING_FROM, OPENING_FROM, 0, OPENING_TO, OPENING_TO, PANE), BooleanOp.ONLY_FIRST));
        }
    }

    public HabitatHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    /** Put down by hand, it faces whoever puts it there. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** A box given in pixels as drawn facing north, turned to face the given way about the block's middle. */
    private static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(OPEN) ? OPENED : SHUT).get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** Its food in hand: the hatch opens and the creature is called to eat it, the food used up only if it comes. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
        BlockHitResult hit) {
        if (!Tardigrade.eats(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof HabitatHatchBlockEntity keeper) {
            open(server, pos, state);
            if (keeper.feed(server, stack)) {
                stack.consume(1, player);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand: the hatch opens and the creature comes to be touched, or wriggles at once if it is there. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof HabitatHatchBlockEntity keeper) {
            open(server, pos, state);
            keeper.call(server, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Slides the hatch open, with the click of its latch, and shuts it again a little later. */
    private void open(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(OPEN)) {
            level.setBlock(pos, state.setValue(OPEN, true), UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 1.5F);
        }
        level.scheduleTick(pos, this, OPEN_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN)) {
            level.setBlock(pos, state.setValue(OPEN, false), UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5F, 1.5F);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HabitatHatchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != SkylinersRegistry.HABITAT_HATCH_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, keeper) -> HabitatHatchBlockEntity.tick((ServerLevel) world, pos, (HabitatHatchBlockEntity) keeper);
    }
}
