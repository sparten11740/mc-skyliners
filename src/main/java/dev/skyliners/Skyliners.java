package dev.skyliners;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.skyliners.airframe.AirframeBuild;
import dev.skyliners.airport.ControlTower;
import dev.skyliners.airport.RunwayBuild;
import dev.skyliners.block.BrewCoffee;
import dev.skyliners.block.LaserBolt;
import dev.skyliners.block.LiftPanelInteraction;
import dev.skyliners.block.LookIntoMicroscope;
import dev.skyliners.autopilot.AssemblyFailed;
import dev.skyliners.autopilot.FreighterFlight;
import dev.skyliners.autopilot.HyperspaceJump;
import dev.skyliners.autopilot.LaunchShock;
import dev.skyliners.autopilot.OpenAutopilotScreen;
import dev.skyliners.autopilot.ParkedShips;
import dev.skyliners.block.OpenCoffeeMachine;
import dev.skyliners.block.OpenTowerRadar;
import dev.skyliners.block.OpenTransponder;
import dev.skyliners.autopilot.SetAutopilot;
import dev.skyliners.autopilot.RocketPilot;
import dev.skyliners.autopilot.RocketDustRing;
import dev.skyliners.autopilot.RocketPlume;
import dev.skyliners.autopilot.SpaceGravity;
import dev.skyliners.block.RampPanelBlockEntity;
import dev.skyliners.block.RocketStairsBlockEntity;
import dev.skyliners.block.RoverBayBlockEntity;
import dev.skyliners.block.SetTransponder;
import dev.skyliners.autopilot.UnstickAirliner;
import dev.skyliners.block.VillagesBelow;
import dev.skyliners.cabin.AirPocketPayload;
import dev.skyliners.cabin.CurtainParting;
import dev.skyliners.cabin.DecompressionPush;
import dev.skyliners.cabin.Headroom;
import dev.skyliners.cabin.HighDive;
import dev.skyliners.cabin.LightningStrikePayload;
import dev.skyliners.cabin.Passengers;
import dev.skyliners.cabin.Seatbelts;
import dev.skyliners.cabin.TurbulencePayload;
import dev.skyliners.client.AutopilotSettingsScreen;
import dev.skyliners.client.BlastFlash;
import dev.skyliners.client.CameraShake;
import dev.skyliners.client.CannonRockets;
import dev.skyliners.client.CoffeeMachineScreen;
import dev.skyliners.client.HyperspaceOverlay;
import dev.skyliners.client.LaserBolts;
import dev.skyliners.client.LightningFlash;
import dev.skyliners.client.MicroscopeScreen;
import dev.skyliners.client.RadarPicture;
import dev.skyliners.client.RadarScreen;
import dev.skyliners.client.RocketPlumeParticles;
import dev.skyliners.client.RocketRoar;
import dev.skyliners.client.SelfDestructWarning;
import dev.skyliners.client.TerrainMap;
import dev.skyliners.client.ThrownClear;
import dev.skyliners.client.TransponderScreen;
import dev.skyliners.compat.Blockpilot;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.entity.SlideRide;
import dev.skyliners.entity.Spookie;
import dev.skyliners.entity.Tardigrade;
import dev.skyliners.horn.ShipHorn;
import dev.skyliners.impact.ImpactJolt;
import dev.skyliners.lookout.LookoutSpot;
import dev.skyliners.parachute.OpenParachute;
import dev.skyliners.parachute.Parachute;
import dev.skyliners.parachute.SafetyCanopy;
import dev.skyliners.parachute.SafetyCanopyPayload;
import dev.skyliners.radar.RadarContacts;
import dev.skyliners.radar.RadarLand;
import dev.skyliners.radar.RadarSurvey;
import dev.skyliners.radar.Transponders;
import dev.skyliners.radio.Broadcasts;
import dev.skyliners.rover.DriveRover;
import dev.skyliners.rover.RoverDrivers;
import dev.skyliners.selfdestruct.BlastPayload;
import dev.skyliners.selfdestruct.SelfDestruct;
import dev.skyliners.selfdestruct.SelfDestructAlarmPayload;
import dev.skyliners.selfdestruct.ThrownClearPayload;
import dev.skyliners.turret.GunnerTrigger;
import dev.skyliners.turret.RocketBlast;
import dev.skyliners.zerog.ZeroGravity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * An A380-style double-deck airliner, big enough to build in. A blueprint lays the whole airframe down in one go: a
 * single hollow hull with a cabin floor over a cargo hold, a few doors, wings, tail, engines and landing gear. With
 * Create Aeronautics and Create Propulsion installed its wings lift, its engines push and a Physics Assembler waits
 * in the cockpit to turn it into a flying contraption.
 */
