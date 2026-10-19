package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.entity.FallingCrate;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What waits in the drop bay: care packages, loaded one at a time from the hold, dropped one at a time when the
 * flight deck releases them, last in first out. A drop leaves just under the belly at the airliner's own speed.
 */
public class DropBayBlockEntity extends BlockEntity {
    /** How many loads the bay holds. */
    public static final int CAPACITY = 6;
    /** How far under the bay's middle a load leaves, in blocks. */
    private static final double DROP_BELOW = 1.2;

    private final List<ItemStack> loads = new ArrayList<>();

    public DropBayBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.DROP_BAY_ENTITY.get(), pos, state);
    }

    /** Whether the bay takes this as a load. */
    public static boolean droppable(ItemStack stack) {
        return stack.is(AerolargeRegistry.CARGO_CRATE_ITEM.get());
    }

    public int count() {
        return loads.size();
    }

    /** Takes one of the stack aboard if there is room; whether it did. */
    public boolean load(ItemStack stack) {
        if (loads.size() >= CAPACITY || !droppable(stack)) {
            return false;
        }
        loads.add(stack.copyWithCount(1));
        setChanged();
        return true;
    }

    /** Hands back the last load taken aboard; empty if there is none. */
    public ItemStack unload() {
        if (loads.isEmpty()) {
            return ItemStack.EMPTY;
        }
        setChanged();
        return loads.removeLast();
    }

    /** Drops the next load, if any, and says what happened. */
    public Component release(@Nullable Player by) {
        if (level == null || loads.isEmpty()) {
            return Component.translatable("message.aerolarge.drop_bay.empty");
        }
        Vec3 middle = Vec3.atCenterOf(worldPosition);
        Vec3 at = SableFlight.toWorld(level, middle.add(0, -DROP_BELOW, 0));
        Vec3 velocity = SableFlight.velocity(level, middle).scale(1 / 20.0);
        ItemStack load = loads.removeLast();
        level.addFreshEntity(FallingCrate.of(level, at, velocity, load, by == null ? null : by.getUUID()));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1.0F, 0.6F);
        setChanged();
        return Component.translatable("message.aerolarge.drop_bay.package_away", loads.size());
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
        ListTag list = new ListTag();
        for (ItemStack load : loads) {
            list.add(load.save(registries));
        }
        tag.put("Loads", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loads.clear();
        for (Tag load : tag.getList("Loads", Tag.TAG_COMPOUND)) {
            ItemStack.parse(registries, load).ifPresent(loads::add);
        }
    }
}
