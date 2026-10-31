package dev.aerolarge;

import dev.aerolarge.block.BeanBagBlock;
import dev.aerolarge.block.BunkBlock;
import dev.aerolarge.block.CabinCarpetBlock;
import dev.aerolarge.block.CabinSeatBlock;
import dev.aerolarge.block.CurtainBlock;
import dev.aerolarge.block.DiscoBallBlockEntity;
import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.block.LookoutGlassBlockEntity;
import dev.aerolarge.block.PassengerScreenBlockEntity;
import dev.aerolarge.block.PropellerBlockEntity;
import dev.aerolarge.block.RobotBartenderBlockEntity;
import dev.aerolarge.block.RubberDuckBlockEntity;
import dev.aerolarge.client.AirstairRenderer;
import dev.aerolarge.client.AnnouncementPanelRenderer;
import dev.aerolarge.client.AtcHeadset;
import dev.aerolarge.client.AtisScreenRenderer;
import dev.aerolarge.client.AutopilotRenderer;
import dev.aerolarge.client.AutopilotScreenRenderer;
import dev.aerolarge.client.BasketballRenderer;
import dev.aerolarge.client.BlastFlash;
import dev.aerolarge.client.BlastPuffParticle;
import dev.aerolarge.client.BlueIceChunkRenderer;
import dev.aerolarge.client.BlueprintPreview;
import dev.aerolarge.client.CabinArchModel;
import dev.aerolarge.client.CameraShake;
import dev.aerolarge.client.CarriedAboard;
import dev.aerolarge.client.CloudlessCabin;
import dev.aerolarge.client.CockpitDisplayRenderer;
import dev.aerolarge.client.BubbleLevelRenderer;
import dev.aerolarge.client.CockpitDoorRenderer;
import dev.aerolarge.client.ContrailParticle;
import dev.aerolarge.client.CraneHookRenderer;
import dev.aerolarge.client.CraneWinchRenderer;
import dev.aerolarge.client.DiscoBallRenderer;
import dev.aerolarge.client.DiscoLights;
import dev.aerolarge.client.DoveParticle;
import dev.aerolarge.client.DropBayScreen;
import dev.aerolarge.client.DuckHints;
import dev.aerolarge.client.Earshot;
import dev.aerolarge.client.FallingCrateRenderer;
import dev.aerolarge.client.GiantRubberDuckRenderer;
import dev.aerolarge.client.GreatShadow;
import dev.aerolarge.client.HullAmbience;
import dev.aerolarge.client.EngineRenderer;
import dev.aerolarge.client.EngineSounds;
import dev.aerolarge.client.FlankScreenRenderer;
import dev.aerolarge.client.FlareSmokeParticle;
import dev.aerolarge.client.FlightDisplayRenderer;
import dev.aerolarge.client.FlightMapRenderer;
import dev.aerolarge.client.FlightStripBoardRenderer;
import dev.aerolarge.client.Floodlights;
import dev.aerolarge.client.GearBayRenderer;
import dev.aerolarge.client.HelmRenderer;
import dev.aerolarge.client.HelmetBreathing;
import dev.aerolarge.client.HelmetVisor;
import dev.aerolarge.client.KeptLooks;
import dev.aerolarge.client.LaserBolts;
import dev.aerolarge.client.LaserTurretRenderer;
import dev.aerolarge.client.LedLights;
import dev.aerolarge.client.LiftPanelRenderer;
import dev.aerolarge.client.LightningFlash;
import dev.aerolarge.client.LiveryBoardRenderer;
import dev.aerolarge.client.LiftBoardRenderer;
import dev.aerolarge.client.LiftCarRenderer;
import dev.aerolarge.client.LiftDoorRenderer;
import dev.aerolarge.client.LiftSounds;
import dev.aerolarge.client.LiverySelectorRenderer;
import dev.aerolarge.client.LookoutGlassRenderer;
import dev.aerolarge.client.LookoutView;
import dev.aerolarge.client.MistyWindows;
import dev.aerolarge.client.NavLightRenderer;
import dev.aerolarge.client.NightLights;
import dev.aerolarge.client.OrreryRenderer;
import dev.aerolarge.client.OvalWindowModel;
import dev.aerolarge.client.PaperPlaneRenderer;
import dev.aerolarge.client.ParachuteControls;
import dev.aerolarge.client.ParachuteLayer;
import dev.aerolarge.client.PassengerCameras;
import dev.aerolarge.client.PassengerScreenRenderer;
import dev.aerolarge.client.PilotHud;
import dev.aerolarge.client.PlugDoorRenderer;
import dev.aerolarge.client.PropellerRenderer;
import dev.aerolarge.client.RobotBartenderRenderer;
import dev.aerolarge.client.RocketExhaust;
import dev.aerolarge.client.RocketRoar;
import dev.aerolarge.client.RocketStairsRenderer;
import dev.aerolarge.client.RoomAmbience;
import dev.aerolarge.client.RoverClient;
import dev.aerolarge.client.RubberDuckRenderer;
import dev.aerolarge.client.ScoreboardRenderer;
import dev.aerolarge.client.SearchlightBeams;
import dev.aerolarge.client.SelfDestructButtonRenderer;
import dev.aerolarge.client.SelfDestructWarning;
import dev.aerolarge.client.ShellModel;
import dev.aerolarge.client.ShipsColoursRenderer;
import dev.aerolarge.client.ShipsPlaqueRenderer;
import dev.aerolarge.client.SkyTrails;
import dev.aerolarge.client.SkywritingBoardRenderer;
import dev.aerolarge.client.SleepParticle;
import dev.aerolarge.client.SleighBells;
import dev.aerolarge.client.SlideRenderer;
import dev.aerolarge.client.SpaceSky;
import dev.aerolarge.client.SporeParticle;
import dev.aerolarge.client.TabBanners;
import dev.aerolarge.client.TailCameraDisplayRenderer;
import dev.aerolarge.client.TailCameraFeed;
import dev.aerolarge.client.TardigradeModel;
import dev.aerolarge.client.TardigradeRenderer;
import dev.aerolarge.client.TechnoRoom;
import dev.aerolarge.client.TechnoRoomRenderer;
import dev.aerolarge.client.TerrainMap;
import dev.aerolarge.client.ThrownClear;
import dev.aerolarge.client.TowedBanner;
import dev.aerolarge.client.TowerRadarRenderer;
import dev.aerolarge.client.TransponderRenderer;
import dev.aerolarge.client.TrolleyRenderer;
import dev.aerolarge.client.UpShipScene;
import dev.aerolarge.client.VeilLights;
import dev.aerolarge.client.WindSockRenderer;
import dev.aerolarge.client.ZeppelinEngineSounds;
import dev.aerolarge.client.ZeppelinPaint;
import dev.aerolarge.entity.LiftCar;
import dev.aerolarge.parachute.SafetyCanopy;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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
 * opening them by jumping, and the canopy over their wearers; the helmets' visors, the pilots' head-up display on the
 * pilot helmet's and the space helmet's air in space; the radio's headset on screen while air traffic control talks;
 * the self-destruct's countdown on screen, its button flashing and the blast, its puffs, flash and shake; the creative
 * tab's section banners; the curtains' dye colours; and the settings screen in the Mods menu.
 */
