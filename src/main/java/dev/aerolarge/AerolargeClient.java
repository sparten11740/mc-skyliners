package dev.aerolarge;

import dev.aerolarge.block.BeanBagBlock;
import dev.aerolarge.block.BunkBlock;
import dev.aerolarge.block.CabinCarpetBlock;
import dev.aerolarge.block.CabinSeatBlock;
import dev.aerolarge.block.CurtainBlock;
import dev.aerolarge.block.DiscoBallBlockEntity;
import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.block.PassengerScreenBlockEntity;
import dev.aerolarge.block.RubberDuckBlockEntity;
import dev.aerolarge.client.AirstairRenderer;
import dev.aerolarge.client.AtcHeadset;
import dev.aerolarge.client.AutopilotScreenRenderer;
import dev.aerolarge.client.BasketballRenderer;
import dev.aerolarge.client.BlueIceChunkRenderer;
import dev.aerolarge.client.CameraShake;
import dev.aerolarge.client.CarriedAboard;
import dev.aerolarge.client.CloudlessCabin;
import dev.aerolarge.client.CockpitDoorRenderer;
import dev.aerolarge.client.ContrailParticle;
import dev.aerolarge.client.DiscoBallRenderer;
import dev.aerolarge.client.DiscoLights;
import dev.aerolarge.client.DropBayScreen;
import dev.aerolarge.client.DuckHints;
import dev.aerolarge.client.FallingCrateRenderer;
import dev.aerolarge.client.GiantRubberDuckRenderer;
import dev.aerolarge.client.EngineIntakeRenderer;
import dev.aerolarge.client.EngineSounds;
import dev.aerolarge.client.FlareSmokeParticle;
import dev.aerolarge.client.FlightDisplayRenderer;
import dev.aerolarge.client.HelmetVisor;
import dev.aerolarge.client.LiftPanelRenderer;
import dev.aerolarge.client.NavLightRenderer;
import dev.aerolarge.client.NightLights;
import dev.aerolarge.client.PaperPlaneRenderer;
import dev.aerolarge.client.ParachuteControls;
import dev.aerolarge.client.ParachuteLayer;
import dev.aerolarge.client.PassengerCameras;
import dev.aerolarge.client.PassengerScreenRenderer;
import dev.aerolarge.client.PilotHud;
import dev.aerolarge.client.PlugDoorRenderer;
import dev.aerolarge.client.RubberDuckRenderer;
import dev.aerolarge.client.ScoreboardRenderer;
import dev.aerolarge.client.ShellModel;
import dev.aerolarge.client.SlideRenderer;
import dev.aerolarge.client.TabBanners;
import dev.aerolarge.client.TailCameraDisplayRenderer;
import dev.aerolarge.client.TailCameraFeed;
import dev.aerolarge.client.TerrainMap;
import dev.aerolarge.client.TowerRadarRenderer;
import dev.aerolarge.client.TransponderRenderer;
import dev.aerolarge.client.TrolleyRenderer;
import dev.aerolarge.client.VeilLights;
import dev.aerolarge.client.WindSockRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
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
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client side: the model of the skin cut to the hull's curve, the renderers for the airliner's exterior lights,
 * flight displays, autopilot screen, tail camera display, passenger screens, engine intakes, hull doors and
 * airstairs, its engines' sound and contrails, the camera shake aboard, the passenger screens' outside cameras and the
 * tail camera display's live picture, the cabin's blue light at night, the den's disco ball turning and its coloured
 * spots, the clouds kept out of the cabin, parachutes:
 * opening them by jumping, and the canopy over their wearers; the pilot helmet's visor and the pilots' head-up display
 * on it; the radio's headset on screen while air traffic control talks; the creative tab's section banners; the curtains' dye colours; and the settings screen in the Mods menu.
 */
