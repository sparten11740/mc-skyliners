package dev.skyliners.client;

import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.item.LaserCrossbowItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * How the laser crossbow is drawn as it stands (see {@link LaserCrossbowItem}): its model overrides choose by whether it
 * is charged, its coil then glowing bright, and whether it vents, its lamps then red (see tools/generate_resources.mjs).
 */
public final class LaserCrossbowLooks {
    private static final ResourceLocation CHARGED = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "charged");
    private static final ResourceLocation VENTING = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "venting");

    private LaserCrossbowLooks() {
    }

    /** Sets its looks up on this client, from the client's own setup. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(LaserCrossbowLooks::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(LaserCrossbowLooks::registerLooks);
    }

    private static void registerLooks() {
        ItemProperties.register(SkylinersRegistry.LASER_CROSSBOW.get(), CHARGED,
            (stack, level, entity, seed) -> LaserCrossbowItem.charged(stack) ? 1 : 0);
        ItemProperties.register(SkylinersRegistry.LASER_CROSSBOW.get(), VENTING,
            (stack, level, entity, seed) -> LaserCrossbowItem.heat(stack) >= LaserCrossbowItem.MOST_HEAT ? 1 : 0);
    }
}