@Mod(value = Aerolarge.MOD_ID, dist = Dist.CLIENT)
public final class AerolargeClient {
    public AerolargeClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, AerolargeClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(CameraShake::tick);
        NeoForge.EVENT_BUS.addListener(CockpitDisplayRenderer::hideOutline);
        NeoForge.EVENT_BUS.addListener(LiftBoardRenderer::hideOutline);
        NeoForge.EVENT_BUS.addListener(LightningFlash::tick);
        NeoForge.EVENT_BUS.addListener(LaserBolts::tick);
        NeoForge.EVENT_BUS.addListener(LaserBolts::stage);
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
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> SafetyCanopy.forget());
        NeoForge.EVENT_BUS.addListener(TerrainMap::loggingOut);
        NeoForge.EVENT_BUS.addListener(DiscoLights::frame);
        NeoForge.EVENT_BUS.addListener(DiscoLights::loggingOut);
        NeoForge.EVENT_BUS.addListener(Floodlights::frame);
        NeoForge.EVENT_BUS.addListener(Floodlights::loggingOut);
        NeoForge.EVENT_BUS.addListener(Floodlights::stage);
        NeoForge.EVENT_BUS.addListener(VeilLights::frame);
        NeoForge.EVENT_BUS.addListener(CloudlessCabin::stage);
        NeoForge.EVENT_BUS.addListener(BlueprintPreview::stage);
        NeoForge.EVENT_BUS.addListener(CloudlessCabin::loggingOut);
        NeoForge.EVENT_BUS.addListener(SpaceSky::sky);
        NeoForge.EVENT_BUS.addListener(SpaceSky::fog);
        // The clouds' colour faded last of all before they are drawn, and put back first of all after the weather.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpaceSky::fadeClouds);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SpaceSky::cloudsFaded);
        NeoForge.EVENT_BUS.addListener(PilotHud::tick);
        NeoForge.EVENT_BUS.addListener(PilotHud::bossBar);
        NeoForge.EVENT_BUS.addListener(AtcHeadset::heard);
        NeoForge.EVENT_BUS.addListener(Earshot::heard);
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
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(ZeppelinPaint::tint,
            AerolargeRegistry.ZEPPELIN_COVER.get(), AerolargeRegistry.ZEPPELIN_FABRIC.get(), AerolargeRegistry.OVAL_WINDOW.get(),
            AerolargeRegistry.ROCKET_LINED_COVER.get(), AerolargeRegistry.ROCKET_LINING_PANEL.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(ZeppelinPaint::itemTint, AerolargeRegistry.ZEPPELIN_FABRIC_ITEM.get()));
        NeoForge.EVENT_BUS.addListener(ZeppelinPaint::tick);
        // The rocket's LED strips: their tint, their remeshing as their colour changes, and their panels' renderers.
        LedLights.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(SleighBells::tick);
        NeoForge.EVENT_BUS.addListener(TechnoRoom::tick);
        NeoForge.EVENT_BUS.addListener(HullAmbience::tick);
        NeoForge.EVENT_BUS.addListener(GreatShadow::tick);
        NeoForge.EVENT_BUS.addListener(GreatShadow::stage);
        NeoForge.EVENT_BUS.addListener(MistyWindows::tick);
        NeoForge.EVENT_BUS.addListener(MistyWindows::stage);
        NeoForge.EVENT_BUS.addListener(MistyWindows::loggingOut);
        NeoForge.EVENT_BUS.addListener(TowedBanner::tick);
        NeoForge.EVENT_BUS.addListener(TowedBanner::stage);
        NeoForge.EVENT_BUS.addListener(TowedBanner::loggingOut);
        NeoForge.EVENT_BUS.addListener(ThrownClear::tick);
        NeoForge.EVENT_BUS.addListener(ThrownClear::loggingOut);
        NeoForge.EVENT_BUS.addListener(TechnoRoom::shake);
        NeoForge.EVENT_BUS.addListener(LookoutView::tick);
        NeoForge.EVENT_BUS.addListener(LookoutView::fov);
        NeoForge.EVENT_BUS.addListener(LookoutView::turn);
        NeoForge.EVENT_BUS.addListener(LookoutView::scroll);
        NeoForge.EVENT_BUS.addListener(LookoutView::input);
        NeoForge.EVENT_BUS.addListener(LookoutView::movement);
        NeoForge.EVENT_BUS.addListener(LookoutView::hand);
        NeoForge.EVENT_BUS.addListener(LookoutView::highlight);
        NeoForge.EVENT_BUS.addListener(LookoutView::guiLayer);
        NeoForge.EVENT_BUS.addListener(LookoutView::loggingOut);
        NeoForge.EVENT_BUS.addListener(UpShipScene::tick);
        NeoForge.EVENT_BUS.addListener(SearchlightBeams::stage);
        NeoForge.EVENT_BUS.addListener(SkyTrails::tick);
        NeoForge.EVENT_BUS.addListener(SkyTrails::stage);
        // A rocket's engines heard as they fire: her roar, its ignition and cut-off, and her touchdown.
        NeoForge.EVENT_BUS.addListener(RocketRoar::tick);
        NeoForge.EVENT_BUS.addListener(RocketExhaust::tick);
        // The space helmet's air, breathed in it aboard a rocket in space.
        NeoForge.EVENT_BUS.addListener(HelmetBreathing::tick);
        // Her greenhouse and her pool heard as one stands in them.
        NeoForge.EVENT_BUS.addListener(RoomAmbience::tick);
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
        PropellerBlockEntity.clientTicker = ZeppelinEngineSounds::tick;
        LiftCar.clientTicker = LiftSounds::tick;
        // The rocket's lab: her tardigrade's model and its renderer.
        modEventBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> event.registerLayerDefinition(TardigradeModel.LAYER,
            TardigradeModel::createBodyLayer));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(AerolargeRegistry.TARDIGRADE.get(),
            TardigradeRenderer::new));
        PassengerScreenBlockEntity.clientUse = PassengerCameras::use;
        LookoutGlassBlockEntity.clientUse = LookoutView::look;
        RoverClient.register(modEventBus);
        KeptLooks.register(modEventBus);
        RubberDuckBlockEntity.clientTicker = DuckHints::tick;
        DiscoBallBlockEntity.clientTicker = DiscoLights::tick;
        RobotBartenderBlockEntity.clientTicker = RobotBartenderRenderer::look;
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "outside_camera"), PassengerCameras::overlay);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "lightning_flash"), LightningFlash::overlay);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "blast_flash"), BlastFlash::overlay);
        // Where the spyglass's own round is drawn, under the hotbar.
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "lookout_glass"), LookoutView::overlay);
        // Under the chat, the titles and the player list, which go over it while they show.
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "pilot_hud"), PilotHud::overlay);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "atc_headset"), AtcHeadset::overlay);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "self_destruct_warning"), SelfDestructWarning::overlay);
        // Over the view as a carved pumpkin's is, under the crosshair and the hotbar.
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "helmet_visor"), HelmetVisor::overlay);
    }

    private static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ShellModel.LOADER, ShellModel.READER);
        event.register(CabinArchModel.LOADER, CabinArchModel.READER);
        event.register(OvalWindowModel.LOADER, OvalWindowModel.READER);
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
        event.registerSpriteSet(AerolargeRegistry.DOVE.get(), DoveParticle.Provider::new);
        event.registerSpriteSet(AerolargeRegistry.BLAST_FIRE.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.FIRE));
        event.registerSpriteSet(AerolargeRegistry.BLAST_SMOKE.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.SMOKE));
        event.registerSpriteSet(AerolargeRegistry.BLAST_WAVE.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.WAVE));
        event.registerSpriteSet(AerolargeRegistry.BLAST_EMBER.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.EMBER));
        event.registerSpriteSet(AerolargeRegistry.ROCKET_EXHAUST.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.EXHAUST));
        event.registerSpriteSet(AerolargeRegistry.ROCKET_CLOUD.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.CLOUD));
        event.registerSpriteSet(AerolargeRegistry.ROCKET_DUST.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.WAVE));
        event.registerSpriteSet(AerolargeRegistry.SLEEP_Z.get(), SleepParticle.Provider::new);
        event.registerSpriteSet(AerolargeRegistry.SPORE.get(), SporeParticle.Provider::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AerolargeRegistry.NAV_LIGHT_ENTITY.get(), context -> new NavLightRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.GEAR_BAY_ENTITY.get(), context -> new GearBayRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.LASER_TURRET_ENTITY.get(), context -> new LaserTurretRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.FLIGHT_DISPLAY_ENTITY.get(), context -> new FlightDisplayRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.AUTOPILOT_SCREEN_ENTITY.get(), context -> new AutopilotScreenRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.COCKPIT_DISPLAY_ENTITY.get(), context -> new CockpitDisplayRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.SCOREBOARD_ENTITY.get(), context -> new ScoreboardRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.ENGINE_CORE_ENTITY.get(), context -> new EngineRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.PROPELLER_ENTITY.get(), context -> new PropellerRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.HELM_ENTITY.get(), context -> new HelmRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.LIVERY_BOARD_ENTITY.get(), context -> new LiveryBoardRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.LIVERY_SELECTOR_ENTITY.get(), context -> new LiverySelectorRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.LIFT_BOARD_ENTITY.get(), context -> new LiftBoardRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.LIFT_DOOR_ENTITY.get(), context -> new LiftDoorRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.FLAG_HALYARD_ENTITY.get(), context -> new ShipsColoursRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.SHIPS_PLAQUE_ENTITY.get(), context -> new ShipsPlaqueRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.PICTURE_BOARD_ENTITY.get(), context -> new FlankScreenRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.BUBBLE_LEVEL_ENTITY.get(), context -> new BubbleLevelRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.CRANE_WINCH_ENTITY.get(), context -> new CraneWinchRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.TECHNO_DECK_ENTITY.get(), context -> new TechnoRoomRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.LOOKOUT_GLASS_ENTITY.get(), context -> new LookoutGlassRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.SKYWRITING_BOARD_ENTITY.get(), context -> new SkywritingBoardRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.AUTOPILOT_ENTITY.get(), AutopilotRenderer::new);
        event.registerBlockEntityRenderer(AerolargeRegistry.PASSENGER_SCREEN_ENTITY.get(), context -> new PassengerScreenRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.TAIL_CAMERA_DISPLAY_ENTITY.get(), context -> new TailCameraDisplayRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.ANNOUNCEMENT_PANEL_ENTITY.get(), context -> new AnnouncementPanelRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.FLIGHT_MAP_ENTITY.get(), context -> new FlightMapRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.SELF_DESTRUCT_BUTTON_ENTITY.get(), context -> new SelfDestructButtonRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.PLUG_DOOR_ENTITY.get(), context -> new PlugDoorRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.COCKPIT_DOOR_ENTITY.get(), context -> new CockpitDoorRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.AIRSTAIR_CONTROLLER_ENTITY.get(), context -> new AirstairRenderer<>());
        event.registerBlockEntityRenderer(AerolargeRegistry.BOARDING_STAIRS_ENTITY.get(), context -> new AirstairRenderer<>());
        event.registerBlockEntityRenderer(AerolargeRegistry.ROCKET_STAIRS_ENTITY.get(), context -> new RocketStairsRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.SLIDE_CONTROL_ENTITY.get(), context -> new SlideRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.TRANSPONDER_ENTITY.get(), context -> new TransponderRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.TOWER_RADAR_ENTITY.get(), context -> new TowerRadarRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.LIFT_PANEL_ENTITY.get(), context -> new LiftPanelRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.WIND_SOCK_ENTITY.get(), context -> new WindSockRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.ATIS_SCREEN_ENTITY.get(), context -> new AtisScreenRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.FLIGHT_STRIP_BOARD_ENTITY.get(), context -> new FlightStripBoardRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.RUBBER_DUCK_ENTITY.get(), RubberDuckRenderer::new);
        event.registerBlockEntityRenderer(AerolargeRegistry.GIANT_RUBBER_DUCK_ENTITY.get(), GiantRubberDuckRenderer::new);
        event.registerBlockEntityRenderer(AerolargeRegistry.DISCO_BALL_ENTITY.get(), context -> new DiscoBallRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.ORRERY_ENTITY.get(), context -> new OrreryRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.ROBOT_BARTENDER_ENTITY.get(), RobotBartenderRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.BASKETBALL.get(), BasketballRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.FALLING_CRATE.get(), FallingCrateRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.CRANE_HOOK.get(), CraneHookRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.LIFT_CAR.get(), LiftCarRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.TROLLEY.get(), TrolleyRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.PAPER_PLANE.get(), PaperPlaneRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.BLUE_ICE_CHUNK.get(), BlueIceChunkRenderer::new);
        // A ride down a slide is only seen in who rides it.
        event.registerEntityRenderer(AerolargeRegistry.SLIDE_RIDE.get(), NoopRenderer::new);
    }
}

