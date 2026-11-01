package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.airframe.EngineFlames;
import dev.skyliners.block.EngineCoreBlockEntity;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The lightweight flame behind a running engine, when the server config's {@code engineFlames} asks for it, seen with
 * Fancy or Fabulous graphics: a blue plasma flame from the back of the nacelle, white-hot at its core, longer and
 * brighter the harder the engine runs and flickering as it burns, with a glow across the nozzle. Each client draws it
 * from the engine's core (see {@link EngineRenderer}), so it costs the server and the network nothing, unlike Create
 * Propulsion's own plume, every puff of which the server sends to every player near (see {@link EngineFlames}).
 *
 * <p>The flame is a picture of one ({@code exhaust_flame.png}) on a strip along the engine's axis, turned about the
 * axis to face the camera, with a shorter, narrower one inside it flickering out of step, so it reads as round from
 * beside it; the glow is a white-hot disc in a cyan one across the nozzle, facing back, so it reads as round from
 * behind too. It is light added over whatever lies behind it, so nothing of it hides anything else.
 */
final class ExhaustFlame {
    private static final ResourceLocation FLAME = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/misc/exhaust_flame.png");
    /** A soft round flare, drawn smoothed, for the glow across the nozzle. */
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/misc/exhaust_glow.png");
    /**
     * How far behind the middle of the engine's core the flame starts, in blocks: past the core's own block and the
     * thruster's behind it, at the back of the nacelle.
     */
    static final float NOZZLE_BEHIND = 1.5F;
    /**
     * Half the width of the flame's strip, as a share of the nacelle's radius. The picture's flame fills three quarters
     * of it at the nozzle, about as wide as the thrusters at the end of the narrowing exhaust cone.
     */
    private static final float HALF_WIDTH = 0.7F;
    /** How long the flame is with the engine idling and at full thrust, as shares of the nacelle's radius. */
    private static final float IDLE_LENGTH = 0.8F;
    private static final float FULL_LENGTH = 4F;
    /** The inner flame's length and width as a share of the outer one's, and how much faster it flickers. */
    private static final float INNER = 0.6F;
    private static final float INNER_FLICKER = 1.3F;
    /**
     * How far the flame's length flickers either way, as a share of it; how fast, in radians a tick; and how much
     * faster the second, smaller wave it flickers with runs, so it never settles into a beat.
     */
    private static final float FLICKER = 0.1F;
    private static final float FLICKER_SPEED = 1.7F;
    private static final float FLICKER_RIPPLE = 2.7F;
    /**
     * The glow across the nozzle, all of the flame there is to see from straight behind, where the strips are edge on:
     * a cyan halo and inside it a white-hot middle, their radii as shares of the nacelle's and their colours at full
     * brightness; and how far behind the nozzle they lie, in blocks, the middle a little further, so neither flickers
     * against the thrusters' backs or the other.
     */
    private static final float HALO_RADIUS = 0.8F;
    private static final int HALO_COLOUR = 0x5AC8FF;
    private static final float HALO_BEHIND = 0.05F;
    private static final float HOT_RADIUS = 0.4F;
    private static final int HOT_COLOUR = 0xE0FAFF;
    private static final float HOT_BEHIND = 0.1F;
    /** Below this spool the engine has stopped, and nothing burns. */
    private static final float BURNING = 0.02F;
    /**
     * The flame's brightness: it fades in over this first share of the spool as the engine lights, and burns from this
     * share of its full brightness at idle to full at full thrust.
     */
    private static final float FADE_IN = 0.1F;
    private static final float IDLE_BRIGHTNESS = 0.5F;
    /** Closer to the axis than this, in blocks, the camera gives the strips no side to face it with, so they keep one. */
    private static final float ON_AXIS = 1.0E-3F;
    /**
     * Within this angle of the engine's axis, as its sine (30 degrees), the strips fade out toward it: seen end on they
     * are edge on, only a streak, and from behind the glow shows the flame instead.
     */
    private static final float END_ON = 0.5F;

    private ExhaustFlame() {
    }

    /**
     * Whether an engine shows its flame: the lightweight flame asked for, Fancy or Fabulous graphics, the engine
     * running, and the thruster behind its core still there to burn.
     */
    static boolean burning(EngineCoreBlockEntity engine, Level level, Direction nose) {
        return SkylinersConfig.ENGINE_FLAMES.get() == EngineFlames.LIGHTWEIGHT_FLAME
            && Minecraft.getInstance().options.graphicsMode().get() != GraphicsStatus.FAST
            && engine.spool() > BURNING
            && !level.getBlockState(engine.getBlockPos().relative(nose.getOpposite())).isAir();
    }

