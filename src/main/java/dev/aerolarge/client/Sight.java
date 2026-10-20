package dev.aerolarge.client;

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
