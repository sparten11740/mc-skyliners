package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The duty-free counter: a shop counter with a glass showcase on top. Using it opens its shop (see {@link Shop}),
 * where emeralds from passengers' fares buy toys, snacks and souvenirs. FACING is the way its front looks: toward the
 * shopper.
 */
public class DutyFreeCounterBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<DutyFreeCounterBlock> CODEC = simpleCodec(DutyFreeCounterBlock::new);
    /**
     * What the counter sells, cheapest first. A passenger pays an emerald for every 200 blocks flown, so a short hop
     * with a few aboard buys a snack or a paper plane, and a souvenir takes a few flights' fares.
     */
    private static final List<Shop.Trade> STOCK = List.of(
        new Shop.Trade(1, AerolargeRegistry.PAPER_PLANE_ITEM, 4),
        new Shop.Trade(1, AerolargeRegistry.AIRLINE_COFFEE, 2),
        new Shop.Trade(1, AerolargeRegistry.PRETZELS, 4),
        new Shop.Trade(1, AerolargeRegistry.ICE_CREAM, 2),
        new Shop.Trade(1, AerolargeRegistry.CHICKEN_MEAL, 1),
        new Shop.Trade(1, AerolargeRegistry.PASTA_MEAL, 1),
        new Shop.Trade(1, AerolargeRegistry.SIGNAL_FLARE_ITEM, 2),
        new Shop.Trade(2, AerolargeRegistry.BASKETBALL_ITEM, 1),
        new Shop.Trade(4, AerolargeRegistry.PARACHUTE, 1),
        new Shop.Trade(5, AerolargeRegistry.PILOT_CAP, 1),
        new Shop.Trade(6, AerolargeRegistry.CARGO_CRATE_ITEM, 1),
        new Shop.Trade(8, AerolargeRegistry.MODEL_AIRLINER_ITEM, 1),
        new Shop.Trade(10, AerolargeRegistry.PILOT_HELMET, 1)
    );
    /** How long after a sale the till rings again, in ticks: the next tick, so a stack bought at once rings once. */
    private static final int TILL_QUIET = 1;
    /** The counter's shop: the ding of a shop's door bell as it opens, and the till ringing up each sale. */
    private static final Shop.Shopfront SHOPFRONT = new Shop.Shopfront(Component.translatable("container.aerolarge.duty_free"), STOCK,
        AerolargeRegistry.SHOP_BELL, AerolargeRegistry.CASH_REGISTER, TILL_QUIET, DutyFreeCounterBlock::sold);

    public DutyFreeCounterBlock(Properties properties) {
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

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer shopper) {
            Shop.open(shopper, server, pos, SHOPFRONT);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A sale at the counter: it sparkles, the till rings, and the first one is the Duty Free advancement. */
    private static boolean sold(ServerLevel level, BlockPos counter, @Nullable ServerPlayer shopper) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, counter.getX() + 0.5, counter.getY() + 1.1, counter.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.0);
        if (shopper != null) {
            FirstFlight.DUTY_FREE.award(shopper);
        }
        return true;
    }
}
