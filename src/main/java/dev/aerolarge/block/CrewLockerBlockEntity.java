package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What is stowed in a flight crew locker: a row of a chest's slots, room for the pilots' helmets and caps and a little
 * more. Its door swings open, with the clank of a light metal cabinet, while anyone looks in, and shuts behind the
 * last of them.
 */
public class CrewLockerBlockEntity extends RandomizableContainerBlockEntity {
    private static final int SLOTS = 9;
    private static final float VOLUME = 0.6F;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            sound(AerolargeRegistry.CREW_LOCKER_OPEN.get());
            setOpen(state, true);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            sound(AerolargeRegistry.CREW_LOCKER_CLOSE.get());
            setOpen(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int after) {
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == CrewLockerBlockEntity.this;
        }
    };

    public CrewLockerBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.CREW_LOCKER_ENTITY.get(), pos, state);
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
        return Component.translatable("container.aerolarge.crew_locker");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x1, id, inventory, this, 1);
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
            level.setBlock(worldPosition, state.setValue(CrewLockerBlock.OPEN, open), 3);
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
