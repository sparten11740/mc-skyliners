package dev.skyliners.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Where the camera stands against what a block entity's renderer draws, so that the renderer can leave out what the
 * camera cannot see. Sable draws an assembled airliner's block entities every frame wherever the camera looks, unless
 * their renderer says otherwise; only those drawn off screen too are culled to the view, as in the level.
 *
 * <p>The game asks a renderer whether to draw with the camera in the block entity's own frame: the level's, or for a
 * block of an assembled airliner the frame of the airliner's blocks, so a test there holds however the airliner lies.
 * While drawing, the pose takes the block's frame to the camera's, which stands at its origin.
 */
final class Sight {
    /** How far from the middle of its block a small block entity draws at most, in blocks. */
    private static final float NEAR_ITS_BLOCK = 3;

    private Sight() {
    }

    /**
     * Whether the camera is in front of a face drawn this far from the middle of its block toward the way it faces,
     * and closer to the middle than the reach: a screen's face is drawn from the front only, so from behind it nothing
     * of it shows.
     */
    static boolean facing(BlockPos pos, Direction facing, double face, Vec3 camera, double reach) {
        double dx = camera.x - (pos.getX() + 0.5);
        double dy = camera.y - (pos.getY() + 0.5);
        double dz = camera.z - (pos.getZ() + 0.5);
        return dx * facing.getStepX() + dy * facing.getStepY() + dz * facing.getStepZ() > face && dx * dx + dy * dy + dz * dz < reach * reach;
    }

    /**
     * Whether anything of a ball this big round a point of the frame a pose draws in can be in front of the camera:
     * whatever lies wholly behind it is never seen, whichever way the airliner lies. Tested in the view's own frame,
     * the pose followed by the view's turn, where the camera looks down the negative z axis.
     */
    static boolean ahead(PoseStack.Pose pose, float x, float y, float z, float radius) {
        Vector3f inView = pose.pose().transformPosition(x, y, z, new Vector3f());
        RenderSystem.getModelViewMatrix().transformPosition(inView);
        return inView.z < radius;
    }

    /** Whether anything within so far of the middle of the block a pose is at can be in front of the camera. */
    static boolean ahead(PoseStack.Pose pose, float radius) {
        return ahead(pose, 0.5F, 0.5F, 0.5F, radius);
    }

    /** Whether anything of a block entity that draws little past its own block can be in front of the camera. */
    static boolean ahead(PoseStack.Pose pose) {
        return ahead(pose, NEAR_ITS_BLOCK);
    }

    /** Where the camera is in the frame a pose draws in. */
    static Vector3f eye(PoseStack.Pose pose) {
        return pose.pose().invertAffine(new Matrix4f()).getTranslation(new Vector3f());
    }

    /**
     * How many pixels tall something this tall, in the units of the frame a pose draws in, is drawn at that frame's
     * origin: as near or far as the camera is, as wide as the view is (narrower through a spyglass) and as tall as the
     * window.
     */
    static float pixels(PoseStack.Pose pose, float height) {
        float zoom = Math.abs(RenderSystem.getProjectionMatrix().m11());
        return height / eye(pose).length() * zoom * Minecraft.getInstance().getWindow().getHeight() / 2;
    }
}
