package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skyliners.block.LegBayBlockEntity;
import dev.skyliners.block.RetractableLegBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws one of the freighter's landing legs while it moves, and its foot while it is up (see
 * {@link LegBayBlockEntity}). Going up, the strut's sleeves unlock and draw in from its full width, each a little
 * narrower than the one above it, then telescope up into the bay one inside another, the lowest carrying the foot plate
 * up with it until the plate lies flat under her belly, tucked against the bay; what is fixed to a sleeve, her ramp's
 * call button, goes in and up with it. Coming down it is the same the other way. Up, only the foot plate shows, the
 * sleeves inside the bay; down and locked the leg is real blocks again, and nothing is drawn once they show.
 */
public class LegBayRenderer implements BlockEntityRenderer<LegBayBlockEntity> {
    /**
     * The share of the movement the sleeves take to draw in at the start of going up, and to widen again at the end of
     * coming down.
     */
    private static final float UNLOCK = 0.12F;
    /** How much narrower each sleeve is than the one above it, on each side, in blocks, and the narrowest one. */
    private static final float SLEEVE_STEP = 1 / 16F;
    private static final float NARROWEST = 4 / 16F;
    /**
     * How far past the bay's floor the sleeves rise once they are all in, in blocks: just inside it, so no face of
     * theirs lies in one of the bay's and flickers against it.
     */
    private static final float TUCKED = 1 / 32F;
    /** How far round the leg the camera may stand behind it and still see some of it, in blocks, past its length. */
    private static final float SEEN_PAST = 3;
    /** How far from the camera a leg is drawn, in blocks: from well out across the ground under her. */
    private static final int VIEW_DISTANCE = 128;

    private final BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();

    @Override
    public void render(LegBayBlockEntity bay, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = bay.getLevel();
        if (level == null || bay.drawn().isEmpty()) {
            return;
        }
        int footRow = footRow(bay);
        int sleeves = -footRow - 1;
        if (!Sight.ahead(pose.last(), 0.5F, footRow / 2F, 0.5F, sleeves + SEEN_PAST)) {
            return;
        }
        float raised = bay.raised(partialTick);
        float unlocked = Math.min(1, raised / UNLOCK);
        float risen = ease(Mth.clamp((raised - UNLOCK) / (1 - UNLOCK), 0, 1));
        boolean moving = bay.motion().moving() || bay.justLocked();
        // Lit as the open air under its foot is: the cells it moves through are its own blocks again once it locks.
        int light = LevelRenderer.getLightColor(level, bay.getBlockPos().above(footRow - 1));
        for (RetractableLegBlockEntity.Part part : bay.drawn()) {
            if (part.offset().getY() == footRow) {
                foot(pose, buffers, part, risen * sleeves, light);
            } else if (moving) {
                onSleeve(pose, buffers, part, unlocked, risen, light);
            }
        }
    }

    /** The row of the leg's foot plate below the bay: its lowest. */
    private static int footRow(LegBayBlockEntity bay) {
        return bay.drawn().stream().mapToInt(part -> part.offset().getY()).min().orElse(-1);
    }

    /** A block of the foot plate, risen so far up from where it stood. */
    private void foot(PoseStack pose, MultiBufferSource buffers, RetractableLegBlockEntity.Part part, float rise, int light) {
        pose.pushPose();
        pose.translate(part.offset().getX(), part.offset().getY() + rise, part.offset().getZ());
        blocks.renderSingleBlock(part.state(), pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    /**
     * A sleeve of the strut, or what is fixed to one beside it: the sleeve narrowed as far as it has drawn in and risen
     * as far as it has telescoped, all of them in the bay by the end; what is beside it moved in with its face.
     */
    private void onSleeve(PoseStack pose, MultiBufferSource buffers, RetractableLegBlockEntity.Part part, float unlocked, float risen, int light) {
        BlockPos at = part.offset();
        int sleeve = -at.getY() - 1;
        float width = Mth.lerp(unlocked, 1, Math.max(NARROWEST, 1 - 2 * SLEEVE_STEP * (sleeve + 1)));
        float inset = (1 - width) / 2;
        pose.pushPose();
        pose.translate(at.getX() - at.getX() * inset, at.getY() + risen * (sleeve + 1 + TUCKED), at.getZ() - at.getZ() * inset);
        if (at.getX() == 0 && at.getZ() == 0) {
            pose.translate(inset, 0, inset);
            pose.scale(width, 1, width);
        }
        blocks.renderSingleBlock(part.state(), pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        pose.popPose();
    }

    /** Slow at either end of a movement, as hydraulics start and stop it. */
    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    /** Drawn whether or not the bay's section is in view: the bay is in her belly, the leg out under it. */
    @Override
    public boolean shouldRenderOffScreen(LegBayBlockEntity bay) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** Everything the leg moves through: from the bay down to its foot plate, a block round. */
    @Override
    public AABB getRenderBoundingBox(LegBayBlockEntity bay) {
        BlockPos pos = bay.getBlockPos();
        return new AABB(pos.getX() - 2, pos.getY() + footRow(bay) - 1, pos.getZ() - 2, pos.getX() + 3, pos.getY() + 2, pos.getZ() + 3);
    }
}
