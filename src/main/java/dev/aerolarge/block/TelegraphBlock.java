package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An engine telegraph in a zeppelin's control car: a brass pedestal with a round dial on top, its handle pointing at
 * one of five orders, each a picture in a colour, so it is read without words: astern (a blue arrow pointing back),
 * stop (a red bar), slow (one yellow dot), half (two lime dots) and full (three green dots). Using it rings it a
 * step toward full ahead, using it while sneaking a step back toward astern, with the double ring of its bell. The
 * zeppelin has two, the port engines' and the starboard engines' (ENGINES), their base plates red and green as a
 * ship's sides are; the autopilot reads them while it is switched off and drives the engines as they say, and rings
 * them itself while it flies. FACING is the way whoever works it looks; the dial faces them.
 */
public class TelegraphBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<TelegraphBlock> CODEC = simpleCodec(TelegraphBlock::new);
    public static final EnumProperty<Order> ORDER = EnumProperty.create("order", Order.class);
    public static final EnumProperty<Engines> ENGINES = EnumProperty.create("engines", Engines.class);
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 16, 12);

    /** What the engines are rung to do, astern to full ahead, and the share of full thrust each asks for. */
    public enum Order implements StringRepresentable {
        ASTERN(-0.5), STOP(0), SLOW(0.3), HALF(0.6), FULL(1);

        public final double thrust;

        Order(double thrust) {
            this.thrust = thrust;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The order nearest a share of full thrust, astern below nothing. */
        public static Order nearest(double thrust) {
            Order nearest = STOP;
            for (Order order : values()) {
                if (Math.abs(order.thrust - thrust) < Math.abs(nearest.thrust - thrust)) {
                    nearest = order;
                }
            }
            return nearest;
        }
    }

    /** Which side's engines a telegraph rings for. */
    public enum Engines implements StringRepresentable {
        PORT, STARBOARD;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public TelegraphBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ORDER, Order.STOP).setValue(ENGINES, Engines.PORT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ORDER, ENGINES);
    }

    /** Put down by hand, its dial faces whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Order[] orders = Order.values();
        int at = Math.clamp(state.getValue(ORDER).ordinal() + (player.isShiftKeyDown() ? -1 : 1), 0, orders.length - 1);
        if (orders[at] == state.getValue(ORDER)) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            ring(level, pos, state.setValue(ORDER, orders[at]));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sets the telegraph to an order, ringing its bell twice as a telegraph is rung; nothing if it says so already. */
    public static void ring(Level level, BlockPos pos, BlockState rung) {
        if (level.getBlockState(pos).equals(rung)) {
            return;
        }
        level.setBlock(pos, rung, Block.UPDATE_ALL);
        float pitch = 1.2F + 0.1F * rung.getValue(ORDER).ordinal();
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, pitch);
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6F, pitch * 1.25F);
    }
}
