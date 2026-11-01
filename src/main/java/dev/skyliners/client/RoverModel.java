package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.Skyliners;
import dev.skyliners.entity.Rover;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The rover as the client draws it (see {@link RoverRenderer}): six wheels on rocker-bogie legs, a white body in a
 * band of gold foil, a dark blue solar deck over it, a mast at its front carrying a camera head with two lenses, a
 * dish aerial at its back and an amber beacon. Built in the vanilla way, out of boxes laid on one texture (made by
 * {@code tools/generate_textures.mjs}, which lays each box's faces where its texture offset here says), at an eighth
 * of a block to the unit, its front toward -z and its y down, as a vanilla mob's is. Its wheels turn as it rolls, and
 * the four at its corners steer as it turns, the front ones one way and the back ones the other. The beacon is a part
 * of its own, drawn lit or dark by the renderer.
 */
public class RoverModel extends EntityModel<Rover> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "rover"), "main");
    /** The texture's size, in pixels. */
    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 64;
    /** How far the wheels stand out from its middle, ahead of and behind it, and how high their axles are, in units. */
    private static final float WHEEL_OUT = 8.5F;
    private static final float WHEEL_AHEAD = 9;
    private static final float AXLE = -2.5F;
    /** How far its corner wheels are steered at full lock, in radians. */
    private static final float STEERING_LOCK = 0.45F;
    /** How far its dish aerial leans back, in radians. */
    private static final float DISH_TILT = 0.5F;
    private static final String[] WHEELS = {"front_right", "middle_right", "rear_right", "front_left", "middle_left", "rear_left"};

    private final ModelPart body;
    private final ModelPart dish;
    private final ModelPart beacon;
    private final ModelPart[] wheels = new ModelPart[WHEELS.length];

    public RoverModel(ModelPart root) {
        this.body = root.getChild("body");
        this.dish = root.getChild("dish");
        this.beacon = root.getChild("beacon");
        for (int wheel = 0; wheel < WHEELS.length; wheel++) {
            wheels[wheel] = root.getChild(WHEELS[wheel]);
        }
    }

    /** Its boxes and where each takes its faces from on the texture. */
    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeListBuilder body = CubeListBuilder.create()
            // The body, white over its band of gold foil, and the solar deck over it.
            .texOffs(0, 0).addBox(-5.5F, -11, -8, 11, 5, 16)
            .texOffs(0, 21).addBox(-8, -12, -11, 16, 1, 22)
            // Its sensor bar across the front of the body, with its two hazard cameras.
            .texOffs(56, 10).addBox(-3.5F, -9, -9, 7, 1, 1)
            // The legs either side: a beam along each, a strut down to each wheel.
            .texOffs(78, 21).addBox(-7, -8, -10, 1, 1, 20)
            .texOffs(78, 21).addBox(6, -8, -10, 1, 1, 20)
            // The mast at the front right (-x is its right) and its camera head, its two lenses looking ahead.
            .texOffs(76, 0).addBox(-4, -15, -8, 1, 3, 1)
            .texOffs(80, 0).addBox(-6, -17, -8, 5, 2, 2)
            .texOffs(94, 0).addBox(-5, -17, -9, 1, 1, 1)
            .texOffs(94, 0).addBox(-3, -17, -9, 1, 1, 1)
            // The beacon's post at the back left.
            .texOffs(98, 0).addBox(4, -13, 8, 1, 1, 1);
        for (float side : new float[]{-6.5F, 6.5F}) {
            for (float along : new float[]{-WHEEL_AHEAD, 0, WHEEL_AHEAD}) {
                body.texOffs(72, 0).addBox(side - 0.5F, -7, along - 0.5F, 1, 5, 1);
            }
        }
        root.addOrReplaceChild("body", body, PartPose.ZERO);
        root.addOrReplaceChild("dish", CubeListBuilder.create().texOffs(110, 0).addBox(-1.5F, -3, -0.5F, 3, 3, 1)
            .texOffs(118, 0).addBox(-0.5F, 0, -0.5F, 1, 2, 1), PartPose.offsetAndRotation(-4.5F, -14, 7, DISH_TILT, 0, 0));
        root.addOrReplaceChild("beacon", CubeListBuilder.create().texOffs(102, 0).addBox(-1, -2, -1, 2, 2, 2), PartPose.offset(4.5F, -13, 8.5F));
        for (int wheel = 0; wheel < WHEELS.length; wheel++) {
            float side = wheel < 3 ? -WHEEL_OUT : WHEEL_OUT;
            float along = (wheel % 3 - 1) * WHEEL_AHEAD;
            root.addOrReplaceChild(WHEELS[wheel], CubeListBuilder.create().texOffs(56, 0).addBox(-1.5F, -2.5F, -2.5F, 3, 5, 5),
                PartPose.offset(side, AXLE, along));
        }
        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /** Its wheels turned as far as it has rolled, its corner wheels steered as it turns. */
    @Override
    public void setupAnim(Rover rover, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float partialTick = ageInTicks - rover.tickCount;
        float turned = rover.wheelTurn(partialTick);
        float steered = rover.steer(partialTick) * STEERING_LOCK;
        for (int wheel = 0; wheel < WHEELS.length; wheel++) {
            wheels[wheel].xRot = turned;
            int along = wheel % 3 - 1;
            wheels[wheel].yRot = -along * steered;
        }
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, int colour) {
        body.render(pose, consumer, light, overlay, colour);
        dish.render(pose, consumer, light, overlay, colour);
        for (ModelPart wheel : wheels) {
            wheel.render(pose, consumer, light, overlay, colour);
        }
    }

    /** Its beacon alone, lit or dark as the renderer has it. */
    public void renderBeacon(PoseStack pose, VertexConsumer consumer, int light, int overlay, int colour) {
        beacon.render(pose, consumer, light, overlay, colour);
    }
}
