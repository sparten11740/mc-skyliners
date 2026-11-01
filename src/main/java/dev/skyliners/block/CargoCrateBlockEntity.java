package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.entity.FallingCrate;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A care package's contents: two rows of a chest's slots, kept on the crate's item when it is broken, so a packed
 * crate can be carried, loaded and dropped whole, and is the same crate wherever it lands.
 */
public class CargoCrateBlockEntity extends RandomizableContainerBlockEntity {
    private static final int ROWS = 2;

    private NonNullList<ItemStack> items = NonNullList.withSize(ROWS * 9, ItemStack.EMPTY);

    public CargoCrateBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.CARGO_CRATE_ENTITY.get(), pos, state);
    }

    /**
     * Shoves the crate off where it stands, the way given (blocks a tick), as a care package falling free, carried on
     * at the speed of the airliner it was in, if any; the block itself is gone.
     */
    public void launch(Vec3 push, @Nullable UUID dropper) {
        if (level == null) {
            return;
        }
        ItemStack crate = new ItemStack(getBlockState().getBlock());
        crate.applyComponents(collectComponents());
        // Emptied first, so taking the block away drops nothing: the contents go with the package.
        clearContent();
        Vec3 at = SableFlight.toWorld(level, Vec3.atBottomCenterOf(worldPosition));
        Vec3 carried = SableFlight.velocity(level, Vec3.atCenterOf(worldPosition)).scale(1 / 20.0);
        level.removeBlock(worldPosition, false);
        level.addFreshEntity(FallingCrate.of(level, at, carried.add(push), crate, dropper));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8F, 0.7F);
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
        return Component.translatable("container.skyliners.cargo_crate");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new ChestMenu(MenuType.GENERIC_9x2, id, inventory, this, ROWS);
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
