package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.LedColour;
import dev.aerolarge.block.LedPanelBlockEntity;
import dev.aerolarge.block.LedSetting;
import dev.aerolarge.block.LedStripBlock;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Draws a story's LED panel (see {@link dev.aerolarge.block.LedPanelBlock}) over its face (see {@link LedControlsArt}),
 * the control the crosshair rests on lit up as on every touch panel (see {@link TouchPanelArt}), and over its story's
 * strips what their mesh cannot show: the soft glow each lit strip casts up its wall from the floor, or down it from
 * the ceiling, added onto the wall at full brightness wherever the wall is flat; and, for an animated mode, the strips
 * themselves brightening over their dimmer mesh as the mode has them now (see {@link LedLights#intensity}), timed by
 * the game clock. Drawn whenever any of the story's box is in view, and nothing of the strips for a story switched off.
 */
public class LedPanelRenderer implements BlockEntityRenderer<LedPanelBlockEntity> {
    /** The glow up a wall, bright at the strip and fading to nothing, which each glow's colour tints. */
    private static final ResourceLocation WASH = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/misc/led_wash.png");
    /**
     * How far the glow reaches from the strip along its wall, in blocks, and how bright it is at the strip, a share.
     */
    private static final float WASH_REACH = 0.9F;
    private static final float WASH_STRENGTH = 0.45F;
    /** A strip's bar, in blocks: as tall and as deep as its model's. */
    private static final float BAR = LedStripBlock.BAR / 16F;
    /** Lifts what is drawn just off what it lies on. */
    private static final float LIFT = 0.004F;
    /** How far off the panel and its strips are drawn, in blocks: across the story and in through its windows. */
    private static final int VIEW_DISTANCE = 96;

    @Override
    public void render(LedPanelBlockEntity panel, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = panel.getLevel();
        if (level == null) {
            return;
        }
        double ticks = DrawingClock.ticks(partialTick);
        LedSetting setting = panel.setting();
        pose.pushPose();
        WideScreenFace.onto(pose, panel.getBlockState());
        LedControlsArt.panel(pose.last().pose(), ScreenDrawing.shapes(buffers), setting, TouchPanelArt.lookedAt(panel), ticks);
        pose.popPose();
        BoundingBox box = panel.box();
        if (box == null || setting.brightness() <= 0) {
            return;
        }
        List<LedRing.Strip> strips = LedRing.of(panel, level, box).strips();
        if (setting.mode().animated()) {
            VertexConsumer bars = buffers.getBuffer(RenderType.eyes(FlatQuads.WHITE));
            strips.forEach(strip -> sides(pose, strip, false, last -> bar(bars, last, strip, setting, ticks)));
        }
        VertexConsumer washes = buffers.getBuffer(RenderType.eyes(WASH));
        strips.forEach(strip -> sides(pose, strip, true, last -> wash(washes, last, strip, setting, ticks)));
    }

    /**
     * Draws on each side a strip runs along, or each whose wall is flat enough to glow on, in a pose moved to the
     * strip's block and turned about its middle so that side is north, the wall at z 0 and the room toward +z.
     */
    private static void sides(PoseStack pose, LedRing.Strip strip, boolean flatOnly, Consumer<PoseStack.Pose> draw) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (flatOnly ? !strip.glowsOn(side) : !strip.runsAlong(side)) {
                continue;
            }
            pose.pushPose();
            pose.translate(strip.at().getX() + 0.5, strip.at().getY(), strip.at().getZ() + 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - side.toYRot()));
            pose.translate(-0.5, 0, -0.5);
            draw.accept(pose.last());
            pose.popPose();
        }
    }

    /**
     * An animated strip brightened as its mode has it now, over its dimmer mesh: added onto the bar's front and its top
     * on the floor, or its underside under the ceiling. In the club's partner colour all of it is added, over the
     * strip's own dim glow.
     */
    private static void bar(VertexConsumer consumer, PoseStack.Pose pose, LedRing.Strip strip, LedSetting setting, double ticks) {
        LedColour colour = LedLights.colourNow(setting, ticks, strip.ceiling());
        float intensity = LedLights.intensity(setting.mode(), ticks, strip.along(), strip.ceiling());
        float added = colour == setting.colour() ? Math.max(0, intensity - LedLights.ANIMATED_BASE) : intensity;
        if (added <= 0) {
            return;
        }
        int argb = 0xFF000000 | LedLights.scaled(colour.rgb(), LedLights.share(setting.brightness()) * added);
        float y0 = strip.ceiling() ? 1 - BAR : 0;
        float front = BAR + LIFT;
        quad(consumer, pose, argb, 0, 0, 1, new float[]{0, y0, front, 0, 0}, new float[]{1, y0, front, 0, 0},
            new float[]{1, y0 + BAR, front, 0, 0}, new float[]{0, y0 + BAR, front, 0, 0});
        if (strip.ceiling()) {
            float under = 1 - BAR - LIFT;
            quad(consumer, pose, argb, 0, -1, 0, new float[]{0, under, 0, 0, 0}, new float[]{1, under, 0, 0, 0},
                new float[]{1, under, BAR, 0, 0}, new float[]{0, under, BAR, 0, 0});
        } else {
            float top = BAR + LIFT;
            quad(consumer, pose, argb, 0, 1, 0, new float[]{0, top, 0, 0, 0}, new float[]{0, top, BAR, 0, 0},
                new float[]{1, top, BAR, 0, 0}, new float[]{1, top, 0, 0, 0});
        }
    }

    /** The glow a strip casts on its wall, up from the floor or down from the ceiling, fading as it goes. */
    private static void wash(VertexConsumer consumer, PoseStack.Pose pose, LedRing.Strip strip, LedSetting setting, double ticks) {
        LedColour colour = LedLights.colourNow(setting, ticks, strip.ceiling());
        float intensity = LedLights.intensity(setting.mode(), ticks, strip.along(), strip.ceiling());
        int argb = 0xFF000000 | LedLights.scaled(colour.rgb(), LedLights.share(setting.brightness()) * intensity * WASH_STRENGTH);
        float near = strip.ceiling() ? 1 - BAR : BAR;
        float far = strip.ceiling() ? near - WASH_REACH : near + WASH_REACH;
        float low = Math.min(near, far);
        float high = Math.max(near, far);
        // The texture is brightest along its top edge (v 0): at the strip, whichever way the glow runs from it.
        float lowV = strip.ceiling() ? 1 : 0;
        quad(consumer, pose, argb, 0, 0, 1, new float[]{0, low, LIFT, 0, lowV}, new float[]{1, low, LIFT, 1, lowV},
            new float[]{1, high, LIFT, 1, 1 - lowV}, new float[]{0, high, LIFT, 0, 1 - lowV});
    }

    /**
     * A quad of one colour facing one way, its corners each x, y, z and u, v, in order round it as seen from in front.
     */
    private static void quad(VertexConsumer consumer, PoseStack.Pose pose, int argb, float nx, float ny, float nz, float[]... corners) {
        for (float[] corner : corners) {
            consumer.addVertex(pose, corner[0], corner[1], corner[2])
                .setColor(argb)
                .setUv(corner[3], corner[4])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(LedPanelBlockEntity panel) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** Its own block and the whole of its story's box, round its strips. */
    @Override
    public AABB getRenderBoundingBox(LedPanelBlockEntity panel) {
        AABB own = new AABB(panel.getBlockPos());
        BoundingBox box = panel.box();
        return box == null ? own : AABB.of(box).minmax(own);
    }
}
