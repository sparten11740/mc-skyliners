package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An airliner lavatory's washbasin: a vanity unit in the toilet's warm grey laminate with a stainless kick strip, a
 * white counter with the basin set into it, a chrome tap arching over the basin from the back and a soap dispenser
 * beside it; and over it, against the wall, a mirror in a steel frame with a strip of warm light along its top. It is
 * two blocks tall, the vanity the lower half and the mirror the upper, and either half is the basin to use: the tap
 * runs into it with a splash, and a glass bottle held under it comes away full of water. Put down, it faces you, its
 * back to the wall behind it. FACING is the way its front looks. With no wall to hang a mirror on, as at the rocket's
 * lab sink, standing out on the floor, it is the vanity alone: MIRROR false, with no upper half.
 */
public class LavatoryBasinBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<LavatoryBasinBlock> CODEC = simpleCodec(LavatoryBasinBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Whether it has a mirror over it, its upper half; without one the vanity stands alone. */
    public static final BooleanProperty MIRROR = BooleanProperty.create("mirror");
    /** The tap's spout in the model drawn facing north, in pixels: where the water runs from, over the basin. */
    private static final double SPOUT_X = 8;
    private static final double SPOUT_Y = 14;
    private static final double SPOUT_Z = 10;
    /** How many drops the tap runs into the basin, and how quietly and how high it splashes. */
    private static final int DROPS = 10;
    private static final float SPLASH_VOLUME = 0.5F;
    private static final float SPLASH_PITCH = 1.4F;
    /** The lower half facing north, its back to the south: the vanity, the counter over it and the tap at its back. */
    private static final VoxelShape LOWER = Shapes.or(
        Block.box(1, 0, 4, 15, 10, 16),
        Block.box(0, 10, 2, 16, 13, 16),
        Block.box(7.5, 13, 9.5, 8.5, 16, 13.5)
    );
    /** The upper half facing north: the mirror flat against the wall. */
    private static final VoxelShape UPPER = Block.box(0.5, 0.5, 15, 15.5, 15.5, 16);
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = TurnedShapes.of(LOWER);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = TurnedShapes.of(UPPER);

    public LavatoryBasinBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(MIRROR, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, MIRROR);
    }

    /** Whether this is a basin's vanity, the lower half, where the basin itself is. */
    public static boolean isBasin(BlockState state) {
        return state.getBlock() instanceof LavatoryBasinBlock && state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    /** It faces whoever puts it down, its back to the wall behind it; it needs the room above for its mirror. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** The mirror goes up over the vanity put down. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** A half whose other half is gone goes too, so the basin is only ever whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardOtherHalf = state.getValue(MIRROR) && direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardOtherHalf && !(neighbour.is(this) && neighbour.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Broken by its mirror in creative mode, the vanity it takes with it drops nothing either. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER && level.getBlockState(pos.below()).is(this)) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES).get(state.getValue(FACING));
    }

    /** A glass bottle held under the tap comes away full of water; anything else just runs the tap. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.GLASS_BOTTLE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server) {
            run(server, basin(pos, state), state.getValue(FACING));
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, PotionContents.createItemStack(Items.POTION, Potions.WATER)));
            server.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            run(server, basin(pos, state), state.getValue(FACING));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Where the basin is: the vanity, under the mirror or the half itself. */
    private static BlockPos basin(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /** The tap runs: a short splash, and drops falling from the spout into the basin. */
    private static void run(ServerLevel level, BlockPos basin, Direction facing) {
        Vec3 spout = Vec3.atLowerCornerOf(basin).add(TurnedShapes.point(facing, SPOUT_X, SPOUT_Z)).add(0, SPOUT_Y / 16, 0);
        level.playSound(null, spout.x, spout.y, spout.z, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, SPLASH_VOLUME, SPLASH_PITCH);
        level.sendParticles(ParticleTypes.FALLING_WATER, spout.x, spout.y, spout.z, DROPS, 0.02, 0.05, 0.02, 0);
        level.sendParticles(ParticleTypes.SPLASH, spout.x, spout.y - 0.25, spout.z, DROPS, 0.08, 0, 0.08, 0.05);
    }
}
