package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The snack machine in the rocket's cafeteria, two blocks tall: a midnight-blue cabinet round a lit showcase of
 * snacks on their spirals, a lamp and a keypad beside it, and the flap at its foot that a snack drops into. Use it
 * while it is ready and its spiral turns with a whirr, its lamp blinking amber; a moment later the snack drops into the
 * flap with a clunk, the flap lighting up green with the packet in it, and using the machine again takes the snack
 * out, into the hand. Which snack it is comes as a surprise. Everything happens on the server, and the step it is at
 * is its state, so nothing more is kept. HALF is which of its two blocks this is; FACING is the way its front looks;
 * VEND, the same in both, is the step it is at.
 */
public class SnackMachineBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<SnackMachineBlock> CODEC = simpleCodec(SnackMachineBlock::new);
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Vend> VEND = EnumProperty.create("vend", Vend.class);
    /** The cabinet, drawn facing north: a pixel in from either side and from its back, its front a little proud. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Block.box(1, 0, 1.5, 15, 16, 15));
    /**
     * How long the spiral turns before the snack drops, in ticks: as long as its whirr (see
     * tools/generate_sounds.mjs).
     */
    private static final int TURN_TICKS = 28;
    /** The light its showcase gives always, and its flap while a snack is on its way or in it, in light levels. */
    private static final int SHOWCASE_GLOW = 7;
    private static final int FLAP_GLOW = 9;
    /** The keypad's beep while the spiral is still turning, low and quiet: it is busy. */
    private static final float BUSY_VOLUME = 0.5F;
    private static final float BUSY_PITCH = 0.9F;
    /** The packet taken out of the flap, a little brighter than an item picked up off the floor. */
    private static final float TAKE_VOLUME = 0.7F;
    private static final float TAKE_PITCH = 1.3F;
    /** What it sells, one of them at random each time. */
    private static final List<Item> SNACKS = List.of(Items.COOKIE, Items.APPLE, Items.MELON_SLICE, Items.PUMPKIN_PIE, Items.GLOW_BERRIES,
        Items.HONEY_BOTTLE, Items.DRIED_KELP);

    /** The steps of a sale: ready for the next, the spiral turning, and the snack dropped into the flap, waiting. */
    public enum Vend implements StringRepresentable {
        READY,
        TURNING,
        DROPPED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public SnackMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(VEND, Vend.READY));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, VEND);
    }

    /** The machine's lower block facing the way given, ready; its upper block is {@link #upper} of it. */
    public static BlockState lower(Block machine, Direction facing) {
        return machine.defaultBlockState().setValue(FACING, facing);
    }

    /** The upper block over a machine's lower block. */
    public static BlockState upper(BlockState lower) {
        return lower.setValue(HALF, DoubleBlockHalf.UPPER);
    }

    /** Put down by hand, it faces whoever puts it there, and needs the room above for its upper block. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return lower(this, context.getHorizontalDirection().getOpposite());
    }

    /** The upper block goes up over the lower block put down. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), upper(state), Block.UPDATE_ALL);
    }

    /** Broken by its upper block in creative mode, the lower block it takes with it drops nothing either. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER && level.getBlockState(pos.below()).is(this)) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** The light a block of it gives: its showcase always, its flap while a sale is under way. */
    public static int light(BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return SHOWCASE_GLOW;
        }
        return state.getValue(VEND) == Vend.READY ? 0 : FLAP_GLOW;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /**
     * Its two blocks keep each other's step and facing, each taking the other's state as it changes; either one gone,
     * the other goes too.
     */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction != (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)) {
            return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        }
        return neighbour.is(this) && neighbour.getValue(HALF) != half ? neighbour.setValue(HALF, half) : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Ready, it starts a sale; with a snack in the flap, it hands it over; with the spiral still turning, it beeps. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos base = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockState machine = server.getBlockState(base);
        if (!machine.is(this)) {
            return InteractionResult.CONSUME;
        }
        switch (machine.getValue(VEND)) {
            case READY -> {
                server.setBlock(base, machine.setValue(VEND, Vend.TURNING), Block.UPDATE_ALL);
                server.playSound(null, base, AerolargeRegistry.SNACK_MACHINE_WHIRR.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                server.scheduleTick(base, this, TURN_TICKS);
            }
            case DROPPED -> handOut(server, base, machine, player);
            case TURNING -> busy(server, base);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * With the spiral still turning, the keypad beeps; a sale whose turn was lost, as when she was assembled while it
     * turned, starts turning again, so the machine never sticks.
     */
    private void busy(ServerLevel level, BlockPos base) {
        if (!level.getBlockTicks().hasScheduledTick(base, this)) {
            level.scheduleTick(base, this, TURN_TICKS);
        }
        level.playSound(null, base, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, BUSY_VOLUME, BUSY_PITCH);
    }

    /** The spiral has turned: the snack drops into the flap, which lights up green. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(VEND) == Vend.TURNING) {
            level.setBlock(pos, state.setValue(VEND, Vend.DROPPED), Block.UPDATE_ALL);
            level.playSound(null, pos, AerolargeRegistry.SNACK_MACHINE_CLUNK.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /** A snack out of the flap, one of them at random, into the hand or at the feet with no room for it. */
    private static void handOut(ServerLevel level, BlockPos base, BlockState machine, Player player) {
        level.setBlock(base, machine.setValue(VEND, Vend.READY), Block.UPDATE_ALL);
        ItemStack snack = new ItemStack(SNACKS.get(level.random.nextInt(SNACKS.size())));
        if (!player.addItem(snack)) {
            player.drop(snack, false);
        }
        level.playSound(null, base, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, TAKE_VOLUME, TAKE_PITCH);
    }
}
