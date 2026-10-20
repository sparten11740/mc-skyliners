package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.PlugDoorBlock;
import dev.aerolarge.block.PlugDoorBlockEntity;
import java.util.List;
import net.minecraft.client.Minecraft;
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
 * Draws a plug door's panel, shut in its doorway or wherever it is in its swing, from the block entity in its bottom
 * left part. Shut, it is one panel flush with the hull, white skin like the plating round it with the door's seam
 * right round it as a stepped gap, one window in the middle of its top and a flush handle by the edge away from its
 * hinge; inside, a padded lining bulges out of it, a big rounded cushion in off-white plastic with the window set deep
 * into it and the handle on it (see {@link PlugDoorMesh}). The window is glazed right through, so the world shows
 * through it either way. Opening, a door hinged at the side lifts a little and moves out of the frame, then is carried
 * round on its arm, the way an airliner's is: the arm swings out and forward about a hinge beside the doorway while
 * the panel stays square to the hull, so it ends up flat against the hull beside the doorway, lining and all just
 * clear of it, its outside still facing out. A door hinged along its top, the cargo door, swings up and out about its
 * top edge instead, first moving out of a doorway the hull above it stands proud of. Shutting runs the same way back.
 *
 * <p>Drawn in the door's own frame: x across the doorway to the right as seen from outside, y up from the sill, z out,
 * the hull's outer face at z 1.
 */
