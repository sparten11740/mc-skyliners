package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** What is stowed in an overhead locker: two rows of a chest's slots, a cabin bag's worth. */
public class OverheadBinBlockEntity extends RandomizableContainerBlockEntity {
    private static final int ROWS = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(ROWS * 9, ItemStack.EMPTY);
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            sound(SoundEvents.BARREL_OPEN);
            setOpen(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            sound(SoundEvents.BARREL_CLOSE);
            setOpen(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int after) {
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == OverheadBinBlockEntity.this;
        }
    };

    public OverheadBinBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.OVERHEAD_BIN_ENTITY.get(), pos, state);
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
        return Component.translatable("container.aerolarge.overhead_bin");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x2, id, inventory, this, ROWS);
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
            level.setBlock(worldPosition, state.setValue(OverheadBinBlock.OPEN, open), 3);
        }
    }

    private void sound(SoundEvent sound) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.5F, 1.3F + level.random.nextFloat() * 0.1F);
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
