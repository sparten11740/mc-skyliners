package dev.skyliners.client;

import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.item.LaserCrossbowItem;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * How the laser crossbow is drawn as it stands (see {@link LaserCrossbowItem}): its model overrides choose by how hot it
 * is, its string drawn back and its lamps green while it is ready, its coil glowing warmer and then hot as it heats,
 * its lamps amber once it is near venting; and by whether it vents, its string then slack and its lamps red (see
 * tools/generate_resources.mjs). How it is held is {@link LaserCrossbowHands}'s, and the heat gauge under the crosshair
 * {@link LaserCrossbowGauge}'s.
 */
public final class LaserCrossbowLooks {
    private static final ResourceLocation HEAT = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "heat");
    private static final ResourceLocation VENTING = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "venting");

    private LaserCrossbowLooks() {
    }

    /** Sets its looks, how it is held and its heat gauge up on this client, from the client's own setup. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(LaserCrossbowLooks::onClientSetup);
        LaserCrossbowHands.register(modEventBus);
        LaserCrossbowGauge.register(modEventBus);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(LaserCrossbowLooks::registerLooks);
    }

    private static void registerLooks() {
        ItemProperties.register(SkylinersRegistry.LASER_CROSSBOW.get(), HEAT,
            (stack, level, entity, seed) -> (float) (LaserCrossbowItem.heat(stack, time(level, entity)) / LaserCrossbowItem.MOST_HEAT));
        ItemProperties.register(SkylinersRegistry.LASER_CROSSBOW.get(), VENTING,
            (stack, level, entity, seed) -> LaserCrossbowItem.venting(stack, time(level, entity)) ? 1 : 0);
    }

    /** The game's time where a crossbow is drawn: its holder's level's, else this client's, as among the slots. */
    private static long time(@Nullable ClientLevel level, @Nullable LivingEntity holder) {
        if (level != null) {
            return level.getGameTime();
        }
        if (holder != null) {
            return holder.level().getGameTime();
        }
        ClientLevel own = Minecraft.getInstance().level;
        return own == null ? 0 : own.getGameTime();
    }
}
