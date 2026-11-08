package dev.skyliners.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.skyliners.SkylinersClientConfig;
import dev.skyliners.autopilot.EdgeOfSpace;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Space, round a camera far over the top of the world, where the rocket climbs (see {@link EdgeOfSpace}): from y 400
 * the sky deepens through darker and darker blues to all but black at y 1,200, and is black by night; the stars fade in
 * from y 500 and are all out by the edge of space, at noon too, more of them than the night shows from the ground; the
 * sun burns white, with no sunrise in it; the clouds far below fade away between y 500 and 800, and the rain and snow
 * round the camera with them; and the land below, as it fades into the distance, is a planet: hazy blue, its rim
 * dipping further under the horizon the higher the camera is, so it curves away under a thin glow of air.
 *
 * <p>NeoForge has no event for the sky's colour, and taking over the overworld's sky would fight any other mod that
 * does, so space is drawn over the game's own sky, right after it ({@code AFTER_SKY}): a dome round the camera, as
 * opaque as the height calls for and in the colour of the sky there; the stars, the sun and the moon over it, since it
 * hides the game's own; then the planet under the rim and the glow along it; by night the northern lights along the
 * rim (see {@link Aurora}); and last, now and then, a shooting star (see {@link ShootingStars}), over the planet when
 * it burns up below the camera. The land fades into the fog's colour at the edge of the world drawn, and the fog takes
 * the planet's colour (see {@link #fog}), so the land meets the planet without a seam. The clouds fade through the
 * colour the game draws them in, set just before it draws them and put back once the weather is drawn; gone, they are
 * left out altogether by the cabin's hold on them (see {@link CloudlessCabin}).
 *
 * <p>All of it goes by the camera each drawing of the world is from, not by the player, so a camera display's picture
 * (see {@link TailCameraFeed}) shows space as its camera sees it. Only in a world with the overworld's sky, never with
 * the camera in water, lava or powder snow, blinded, in a boss's fog, or with the {@code spaceSky} setting off. Under y
 * 400 nothing is drawn or changed; over it, six small draws a frame, the dome and the stars kept on the graphics card,
 * another by night for the northern lights and another while a shooting star streaks.
 */
