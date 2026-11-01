package dev.skyliners.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersClientConfig;
import dev.skyliners.airframe.CameraSpot;
import dev.skyliners.block.CameraScreen;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

/**
 * The live pictures on the camera displays (any {@link CameraScreen}: the flight deck's camera display and the camera
 * panel of its curved row of screens, and a zeppelin's camera display): the world drawn from one of each assembled
 * airliner's or zeppelin's outside cameras (see {@link CameraSpot}), whichever a display is set to, into a small
 * picture of its own, which every display aboard set to that camera shows. A camera that circles the ship circles by a
 * clock of the picture's own, which stands still while the game is paused. A picture is drawn only while one of its
 * displays was in view and within {@link #REACH} blocks in the frame before, at most {@code tailCameraFps} times a
 * second and one picture a frame. It reaches as far as the {@code tailCameraRenderDistance} setting or the player's own
 * render distance, whichever is less; a camera circling a zeppelin holds its fog off past her far end. Once none of its
 * displays has been seen for a while, the picture is thrown away; with no display about, nothing is drawn or kept.
 *
 * <p>The picture is drawn before each frame, outside the frame's own drawing of the world, by Veil's perspective
 * renderer. Veil comes inside Sable's jar, so it is there whenever an airliner can be assembled, and it is made for
 * this: it draws the world from another camera into a framebuffer of ours and puts back what the frame's own drawing
 * relies on (the main framebuffer and its size, the projection, the model view, fog, lights and the culling frustum);
 * it finds the sections its camera sees with an occlusion graph of its own and keeps Sodium's lists apart, so the
 * player's own view never culls from the fin, and the fin's picture never culls from the player. Around it this class
 * holds still what Veil does not: where the world's water and glass were last sorted from, the camera the chunk
 * builder sorts new sections for, entity outlines (which would draw into the window-sized outline buffer), and the
 * size of Sable's water occlusion buffers (see {@link SableClient#withoutWaterOcclusion}). The camera is a
 * {@link Lens}, so nothing the player suffers (blindness, darkness) colours the picture, and the player is drawn in
 * it. Drawing the world does not tick it: no particle, sound or entity moves on for the picture. All the world's own
 * drawing draws is in it, block entities and particles too: Veil cannot leave them out without reaching into the game's
 * drawing, and the camera sees some of them (the engines' intakes, the navigation lights, the contrails).
 *
 * <p>It is never drawn with a shader pack in use (Iris or Oculus; where their API cannot be asked, whenever either is
 * installed), with Fabulous graphics (whose transparency pass draws into the main framebuffer whatever the target),
 * or with the {@code tailCameraFeed} setting off; and once drawing it has failed, the failure is logged and the
 * pictures stay off until the game is restarted. The displays show their test card instead.
 */
public final class TailCameraFeed {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean SABLE = ModList.get().isLoaded("sable");
    private static final boolean VEIL = ModList.get().isLoaded("veil");
    private static final boolean SHADER_MODS = ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus");
    /** Further than this from the camera, in blocks, a display does not ask for its picture. */
    private static final double REACH = 32;
    /** How long an airliner's picture is kept after the last of its displays was seen, in milliseconds. */
    private static final long FORGET_AFTER = 10_000;
    /** How often an airliner whose camera cannot be placed (it has no autopilot to go by) is looked at again, in milliseconds. */
    private static final long RETRY_AFTER = 5_000;
    /**
     * Each camera's lens is set for a 16:9 screen, as the passenger screens show it; the picture keeps that width of
     * view (the fin camera's, the wingtips at its edges) and is cut to the display's shape top and bottom.
     */
    private static final double LENS_ASPECT = 16 / 9.0;
    /** How close to the camera the picture shows anything, in blocks, as the world's own drawing does. */
    private static final float NEAR = 0.05F;
    /**
     * How far the picture's far plane lies, as a multiple of its render distance, as the world's own drawing has it
     * ({@link GameRenderer#getDepthFar}): well past the fog, so nothing short of the fog is cut off.
     */
    private static final float FAR = 4;
    /**
     * How far past the far end of the ship the fog of a camera circling her ends, in blocks: fog thickens over the last
     * tenth of its reach, at most 64 blocks, so all of her stands clear of it.
     */
    private static final double FOG_MARGIN = 64;

