package dev.aerolarge;

import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.client.CameraShake;
import dev.aerolarge.client.CockpitWindscreenRenderer;
import dev.aerolarge.client.ContrailParticle;
import dev.aerolarge.client.EngineSounds;
import dev.aerolarge.client.FlightDisplayRenderer;
import dev.aerolarge.client.NavLightRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * The client side: the renderers for the airliner's nose, windscreen, exterior lights and flight displays, its
 * engines' sound and contrails, and the camera shake aboard.
 */
@Mod(value = Aerolarge.MOD_ID, dist = Dist.CLIENT)
public final class AerolargeClient {
    public AerolargeClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, AerolargeClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(CameraShake::tick);
        NeoForge.EVENT_BUS.addListener(CameraShake::angles);
        modEventBus.addListener(AerolargeClient::onRegisterRenderers);
        modEventBus.addListener(AerolargeClient::onRegisterParticles);
        EngineCoreBlockEntity.clientTicker = EngineSounds::tick;
    }

    private static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AerolargeRegistry.CONTRAIL.get(), ContrailParticle.Provider::new);
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AerolargeRegistry.COCKPIT_WINDSCREEN_ENTITY.get(), context -> new CockpitWindscreenRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.NAV_LIGHT_ENTITY.get(), context -> new NavLightRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.FLIGHT_DISPLAY_ENTITY.get(), context -> new FlightDisplayRenderer());
    }
}
