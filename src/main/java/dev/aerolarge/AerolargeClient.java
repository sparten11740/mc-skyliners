package dev.aerolarge;

import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.client.AutopilotScreenRenderer;
import dev.aerolarge.client.CameraShake;
import dev.aerolarge.client.ContrailParticle;
import dev.aerolarge.client.FallingCrateRenderer;
import dev.aerolarge.client.EngineIntakeRenderer;
import dev.aerolarge.client.EngineSounds;
import dev.aerolarge.client.FlareSmokeParticle;
import dev.aerolarge.client.FlightDisplayRenderer;
import dev.aerolarge.client.NavLightRenderer;
import dev.aerolarge.client.ParachuteControls;
import dev.aerolarge.client.ParachuteLayer;
import dev.aerolarge.client.PassengerScreenRenderer;
import dev.aerolarge.client.ScoreboardRenderer;
import dev.aerolarge.client.ShellModel;
import dev.aerolarge.client.TabBanners;
import dev.aerolarge.client.TrolleyRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The client side: the model of the skin cut to the hull's curve, the renderers for the airliner's exterior lights,
 * flight displays, autopilot screen, passenger screens and engine intakes, its engines' sound and contrails, the
 * camera shake aboard, parachutes: opening them by jumping, and the canopy over their wearers; the creative tab's
 * section banners; and the settings screen in the Mods menu.
 */
@Mod(value = Aerolarge.MOD_ID, dist = Dist.CLIENT)
public final class AerolargeClient {
    /** How big the basketball is drawn, as a share of an item's sprite. */
    private static final float BALL_SIZE = 1.4F;

    public AerolargeClient(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, AerolargeClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(CameraShake::tick);
        NeoForge.EVENT_BUS.addListener(CameraShake::angles);
        NeoForge.EVENT_BUS.addListener(ParachuteControls::tick);
        NeoForge.EVENT_BUS.addListener(TabBanners::layOut);
        NeoForge.EVENT_BUS.addListener(TabBanners::draw);
        modEventBus.addListener(AerolargeClient::onAddLayers);
        modEventBus.addListener(AerolargeClient::onRegisterGeometryLoaders);
        modEventBus.addListener(AerolargeClient::onRegisterRenderers);
        modEventBus.addListener(AerolargeClient::onRegisterParticles);
        EngineCoreBlockEntity.clientTicker = EngineSounds::tick;
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
        event.registerEntityRenderer(AerolargeRegistry.BASKETBALL.get(), context -> new ThrownItemRenderer<>(context, BALL_SIZE, false));
        event.registerEntityRenderer(AerolargeRegistry.FALLING_CRATE.get(), FallingCrateRenderer::new);
        event.registerEntityRenderer(AerolargeRegistry.TROLLEY.get(), TrolleyRenderer::new);
    }
}
