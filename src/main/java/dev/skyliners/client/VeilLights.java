package dev.skyliners.client;

import dev.skyliners.Skyliners;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.dynamicbuffer.DynamicBufferType;
import foundry.veil.api.client.render.framebuffer.FramebufferAttachmentDefinition;
import foundry.veil.api.client.render.framebuffer.FramebufferDefinition;
import foundry.veil.api.client.render.framebuffer.FramebufferManager;
import foundry.veil.api.client.render.framebuffer.VeilFramebuffers;
import foundry.veil.api.client.render.light.data.AreaLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix3f;
import org.joml.Vector3dc;
import org.joml.Vector3f;

/**
 * Real light drawn through Veil's light renderer (Veil comes inside Sable's jar), for the cabin's blue night lights
 * ({@link NightLights}) and the disco ball's spots ({@link DiscoLights}): Veil's area lights, a glowing face of a size
 * that shines out of one side of it, within an angle of straight out and as far as it reaches, added, shone, placed
 * and removed; and the buffers Veil draws them from kept on while they are near (see {@link Buffers}). A light is held
 * outside as the handle Veil gave for it.
 *
 * <p>Veil's own classes are only touched in {@link Veil}, and only once Veil is known to be installed: callers check
 * {@link #USABLE} first.
 */
public final class VeilLights {
    /**
     * Whether Veil's lights can be drawn at all: Veil installed, and no Iris (or Oculus), with or without a shader pack,
     * for Veil then cannot add its buffers, and its lights would be drawn from garbage.
     */
    static final boolean USABLE = ModList.get().isLoaded("veil") && !ModList.get().isLoaded("iris") && !ModList.get().isLoaded("oculus");
    /**
     * How long the player has to have been in the world, in ticks, before any light is drawn (again after a respawn):
     * joining aboard an airliner in flight with its night lights lit crashed the game outright, about three seconds in,
     * most times, it seems as Veil's buffers went on while Sable was still setting the airliner up; held off this long,
     * it never did.
     */
    private static final int SETTLE_TICKS = 200;

    private VeilLights() {
    }

    /** Whether the player has been in the world long enough for lights to be drawn (see {@link #SETTLE_TICKS}). */
    static boolean settled(Minecraft minecraft) {
        return minecraft.player != null && minecraft.player.tickCount >= SETTLE_TICKS;
    }

    /**
     * Keeps the depth of Veil's light framebuffer in the window's own format, each frame. Once any mod asks for a
     * stencil (Supplementaries does), NeoForge gives the window's depth one, which is a format of its own, while Veil's
     * light framebuffer keeps plain depth. Veil copies the world's depth across before it draws its lights, and between
     * two formats the copy fails: every frame lights are drawn, each failure logged as a GL error, hundreds a second.
     */
    public static void frame(RenderFrameEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (USABLE && minecraft.level != null && minecraft.getMainRenderTarget().isStencilEnabled()) {
            Veil.matchLightDepth();
        }
    }

    /**
     * A new light, Veil's area light with a face reaching so far either way along and across its middle, in blocks,
     * shining at most the angle from straight out (in radians); dark until {@link #shine} and placed with
     * {@link #place}.
     */
    static Object add(double halfLength, double halfWidth, float angle) {
        return Veil.add(halfLength, halfWidth, angle);
    }

    /** Sets how far a light reaches, in blocks, its colour and its brightness. */
    static void shine(Object light, float reach, float red, float green, float blue, float brightness) {
        Veil.shine(light, reach, red, green, blue, brightness);
    }

    /**
     * Puts a light with the middle of its face here in the world, its length running along the one way and shining the
     * other (both of length one, square to each other).
     */
    static void place(Object light, Vector3dc middle, Vector3dc along, Vector3dc out) {
        Veil.place(light, middle, along, out);
    }

    static void remove(Object light) {
        Veil.remove(light);
    }

    /**
     * The buffers Veil draws its lights from, kept on under a name while lights are drawn, and let go only
     * {@link #RELEASE_AFTER} after the last went. Veil draws its lights after the world, from what the world's drawing
     * left in two buffers it adds to it: each pixel's own colour and the way its surface faces. Veil itself turns them
     * on and off as its lights come into and out of view, and each switch recompiles the game's shaders over the next
     * frames and redraws every chunk in view: a hitch each time the player turned round, or walked in and out of reach.
     */
    static final class Buffers {
        /** How long the buffers are kept on after the last light went, in milliseconds. */
        private static final long RELEASE_AFTER = 10_000;

