package dev.aerolarge;

import dev.aerolarge.airframe.AirframeBuild;
import dev.aerolarge.block.BrewCoffee;
import dev.aerolarge.block.OpenAutopilotScreen;
import dev.aerolarge.block.OpenCoffeeMachine;
import dev.aerolarge.block.SetAutopilot;
import dev.aerolarge.block.UnstickAirliner;
import dev.aerolarge.cabin.Headroom;
import dev.aerolarge.cabin.HighDive;
import dev.aerolarge.cabin.Passengers;
import dev.aerolarge.cabin.Seatbelts;
import dev.aerolarge.cabin.TurbulencePayload;
import dev.aerolarge.client.AutopilotSettingsScreen;
import dev.aerolarge.client.CameraShake;
import dev.aerolarge.client.CoffeeMachineScreen;
import dev.aerolarge.entity.SlideRide;
import dev.aerolarge.parachute.OpenParachute;
import dev.aerolarge.parachute.Parachute;
import dev.aerolarge.radio.Broadcasts;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> AerolargeCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(LevelTickEvent.Post.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                AirframeBuild.tick(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                AirframeBuild.drop(level);
            }
        });
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("6")
            .playToServer(OpenParachute.TYPE, OpenParachute.STREAM_CODEC, Parachute::handle)
            .playToServer(SetAutopilot.TYPE, SetAutopilot.STREAM_CODEC, SetAutopilot::handle)
            .playToServer(UnstickAirliner.TYPE, UnstickAirliner.STREAM_CODEC, UnstickAirliner::handle)
            .playToServer(BrewCoffee.TYPE, BrewCoffee.STREAM_CODEC, BrewCoffee::handle)
            // Only ever handled on a client: the lambdas keep the client classes from loading anywhere else.
            .playToClient(TurbulencePayload.TYPE, TurbulencePayload.STREAM_CODEC,
                (payload, context) -> CameraShake.turbulence(payload.strength(), payload.ticks()))
            .playToClient(OpenAutopilotScreen.TYPE, OpenAutopilotScreen.STREAM_CODEC,
                (payload, context) -> AutopilotSettingsScreen.open(payload))
            .playToClient(OpenCoffeeMachine.TYPE, OpenCoffeeMachine.STREAM_CODEC,
                (payload, context) -> CoffeeMachineScreen.open(payload.pos()));
    }
}
