package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.block.DeckRobotBlock;
import dev.skyliners.block.DeckRobotBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a deck robot's head (see {@link DeckRobotBlock}), turned about its neck toward whoever is nearest within
 * {@link #LOOK_RANGE} (see {@link #look}), all the way round if it must, its cyan visor following them, and back to
 * facing ahead once nobody is near; its body is its block's own model.
 */
public class DeckRobotRenderer implements BlockEntityRenderer<DeckRobotBlockEntity> {
    /** Its neck in its model drawn facing north, in pixels (see tools/generate_resources.mjs): its head turns about it. */
    private static final float NECK_X = 8;
    private static final float NECK_Y = 9;
    private static final float NECK_Z = 8;
    /** How near a player has to be for its head to turn toward them, in blocks. */
    private static final double LOOK_RANGE = 6;
    /** How far up in its block its head's middle is, and how far round it its head may reach, for leaving it out unseen. */
    private static final float MIDDLE = 0.7F;
    private static final float REACH = 0.6F;

    private final BlockRenderDispatcher blocks;

    public DeckRobotRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(DeckRobotBlockEntity robot, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = robot.getLevel();
        if (level == null || !Sight.ahead(pose.last(), 0.5F, MIDDLE, 0.5F, REACH)) {
            return;
        }
        BlockState state = robot.getBlockState();
        Direction facing = state.getValue(DeckRobotBlock.FACING);
        BlockState head = state.setValue(DeckRobotBlock.FACING, Direction.NORTH).setValue(DeckRobotBlock.PART, DeckRobotBlock.Part.HEAD);
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(180 - facing.toYRot()), 0.5F, 0, 0.5F);
        pose.rotateAround(Axis.YP.rotationDegrees(robot.headYaw(partialTick)), NECK_X / 16, NECK_Y / 16, NECK_Z / 16);
        RubberDuckRenderer.draw(blocks, head, pose, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** Each client tick: its head turned a tick's way toward whoever is nearest within reach, or back to ahead. */
    public static void look(DeckRobotBlockEntity robot) {
        Level level = robot.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = robot.getBlockPos();
        Vec3 neck = new Vec3(pos.getX() + NECK_X / 16, pos.getY() + NECK_Y / 16, pos.getZ() + NECK_Z / 16);
        Player nearest = Onlookers.nearest(level, pos, neck, LOOK_RANGE);
        if (nearest == null) {
            robot.turnHead(0);
            return;
        }
        Direction facing = robot.getBlockState().getValue(DeckRobotBlock.FACING);
        Vec3 way = SableClient.toPlot(level, pos, nearest.getEyePosition(), false).subtract(neck)
            .yRot((float) -Math.toRadians(180 - facing.toYRot()));
        robot.turnHead((float) Math.toDegrees(Math.atan2(-way.x, -way.z)));
    }
}