    /**
     * Draws the flame, with the pose on the engine's axis level with the middle of its core (x to port, y up, z out
     * along the nose) and the nacelle's radius, in blocks.
     */
    static void draw(EngineCoreBlockEntity engine, Level level, float radius, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        float spool = engine.spool();
        float brightness = Math.min(1, spool / FADE_IN) * (IDLE_BRIGHTNESS + (1 - IDLE_BRIGHTNESS) * spool);
        float length = radius * (IDLE_LENGTH + (FULL_LENGTH - IDLE_LENGTH) * spool);
        // Each engine flickers out of step with the others.
        float time = (float) DrawingClock.ticks(partialTick) + Math.floorMod(engine.getBlockPos().hashCode(), 100);
        pose.pushPose();
        pose.translate(0, 0, -NOZZLE_BEHIND);
        PoseStack.Pose nozzle = pose.last();
        // Across the axis, square to the camera's line to it, so the strips face the camera as squarely as they can.
        Vector3f eye = Sight.eye(nozzle);
        float off = (float) Math.hypot(eye.x, eye.y);
        float sideX = off < ON_AXIS ? 1 : -eye.y / off;
        float sideY = off < ON_AXIS ? 0 : eye.x / off;
        float side = off < ON_AXIS ? 0 : Math.min(1, off / eye.length() / END_ON);
        float half = radius * HALF_WIDTH;
        VertexConsumer flame = buffers.getBuffer(RenderType.eyes(FLAME));
        strip(flame, nozzle, sideX * half, sideY * half, length * flicker(time), brightness * side);
        strip(flame, nozzle, sideX * half * INNER, sideY * half * INNER, length * INNER * flicker(time * INNER_FLICKER), brightness * side);
        // Last: taking another buffer finishes the one the flame went into.
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(GLOW));
        glow(glow, nozzle, radius * HALO_RADIUS, HALO_BEHIND, HALO_COLOUR, brightness);
        glow(glow, nozzle, radius * HOT_RADIUS, HOT_BEHIND, HOT_COLOUR, brightness);
        pose.popPose();
    }

    /** How long the flame is at a moment, as a share of its length: flickering either way about it. */
    private static float flicker(float time) {
        return 1 + FLICKER * (0.6F * Mth.sin(time * FLICKER_SPEED) + 0.4F * Mth.sin(time * FLICKER_SPEED * FLICKER_RIPPLE));
    }

    /**
     * A strip of the flame's picture from the nozzle back along the axis, this far out to either side, drawn both
     * ways round: whichever side the camera sees it from, one of the two faces it.
     */
    private static void strip(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float length, float brightness) {
        vertex(consumer, pose, -x, -y, 0, 0, 0, brightness, brightness, brightness);
        vertex(consumer, pose, x, y, 0, 1, 0, brightness, brightness, brightness);
        vertex(consumer, pose, x, y, -length, 1, 1, brightness, brightness, brightness);
        vertex(consumer, pose, -x, -y, -length, 0, 1, brightness, brightness, brightness);
        vertex(consumer, pose, -x, -y, -length, 0, 1, brightness, brightness, brightness);
        vertex(consumer, pose, x, y, -length, 1, 1, brightness, brightness, brightness);
        vertex(consumer, pose, x, y, 0, 1, 0, brightness, brightness, brightness);
        vertex(consumer, pose, -x, -y, 0, 0, 0, brightness, brightness, brightness);
    }

    /** A disc of the glow, this far behind the nozzle and facing back, drawn both ways round as the strips are. */
    private static void glow(VertexConsumer consumer, PoseStack.Pose pose, float radius, float behind, int colour, float brightness) {
        float red = (colour >> 16 & 0xFF) / 255F * brightness;
        float green = (colour >> 8 & 0xFF) / 255F * brightness;
        float blue = (colour & 0xFF) / 255F * brightness;
        vertex(consumer, pose, -radius, -radius, -behind, 0, 1, red, green, blue);
        vertex(consumer, pose, radius, -radius, -behind, 1, 1, red, green, blue);
        vertex(consumer, pose, radius, radius, -behind, 1, 0, red, green, blue);
        vertex(consumer, pose, -radius, radius, -behind, 0, 0, red, green, blue);
        vertex(consumer, pose, -radius, radius, -behind, 0, 0, red, green, blue);
        vertex(consumer, pose, radius, radius, -behind, 1, 0, red, green, blue);
        vertex(consumer, pose, radius, -radius, -behind, 1, 1, red, green, blue);
        vertex(consumer, pose, -radius, -radius, -behind, 0, 1, red, green, blue);
    }

    /** A point of the flame or the glow, at full brightness whatever the light around, in the colour it adds. */
    private static void vertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, float u, float v,
                               float red, float green, float blue) {
        consumer.addVertex(pose, x, y, z)
            .setColor(red, green, blue, 1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0, 0, -1);
    }

    /** The space the flame can fill at its longest, from its nozzle in the level, the way back and the nacelle's radius. */
    static AABB bounds(Vec3 nozzle, Direction back, float radius) {
        Vec3 tip = nozzle.add(Vec3.atLowerCornerOf(back.getNormal()).scale(radius * FULL_LENGTH * (1 + FLICKER)));
        return new AABB(nozzle, tip).inflate(radius * Math.max(HALF_WIDTH, HALO_RADIUS));
    }
}
