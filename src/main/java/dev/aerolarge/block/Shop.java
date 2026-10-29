package dev.aerolarge.block;

import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Block;

/**
 * A shop at a block, opened for one shopper: vanilla's trading screen, its goods priced in emeralds, the fares villager
 * passengers pay, and each shown by its picture with its price, so it needs no reading. No villager stands behind it,
 * so nobody can wander off, be sucked out of a door or be left behind by a moving airliner: the block itself sells, a
 * duty-free counter (see {@link DutyFreeCounterBlock}) or a robot bartender (see {@link RobotBartenderBlock}). Its
 * goods never run out and it never levels up, so the prices always stay the same. What it sells, its name and its
 * sounds are its {@link Shopfront}; the shop stays open while that block stands and the shopper is in reach of it.
 */
public final class Shop implements Merchant {
    /** How often a trade can be made before it is sold out: never. */
    private static final int UNLIMITED = Integer.MAX_VALUE;
    /** How far past their reach a shopper can step before the shop closes, in blocks, as for a chest. */
    private static final double REACH_SLACK = 4.0;
    /** Long enough ago that a sale then is past any shop's quiet, without overflowing when taken from the time now. */
    private static final long NEVER = -1_000_000;

    private final ServerLevel level;
    private final BlockPos stand;
    /** The block the shop is at, which it closes without. */
    private final Block block;
    private final Shopfront front;
    private final MerchantOffers offers = new MerchantOffers();
    @Nullable
    private Player shopper;
    /** The tick the last sale was rung up, in game ticks: buying a whole stack with one click rings it once. */
    private long rang = NEVER;

    /**
     * A kind of shop: its name over the trading screen, what it sells, cheapest first, the sound it opens with, the
     * sound of a sale, how long after one, in ticks, the next makes its sound again, and what more a sale does there.
     */
    record Shopfront(Component title, List<Trade> stock, Supplier<SoundEvent> opening, Supplier<SoundEvent> sale, int saleQuiet, Sale sold) {
    }

    /** One of a shop's trades: so many emeralds for so many of an item. */
    record Trade(int emeralds, Supplier<? extends ItemLike> item, int count) {
    }

    /** What a sale does at its shop's block besides its sound, given the shopper; once a sale's quiet is over. */
    @FunctionalInterface
    interface Sale {
        void made(ServerLevel level, BlockPos stand, @Nullable ServerPlayer shopper);
    }

    private Shop(ServerLevel level, BlockPos stand, Shopfront front, Player shopper) {
        this.level = level;
        this.stand = stand;
        this.block = level.getBlockState(stand).getBlock();
        this.front = front;
        this.shopper = shopper;
        for (Trade trade : front.stock()) {
            // No experience and no price swings: the price on the screen is the price.
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, trade.emeralds()), new ItemStack(trade.item().get(), trade.count()), UNLIMITED, 0, 0));
        }
    }

    /** Opens the shop at the block for the player, with its opening sound. */
    static void open(ServerPlayer player, ServerLevel level, BlockPos stand, Shopfront front) {
        Shop shop = new Shop(level, stand, front, player);
        player.openMenu(new SimpleMenuProvider((id, inventory, opener) -> new ShopMenu(id, inventory, shop), front.title()))
            .ifPresent(id -> player.sendMerchantOffers(id, shop.offers, 0, 0, false, false));
        level.playSound(null, stand, front.opening().get(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** Whether the shop is still open to the player: its block still stands, and they are still in reach of it. */
    boolean openTo(Player player) {
        return level.getBlockState(stand).is(block) && player.canInteractWithBlock(stand, REACH_SLACK);
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

    /** A sale: its sound, and whatever more it does at the shop's block, unless the last one's quiet is not over. */
    @Override
    public void notifyTrade(MerchantOffer offer) {
        long now = level.getGameTime();
        if (now - rang < front.saleQuiet()) {
            return;
        }
        rang = now;
        level.playSound(null, stand, getNotifyTradeSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
        front.sold().made(level, stand, shopper instanceof ServerPlayer player ? player : null);
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
        return front.sale().get();
    }

    @Override
    public boolean isClientSide() {
        return false;
    }
}