@Mod(Skyliners.MOD_ID)
public final class Skyliners {
    public static final String MOD_ID = "skyliners";

    public Skyliners(IEventBus modEventBus, ModContainer container) {
        SkylinersRegistry.register(modEventBus);
        container.registerConfig(ModConfig.Type.SERVER, SkylinersConfig.SPEC);

        modEventBus.addListener(Skyliners::onRegisterPayloads);
        modEventBus.addListener(Tardigrade::onAttributes);
        modEventBus.addListener(Spookie::onAttributes);
        // A lift panel carried in an elevator's cabin is touched through Create, which only knows blocks it is told of.
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(
            () -> MovingInteractionBehaviour.REGISTRY.register(SkylinersRegistry.LIFT_PANEL.get(), new LiftPanelInteraction())));
        NeoForge.EVENT_BUS.addListener(AirlinerSpawnGuard::onSpawnPlacementCheck);
        NeoForge.EVENT_BUS.addListener(Parachute::tick);
        NeoForge.EVENT_BUS.addListener(SafetyCanopy::loggedOut);
        NeoForge.EVENT_BUS.addListener(HighDive::onTick);
        NeoForge.EVENT_BUS.addListener(Headroom::onTick);
        NeoForge.EVENT_BUS.addListener(CurtainParting::onTick);
        NeoForge.EVENT_BUS.addListener(Broadcasts::onChat);
        NeoForge.EVENT_BUS.addListener(Broadcasts::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(Seatbelts::onMount);
        NeoForge.EVENT_BUS.addListener(SlideRide::onMount);
        NeoForge.EVENT_BUS.addListener(SpaceGravity::onTick);
        NeoForge.EVENT_BUS.addListener(SpaceGravity::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(ZeroGravity::onTick);
        NeoForge.EVENT_BUS.addListener(ZeroGravity::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(ZeroGravity::onJoinLevel);
        NeoForge.EVENT_BUS.addListener(Passengers::onInteract);
        NeoForge.EVENT_BUS.addListener(Passengers::onTick);
        NeoForge.EVENT_BUS.addListener(FrameProtection::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(FrameProtection::onBreak);
        NeoForge.EVENT_BUS.addListener(FrameProtection::onExplosion);
        NeoForge.EVENT_BUS.addListener(SelfDestruct::onFall);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> SelfDestruct.forget());
        // Last, so a placement another mod turned down is not marked.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HandPlaced::onPlace);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, HandPlaced::onArrive);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, HandPlaced::onArrive);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, HandPlaced::onArrive);
        SableFlight.watch(HandPlaced::assembled, HandPlaced::gone);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> SkylinersCommand.register(event.getDispatcher()));
        Blockpilot.register();
        RoverDrivers.register();
        RocketPilot.holdCountdownWhile(RoverBayBlockEntity::holdsCountdown);
        RocketPilot.holdCountdownWhile(RocketStairsBlockEntity::holdsCountdown);
        FreighterFlight.holdTakeoffWhile(RampPanelBlockEntity::holdsTakeoff);
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                AirframeBuild.tick(level);
                Transponders.tick(level);
                ControlTower.tick(level);
                RunwayBuild.tick(level);
                RadarSurvey.tick(level);
                HandPlaced.tick(level);
                SelfDestruct.tick(level);
                ShipHorn.tick(level);
                LandAhead.tick(level);
                ParkedShips.tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Save.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                RadarSurvey.save(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                RadarSurvey.forget(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                AirframeBuild.drop(level);
                ControlTower.drop(level);
                RunwayBuild.drop(level);
                RadarSurvey.unload(level);
                SelfDestruct.drop(level);
                ShipHorn.drop(level);
                LandAhead.drop(level);
            }
        });
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("37")
            .playToServer(OpenParachute.TYPE, OpenParachute.STREAM_CODEC, Parachute::handle)
            .playToServer(SetAutopilot.TYPE, SetAutopilot.STREAM_CODEC, SetAutopilot::handle)
            .playToServer(UnstickAirliner.TYPE, UnstickAirliner.STREAM_CODEC, UnstickAirliner::handle)
            .playToServer(GunnerTrigger.TYPE, GunnerTrigger.STREAM_CODEC, GunnerTrigger::handle)
            .playToClient(RocketBlast.TYPE, RocketBlast.STREAM_CODEC, (payload, context) -> CannonRockets.blast(payload))
            .playToServer(BrewCoffee.TYPE, BrewCoffee.STREAM_CODEC, BrewCoffee::handle)
            .playToServer(SetTransponder.TYPE, SetTransponder.STREAM_CODEC, SetTransponder::handle)
            .playToServer(LookoutSpot.TYPE, LookoutSpot.STREAM_CODEC, LookoutSpot::handle)
            .playToServer(DriveRover.TYPE, DriveRover.STREAM_CODEC, DriveRover::handle)
            .playToClient(HandPlacedBlocks.TYPE, HandPlacedBlocks.STREAM_CODEC,
                (payload, context) -> HandPlaced.told(context.player().level(), payload))
            .playToClient(DecompressionPush.TYPE, DecompressionPush.STREAM_CODEC,
                (payload, context) -> context.player().push(payload.x(), payload.y(), payload.z()))
            // Only ever handled on a client: the lambdas keep the client classes from loading anywhere else.
            .playToClient(TurbulencePayload.TYPE, TurbulencePayload.STREAM_CODEC,
                (payload, context) -> CameraShake.turbulence(payload.strength(), payload.ticks()))
            .playToClient(AirPocketPayload.TYPE, AirPocketPayload.STREAM_CODEC,
                (payload, context) -> CameraShake.airPocket(payload.depth()))
            .playToClient(LightningStrikePayload.TYPE, LightningStrikePayload.STREAM_CODEC,
                (payload, context) -> LightningFlash.struck())
            .playToClient(SafetyCanopyPayload.TYPE, SafetyCanopyPayload.STREAM_CODEC,
                (payload, context) -> SafetyCanopy.told(payload))
            .playToClient(LaserBolt.TYPE, LaserBolt.STREAM_CODEC,
                (payload, context) -> LaserBolts.add(payload))
            .playToClient(OpenAutopilotScreen.TYPE, OpenAutopilotScreen.STREAM_CODEC,
                (payload, context) -> AutopilotSettingsScreen.open(payload))
            .playToClient(AssemblyFailed.TYPE, AssemblyFailed.STREAM_CODEC,
                (payload, context) -> AutopilotSettingsScreen.assemblyFailed(payload.pos()))
            .playToClient(OpenCoffeeMachine.TYPE, OpenCoffeeMachine.STREAM_CODEC,
                (payload, context) -> CoffeeMachineScreen.open(payload.pos()))
            .playToClient(LookIntoMicroscope.TYPE, LookIntoMicroscope.STREAM_CODEC,
                (payload, context) -> MicroscopeScreen.open(payload.slide()))
            .playToClient(VillagesBelow.TYPE, VillagesBelow.STREAM_CODEC,
                (payload, context) -> TerrainMap.villages(payload.villages()))
            .playToClient(OpenTransponder.TYPE, OpenTransponder.STREAM_CODEC,
                (payload, context) -> TransponderScreen.open(payload))
            .playToClient(OpenTowerRadar.TYPE, OpenTowerRadar.STREAM_CODEC,
                (payload, context) -> RadarScreen.open(payload.pos()))
            .playToClient(RadarContacts.TYPE, RadarContacts.STREAM_CODEC,
                (payload, context) -> RadarPicture.receive(payload))
            .playToClient(RadarLand.TYPE, RadarLand.STREAM_CODEC,
                (payload, context) -> TerrainMap.surveyed(payload))
            .playToClient(SelfDestructAlarmPayload.TYPE, SelfDestructAlarmPayload.STREAM_CODEC,
                (payload, context) -> SelfDestructWarning.alarm(payload.ticks()))
            .playToClient(BlastPayload.TYPE, BlastPayload.STREAM_CODEC,
                (payload, context) -> BlastFlash.blast(payload.shake(), payload.flash()))
            .playToClient(LaunchShock.TYPE, LaunchShock.STREAM_CODEC,
                (payload, context) -> RocketRoar.shockwave(payload.strength()))
            .playToClient(RocketPlume.TYPE, RocketPlume.STREAM_CODEC,
                (payload, context) -> RocketPlumeParticles.show(payload))
            .playToClient(RocketDustRing.TYPE, RocketDustRing.STREAM_CODEC,
                (payload, context) -> RocketPlumeParticles.ring(payload))
            .playToClient(ThrownClearPayload.TYPE, ThrownClearPayload.STREAM_CODEC,
                (payload, context) -> ThrownClear.thrown())
            .playToClient(HyperspaceJump.TYPE, HyperspaceJump.STREAM_CODEC,
                (payload, context) -> HyperspaceOverlay.told(payload))
            .playToClient(ImpactJolt.TYPE, ImpactJolt.STREAM_CODEC,
                (payload, context) -> CameraShake.impact(payload.strength()));
    }
}
