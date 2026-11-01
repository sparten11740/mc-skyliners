package dev.skyliners.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.block.CockpitDoorBlock;
import dev.skyliners.block.CockpitDoorBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Draws a cockpit door's panel, shut in its doorway or wherever it is in its swing, from the block entity in its bottom
 * left part: one panel, its top corners rounded, with a round porthole high in its middle and a round handle rose on
 * either face by the edge away from its hinge, a lever on each (see the cockpit_door pictures in
 * tools/generate_textures.mjs, whose porthole, corners and handle the constants here match). The porthole is glazed
 * right through, so the flight deck shows through it from the aisle. Opening, it swings out about its hinge a quarter
 * turn, until it stands along the aisle's side. The panel stands in the middle of its doorway, between half a block of
 * frame either side, drawn here too, lined like the bulkhead; a door built before it had a frame has the panel right
 * across its doorway.
 *
 * <p>Drawn in the door's own frame: x across the doorway to the right as seen from the aisle it swings into, y up from
 * the floor, z out toward the aisle, the doorway's outer face at z 1.
 */
public class CockpitDoorRenderer implements BlockEntityRenderer<CockpitDoorBlockEntity> {
    private static final float THICK = CockpitDoorBlock.THICKNESS / 16F;
    private static final float WIDE = CockpitDoorBlock.PANEL_WIDTH;
    /** How far the door swings open, in degrees. */
    private static final float SWING = 90;
    /** The radius of its rounded top corners, in blocks. */
    private static final float CORNER = 0.75F;
    /** The arcs of the rounded corners and the porthole, in pieces. */
    private static final int CORNER_PIECES = 6;
    private static final int PORTHOLE_PIECES = 16;
    /** The porthole's radius, and how far its middle is below the top of the door, in blocks. */
    private static final float PORTHOLE = 6 / 16F;
    private static final float PORTHOLE_DROP = 1;
    /** The handle's middle, in blocks across from the hinge's edge and up from the floor; its lever's size. */
    private static final float HANDLE_X = 27.5F / 16;
    private static final float HANDLE_Y = 1;
    private static final float LEVER_LONG = 0.32F;
    private static final float LEVER_TALL = 0.07F;
    private static final float LEVER_STANDS = 0.1F;
    /**
     * The lock's lamp over the handle on each face: how far over the handle's middle, how big, in blocks, and how far
     * it stands out of the face; red while the door is locked from the flight deck, green while it is not, and lit
     * whatever the light round it.
     */
    private static final float LAMP_OVER = 0.22F;
    private static final float LAMP_SIZE = 0.09F;
    private static final float LAMP_STANDS = 0.02F;
    private static final int LAMP_LOCKED = 0xE02424;
    private static final int LAMP_OPEN = 0x2EC24A;
    private static final ResourceLocation LAMP = ResourceLocation.withDefaultNamespace("block/white_concrete");
    private static final ResourceLocation EDGE = texture("cockpit_door_edge");
    private static final ResourceLocation LEVER = texture("landing_gear");
    private static final ResourceLocation GLASS_PICTURE = texture("plug_door_glass");
    /** The bulkhead's lining, which the doorway's arch is finished in, and the lining round a doorway's sides. */
    private static final ResourceLocation LINING = texture("hull_lining");
    private static final ResourceLocation REVEAL = texture("hull_lining_reveal");
    /** Across the lining's picture, clear of the joint at its left edge: where it is plain. */
    private static final float PLAIN = 0.5F;
    private static final int FEWEST_ROWS = 3;
    /** The porthole's glass: see-through, as the plug doors' windows are (see {@link PlugDoorRenderer}). */
    private static final RenderType GLASS = RenderType.create(Skyliners.MOD_ID + "_cockpit_door_glass", DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, false, true, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "block/" + name);
    }

    @Override
    public void render(CockpitDoorBlockEntity door, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = door.getLevel();
        BlockState state = door.getBlockState();
        int rows = door.rows();
        if (level == null || !CockpitDoorBlock.isController(state) || rows == 0) {
            return;
        }
        float progress = door.progress(partialTick);
        Direction out = state.getValue(CockpitDoorBlock.FACING);
        boolean left = state.getValue(CockpitDoorBlock.HINGE) == CockpitDoorBlock.Hinge.LEFT;
        int columns = CockpitDoorBlock.columns(state);
        // The panel in the middle of the doorway, its frame either side; a door built before it had one has none.
        float jamb = (columns - WIDE) / 2;
        Lights lights = Lights.of(level, door.getBlockPos(), out, rows, columns, progress > 0);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-out.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        arch(buffers, pose.last(), level, door.getBlockPos(), out, rows, columns, jamb);
        if (jamb > 0) {
            jambs(buffers, pose.last(), level, door.getBlockPos(), out, rows, columns, jamb);
        }
        // About the hinge, on the doorway's outer face, the far edge swinging out toward the aisle.
        float hingeX = left ? jamb : jamb + WIDE;
        float turn = (left ? -SWING : SWING) * ease(progress);
        pose.rotateAround(Axis.YP.rotationDegrees(turn), hingeX, 0, 1);
        panel(buffers, pose.last(), lights, rows, jamb, left);
        lamps(buffers, pose.last(), jamb, left, door.locked() ? LAMP_LOCKED : LAMP_OPEN);
        pose.popPose();
    }

    /**
     * The panel: its faces block by block with the light where each is, its edge round the rounded top and down its
     * sides, the porthole's reveal through it and the levers on both faces; then the porthole's glass. Only the quads
     * facing the camera are drawn. The panel stands this far in from the doorway's left edge, past its frame, and its
     * faces are cut at the blocks' edges, each piece lit by the block it is in.
     */
    private static void panel(MultiBufferSource buffers, PoseStack.Pose pose, Lights lights, int rows, float jamb, boolean left) {
        TextureAtlasSprite face = sprite(texture("cockpit_door_" + Math.clamp(rows, FEWEST_ROWS, CockpitDoorBlock.MOST_ROWS)));
        TextureAtlasSprite edge = sprite(EDGE);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Vector3f eye = Sight.eye(pose);
        float back = 1 - THICK;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < lights.columns(); column++) {
                float x0 = Math.max(column, jamb);
                float x1 = Math.min(column + 1, jamb + WIDE);
                float y0 = row;
                float y1 = row + 1;
                // The picture is drawn hinged on the left, seen from the aisle; mirrored for a door hinged on the right.
                float u0 = left ? (x0 - jamb) / WIDE : 1 - (x0 - jamb) / WIDE;
                float u1 = left ? (x1 - jamb) / WIDE : 1 - (x1 - jamb) / WIDE;
                float v0 = 1 - y0 / rows;
                float v1 = 1 - y1 / rows;
                if (x0 < x1 && eye.z > 1) {
                    quad(solid, pose, face, lights.at(column, row, true), 1, 0, 0, 1,
                        x0, y0, 1, u0, v0, x1, y0, 1, u1, v0, x1, y1, 1, u1, v1, x0, y1, 1, u0, v1);
                }
                if (x0 < x1 && eye.z < back) {
                    quad(solid, pose, face, lights.at(column, row, false), 1, 0, 0, -1,
                        x1, y0, back, u1, v0, x0, y0, back, u0, v0, x0, y1, back, u0, v1, x1, y1, back, u1, v1);
                }
            }
        }
        // The edge right round the panel, and the porthole's reveal, each piece facing away from what it bounds.
        List<float[]> outline = outline(rows, jamb);
        for (int i = 0; i < outline.size(); i++) {
            float[] a = outline.get(i);
            float[] b = outline.get((i + 1) % outline.size());
            strip(solid, pose, edge, lights, eye, a[0], a[1], b[0], b[1], back, 0.85F);
        }
        float cx = jamb + WIDE / 2;
        float cy = rows - PORTHOLE_DROP;
        for (int i = 0; i < PORTHOLE_PIECES; i++) {
            double a = 2 * Math.PI * i / PORTHOLE_PIECES;
            double b = 2 * Math.PI * (i + 1) / PORTHOLE_PIECES;
            // Round the hole the other way, so the reveal faces into it.
            strip(solid, pose, edge, lights, eye, cx + PORTHOLE * (float) Math.cos(b), cy + PORTHOLE * (float) Math.sin(b),
                cx + PORTHOLE * (float) Math.cos(a), cy + PORTHOLE * (float) Math.sin(a), back, 0.7F);
        }
        TextureAtlasSprite lever = sprite(LEVER);
        float handleX = jamb + (left ? HANDLE_X : WIDE - HANDLE_X);
        // The levers point from the rose toward the middle of the door.
        float from = left ? handleX - LEVER_LONG : handleX - LEVER_TALL / 2;
        float to = left ? handleX + LEVER_TALL / 2 : handleX + LEVER_LONG;
        int handleColumn = (int) handleX;
        int handleLight = lights.at(handleColumn, 1, true);
        box(solid, pose, lever, handleLight, from, HANDLE_Y - LEVER_TALL / 2, 1, to, HANDLE_Y + LEVER_TALL / 2, 1 + LEVER_STANDS);
        box(solid, pose, lever, lights.at(handleColumn, 1, false), from, HANDLE_Y - LEVER_TALL / 2, back - LEVER_STANDS, to, HANDLE_Y + LEVER_TALL / 2, back);

        // The glass last, in its own buffer, in the middle of the panel: the panel's faces hide all of it but the hole.
        VertexConsumer glass = buffers.getBuffer(GLASS);
        TextureAtlasSprite pane = sprite(GLASS_PICTURE);
        float mid = 1 - THICK / 2;
        float gx0 = cx - PORTHOLE;
        float gx1 = cx + PORTHOLE;
        float gy0 = cy - PORTHOLE;
        float gy1 = cy + PORTHOLE;
        int row = Math.min((int) cy, rows - 1);
        boolean front = eye.z > mid;
        quad(glass, pose, pane, lights.at((int) cx, row, front), 1, 0, 0, front ? 1 : -1, front
            ? new float[]{gx0, gy0, mid, 0, 1, gx1, gy0, mid, 1, 1, gx1, gy1, mid, 1, 0, gx0, gy1, mid, 0, 0}
            : new float[]{gx1, gy0, mid, 0, 1, gx0, gy0, mid, 1, 1, gx0, gy1, mid, 1, 0, gx1, gy1, mid, 0, 0});
    }

    /**
     * The doorway's arch: its top corners filled in, as rounded as the panel's, so the doorway is the panel's shape and
     * the bulkhead closes round it. Lined like the bulkhead on both faces, with a reveal round the arch through the
     * doorway's depth; it stays where it is as the door swings. Its corners stand at the panel's, this far in from the
     * doorway's edges, past its frame.
     */
    private static void arch(MultiBufferSource buffers, PoseStack.Pose pose, Level level, BlockPos door, Direction out, int rows,
                             int columns, float jamb) {
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        TextureAtlasSprite lining = sprite(LINING);
        TextureAtlasSprite reveal = sprite(REVEAL);
        Vector3f eye = Sight.eye(pose);
        BlockPos top = door.above(rows - 1);
        for (boolean left : new boolean[]{true, false}) {
            BlockPos part = top.relative(out.getCounterClockWise(), left ? 0 : columns - 1);
            int aisle = SableClient.light(level, part.relative(out));
            int deck = SableClient.light(level, part.relative(out.getOpposite()));
            float cx = left ? jamb + CORNER : jamb + WIDE - CORNER;
            float cy = rows - CORNER;
            float cornerX = left ? jamb : jamb + WIDE;
            float from = left ? 90 : 0;
            for (int i = 0; i < CORNER_PIECES; i++) {
                double a = Math.toRadians(from + 90.0 * i / CORNER_PIECES);
                double b = Math.toRadians(from + 90.0 * (i + 1) / CORNER_PIECES);
                float ax = cx + CORNER * (float) Math.cos(a);
                float ay = cy + CORNER * (float) Math.sin(a);
                float bx = cx + CORNER * (float) Math.cos(b);
                float by = cy + CORNER * (float) Math.sin(b);
                // Each piece a triangle from the doorway's corner, a quad with its corner doubled, in the lining's plain
                // middle: a corner may straddle two blocks, and the joint at their edge would not line up.
                float[] u = {PLAIN, PLAIN, PLAIN};
                float[] v = {0, rows - ay, rows - by};
                if (eye.z > 1) {
                    quad(solid, pose, lining, aisle, 1, 0, 0, 1,
                        cornerX, rows, 1, u[0], v[0], bx, by, 1, u[2], v[2], ax, ay, 1, u[1], v[1], cornerX, rows, 1, u[0], v[0]);
                }
                if (eye.z < 0) {
                    quad(solid, pose, lining, deck, 1, 0, 0, -1,
                        cornerX, rows, 0, u[0], v[0], ax, ay, 0, u[1], v[1], bx, by, 0, u[2], v[2], cornerX, rows, 0, u[0], v[0]);
                }
                // The reveal faces into the doorway, toward the arc's middle.
                float nx = cx - (ax + bx) / 2;
                float ny = cy - (ay + by) / 2;
                float length = (float) Math.hypot(nx, ny);
                if ((eye.x - ax) * nx + (eye.y - ay) * ny > 0) {
                    quad(solid, pose, reveal, aisle, 0.8F, nx / length, ny / length, 0,
                        ax, ay, 0, 0, 0, bx, by, 0, 1, 0, bx, by, 1, 1, 1, ax, ay, 1, 0, 1);
                }
            }
        }
    }

    /**
     * The doorway's frame either side of the panel, this wide, from the floor to the arch: its faces lined like the
     * bulkhead's, block by block, and a reveal down its side into the doorway.
     */
    private static void jambs(MultiBufferSource buffers, PoseStack.Pose pose, Level level, BlockPos door, Direction out, int rows,
                              int columns, float jamb) {
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Vector3f eye = Sight.eye(pose);
        for (int row = 0; row < rows; row++) {
            jamb(solid, pose, eye, level, door.above(row), out, row, 0, 0, jamb);
            jamb(solid, pose, eye, level, door.above(row), out, row, columns - 1, columns - jamb, columns);
        }
    }

    /**
     * One block of the frame, in the column of the doorway it stands in, from x0 to x1 across it: its faces to the
     * aisle and the flight deck, lined in step with the bulkhead's blocks, and its side facing into the doorway.
     */
    private static void jamb(VertexConsumer solid, PoseStack.Pose pose, Vector3f eye, Level level, BlockPos bottom, Direction out,
                             int row, int column, float x0, float x1) {
        TextureAtlasSprite lining = sprite(LINING);
        BlockPos part = bottom.relative(out.getCounterClockWise(), column);
        int aisle = SableClient.light(level, part.relative(out));
        int y0 = row;
        int y1 = row + 1;
        float u0 = x0 - column;
        float u1 = x1 - column;
        if (eye.z > 1) {
            quad(solid, pose, lining, aisle, 1, 0, 0, 1, x0, y0, 1, u0, 1, x1, y0, 1, u1, 1, x1, y1, 1, u1, 0, x0, y1, 1, u0, 0);
        }
        if (eye.z < 0) {
            int deck = SableClient.light(level, part.relative(out.getOpposite()));
            quad(solid, pose, lining, deck, 1, 0, 0, -1, x1, y0, 0, u1, 1, x0, y0, 0, u0, 1, x0, y1, 0, u0, 0, x1, y1, 0, u1, 0);
        }
        // The side away from the doorway's edge, facing the panel.
        boolean left = column == 0;
        float side = left ? x1 : x0;
        if (left ? eye.x > side : eye.x < side) {
            quad(solid, pose, sprite(REVEAL), aisle, 0.8F, left ? 1 : -1, 0, 0,
                side, y0, 0, 0, 0, side, y0, 1, 0, 1, side, y1, 1, 1, 1, side, y1, 0, 1, 0);
        }
    }

    /**
     * The panel's outline seen from the aisle, this far in from the doorway's left edge, round from the bottom left
     * corner: along the floor, up the right side, round the rounded top corners and down the left side.
     */
    private static List<float[]> outline(int rows, float jamb) {
        List<float[]> points = new ArrayList<>();
        points.add(new float[]{jamb, 0});
        points.add(new float[]{jamb + WIDE, 0});
        arc(points, jamb + WIDE - CORNER, rows - CORNER, 0);
        arc(points, jamb + CORNER, rows - CORNER, 90);
        return points;
    }

    /** A rounded corner's points, a quarter circle anticlockwise from this angle, in degrees. */
    private static void arc(List<float[]> points, float cx, float cy, float from) {
        for (int i = 0; i <= CORNER_PIECES; i++) {
            double angle = Math.toRadians(from + 90.0 * i / CORNER_PIECES);
            points.add(new float[]{cx + CORNER * (float) Math.cos(angle), cy + CORNER * (float) Math.sin(angle)});
        }
    }

    /**
     * A strip through the panel from back to front between two points of an outline running anticlockwise, facing out
     * of it to the right of the way it runs, shaded this much and lit by the block it is in.
     */
    private static void strip(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, Lights lights, Vector3f eye,
                              float ax, float ay, float bx, float by, float back, float shade) {
        float nx = by - ay;
        float ny = ax - bx;
        float length = (float) Math.hypot(nx, ny);
        if (length == 0 || (eye.x - ax) * nx + (eye.y - ay) * ny <= 0) {
            return;
        }
        nx /= length;
        ny /= length;
        int column = Math.clamp((int) ((ax + bx) / 2), 0, lights.columns() - 1);
        int row = Math.clamp((int) ((ay + by) / 2), 0, lights.rows() - 1);
        quad(consumer, pose, sprite, lights.at(column, row, true), shade, nx, ny, 0,
            ax, ay, 1, 0, 1, bx, by, 1, 1, 1, bx, by, back, 1, 0, ax, ay, back, 0, 0);
    }

    /**
     * The lock's lamps over the handle, on both faces of the panel, glowing this colour: red while it is locked, so
     * whoever cannot read sees it from the aisle, and green while it is not.
     */
    private static void lamps(MultiBufferSource buffers, PoseStack.Pose pose, float jamb, boolean left, int colour) {
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        TextureAtlasSprite white = sprite(LAMP);
        float x = jamb + (left ? HANDLE_X : WIDE - HANDLE_X);
        float y = HANDLE_Y + LAMP_OVER;
        float back = 1 - THICK;
        lamp(solid, pose, white, colour, x - LAMP_SIZE / 2, y - LAMP_SIZE / 2, 1, x + LAMP_SIZE / 2, y + LAMP_SIZE / 2, 1 + LAMP_STANDS);
        lamp(solid, pose, white, colour, x - LAMP_SIZE / 2, y - LAMP_SIZE / 2, back - LAMP_STANDS, x + LAMP_SIZE / 2, y + LAMP_SIZE / 2, back);
    }

    /** A small lit box from one corner to the other, in one colour on every face. */
    private static void lamp(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, int colour,
                             float x0, float y0, float z0, float x1, float y1, float z1) {
        float[][] faces = {
            {0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
            {0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0},
            {0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0},
            {0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1},
            {1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
            {-1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0},
        };
        for (float[] face : faces) {
            for (int i = 0; i < 4; i++) {
                consumer.addVertex(pose, face[3 + i * 3], face[4 + i * 3], face[5 + i * 3])
                    .setColor(0xFF000000 | colour)
                    .setUv(sprite.getU(0.5F), sprite.getV(0.5F))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose, face[0], face[1], face[2]);
            }
        }
    }

    /** A box from one corner to the other, every face showing the whole sprite. */
    private static void box(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light,
                            float x0, float y0, float z0, float x1, float y1, float z1) {
        quad(consumer, pose, sprite, light, 1, 0, 0, 1, x0, y0, z1, 0, 1, x1, y0, z1, 1, 1, x1, y1, z1, 1, 0, x0, y1, z1, 0, 0);
        quad(consumer, pose, sprite, light, 1, 0, 0, -1, x1, y0, z0, 0, 1, x0, y0, z0, 1, 1, x0, y1, z0, 1, 0, x1, y1, z0, 0, 0);
        quad(consumer, pose, sprite, light, 1, 0, 1, 0, x0, y1, z1, 0, 1, x1, y1, z1, 1, 1, x1, y1, z0, 1, 0, x0, y1, z0, 0, 0);
        quad(consumer, pose, sprite, light, 0.6F, 0, -1, 0, x0, y0, z0, 0, 1, x1, y0, z0, 1, 1, x1, y0, z1, 1, 0, x0, y0, z1, 0, 0);
        quad(consumer, pose, sprite, light, 0.8F, 1, 0, 0, x1, y0, z1, 0, 1, x1, y0, z0, 1, 1, x1, y1, z0, 1, 0, x1, y1, z1, 0, 0);
        quad(consumer, pose, sprite, light, 0.8F, -1, 0, 0, x0, y0, z0, 0, 1, x0, y0, z1, 1, 1, x0, y1, z1, 1, 0, x0, y1, z0, 0, 0);
    }

    /**
     * A quad facing (nx, ny, nz), shaded this much (0 to 1), its four corners each (x, y, z, u, v) with u and v across
     * the sprite, 0 to 1.
     */
    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, float shade,
                             float nx, float ny, float nz, float... corners) {
        for (int i = 0; i < 4; i++) {
            int at = i * 5;
            consumer.addVertex(pose, corners[at], corners[at + 1], corners[at + 2])
                .setColor(shade, shade, shade, 1)
                .setUv(sprite.getU(corners[at + 3]), sprite.getV(corners[at + 4]))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        }
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    /** Eased in and out, so the panel starts and stops gently. */
    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    /** Drawn whether or not the doorway's section is: swung open, the panel stands in the aisle beside it. */
    @Override
    public boolean shouldRenderOffScreen(CockpitDoorBlockEntity door) {
        return true;
    }

    /** The doorway, and all round it as far as the panel swings. */
    @Override
    public AABB getRenderBoundingBox(CockpitDoorBlockEntity door) {
        return new AABB(door.getBlockPos()).expandTowards(0, CockpitDoorBlock.MOST_ROWS, 0).inflate(CockpitDoorBlock.COLUMNS + 1, 1, CockpitDoorBlock.COLUMNS + 1);
    }

    /**
     * The light each block of the panel is drawn with, looked up once a frame for each block and face (aisle side after
     * flight deck side, block after block along each row). Shut, its aisle face is lit by the aisle and its flight deck
     * face by the doorway; swung open, all of it by the aisle.
     */
    private record Lights(int[] lights, int rows, int columns) {
        static Lights of(Level level, BlockPos door, Direction out, int rows, int columns, boolean moved) {
            int[] lights = new int[rows * columns * 2];
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    BlockPos part = door.relative(out.getCounterClockWise(), column).above(row);
                    int at = (row * columns + column) * 2;
                    lights[at] = SableClient.light(level, moved ? part.relative(out) : part);
                    lights[at + 1] = SableClient.light(level, part.relative(out));
                }
            }
            return new Lights(lights, rows, columns);
        }

        /**
         * The light of a block of the panel; for one outside it (the handle's or the porthole's, on a door broken down to
         * its bottom row), the nearest block's.
         */
        int at(int column, int row, boolean aisle) {
            int r = Math.clamp(row, 0, rows - 1);
            int c = Math.clamp(column, 0, columns - 1);
            return lights[(r * columns + c) * 2 + (aisle ? 1 : 0)];
        }
    }
}
