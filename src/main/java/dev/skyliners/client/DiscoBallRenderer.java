package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.block.DiscoBallBlock;
import dev.skyliners.block.DiscoBallBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Draws a disco ball: a ball faceted all over with little mirror tiles in rings from pole to pole, fewer of them round
 * the rings near the poles, each ring's tiles set off from the next's, as a real one's are; round its middle for the
 * way it is put up (see {@link DiscoBallBlock#centre}), turning about the upright through it as far as it has turned
 * at this partial tick, so it turns smoothly. Each tile is flat, so each catches the light its own way, and some
 * brighter than others; while the ball is on they take on a little of its spots' colours. Wherever a tile faces just
 * right to throw one of the room's lights into the eye, it glints: a four-pointed star of light over it, twinkling, in
 * the colour of the spot that light goes with.
 */
public class DiscoBallRenderer implements BlockEntityRenderer<DiscoBallBlockEntity> {
    private static final ResourceLocation MIRRORS = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/block/disco_ball.png");
    private static final ResourceLocation GLINT = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/misc/disco_glint.png");
    /** How many rings of tiles go round the ball from pole to pole, how many tiles the widest has, and the fewest. */
    private static final int RINGS = 10;
    private static final int WIDEST = 24;
    private static final int FEWEST = 4;
    /** How bright the dullest tile is, as a share of the brightest. */
    private static final float DULLEST = 0.65F;
    /** How much of the spots' colours the tiles show while the ball is on. */
    private static final float TINT = 0.3F;
    /** How many of the spots' colours the tiles are shared out among. */
    private static final int TILE_COLOURS = 12;
    /** Where a tile's corners are on its mirror's picture, in the order its corners go round. */
    private static final float[][] CORNER_UVS = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
    /** The seed the tiles' brightness and colours are picked with, the same for every ball. */
    private static final long TILES_SEED = 0xD15C0;
    /** The lights the tiles glint with, fixed in the room: the ways to them from the ball. */
    private static final Vector3f[] GLINT_LIGHTS = {
        new Vector3f(-0.6F, 0.7F, -0.4F).normalize(),
        new Vector3f(0.7F, 0.5F, 0.5F).normalize(),
        new Vector3f(0.2F, -0.4F, 0.9F).normalize(),
    };
    /** How sharply a tile glints: the higher, the more nearly just right it has to face. */
    private static final float SHARPNESS = 80;
    /** How big a glint is across at its brightest, in blocks. */
    private static final float GLINT_SIZE = 0.6F;
    /** Fainter glints than this are not drawn. */
    private static final float FAINTEST = 0.05F;
    /** How far a glint twinkles down from full, and how fast, in radians a tick. */
    private static final float TWINKLE = 0.35F;
    private static final float TWINKLE_SPEED = 0.9F;
    /** How far over its tile a glint is drawn, in blocks, so the tile does not cover it. */
    private static final float OVER_TILE = 0.02F;
    /** How much of a glint is the white of the light, the rest its spot's colour. */
    private static final float GLINT_WHITE = 0.5F;
    private static final List<Tile> TILES = tiles();

    /**
     * A mirror tile, round the ball's middle as it lies unturned: its corners (counter-clockwise seen from outside),
     * its middle, the way it faces, how brightly it shines, and which of the spots' colours it shows.
     */
    private record Tile(Vector3f[] corners, Vector3f middle, Vector3f normal, float shine, int colour) {
    }

    @Override
    public void render(DiscoBallBlockEntity ball, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (ball.getLevel() == null) {
            return;
        }
        if (!Sight.ahead(pose.last())) {
            return;
        }
        float power = ball.power(partialTick);
        float ticks = (float) DrawingClock.ticks(partialTick);
        pose.pushPose();
        Vec3 centre = DiscoBallBlock.centre(ball.getBlockState());
        pose.translate(centre.x, centre.y, centre.z);
        // The room's ways as drawn, before the ball turns: the lights it glints with stay put.
        Matrix3f room = new Matrix3f(pose.last().normal());
        pose.mulPose(Axis.YP.rotation(ball.turned(partialTick)));
        PoseStack.Pose turned = pose.last();
        float[][] colours = new float[TILE_COLOURS][];
        for (int i = 0; i < colours.length; i++) {
            colours[i] = rgb(DiscoLights.colour(i, ticks));
        }
        float tint = TINT * power;
        VertexConsumer mirrors = buffers.getBuffer(RenderType.entitySolid(MIRRORS));
        for (Tile tile : TILES) {
            float[] rgb = colours[tile.colour()];
            float r = tile.shine() * Mth.lerp(tint, 1, rgb[0]);
            float g = tile.shine() * Mth.lerp(tint, 1, rgb[1]);
            float b = tile.shine() * Mth.lerp(tint, 1, rgb[2]);
            Vector3f n = tile.normal();
            for (int corner = 0; corner < CORNER_UVS.length; corner++) {
                Vector3f at = tile.corners()[corner];
                mirrors.addVertex(turned, at.x, at.y, at.z).setColor(r, g, b, 1).setUv(CORNER_UVS[corner][0], CORNER_UVS[corner][1])
                    .setOverlay(overlay).setLight(light).setNormal(turned, n.x, n.y, n.z);
            }
        }
        if (power > 0) {
            glints(turned, room, buffers.getBuffer(RenderType.eyes(GLINT)), power, ticks, colours);
        }
        pose.popPose();
    }

    /**
     * The glints, over the tiles that face just right to throw one of the room's lights to the camera: halfway between
     * the way to that light and the way to the camera. Worked out where the tiles are drawn, the camera at the middle.
     */
    private static void glints(PoseStack.Pose turned, Matrix3f room, VertexConsumer glints, float power, float ticks, float[][] colours) {
        Vector3f[] lights = new Vector3f[GLINT_LIGHTS.length];
        for (int i = 0; i < lights.length; i++) {
            lights[i] = room.transform(GLINT_LIGHTS[i], new Vector3f());
        }
        // The camera's left and up, among the ball's own ways, to draw each glint square to the camera.
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Matrix3f back = new Matrix3f(turned.normal()).transpose();
        Vector3f left = back.transform(camera.getLeftVector(), new Vector3f());
        Vector3f up = back.transform(camera.getUpVector(), new Vector3f());
        Vector3f toCamera = new Vector3f();
        Vector3f facing = new Vector3f();
        Vector3f half = new Vector3f();
        for (Tile tile : TILES) {
            turned.pose().transformPosition(tile.middle(), toCamera).negate().normalize();
            turned.normal().transform(tile.normal(), facing);
            if (facing.dot(toCamera) <= 0) {
                // Round the back of the ball, out of sight.
                continue;
            }
            float glint = 0;
            int brightest = 0;
            for (int i = 0; i < lights.length; i++) {
                float seen = (float) Math.pow(Math.max(0, facing.dot(half.set(lights[i]).add(toCamera).normalize())), SHARPNESS);
                if (seen > glint) {
                    glint = seen;
                    brightest = i;
                }
            }
            float twinkle = 1 - TWINKLE * (0.5F + 0.5F * Mth.sin(ticks * TWINKLE_SPEED + tile.colour()));
            float strength = glint * power * twinkle;
            if (strength >= FAINTEST) {
                glint(glints, turned, tile, left, up, strength, colours[brightest]);
            }
        }
    }

    /** One glint over a tile, as big and as bright as its strength, square to the camera. */
    private static void glint(VertexConsumer glints, PoseStack.Pose turned, Tile tile, Vector3f left, Vector3f up, float strength, float[] rgb) {
        Vector3f middle = new Vector3f(tile.normal()).mul(OVER_TILE).add(tile.middle());
        float size = GLINT_SIZE / 2 * strength;
        float r = strength * Mth.lerp(GLINT_WHITE, rgb[0], 1);
        float g = strength * Mth.lerp(GLINT_WHITE, rgb[1], 1);
        float b = strength * Mth.lerp(GLINT_WHITE, rgb[2], 1);
        // Counter-clockwise as the camera sees it: bottom left, bottom right, top right, top left.
        float[][] corners = {{1, -1, 0, 1}, {-1, -1, 1, 1}, {-1, 1, 1, 0}, {1, 1, 0, 0}};
        for (float[] corner : corners) {
            float x = middle.x + (left.x * corner[0] + up.x * corner[1]) * size;
            float y = middle.y + (left.y * corner[0] + up.y * corner[1]) * size;
            float z = middle.z + (left.z * corner[0] + up.z * corner[1]) * size;
            glints.addVertex(turned, x, y, z).setColor(r, g, b, 1).setUv(corner[2], corner[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(turned, tile.normal().x, tile.normal().y, tile.normal().z);
        }
    }

    private static float[] rgb(int colour) {
        return new float[]{(colour >> 16 & 0xFF) / 255F, (colour >> 8 & 0xFF) / 255F, (colour & 0xFF) / 255F};
    }

    /** The ball's tiles, ring by ring from the bottom pole up. */
    private static List<Tile> tiles() {
        RandomSource random = RandomSource.create(TILES_SEED);
        List<Tile> tiles = new ArrayList<>();
        for (int ring = 0; ring < RINGS; ring++) {
            float bottom = Mth.PI * ring / RINGS - Mth.HALF_PI;
            float top = bottom + Mth.PI / RINGS;
            int count = Math.max(FEWEST, Math.round(WIDEST * Mth.cos((bottom + top) / 2)));
            // Every other ring set off by half a tile, as a real ball's rings are.
            float offset = ring % 2 == 0 ? 0 : 0.5F;
            for (int i = 0; i < count; i++) {
                float from = Mth.TWO_PI * (i + offset) / count;
                float to = Mth.TWO_PI * (i + 1 + offset) / count;
                Vector3f[] corners = {point(bottom, from), point(bottom, to), point(top, to), point(top, from)};
                Vector3f middle = new Vector3f();
                for (Vector3f corner : corners) {
                    middle.add(corner);
                }
                middle.div(corners.length);
                tiles.add(new Tile(corners, middle, new Vector3f(middle).normalize(), Mth.lerp(random.nextFloat(), DULLEST, 1),
                    random.nextInt(TILE_COLOURS)));
            }
        }
        return tiles;
    }

    /** A point on the ball, so far up from its equator and round from south toward east, in radians. */
    private static Vector3f point(float up, float round) {
        float radius = (float) DiscoBallBlock.RADIUS;
        float level = Mth.cos(up);
        return new Vector3f(radius * level * Mth.sin(round), radius * Mth.sin(up), radius * level * Mth.cos(round));
    }

    /** Its glints reach a little past the ball's block. */
    @Override
    public AABB getRenderBoundingBox(DiscoBallBlockEntity ball) {
        return new AABB(ball.getBlockPos()).inflate(GLINT_SIZE / 2);
    }
}
