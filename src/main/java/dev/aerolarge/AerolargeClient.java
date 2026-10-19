package dev.aerolarge;

import dev.aerolarge.client.CockpitWindscreenRenderer;
import dev.aerolarge.client.NavLightRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** The client side: the renderers for the cockpit's curved windscreen and the airliner's exterior lights. */
@Mod(value = Aerolarge.MOD_ID, dist = Dist.CLIENT)
public final class AerolargeClient {
    public AerolargeClient(IEventBus modEventBus) {
        modEventBus.addListener(AerolargeClient::onRegisterRenderers);
    }


    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AerolargeRegistry.COCKPIT_WINDSCREEN_ENTITY.get(), context -> new CockpitWindscreenRenderer());
        event.registerBlockEntityRenderer(AerolargeRegistry.NAV_LIGHT_ENTITY.get(), context -> new NavLightRenderer());
    }
}
