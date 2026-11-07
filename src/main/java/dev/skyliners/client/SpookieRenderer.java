package dev.skyliners.client;

import dev.skyliners.Skyliners;
import dev.skyliners.entity.Spookie;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the freighter's Spookie (see {@link Spookie}) with its model, built at its full size, and its big amber eyes
 * glowing faintly over it, as a spider's do, so they show in a dark corridor. It is drawn in her frame, as Sable draws
 * what lives among her blocks.
 */
public class SpookieRenderer extends MobRenderer<Spookie, SpookieModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/entity/spookie.png");
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/entity/spookie_eyes.png");
    /** How far its shadow spreads, in blocks. */
    private static final float SHADOW = 0.7F;

    public SpookieRenderer(EntityRendererProvider.Context context) {
        super(context, new SpookieModel(context.bakeLayer(SpookieModel.LAYER)), SHADOW);
        addLayer(new GlowingEyes(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Spookie spookie) {
        return TEXTURE;
    }

    /** Its eyes, drawn bright whatever the light round it. */
    private static final class GlowingEyes extends EyesLayer<Spookie, SpookieModel> {
        private static final RenderType GLOW = RenderType.eyes(EYES);

        GlowingEyes(RenderLayerParent<Spookie, SpookieModel> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return GLOW;
        }
    }
}
