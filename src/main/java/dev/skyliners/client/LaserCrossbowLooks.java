package dev.skyliners.client;

import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.item.LaserCrossbowItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * How the laser crossbow is drawn as it stands (see {@link LaserCrossbowItem}): its model overrides choose by how far
 * it is through charging, its coil glowing brighter in steps that only ever rise while use is held, so it never
 * flickers between them; by whether it is charged, its string then drawn back and glowing near white, its lamps green;
 * and by whether it vents, its lamps then red (see tools/generate_resources.mjs). How it is held is
 * {@link LaserCrossbowHands}'s.
 */
public final class LaserCrossbowLooks {
    private static final ResourceLocation CHARGE = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "charge");
    private static final ResourceLocation CHARGED = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "charged");
    private static final ResourceLocation VENTING = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "venting");

    private LaserCrossbowLooks() {
    }

    /** Sets its looks and how it is held up on this client, from the client's own setup. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(LaserCrossbowLooks::onClientSetup);
        LaserCrossbowHands.register(modEventBus);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(LaserCrossbowLooks::registerLooks);
    }

    private static void registerLooks() {
        ItemProperties.register(SkylinersRegistry.LASER_CROSSBOW.get(), CHARGE,
            (stack, level, entity, seed) -> LaserCrossbowItem.charging(stack, entity, 0));
        ItemProperties.register(SkylinersRegistry.LASER_CROSSBOW.get(), CHARGED,
            (stack, level, entity, seed) -> LaserCrossbowItem.charged(stack) ? 1 : 0);
        ItemProperties.register(SkylinersRegistry.LASER_CROSSBOW.get(), VENTING,
            (stack, level, entity, seed) -> LaserCrossbowItem.heat(stack) >= LaserCrossbowItem.MOST_HEAT ? 1 : 0);
    }
}
