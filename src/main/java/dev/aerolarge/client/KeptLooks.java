package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BulkheadSignBlock;
import dev.aerolarge.block.GirderDecorBlock;
import dev.aerolarge.block.HabitatFloorBlock;
import dev.aerolarge.block.LabInstrumentBlock;
import dev.aerolarge.block.LabPartitionBlock;
import dev.aerolarge.block.NeonSignBlock;
import dev.aerolarge.block.OverheadWcSignBlock;
import dev.aerolarge.block.PalletRackBlock;
import dev.aerolarge.block.PlanterBlock;
import dev.aerolarge.block.ShelfUnitBlock;
import java.util.List;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Items that keep what their block showed when it was broken or picked, a neon sign its picture, a planter its plant, a
 * girder decoration its kind and which of a pair it is, a bulkhead sign its picture, an overhead WC sign its arrow, a
 * shelf unit what is on it, a pallet rack its load, a lab instrument its kind, a lab partition its row and a patch of
 * tank floor its kind, drawn in the hand and the inventory as what they keep. Each look is an item property named
 * after the block state property, which the item's model overrides choose by (see tools/generate_resources.mjs, whose
 * lists of each property's values must keep the enum's order): the share of the way through the property's values its
 * value is, taken at the middle of its step so that no rounding puts it on the wrong side of an override's threshold.
 */
public final class KeptLooks {
    private KeptLooks() {
    }

    /** Sets the looks up on this client, from the client's own setup. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(KeptLooks::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(KeptLooks::registerLooks);
    }

    private static void registerLooks() {
        look(AerolargeRegistry.NEON_SIGN_ITEM.get(), NeonSignBlock.DESIGN);
        look(AerolargeRegistry.PLANTER_ITEM.get(), PlanterBlock.PLANT);
        look(AerolargeRegistry.GIRDER_DECOR_ITEM.get(), GirderDecorBlock.KIND);
        look(AerolargeRegistry.GIRDER_DECOR_ITEM.get(), GirderDecorBlock.PART);
        look(AerolargeRegistry.BULKHEAD_SIGN_ITEM.get(), BulkheadSignBlock.PICTURE);
        look(AerolargeRegistry.OVERHEAD_WC_SIGN_ITEM.get(), OverheadWcSignBlock.ARROW);
        look(AerolargeRegistry.SHELF_UNIT_ITEM.get(), ShelfUnitBlock.KIND);
        look(AerolargeRegistry.PALLET_RACK_ITEM.get(), PalletRackBlock.LOAD);
        look(AerolargeRegistry.LAB_INSTRUMENT_ITEM.get(), LabInstrumentBlock.KIND);
        look(AerolargeRegistry.LAB_PARTITION_ITEM.get(), LabPartitionBlock.ROW);
        look(AerolargeRegistry.HABITAT_FLOOR_ITEM.get(), HabitatFloorBlock.KIND);
    }

    /** The item looks as the value of the block state property it keeps (see {@link #share}). */
    private static <T extends Comparable<T>> void look(BlockItem item, Property<T> property) {
        List<T> values = List.copyOf(property.getPossibleValues());
        T plain = item.getBlock().defaultBlockState().getValue(property);
        ResourceLocation name = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, property.getName());
        ItemProperties.register(item, name, (stack, level, entity, seed) -> share(stack, property, values, plain));
    }

    /**
     * How far through a property's values a stack's kept value is, at the middle of its step: the value it keeps, or
     * its block's own default where it keeps none, as a stack fresh from the creative tab does.
     */
    private static <T extends Comparable<T>> float share(ItemStack stack, Property<T> property, List<T> values, T plain) {
        T kept = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(property);
        return (values.indexOf(kept == null ? plain : kept) + 0.5F) / values.size();
    }
}
