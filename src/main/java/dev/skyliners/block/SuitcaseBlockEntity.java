package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.FirstFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * What is packed in a suitcase: a row of a chest's slots. A golden suitcase hidden in the hold also knows which of
 * the hold's golden suitcases it is, one to {@link #GOLDEN_SUITCASES}: opening it counts that one toward the player's
 * Lost and Found advancement, with a fanfare and a shower of sparkles the first time. One placed by hand is none of
 * them.
 */
public class SuitcaseBlockEntity extends RandomizableContainerBlockEntity {
    /** How many golden suitcases an airliner's hold hides: the Lost and Found advancement has a criterion for each. */
    public static final int GOLDEN_SUITCASES = 3;
    /** The sparkles as a golden suitcase is found. */
    private static final int SPARKLES = 40;
    /** A suitcase holds one row of a chest's slots. */
    private static final int SLOTS = 9;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    /** Which of the hold's golden suitcases this is, from 1; 0 for none. */
    private int golden;

    public SuitcaseBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.SUITCASE_ENTITY.get(), pos, state);
    }

    /** Packs the suitcase from a loot table when it is first opened, and makes it the given golden suitcase, from 1. */
    public void pack(ResourceKey<LootTable> loot, int goldenSuitcase) {
        setLootTable(loot, 0);
        golden = goldenSuitcase;
        setChanged();
    }

    /** Counts this golden suitcase as found by the player, with a fanfare the first time; nothing for any other. */
    void found(ServerPlayer player) {
        if (golden <= 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        BlockPos pos = getBlockPos();
        server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, SPARKLES, 0.3, 0.3, 0.3, 0.4);
        if (FirstFlight.LOST_AND_FOUND.award(player, "suitcase_" + golden)) {
            server.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("message.skyliners.golden_suitcase.found",
                FirstFlight.LOST_AND_FOUND.progress(player), GOLDEN_SUITCASES), true);
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (level != null && !remove && !player.isSpectator()) {
            level.playSound(null, getBlockPos(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 0.8F, 0.9F);
        }
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(getBlockState().getBlock() instanceof SuitcaseBlock suitcase && suitcase.golden()
            ? "container.skyliners.golden_suitcase" : "container.skyliners.suitcase");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x1, id, inventory, this, 1);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items, registries);
        }
        tag.putInt("Golden", golden);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items, registries);
        }
        golden = tag.getInt("Golden");
    }
}