        private final ResourceLocation name;
        /** When a light was last drawn, as {@link net.minecraft.Util#getMillis}. */
        private long litAt;
        private boolean holding;

        Buffers(String name) {
            this.name = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, name);
        }

        /** Keeps the buffers on while any light is drawn, and lets them go once none has been for a while. */
        void keep(boolean lit, long now) {
            if (lit) {
                litAt = now;
                if (!holding) {
                    Veil.hold(name);
                    holding = true;
                }
            } else if (holding && now - litAt > RELEASE_AFTER) {
                release();
            }
        }

        /** Whether the buffers are kept on now. */
        boolean held() {
            return holding;
        }

        /** Lets the buffers go now. */
        void release() {
            if (holding) {
                holding = false;
                Veil.release(name);
            }
        }
    }

    /** The calls into Veil: only loaded once Veil is known to be installed. */
    private static final class Veil {
        /** The light's own axes in the world, and its turn from them: reused, as lights fly every frame. */
        private static final Vector3f X = new Vector3f();
        private static final Vector3f Y = new Vector3f();
        private static final Vector3f Z = new Vector3f();
        private static final Matrix3f TURN = new Matrix3f();
        /** The format NeoForge gives the window's depth once it has a stencil. */
        private static final FramebufferAttachmentDefinition.Format STENCIL_DEPTH = FramebufferAttachmentDefinition.Format.DEPTH32F_STENCIL8;

        private Veil() {
        }

        /**
         * Gives Veil's light framebuffer the depth format of a window's depth with a stencil, unless it has it already:
         * after each resource reload, which sets it back to Veil's own.
         */
        static void matchLightDepth() {
            FramebufferManager framebuffers = VeilRenderSystem.renderer().getFramebufferManager();
            FramebufferDefinition light = framebuffers.getFramebufferDefinition(VeilFramebuffers.LIGHT);
            FramebufferAttachmentDefinition depth = light == null ? null : light.depthBuffer();
            if (depth != null && depth.format() != STENCIL_DEPTH) {
                FramebufferAttachmentDefinition matched = new FramebufferAttachmentDefinition(depth.type(), STENCIL_DEPTH, true, depth.filter(),
                    depth.levels(), depth.name());
                framebuffers.setDefinition(VeilFramebuffers.LIGHT,
                    new FramebufferDefinition(light.width(), light.height(), light.colorBuffers(), matched, light.autoClear()));
            }
        }

        static Object add(double halfLength, double halfWidth, float angle) {
            AreaLightData light = new AreaLightData().setSize(halfLength, halfWidth).setAngle(angle).setBrightness(0);
            return VeilRenderSystem.renderer().getLightRenderer().addLight(light);
        }

        static void shine(Object handle, float reach, float red, float green, float blue, float brightness) {
            data(handle).setDistance(reach).setColor(red, green, blue).setBrightness(brightness);
        }

        /**
         * Veil turns a point in the world into the light's own frame with its orientation: its length along x, across
         * it y, and the way it shines z.
         */
        static void place(Object handle, Vector3dc middle, Vector3dc along, Vector3dc out) {
            AreaLightData light = data(handle);
            X.set(along);
            Z.set(out);
            Z.cross(X, Y);
            light.getOrientationMutable().setFromNormalized(TURN.set(X, Y, Z)).conjugate();
            // Last, as the position marks the light changed for Veil, the orientation with it.
            light.getPositionMutable().set(middle);
        }

        static void remove(Object handle) {
            ((LightRenderHandle<?>) handle).free();
        }

        /** Keeps the buffers Veil draws its lights from on, as if its lights were in view all along. */
        static void hold(ResourceLocation name) {
            VeilRenderSystem.renderer().enableBuffers(name, DynamicBufferType.ALBEDO, DynamicBufferType.NORMAL);
        }

        static void release(ResourceLocation name) {
            VeilRenderSystem.renderer().disableBuffers(name);
        }

        @SuppressWarnings("unchecked")
        private static AreaLightData data(Object handle) {
            return ((LightRenderHandle<AreaLightData>) handle).getLightData();
        }
    }
}
