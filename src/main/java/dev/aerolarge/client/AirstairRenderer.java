package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.AirstairFlight;
import dev.aerolarge.block.Airstairs;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws integral airstairs from their controller, wherever they are between stowed and down (see
 * {@link AirstairFlight}): an airliner's hatch below its door, and the flight with its treads, stringers and handrails,
 * as the controller's clock says between the last tick and this one. Drawn in the stairs' own frame (see
 * {@link Airstairs}), the served door's or a zeppelin's lower deck's at the head of her boarding stairs, so they come
 * out the right way whichever way the airliner or zeppelin was built, and aboard one assembled wherever it stands. Each
 * part is lit by the light where it is.
 */
public class AirstairRenderer<T extends BlockEntity & Airstairs> implements BlockEntityRenderer<T> {
    /** How far off the stairs are still drawn, in blocks: they are big enough to see from across the apron. */
    private static final int VIEW_DISTANCE = 256;
    /** How far the stairs and their bay reach from the controller, in blocks, past the flight's own length. */
    private static final int REACH_MARGIN = 16;
    private static final Map<AirstairFlight.Part, ResourceLocation> TEXTURES = new EnumMap<>(Map.of(
        AirstairFlight.Part.TREAD, texture("airstair_tread"),
        AirstairFlight.Part.NOSING, texture("airstair_nosing"),
        AirstairFlight.Part.STRINGER, texture("airstair_stringer"),
        AirstairFlight.Part.RAIL, texture("airstair_rail"),
        AirstairFlight.Part.POST, texture("airstair_rail"),
        AirstairFlight.Part.HATCH, texture("airstair_hatch")
    ));
    /** The hatch's edges and inside: the plain skin it is cut from. */
    private static final ResourceLocation SKIN = texture("airframe_panel");

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "block/" + name);
    }

    @Override
    public void render(T airstairs, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = airstairs.getLevel();
        AirstairFlight.Motion motion = airstairs.motion(partialTick);
        Airstairs.Frame frame = airstairs.frame();
        if (level == null || motion == null || frame == null) {
            return;
        }
        Direction out = frame.out();
        Painter paint = new Painter(buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)), level, frame.at(), out, frame.right());
        BlockPos from = frame.at().subtract(airstairs.getBlockPos());
        pose.pushPose();
        pose.translate(from.getX(), from.getY(), from.getZ());
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-out.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        AirstairFlight.draw(motion, (box, min, max, kind) -> paint.box(pose, box, min, max, kind));
        pose.popPose();
    }

    /**
     * Drawn whether or not the section its controller stands in is: the controller is in the hull's jamb, and seen from
     * close by outside the hull hides that section, while the stairs out on the apron are in plain view.
     */
    @Override
    public boolean shouldRenderOffScreen(T airstairs) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** Everything round the controller, as far as the stairs reach down and out, and the bay reaches in. */
    @Override
    public AABB getRenderBoundingBox(T airstairs) {
        AirstairFlight.Motion motion = airstairs.motion(0);
        int reach = (motion == null ? 0 : motion.rise()) + REACH_MARGIN;
        return new AABB(airstairs.getBlockPos()).inflate(reach);
    }

    /**
     * Where the boxes go: the buffer, and the light each is drawn with, found where its middle is, from the stairs'
     * frame (x across to the right, y up, z out with the hull's face at z 1) back to the block it is in.
     */
    private record Painter(VertexConsumer consumer, Level level, BlockPos frame, Direction out, Direction right) {
        TextureAtlasSprite sprite(ResourceLocation texture) {
            return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
        }

        int light(Matrix4f box, Vector3f min, Vector3f max) {
            Vector3f middle = box.transformPosition(new Vector3f(min).add(max).mul(0.5F));
            BlockPos at = frame.relative(right, Mth.floor(middle.x)).above(Mth.floor(middle.y)).relative(out, Mth.floor(middle.z));
            return SableClient.light(level, at);
        }

        /**
         * A box, every face textured by the block: as much of its texture as the face is big, from its corner, so a
         * part shows its texture at the airliner's own pixel size. The hatch shows its whole picture outside, and
         * plain skin round its edges and inside.
         */
        void box(PoseStack stack, Matrix4f box, Vector3f min, Vector3f max, AirstairFlight.Part part) {
            stack.pushPose();
            stack.mulPose(box);
            PoseStack.Pose pose = stack.last();
            int light = light(box, min, max);
            TextureAtlasSprite face = sprite(TEXTURES.get(part));
            TextureAtlasSprite edge = part == AirstairFlight.Part.HATCH ? sprite(SKIN) : face;
            float x0 = min.x;
            float y0 = min.y;
            float z0 = min.z;
            float x1 = max.x;
            float y1 = max.y;
            float z1 = max.z;
            float dx = x1 - x0;
            float dy = y1 - y0;
            float dz = z1 - z0;
            if (part == AirstairFlight.Part.HATCH) {
                quad(pose, face, light, 0, 0, 1, x0, y0, z1, 0, 1, x1, y0, z1, 1, 1, x1, y1, z1, 1, 0, x0, y1, z1, 0, 0);
            } else {
                quad(pose, face, light, 0, 0, 1, x0, y0, z1, 0, px(dy), x1, y0, z1, px(dx), px(dy), x1, y1, z1, px(dx), 0, x0, y1, z1, 0, 0);
            }
            quad(pose, edge, light, 0, 0, -1, x1, y0, z0, 0, px(dy), x0, y0, z0, px(dx), px(dy), x0, y1, z0, px(dx), 0, x1, y1, z0, 0, 0);
            quad(pose, face, light, 0, 1, 0, x0, y1, z1, 0, px(dz), x1, y1, z1, px(dx), px(dz), x1, y1, z0, px(dx), 0, x0, y1, z0, 0, 0);
            quad(pose, edge, light, 0, -1, 0, x0, y0, z0, 0, px(dz), x1, y0, z0, px(dx), px(dz), x1, y0, z1, px(dx), 0, x0, y0, z1, 0, 0);
            quad(pose, edge, light, 1, 0, 0, x1, y0, z1, 0, px(dy), x1, y0, z0, px(dz), px(dy), x1, y1, z0, px(dz), 0, x1, y1, z1, 0, 0);
            quad(pose, edge, light, -1, 0, 0, x0, y0, z0, 0, px(dy), x0, y0, z1, px(dz), px(dy), x0, y1, z1, px(dz), 0, x0, y1, z0, 0, 0);
            stack.popPose();
        }

        /** How far across a sprite a length is, 0 to 1: a block is the whole sprite, a pixel of texture a pixel. */
        private static float px(float length) {
            return Math.min(1, length);
        }

        /** A quad facing (nx, ny, nz), its four corners each (x, y, z, u, v) with u and v across the sprite, 0 to 1. */
        void quad(PoseStack.Pose pose, TextureAtlasSprite sprite, int light, float nx, float ny, float nz, float... corners) {
            for (int i = 0; i < 4; i++) {
                int at = i * 5;
                consumer.addVertex(pose, corners[at], corners[at + 1], corners[at + 2])
                    .setColor(255, 255, 255, 255)
                    .setUv(sprite.getU(corners[at + 3]), sprite.getV(corners[at + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
            }
        }
    }
}
