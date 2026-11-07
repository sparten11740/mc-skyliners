package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.engineering.JumpDriveBlock;
import dev.skyliners.engineering.JumpDriveBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws the jump drive's core and its three gyroscope rings over its plinth (see {@link JumpDriveBlock}): a glowing
 * core in three rings, each tilted its own way and turning at its own pace, their speed eased toward the drive's step
 * (see {@link JumpDriveBlockEntity}). At rest they drift dim steel blue; spooling up they speed up glowing sky blue;
 * running they race, bright white-blue, the core swelling and shrinking; sputtering they crawl, a flickering red,
 * shuddering. Left out while out of sight or across her deck.
 */
public class JumpDriveRenderer implements BlockEntityRenderer<JumpDriveBlockEntity> {
    /** How high the middle of the core and rings is over the drive's block's foot, in blocks. */
    private static final float MIDDLE = 1.95F;
    /** How far each ring is from the core, in blocks, how many pieces round it, and how thick and deep a piece is. */
    private static final float[] RADII = {0.55F, 0.72F, 0.9F};
    private static final int PIECES = 24;
    private static final float THICK = 0.05F;
    private static final float DEEP = 0.09F;
    /**
     * How far each ring leans from level, in degrees, about one way and the other by turns, and how many times as fast
     * as the first it turns.
     */
    private static final float[] LEANS = {70, -70, 0};
    private static final float[] PACES = {1, -1.5F, 2};
    /** How big the core is across at rest, in blocks, and how much it swells running, as a share. */
    private static final float CORE = 0.3F;
    private static final float CORE_SWELL = 0.25F;
    /**
     * How fast the rings turn at each step of the drive, in turns a tick, and how much of the way to it their speed
     * comes each tick.
     */
    private static final double REST_SPEED = 0.002;
    private static final double SPOOL_SPEED = 0.04;
    private static final double RUN_SPEED = 0.08;
    private static final double SPUTTER_SPEED = 0.006;
    private static final double EASE = 0.04;
    /** The rings' colours at each step. */
    private static final int REST = 0xFF4A6A8A;
    private static final int SPOOL = 0xFF3CC8FF;
    private static final int RUN = 0xFFAEEBFF;
    private static final int SPUTTER = 0xFFFF3A2A;
    private static final int SPUTTER_DIM = 0xFF7A1A12;
    /** How often a sputtering drive's red flickers, in ticks. */
    private static final int FLICKER_TICKS = 3;
    /** How far round its middle the rings reach, for leaving it out unseen, in blocks. */
    private static final float REACH = 1.0F;
    /** How far off it is drawn at most, in blocks: across her engineering, not across her whole deck. */
    private static final int DRAWN_WITHIN = 32;
    /** How far a sputtering drive's rings shudder, in blocks. */
    private static final float SHUDDER = 0.03F;

    @Override
    public void render(JumpDriveBlockEntity drive, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = drive.getBlockState();
        if (!(state.getBlock() instanceof JumpDriveBlock) || !Sight.ahead(pose.last(), 0.5F, MIDDLE, 0.5F, REACH)) {
            return;
        }
        JumpDriveBlock.Drive step = state.getValue(JumpDriveBlock.DRIVE);
        double ticks = DrawingClock.ticks(partialTick);
        float turned = (float) drive.turned(ticks, speed(step), EASE);
        int colour = colour(step, (long) ticks);
        int glow = step == JumpDriveBlock.Drive.REST ? light : LightTexture.FULL_BRIGHT;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        pose.pushPose();
        float shudder = step == JumpDriveBlock.Drive.SPUTTERING ? SHUDDER * (float) Math.sin(ticks * 2.3) : 0;
        pose.translate(0.5 + shudder, MIDDLE, 0.5 - shudder);
        float core = CORE / 2 * (step == JumpDriveBlock.Drive.RUNNING ? 1 + CORE_SWELL * (float) Math.sin(ticks * 0.6) : 1);
        FlatQuads.box(consumer, pose.last(), glow, colour, -core, -core, -core, core, core, core);
        for (int ring = 0; ring < RADII.length; ring++) {
            ring(pose, consumer, glow, colour, ring, turned);
        }
        pose.popPose();
    }

    /** A ring: leaning its own way, turned so far round its own middle at its pace, its pieces all round it. */
    private static void ring(PoseStack pose, VertexConsumer consumer, int light, int colour, int ring, float turned) {
        pose.pushPose();
        pose.mulPose((ring % 2 == 0 ? Axis.XP : Axis.ZP).rotationDegrees(LEANS[ring]));
        pose.mulPose(Axis.YP.rotationDegrees(turned * 360 * PACES[ring]));
        float radius = RADII[ring];
        float half = (float) (Math.PI * radius / PIECES) + THICK / 2;
        for (int piece = 0; piece < PIECES; piece++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(piece * 360F / PIECES));
            FlatQuads.box(consumer, pose.last(), light, colour, -half, -DEEP / 2, radius - THICK / 2, half, DEEP / 2, radius + THICK / 2);
            pose.popPose();
        }
        pose.popPose();
    }

    /** How fast the rings turn at a step of the drive, in turns a tick. */
    private static double speed(JumpDriveBlock.Drive step) {
        return switch (step) {
            case REST -> REST_SPEED;
            case SPOOLING -> SPOOL_SPEED;
            case RUNNING -> RUN_SPEED;
            case SPUTTERING -> SPUTTER_SPEED;
        };
    }

    /** The rings' colour at a step of the drive and a tick, a sputtering drive's flickering. */
    private static int colour(JumpDriveBlock.Drive step, long tick) {
        return switch (step) {
            case REST -> REST;
            case SPOOLING -> SPOOL;
            case RUNNING -> RUN;
            case SPUTTERING -> tick / FLICKER_TICKS % 2 == 0 ? SPUTTER : SPUTTER_DIM;
        };
    }

    @Override
    public int getViewDistance() {
        return DRAWN_WITHIN;
    }

    @Override
    public AABB getRenderBoundingBox(JumpDriveBlockEntity drive) {
        return new AABB(drive.getBlockPos()).expandTowards(0, 2, 0).inflate(REACH - 0.5F);
    }
}