    private static final Map<Key, Feed> FEEDS = new HashMap<>();
    /** Counts the frames, so a display seen in one frame is drawn for in the next. */
    private static long frame;
    /** Whether a picture is being drawn now, when the displays it shows must not show it (or ask for it). */
    private static boolean drawing;
    /** Whether drawing a picture has failed; then no more are drawn until the game is restarted. */
    private static boolean failed;
    @Nullable
    private static Lens lens;
    /** Iris's API, where it can be asked whether a shader pack is in use; null where it cannot. */
    @Nullable
    private static Object irisApi;
    @Nullable
    private static Method shaderPackInUse;
    private static boolean irisLookedFor;

    private TailCameraFeed() {
    }

    /** A picture is an airliner's or a zeppelin's from one of its cameras. */
    private record Key(UUID airliner, CameraSpot camera) {
    }

    /**
     * A picture from one of an airliner's cameras: the camera, where it is on the airliner, where the picture is drawn
     * to, when a display last asked for it, and when it may be drawn next or its camera looked for again. Times are
     * {@link Util#getMillis} and compared by difference.
     */
    private static final class Feed {
        final CameraSpot camera;
        final ResourceLocation texture;
        @Nullable
        CameraMount mount;
        long mountDue;
        @Nullable
        Target target;
        long seenFrame;
        long seenAt;
        long drawDue;
        /** Whether the picture has been drawn at all, so there is something to show. */
        boolean ready;
        /** How far a circling camera has come round, in game ticks since the picture was first asked for. */
        double circled;

        Feed(Key key, long now) {
            camera = key.camera();
            texture = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID,
                "tail_camera/" + key.airliner() + "/" + camera.name().toLowerCase(Locale.ROOT));
            mountDue = now;
            drawDue = now;
        }

