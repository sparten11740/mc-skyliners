package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A little rubber duck: yellow, with an orange beak, a few pixels big, to put down anywhere. FACING is the way its
 * beak points, toward whoever put it down. Put into water it is WATERLOGGED and floats at the top of its block, its
 * body a little under the water line. Used or hit, it squeaks, with a few hearts and a wiggle that everyone near sees
 * (a block event, see {@link RubberDuckBlockEntity}). Its renderer draws it from one model whatever its state, so it
 * can bob afloat and wiggle.
 * <p>
 * DUCK is which of the ducks hidden aboard a furnished airliner it is, 1 to {@link #HIDDEN_DUCKS}, or 0 for one put
 * down by hand. Squeaking a hidden duck finds it, in its airliner's own hunt and for the Duck Hunt advancement (see
 * {@link RubberDuckBlockEntity}). A hidden duck stays put for anyone not sneaking: a hit finds it rather than breaking
 * it, even in creative mode, so a child cannot lose one by clicking the wrong button; taken away on purpose, it leaves
 * nothing behind.
 */
public class RubberDuckBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, EntityBlock {
    private static final MapCodec<RubberDuckBlock> CODEC = simpleCodec(RubberDuckBlock::new);
    /** How many ducks a furnished airliner hides: the Duck Hunt advancement has a criterion for each. */
    public static final int HIDDEN_DUCKS = 5;
    public static final IntegerProperty DUCK = IntegerProperty.create("duck", 0, HIDDEN_DUCKS);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /**
     * Whether the duck sits back on the back half of its block, {@link #PERCH} pixels behind the middle: perched on
     * the edge of something only half a block deep, such as a locker's top, instead of hanging over the air in front.
     */
    public static final BooleanProperty PERCHED = BooleanProperty.create("perched");
    /**
     * The duck drawn facing north, as boxes from one corner to the other in pixels: its body, its head on the front of
     * it, its beak out of the front of the head, and its tail cocked up at the back. The model is drawn from the same
     * boxes (tools/generate_resources.mjs).
     */
    public static final List<double[]> BOXES = List.of(
        new double[]{5, 0, 5, 11, 3, 12},
        new double[]{6, 3, 5, 10, 6, 8},
        new double[]{7, 4, 3, 9, 5, 5},
        new double[]{6, 3, 11, 10, 4, 12}
    );
    /** How far up its block a duck afloat sits, in pixels: the water stands a little over 14 high in it. */
    public static final double FLOAT = 13;
    /** How far back from the middle of its block a perched duck sits, in pixels. */
    public static final double PERCH = 4;
    /** A block's width in pixels, what the boxes turn about the middle of. */
    private static final double BLOCK = 16;
    private static final Map<Direction, VoxelShape> SITTING = shapes(0, 0);
    private static final Map<Direction, VoxelShape> AFLOAT = shapes(FLOAT, 0);
    private static final Map<Direction, VoxelShape> SITTING_BACK = shapes(0, PERCH);
    /** The hearts as it squeaks, how far they spread and how high over its block they start. */
    private static final int HEARTS = 3;
    private static final double HEART_SPREAD = 0.25;
    private static final double HEART_HEIGHT = 0.6;
    /** How loud a squeak is, and how far either side of its own pitch each one is: no two quite alike. */
    private static final float SQUEAK_VOLUME = 1.2F;
    private static final float SQUEAK_PITCH_SPREAD = 0.1F;

    public RubberDuckBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false).setValue(DUCK, 0)
            .setValue(PERCHED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, DUCK, PERCHED);
    }

    /**
     * One of the boxes turned from facing north to facing the given way, about the middle of a square {@code size}
     * across: a block's pixels, or for the giant duck the blocks it stands on.
     */
    public static double[] turn(double[] box, Direction facing, double size) {
        return switch (facing) {
            case EAST -> new double[]{size - box[5], box[1], box[0], size - box[2], box[4], box[3]};
            case SOUTH -> new double[]{size - box[3], box[1], size - box[5], size - box[0], box[4], size - box[2]};
            case WEST -> new double[]{box[2], box[1], size - box[3], box[5], box[4], size - box[0]};
            default -> box.clone();
        };
    }

    /** The duck's shape facing each way, its boxes lifted this many pixels and moved this many back. */
    private static Map<Direction, VoxelShape> shapes(double lift, double back) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : BOXES) {
                // Facing north, the duck's back is to the south, along z.
                double[] turned = turn(new double[]{box[0], box[1], box[2] + back, box[3], box[4], box[5] + back}, facing, BLOCK);
                shape = Shapes.or(shape, Block.box(turned[0], turned[1] + lift, turned[2], turned[3], turned[4] + lift, turned[5]));
            }
            shapes.put(facing, shape.optimize());
        }
        return shapes;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WATERLOGGED, water);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Map<Direction, VoxelShape> shapes = state.getValue(WATERLOGGED) ? AFLOAT : sitsBack(state) ? SITTING_BACK : SITTING;
        return shapes.get(state.getValue(FACING));
    }

    /** Whether the duck sits back on its block, perched: not afloat, which drifts it to the middle. */
    public static boolean sitsBack(BlockState state) {
        return state.getValue(PERCHED) && !state.getValue(WATERLOGGED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        squeak(state, level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        squeak(state, level, pos, player);
    }

    /** Whether the duck stays put for the player: a hidden one, unless they sneak. */
    private static boolean holdsOn(BlockState state, Player player) {
        return state.getValue(DUCK) > 0 && !player.isShiftKeyDown();
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return holdsOn(state, player) ? 0 : super.getDestroyProgress(state, player, level, pos);
    }

    /** No breaking crumbs or sound for a duck that stays put. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        return holdsOn(state, player) ? state : super.playerWillDestroy(level, pos, state, player);
    }

    /** Creative mode breaks a block outright, without mining it: a hidden duck squeaks and is found instead. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (holdsOn(state, player)) {
            squeak(state, level, pos, player);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /**
     * A squeak and a few hearts for everyone near, and a wiggle on every client in sight; a hidden duck is found by
     * the player who squeaked it.
     */
    private void squeak(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        float pitch = 1.0F + (server.random.nextFloat() * 2 - 1) * SQUEAK_PITCH_SPREAD;
        server.playSound(null, pos, AerolargeRegistry.RUBBER_DUCK_SQUEAK.get(), SoundSource.BLOCKS, SQUEAK_VOLUME, pitch);
        server.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5, pos.getY() + HEART_HEIGHT + lift(state), pos.getZ() + 0.5,
            HEARTS, HEART_SPREAD, HEART_SPREAD / 2, HEART_SPREAD, 0);
        server.blockEvent(pos, this, RubberDuckBlockEntity.WIGGLE, 0);
        if (player instanceof ServerPlayer finder && level.getBlockEntity(pos) instanceof RubberDuckBlockEntity duck) {
            duck.found(finder);
        }
    }

    /** How far up its block the duck is, in blocks: afloat, it sits near the top. */
    public static double lift(BlockState state) {
        return state.getValue(WATERLOGGED) ? FLOAT / BLOCK : 0;
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        BlockEntity duck = level.getBlockEntity(pos);
        return duck != null && duck.triggerEvent(id, param);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RubberDuckBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AerolargeRegistry.RUBBER_DUCK_ENTITY.get()) {
            return null;
        }
        return level.isClientSide
            ? (tickLevel, pos, tickState, duck) -> RubberDuckBlockEntity.clientTicker.accept((RubberDuckBlockEntity) duck)
            : (tickLevel, pos, tickState, duck) -> ((RubberDuckBlockEntity) duck).serverTick();
    }
}
