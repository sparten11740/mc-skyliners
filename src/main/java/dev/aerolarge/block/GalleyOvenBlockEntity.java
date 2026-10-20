package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a galley oven is heating: the smoker's slots and its cooking, food only, in half a furnace's time, as the
 * smoker it stands in for in the galley does, fuel burning twice as fast to match. Opened, it shows the smoker's screen.
 */
public class GalleyOvenBlockEntity extends AbstractFurnaceBlockEntity {
    public GalleyOvenBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.GALLEY_OVEN_ENTITY.get(), pos, state, RecipeType.SMOKING);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected int getBurnDuration(ItemStack fuel) {
        return super.getBurnDuration(fuel) / 2;
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return new SmokerMenu(id, inventory, this, dataAccess);
    }
}
