package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.PlugDoorBlock;
import dev.aerolarge.block.PlugDoorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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

/**
 * Draws a plug door's panel, shut in its doorway or wherever it is in its swing, from the block entity in its bottom
 * left part. Shut, it is one panel flush with the hull, white skin like the plating round it with the door's seam
 * right round it as a stepped gap, one window in the middle of its top and a flush handle by the edge away from its
 * hinge; inside, a padded lining stands proud of it, an oval in off-white plastic with the window set deep into it and
 * the handle on it (see {@link PlugDoorMesh}). Opening, a door hinged at the side lifts a little and moves out of the
 * frame, then is carried round on its arm, the way an airliner's is: the arm swings out and forward about a hinge
 * beside the doorway while the panel stays square to the hull, so it ends up flat against the hull beside the doorway,
 * lining and all just clear of it, its outside still facing out. A door hinged along its top, the cargo door, swings up
 * and out about its top edge instead. Shutting runs the same way back.
 *
 * <p>Drawn in the door's own frame: x across the doorway to the right as seen from outside, y up from the sill, z out,
 * the hull's outer face at z 1.
 */
public class PlugDoorRenderer implements BlockEntityRenderer<PlugDoorBlockEntity> {
    /** How deep the door is, panel and lining, in blocks: the arm holds it by the lining's face. */
    private static final float DEPTH = PlugDoorBlock.DEPTH / 16F;
    /** How far the panel lifts as it comes out of the frame, in blocks: off the stops it rests on shut. */
    private static final float LIFT = 1 / 16F;
    /** How far from the doorway's edge the open panel stands, in blocks. */
    private static final float GAP = 0.05F;
    /** The share of the swing spent lifting and moving out of the frame, before the arm swings it away. */
    private static final float PLUG_SHARE = 0.25F;
    /** How far a door hinged along its top swings up, in degrees: past level, as a cargo door does. */
    private static final float TOP_SWING = 110;
    /** The arm's thickness and height, in blocks: thinner than two of the lining's steps, so its faces clear them. */
    private static final float ARM_THICK = 0.1F;
    private static final float ARM_TALL = 0.3F;
    /**
     * How far off the door is still drawn, in blocks: as far as the airliner itself is, or a shut door would leave a
     * hole in the hull.
     */
    private static final int VIEW_DISTANCE = 1024;
    private static final ResourceLocation ARM = texture("landing_gear");
    /** The fewest rows a door's picture is drawn for: as short as the blueprints make a door. */
    private static final int FEWEST_ROWS = 2;

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "block/" + name);
    }

    @Override
    public void render(PlugDoorBlockEntity door, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = door.getLevel();
        BlockState state = door.getBlockState();
        int rows = door.rows();
        if (level == null || !PlugDoorBlock.isController(state) || rows == 0) {
            return;
        }
        float progress = door.progress(partialTick);
        Direction out = state.getValue(PlugDoorBlock.FACING);
        PlugDoorBlock.Hinge hinge = state.getValue(PlugDoorBlock.HINGE);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Painter paint = new Painter(consumer, level, door.getBlockPos(), out, progress > 0);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-out.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        if (hinge == PlugDoorBlock.Hinge.TOP) {
            pose.pushPose();
            pose.translate(0, rows, 1);
            pose.mulPose(Axis.XN.rotationDegrees(TOP_SWING * ease(progress)));
            pose.translate(0, -rows, -1);
            panel(paint, pose.last(), rows, hinge);
            pose.popPose();
        } else {
            swing(paint, pose, rows, hinge, progress);
        }
        pose.popPose();
    }

    /**
     * A door hinged at its side, this far through its swing: out of the frame, then round on its arm, which reaches from
     * a hinge just past the doorway's edge to the middle of the lining's face. A parallel arm turning half a circle
     * carries the panel round with it square to the hull, from the doorway to beside it, as far again as the arm is
     * long.
     */
    private static void swing(Painter paint, PoseStack pose, int rows, PlugDoorBlock.Hinge hinge, float progress) {
        // Which way across the panel moves: to the right (1) for a hinge on the right, seen from outside, or the left.
        int side = hinge == PlugDoorBlock.Hinge.RIGHT ? 1 : -1;
        float plug = ease(Math.clamp(progress / PLUG_SHARE, 0F, 1F));
        float turn = (float) Math.PI * ease(Math.clamp((progress - PLUG_SHARE) / (1 - PLUG_SHARE), 0F, 1F));
        float arm = (PlugDoorBlock.COLUMNS + GAP) / 2;
        float dx = side * arm * (1 - (float) Math.cos(turn));
        float dy = LIFT * plug;
        float dz = PlugDoorBlockEntity.PLUG * plug + arm * (float) Math.sin(turn);
        if (turn > 0) {
            float hingeX = PlugDoorBlock.COLUMNS / 2F + side * arm;
            float inner = 1 - DEPTH + PlugDoorBlockEntity.PLUG;
            arm(paint, pose, hingeX, rows / 2F + dy, inner, PlugDoorBlock.COLUMNS / 2F + dx, 1 - DEPTH + dz);
        }
        pose.pushPose();
        pose.translate(dx, dy, dz);
        panel(paint, pose.last(), rows, hinge);
        pose.popPose();
    }

    /** The arm, a bar at mid height from its hinge at (x, z) to where it holds the panel. */
    private static void arm(Painter paint, PoseStack pose, float x, float y, float z, float toX, float toZ) {
        float length = (float) Math.hypot(toX - x, toZ - z);
        pose.pushPose();
        pose.translate(x, y - ARM_TALL / 2, z);
        pose.mulPose(Axis.YP.rotation((float) Math.atan2(-(toZ - z), toX - x)));
        paint.box(pose.last(), 0, 0, -ARM_THICK / 2, length, ARM_TALL, ARM_THICK / 2, paint.sprite(ARM));
        pose.popPose();
    }

    /**
     * The panel, layer on layer (see {@link PlugDoorMesh}): the door's picture across its outside and the lining's
     * across its inside, each block drawn with the light where it is. Shut, its edges are left out: they stand hidden
     * in the frame, where they would only fight with the frame's own faces.
     */
    private static void panel(Painter paint, PoseStack.Pose pose, int rows, PlugDoorBlock.Hinge hinge) {
        TextureAtlasSprite outside = paint.sprite(face(hinge, rows, ""));
        TextureAtlasSprite inside = paint.sprite(face(hinge, rows, "_inside"));
        for (PlugDoorMesh.Quad quad : PlugDoorMesh.of(hinge, rows)) {
            if (quad.side() == PlugDoorMesh.Side.EDGE && !paint.moved()) {
                continue;
            }
            boolean in = quad.side() == PlugDoorMesh.Side.INSIDE;
            paint.quad(pose, in ? inside : outside, paint.light(quad.column(), quad.row(), !in), quad.shade(),
                quad.nx(), quad.ny(), quad.nz(), quad.corners());
        }
    }

    /** The picture of a door hinged this way and this many rows tall: its outside, or with "_inside", its lining. */
    private static ResourceLocation face(PlugDoorBlock.Hinge hinge, int rows, String side) {
        String kind = hinge == PlugDoorBlock.Hinge.TOP ? "top" : "side";
        return texture("plug_door_" + kind + "_" + Math.clamp(rows, FEWEST_ROWS, PlugDoorBlock.MOST_ROWS) + side);
    }

    /** Eased in and out, so the panel starts and stops gently. */
    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** The doorway, and all round it as far as the panel swings. */
    @Override
    public AABB getRenderBoundingBox(PlugDoorBlockEntity door) {
        float reach = 2 * PlugDoorBlock.COLUMNS + 1;
        return new AABB(door.getBlockPos()).expandTowards(0, PlugDoorBlock.MOST_ROWS, 0).inflate(reach, 1, reach);
    }

    /**
     * Where the door's quads go: the buffer, and the light each block of the panel is drawn with. Shut, its outside is
     * lit by the air outside the doorway and its inside by the cabin; out of its frame, all of it by the air outside.
     */
    private record Painter(VertexConsumer consumer, Level level, BlockPos door, Direction out, boolean moved) {
        TextureAtlasSprite sprite(ResourceLocation texture) {
            return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        }

        int light(int column, int row, boolean outside) {
            BlockPos part = door.relative(out.getCounterClockWise(), column).above(row);
            return SableClient.light(level, part.relative(outside || moved ? out : out.getOpposite()));
        }

        /**
         * A quad facing (nx, ny, nz), shaded this much (0 to 1), its four corners each (x, y, z, u, v) with u and v
         * across the sprite, 0 to 1.
         */
        void quad(PoseStack.Pose pose, TextureAtlasSprite sprite, int light, float shade, float nx, float ny, float nz, float... corners) {
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

        /** A box from one corner to the other, every face showing the whole sprite, lit by the air outside the door. */
        void box(PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, TextureAtlasSprite sprite) {
            int light = light(0, 0, true);
            quad(pose, sprite, light, 1, 0, 0, 1, x0, y0, z1, 0, 1, x1, y0, z1, 1, 1, x1, y1, z1, 1, 0, x0, y1, z1, 0, 0);
            quad(pose, sprite, light, 1, 0, 0, -1, x1, y0, z0, 0, 1, x0, y0, z0, 1, 1, x0, y1, z0, 1, 0, x1, y1, z0, 0, 0);
            quad(pose, sprite, light, 1, 0, 1, 0, x0, y1, z1, 0, 1, x1, y1, z1, 1, 1, x1, y1, z0, 1, 0, x0, y1, z0, 0, 0);
            quad(pose, sprite, light, 1, 0, -1, 0, x0, y0, z0, 0, 1, x1, y0, z0, 1, 1, x1, y0, z1, 1, 0, x0, y0, z1, 0, 0);
            quad(pose, sprite, light, 1, 1, 0, 0, x1, y0, z1, 0, 1, x1, y0, z0, 1, 1, x1, y1, z0, 1, 0, x1, y1, z1, 0, 0);
            quad(pose, sprite, light, 1, -1, 0, 0, x0, y0, z0, 0, 1, x0, y0, z1, 1, 1, x0, y1, z1, 1, 0, x0, y1, z0, 0, 0);
        }
    }
}
