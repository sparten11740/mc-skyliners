package dev.aerolarge;

import dev.aerolarge.airframe.AirframeBuild;
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

        modEventBus.addListener(RegisterPayloadHandlersEvent.class,
            event -> event.registrar("1").playToServer(OpenParachute.TYPE, OpenParachute.STREAM_CODEC, Parachute::handle));
        NeoForge.EVENT_BUS.addListener(AirlinerSpawnGuard::onSpawnPlacementCheck);
        NeoForge.EVENT_BUS.addListener(Parachute::tick);
        NeoForge.EVENT_BUS.addListener(Broadcasts::onChat);
        NeoForge.EVENT_BUS.addListener(Broadcasts::onLoggedOut);
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
}
