package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.entity.PaperPlane;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A paper plane in flight, folded: two wings meeting along the top in a shallow V and the keel they were folded from
 * hanging under them, nose first the way it flies, pitched down as it sinks and banked into its turns.
 */
public class PaperPlaneRenderer extends EntityRenderer<PaperPlane> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/entity/paper_plane.png");
    /**
     * The folded paper, nose toward +z and y up, in blocks: each face four corners (a triangle has its last twice) and
     * the way it faces, for its shading. The wings take the top half of the texture, the keel the bottom half.
     */
    private static final List<Face> FACES = List.of(
        new Face(0, 1, 0, new Corner(0, 0.02F, 0.25F, 0.5F, 0), new Corner(0.2F, 0.07F, -0.25F, 1, 0.5F),
            new Corner(0, 0.02F, -0.25F, 0.5F, 0.5F), new Corner(0, 0.02F, -0.25F, 0.5F, 0.5F)),
        new Face(0, 1, 0, new Corner(0, 0.02F, 0.25F, 0.5F, 0), new Corner(0, 0.02F, -0.25F, 0.5F, 0.5F),
            new Corner(-0.2F, 0.07F, -0.25F, 0, 0.5F), new Corner(-0.2F, 0.07F, -0.25F, 0, 0.5F)),
        new Face(1, 0, 0, new Corner(0, 0.02F, 0.25F, 1, 0.5F), new Corner(0, 0.02F, -0.25F, 0, 0.5F),
            new Corner(0, -0.07F, -0.25F, 0, 1), new Corner(0, -0.02F, 0.05F, 0.7F, 1))
    );

    public PaperPlaneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.15F;
    }

    @Override
    public void render(PaperPlane plane, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.translate(0, plane.getBbHeight() / 2, 0);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, plane.xRotO, plane.getXRot())));
        pose.mulPose(Axis.ZP.rotationDegrees(plane.roll(partialTick)));
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose last = pose.last();
        for (Face face : FACES) {
            for (Corner corner : face.corners()) {
                buffer.addVertex(last, corner.x(), corner.y(), corner.z()).setColor(-1).setUv(corner.u(), corner.v())
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(last, face.nx(), face.ny(), face.nz());
            }
        }
        pose.popPose();
        super.render(plane, yaw, partialTick, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(PaperPlane plane) {
        return TEXTURE;
    }

    /** A corner of the folded paper: where it is, and where on the texture. */
    private record Corner(float x, float y, float z, float u, float v) {
    }

    /** A face of the folded paper: the way it faces and its corners. */
    private record Face(float nx, float ny, float nz, Corner... corners) {
    }
}
