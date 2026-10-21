package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ItemLike;

/**
 * The shop at a duty-free counter, opened for one shopper: vanilla's trading screen, its goods priced in emeralds, the
 * fares villager passengers pay, and each shown by its picture with its price, so it needs no reading. No villager
 * stands behind the counter, so nobody can wander off, be sucked out of a door or be left behind by a moving airliner:
 * the counter itself sells. Its goods never run out and it never levels up, so the prices always stay the same.
 */
public final class DutyFreeShop implements Merchant {
    private static final Component TITLE = Component.translatable("container.aerolarge.duty_free");
    /** How often a trade can be made before it is sold out: never. */
    private static final int UNLIMITED = Integer.MAX_VALUE;
    /** How far past their reach a shopper can step before the shop closes, in blocks, as for a chest. */
    private static final double REACH_SLACK = 4.0;
    /**
     * What the counter sells, cheapest first. A passenger pays an emerald for every 200 blocks flown, so a short hop
     * with a few aboard buys a snack or a paper plane, and a souvenir takes a few flights' fares.
     */
    private static final List<Trade> TRADES = List.of(
        new Trade(1, AerolargeRegistry.PAPER_PLANE_ITEM, 4),
        new Trade(1, AerolargeRegistry.AIRLINE_COFFEE, 2),
        new Trade(1, AerolargeRegistry.PRETZELS, 4),
        new Trade(1, AerolargeRegistry.ICE_CREAM, 2),
        new Trade(1, AerolargeRegistry.CHICKEN_MEAL, 1),
        new Trade(1, AerolargeRegistry.PASTA_MEAL, 1),
        new Trade(1, AerolargeRegistry.SIGNAL_FLARE_ITEM, 2),
        new Trade(2, AerolargeRegistry.BASKETBALL_ITEM, 1),
        new Trade(4, AerolargeRegistry.PARACHUTE, 1),
        new Trade(5, AerolargeRegistry.PILOT_CAP, 1),
        new Trade(6, AerolargeRegistry.CARGO_CRATE_ITEM, 1),
        new Trade(8, AerolargeRegistry.MODEL_AIRLINER_ITEM, 1),
        new Trade(10, AerolargeRegistry.PILOT_HELMET, 1)
    );

    private final ServerLevel level;
    private final BlockPos counter;
    private final MerchantOffers offers = new MerchantOffers();
    @Nullable
    private Player shopper;
    /** The tick the till last rang: buying a whole stack with one click rings it once. */
    private long rang = -1;

    private DutyFreeShop(ServerLevel level, BlockPos counter, Player shopper) {
        this.level = level;
        this.counter = counter;
        this.shopper = shopper;
        for (Trade trade : TRADES) {
            // No experience and no price swings: the price on the screen is the price.
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, trade.emeralds()), new ItemStack(trade.item().get(), trade.count()), UNLIMITED, 0, 0));
        }
    }

    /** Opens the counter's shop for the player, with the ding of a shop's door bell. */
    static void open(ServerPlayer player, ServerLevel level, BlockPos counter) {
        DutyFreeShop shop = new DutyFreeShop(level, counter, player);
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new DutyFreeMenu(id, inventory, shop), TITLE))
            .ifPresent(id -> player.sendMerchantOffers(id, shop.offers, 0, 0, false, false));
        level.playSound(null, counter, AerolargeRegistry.SHOP_BELL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** Whether the shop is still open to the player: the counter still stands, and they are still in reach of it. */
    boolean openTo(Player player) {
        return level.getBlockState(counter).is(AerolargeRegistry.DUTY_FREE_COUNTER.get()) && player.canInteractWithBlock(counter, REACH_SLACK);
    }

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        shopper = player;
    }

    @Nullable
    @Override
    public Player getTradingPlayer() {
        return shopper;
    }

    @Override
    public MerchantOffers getOffers() {
        return offers;
    }

    /** Only the client's copy of a shop takes its offers from the server; this is the server's own. */
    @Override
    public void overrideOffers(MerchantOffers offers) {
    }

    /** A sale: the till rings and the counter sparkles, and the first one is the Duty Free advancement. */
    @Override
    public void notifyTrade(MerchantOffer offer) {
        if (level.getGameTime() != rang) {
            rang = level.getGameTime();
            level.playSound(null, counter, getNotifyTradeSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, counter.getX() + 0.5, counter.getY() + 1.1, counter.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.0);
        }
        if (shopper instanceof ServerPlayer player) {
            FirstFlight.DUTY_FREE.award(player);
        }
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return AerolargeRegistry.CASH_REGISTER.get();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    /** One of the counter's trades: so many emeralds for so many of an item. */
    private record Trade(int emeralds, Supplier<? extends ItemLike> item, int count) {
    }
}
