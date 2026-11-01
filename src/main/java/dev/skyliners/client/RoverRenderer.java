package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.block.RoverHatchBlock;
import dev.skyliners.entity.Rover;
import dev.skyliners.rover.RoverRoute;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The rover (see {@link RoverModel}), turned the way it faces, its beacon blinking amber and lit while it drives; and
 * while it is on its winch's hook, the steel cable from the boom's trolley down to the hook, yellow lifting straps from
 * the hook to the corners of its deck, and the trolley itself, where the rover's bay has it (see {@link RoverRoute}),
 * as this client draws her.
 */
public class RoverRenderer extends EntityRenderer<Rover> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/entity/rover.png");
    /** Model units to the block: the model is built at an eighth of a block a unit, a vanilla one at a sixteenth. */
    private static final float UNITS = 2;
    /** How long the beacon is lit, and dark, each blink, in ticks: under three blinks a second. */
    private static final int BLINK_TICKS = 4;
    /** The beacon's glass unlit: its amber, dimmed. */
    private static final int BEACON_DARK = 0xFF6A5030;
    /** The cable's and straps' half widths, in blocks, and their colours: steel, and safety yellow. */
    private static final float CABLE_HALF = 0.04F;
    private static final float STRAP_HALF = 0.035F;
    private static final int CABLE = 0xFF5C6168;
    private static final int STRAP = 0xFFE0B020;
    /** The hook: its half width and height, in blocks, and its iron. */
    private static final float HOOK_HALF = 0.08F;
    private static final float HOOK_HEIGHT = 0.22F;
    private static final int IRON = 0xFF8A8D91;
    /** The trolley over the pulley: its half width and its height under the boom, in blocks, and its dark steel. */
    static final float TROLLEY_HALF = 0.25F;
    static final int TROLLEY = 0xFF2B2E34;
    /** Where the straps meet the rover's deck, from its middle, in blocks: across, along, and up. */
    private static final double STRAP_ACROSS = 0.85;
    private static final double STRAP_ALONG = 1.2;
    private static final double DECK_TOP = 1.5;
    /** How far off the camera the rover and its long cable are drawn while it is on the hook, in blocks. */
    private static final double SEEN = 160;

    private final RoverModel model;

    public RoverRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new RoverModel(context.bakeLayer(RoverModel.LAYER));
        this.shadowRadius = 1.1F;
    }

    /** On the hook it is drawn while near, its box in view or not, as its cable reaches up out of it to her. */
    @Override
    public boolean shouldRender(Rover rover, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        if (rover.onTheHook()) {
            return rover.distanceToSqr(cameraX, cameraY, cameraZ) < SEEN * SEEN;
        }
        return super.shouldRender(rover, frustum, cameraX, cameraY, cameraZ);
    }

    @Override
    public void render(Rover rover, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.scale(-UNITS, -UNITS, UNITS);
        model.setupAnim(rover, 0, 0, rover.tickCount + partialTick, 0, 0);
        VertexConsumer skin = buffers.getBuffer(model.renderType(TEXTURE));
        model.renderToBuffer(pose, skin, light, OverlayTexture.NO_OVERLAY);
        boolean lit = rover.moving() && rover.tickCount / BLINK_TICKS % 2 == 0;
        model.renderBeacon(pose, skin, lit ? LightTexture.FULL_BRIGHT : light, OverlayTexture.NO_OVERLAY, lit ? -1 : BEACON_DARK);
        pose.popPose();
        if (rover.onTheHook()) {
            winch(rover, yaw, partialTick, pose, buffers, light);
        }
        super.render(rover, yaw, partialTick, pose, buffers, light);
    }

    /** The trolley, cable, hook and straps, in the rover's own place, from where its bay's boom is drawn this frame. */
    private void winch(Rover rover, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        Vec3 at = rover.getPosition(partialTick);
        Vec3 pulley = pulley(rover, at, partialTick);
        if (pulley == null) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        PoseStack.Pose last = pose.last();
        Vec3 camera = entityRenderDispatcher.camera.getPosition().subtract(at);
        Vec3 top = pulley.subtract(at);
        Vec3 meet = new Vec3(0, RoverRoute.SLINGS, 0);
        Vec3 hook = meet.lerp(top, Mth.clamp(rover.hook(), 0, 1));
        FlatQuads.box(consumer, last, light, TROLLEY, (float) top.x - TROLLEY_HALF, (float) top.y, (float) top.z - TROLLEY_HALF,
            (float) top.x + TROLLEY_HALF, (float) (top.y + RoverRoute.BOOM_BOTTOM - RoverRoute.PULLEY), (float) top.z + TROLLEY_HALF);
        FlatQuads.strand(consumer, last, light, CABLE, camera, top, hook.add(0, HOOK_HEIGHT, 0), CABLE_HALF);
        FlatQuads.box(consumer, last, light, IRON, (float) hook.x - HOOK_HALF, (float) hook.y, (float) hook.z - HOOK_HALF,
            (float) hook.x + HOOK_HALF, (float) hook.y + HOOK_HEIGHT, (float) hook.z + HOOK_HALF);
        if (rover.hook() <= 0) {
            straps(consumer, last, light, camera, yaw, hook);
        }
    }

    /** Four straps from the hook down to the corners of the rover's deck, turned with it. */
    private static void straps(VertexConsumer consumer, PoseStack.Pose last, int light, Vec3 camera, float yaw, Vec3 hook) {
        double turn = Math.toRadians(yaw);
        Vec3 ahead = new Vec3(-Math.sin(turn), 0, Math.cos(turn));
        Vec3 across = new Vec3(Math.cos(turn), 0, Math.sin(turn));
        for (int corner = 0; corner < 4; corner++) {
            double side = (corner & 1) == 0 ? -STRAP_ACROSS : STRAP_ACROSS;
            double end = (corner & 2) == 0 ? -STRAP_ALONG : STRAP_ALONG;
            Vec3 foot = across.scale(side).add(ahead.scale(end)).add(0, DECK_TOP, 0);
            FlatQuads.strand(consumer, last, light, STRAP, camera, hook, foot, STRAP_HALF);
        }
    }

    /**
     * Where the trolley's pulley is drawn this frame, in the world: at the boom's tip over the drop, or over the rover
     * as it is carried along the boom; null once its bay is not there to draw it from.
     */
    @Nullable
    private static Vec3 pulley(Rover rover, Vec3 at, float partialTick) {
        Level level = rover.level();
        BlockPos bay = rover.bay();
        BlockState door = level.getBlockState(bay);
        if (!(door.getBlock() instanceof RoverHatchBlock)) {
            return null;
        }
        Direction out = door.getValue(RoverHatchBlock.FACING);
        double along = RoverRoute.OVERHANG;
        if (!rover.leg().trolleyAtTip()) {
            Vec3 hinge = drawn(level, RoverRoute.hinge(bay), partialTick);
            Vec3 outward = drawn(level, RoverRoute.at(bay, out, 1, 0), partialTick).subtract(hinge);
            along = Mth.clamp(at.subtract(hinge).dot(outward), RoverRoute.BOOM_ROOT, RoverRoute.OVERHANG);
        }
        return drawn(level, RoverRoute.at(bay, out, along, RoverRoute.PULLEY), partialTick);
    }

    /** Where a point among her blocks is in the world as she is drawn this frame, or else as she last ticked. */
    static Vec3 drawn(Level level, Vec3 aboard, float partialTick) {
        SableClient.View view = SableClient.view(aboard, new Vec3(0, 0, 1), new Vec3(0, 1, 0), partialTick);
        return view != null ? view.eye() : SableClient.toWorld(level, aboard);
    }

    @Override
    public ResourceLocation getTextureLocation(Rover rover) {
        return TEXTURE;
    }

    @Override
    protected boolean shouldShowName(Rover rover) {
        return false;
    }
}