public final class SpaceSky {
    /** How high the camera must be for anything of space to show, in world blocks: under it, the sky is the game's. */
    private static final double SPACE_FROM = 400;
    /** How high the dome is wholly opaque from, in world blocks: the sky is the colour of its height from there up. */
    private static final double DOME_OPAQUE = 600;
    /**
     * The sky by day at heights, in world blocks: the overworld's own blue, deepening to all but black; between two
     * heights it shades from one to the next, and by night it is black at every height.
     */
    private static final double[] SKY_HEIGHTS = {320, 600, 900, 1200};
    private static final int[] SKY_COLOURS = {0x78A7FF, 0x2E58B8, 0x0E1C52, 0x020306};
    /**
     * How high the stars begin to come out by day and how high they are all out, in world blocks, and how bright they
     * are then, on the scale the game's stars are set by (at most half, on a clear night).
     */
    private static final double STARS_FROM = 500;
    private static final double STARS_OUT = EdgeOfSpace.HEIGHT;
    private static final float STARS_BRIGHTEST = 0.9F;
    /** How high the clouds begin to fade, and how high they are gone, in world blocks. */
    private static final double CLOUDS_FROM = 500;
    private static final double CLOUDS_GONE = 800;
    /** How high the fog the land fades into has taken the planet's colour, in world blocks. */
    private static final double PLANET_FULL = 800;
    /** The planet by day, a hazy blue, and how much of its light it keeps by night, as a share. */
    private static final int PLANET = 0x3D6A99;
    private static final float PLANET_NIGHT = 0.06F;
    /**
     * The glow of the air along the planet's rim: its colour by day, how much of its light it keeps by night, as a
     * share, and how opaque it is at the rim, fading from there.
     */
    private static final int GLOW = 0x6FB4FF;
    private static final float GLOW_NIGHT = 0.25F;
    private static final float GLOW_PEAK = 0.8F;
    /** How far the glow reaches under the rim, onto the planet, and over it, into space, in degrees. */
    private static final double GLOW_UNDER = 3;
    private static final double GLOW_OVER = 8;
    /**
     * How far the planet's rim lies under the horizon, in degrees: so far at {@link #SPACE_FROM}, so much further for
     * each block higher, and at most: the higher the camera, the more the planet curves away under it.
     */
    private static final double DIP_FROM = 2;
    private static final double DIP_A_BLOCK = 12.0 / 800;
    private static final double DIP_MOST = 20;
    /**
     * The sun seen from space, white: its colour, the half width of its square, the radius of the glow round it, as far
     * off as the game draws its own, and the glow's opacity at its middle, fading to nothing at its edge.
     */
    private static final int SUN = 0xFFFCF0;
    private static final float SUN_CORE = 12;
    private static final float SUN_GLOW = 32;
    private static final float SUN_GLOW_OPACITY = 0.35F;
    /** The sun's square as two triangles, its corners a half width out each way. */
    private static final float[][] SUN_SQUARE = {{-1, -1}, {1, -1}, {1, 1}, {-1, -1}, {1, 1}, {-1, 1}};
    /** The moon's half width, as far off as the game draws its own, and the game's picture of its phases. */
    private static final float MOON_SIZE = 20;
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    /**
     * How far off the sun, the moon and the stars are drawn, as the game draws its own, the half width of the dome, a
     * box all one colour so that its shape never shows, how far under the camera the planet is drawn and how far out
     * the glow, in blocks: all well short of the nearest the game's far plane comes.
     */
    private static final float SKY_DISTANCE = 100;
    private static final float DOME_SIZE = 50;
    private static final float PLANET_DEPTH = 2;
    private static final float GLOW_DISTANCE = 4;
    /** How many pieces round the planet's rim and its glow, and the sun's glow, are drawn in. */
    private static final int RIM_PIECES = 64;
    private static final int SUN_PIECES = 16;
    /**
     * The game's own stars, scattered as it scatters them from the seed it does, so the same ones come out: how many
     * tries at one it makes, and each one's half width, at least and how much more at most, in blocks at
     * {@link #SKY_DISTANCE}.
     */
    private static final long GAME_STARS_SEED = 10842;
    private static final int GAME_STARS = 1500;
    private static final float GAME_STAR_SIZE = 0.15F;
    private static final float GAME_STAR_SPREAD = 0.1F;
    /**
     * The fainter stars only space shows, from a seed of their own: how many tries at one, each one's half width, at
     * least and how much more at most, and how bright, at least and how much brighter at most, as a share of the
     * brightest.
     */
    private static final long FAINT_STARS_SEED = 0x5ACE5EEDL;
    private static final int FAINT_STARS = 2500;
    private static final float FAINT_STAR_SIZE = 0.07F;
    private static final float FAINT_STAR_SPREAD = 0.06F;
    private static final float FAINT_STAR_LIGHT = 0.25F;
    private static final float FAINT_STAR_LIGHT_SPREAD = 0.35F;
    /**
     * The corners of the dome, a box round the camera, by the face, each its corners' numbers (x, y and z the bits).
     */
    private static final int[][] DOME_FACES = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 1, 3, 2}, {4, 6, 7, 5}};

    /**
     * The dome and the stars, made the first time space is drawn and kept as long as the game runs, as its own sky is.
     */
    @Nullable
    private static VertexBuffer dome;
    @Nullable
    private static VertexBuffer stars;
    /** Whether the colour the clouds are drawn in is faded for the drawing of the world under way, to be put back. */
    private static boolean fadingClouds;

    private SpaceSky() {
    }

    /** Right after the game's own sky: space over it, as far as the camera is into it. */
    public static void sky(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        Camera camera = event.getCamera();
        ClientLevel level = shownFor(camera);
        if (level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double height = camera.getPosition().y;
        float opacity = ramp(height, SPACE_FROM, DOME_OPAQUE);
        float day = daylight(level, partialTick);
        float[] fogColour = RenderSystem.getShaderFogColor();
        float planetRed = fogColour[0];
        float planetGreen = fogColour[1];
        float planetBlue = fogColour[2];
        float fogStart = RenderSystem.getShaderFogStart();
        float fogEnd = RenderSystem.getShaderFogEnd();
        FogShape fogShape = RenderSystem.getShaderFogShape();
        FogRenderer.setupNoFog();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        Matrix4f pose = event.getModelViewMatrix();
        Matrix4f projection = event.getProjectionMatrix();
        float starlight = Math.max(STARS_BRIGHTEST * ramp(height, STARS_FROM, STARS_OUT), level.getStarBrightness(partialTick));
        drawDome(pose, projection, SolarFlare.tinted(skyAt(height).scale(day), partialTick), opacity);
        drawHeavens(pose, projection, level, partialTick, starlight, opacity);
        double dip = Math.min(DIP_MOST, DIP_FROM + (height - SPACE_FROM) * DIP_A_BLOCK);
        drawPlanet(pose, dip, planetRed, planetGreen, planetBlue);
        drawGlow(pose, dip, Math.max(day, GLOW_NIGHT), opacity);
        Aurora.draw(pose, partialTick, height, dip, day);
        ShootingStars.draw(pose, partialTick, height, dip, starsShown(starlight, opacity));
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderFogStart(fogStart);
        RenderSystem.setShaderFogEnd(fogEnd);
        RenderSystem.setShaderFogShape(fogShape);
    }

    /**
     * The fog the land fades into at the edge of the world drawn, which the game also clears the picture to: high over
     * the world it shades into the planet's haze, as bright as the time of day makes it, so the land fades into the
     * planet drawn round it.
     */
    public static void fog(ViewportEvent.ComputeFogColor event) {
        Camera camera = event.getCamera();
        ClientLevel level = shownFor(camera);
        if (level == null) {
            return;
        }
        float share = ramp(camera.getPosition().y, SPACE_FROM, PLANET_FULL);
        Vec3 planet = Vec3.fromRGB24(PLANET).scale(Math.max(daylight(level, (float) event.getPartialTick()), PLANET_NIGHT));
        event.setRed(Mth.lerp(share, event.getRed(), (float) planet.x));
        event.setGreen(Mth.lerp(share, event.getGreen(), (float) planet.y));
        event.setBlue(Mth.lerp(share, event.getBlue(), (float) planet.z));
    }

    /**
     * Last of all before the clouds are drawn: fades the colour they are drawn in as far as they are faded from the
     * camera. The rain and snow round the camera, drawn after them, fade with them, as the camera rises out of the
     * weather.
     */
    public static void fadeClouds(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        float shown = cloudsShown(event.getCamera());
        if (shown < 1) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, shown);
            fadingClouds = true;
        }
    }

    /**
     * First of all once the weather is drawn, and at the end of the world's drawing at the latest: the colour put back.
     */
    public static void cloudsFaded(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (fadingClouds && (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER || stage == RenderLevelStageEvent.Stage.AFTER_LEVEL)) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            fadingClouds = false;
        }
    }

    /** Whether the clouds have faded from a camera's view altogether, so they are not drawn for it at all. */
    public static boolean cloudsGone(Camera camera) {
        return cloudsShown(camera) <= 0;
    }

    /**
     * How much of the clouds shows from a camera: all of them under {@link #CLOUDS_FROM}, none from
     * {@link #CLOUDS_GONE}.
     */
    private static float cloudsShown(Camera camera) {
        return shownFor(camera) == null ? 1 : 1 - ramp(camera.getPosition().y, CLOUDS_FROM, CLOUDS_GONE);
    }

    /**
     * The level space is shown in from a camera, or null where it is not: too low, the setting off, a world without the
     * overworld's sky, or a camera that sees no sky at all.
     */
    @Nullable
    private static ClientLevel shownFor(Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || camera.getPosition().y < SPACE_FROM || !SkylinersClientConfig.SPACE_SKY.get()) {
            return null;
        }
        boolean overworldSky = level.effects().skyType() == DimensionSpecialEffects.SkyType.NORMAL;
        boolean seesSky = camera.getFluidInCamera() == FogType.NONE && !blinded(camera) && !minecraft.gui.getBossOverlay().shouldCreateWorldFog();
        return overworldSky && seesSky ? level : null;
    }

    /** Whether what the camera sees through is blind or in darkness, when the game draws no sky either. */
    private static boolean blinded(Camera camera) {
        return camera.getEntity() instanceof LivingEntity seeing && (seeing.hasEffect(MobEffects.BLINDNESS) || seeing.hasEffect(MobEffects.DARKNESS));
    }

    /** How much of the day's light there is, from 0 at night to 1 by day, as the game lights its sky by. */
    private static float daylight(ClientLevel level, float partialTick) {
        return Mth.clamp(Mth.cos(level.getTimeOfDay(partialTick) * (float) (Math.PI * 2)) * 2.0F + 0.5F, 0.0F, 1.0F);
    }

    /** How far a height is from one to another, as a share, eased in and out. */
    static float ramp(double height, double from, double to) {
        float along = (float) Mth.clamp((height - from) / (to - from), 0, 1);
        return along * along * (3 - 2 * along);
    }

    /**
     * The colour of the sky by day at a height (see {@link #SKY_HEIGHTS}), from 0 to 1 for each of red, green and blue.
     */
    static Vec3 skyAt(double height) {
        for (int key = 1; key < SKY_HEIGHTS.length; key++) {
            if (height <= SKY_HEIGHTS[key]) {
                double along = Mth.clamp((height - SKY_HEIGHTS[key - 1]) / (SKY_HEIGHTS[key] - SKY_HEIGHTS[key - 1]), 0, 1);
                return Vec3.fromRGB24(SKY_COLOURS[key - 1]).lerp(Vec3.fromRGB24(SKY_COLOURS[key]), along);
            }
        }
        return Vec3.fromRGB24(SKY_COLOURS[SKY_COLOURS.length - 1]);
    }

    /** The dome round the camera, all of it the colour given, as opaque as given. */
    private static void drawDome(Matrix4f pose, Matrix4f projection, Vec3 colour, float opacity) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor((float) colour.x, (float) colour.y, (float) colour.z, opacity);
        VertexBuffer buffer = dome();
        buffer.bind();
        buffer.drawWithShader(pose, projection, GameRenderer.getPositionShader());
        VertexBuffer.unbind();
    }

    /**
     * The stars, the sun and the moon, turned with the time of day as the game turns its own and added to the sky
     * behind them as it adds its own, the stars as bright as the height or the night makes them (given, on the scale
     * the game's stars are set by).
     */
    private static void drawHeavens(Matrix4f view, Matrix4f projection, ClientLevel level, float partialTick, float starlight, float opacity) {
        PoseStack pose = new PoseStack();
        pose.mulPose(view);
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(partialTick) * 360.0F));
        Matrix4f turned = pose.last().pose();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        drawStars(turned, projection, starlight, opacity);
        drawSun(turned, opacity, partialTick);
        drawMoon(turned, level.getMoonPhase(), opacity);
    }

    /**
     * How bright the stars show, as a share of their brightest, from 0 to 1: as bright as given, on the scale the
     * game's own are, in a colour as bright as that and as opaque again, fading in with the dome (see
     * {@link #drawStars}).
     */
    private static float starsShown(float light, float opacity) {
        float share = light / STARS_BRIGHTEST;
        return share * share * opacity;
    }

    /** The stars, as bright as given, on the scale the game's own are, fading in with the dome. */
    private static void drawStars(Matrix4f pose, Matrix4f projection, float light, float opacity) {
        if (light <= 0) {
            return;
        }
        RenderSystem.setShaderColor(light, light, light, light * opacity);
        VertexBuffer buffer = stars();
        buffer.bind();
        buffer.drawWithShader(pose, projection, GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
    }

    /**
     * The sun where the game puts its own, white: a square, and a round glow fading out from it; swollen and burning
     * white-gold in a solar flare, its corona, tendrils and rings of light round it (see {@link SolarFlare}).
     */
    private static void drawSun(Matrix4f pose, float opacity, float partialTick) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, opacity);
        float flare = SolarFlare.strength(partialTick);
        int colour = FastColor.ARGB32.lerp(flare, FastColor.ARGB32.opaque(SUN), FastColor.ARGB32.opaque(SolarFlare.FLARING)) & 0xFFFFFF;
        float coreSize = SUN_CORE * (1 + SolarFlare.SWELLS * flare);
        int core = FastColor.ARGB32.opaque(colour);
        int glowing = FastColor.ARGB32.color(Math.round(SUN_GLOW_OPACITY * 255), colour);
        int faded = FastColor.ARGB32.color(0, colour);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int piece = 0; piece < SUN_PIECES; piece++) {
            double from = Math.PI * 2 * piece / SUN_PIECES;
            double to = Math.PI * 2 * (piece + 1) / SUN_PIECES;
            builder.addVertex(pose, 0, SKY_DISTANCE, 0).setColor(glowing);
            builder.addVertex(pose, (float) Math.cos(from) * SUN_GLOW, SKY_DISTANCE, (float) Math.sin(from) * SUN_GLOW).setColor(faded);
            builder.addVertex(pose, (float) Math.cos(to) * SUN_GLOW, SKY_DISTANCE, (float) Math.sin(to) * SUN_GLOW).setColor(faded);
        }
        for (float[] corner : SUN_SQUARE) {
            builder.addVertex(pose, corner[0] * coreSize, SKY_DISTANCE, corner[1] * coreSize).setColor(core);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
        SolarFlare.draw(pose, SUN_CORE, SUN_GLOW, SKY_DISTANCE, opacity, partialTick);
    }

    /** The moon where the game puts its own, in its phase tonight, as the game draws it. */
    private static void drawMoon(Matrix4f pose, int phase, float opacity) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MOON);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, opacity);
        float left = (phase % 4) / 4.0F;
        float top = (phase / 4 % 2) / 2.0F;
        float right = left + 0.25F;
        float bottom = top + 0.5F;
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(pose, -MOON_SIZE, -SKY_DISTANCE, MOON_SIZE).setUv(right, bottom);
        builder.addVertex(pose, MOON_SIZE, -SKY_DISTANCE, MOON_SIZE).setUv(left, bottom);
        builder.addVertex(pose, MOON_SIZE, -SKY_DISTANCE, -MOON_SIZE).setUv(left, top);
        builder.addVertex(pose, -MOON_SIZE, -SKY_DISTANCE, -MOON_SIZE).setUv(right, top);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /**
     * The planet under its rim, so many degrees under the horizon, in the colour of the fog the land fades into: a disc
     * under the camera reaching out to the rim, wholly opaque, so that over the land it is what the game would show.
     */
    private static void drawPlanet(Matrix4f pose, double dip, float red, float green, float blue) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionShader);
        RenderSystem.setShaderColor(red, green, blue, 1.0F);
        float rim = (float) (PLANET_DEPTH / Math.tan(Math.toRadians(dip)));
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        builder.addVertex(pose, 0, -PLANET_DEPTH, 0);
        for (int piece = 0; piece <= RIM_PIECES; piece++) {
            double round = Math.PI * 2 * piece / RIM_PIECES;
            builder.addVertex(pose, (float) Math.cos(round) * rim, -PLANET_DEPTH, (float) Math.sin(round) * rim);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /**
     * The glow of the air along the planet's rim, so many degrees under the horizon, as bright as given: a band round
     * the camera, brightest at the rim and fading out above it and below, added to what is behind it.
     */
    private static void drawGlow(Matrix4f pose, double dip, float light, float opacity) {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.setShaderColor(light, light, light, opacity);
        int lit = FastColor.ARGB32.color(Math.round(GLOW_PEAK * 255), GLOW);
        int faded = FastColor.ARGB32.color(0, GLOW);
        float under = elevation(-dip - GLOW_UNDER);
        float rim = elevation(-dip);
        float over = elevation(-dip + GLOW_OVER);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int piece = 0; piece < RIM_PIECES; piece++) {
            double from = Math.PI * 2 * piece / RIM_PIECES;
            double to = Math.PI * 2 * (piece + 1) / RIM_PIECES;
            float x0 = (float) Math.cos(from) * GLOW_DISTANCE;
            float z0 = (float) Math.sin(from) * GLOW_DISTANCE;
            float x1 = (float) Math.cos(to) * GLOW_DISTANCE;
            float z1 = (float) Math.sin(to) * GLOW_DISTANCE;
            builder.addVertex(pose, x0, under, z0).setColor(faded);
            builder.addVertex(pose, x1, under, z1).setColor(faded);
            builder.addVertex(pose, x1, rim, z1).setColor(lit);
            builder.addVertex(pose, x0, rim, z0).setColor(lit);
            builder.addVertex(pose, x0, rim, z0).setColor(lit);
            builder.addVertex(pose, x1, rim, z1).setColor(lit);
            builder.addVertex(pose, x1, over, z1).setColor(faded);
            builder.addVertex(pose, x0, over, z0).setColor(faded);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /**
     * How high over the camera a point {@link #GLOW_DISTANCE} out is at so many degrees over the horizon, in blocks.
     */
    private static float elevation(double degrees) {
        return (float) (GLOW_DISTANCE * Math.tan(Math.toRadians(degrees)));
    }

    /** The dome round the camera, made once. */
    private static VertexBuffer dome() {
        if (dome == null) {
            dome = upload(box());
        }
        return dome;
    }

    /** A box round the camera, {@link #DOME_SIZE} out each way, its faces seen from in and out alike. */
    private static MeshData box() {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        for (int[] face : DOME_FACES) {
            for (int corner : face) {
                builder.addVertex((corner & 1) == 0 ? -DOME_SIZE : DOME_SIZE, (corner & 2) == 0 ? -DOME_SIZE : DOME_SIZE,
                    (corner & 4) == 0 ? -DOME_SIZE : DOME_SIZE);
            }
        }
        return builder.buildOrThrow();
    }

    /** The stars, made once: the game's own and the fainter ones round them. */
    private static VertexBuffer stars() {
        if (stars == null) {
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            scatter(builder, RandomSource.create(GAME_STARS_SEED), GAME_STARS, GAME_STAR_SIZE, GAME_STAR_SPREAD, 1, 0);
            scatter(builder, RandomSource.create(FAINT_STARS_SEED), FAINT_STARS, FAINT_STAR_SIZE, FAINT_STAR_SPREAD, FAINT_STAR_LIGHT,
                FAINT_STAR_LIGHT_SPREAD);
            stars = upload(builder.buildOrThrow());
        }
        return stars;
    }

    /**
     * Scatters stars over the sky as the game scatters its own, drawing on the random numbers in the same order: so
     * many tries at a point in a ball, each star toward one far enough out of its middle and not past its edge, so many
     * blocks across at least and at most so many more, as bright as given at least and at most so much brighter (the
     * game's own all alike, drawing no number for it).
     */
    private static void scatter(BufferBuilder builder, RandomSource random, int tries, float size, float sizeSpread, float light, float lightSpread) {
        for (int each = 0; each < tries; each++) {
            float x = random.nextFloat() * 2.0F - 1.0F;
            float y = random.nextFloat() * 2.0F - 1.0F;
            float z = random.nextFloat() * 2.0F - 1.0F;
            float half = size + random.nextFloat() * sizeSpread;
            float length = Mth.lengthSquared(x, y, z);
            if (length <= 0.010000001F || length >= 1.0F) {
                continue;
            }
            Vector3f toward = new Vector3f(x, y, z).normalize(SKY_DISTANCE);
            float turn = (float) (random.nextDouble() * (float) Math.PI * 2.0);
            float shade = lightSpread > 0 ? light + random.nextFloat() * lightSpread : light;
            star(builder, toward, half, turn, shade);
        }
    }

    /**
     * A star where the game would draw one, a small square turned on the sky: each of its corners on from the one
     * before, as the game lays them, in the shade of white given.
     */
    private static void star(BufferBuilder builder, Vector3f toward, float half, float turn, float shade) {
        Quaternionf facing = new Quaternionf().rotateTo(new Vector3f(0.0F, 0.0F, -1.0F), toward).rotateZ(turn);
        int colour = FastColor.ARGB32.colorFromFloat(1.0F, shade, shade, shade);
        builder.addVertex(toward.add(new Vector3f(half, -half, 0.0F).rotate(facing))).setColor(colour);
        builder.addVertex(toward.add(new Vector3f(half, half, 0.0F).rotate(facing))).setColor(colour);
        builder.addVertex(toward.add(new Vector3f(-half, half, 0.0F).rotate(facing))).setColor(colour);
        builder.addVertex(toward.add(new Vector3f(-half, -half, 0.0F).rotate(facing))).setColor(colour);
    }

    /** A mesh put on the graphics card for good. */
    private static VertexBuffer upload(MeshData mesh) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(mesh);
        VertexBuffer.unbind();
        return buffer;
    }
}
