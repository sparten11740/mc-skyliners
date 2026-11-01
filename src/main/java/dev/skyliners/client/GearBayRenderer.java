package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.GearBayBlock;
import dev.skyliners.block.GearBayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;

/**
 * Draws a retractable gear leg while it swings (see {@link GearBayBlockEntity}): the leg's own blocks, its tires on
 * their wheel mounts, swung up about the bay's lower edge the way the bay faces, and the two doors under the bay, which
 * open before the leg moves and close once it is in. Down and locked the leg is real blocks again, and up it is out of
 * sight, so nothing is drawn then.
 */
public class GearBayRenderer implements BlockEntityRenderer<GearBayBlockEntity> {
    /** How far up the leg swings, in degrees: level with the belly. */
    private static final float SWING = 90;
    /** The share of the swing the doors take to open at its start, and to close at its end. */
    private static final float DOORS = 0.15F;
    /** How far a door swings open, in degrees, and how thick it is, in blocks. */
    private static final float DOOR_SWING = 95;
    private static final float DOOR_THICK = 1 / 16F;
    /** How big a tire is drawn, in blocks across, and how far out from its mount's middle it stands. */
    private static final float TIRE_SIZE = 2;
    private static final float TIRE_OUT = 0.75F;
    /** How far from the camera a moving leg is drawn, in blocks: from well out on the apron. */
    private static final int VIEW_DISTANCE = 128;

    private final BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();

    @Override
    public void render(GearBayBlockEntity bay, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = bay.getLevel();
        if (level == null || !bay.motion().moving()) {
            return;
        }
        float raised = bay.raised(partialTick);
        // The doors open, the leg swings, the doors close: each in its share of the time.
        float open = Math.min(1, Math.min(raised, 1 - raised) / DOORS);
        float swung = ease(Mth.clamp((raised - DOORS) / (1 - 2 * DOORS), 0, 1));
        Direction swing = bay.getBlockState().getValue(GearBayBlock.FACING);
        int light = LevelRenderer.getLightColor(level, bay.getBlockPos().below());
        doors(pose, buffers, swing, ease(open), light);
        if (swung < 1) {
            pose.pushPose();
            // About the bay's lower edge, its middle: the leg's foot goes the way the bay faces.
            pose.rotateAround(turn(swing, SWING * swung), 0.5F, 0, 0.5F);
            for (GearBayBlockEntity.Part part : bay.stowed()) {
                part(pose, buffers, level, part, light);
            }
            pose.popPose();
        }
    }

    /** A turn about the horizontal axis square to the way the leg swings, that swings a leg's foot that way. */
    private static Quaternionf turn(Direction swing, float degrees) {
        return switch (swing) {
            case NORTH -> Axis.XP.rotationDegrees(degrees);
            case SOUTH -> Axis.XP.rotationDegrees(-degrees);
            case EAST -> Axis.ZP.rotationDegrees(degrees);
            default -> Axis.ZP.rotationDegrees(-degrees);
        };
    }

    /** One block of the leg, where it stands from the bay, and a wheel mount's tire beside it. */
    private void part(PoseStack pose, MultiBufferSource buffers, Level level, GearBayBlockEntity.Part part, int light) {
        pose.pushPose();
        pose.translate(part.offset().getX(), part.offset().getY(), part.offset().getZ());
        blocks.renderSingleBlock(part.state(), pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ItemStack tire = tire(level, part);
        if (!tire.isEmpty()) {
            Direction out = part.state().getValue(BlockStateProperties.HORIZONTAL_FACING);
            pose.translate(0.5 + out.getStepX() * TIRE_OUT, 0, 0.5 + out.getStepZ() * TIRE_OUT);
            pose.mulPose(Axis.YP.rotationDegrees(90 - out.toYRot()));
            pose.scale(TIRE_SIZE, TIRE_SIZE, TIRE_SIZE);
            Minecraft.getInstance().getItemRenderer().renderStatic(tire, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        }
        pose.popPose();
    }

    /** The tire a wheel mount of the leg holds, as its data keeps it ("CurrentStack"); nothing for any other block. */
    private static ItemStack tire(Level level, GearBayBlockEntity.Part part) {
        BlockState state = part.state();
        if (part.data() == null || !part.data().contains("CurrentStack") || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.parseOptional(level.registryAccess(), part.data().getCompound("CurrentStack"));
    }

    /**
     * The two doors under the bay, hinged along its lower edges either side of the way the leg swings, swung down
     * open this share of the way: plates of the gear's metal.
     */
    private void doors(PoseStack pose, MultiBufferSource buffers, Direction swing, float open, int light) {
        BlockState plate = SkylinersRegistry.LANDING_GEAR.get().defaultBlockState();
        boolean alongX = swing.getAxis() == Direction.Axis.Z;
        for (int side : new int[]{-1, 1}) {
            pose.pushPose();
            float hinge = side < 0 ? 0 : 1;
            Quaternionf turn = alongX ? Axis.ZP.rotationDegrees(-side * DOOR_SWING * open) : Axis.XP.rotationDegrees(side * DOOR_SWING * open);
            pose.rotateAround(turn, alongX ? hinge : 0.5F, 0, alongX ? 0.5F : hinge);
            // Each door covers half the bay's underside, from its hinge to the middle.
            float from = side < 0 ? 0 : 0.5F;
            pose.translate(alongX ? from : 0, -DOOR_THICK, alongX ? 0 : from);
            pose.scale(alongX ? 0.5F : 1, DOOR_THICK, alongX ? 1 : 0.5F);
            blocks.renderSingleBlock(plate, pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
    }

    /** Slow at either end of a movement, as hydraulics start and stop it. */
    private static float ease(float t) {
        return t * t * (3 - 2 * t);
    }

    /** Drawn whether or not the bay's section is in view: the bay is in the hull's skin, the leg out under it. */
    @Override
    public boolean shouldRenderOffScreen(GearBayBlockEntity bay) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** Everything the leg sweeps through, as long as it is, every way round the bay. */
    @Override
    public AABB getRenderBoundingBox(GearBayBlockEntity bay) {
        int reach = 2;
        for (GearBayBlockEntity.Part part : bay.stowed()) {
            reach = Math.max(reach, Math.abs(part.offset().getY()) + 2);
        }
        BlockPos pos = bay.getBlockPos();
        return new AABB(pos).inflate(reach);
    }
}
