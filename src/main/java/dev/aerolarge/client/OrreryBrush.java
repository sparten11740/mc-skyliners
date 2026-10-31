package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Vector3f;

/**
 * Where the quads of the rocket's orrery and of the planetarium round it go (see {@link OrreryRenderer}), in the pose
 * they are drawn in, the orrery's mount's own frame: every corner at full brightness and facing straight up, so that
 * the entity shader lights them all alike, flat, and a corner's colour is all the light it shows, the sun's light on a
 * planet baked into it as it is drawn, or the glow added over the dome.
 */
record OrreryBrush(VertexConsumer consumer, PoseStack.Pose pose) {
    /** One corner of a quad, where it is, where on its texture and how bright in each colour, each a share. */
    void vertex(float x, float y, float z, float u, float v, float r, float g, float b) {
        consumer.addVertex(pose, x, y, z)
            .setColor(r, g, b, 1)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(pose, 0, 1, 0);
    }

    /**
     * A square of a patch facing the camera, so big either way from its middle, in one colour: a glow or a sparkle,
     * which looks the same from wherever it is seen.
     */
    void facingCamera(Facing facing, float x, float y, float z, float half, Patch patch, float r, float g, float b) {
        Vector3f left = facing.left();
        Vector3f up = facing.up();
        // Counter-clockwise as the camera sees it: bottom left, bottom right, top right, top left.
        float[][] corners = {{1, -1, 0, 1}, {-1, -1, 1, 1}, {-1, 1, 1, 0}, {1, 1, 0, 0}};
        for (float[] corner : corners) {
            vertex(x + (left.x * corner[0] + up.x * corner[1]) * half, y + (left.y * corner[0] + up.y * corner[1]) * half,
                z + (left.z * corner[0] + up.z * corner[1]) * half, patch.u(corner[2]), patch.v(corner[3]), r, g, b);
        }
    }

    /** The camera's left and up in the frame a pose draws in, to draw squares facing the camera there. */
    record Facing(Vector3f left, Vector3f up) {
        static Facing of(PoseStack.Pose pose) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            Matrix3f back = new Matrix3f(pose.normal()).transpose();
            return new Facing(back.transform(camera.getLeftVector(), new Vector3f()), back.transform(camera.getUpVector(), new Vector3f()));
        }
    }

    /**
     * A picture on a sheet of them, from its top left corner across and down, as shares of the sheet: half a pixel in
     * from its edges, so that its neighbours on the sheet never bleed into it.
     */
    record Patch(float u0, float v0, float u1, float v1) {
        /** The picture so many pixels across and down from a corner of a sheet so big. */
        static Patch of(int x, int y, int width, int height, int sheetWidth, int sheetHeight) {
            return new Patch((x + 0.5F) / sheetWidth, (y + 0.5F) / sheetHeight, (x + width - 0.5F) / sheetWidth, (y + height - 0.5F) / sheetHeight);
        }

        /** So far across the picture, a share. */
        float u(float across) {
            return u0 + (u1 - u0) * across;
        }

        /** So far down the picture, a share. */
        float v(float down) {
            return v0 + (v1 - v0) * down;
        }
    }
}
