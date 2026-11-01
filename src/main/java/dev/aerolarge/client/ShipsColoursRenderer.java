package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.block.FlagHalyardBlock;
import dev.aerolarge.block.FlagHalyardBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Draws the ship's colours flying from a flagstaff (see {@link FlagHalyardBlock}): the halyard running up beside the
 * staff from the cleat to the truck, and the banner bent on to it, hoisted as a great flag twice as long as it is
 * deep, streaming out the way the halyard faces. Its top is at the hoist and its left edge along the flag's top, as a
 * banner flown as a flag is, and waves run along it to its free end, all of it drawn with the banner's own layers,
 * as a banner is. While the colours run up the staff the flag grows from a bundle at its foot to its full size at the
 * top. The waves follow the game clock, the same on every client.
 */
public class ShipsColoursRenderer implements BlockEntityRenderer<FlagHalyardBlockEntity> {
    /** How long the flag is from the hoist to its free end, and how deep down the staff, in blocks. */
    private static final float FLY = 20;
    private static final float DEPTH = 10;
    /** How many strips it is drawn in along its length, each bent a little to the wave. */
    private static final int STRIPS = 24;
    /**
     * How far its free end swings either side, in blocks, how many waves run along it at once, and how long each takes
     * to pass, in ticks.
     */
    private static final float SWING = 0.9F;
    private static final float WAVES = 1.5F;
    private static final float WAVE_TICKS = 26;
    /** How big the bundle it is hoisted as starts, a share of its full size, and how far over the halyard it starts. */
    private static final float BUNDLE = 0.15F;
    private static final float BUNDLE_OVER = 1.2F;
    /**
     * How far below the staff's top the flag's top flies, under the truck, and how far out from the staff the halyard
     * runs, and how thick it is, in blocks.
     */
    private static final float UNDER_TRUCK = 0.25F;
    private static final float HALYARD_OUT = 0.16F;
    private static final float HALYARD_WIDTH = 0.04F;
    private static final int HALYARD = 0xFFD9C79B;
    /** How far off the colours are drawn, in blocks: a zeppelin's length. */
    private static final int VIEW_DISTANCE = 512;

    @Override
    public void render(FlagHalyardBlockEntity halyard, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = halyard.getLevel();
        if (level == null) {
            return;
        }
        Direction fly = halyard.getBlockState().getValue(FlagHalyardBlock.FACING);
        float top = 1 + FlagHalyardBlock.staffHeight(level, halyard.getBlockPos()) - UNDER_TRUCK;
        PoseStack.Pose last = pose.last();
        float x = 0.5F + fly.getStepX() * HALYARD_OUT;
        float z = 0.5F + fly.getStepZ() * HALYARD_OUT;
        FlatQuads.box(buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE)), last, light, HALYARD,
            x - HALYARD_WIDTH, 0.3F, z - HALYARD_WIDTH, x + HALYARD_WIDTH, top + UNDER_TRUCK / 2, z + HALYARD_WIDTH);
        if (!halyard.flying()) {
            return;
        }
        float hoist = smooth(halyard.hoist(DrawingClock.gameTime(partialTick)));
        float size = Math.max(BUNDLE, hoist);
        Flag flag = new Flag(fly, Mth.lerp(hoist, BUNDLE_OVER + DEPTH * BUNDLE, top), size, (float) DrawingClock.ticks(partialTick) / WAVE_TICKS);
        BannerQuads.layers(buffers, halyard.base(), halyard.patterns(), (consumer, colour) -> strips(flag, last, consumer, colour, light));
    }

    /** A share eased in and out: the flag starts up the staff gently and slows as it closes up at the top. */
    private static float smooth(float share) {
        return share * share * (3 - 2 * share);
    }

    /** The flag as it flies now: the way it streams, how high its top is, how big it is, how far its waves have run. */
    private record Flag(Direction fly, float top, float size, float waves) {
        /** How far along it a share of its length is from the staff, in blocks. */
        float along(float share) {
            return HALYARD_OUT + share * FLY * size;
        }

        /** How far to one side a share of its length has swung, in blocks: the more, the nearer its free end. */
        float swing(float share) {
            return SWING * size * share * Mth.sin((float) (2 * Math.PI * (WAVES * share - waves)));
        }
    }

    /** One of the banner's layers over the whole flag, tinted its dye's colour, strip by strip. */
    private static void strips(Flag flag, PoseStack.Pose pose, VertexConsumer consumer, int colour, int light) {
        for (int strip = 0; strip < STRIPS; strip++) {
            strip(flag, pose, consumer, colour, light, strip / (float) STRIPS, (strip + 1) / (float) STRIPS);
        }
    }

    /** A strip of the flag between two shares of its length, from its top to its bottom. */
    private static void strip(Flag flag, PoseStack.Pose pose, VertexConsumer consumer, int colour, int light, float from, float to) {
        Direction fly = flag.fly();
        Direction side = fly.getClockWise();
        float bottom = flag.top() - DEPTH * flag.size();
        float slope = (flag.swing(to) - flag.swing(from)) / (flag.along(to) - flag.along(from));
        float length = Mth.sqrt(1 + slope * slope);
        float nx = (side.getStepX() - fly.getStepX() * slope) / length;
        float nz = (side.getStepZ() - fly.getStepZ() * slope) / length;
        corner(flag, pose, consumer, colour, light, nx, nz, from, flag.top(), 0);
        corner(flag, pose, consumer, colour, light, nx, nz, from, bottom, 1);
        corner(flag, pose, consumer, colour, light, nx, nz, to, bottom, 1);
        corner(flag, pose, consumer, colour, light, nx, nz, to, flag.top(), 0);
    }

    /**
     * A corner of a strip, at a share of the flag's length and a height, and how far across the banner that is: the
     * banner's left edge along the flag's top, its top at the hoist.
     */
    private static void corner(Flag flag, PoseStack.Pose pose, VertexConsumer consumer, int colour, int light, float nx, float nz,
                               float share, float y, float across) {
        Direction fly = flag.fly();
        Direction side = fly.getClockWise();
        float along = flag.along(share);
        float swing = flag.swing(share);
        consumer.addVertex(pose, 0.5F + fly.getStepX() * along + side.getStepX() * swing, y, 0.5F + fly.getStepZ() * along + side.getStepZ() * swing)
            .setColor(colour)
            .setUv(BannerQuads.FACE_LEFT + across * BannerQuads.FACE_WIDTH, BannerQuads.FACE_TOP + share * BannerQuads.FACE_HEIGHT)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, 0, nz);
    }

    @Override
    public boolean shouldRenderOffScreen(FlagHalyardBlockEntity halyard) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** As far round the halyard as the flag can reach, out from the staff and up it. */
    @Override
    public AABB getRenderBoundingBox(FlagHalyardBlockEntity halyard) {
        return new AABB(halyard.getBlockPos()).inflate(FLY + 2);
    }
}