@Mod(value = Aerolarge.MOD_ID, dist = Dist.CLIENT)
public final class AerolargeClient {
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
        NeoForge.EVENT_BUS.addListener(NightLights::frame);
        NeoForge.EVENT_BUS.addListener(NightLights::loggingOut);
        NeoForge.EVENT_BUS.addListener(TerrainMap::loggingOut);
        NeoForge.EVENT_BUS.addListener(DiscoLights::frame);
        NeoForge.EVENT_BUS.addListener(DiscoLights::loggingOut);
        NeoForge.EVENT_BUS.addListener(VeilLights::frame);
        NeoForge.EVENT_BUS.addListener(CloudlessCabin::stage);
        NeoForge.EVENT_BUS.addListener(CloudlessCabin::loggingOut);
        NeoForge.EVENT_BUS.addListener(PilotHud::tick);
        NeoForge.EVENT_BUS.addListener(PilotHud::bossBar);
        NeoForge.EVENT_BUS.addListener(AtcHeadset::heard);
        NeoForge.EVENT_BUS.addListener(CarriedAboard::onTick);
        NeoForge.EVENT_BUS.addListener(AtcHeadset::loggingOut);
        NeoForge.EVENT_BUS.addListener(HelmetVisor::loggingOut);
        modEventBus.addListener(AerolargeClient::onAddLayers);
        modEventBus.addListener(AerolargeClient::onRegisterGeometryLoaders);
        modEventBus.addListener(AerolargeClient::onRegisterRenderers);
        modEventBus.addListener(AerolargeClient::onRegisterParticles);
        modEventBus.addListener(AerolargeClient::onRegisterGuiLayers);
        modEventBus.addListener((RegisterMenuScreensEvent event) -> event.register(AerolargeRegistry.DROP_BAY_MENU.get(), DropBayScreen::new));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(CurtainBlock::tint, AerolargeRegistry.CABIN_CURTAIN.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(CurtainBlock::itemTint, AerolargeRegistry.CABIN_CURTAIN_ITEM.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(CabinSeatBlock::tint,
            AerolargeRegistry.ECONOMY_SEAT.get(), AerolargeRegistry.BUSINESS_SEAT.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(CabinSeatBlock::itemTint,
            AerolargeRegistry.ECONOMY_SEAT_ITEM.get(), AerolargeRegistry.BUSINESS_SEAT_ITEM.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(CabinCarpetBlock::tint,
            AerolargeRegistry.CABIN_CARPET.get(), AerolargeRegistry.AISLE_CARPET.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(CabinCarpetBlock::itemTint,
            AerolargeRegistry.CABIN_CARPET_ITEM.get(), AerolargeRegistry.AISLE_CARPET_ITEM.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(BunkBlock::tint, AerolargeRegistry.BUNK.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(BunkBlock::itemTint, AerolargeRegistry.BUNK_ITEM.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(BeanBagBlock::tint, AerolargeRegistry.BEAN_BAG.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(BeanBagBlock::itemTint, AerolargeRegistry.BEAN_BAG_ITEM.get()));
        EngineCoreBlockEntity.clientTicker = EngineSounds::tick;
        PassengerScreenBlockEntity.clientUse = PassengerCameras::use;
        RubberDuckBlockEntity.clientTicker = DuckHints::tick;
        DiscoBallBlockEntity.clientTicker = DiscoLights::tick;
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "outside_camera"), PassengerCameras::overlay);
        // Under the chat, the titles and the player list, which go over it while they show.
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "pilot_hud"), PilotHud::overlay);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "atc_headset"), AtcHeadset::overlay);
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
        event.registerBlockEntityRenderer(AerolargeRegistry.COCKPIT_DOOR_ENTITY.get(), context -> new CockpitDoorRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.AIRSTAIR_CONTROLLER_ENTITY.get(), context -> new AirstairRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.SLIDE_CONTROL_ENTITY.get(), context -> new SlideRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.TRANSPONDER_ENTITY.get(), context -> new TransponderRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.TOWER_RADAR_ENTITY.get(), context -> new TowerRadarRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.LIFT_PANEL_ENTITY.get(), context -> new LiftPanelRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.WIND_SOCK_ENTITY.get(), context -> new WindSockRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.RUBBER_DUCK_ENTITY.get(), RubberDuckRenderer::new);
        event.registerBlockEntityRenderer(AerolargeRegistry.GIANT_RUBBER_DUCK_ENTITY.get(), GiantRubberDuckRenderer::new);
        event.registerBlockEntityRenderer(AerolargeRegistry.DISCO_BALL_ENTITY.get(), context -> new DiscoBallRenderer());
        event.registerEntityRenderer(AerolargeRegistry.BASKETBALL.get(), BasketballRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.FALLING_CRATE.get(), FallingCrateRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.TROLLEY.get(), TrolleyRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.PAPER_PLANE.get(), PaperPlaneRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.BLUE_ICE_CHUNK.get(), BlueIceChunkRenderer::new);
        // A ride down a slide is only seen in who rides it.
        event.registerEntityRenderer(AerolargeRegistry.SLIDE_RIDE.get(), NoopRenderer::new);
    }
}
