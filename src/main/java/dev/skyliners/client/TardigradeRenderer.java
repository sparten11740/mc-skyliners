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
 * in her frame, as Sable draws what lives among her blocks.
 */
public class TardigradeRenderer extends MobRenderer<Tardigrade, TardigradeModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/entity/tardigrade.png");
    /** How many times its model's size it is drawn, and how far its shadow spreads, in blocks. */
    private static final float SCALE = 2.0F;
    private static final float SHADOW = 1.8F;

    public TardigradeRenderer(EntityRendererProvider.Context context) {
        super(context, new TardigradeModel(context.bakeLayer(TardigradeModel.LAYER)), SHADOW);
    }

    @Override
    protected void scale(Tardigrade creature, PoseStack poseStack, float partialTick) {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(Tardigrade creature) {
        return TEXTURE;
    }
}
