package dev.aerolarge;

import dev.aerolarge.block.CurtainBlock;
import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.block.PassengerScreenBlockEntity;
import dev.aerolarge.client.AutopilotScreenRenderer;
import dev.aerolarge.client.BlueIceChunkRenderer;
import dev.aerolarge.client.CameraShake;
import dev.aerolarge.client.ContrailParticle;
import dev.aerolarge.client.FallingCrateRenderer;
import dev.aerolarge.client.EngineIntakeRenderer;
import dev.aerolarge.client.EngineSounds;
import dev.aerolarge.client.FlareSmokeParticle;
import dev.aerolarge.client.FlightDisplayRenderer;
import dev.aerolarge.client.HelmetVisor;
import dev.aerolarge.client.NavLightRenderer;
import dev.aerolarge.client.PaperPlaneRenderer;
import dev.aerolarge.client.ParachuteControls;
import dev.aerolarge.client.ParachuteLayer;
import dev.aerolarge.client.PassengerCameras;
import dev.aerolarge.client.PassengerScreenRenderer;
import dev.aerolarge.client.PilotHud;
import dev.aerolarge.client.PlugDoorRenderer;
import dev.aerolarge.client.ScoreboardRenderer;
import dev.aerolarge.client.ShellModel;
import dev.aerolarge.client.TabBanners;
import dev.aerolarge.client.TailCameraDisplayRenderer;
import dev.aerolarge.client.TailCameraFeed;
import dev.aerolarge.client.TrolleyRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client side: the model of the skin cut to the hull's curve, the renderers for the airliner's exterior lights,
 * flight displays, autopilot screen, tail camera display, passenger screens, engine intakes and hull doors, its
 * engines' sound and contrails, the camera shake aboard, the passenger screens' outside cameras and the tail camera
 * display's live picture, parachutes: opening them by jumping, and the canopy over their wearers; the pilot helmet's
 * visor and the pilots' head-up display on it; the creative tab's section banners; the curtains' dye colours; and the
 * settings screen in the Mods menu.
 */
@Mod(value = Aerolarge.MOD_ID, dist = Dist.CLIENT)
public final class AerolargeClient {
    /** How big the basketball is drawn, as a share of an item's sprite. */
    private static final float BALL_SIZE = 1.4F;

    public AerolargeClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, AerolargeClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(CameraShake::tick);
        NeoForge.EVENT_BUS.addListener(CameraShake::angles);
        NeoForge.EVENT_BUS.addListener(ParachuteControls::tick);
        NeoForge.EVENT_BUS.addListener(TabBanners::layOut);
        NeoForge.EVENT_BUS.addListener(TabBanners::draw);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::lend);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::tick);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::frame);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::angles);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::fov);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::input);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::movement);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::hand);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::highlight);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::guiLayer);
        NeoForge.EVENT_BUS.addListener(PassengerCameras::loggingOut);
        NeoForge.EVENT_BUS.addListener(TailCameraFeed::frame);
        NeoForge.EVENT_BUS.addListener(TailCameraFeed::loggingOut);
        NeoForge.EVENT_BUS.addListener(PilotHud::tick);
        NeoForge.EVENT_BUS.addListener(HelmetVisor::loggingOut);
        modEventBus.addListener(AerolargeClient::onAddLayers);
        modEventBus.addListener(AerolargeClient::onRegisterGeometryLoaders);
        modEventBus.addListener(AerolargeClient::onRegisterRenderers);
        modEventBus.addListener(AerolargeClient::onRegisterParticles);
        modEventBus.addListener(AerolargeClient::onRegisterGuiLayers);
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(CurtainBlock::tint, AerolargeRegistry.CABIN_CURTAIN.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(CurtainBlock::itemTint, AerolargeRegistry.CABIN_CURTAIN_ITEM.get()));
        EngineCoreBlockEntity.clientTicker = EngineSounds::tick;
        PassengerScreenBlockEntity.clientUse = PassengerCameras::use;
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "outside_camera"), PassengerCameras::overlay);
        // Under the chat, the titles and the player list, which go over it while they show.
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "pilot_hud"), PilotHud::overlay);
        // Over the view as a carved pumpkin's is, under the crosshair and the hotbar.
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "helmet_visor"), HelmetVisor::overlay);
    }

    private static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ShellModel.LOADER, ShellModel.READER);
    }

    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                renderer.addLayer(new ParachuteLayer(renderer));
            }
        }
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AerolargeRegistry.CONTRAIL.get(), ContrailParticle.Provider::new);
        event.registerSpriteSet(AerolargeRegistry.FLARE_SMOKE.get(), FlareSmokeParticle.Provider::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AerolargeRegistry.NAV_LIGHT_ENTITY.get(), context -> new NavLightRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.FLIGHT_DISPLAY_ENTITY.get(), context -> new FlightDisplayRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.AUTOPILOT_SCREEN_ENTITY.get(), context -> new AutopilotScreenRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.SCOREBOARD_ENTITY.get(), context -> new ScoreboardRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.ENGINE_CORE_ENTITY.get(), context -> new EngineIntakeRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.PASSENGER_SCREEN_ENTITY.get(), context -> new PassengerScreenRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.TAIL_CAMERA_DISPLAY_ENTITY.get(), context -> new TailCameraDisplayRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.PLUG_DOOR_ENTITY.get(), context -> new PlugDoorRenderer());
        event.registerEntityRenderer(AerolargeRegistry.BASKETBALL.get(), context -> new ThrownItemRenderer<>(context, BALL_SIZE, false));
        event.registerEntityRenderer(AerolargeRegistry.FALLING_CRATE.get(), FallingCrateRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.TROLLEY.get(), TrolleyRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.PAPER_PLANE.get(), PaperPlaneRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.BLUE_ICE_CHUNK.get(), BlueIceChunkRenderer::new);
    }
}
