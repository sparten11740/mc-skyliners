package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The club bar's tap tower, to stand on the bar counter: a chrome column under a head with three spouts, a tall neon
 * handle on each, pink, cyan and lime, glowing whatever the light around, and a drip tray under the spouts. Use it to
 * pull the handle nearest where you click: the handle tips down toward you, a stream of fizzing Star Fizz runs from its
 * spout into the drip tray for a second (POUR, cleared again by a scheduled tick), splashing and popping as it hits the
 * tray, and it pours with a fizz. With a glass bottle in hand the stream fills it: the empty bottle is gone (kept in
 * creative mode) and a bottle of Star Fizz is in its place. While it pours, a handle only clicks. FACING is the way its
 * handles face, toward whoever stands at the bar.
 *
 * <p>The fizz and the splashes are set at the tap itself, which Sable carries along with the rocket, or whatever else
 * the tap stands in, once that is assembled, so in flight they stay with the bar instead of being left behind.
 */
public class DrinkTapBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<DrinkTapBlock> CODEC = simpleCodec(DrinkTapBlock::new);
    public static final EnumProperty<Pour> POUR = EnumProperty.create("pour", Pour.class);
    /** How long a pull of a handle pours for, in ticks. */
    private static final int POUR_TICKS = SharedConstants.TICKS_PER_SECOND;
    /** How loud the pour is; and the click of a handle pulled while another pours, quiet and high, a plastic one's. */
    private static final float POUR_VOLUME = 0.8F;
    private static final float BUSY_VOLUME = 0.3F;
    private static final float BUSY_PITCH = 1.6F;
    /**
     * Where the stream hits the drip tray's grate, in the model drawn facing north: how high, in pixels, and how far
     * back from the front of the block, under the spouts.
     */
    private static final double GRATE_Y = 0.75;
    private static final double SPOUT_Z = 4;
    /** How many splashes and popping bubbles the stream throws up off the grate, and how far they spread, in blocks. */
    private static final int SPLASHES = 6;
    private static final int BUBBLES = 4;
    private static final double SPREAD = 0.06;
    /** The tower facing north: the drip tray, the column, its head with the spouts out of it, the handles on top. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Shapes.or(
        Block.box(3, 0, 1.5, 13, 0.75, 6),
        Block.box(6, 0, 7, 10, 6, 11),
        Block.box(2.5, 5.25, 3.5, 13.5, 8, 9.5),
        Block.box(3.25, 7.5, 5.25, 12.75, 13, 6.75)
    ));

    /** Which handle is pulled and pouring, by its colour, and where its spout is across the tower. */
    public enum Pour implements StringRepresentable {
        NONE(0),
        PINK(4),
        CYAN(8),
        LIME(12);

        /** How far across the tower its handle and spout are: pixels from the model's west side, drawn facing north. */
        private final double across;

        Pour(double across) {
            this.across = across;
        }

        /** The handle nearest a point across the tower, in pixels from the west side of the model facing north. */
        static Pour nearest(double across) {
            return Arrays.stream(values()).filter(pour -> pour != NONE)
                .min(Comparator.comparingDouble(pour -> Math.abs(pour.across - across))).orElse(CYAN);
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public DrinkTapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POUR, Pour.NONE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POUR);
    }

    /** Its handles toward whoever puts it down, as a bar counter's front is. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** A glass bottle held under the stream is filled with Star Fizz; anything else in hand only pulls the handle. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.GLASS_BOTTLE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server && pull(server, state, pos, hit)) {
            ItemStack fizz = new ItemStack(AerolargeRegistry.STAR_FIZZ.get());
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, fizz, false));
            player.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
            server.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            pull(server, state, pos, hit);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Pulls the handle nearest the point clicked and pours, if none is pouring: whether it poured. While one pours, a
     * click, and the stream's end at once where that was lost, as when the rocket was assembled in the middle of a
     * pour.
     */
    private boolean pull(ServerLevel level, BlockState state, BlockPos pos, BlockHitResult hit) {
        if (state.getValue(POUR) != Pour.NONE) {
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, BUSY_VOLUME, BUSY_PITCH);
            if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, this, 1);
            }
            return false;
        }
        Direction facing = state.getValue(FACING);
        Pour pour = Pour.nearest(16 * across(facing, hit.getLocation().subtract(Vec3.atLowerCornerOf(pos))));
        level.setBlock(pos, state.setValue(POUR, pour), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, POUR_TICKS);
        Vec3 grate = Vec3.atLowerCornerOf(pos).add(TurnedShapes.point(facing, pour.across, SPOUT_Z)).add(0, GRATE_Y / 16, 0);
        level.playSound(null, grate.x, grate.y, grate.z, AerolargeRegistry.TAP_POUR.get(), SoundSource.BLOCKS, POUR_VOLUME, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, grate.x, grate.y, grate.z, SPLASHES, SPREAD, 0, SPREAD, 0);
        level.sendParticles(ParticleTypes.BUBBLE_POP, grate.x, grate.y, grate.z, BUBBLES, SPREAD, 0, SPREAD, 0);
        return true;
    }

    /**
     * How far across the tower a point in its block is, from 0 to 1, as the model drawn facing north runs from its west
     * side to its east, turned the way the tower faces (see {@link TurnedShapes#point}). The point is the one clicked
     * less the block's corner: aboard an assembled rocket both are in her plot, so it is in the block either way.
     */
    private static double across(Direction facing, Vec3 inBlock) {
        return switch (facing) {
            case SOUTH -> 1 - inBlock.x;
            case EAST -> inBlock.z;
            case WEST -> 1 - inBlock.z;
            default -> inBlock.x;
        };
    }

    /** The pour is done: the handle springs back up and the stream stops. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POUR) != Pour.NONE) {
            level.setBlock(pos, state.setValue(POUR, Pour.NONE), Block.UPDATE_ALL);
        }
    }
}
