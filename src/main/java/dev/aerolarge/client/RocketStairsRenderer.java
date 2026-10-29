package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.AirlockDoorBlock;
import dev.aerolarge.block.AirlockDoors;
import dev.aerolarge.block.RocketStairsBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws a rocket's stairs button's airstairs as {@link AirstairRenderer} draws any (see
 * {@link RocketStairsBlockEntity}), and the sliding airlock doors it keeps across her doorway (see
 * {@link AirlockDoors}) out of plain coloured boxes: two leaves meeting in the doorway's middle, each a thick plate in
 * her skin's colour, whatever livery she wears, its inside faced in brushed steel, a black rubber seal down its meeting
 * edge and a square porthole of dark glass in a steel ring at eye height. Opening, they slide apart into her skin
 * either side, only what is still in the doorway drawn, so they disappear into the jambs; open, nothing of them shows.
 */
public class RocketStairsRenderer implements BlockEntityRenderer<RocketStairsBlockEntity> {
    /** What makes a block's tint, which has no alpha, a colour drawn solid. */
    private static final int OPAQUE = 0xFF000000;
    private static final int STEEL = 0xFF9CA4AD;
    private static final int RUBBER = 0xFF1C1D20;
    private static final int GLASS = 0xFF1E3A52;
    private static final int RING = 0xFFC4CAD0;
    /** The leaves' plate, across the middle of the doorway's blocks, in blocks out from their inner faces. */
    private static final float BACK = (8 - AirlockDoorBlock.THICKNESS / 2F) / 16;
    private static final float FRONT = (8 + AirlockDoorBlock.THICKNESS / 2F) / 16;
    /** How thick the steel facing inside and the parts standing proud of the plate are, in blocks. */
    private static final float PROUD = 0.02F;
    /** How far in from a leaf's edges its steel facing stops, in blocks. */
    private static final float FACING_INSET = 0.08F;
    /** How wide the rubber seal down a leaf's meeting edge is, in blocks. */
    private static final float SEAL = 0.08F;
    /** The porthole: how high its middle is over the sill, its half width, and its ring's width round it, in blocks. */
    private static final float PORTHOLE_UP = 1.65F;
    private static final float PORTHOLE_HALF = 0.22F;
    private static final float PORTHOLE_RING = 0.06F;
    /** Shown narrower than this, in blocks, a part has gone into the jamb. */
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
        float slid = doors.width() / 2F * doors.openness(partialTick);
        if (slid >= doors.width() / 2F - GONE) {
            return;
        }
        BlockPos corner = doors.corner();
        BlockPos from = corner.subtract(stairs.getBlockPos());
        int lit = SableClient.light(level, corner.relative(doors.right(), doors.width() / 2).above());
        int skin = OPAQUE | ZeppelinPaint.tint(AerolargeRegistry.ZEPPELIN_COVER.get().defaultBlockState(), level, corner.above(doors.rows()), 0);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        pose.pushPose();
        pose.translate(from.getX(), from.getY(), from.getZ());
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-doors.out().toYRot()));
        pose.translate(-0.5, 0, -0.5);
        Leaf left = new Leaf(pose.last(), consumer, lit, 0, doors.width() / 2F, -slid, doors.rows());
        Leaf right = new Leaf(pose.last(), consumer, lit, doors.width() / 2F, doors.width(), slid, doors.rows());
        left.draw(skin, false);
        right.draw(skin, true);
        pose.popPose();
    }

    /**
     * One leaf, drawn in the doorway's frame (x across to the right seen from outside, y up from the sill, z out, the
     * doorway's outer face at z 1): its place in the doorway shut, from {@code from} to {@code to} across, slid this
     * far across toward its jamb, and how tall it is; only what is still in its place in the doorway is drawn.
     */
    private record Leaf(PoseStack.Pose pose, VertexConsumer consumer, int light, float from, float to, float slid, int rows) {
        /** Its plate, its steel facing inside, its seal down its meeting edge and its porthole. */
        void draw(int skin, boolean meetsOnLeft) {
            float width = to - from;
            box(skin, 0, width, 0, rows, BACK, FRONT);
            box(STEEL, FACING_INSET, width - FACING_INSET, FACING_INSET, rows - FACING_INSET, BACK - PROUD, BACK);
            float seal = meetsOnLeft ? 0 : width - SEAL;
            box(RUBBER, seal, seal + SEAL, 0, rows, BACK - PROUD, FRONT + PROUD);
            float middle = width / 2;
            float ring = PORTHOLE_HALF + PORTHOLE_RING;
            box(RING, middle - ring, middle + ring, PORTHOLE_UP - ring, PORTHOLE_UP + ring, BACK - PROUD, FRONT + PROUD);
            box(GLASS, middle - PORTHOLE_HALF, middle + PORTHOLE_HALF, PORTHOLE_UP - PORTHOLE_HALF, PORTHOLE_UP + PORTHOLE_HALF,
                BACK - 2 * PROUD, FRONT + 2 * PROUD);
        }

        /**
         * A box of the leaf, from so far to so far across it from its left edge as it stands shut, moved as far as it
         * has slid and cut off where it passes into the jamb.
         */
        void box(int colour, float x0, float x1, float y0, float y1, float z0, float z1) {
            float left = Math.max(from, from + x0 + slid);
            float right = Math.min(to, from + x1 + slid);
            if (right - left > GONE) {
                FlatQuads.box(consumer, pose, light, colour, left, y0, z0, right, y1, z1);
            }
        }
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
