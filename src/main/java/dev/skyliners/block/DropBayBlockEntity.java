package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What waits in the drop bay: anything, in a dispenser's nine slots, loaded from the hold through its screen (see
 * {@link DropBayMenu}). Each time the flight deck releases it, one item leaves from the first slot that holds any,
 * under the airliner at its own speed (under its wheels, once it is assembled), the way its kind leaves (see
 * {@link BayDrops}).
 */
public class DropBayBlockEntity extends BaseContainerBlockEntity {
    /** How many slots the bay has: a dispenser's three by three. */
    public static final int SLOTS = 9;
    /** How far under the bay's middle a load leaves, in blocks, standing as blocks in the world. */
    private static final double DROP_BELOW = 1.2;
    /**
     * How far under the bottom of an assembled airliner's wheels a load leaves, in blocks: clear of the whole airliner,
     * a load as tall as a crate and all. Leaving at the airliner's speed, a load sweeps forward a few blocks a tick, and
     * just under the belly that swept a falling bomb through the main gear ahead of the bay, where it went off.
     */
    private static final double CLEARANCE = 1.5;
    /**
     * The key a bay saved before it had slots kept its loads under: a list, one item each, the last of them dropped
     * first.
     */
    private static final String OLD_LOADS = "Loads";

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    public DropBayBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.DROP_BAY_ENTITY.get(), pos, state);
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
        return Component.translatable("container.skyliners.drop_bay");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new DropBayMenu(containerId, inventory, this);
    }

    /** Drops one item from the first slot that holds any, if one does, and says what happened. */
    public Component release(@Nullable Player by) {
        ItemStack next = items.stream().filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        if (!(level instanceof ServerLevel server) || next.isEmpty()) {
            return Component.translatable("message.skyliners.drop_bay.empty");
        }
        Vec3 middle = Vec3.atCenterOf(worldPosition);
        double wheels = SableFlight.underside(level, middle);
        double release = Double.isNaN(wheels) ? middle.y - DROP_BELOW : wheels - CLEARANCE;
        Vec3 at = SableFlight.toWorld(level, new Vec3(middle.x, release, middle.z));
        Vec3 velocity = SableFlight.velocity(level, middle).scale(1 / 20.0);
        Component refusal = BayDrops.drop(server, at, velocity, next.copyWithCount(1), by);
        if (refusal != null) {
            return refusal;
        }
        Component name = next.getHoverName();
        next.shrink(1);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1.0F, 0.6F);
        setChanged();
        return Component.translatable("message.skyliners.drop_bay.away", name, left());
    }

    /** How many items are left in the bay, all its slots together. */
    private int left() {
        return items.stream().mapToInt(ItemStack::getCount).sum();
    }

    /** Opens or closes the bay doors. */
    public void setOpen(boolean open) {
        BlockState state = getBlockState();
        if (level != null && state.getValue(DropBayBlock.OPEN) != open) {
            level.setBlock(worldPosition, state.setValue(DropBayBlock.OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        loadOldLoads(tag, registries);
    }

    /**
     * Takes in the loads of a bay saved before it had slots, from the first slot on in the order they would have
     * dropped, so the one that was to go next still does. There were never more than fit.
     */
    private void loadOldLoads(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag loads = tag.getList(OLD_LOADS, Tag.TAG_COMPOUND);
        for (int slot = 0; slot < loads.size() && slot < items.size(); slot++) {
            int into = slot;
            ItemStack.parse(registries, loads.getCompound(loads.size() - 1 - slot)).ifPresent(load -> items.set(into, load));
        }
    }
}
