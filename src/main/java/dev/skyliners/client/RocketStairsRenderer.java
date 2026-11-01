package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.AirlockDoorBlock;
import dev.skyliners.block.AirlockDoors;
import dev.skyliners.block.RocketStairsBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws a rocket's stairs button's airstairs as {@link AirstairRenderer} draws any (see
 * {@link RocketStairsBlockEntity}), and the sliding airlock doors it keeps across her doorway (see
 * {@link AirlockDoors}): two leaves meeting in the doorway's middle, each a thick plate, faced outside in her skin's
 * own weave and colour, whatever livery she wears, a stripe of her fins' colour round the doorway, a rubber seal down
 * the meeting edge with latch dogs across it, a tall slim window in a steel frame beside it and hazard chevrons
 * along the foot; inside, ribbed brushed steel with a grab bar under each window and a hazard kick plate (see the
 * airlock_door pictures in tools/generate_textures.mjs, each of the whole doorway). Opening, they slide apart into her
 * skin either side, only what is still in the doorway drawn, so they disappear into the jambs; open, nothing of them
 * shows.
 */
public class RocketStairsRenderer implements BlockEntityRenderer<RocketStairsBlockEntity> {
    /** The leaves' pictures outside, one over another: her skin's weave, the stripe and the parts in their colours. */
    private static final ResourceLocation SKIN = block("airlock_door_skin");
    private static final ResourceLocation STRIPE = block("airlock_door_stripe");
    private static final ResourceLocation TRIM = block("airlock_door_trim");
    /** The leaves' picture inside, and their edges' steel. */
    private static final ResourceLocation INSIDE = block("airlock_door_inside");
    private static final ResourceLocation EDGE = block("airlock_post");
    /** What makes a block's tint, which has no alpha, a colour drawn solid; and the colour a picture is drawn as is. */
    private static final int OPAQUE = 0xFF000000;
    private static final int PLAIN = 0xFFFFFFFF;
    /** The leaves' plate, across the middle of the doorway's blocks, in blocks out from their inner faces. */
    private static final float BACK = (8 - AirlockDoorBlock.THICKNESS / 2F) / 16;
    private static final float FRONT = (8 + AirlockDoorBlock.THICKNESS / 2F) / 16;
    /** How far each picture over the skin outside stands off the one under it, in blocks, so it is drawn over it. */
    private static final float LAYER = 0.004F;
    /** Shown narrower than this, in blocks, a leaf has gone into the jamb. */
    private static final float GONE = 0.001F;

    private final AirstairRenderer<RocketStairsBlockEntity> airstairs = new AirstairRenderer<>();