public class PlugDoorRenderer implements BlockEntityRenderer<PlugDoorBlockEntity> {
    /** How deep the door is, panel and lining, in blocks: the arm runs along the lining's crown. */
    private static final float DEPTH = PlugDoorBlock.DEPTH / 16F;
    /** How far the panel lifts as it comes out of the frame, in blocks: off the stops it rests on shut. */
    private static final float LIFT = 1 / 16F;
    /** How far from the doorway's edge the open panel stands, in blocks. */
    private static final float GAP = 0.05F;
    /** The share of the swing spent lifting and moving out of the frame, before the arm swings it away. */
    private static final float PLUG_SHARE = 0.25F;
    /** How far a door hinged along its top swings up, in degrees: past level, as a cargo door does. */
    private static final float TOP_SWING = 110;
    /** The arm's thickness and height, in blocks. */
    private static final float ARM_THICK = 0.1F;
    private static final float ARM_TALL = 0.3F;
    /**
     * How far off the door is still drawn, in blocks: as far as the airliner itself is, or a shut door would leave a
     * hole in the hull. That is further than it might seem: a level's blocks are drawn as far as the player's render
     * distance, and Sable by default sends an assembled airliner to players up to 320 blocks off and draws it all. From
     * outside, little more than the door's skin faces the camera (see {@link #panel}), so a far door costs little.
     */
    private static final int VIEW_DISTANCE = 1024;
    private static final ResourceLocation ARM = texture("landing_gear");
    private static final ResourceLocation GLASS_PICTURE = texture("plug_door_glass");
    /** The fewest rows a door's picture is drawn for: as short as the blueprints make a door. */
    private static final int FEWEST_ROWS = 2;
    /**
     * The window's glass: see-through, lit as the panel is and from its own side only, leaving the depth of what lies
     * behind it alone, so whatever is drawn after the door (water, clouds, rain) still shows through it.
     */
    private static final RenderType GLASS = RenderType.create(Aerolarge.MOD_ID + "_plug_door_glass", DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE, false, true, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
            .setTextureState(new RenderStateShard.TextureStateShard(InventoryMenu.BLOCK_ATLAS, false, false))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(false));

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
        Painter paint = Painter.of(buffers, level, door.getBlockPos(), out, rows, progress > 0);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-out.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        if (hinge == PlugDoorBlock.Hinge.TOP) {
            swingUp(paint, pose, rows, door.standoff(), progress);
        } else {
            swing(paint, pose, rows, hinge, door.standoff(), progress);
        }
        pose.popPose();
    }

    /**
     * A door hinged along its top, this far through its swing: up and out about its top edge, once out of the doorway
     * as far as the hull above it stands out past it, if it does.
     */
    private static void swingUp(Painter paint, PoseStack pose, int rows, float standoff, float progress) {
        boolean recessed = standoff > 0;
        float plug = recessed ? ease(Math.clamp(progress / PLUG_SHARE, 0F, 1F)) : 0;
        float swing = recessed ? ease(Math.clamp((progress - PLUG_SHARE) / (1 - PLUG_SHARE), 0F, 1F)) : ease(progress);
        pose.pushPose();
        pose.translate(0, rows, 1 + standoff * plug);
        pose.mulPose(Axis.XN.rotationDegrees(TOP_SWING * swing));
        pose.translate(0, -rows, -1);
        panel(paint, pose.last(), rows, PlugDoorBlock.Hinge.TOP);
        pose.popPose();
    }

    /**
     * A door hinged at its side, this far through its swing: out of the frame, then round on its arm, which reaches from
     * a hinge just past the doorway's edge to the middle of the lining's crown. A parallel arm turning half a circle
     * carries the panel round with it square to the hull, from the doorway to beside it, as far again as the arm is
     * long.
     */
    private static void swing(Painter paint, PoseStack pose, int rows, PlugDoorBlock.Hinge hinge, float standoff, float progress) {
        // Which way across the panel moves: to the right (1) for a hinge on the right, seen from outside, or the left.
        int side = hinge == PlugDoorBlock.Hinge.RIGHT ? 1 : -1;
        float plug = ease(Math.clamp(progress / PLUG_SHARE, 0F, 1F));
        float turn = (float) Math.PI * ease(Math.clamp((progress - PLUG_SHARE) / (1 - PLUG_SHARE), 0F, 1F));
        float arm = (PlugDoorBlock.COLUMNS + GAP) / 2;
        float dx = side * arm * (1 - (float) Math.cos(turn));
        float dy = LIFT * plug;
        float dz = standoff * plug + arm * (float) Math.sin(turn);
        if (turn > 0) {
            // The arm runs along the crown, half sunk into it, its hinge as far out past the hull as the crown is once out
            // of the frame.
            float crown = 1 - DEPTH;
            float hingeX = PlugDoorBlock.COLUMNS / 2F + side * arm;
            arm(paint, pose, hingeX, rows / 2F + dy, crown + standoff, PlugDoorBlock.COLUMNS / 2F + dx, crown + dz);
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
     * The panel (see {@link PlugDoorMesh}): the door's picture across its outside and the lining's across its inside,
     * each block drawn with the light where it is, then the window's glass. Shut, its edges are left out: they stand
     * hidden in the frame, where they would only fight with the frame's own faces. Only the quads facing the camera
     * are drawn, as the back of every one is never seen; that leaves out about half the panel from inside the cabin,
     * and from outside most of it, every face of the lining turning away from the camera there.
     */
    private static void panel(Painter paint, PoseStack.Pose pose, int rows, PlugDoorBlock.Hinge hinge) {
        TextureAtlasSprite outside = paint.sprite(face(hinge, rows, ""));
        TextureAtlasSprite inside = paint.sprite(face(hinge, rows, "_inside"));
        TextureAtlasSprite glass = paint.sprite(GLASS_PICTURE);
        List<PlugDoorMesh.Quad> quads = PlugDoorMesh.of(hinge, rows);
        Vector3f eye = Sight.eye(pose);
        for (PlugDoorMesh.Quad quad : quads) {
            if (quad.glass() || quad.side() == PlugDoorMesh.Side.EDGE && !paint.moved() || !quad.faces(eye)) {
                continue;
            }
            boolean in = quad.side() == PlugDoorMesh.Side.INSIDE;
            paint.quad(paint.solid(), pose, in ? inside : outside, paint.light(quad.column(), quad.row(), !in), quad.shade(),
                quad.nx(), quad.ny(), quad.nz(), quad.corners());
        }
        // The glass last, in its own buffer: fetching that one draws what went into the panel's.
        VertexConsumer see = paint.glass();
        for (PlugDoorMesh.Quad quad : quads) {
            if (quad.glass() && quad.faces(eye)) {
                boolean in = quad.side() == PlugDoorMesh.Side.INSIDE;
                paint.quad(see, pose, glass, paint.light(quad.column(), quad.row(), !in), quad.shade(),
                    quad.nx(), quad.ny(), quad.nz(), quad.corners());
            }
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

    /**
     * Drawn whether or not the section the doorway is in is: swung out, the panel stands in the open air beside the
     * hull, where it is in plain view from close by while the doorway's own section is hidden behind the hull.
     */
    @Override
    public boolean shouldRenderOffScreen(PlugDoorBlockEntity door) {
        return true;
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
     * Where the door's quads go: the panel's buffer and the glass's, and the light each block of the panel is drawn
     * with, looked up once a frame for each block and side ({@code lights}, outside after inside, block after block
     * along each row). Shut, its outside is lit by the air outside the doorway and its inside by the cabin; out of its
     * frame, all of it by the air outside.
     */
    private record Painter(MultiBufferSource buffers, VertexConsumer solid, int[] lights, boolean moved) {
        static Painter of(MultiBufferSource buffers, Level level, BlockPos door, Direction out, int rows, boolean moved) {
            int[] lights = new int[rows * PlugDoorBlock.COLUMNS * 2];
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < PlugDoorBlock.COLUMNS; column++) {
                    BlockPos part = door.relative(out.getCounterClockWise(), column).above(row);
                    int at = (row * PlugDoorBlock.COLUMNS + column) * 2;
                    lights[at] = SableClient.light(level, part.relative(moved ? out : out.getOpposite()));
                    lights[at + 1] = SableClient.light(level, part.relative(out));
                }
            }
            return new Painter(buffers, buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), lights, moved);
        }

        TextureAtlasSprite sprite(ResourceLocation texture) {
            return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        }

        /** The glass's buffer: once it is fetched, nothing more goes into the panel's. */
        VertexConsumer glass() {
            return buffers.getBuffer(GLASS);
        }

        int light(int column, int row, boolean outside) {
            return lights[(row * PlugDoorBlock.COLUMNS + column) * 2 + (outside ? 1 : 0)];
        }

        /**
         * A quad facing (nx, ny, nz), shaded this much (0 to 1), its four corners each (x, y, z, u, v) with u and v
         * across the sprite, 0 to 1.
         */
        void quad(VertexConsumer consumer, PoseStack.Pose pose, TextureAtlasSprite sprite, int light, float shade, float nx, float ny, float nz, float... corners) {
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
            quad(solid, pose, sprite, light, 1, 0, 0, 1, x0, y0, z1, 0, 1, x1, y0, z1, 1, 1, x1, y1, z1, 1, 0, x0, y1, z1, 0, 0);
            quad(solid, pose, sprite, light, 1, 0, 0, -1, x1, y0, z0, 0, 1, x0, y0, z0, 1, 1, x0, y1, z0, 1, 0, x1, y1, z0, 0, 0);
            quad(solid, pose, sprite, light, 1, 0, 1, 0, x0, y1, z1, 0, 1, x1, y1, z1, 1, 1, x1, y1, z0, 1, 0, x0, y1, z0, 0, 0);
            quad(solid, pose, sprite, light, 1, 0, -1, 0, x0, y0, z0, 0, 1, x1, y0, z0, 1, 1, x1, y0, z1, 1, 0, x0, y0, z1, 0, 0);
            quad(solid, pose, sprite, light, 1, 1, 0, 0, x1, y0, z1, 0, 1, x1, y0, z0, 1, 1, x1, y1, z0, 1, 0, x1, y1, z1, 0, 0);
            quad(solid, pose, sprite, light, 1, -1, 0, 0, x0, y0, z0, 0, 1, x0, y0, z1, 1, 1, x0, y1, z1, 1, 0, x0, y1, z0, 0, 0);
        }
    }
}
