package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What is stowed in a galley unit: a chest's three rows of slots, as much as the cupboard it stands in for in the
 * galley held. Its latch clacks open as the first one looks in, and shut behind the last.
 */
public class GalleyUnitBlockEntity extends RandomizableContainerBlockEntity {
    private static final int ROWS = 3;
    /** The latch's clack: an iron trapdoor's, quieter and higher, a small metal catch. */
    private static final float VOLUME = 0.5F;
    private static final float PITCH = 1.4F;

    private NonNullList<ItemStack> items = NonNullList.withSize(ROWS * 9, ItemStack.EMPTY);
    private final ContainerOpenersCounter openers = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState state) {
            sound(SoundEvents.IRON_TRAPDOOR_OPEN);
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState state) {
            sound(SoundEvents.IRON_TRAPDOOR_CLOSE);
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState state, int before, int after) {
        }

        @Override
        protected boolean isOwnContainer(Player player) {
            return player.containerMenu instanceof ChestMenu menu && menu.getContainer() == GalleyUnitBlockEntity.this;
        }
    };

    public GalleyUnitBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.GALLEY_UNIT_ENTITY.get(), pos, state);
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
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
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

    private void sound(SoundEvent sound) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, VOLUME, PITCH + level.random.nextFloat() * 0.1F);
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