    @Override
    public void render(RocketStairsBlockEntity stairs, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        airstairs.render(stairs, partialTick, pose, buffers, light, overlay);
        AirlockDoors doors = stairs.doors();
        Level level = stairs.getLevel();
        if (doors == null || level == null) {
            return;
        }
        float half = doors.width() / 2F;
        float slid = half * doors.openness(partialTick);
        if (slid >= half - GONE) {
            return;
        }
        BlockPos corner = doors.corner();
        BlockPos from = corner.subtract(stairs.getBlockPos());
        BlockPos over = corner.above(doors.rows());
        int lit = SableClient.light(level, corner.relative(doors.right(), doors.width() / 2).above());
        int skin = OPAQUE | ZeppelinPaint.tint(SkylinersRegistry.ZEPPELIN_COVER.get().defaultBlockState(), level, over, 0);
        int stripe = OPAQUE | ZeppelinPaint.tint(SkylinersRegistry.ZEPPELIN_FABRIC.get().defaultBlockState(), level, over, 0);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        pose.pushPose();
        pose.translate(from.getX(), from.getY(), from.getZ());
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-doors.out().toYRot()));
        pose.translate(-0.5, 0, -0.5);
        Look look = new Look(skin, stripe);
        new Leaf(pose.last(), consumer, lit, 0, half, -slid, doors.width(), doors.rows()).draw(look);
        new Leaf(pose.last(), consumer, lit, half, doors.width(), slid, doors.width(), doors.rows()).draw(look);
        pose.popPose();
    }

    /** The colours the leaves' skin and stripe are drawn in: her cover's and her fins'. */
    private record Look(int skin, int stripe) {
    }

    /**
     * One leaf, drawn in the doorway's frame (x across to the right seen from outside, y up from the sill, z out, the
     * doorway's outer face at z 1): its place in the doorway shut, from {@code from} to {@code to} across, slid this
     * far across toward its jamb, and how wide and tall the doorway is; only what is still in its place in the doorway
     * is drawn, each picture moving with it.
     */
    private record Leaf(PoseStack.Pose pose, VertexConsumer consumer, int light, float from, float to, float slid, float width, int rows) {
        /** Its faces outside, one picture over another, its face inside and its edges. */
        void draw(Look look) {
            float left = Math.max(from, from + slid);
            float right = Math.min(to, to + slid);
            if (right - left <= GONE) {
                return;
            }
            float u0 = (left - slid) / width;
            float u1 = (right - slid) / width;
            outside(sprite(SKIN), look.skin(), left, right, u0, u1, FRONT);
            outside(sprite(STRIPE), look.stripe(), left, right, u0, u1, FRONT + LAYER);
            outside(sprite(TRIM), PLAIN, left, right, u0, u1, FRONT + 2 * LAYER);
            inside(sprite(INSIDE), left, right, 1 - u0, 1 - u1);
            edges(sprite(EDGE), left, right);
        }

        /** A face outside, from so far to so far across, its picture from so far to so far across the doorway's. */
        private void outside(TextureAtlasSprite sprite, int colour, float left, float right, float u0, float u1, float z) {
            vertex(sprite, colour, left, 0, z, u0, 1, 0, 0, 1);
            vertex(sprite, colour, right, 0, z, u1, 1, 0, 0, 1);
            vertex(sprite, colour, right, rows, z, u1, 0, 0, 0, 1);
            vertex(sprite, colour, left, rows, z, u0, 0, 0, 0, 1);
        }

        /** The face inside, its picture as seen from inside, its left over the doorway's right seen from outside. */
        private void inside(TextureAtlasSprite sprite, float left, float right, float uLeft, float uRight) {
            vertex(sprite, PLAIN, right, 0, BACK, uRight, 1, 0, 0, -1);
            vertex(sprite, PLAIN, left, 0, BACK, uLeft, 1, 0, 0, -1);
            vertex(sprite, PLAIN, left, rows, BACK, uLeft, 0, 0, 0, -1);
            vertex(sprite, PLAIN, right, rows, BACK, uRight, 0, 0, 0, -1);
        }

        /** Its top and foot, and its sides, the one in the jamb out of sight. */
        private void edges(TextureAtlasSprite sprite, float left, float right) {
            vertex(sprite, PLAIN, left, rows, FRONT, 0, 0, 0, 1, 0);
            vertex(sprite, PLAIN, right, rows, FRONT, 1, 0, 0, 1, 0);
            vertex(sprite, PLAIN, right, rows, BACK, 1, 1, 0, 1, 0);
            vertex(sprite, PLAIN, left, rows, BACK, 0, 1, 0, 1, 0);
            vertex(sprite, PLAIN, left, 0, BACK, 0, 0, 0, -1, 0);
            vertex(sprite, PLAIN, right, 0, BACK, 1, 0, 0, -1, 0);
            vertex(sprite, PLAIN, right, 0, FRONT, 1, 1, 0, -1, 0);
            vertex(sprite, PLAIN, left, 0, FRONT, 0, 1, 0, -1, 0);
            side(sprite, left, -1);
            side(sprite, right, 1);
        }

        /** A side of the leaf, up from the sill, facing across the doorway one way or the other. */
        private void side(TextureAtlasSprite sprite, float x, float facing) {
            float near = facing > 0 ? FRONT : BACK;
            float far = facing > 0 ? BACK : FRONT;
            vertex(sprite, PLAIN, x, 0, near, 0, 1, facing, 0, 0);
            vertex(sprite, PLAIN, x, 0, far, 1, 1, facing, 0, 0);
            vertex(sprite, PLAIN, x, rows, far, 1, 0, facing, 0, 0);
            vertex(sprite, PLAIN, x, rows, near, 0, 0, facing, 0, 0);
        }

        private void vertex(TextureAtlasSprite sprite, int colour, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            consumer.addVertex(pose, x, y, z).setColor(colour).setUv(sprite.getU(u), sprite.getV(v)).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, nx, ny, nz);
        }
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture);
    }

    private static ResourceLocation block(String name) {
        return ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "block/" + name);
    }

    /** Drawn whether or not the section its button stands in is, as the airstairs are. */
    @Override
    public boolean shouldRenderOffScreen(RocketStairsBlockEntity stairs) {
        return airstairs.shouldRenderOffScreen(stairs);
    }

    @Override
    public int getViewDistance() {
        return airstairs.getViewDistance();
    }

    /** Everything round the button the stairs reach, the doorway among it. */
    @Override
    public AABB getRenderBoundingBox(RocketStairsBlockEntity stairs) {
        return airstairs.getRenderBoundingBox(stairs);
    }
}
