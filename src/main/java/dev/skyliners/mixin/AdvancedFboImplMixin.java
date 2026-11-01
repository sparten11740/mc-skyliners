package dev.skyliners.mixin;

import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFboAttachment;
import foundry.veil.impl.client.render.framebuffer.AdvancedFboImpl;
import foundry.veil.impl.client.render.framebuffer.AdvancedFboMutableTextureAttachment;
import javax.annotation.Nullable;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a Veil framebuffer that draws into another one's depth texture see whether that texture has a stencil. Once
 * any mod asks for a stencil (Supplementaries does), NeoForge gives the window's depth one, and Veil gives the depth it
 * draws the hand into one too. Veil's lights draw the hand through a framebuffer that borrows that depth texture, and
 * it took the texture for plain depth whatever it was. So clearing it before the hand was drawn failed, logged as a GL
 * error every frame Veil's lights were drawn. And the hand and what it held were drawn against the depth of the
 * frames before, partly hidden as they moved.
 *
 * <p>The format is asked of the texture itself, through direct state access, which is also the only way Veil's
 * clearing takes that goes wrong (without it, Veil clears through the framebuffer, whatever the format).
 */
@Mixin(AdvancedFboImpl.class)
abstract class AdvancedFboImplMixin {
    @Shadow
    @Final
    @Mutable
    protected boolean hasStencil;

    @Shadow
    @Final
    protected AdvancedFboAttachment depthAttachment;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void skyliners$seeBorrowedStencil(int width, int height, AdvancedFboAttachment[] colorAttachments, @Nullable AdvancedFboAttachment depth,
        @Nullable String debugLabel, CallbackInfo info) {
        if (hasStencil || !(depthAttachment instanceof AdvancedFboMutableTextureAttachment borrowed) || !VeilRenderSystem.directStateAccessSupported()
            || !GL11C.glIsTexture(borrowed.getId())) {
            return;
        }
        int format = ARBDirectStateAccess.glGetTextureLevelParameteri(borrowed.getId(), 0, GL11C.GL_TEXTURE_INTERNAL_FORMAT);
        hasStencil = format == GL30C.GL_DEPTH_STENCIL || format == GL30C.GL_DEPTH24_STENCIL8 || format == GL30C.GL_DEPTH32F_STENCIL8;
    }
}
