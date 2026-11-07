package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.BoardingRamp;
import dev.skyliners.block.RampPanelBlock;
import dev.skyliners.block.RampPanelBlockEntity;
import dev.skyliners.block.TouchPanel;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws the freighter's ramp panel (see {@link RampPanelBlock}) and the boarding ramp it keeps (see
 * {@link BoardingRamp}). The panel's face: the lamp across its top in the colour of how the ramp stands, blinking
 * while it moves, and its one big button under it, showing only what pressing it does (see {@link RampPanelBlock#ACTION}):
 * blue with a ramp coming down while the ramp is up or coming up, green with an arrow up while it is down or coming
 * down, so pressed while it moves it turns round, lit up as every touch panel's are (see {@link TouchPanelArt}). The ramp, out of plain coloured boxes, while its hatch is
 * open: the plate, grey ribbed deck plate edged in yellow and black hazard stripes on top and her skin's colour under
 * it, swinging down on its hinge tube at the head of the well; two hydraulic rams from brackets on the well's sides to
 * lugs on the plate's edges, lengthening as it falls; its foot lip unfolding at the end; and lamps at the well's head
 * and at the plate's foot, amber and blinking while it moves, steady white once it is down. Stowed, the hatch's own
 * blocks are the plate, and nothing is drawn but the panel.
 */
public class BoardingRampRenderer implements BlockEntityRenderer<RampPanelBlockEntity> {
    /** The panel's face, from the middle of the block toward the way it looks, lifted just off its front. */
    private static final float FACE = -0.5F + RampPanelBlock.THICKNESS / 16F + 0.002F;
    /** The gap round each button and the lamp, in blocks. */
    private static final double GAP = 0.02;
    /** How far out each way from its middle the button's picture reaches, as a share of the button's height. */
    private static final double PICTURE = 0.7;
    /** How often the panel's lamp and the ramp's lamps blink while it moves, in ticks a blink. */
    private static final int BLINK = 8;
    /** Lifts each layer of the panel's face off the one below it. */
    private static final float LAYER = 0.001F;
    private static final int CASING = 0xFF15171B;
    private static final int WHITE = 0xFFF2F6FB;
    private static final Map<RampPanelBlock.Status, Integer> LAMPS = Map.of(
        RampPanelBlock.Status.READY, 0xFF40E060, RampPanelBlock.Status.BUSY, 0xFFFFB020,
        RampPanelBlock.Status.DOWN, 0xFF4090FF, RampPanelBlock.Status.REFUSED, 0xFFE83030);
    private static final Map<RampPanelBlock.Button, Integer> COLOURS = Map.of(
        RampPanelBlock.Button.LOWER, 0xFF2F6FC0, RampPanelBlock.Button.RAISE, 0xFF2E9A4A);
    /**
     * The plate: how far in from the well's sides its edges stand, how thick it is (as thick as the ramp has it, which
     * sets blocks under it as it swings), how deep the dark edge at its foot is, and how far its floor stands proud of
     * it, all in blocks.
     */
    private static final float SIDE_GAP = 0.05F;
    private static final float THICK = BoardingRamp.THICKNESS;
    private static final float PLATE = 0.02F;
    private static final float TOP_PROUD = 0.002F;
    /** What makes a block's tint, which has no alpha, a colour drawn solid. */
    private static final int OPAQUE = 0xFF000000;
    /** The foot lip's grey steel, and the dark edge at the plate's foot. */
    private static final int DECK = 0xFF7A7F88;
    private static final int EDGE = 0xFF2A2D33;
    /**
     * The hinge tube along the plate's head: its half thickness, how far its ends stand past the plate's sides, so
     * neither lies in the plane of the other and flickers through it, in blocks, and its steel.
     */
    private static final float HINGE_HALF = 0.14F;
    private static final float HINGE_PROUD = 0.02F;
    private static final int STEEL = 0xFF8A8F98;
    /** The foot lip: how long it is, in blocks, and how far it is turned back over the plate while folded, in degrees. */
    private static final float LIP = 0.6F;
    private static final float LIP_FOLDED = -175;
    /**
     * The rams: how far along the plate their lugs are, as a share of its length; how far out from the head and how far
     * under the deck's top their brackets on the well's sides stand, in blocks; the barrel's length, and the barrel's
     * and the rod's half thickness, in blocks; and their colours, the barrel painted hydraulic yellow, to be seen in the
     * shade under her, the rod bright chrome.
     */
    private static final double LUG_ALONG = 0.4;
    private static final double BRACKET_OUT = 8.5;
    private static final double BRACKET_DOWN = 0.6;
    private static final double BARREL = 1.8;
    private static final float BARREL_HALF = 0.12F;
    private static final float ROD_HALF = 0.06F;
    private static final float BRACKET_HALF = 0.15F;
    private static final int BARREL_PAINT = 0xFFD9A62A;
    private static final int ROD = 0xFFD8DCE2;
    /**
     * The lamps: half their size, in blocks, their tops a little over the hinge's they stand by, and their colours,
     * amber moving and white down.
     */
    private static final float LAMP_HALF = 0.075F;
    private static final int LAMP_AMBER = 0xFFFFA020;
    private static final int LAMP_WHITE = 0xFFF4F8FF;
    /** Either side of the ramp's middle line, for what stands in pairs: its rams and its lamps. */
    private static final int[] SIDES = {-1, 1};
    /** How far past the ramp's own reach the camera may be and still see some of it, in blocks, for leaving it out. */
    private static final float SIGHT_MARGIN = 1.5F;

    @Override
    public void render(RampPanelBlockEntity panel, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = panel.getBlockState();
        Level level = panel.getLevel();
        if (!(state.getBlock() instanceof RampPanelBlock) || level == null) {
            return;
        }
        if (Sight.ahead(pose.last())) {
            face(panel, state, pose, buffers);
        }
        BoardingRamp ramp = panel.ramp();
        float ticks = ramp == null ? 0 : ramp.ticks(partialTick);
        if (ramp != null && (ramp.hatchOpen() || ticks > 0)) {
            ramp(panel, ramp, ticks, pose, buffers, level);
        }
    }

    /** The panel's face: its casing, its lamp and its two picture buttons. */
    private static void face(RampPanelBlockEntity panel, BlockState state, PoseStack pose, MultiBufferSource buffers) {
        RampPanelBlock.Status status = state.getValue(RampPanelBlock.STATUS);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-state.getValue(RampPanelBlock.FACING).toYRot()));
        pose.translate(-0.5, -0.5, FACE);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer shapes = ScreenDrawing.shapes(buffers);
        ScreenDrawing.rect(shapes, matrix, 0, 0, 1, 1, 0, CASING);
        boolean blinkedOff = status == RampPanelBlock.Status.BUSY && DrawingClock.wholeTicks() / BLINK % 2 == 1;
        TouchPanelArt.tile(shapes, matrix, RampPanelBlock.LAMP_TILE.inset(GAP), LAMPS.get(status), new TouchPanelArt.Look(false, false, !blinkedOff),
            LAYER);
        BoardingRamp ramp = panel.ramp();
        RampPanelBlock.Button action = RampPanelBlock.ACTION.action(ramp != null && ramp.down());
        TouchPanel.Tile tile = RampPanelBlock.BUTTONS.get(0);
        TouchPanelArt.tile(shapes, matrix, tile.inset(GAP), COLOURS.get(action),
            TouchPanelArt.Look.of(0, TouchPanelArt.lookedAt(panel), -1, true), LAYER);
        ResourceLocation picture = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID,
            "textures/gui/ramp/" + action.name().toLowerCase(Locale.ROOT) + ".png");
        ScreenDrawing.picture(buffers, matrix, picture, tile.middleX(), tile.middleY(), tile.height() * PICTURE, 4 * LAYER, WHITE);
        pose.popPose();
    }

    /**
     * The ramp, so far through coming down, drawn from the middle of its hinge on her deck's top, out of her along +z
     * and to its right, seen from outside, along +x.
     */
    private static void ramp(RampPanelBlockEntity panel, BoardingRamp ramp, float ticks, PoseStack pose, MultiBufferSource buffers, Level level) {
        BoardingRamp.Frame frame = ramp.frame();
        Direction out = frame.out();
        BlockPos head = frame.head();
        pose.pushPose();
        pose.translate(head.getX() + 0.5 - out.getStepX() * 0.5, head.getY() + 1, head.getZ() + 0.5 - out.getStepZ() * 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-out.toYRot()));
        float reach = (float) frame.length() / 2 + SIGHT_MARGIN;
        if (!Sight.ahead(pose.last(), 0, -frame.rise() / 2F, frame.run() / 2F, reach)) {
            pose.popPose();
            return;
        }
        BlockPos foot = panel.getBlockPos().offset(head).relative(out, frame.run()).below(frame.rise() - 1);
        int light = SableClient.light(level, foot);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        double slope = frame.slope();
        double angle = ramp.angle(ticks);
        float half = frame.width() / 2F - SIDE_GAP;
        int lamp = ticks >= BoardingRamp.OUT ? LAMP_WHITE : DrawingClock.wholeTicks() / BLINK % 2 == 0 ? LAMP_AMBER : 0;
        float hinge = half + HINGE_PROUD;
        FlatQuads.box(consumer, pose.last(), light, STEEL, -hinge, -HINGE_HALF, -HINGE_HALF, hinge, HINGE_HALF, HINGE_HALF);
        lamps(consumer, pose.last(), frame.width() / 2F, 0, lamp);
        for (int side : SIDES) {
            ram(pose, consumer, light, side * (frame.width() / 2F - ROD_HALF), angle, frame.length());
        }
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees((float) angle));
        plate(consumer, pose.last(), light, half, (float) frame.length(), skin(panel, frame));
        pose.translate(0, 0, frame.length());
        lamps(consumer, pose.last(), half, -LAMP_HALF, lamp);
        pose.mulPose(Axis.XP.rotationDegrees(lip(ticks, slope)));
        FlatQuads.box(consumer, pose.last(), light, DECK, -half, -THICK / 2, 0, half, 0, LIP);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees((float) angle));
        floorTop(buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS)), pose.last(), light, ramp.floor(), level, foot, half,
            (float) frame.length());
        pose.popPose();
        pose.popPose();
    }

    /** How far the foot lip is turned from the plate, in degrees: folded back over it, then out level on the ground. */
    private static float lip(float ticks, double slope) {
        double share = Math.clamp((ticks - BoardingRamp.SWUNG) / (double) (BoardingRamp.OUT - BoardingRamp.SWUNG), 0, 1);
        return (float) (LIP_FOLDED + share * (-LIP_FOLDED - slope));
    }

    /**
     * The plate, its top at y 0 from its hinge out to its foot along +z: her skin under it and along its sides up to
     * a dark edge at its foot, the two meeting rather than overlapping, which flickers, and on top the floor it is part
     * of while up (see {@link #floorTop}), so nothing changes as it folds away into the floor.
     */
    private static void plate(VertexConsumer consumer, PoseStack.Pose at, int light, float half, float length, int skin) {
        FlatQuads.box(consumer, at, light, skin, -half, -THICK, 0, half, 0, length - PLATE);
        FlatQuads.box(consumer, at, light, EDGE, -half, -THICK, length - PLATE, half, 0, length);
    }

    /**
     * The plate's top, the floor it is part of while up, laid block by block from its hinge out along it and from one
     * side across, standing just proud of the plate, its texture as the floor's own blocks lay it.
     */
    private static void floorTop(VertexConsumer consumer, PoseStack.Pose at, int light, BlockState floor, Level level, BlockPos foot, float half,
                                 float length) {
        if (floor.isAir()) {
            return;
        }
        FloorLook look = FloorLook.of(floor);
        int colour = look.colour(floor, level, foot);
        for (float near = 0; near < length; near++) {
            float far = Math.min(length, near + 1);
            for (float left = -half; left < half; left++) {
                float right = Math.min(half, left + 1);
                floorTile(consumer, at, light, colour, look, left, right, near, far);
            }
        }
    }

    /** One block's worth or less of the plate's floor, its texture from its corner as far as it reaches. */
    private static void floorTile(VertexConsumer consumer, PoseStack.Pose at, int light, int colour, FloorLook look, float left, float right,
                                  float near, float far) {
        float u1 = look.u(right - left);
        float v1 = look.v(far - near);
        float u0 = look.u(0);
        float v0 = look.v(0);
        float y = TOP_PROUD;
        consumer.addVertex(at, left, y, near).setColor(colour).setUv(u0, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(at, 0, 1, 0);
        consumer.addVertex(at, left, y, far).setColor(colour).setUv(u0, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(at, 0, 1, 0);
        consumer.addVertex(at, right, y, far).setColor(colour).setUv(u1, v1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(at, 0, 1, 0);
        consumer.addVertex(at, right, y, near).setColor(colour).setUv(u1, v0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(at, 0, 1, 0);
    }

    /** A lamp either side at so far out from the ramp's middle line, glowing in a colour; none while blinked off. */
    private static void lamps(VertexConsumer consumer, PoseStack.Pose at, float out, float z, int colour) {
        if (colour == 0) {
            return;
        }
        for (int side : SIDES) {
            float x = side * out;
            FlatQuads.box(consumer, at, LightTexture.FULL_BRIGHT, colour, x - LAMP_HALF, 0, z - LAMP_HALF, x + LAMP_HALF, 2 * LAMP_HALF,
                z + LAMP_HALF);
        }
    }

    /**
     * A ram along one of the well's sides, so far across from the ramp's middle line: from its bracket on the well's
     * side under the deck to its lug on the plate's edge, the plate swung so far down, its barrel at the bracket and
     * its rod running out of it to the lug.
     */
    private static void ram(PoseStack pose, VertexConsumer consumer, int light, float across, double angle, double length) {
        double turn = Math.toRadians(angle);
        double along = LUG_ALONG * length;
        double lugY = -THICK / 2 * Math.cos(turn) - along * Math.sin(turn);
        double lugZ = -THICK / 2 * Math.sin(turn) + along * Math.cos(turn);
        Vec3 bracket = new Vec3(across, -BRACKET_DOWN, BRACKET_OUT);
        Vec3 lug = new Vec3(across, lugY, lugZ);
        Vec3 way = lug.subtract(bracket).normalize();
        float down = (float) -BRACKET_DOWN;
        float out = (float) BRACKET_OUT;
        FlatQuads.box(consumer, pose.last(), light, STEEL, across - BRACKET_HALF, down - BRACKET_HALF, out - BRACKET_HALF,
            across + BRACKET_HALF, down + BRACKET_HALF, out + BRACKET_HALF);
        bar(pose, consumer, light, BARREL_PAINT, bracket, bracket.add(way.scale(BARREL)), BARREL_HALF);
        bar(pose, consumer, light, ROD, bracket, lug, ROD_HALF);
    }

    /** A bar of one colour from one point to another, so many blocks either side of its line. */
    private static void bar(PoseStack pose, VertexConsumer consumer, int light, int colour, Vec3 from, Vec3 to, float half) {
        Vec3 way = to.subtract(from);
        float length = (float) way.length();
        if (length < 1e-3F) {
            return;
        }
        pose.pushPose();
        pose.translate(from.x, from.y, from.z);
        pose.mulPose(new Quaternionf().rotationTo(0, 1, 0, (float) way.x, (float) way.y, (float) way.z));
        FlatQuads.box(consumer, pose.last(), light, colour, -half, 0, -half, half, length, half);
        pose.popPose();
    }

    /**
     * The colour of her skin beside the well, in whatever livery she wears, for the plate's underside to match it: the
     * cover's paint at the well's foot, under her deck, as the client paints her.
     */
    private static int skin(RampPanelBlockEntity panel, BoardingRamp.Frame frame) {
        BlockPos beside = panel.getBlockPos().offset(frame.head()).relative(frame.out(), frame.run() / 2).below()
            .relative(frame.right(), frame.width());
        return OPAQUE | ZeppelinPaint.tint(SkylinersRegistry.ZEPPELIN_COVER.get().defaultBlockState(), panel.getLevel(), beside, 0);
    }

    @Override
    public boolean shouldRenderOffScreen(RampPanelBlockEntity panel) {
        return true;
    }

    /** Big enough to take in the whole ramp, down or up, from its panel. */
    @Override
    public AABB getRenderBoundingBox(RampPanelBlockEntity panel) {
        BoardingRamp ramp = panel.ramp();
        AABB own = new AABB(panel.getBlockPos());
        if (ramp == null) {
            return own;
        }
        BoardingRamp.Frame frame = ramp.frame();
        BlockPos head = panel.getBlockPos().offset(frame.head());
        BlockPos foot = head.relative(frame.out(), (int) Math.ceil(frame.length()) + 1).below(frame.rise());
        return own.minmax(AABB.encapsulatingFullBlocks(head.above(), foot).inflate(frame.width()));
    }
}
