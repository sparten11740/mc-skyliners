package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.entity.Rover;
import dev.aerolarge.item.RoverRemoteItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The rocket's rover on the client, all in one place so it stays apart from the rest: its renderer and model, its
 * bay's renderer, its motor's sound, its remote's light, which shows green once paired, and driving it from the remote
 * (see {@link RoverDriving}), its picture over the view.
 */
public final class RoverClient {
    /** The remote's light, lit once it is paired with a rover, as its item model goes by. */
    private static final ResourceLocation PAIRED = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "paired");

    private RoverClient() {
    }

    /** Sets the rover up on this client, from the client's own setup. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RoverClient::onRegisterRenderers);
        modEventBus.addListener(RoverClient::onRegisterLayers);
        modEventBus.addListener(RoverClient::onRegisterGuiLayers);
        modEventBus.addListener(RoverClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(RoverDriving::lend);
        NeoForge.EVENT_BUS.addListener(RoverDriving::tick);
        NeoForge.EVENT_BUS.addListener(RoverDriving::frame);
        NeoForge.EVENT_BUS.addListener(RoverDriving::input);
        NeoForge.EVENT_BUS.addListener(RoverDriving::movement);
        NeoForge.EVENT_BUS.addListener(RoverDriving::hand);
        NeoForge.EVENT_BUS.addListener(RoverDriving::highlight);
        NeoForge.EVENT_BUS.addListener(RoverDriving::guiLayer);
        NeoForge.EVENT_BUS.addListener(RoverDriving::loggingOut);
        NeoForge.EVENT_BUS.addListener(RoverSounds::loggingOut);
        Rover.clientTicker = RoverSounds::tick;
        RoverRemoteItem.clientUse = RoverDriving::use;
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AerolargeRegistry.ROVER.get(), RoverRenderer::new);
        event.registerBlockEntityRenderer(AerolargeRegistry.ROVER_BAY_ENTITY.get(), context -> new RoverBayRenderer());
    }

    private static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RoverModel.LAYER, RoverModel::createLayer);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "rover_camera"), RoverDriving::overlay);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(AerolargeRegistry.ROVER_REMOTE.get(), PAIRED,
            (stack, level, entity, seed) -> RoverRemoteItem.pairing(stack) == null ? 0 : 1));
    }
}
