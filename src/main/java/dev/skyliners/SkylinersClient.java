package dev.skyliners;

import dev.skyliners.block.BeanBagBlock;
import dev.skyliners.block.BunkBlock;
import dev.skyliners.block.CabinCarpetBlock;
import dev.skyliners.block.CabinSeatBlock;
import dev.skyliners.block.CurtainBlock;
import dev.skyliners.block.DeckRobotBlockEntity;
import dev.skyliners.block.DiscoBallBlockEntity;
import dev.skyliners.block.EngineCoreBlockEntity;
import dev.skyliners.block.FogMachineBlockEntity;
import dev.skyliners.block.LaserBolt;
import dev.skyliners.block.LookoutGlassBlockEntity;
import dev.skyliners.block.PassengerScreenBlockEntity;
import dev.skyliners.block.PropellerBlockEntity;
import dev.skyliners.block.RobotBartenderBlockEntity;
import dev.skyliners.block.RubberDuckBlockEntity;
import dev.skyliners.client.AirstairRenderer;
import dev.skyliners.client.AnnouncementPanelRenderer;
import dev.skyliners.client.AtcHeadset;
import dev.skyliners.client.AtisScreenRenderer;
import dev.skyliners.client.AutopilotRenderer;
import dev.skyliners.client.AutopilotScreenRenderer;
import dev.skyliners.client.BandstandRenderer;
import dev.skyliners.client.BasketballRenderer;
import dev.skyliners.client.BlastFlash;
import dev.skyliners.client.BlastPuffParticle;
import dev.skyliners.client.BlueIceChunkRenderer;
import dev.skyliners.client.BlueprintPreview;
import dev.skyliners.client.BoardingRampRenderer;
import dev.skyliners.client.CabinArchModel;
import dev.skyliners.client.CameraShake;
import dev.skyliners.client.CannonRocketRenderer;
import dev.skyliners.client.CannonRockets;
import dev.skyliners.client.CannonServo;
import dev.skyliners.client.CanopyLiningRenderer;
import dev.skyliners.client.CarriedAboard;
import dev.skyliners.client.CarriedParticles;
import dev.skyliners.client.CloudlessCabin;
import dev.skyliners.client.ClubBand;
import dev.skyliners.client.ConsoleRenderer;
import dev.skyliners.client.CockpitDisplayRenderer;
import dev.skyliners.client.BubbleLevelRenderer;
import dev.skyliners.client.CockpitDoorRenderer;
import dev.skyliners.client.ContrailParticle;
import dev.skyliners.client.CraneHookRenderer;
import dev.skyliners.client.CraneWinchRenderer;
import dev.skyliners.client.DiscoBallRenderer;
import dev.skyliners.client.DiscoLights;
import dev.skyliners.client.DoveParticle;
import dev.skyliners.client.DrawingClock;
import dev.skyliners.client.DropBayScreen;
import dev.skyliners.client.DuckHints;
import dev.skyliners.client.Earshot;
import dev.skyliners.client.FallingCrateRenderer;
import dev.skyliners.client.GiantRubberDuckRenderer;
import dev.skyliners.client.GreatShadow;
import dev.skyliners.client.GunnerControls;
import dev.skyliners.client.GunnerSight;
import dev.skyliners.client.GunnerView;
import dev.skyliners.client.HullAmbience;
import dev.skyliners.client.EngineRenderer;
import dev.skyliners.client.EngineSounds;
import dev.skyliners.client.FlankScreenRenderer;
import dev.skyliners.client.FlareSmokeParticle;
import dev.skyliners.client.FlightDisplayRenderer;
import dev.skyliners.client.FlightMapRenderer;
import dev.skyliners.client.FlightStripBoardRenderer;
import dev.skyliners.client.Floodlights;
import dev.skyliners.client.FogMachineSounds;
import dev.skyliners.client.FreighterEngineGlow;
import dev.skyliners.client.FreighterRoar;
import dev.skyliners.client.DeckRobotRenderer;
import dev.skyliners.client.GearBayRenderer;
import dev.skyliners.client.HelmRenderer;
import dev.skyliners.client.HelmetBreathing;
import dev.skyliners.client.HelmetVisor;
import dev.skyliners.client.HyperspaceOverlay;
import dev.skyliners.client.KeptLooks;
import dev.skyliners.client.LaserBolts;
import dev.skyliners.client.LaserCannonRenderer;
import dev.skyliners.client.LaserCrossbowLooks;
import dev.skyliners.client.LaserTurretRenderer;
import dev.skyliners.client.LedLights;
import dev.skyliners.client.LegBayRenderer;
import dev.skyliners.client.LiftPanelRenderer;
import dev.skyliners.client.LiftRide;
import dev.skyliners.client.LightningFlash;
import dev.skyliners.client.LiveryBoardRenderer;
import dev.skyliners.client.LiftBoardRenderer;
import dev.skyliners.client.LiftCarRenderer;
import dev.skyliners.client.LiftDoorRenderer;
import dev.skyliners.client.LiftSounds;
import dev.skyliners.client.LiverySelectorRenderer;
import dev.skyliners.client.LookoutGlassRenderer;
import dev.skyliners.client.LookoutView;
import dev.skyliners.client.MistyWindows;
import dev.skyliners.client.NavLightRenderer;
import dev.skyliners.client.NightLights;
import dev.skyliners.client.OculusRing;
import dev.skyliners.client.OrreryRenderer;
import dev.skyliners.client.OvalWindowModel;
import dev.skyliners.client.OvalWindowWall;
import dev.skyliners.client.PaperPlaneRenderer;
import dev.skyliners.client.ParachuteControls;
import dev.skyliners.client.ParachuteLayer;
import dev.skyliners.client.PassengerCameras;
import dev.skyliners.client.PassengerScreenRenderer;
import dev.skyliners.client.PilotHud;
import dev.skyliners.client.PlugDoorRenderer;
import dev.skyliners.client.PropellerRenderer;
import dev.skyliners.client.RobotBartenderRenderer;
import dev.skyliners.client.RocketExhaust;
import dev.skyliners.client.RocketRoar;
import dev.skyliners.client.RocketStairsRenderer;
import dev.skyliners.client.RoomAmbience;
import dev.skyliners.client.RoverClient;
import dev.skyliners.client.RoverPanelRenderer;
import dev.skyliners.client.RubberDuckRenderer;
import dev.skyliners.client.SatelliteDishRenderer;
import dev.skyliners.client.ScoreboardRenderer;
import dev.skyliners.client.SearchlightBeams;
import dev.skyliners.client.SelfDestructButtonRenderer;
import dev.skyliners.client.SelfDestructWarning;
import dev.skyliners.client.ShellModel;
import dev.skyliners.client.ShipWeather;
import dev.skyliners.client.ShipsColoursRenderer;
import dev.skyliners.client.ShipsPlaqueRenderer;
import dev.skyliners.client.SkyTrails;
import dev.skyliners.client.SkywritingBoardRenderer;
import dev.skyliners.client.SleepParticle;
import dev.skyliners.client.SleighBells;
import dev.skyliners.client.SlideRenderer;
import dev.skyliners.client.SpaceSky;
import dev.skyliners.client.BreakerBoardRenderer;
import dev.skyliners.client.CoolantValveRenderer;
import dev.skyliners.client.FloorWindowRenderer;
import dev.skyliners.client.CrossbowRackRenderer;
import dev.skyliners.client.JumpDriveRenderer;
import dev.skyliners.client.SpookieModel;
import dev.skyliners.client.SpookieRenderer;
import dev.skyliners.client.SporeParticle;
import dev.skyliners.client.SprinklerRenderer;
import dev.skyliners.client.TabBanners;
import dev.skyliners.client.TailCameraDisplayRenderer;
import dev.skyliners.client.TailCameraFeed;
import dev.skyliners.client.TouchPanelArt;
import dev.skyliners.client.TardigradeModel;
import dev.skyliners.client.TardigradeRenderer;
import dev.skyliners.client.TechnoRoom;
import dev.skyliners.client.TechnoRoomRenderer;
import dev.skyliners.client.TerrainMap;
import dev.skyliners.client.ThrownClear;
import dev.skyliners.client.TowedBanner;
import dev.skyliners.client.TowerRadarRenderer;
import dev.skyliners.client.TransponderRenderer;
import dev.skyliners.client.TrolleyRenderer;
import dev.skyliners.client.UpShipScene;
import dev.skyliners.client.VeilLights;
import dev.skyliners.client.WindSockRenderer;
import dev.skyliners.client.WindowDimmerRenderer;
import dev.skyliners.client.ZeppelinEngineSounds;
import dev.skyliners.client.ZeppelinPaint;
import dev.skyliners.entity.LiftCar;
import dev.skyliners.entity.ShipParticles;
import dev.skyliners.parachute.SafetyCanopy;
import dev.skyliners.turret.CannonRocket;
import dev.skyliners.turret.LaserCannonBlockEntity;
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
@Mod(value = Skyliners.MOD_ID, dist = Dist.CLIENT)
public final class SkylinersClient {
    public SkylinersClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SkylinersClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(DrawingClock::tick);
        NeoForge.EVENT_BUS.addListener(CameraShake::tick);
        NeoForge.EVENT_BUS.addListener(CockpitDisplayRenderer::hideOutline);
        NeoForge.EVENT_BUS.addListener(TouchPanelArt::hideOutline);
        NeoForge.EVENT_BUS.addListener(LightningFlash::tick);
        NeoForge.EVENT_BUS.addListener(LaserBolts::tick);
        NeoForge.EVENT_BUS.addListener(CarriedParticles::tick);
        ShipParticles.spawner = CarriedParticles::spawn;
        NeoForge.EVENT_BUS.addListener(LaserBolts::stage);
        LaserBolt.clientSeen = LaserBolts::seen;
        NeoForge.EVENT_BUS.addListener(FreighterEngineGlow::stage);
        NeoForge.EVENT_BUS.addListener(GunnerControls::tick);
        NeoForge.EVENT_BUS.addListener(ClubBand::tick);
        NeoForge.EVENT_BUS.addListener(GunnerControls::input);
        // The view from the freighter's laser cannon's sight while its gunner sits at it.
        NeoForge.EVENT_BUS.addListener(GunnerView::tick);
        NeoForge.EVENT_BUS.addListener(GunnerView::frame);
        NeoForge.EVENT_BUS.addListener(GunnerView::angles);
        NeoForge.EVENT_BUS.addListener(GunnerView::hand);
        NeoForge.EVENT_BUS.addListener(GunnerView::highlight);
        NeoForge.EVENT_BUS.addListener(GunnerView::guiLayer);
        NeoForge.EVENT_BUS.addListener(GunnerView::loggingOut);
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
        NeoForge.EVENT_BUS.addListener(ShipWeather::strikeBelow);
        NeoForge.EVENT_BUS.addListener(SpaceSky::sky);
        NeoForge.EVENT_BUS.addListener(SpaceSky::fog);
        // The clouds' colour faded last of all before they are drawn, and put back first of all after the weather.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SpaceSky::fadeClouds);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, SpaceSky::cloudsFaded);
        NeoForge.EVENT_BUS.addListener(PilotHud::tick);
        NeoForge.EVENT_BUS.addListener(PilotHud::bossBar);
        NeoForge.EVENT_BUS.addListener(AtcHeadset::heard);
        NeoForge.EVENT_BUS.addListener(OculusRing::heard);
        NeoForge.EVENT_BUS.addListener(Earshot::heard);
        NeoForge.EVENT_BUS.addListener(CarriedAboard::onTick);
        NeoForge.EVENT_BUS.addListener(LiftRide::onLevelTick);
        NeoForge.EVENT_BUS.addListener(LiftRide::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(AtcHeadset::loggingOut);
        NeoForge.EVENT_BUS.addListener(HelmetVisor::loggingOut);
        modEventBus.addListener(SkylinersClient::onAddLayers);
        modEventBus.addListener(SkylinersClient::onRegisterGeometryLoaders);
        modEventBus.addListener(SkylinersClient::onRegisterRenderers);
        modEventBus.addListener(SkylinersClient::onRegisterParticles);
        modEventBus.addListener(SkylinersClient::onRegisterGuiLayers);
        modEventBus.addListener((RegisterMenuScreensEvent event) -> event.register(SkylinersRegistry.DROP_BAY_MENU.get(), DropBayScreen::new));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(CurtainBlock::tint, SkylinersRegistry.CABIN_CURTAIN.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(ZeppelinPaint::tint,
            SkylinersRegistry.ZEPPELIN_COVER.get(), SkylinersRegistry.ZEPPELIN_FABRIC.get(), SkylinersRegistry.ROCKET_LINED_COVER.get(),
            SkylinersRegistry.ROCKET_LINING_PANEL.get(), SkylinersRegistry.CANOPY_FRAME.get(), SkylinersRegistry.FREIGHTER_COVER.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(OvalWindowWall::tint,
            SkylinersRegistry.OVAL_WINDOW.get(), SkylinersRegistry.FLAT_OVAL_WINDOW.get(), SkylinersRegistry.PORTHOLE.get(),
            SkylinersRegistry.FLAT_PORTHOLE.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(ZeppelinPaint::itemTint, SkylinersRegistry.ZEPPELIN_FABRIC_ITEM.get(),
            SkylinersRegistry.FREIGHTER_COVER_ITEM.get()));
        NeoForge.EVENT_BUS.addListener(ZeppelinPaint::tick);
        // A freighter's hyperspace jump: her computer's last word as she comes out, and the view widening as she goes.
        NeoForge.EVENT_BUS.addListener(HyperspaceOverlay::tick);
        NeoForge.EVENT_BUS.addListener(HyperspaceOverlay::widen);
        NeoForge.EVENT_BUS.addListener(HyperspaceOverlay::world);
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
        NeoForge.EVENT_BUS.addListener(FreighterRoar::tick);
        NeoForge.EVENT_BUS.addListener(RocketExhaust::tick);
        // The space helmet's air, breathed in it aboard a rocket in space.
        NeoForge.EVENT_BUS.addListener(HelmetBreathing::tick);
        // Her greenhouse and her pool heard as one stands in them.
        NeoForge.EVENT_BUS.addListener(RoomAmbience::tick);
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(CurtainBlock::itemTint, SkylinersRegistry.CABIN_CURTAIN_ITEM.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(CabinSeatBlock::tint,
            SkylinersRegistry.ECONOMY_SEAT.get(), SkylinersRegistry.BUSINESS_SEAT.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(CabinSeatBlock::itemTint,
            SkylinersRegistry.ECONOMY_SEAT_ITEM.get(), SkylinersRegistry.BUSINESS_SEAT_ITEM.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(CabinCarpetBlock::tint,
            SkylinersRegistry.CABIN_CARPET.get(), SkylinersRegistry.AISLE_CARPET.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(CabinCarpetBlock::itemTint,
            SkylinersRegistry.CABIN_CARPET_ITEM.get(), SkylinersRegistry.AISLE_CARPET_ITEM.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(BunkBlock::tint, SkylinersRegistry.BUNK.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(BunkBlock::itemTint, SkylinersRegistry.BUNK_ITEM.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(BeanBagBlock::tint, SkylinersRegistry.BEAN_BAG.get()));
        modEventBus.addListener((RegisterColorHandlersEvent.Item event) -> event.register(BeanBagBlock::itemTint, SkylinersRegistry.BEAN_BAG_ITEM.get()));
        EngineCoreBlockEntity.clientTicker = EngineSounds::tick;
        PropellerBlockEntity.clientTicker = ZeppelinEngineSounds::tick;
        FogMachineBlockEntity.clientHiss = FogMachineSounds::hiss;
        LiftCar.clientTicker = LiftSounds::tick;
        // The rocket's lab: her tardigrade's model and its renderer.
        modEventBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> event.registerLayerDefinition(TardigradeModel.LAYER,
            TardigradeModel::createBodyLayer));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(SkylinersRegistry.TARDIGRADE.get(),
            TardigradeRenderer::new));
        // The freighter's Spookie: its model and its renderer.
        modEventBus.addListener((EntityRenderersEvent.RegisterLayerDefinitions event) -> event.registerLayerDefinition(SpookieModel.LAYER,
            SpookieModel::createBodyLayer));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerEntityRenderer(SkylinersRegistry.SPOOKIE.get(),
            SpookieRenderer::new));
        // The freighter's jump drive: its rings and core over its plinth; and her breaker board's switches.
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerBlockEntityRenderer(SkylinersRegistry.JUMP_DRIVE_ENTITY.get(),
            context -> new JumpDriveRenderer()));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerBlockEntityRenderer(
            SkylinersRegistry.BREAKER_BOARD_ENTITY.get(), context -> new BreakerBoardRenderer()));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerBlockEntityRenderer(
            SkylinersRegistry.COOLANT_VALVE_ENTITY.get(), context -> new CoolantValveRenderer()));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerBlockEntityRenderer(
            SkylinersRegistry.FLOOR_WINDOW_ENTITY.get(), context -> new FloorWindowRenderer()));
        modEventBus.addListener((EntityRenderersEvent.RegisterRenderers event) -> event.registerBlockEntityRenderer(
            SkylinersRegistry.CROSSBOW_RACK_ENTITY.get(), context -> new CrossbowRackRenderer()));
        PassengerScreenBlockEntity.clientUse = PassengerCameras::use;
        LookoutGlassBlockEntity.clientUse = LookoutView::look;
        RoverClient.register(modEventBus);
        KeptLooks.register(modEventBus);
        LaserCrossbowLooks.register(modEventBus);
        RubberDuckBlockEntity.clientTicker = DuckHints::tick;
        DiscoBallBlockEntity.clientTicker = DiscoLights::tick;
        RobotBartenderBlockEntity.clientTicker = RobotBartenderRenderer::look;
        DeckRobotBlockEntity.clientTicker = DeckRobotRenderer::look;
        LaserCannonBlockEntity.clientTicker = CannonServo::listen;
        LaserCannonBlockEntity.clientSighting = GunnerView::sighting;
        CannonRocket.clientTicker = CannonRockets::tick;
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "outside_camera"), PassengerCameras::overlay);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "lightning_flash"), LightningFlash::overlay);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "blast_flash"), BlastFlash::overlay);
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "hyperspace_jump"),
            HyperspaceOverlay::overlay);
        // Where the spyglass's own round is drawn, under the hotbar.
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "lookout_glass"), LookoutView::overlay);
        // The gunner's sight, over the view from the freighter's laser cannon and under the hotbar and the chat.
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "gunner_sight"), GunnerSight::overlay);
        // Under the chat, the titles and the player list, which go over it while they show.
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "pilot_hud"), PilotHud::overlay);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "atc_headset"), AtcHeadset::overlay);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "self_destruct_warning"), SelfDestructWarning::overlay);
        // Over the view as a carved pumpkin's is, under the crosshair and the hotbar.
        event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "helmet_visor"), HelmetVisor::overlay);
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
        event.registerSpriteSet(SkylinersRegistry.CONTRAIL.get(), ContrailParticle.Provider::new);
        event.registerSpriteSet(SkylinersRegistry.FLARE_SMOKE.get(), FlareSmokeParticle.Provider::new);
        event.registerSpriteSet(SkylinersRegistry.DOVE.get(), DoveParticle.Provider::new);
        event.registerSpriteSet(SkylinersRegistry.BLAST_FIRE.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.FIRE));
        event.registerSpriteSet(SkylinersRegistry.BLAST_SMOKE.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.SMOKE));
        event.registerSpriteSet(SkylinersRegistry.BLAST_WAVE.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.WAVE));
        event.registerSpriteSet(SkylinersRegistry.BLAST_EMBER.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.EMBER));
        event.registerSpriteSet(SkylinersRegistry.ROCKET_EXHAUST.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.EXHAUST));
        event.registerSpriteSet(SkylinersRegistry.ROCKET_CLOUD.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.CLOUD));
        event.registerSpriteSet(SkylinersRegistry.ROCKET_DUST.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.WAVE));
        event.registerSpriteSet(SkylinersRegistry.FOG_PUFF.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.FOG));
        event.registerSpriteSet(SkylinersRegistry.CANNON_ROCKET_TRAIL.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.TRAIL));
        event.registerSpriteSet(SkylinersRegistry.CANNON_ROCKET_FLAME.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.FLAME));
        event.registerSpriteSet(SkylinersRegistry.CANNON_ROCKET_FIREBALL.get(),
            sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.FIREBALL));
        event.registerSpriteSet(SkylinersRegistry.CANNON_ROCKET_SOOT.get(), sprites -> new BlastPuffParticle.Provider(sprites, BlastPuffParticle.Kind.SOOT));
        event.registerSpriteSet(SkylinersRegistry.SLEEP_Z.get(), SleepParticle.Provider::new);
        event.registerSpriteSet(SkylinersRegistry.SPORE.get(), SporeParticle.Provider::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SkylinersRegistry.NAV_LIGHT_ENTITY.get(), context -> new NavLightRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.DECK_ROBOT_ENTITY.get(), DeckRobotRenderer::new);
        event.registerBlockEntityRenderer(SkylinersRegistry.GEAR_BAY_ENTITY.get(), context -> new GearBayRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.LEG_BAY_ENTITY.get(), context -> new LegBayRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.LASER_TURRET_ENTITY.get(), context -> new LaserTurretRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.FLIGHT_DISPLAY_ENTITY.get(), context -> new FlightDisplayRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.AUTOPILOT_SCREEN_ENTITY.get(), context -> new AutopilotScreenRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.COCKPIT_DISPLAY_ENTITY.get(), context -> new CockpitDisplayRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.SCOREBOARD_ENTITY.get(), context -> new ScoreboardRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.ENGINE_CORE_ENTITY.get(), context -> new EngineRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.PROPELLER_ENTITY.get(), context -> new PropellerRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.HELM_ENTITY.get(), context -> new HelmRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.LIVERY_BOARD_ENTITY.get(), context -> new LiveryBoardRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.LIVERY_SELECTOR_ENTITY.get(), context -> new LiverySelectorRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.LIFT_BOARD_ENTITY.get(), context -> new LiftBoardRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.ROVER_PANEL_ENTITY.get(), context -> new RoverPanelRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.RAMP_PANEL_ENTITY.get(), context -> new BoardingRampRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.LASER_CANNON_ENTITY.get(), context -> new LaserCannonRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.SATELLITE_DISH_ENTITY.get(), context -> new SatelliteDishRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.BANDSTAND_ENTITY.get(), context -> new BandstandRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.WINDOW_DIMMER_ENTITY.get(), context -> new WindowDimmerRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.LIFT_DOOR_ENTITY.get(), context -> new LiftDoorRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.FLAG_HALYARD_ENTITY.get(), context -> new ShipsColoursRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.SHIPS_PLAQUE_ENTITY.get(), context -> new ShipsPlaqueRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.PICTURE_BOARD_ENTITY.get(), context -> new FlankScreenRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.BUBBLE_LEVEL_ENTITY.get(), context -> new BubbleLevelRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.CRANE_WINCH_ENTITY.get(), context -> new CraneWinchRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.TECHNO_DECK_ENTITY.get(), context -> new TechnoRoomRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.LOOKOUT_GLASS_ENTITY.get(), context -> new LookoutGlassRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.SKYWRITING_BOARD_ENTITY.get(), context -> new SkywritingBoardRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.AUTOPILOT_ENTITY.get(), AutopilotRenderer::new);
        event.registerBlockEntityRenderer(SkylinersRegistry.PASSENGER_SCREEN_ENTITY.get(), context -> new PassengerScreenRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.TAIL_CAMERA_DISPLAY_ENTITY.get(), context -> new TailCameraDisplayRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.ANNOUNCEMENT_PANEL_ENTITY.get(), context -> new AnnouncementPanelRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.FLIGHT_MAP_ENTITY.get(), context -> new FlightMapRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.SELF_DESTRUCT_BUTTON_ENTITY.get(), context -> new SelfDestructButtonRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.PLUG_DOOR_ENTITY.get(), context -> new PlugDoorRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.COCKPIT_DOOR_ENTITY.get(), context -> new CockpitDoorRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.AIRSTAIR_CONTROLLER_ENTITY.get(), context -> new AirstairRenderer<>());
        event.registerBlockEntityRenderer(SkylinersRegistry.BOARDING_STAIRS_ENTITY.get(), context -> new AirstairRenderer<>());
        event.registerBlockEntityRenderer(SkylinersRegistry.ROCKET_STAIRS_ENTITY.get(), context -> new RocketStairsRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.SLIDE_CONTROL_ENTITY.get(), context -> new SlideRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.TRANSPONDER_ENTITY.get(), context -> new TransponderRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.TOWER_RADAR_ENTITY.get(), context -> new TowerRadarRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.LIFT_PANEL_ENTITY.get(), context -> new LiftPanelRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.WIND_SOCK_ENTITY.get(), context -> new WindSockRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.ATIS_SCREEN_ENTITY.get(), context -> new AtisScreenRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.FLIGHT_STRIP_BOARD_ENTITY.get(), context -> new FlightStripBoardRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.RUBBER_DUCK_ENTITY.get(), RubberDuckRenderer::new);
        event.registerBlockEntityRenderer(SkylinersRegistry.GIANT_RUBBER_DUCK_ENTITY.get(), GiantRubberDuckRenderer::new);
        event.registerBlockEntityRenderer(SkylinersRegistry.DISCO_BALL_ENTITY.get(), context -> new DiscoBallRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.ORRERY_ENTITY.get(), context -> new OrreryRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.SPRINKLER_ENTITY.get(), context -> new SprinklerRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.ROBOT_BARTENDER_ENTITY.get(), RobotBartenderRenderer::new);
        event.registerBlockEntityRenderer(SkylinersRegistry.CANOPY_LINING_ENTITY.get(), context -> new CanopyLiningRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.CONSOLE_ENTITY.get(), context -> new ConsoleRenderer());
        event.registerEntityRenderer(SkylinersRegistry.BASKETBALL.get(), BasketballRenderer::new);
        event.registerEntityRenderer(SkylinersRegistry.FALLING_CRATE.get(), FallingCrateRenderer::new);
        event.registerEntityRenderer(SkylinersRegistry.CRANE_HOOK.get(), CraneHookRenderer::new);
        event.registerEntityRenderer(SkylinersRegistry.LIFT_CAR.get(), LiftCarRenderer::new);
        event.registerEntityRenderer(SkylinersRegistry.TROLLEY.get(), TrolleyRenderer::new);
        event.registerEntityRenderer(SkylinersRegistry.PAPER_PLANE.get(), PaperPlaneRenderer::new);
        event.registerEntityRenderer(SkylinersRegistry.BLUE_ICE_CHUNK.get(), BlueIceChunkRenderer::new);
        event.registerEntityRenderer(SkylinersRegistry.CANNON_ROCKET.get(), CannonRocketRenderer::new);
        // A ride down a slide is only seen in who rides it.
        event.registerEntityRenderer(SkylinersRegistry.SLIDE_RIDE.get(), NoopRenderer::new);
    }
}