        void free() {
            if (target != null) {
                target.free();
                target = null;
            }
            ready = false;
        }
    }

    /**
     * A display is being drawn: the picture to show on it, from the camera it is set to, or null for its test card. A
     * display in view within reach asks for that picture to be drawn before the next frame.
     */
    @Nullable
    static <T extends BlockEntity & CameraScreen> ResourceLocation show(T screen) {
        Level level = screen.getLevel();
        if (drawing || level == null || !available()) {
            return null;
        }
        UUID airliner = SableClient.airliner(screen);
        if (airliner == null) {
            return null;
        }
        Key key = new Key(airliner, screen.camera());
        Feed feed = FEEDS.get(key);
        if (inSight(level, screen)) {
            long now = Util.getMillis();
            if (feed == null) {
                feed = new Feed(key, now);
                FEEDS.put(key, feed);
            }
            feed.seenFrame = frame;
            feed.seenAt = now;
            if (feed.mount == null && now - feed.mountDue >= 0) {
                feed.mountDue = now + RETRY_AFTER;
                SableClient.Frame airlinerFrame = SableClient.frame(screen);
                // An airliner's scale is the server's setting, which reaches the client as it joins; a zeppelin's is
                // her size's.
                feed.mount = airlinerFrame != null
                    ? CameraMount.on(airlinerFrame, CameraSpot.scale(airlinerFrame.craft(), airlinerFrame.size()), feed.camera)
                    : null;
            }
        }
        return feed != null && feed.ready ? feed.texture : null;
    }

    /**
     * Whether the display is within reach of the camera the frame is drawn from, in its view, and turned toward it.
     * Its block entity is drawn whenever it is anywhere near, so being drawn says nothing of being seen: standing
     * right by the display with its back to it, a player would otherwise pay for a picture nobody looks at.
     */
    private static boolean inSight(Level level, CameraScreen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 middle = screen.faceMiddle();
        Vec3 here = SableClient.toWorld(level, middle);
        if (here.distanceToSqr(camera) > REACH * REACH) {
            return false;
        }
        if (camera.subtract(here).dot(SableClient.toWorld(level, middle.add(screen.faceOut())).subtract(here)) <= 0) {
            return false;
        }
        // Just round the screen, wide enough for all of it however the airliner lies.
        double size = 2 * screen.faceReach();
        return minecraft.levelRenderer.getFrustum().isVisible(AABB.ofSize(here, size, size, size));
    }

    /** Whether pictures may be drawn now at all. */
    private static boolean available() {
        return SABLE && VEIL && !failed && SkylinersClientConfig.TAIL_CAMERA_FEED.get() && !Minecraft.useShaderTransparency() && !shadersInUse();
    }

    /** Whether a shader pack is in use, as Iris's (or Oculus's) API says; taken to be so where the API cannot be asked. */
    private static boolean shadersInUse() {
        if (!SHADER_MODS) {
            return false;
        }
        if (!irisLookedFor) {
            irisLookedFor = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                shaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unreachable) {
                irisApi = null;
                shaderPackInUse = null;
            }
        }
        if (irisApi == null || shaderPackInUse == null) {
            return true;
        }
        try {
            return !Boolean.FALSE.equals(shaderPackInUse.invoke(irisApi));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unreachable) {
            return true;
        }
    }

    /**
     * Before each frame: throws away the pictures nobody has looked at for a while, or all of them while none may be
     * drawn, then draws the one picture most overdue among those asked for in the frame before.
     */
    public static void frame(RenderFrameEvent.Pre event) {
        frame++;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || !available()) {
            forget();
            return;
        }
        long now = Util.getMillis();
        Iterator<Feed> feeds = FEEDS.values().iterator();
        while (feeds.hasNext()) {
            Feed feed = feeds.next();
            if (now - feed.seenAt > FORGET_AFTER) {
                feed.free();
                feeds.remove();
            }
        }
        if (minecraft.isPaused()) {
            return;
        }
        Feed due = null;
        float ticks = event.getPartialTick().getGameTimeDeltaTicks();
        for (Feed feed : FEEDS.values()) {
            feed.circled += ticks;
            if (feed.mount != null && feed.seenFrame >= frame - 1 && now - feed.drawDue >= 0 && (due == null || feed.drawDue - due.drawDue < 0)) {
                due = feed;
            }
        }
        if (due != null) {
            draw(minecraft, due, event.getPartialTick(), now);
        }
    }

    /** Draws a picture from its camera as the airliner is drawn this frame, putting back all it moves. */
    private static void draw(Minecraft minecraft, Feed feed, DeltaTracker deltaTracker, long now) {
        LevelRenderer levelRenderer = minecraft.levelRenderer;
        SectionRenderDispatcher sections = levelRenderer.getSectionRenderDispatcher();
        // The partial tick Sable draws the airliners with.
        SableClient.View view = feed.mount.view(deltaTracker.getGameTimeDeltaPartialTick(false), feed.circled);
        if (sections == null || view == null) {
            return;
        }
        if (lens == null || lens.level() != minecraft.level) {
            lens = new Lens(minecraft.level, 0);
        }
        int height = SkylinersClientConfig.TAIL_CAMERA_RESOLUTION.get();
        int width = (int) Math.round(height * TailCameraDisplayRenderer.ASPECT);
        GameRenderer gameRenderer = minecraft.gameRenderer;
        Vec3 sortedFor = sections.getCameraPosition();
        double sortedX = levelRenderer.xTransparentOld;
        double sortedY = levelRenderer.yTransparentOld;
        double sortedZ = levelRenderer.zTransparentOld;
        boolean panoramic = gameRenderer.isPanoramicMode();
        drawing = true;
        try {
            if (feed.target == null || !feed.target.fits(width, height)) {
                feed.free();
                feed.target = new Target(feed.texture, width, height);
            }
            // As if the world's water and glass were last sorted from the fin, so drawing it does not sort them anew.
            levelRenderer.xTransparentOld = view.eye().x;
            levelRenderer.yTransparentOld = view.eye().y;
            levelRenderer.zTransparentOld = view.eye().z;
            // Panoramic mode leaves out entity outlines, which would draw into the window-sized outline buffer.
            gameRenderer.setPanoramicMode(true);
            // As far as the setting asks, but never past the player's own render distance: only that much of the world
            // is loaded and built round the player. Veil draws with it as the game's render distance, so the world's
            // own fog closes in at the edge of the picture, as it does at the edge of the player's view. A camera
            // circling a zeppelin stands so far out that the fog would hide most of her: its fog reaches past her far
            // end, while Veil still draws no more of the world than is built round the player, so beyond that the
            // land is not there.
            float settled = Math.min(minecraft.options.getEffectiveRenderDistance(), SkylinersClientConfig.TAIL_CAMERA_RENDER_DISTANCE.get());
            float renderDistance = feed.mount.farthest() > 0 ? Math.max(settled, (float) ((feed.mount.farthest() + FOG_MARGIN) / 16)) : settled;
            Target target = feed.target;
            SableClient.withoutWaterOcclusion(() -> target.draw(lens, view, feed.camera, renderDistance, deltaTracker));
            feed.ready = true;
            feed.drawDue = now + 1000L / SkylinersClientConfig.TAIL_CAMERA_FPS.get();
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.error("The tail camera display's picture could not be drawn; it stays off until the game is restarted", e);
        } finally {
            gameRenderer.setPanoramicMode(panoramic);
            levelRenderer.xTransparentOld = sortedX;
            levelRenderer.yTransparentOld = sortedY;
            levelRenderer.zTransparentOld = sortedZ;
            sections.setCamera(sortedFor);
            // As the frame had it before the picture: the main framebuffer bound, no fog, back faces culled.
            minecraft.getMainRenderTarget().bindWrite(true);
            FogRenderer.setupNoFog();
            RenderSystem.enableCull();
            drawing = false;
        }
        if (failed) {
            forget();
        }
    }

    /** Throws away every picture. */
    private static void forget() {
        FEEDS.values().forEach(Feed::free);
        FEEDS.clear();
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        forget();
        lens = null;
    }

    /**
     * The vertical field of view of a picture of this width over its height, in radians: as wide as the camera's lens on
     * a 16:9 screen.
     */
    private static float fieldOfView(double aspect, CameraSpot camera) {
        double halfWidth = Math.tan(Math.toRadians(camera.fov()) / 2) * LENS_ASPECT;
        return (float) (2 * Math.atan(halfWidth / aspect));
    }

    /**
     * Where a picture is drawn to, through Veil: a framebuffer of its own, and the texture the displays draw its
     * colour with. Only made once Veil is known to be installed.
     */
    private static final class Target {
        private final AdvancedFbo framebuffer;
        private final ResourceLocation texture;

        Target(ResourceLocation texture, int width, int height) {
            this.framebuffer = AdvancedFbo.withSize(width, height).addColorTextureBuffer().setDepthTextureBuffer().build(true);
            this.texture = texture;
            Minecraft.getInstance().getTextureManager().register(texture, new Picture(() -> framebuffer.getColorTextureAttachment(0).getId()));
        }

        boolean fits(int width, int height) {
            return framebuffer.getWidth() == width && framebuffer.getHeight() == height;
        }

        /**
         * Draws the world into the framebuffer from the camera's place, looking its way, with its up, then makes the
         * picture opaque: the world's drawing leaves the sky's alpha clear, and the displays draw the picture blended
         * by its alpha, which would let the screen behind show through the sky.
         */
        void draw(Entity lens, SableClient.View view, CameraSpot camera, float renderDistance, DeltaTracker deltaTracker) {
            Vector3f forward = view.forward().toVector3f().normalize();
            Vector3f up = view.up().toVector3f();
            up.sub(new Vector3f(forward).mul(up.dot(forward))).normalize();
            Vector3f right = new Vector3f(forward).cross(up);
            // The camera's turn from its own axes (right, up, and back toward the viewer) into the world's, and the
            // view's turn back. Veil draws with the model view times the orientation it is given, and hands that
            // orientation to whatever faces the camera (particles, name tags) as the camera's own turn, as the
            // world's own drawing does. So it is given the camera's turn, and the model view turns back twice over.
            Quaternionf orientation = new Quaternionf().setFromNormalized(new Matrix3f(right, up, new Vector3f(forward).negate()));
            Quaternionf viewTurn = orientation.conjugate(new Quaternionf());
            Matrix4f modelView = new Matrix4f().rotation(viewTurn).rotate(viewTurn);
            float aspect = (float) framebuffer.getWidth() / framebuffer.getHeight();
            Matrix4f projection = new Matrix4f().perspective(fieldOfView(aspect, camera), aspect, NEAR, renderDistance * 16 * FAR);
            Vec3 eye = view.eye();
            VeilLevelPerspectiveRenderer.render(framebuffer, lens, modelView, projection, new Vector3d(eye.x, eye.y, eye.z), orientation, renderDistance,
                deltaTracker, false);
            framebuffer.bind(true);
            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.clearColor(0, 0, 0, 1);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.colorMask(true, true, true, true);
        }

        void free() {
            Minecraft.getInstance().getTextureManager().release(texture);
            framebuffer.free();
        }
    }

    /**
     * A picture as a texture the displays can draw with: the framebuffer's own colour texture, which the framebuffer
     * keeps and frees. It never takes a texture of its own, so letting it go frees nothing.
     */
    private static final class Picture extends AbstractTexture {
        private final IntSupplier id;

        Picture(IntSupplier id) {
            this.id = id;
        }

        @Override
        public int getId() {
            return id.getAsInt();
        }

        @Override
        public void load(ResourceManager resourceManager) {
        }
    }
}
