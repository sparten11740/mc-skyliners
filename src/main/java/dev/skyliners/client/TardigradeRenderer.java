package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skyliners.Skyliners;
import dev.skyliners.entity.Tardigrade;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the rocket's tardigrade (see {@link Tardigrade}): its model, built at half its size as vanilla's creatures are
 * built at theirs, drawn twice as big, about six blocks long, three and a half tall and nearly three wide. It is drawn
 * in her frame, as Sable draws what lives among her blocks. It swells toward its massive burp after an ice cream, and
 * stretches out long and thin going to hyperspace.
 */
public class TardigradeRenderer extends MobRenderer<Tardigrade, TardigradeModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/entity/tardigrade.png");
    /** How many times its model's size it is drawn, and how far its shadow spreads, in blocks. */
    private static final float SCALE = 2.0F;
    private static final float SHADOW = 1.8F;
    /**
     * How much bigger it is all round at the height of its swelling, and how much thinner and how many times longer
     * besides its own length it is at the end of its stretching out, each as a share of its size.
     */
    private static final float SWELLING = 0.3F;
    private static final float THINNER = 0.7F;
    private static final float LONGER = 5.0F;

    public TardigradeRenderer(EntityRendererProvider.Context context) {
        super(context, new TardigradeModel(context.bakeLayer(TardigradeModel.LAYER)), SHADOW);
    }

    @Override
    protected void scale(Tardigrade creature, PoseStack poseStack, float partialTick) {
        float size = SCALE * (1 + SWELLING * creature.swell(partialTick));
        float stretch = creature.stretch(partialTick) * creature.stretch(partialTick);
        float across = size * (1 - THINNER * stretch);
        poseStack.scale(across, across, size * (1 + LONGER * stretch));
    }

    @Override
    public ResourceLocation getTextureLocation(Tardigrade creature) {
        return TEXTURE;
    }
}
