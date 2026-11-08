package dev.skyliners.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.block.LedColour;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.LedSetting;
import dev.skyliners.block.LedStripBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a story's LED panel (see {@link dev.skyliners.block.LedPanelBlock}) over its face (see {@link LedControlsArt}),
 * the control the crosshair rests on lit up as on every touch panel (see {@link TouchPanelArt}), and over its story's
 * strips what their mesh cannot show: the soft glow each lit strip casts up its wall from the floor, or down it from
 * the ceiling, added onto the wall at full brightness wherever the wall is flat; and, for an animated mode, the strips
 * themselves brightening over their dimmer mesh as the mode has them now (see {@link LedLights#intensity}), timed by
 * the game clock. Drawn whenever any of the story's box is in view, its face only while its block can be, and nothing
 * of the strips for a story switched off.
 *
 * <p>The strips' bars and glows are worked out as quads in the panel's own frame once each time its story's strips are
 * found (see {@link LedRing}), so each frame only colours them and moves them to where the panel is drawn. Being light
 * added onto what lies behind, the same in whatever order, they are left unsorted and every panel's are drawn together
 * (see {@link BatchedBuffers}).
 */
public class LedPanelRenderer implements BlockEntityRenderer<LedPanelBlockEntity> {
    /** The glow up a wall, bright at the strip and fading to nothing, which each glow's colour tints. */
    private static final ResourceLocation WASH = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/misc/led_wash.png");
    /** The strips' bars and their glows: light added onto what lies behind, as vanilla's eyes are, in no order needed. */
    private static final RenderType BARS = glow("led_bars", FlatQuads.WHITE);
    private static final RenderType WASHES = glow("led_washes", WASH);
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
    /** How many numbers each corner of a quad is kept as: x, y, z, u and v. */
    private static final int CORNER = 5;

    /** Each story's strips' quads, by its strips as last found, so they are worked out again as those are. */
    private final Map<LedRing, Glows> glows = new WeakHashMap<>();

    /**
     * A quad of a strip's: the strip, the way it faces in the panel's frame, and its corners there, in order round it
     * as seen from in front, {@link #CORNER} numbers each.
     */
    private record Quad(LedRing.Strip strip, Direction facing, float[] corners) {
    }

    /** A story's strips' quads: the bars an animated mode brightens, and the glows on their walls. */
    private record Glows(List<Quad> bars, List<Quad> washes) {
    }

    /** Vanilla's eyes on a texture, light added onto what lies behind, but left unsorted, which it never needs. */
    private static RenderType glow(String name, ResourceLocation texture) {
        return RenderType.create(Skyliners.MOD_ID + "_" + name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            RenderType.TRANSIENT_BUFFER_SIZE, false, false, RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_EYES_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(false));
    }

    /** Gives the strips' bars and glows buffers of their own, which every panel adds to through a frame. */
    static void registerBuffers(RegisterRenderBuffersEvent event) {
        BatchedBuffers.register(event, BARS, WASHES);
    }

    /** Draws every panel's bars and glows together, once the level's block entities are drawn. */
    static void drawGlows(RenderLevelStageEvent event) {
        BatchedBuffers.draw(event, BARS, WASHES);
    }

    @Override
    public void render(LedPanelBlockEntity panel, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = panel.getLevel();
        if (level == null) {
            return;
        }
        double ticks = DrawingClock.ticks(partialTick);
        LedSetting setting = panel.setting();
        if (Sight.ahead(pose.last())) {
            pose.pushPose();
            WideScreenFace.onto(pose, panel.getBlockState());
            LedControlsArt.panel(pose.last().pose(), ScreenDrawing.shapes(buffers), setting, TouchPanelArt.lookedAt(panel), ticks);
            pose.popPose();
        }
        BoundingBox box = panel.box();
        if (box == null || setting.brightness() <= 0) {
            return;
        }
        Glows story = glows.computeIfAbsent(LedRing.of(panel, level, box), LedPanelRenderer::glows);
        PoseStack.Pose last = pose.last();
        Vector3f[] normals = normals(last);
        if (setting.mode().animated()) {
            VertexConsumer bars = buffers.getBuffer(BARS);
            for (Quad quad : story.bars()) {
                bar(bars, last.pose(), normals, quad, setting, ticks);
            }
        }
        VertexConsumer washes = buffers.getBuffer(WASHES);
        for (Quad quad : story.washes()) {
            wash(washes, last.pose(), normals, quad, setting, ticks);
        }
    }

    /** The way each direction of the panel's frame faces where it is drawn, by the direction's index. */
    private static Vector3f[] normals(PoseStack.Pose pose) {
        Vector3f[] normals = new Vector3f[Direction.values().length];
        for (Direction way : Direction.values()) {
            normals[way.get3DDataValue()] = pose.transformNormal(way.getStepX(), way.getStepY(), way.getStepZ(), new Vector3f());
        }
        return normals;
    }

    /** A story's strips' bars and glows, worked out from where its strips are. */
    private static Glows glows(LedRing ring) {
        List<Quad> bars = new ArrayList<>();
        List<Quad> washes = new ArrayList<>();
        for (LedRing.Strip strip : ring.strips()) {
            sides(strip, bars, washes);
        }
        return new Glows(List.copyOf(bars), List.copyOf(washes));
    }

    /**
     * A strip's bar on each side it runs along and its glow on each whose wall is flat enough to glow on, each drawn
     * in a frame moved to the strip's block and turned about its middle so that side is north, the wall at z 0 and the
     * room toward +z, and kept in the panel's frame.
     */
    private static void sides(LedRing.Strip strip, List<Quad> bars, List<Quad> washes) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Quaternionf turn = Axis.YP.rotationDegrees(180 - side.toYRot());
            Matrix4f frame = new Matrix4f().translation(strip.at().getX() + 0.5F, strip.at().getY(), strip.at().getZ() + 0.5F)
                .rotate(turn).translate(-0.5F, 0, -0.5F);
            if (strip.runsAlong(side)) {
                bar(strip, frame, turn, bars);
            }
            if (strip.glowsOn(side)) {
                washes.add(wash(strip, frame, turn));
            }
        }
    }

    /**
     * A strip's bar in its side's frame, where an animated mode brightens it over its dimmer mesh: its front, and its
     * top on the floor or its underside under the ceiling.
     */
    private static void bar(LedRing.Strip strip, Matrix4f frame, Quaternionf turn, List<Quad> bars) {
        float y0 = strip.ceiling() ? 1 - BAR : 0;
        float front = BAR + LIFT;
        bars.add(quad(strip, frame, turn, 0, 0, 1, new float[]{0, y0, front, 0, 0}, new float[]{1, y0, front, 0, 0},
            new float[]{1, y0 + BAR, front, 0, 0}, new float[]{0, y0 + BAR, front, 0, 0}));
        if (strip.ceiling()) {
            float under = 1 - BAR - LIFT;
            bars.add(quad(strip, frame, turn, 0, -1, 0, new float[]{0, under, 0, 0, 0}, new float[]{1, under, 0, 0, 0},
                new float[]{1, under, BAR, 0, 0}, new float[]{0, under, BAR, 0, 0}));
        } else {
            float top = BAR + LIFT;
            bars.add(quad(strip, frame, turn, 0, 1, 0, new float[]{0, top, 0, 0, 0}, new float[]{0, top, BAR, 0, 0},
                new float[]{1, top, BAR, 0, 0}, new float[]{1, top, 0, 0, 0}));
        }
    }

    /** The glow a strip casts on its wall in its side's frame, up from the floor or down from the ceiling. */
    private static Quad wash(LedRing.Strip strip, Matrix4f frame, Quaternionf turn) {
        float near = strip.ceiling() ? 1 - BAR : BAR;
        float far = strip.ceiling() ? near - WASH_REACH : near + WASH_REACH;
        float low = Math.min(near, far);
        float high = Math.max(near, far);
        // The texture is brightest along its top edge (v 0): at the strip, whichever way the glow runs from it.
        float lowV = strip.ceiling() ? 1 : 0;
        return quad(strip, frame, turn, 0, 0, 1, new float[]{0, low, LIFT, 0, lowV}, new float[]{1, low, LIFT, 1, lowV},
            new float[]{1, high, LIFT, 1, 1 - lowV}, new float[]{0, high, LIFT, 0, 1 - lowV});
    }

    /**
     * A quad facing one way in a side's frame, its corners each x, y, z and u, v there, kept in the panel's frame.
     */
    private static Quad quad(LedRing.Strip strip, Matrix4f frame, Quaternionf turn, float nx, float ny, float nz, float[]... corners) {
        float[] kept = new float[corners.length * CORNER];
        Vector3f at = new Vector3f();
        for (int corner = 0; corner < corners.length; corner++) {
            float[] given = corners[corner];
            frame.transformPosition(given[0], given[1], given[2], at);
            int first = corner * CORNER;
            kept[first] = at.x;
            kept[first + 1] = at.y;
            kept[first + 2] = at.z;
            kept[first + 3] = given[3];
            kept[first + 4] = given[4];
        }
        Vector3f facing = new Vector3f(nx, ny, nz).rotate(turn);
        return new Quad(strip, Direction.getNearest(facing.x, facing.y, facing.z), kept);
    }

    /**
     * An animated strip's bar brightened as its mode has it now, over its dimmer mesh. In the club's partner colour all
     * of it is added, over the strip's own dim glow.
     */
    private static void bar(VertexConsumer consumer, Matrix4f pose, Vector3f[] normals, Quad quad, LedSetting setting, double ticks) {
        LedRing.Strip strip = quad.strip();
        LedColour colour = LedLights.colourNow(setting, ticks, strip.ceiling());
        float intensity = LedLights.intensity(setting.mode(), ticks, strip.along(), strip.ceiling());
        float added = colour == setting.colour() ? Math.max(0, intensity - LedLights.ANIMATED_BASE) : intensity;
        if (added > 0) {
            draw(consumer, pose, normals, quad, 0xFF000000 | LedLights.scaled(colour.rgb(), LedLights.share(setting.brightness()) * added));
        }
    }

    /** A strip's glow on its wall, as bright as its mode has it now. */
    private static void wash(VertexConsumer consumer, Matrix4f pose, Vector3f[] normals, Quad quad, LedSetting setting, double ticks) {
        LedRing.Strip strip = quad.strip();
        LedColour colour = LedLights.colourNow(setting, ticks, strip.ceiling());
        float intensity = LedLights.intensity(setting.mode(), ticks, strip.along(), strip.ceiling());
        draw(consumer, pose, normals, quad, 0xFF000000 | LedLights.scaled(colour.rgb(), LedLights.share(setting.brightness()) * intensity * WASH_STRENGTH));
    }

    /** A quad in one colour, moved from the panel's frame to where the panel is drawn. */
    private static void draw(VertexConsumer consumer, Matrix4f pose, Vector3f[] normals, Quad quad, int argb) {
        Vector3f normal = normals[quad.facing().get3DDataValue()];
        float[] corners = quad.corners();
        for (int at = 0; at < corners.length; at += CORNER) {
            consumer.addVertex(pose, corners[at], corners[at + 1], corners[at + 2])
                .setColor(argb)
                .setUv(corners[at + 3], corners[at + 4])
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(normal.x, normal.y, normal.z);
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
