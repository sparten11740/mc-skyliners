package dev.aerolarge;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import dev.aerolarge.airframe.AirframeBuild;
import dev.aerolarge.airport.ControlTower;
import dev.aerolarge.airport.RunwayBuild;
import dev.aerolarge.block.BrewCoffee;
import dev.aerolarge.block.LiftPanelInteraction;
import dev.aerolarge.autopilot.OpenAutopilotScreen;
import dev.aerolarge.block.OpenCoffeeMachine;
import dev.aerolarge.block.OpenTowerRadar;
import dev.aerolarge.block.OpenTransponder;
import dev.aerolarge.autopilot.SetAutopilot;
import dev.aerolarge.block.SetTransponder;
import dev.aerolarge.autopilot.UnstickAirliner;
import dev.aerolarge.block.VillagesBelow;
import dev.aerolarge.cabin.AirPocketPayload;
import dev.aerolarge.cabin.DecompressionPush;
import dev.aerolarge.cabin.Headroom;
import dev.aerolarge.cabin.HighDive;
import dev.aerolarge.cabin.LightningStrikePayload;
import dev.aerolarge.cabin.Passengers;
import dev.aerolarge.cabin.Seatbelts;
import dev.aerolarge.cabin.TurbulencePayload;
import dev.aerolarge.client.AutopilotSettingsScreen;
import dev.aerolarge.client.CameraShake;
import dev.aerolarge.client.CoffeeMachineScreen;
import dev.aerolarge.client.LightningFlash;
import dev.aerolarge.client.RadarPicture;
import dev.aerolarge.client.RadarScreen;
import dev.aerolarge.client.TerrainMap;
import dev.aerolarge.client.TransponderScreen;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.entity.SlideRide;
import dev.aerolarge.parachute.OpenParachute;
import dev.aerolarge.parachute.Parachute;
import dev.aerolarge.radar.RadarContacts;
import dev.aerolarge.radar.RadarLand;
import dev.aerolarge.radar.RadarSurvey;
import dev.aerolarge.radar.Transponders;
import dev.aerolarge.radio.Broadcasts;
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
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * An A380-style double-deck airliner, big enough to build in. A blueprint lays the whole airframe down in one go: a
 * single hollow hull with a cabin floor over a cargo hold, a few doors, wings, tail, engines and landing gear. With
 * Create Aeronautics and Create Propulsion installed its wings lift, its engines push and a Physics Assembler waits
 * in the cockpit to turn it into a flying contraption.
 */
@Mod(Aerolarge.MOD_ID)
public final class Aerolarge {
    public static final String MOD_ID = "aerolarge";

    public Aerolarge(IEventBus modEventBus, ModContainer container) {
        AerolargeRegistry.register(modEventBus);
        container.registerConfig(ModConfig.Type.SERVER, AerolargeConfig.SPEC);

        modEventBus.addListener(Aerolarge::onRegisterPayloads);
        // A lift panel carried in an elevator's cabin is touched through Create, which only knows blocks it is told of.
        modEventBus.addListener(FMLCommonSetupEvent.class, event -> event.enqueueWork(
            () -> MovingInteractionBehaviour.REGISTRY.register(AerolargeRegistry.LIFT_PANEL.get(), new LiftPanelInteraction())));
        NeoForge.EVENT_BUS.addListener(AirlinerSpawnGuard::onSpawnPlacementCheck);
        NeoForge.EVENT_BUS.addListener(Parachute::tick);
        NeoForge.EVENT_BUS.addListener(HighDive::onTick);
        NeoForge.EVENT_BUS.addListener(Headroom::onTick);
        NeoForge.EVENT_BUS.addListener(Broadcasts::onChat);
        NeoForge.EVENT_BUS.addListener(Broadcasts::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(Seatbelts::onMount);
        NeoForge.EVENT_BUS.addListener(SlideRide::onMount);
        NeoForge.EVENT_BUS.addListener(Passengers::onInteract);
        NeoForge.EVENT_BUS.addListener(Passengers::onTick);
        NeoForge.EVENT_BUS.addListener(FrameProtection::onBreakSpeed);
        NeoForge.EVENT_BUS.addListener(FrameProtection::onBreak);
        NeoForge.EVENT_BUS.addListener(FrameProtection::onExplosion);
        // Last, so a placement another mod turned down is not marked.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HandPlaced::onPlace);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, HandPlaced::onArrive);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, HandPlaced::onArrive);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, HandPlaced::onArrive);
        SableFlight.watch(HandPlaced::assembled, HandPlaced::gone);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> AerolargeCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                AirframeBuild.tick(level);
                Transponders.tick(level);
                ControlTower.tick(level);
                RunwayBuild.tick(level);
                RadarSurvey.tick(level);
                HandPlaced.tick(level);
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
            }
        });
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("15")
            .playToServer(OpenParachute.TYPE, OpenParachute.STREAM_CODEC, Parachute::handle)
            .playToServer(SetAutopilot.TYPE, SetAutopilot.STREAM_CODEC, SetAutopilot::handle)
            .playToServer(UnstickAirliner.TYPE, UnstickAirliner.STREAM_CODEC, UnstickAirliner::handle)
            .playToServer(BrewCoffee.TYPE, BrewCoffee.STREAM_CODEC, BrewCoffee::handle)
            .playToServer(SetTransponder.TYPE, SetTransponder.STREAM_CODEC, SetTransponder::handle)
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
            .playToClient(OpenAutopilotScreen.TYPE, OpenAutopilotScreen.STREAM_CODEC,
                (payload, context) -> AutopilotSettingsScreen.open(payload))
            .playToClient(OpenCoffeeMachine.TYPE, OpenCoffeeMachine.STREAM_CODEC,
                (payload, context) -> CoffeeMachineScreen.open(payload.pos()))
            .playToClient(VillagesBelow.TYPE, VillagesBelow.STREAM_CODEC,
                (payload, context) -> TerrainMap.villages(payload.villages()))
            .playToClient(OpenTransponder.TYPE, OpenTransponder.STREAM_CODEC,
                (payload, context) -> TransponderScreen.open(payload))
            .playToClient(OpenTowerRadar.TYPE, OpenTowerRadar.STREAM_CODEC,
                (payload, context) -> RadarScreen.open(payload.pos()))
            .playToClient(RadarContacts.TYPE, RadarContacts.STREAM_CODEC,
                (payload, context) -> RadarPicture.receive(payload))
            .playToClient(RadarLand.TYPE, RadarLand.STREAM_CODEC,
                (payload, context) -> TerrainMap.surveyed(payload));
    }
}
