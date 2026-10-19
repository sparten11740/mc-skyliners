package dev.aerolarge;

import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.client.CockpitWindscreenRenderer;
import dev.aerolarge.client.ContrailParticle;
import dev.aerolarge.client.EngineSounds;
import dev.aerolarge.client.NavLightRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * The client side: the renderers for the cockpit's curved windscreen and the airliner's exterior lights, and its
 * engines' sound and contrails.
 */
@Mod(value = Aerolarge.MOD_ID, dist = Dist.CLIENT)
public final class AerolargeClient {
    public AerolargeClient(IEventBus modEventBus) {
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
    }
}
