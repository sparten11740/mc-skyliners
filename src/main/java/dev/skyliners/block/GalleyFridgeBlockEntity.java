package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What is kept cold in the galley's fridge: three rows of a chest's slots. Its door swings open, with the pop of its
 * seal and the hum of its cooling, while anyone looks in, and shuts behind the last of them with a soft thud.
 */
public class GalleyFridgeBlockEntity extends RandomizableContainerBlockEntity {
    /** Three rows of a chest's nine slots. */
    private static final int SLOTS = 27;
    /** How loud its door's pop and hum and its thud are. */
    private static final float VOLUME = 0.7F;
    /** What the blueprint stocks a fridge with, from its first slot on: something for everyone aboard. */
    private static final List<ItemStack> STOCK = List.of(
        new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.APPLE, 4), new ItemStack(Items.CARROT, 6),
        new ItemStack(Items.MELON_SLICE, 8), new ItemStack(Items.COOKED_CHICKEN, 3), new ItemStack(Items.SWEET_BERRIES, 12),
        new ItemStack(Items.PUMPKIN_PIE, 2), new ItemStack(Items.COOKIE, 6));

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            sound(SkylinersRegistry.FRIDGE_OPEN.get());
            setOpen(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            sound(SkylinersRegistry.FRIDGE_CLOSE.get());
            setOpen(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int after) {
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == GalleyFridgeBlockEntity.this;
        }
    };

    public GalleyFridgeBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.GALLEY_FRIDGE_ENTITY.get(), pos, state);
    }

    /** Stocks it as the blueprint does, each of its stock in a slot from the first on. */
    public void stock() {
        for (int slot = 0; slot < STOCK.size() && slot < items.size(); slot++) {
            items.set(slot, STOCK.get(slot).copy());
        }
        setChanged();
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
        return Component.translatable("container.skyliners.galley_fridge");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.threeRows(id, inventory, this);
    }

    @Override
    public void startOpen(Player player) {
        if (!remove && !player.isSpectator()) {
            openers.incrementOpeners(player, getLevel(), getBlockPos(), getBlockState());
        }
    }

    @Override
    public void stopOpen(Player player) {
        if (!remove && !player.isSpectator()) {
            openers.decrementOpeners(player, getLevel(), getBlockPos(), getBlockState());
        }
    }

    void recheckOpen() {
        if (!remove) {
            openers.recheckOpeners(getLevel(), getBlockPos(), getBlockState());
        }
    }

    private void setOpen(BlockState state, boolean open) {
        if (level != null) {
            level.setBlock(worldPosition, state.setValue(GalleyFridgeBlock.OPEN, open), 3);
        }
    }

    private void sound(SoundEvent sound) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, VOLUME, 0.95F + level.random.nextFloat() * 0.1F);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items, registries);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items, registries);
        }
    }
}
