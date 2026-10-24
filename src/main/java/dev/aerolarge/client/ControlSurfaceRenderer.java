package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.ControlSurfaces;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Draws the airliner's ailerons and elevators from its autopilot: panels of the wing's own lifting blocks, hinged
 * along the trailing edges of the outer wings and the tailplane, one for each step of the trailing edge (see
 * {@link ControlSurfaces.Panel}), and tilted up and down with how hard the control surfaces they stand for steer, so
 * their work can be seen; the lifting blocks the surfaces are made of look the same either way up, and a block can
 * only face six ways, so the panels are drawn rather than built. Each swings on a steel hinge along its leading edge,
 * a rod with brackets round it, fast to the wing. The clients are told how far the panels swing in steps,
 * every few ticks at most; the panels ease toward each step, so they swing rather than jump. Each is lit by the light
 * just behind it. An airliner built before the panels draws none.
 */
public class ControlSurfaceRenderer implements BlockEntityRenderer<AutopilotBlockEntity> {
    /** How far a panel swings with its surfaces all the way over, in degrees. */
    private static final float MOST_SWING = 25;
    /** How far in from either end a panel is drawn, in blocks, so neighbours stand apart as separate panels. */
    private static final float GAP = 1 / 16F;
    /** How fast a panel eases toward how far it should swing: this share of the way left, every second, is all but gone. */
    private static final double EASE = 8;
    /** The longest a frame is counted as, in seconds, so a panel does not leap after the game stalls. */
    private static final double LONGEST_FRAME = 0.25;
    /** How far off the panels are still drawn, in blocks: an airliner's wings are seen from a long way. */
    private static final int VIEW_DISTANCE = 256;
    /** Vanilla's plain white texture, painted over in the vertices' colours: the hinges. */
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    /**
     * The hinge a panel swings on, fast to the wing: a rod along its whole length, this thick (a little thicker than the
     * wing's lifting layer, so it shows), the panel hung this far aft of it, so a dark slot runs between wing and panel;
     * and a bracket round the rod every so many blocks, this wide along it and this tall and deep, in blocks; in dark
     * steel, the brackets a shade darker.
     */
    private static final float ROD = 0.3F;
    private static final float HINGE_SLOT = 0.12F;
    private static final int BRACKET_EVERY = 2;
    private static final float BRACKET_WIDTH = 0.18F;
    private static final float BRACKET_SIZE = 0.42F;
    private static final int ROD_COLOUR = 0xFF4A5058;
    private static final int BRACKET_COLOUR = 0xFF3C4148;

    private final BlockRenderDispatcher blocks;
    /** How far each autopilot's panels swing as now drawn, eased toward what the surfaces show, and when last drawn. */
    private final Map<AutopilotBlockEntity, Swing> swings = new WeakHashMap<>();

    private static final class Swing {
        final Map<ControlSurfaces.Axis, Double> shown = new EnumMap<>(ControlSurfaces.Axis.class);
        long drawnAt = System.nanoTime();
    }

    public ControlSurfaceRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(AutopilotBlockEntity autopilot, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        ControlSurfaces surfaces = autopilot.surfaces();
        List<ControlSurfaces.Panel> panels = surfaces.panels();
        Level level = autopilot.getLevel();
        if (panels.isEmpty() || level == null) {
            return;
        }
        Swing swing = ease(autopilot, surfaces);
        Direction nose = autopilot.getBlockState().getValue(AutopilotBlock.FACING);
        pose.pushPose();
        // Onto the middle of the autopilot's block: x to port, y up, z out along the nose.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-nose.toYRot()));
        for (ControlSurfaces.Panel panel : panels) {
            float swung = (float) (MOST_SWING * panel.way() * swing.shown.getOrDefault(panel.axis(), 0.0));
            int light = SableClient.light(level, BlockPos.containing(behind(autopilot, nose, panel)));
            panel(pose, buffers, panel, swung, light);
        }
        pose.popPose();
    }

    /** Eases the autopilot's panels toward how far its surfaces are deflected, as far as the time since last drawn goes. */
    private Swing ease(AutopilotBlockEntity autopilot, ControlSurfaces surfaces) {
        Swing swing = swings.computeIfAbsent(autopilot, ignored -> new Swing());
        long now = System.nanoTime();
        double seconds = Math.min(LONGEST_FRAME, (now - swing.drawnAt) / 1e9);
        swing.drawnAt = now;
        double share = 1 - Math.exp(-EASE * seconds);
        for (ControlSurfaces.Axis axis : ControlSurfaces.Axis.values()) {
            double shown = swing.shown.getOrDefault(axis, 0.0);
            swing.shown.put(axis, shown + (surfaces.deflection(axis) - shown) * share);
        }
        return swing;
    }

    /**
     * One panel, swung this far about its hinge (in degrees, its trailing edge up for more): its blocks laid out from
     * the inner end of the hinge outward and aft, the middle of each block level with the hinge, all swung together.
     */
    private void panel(PoseStack pose, MultiBufferSource buffers, ControlSurfaces.Panel panel, float swung, int light) {
        // In the frame the panel is drawn in, x runs to port: from the inner end, out toward the tip.
        float out = (float) Math.signum(panel.inner().x - panel.outer().x);
        int width = (int) Math.round(Math.abs(panel.outer().x - panel.inner().x));
        pose.pushPose();
        pose.translate(-panel.inner().x, panel.inner().y, panel.inner().z);
        hinge(new Painter(buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE)), pose.last(), light), out, width);
        // Trailing edge up for more: aft is down the z axis, so a turn about x lifts it.
        pose.mulPose(Axis.XP.rotationDegrees(swung));
        pose.translate(out * GAP, 0, -HINGE_SLOT);
        pose.scale((width - 2 * GAP) / width, 1, 1);
        for (int across = 0; across < width; across++) {
            for (int row = 0; row < panel.rows(); row++) {
                pose.pushPose();
                pose.translate(out > 0 ? across : -across - 1, -0.5, -row - 1);
                blocks.renderSingleBlock(panel.look(), pose, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /**
     * The hinge along a panel's leading edge, where it meets the wing, from its inner end so many blocks out (the way
     * given, along x): the rod, and brackets round it at either end and evenly between, about every
     * {@link #BRACKET_EVERY} blocks. It stays with the wing as the panel swings on it.
     */
    private static void hinge(Painter paint, float out, int width) {
        float half = ROD / 2;
        paint.box(Math.min(0, out * width), -half, -half, Math.max(0, out * width), half, half, ROD_COLOUR);
        float bracket = BRACKET_SIZE / 2;
        int gaps = Math.max(1, Math.round((float) width / BRACKET_EVERY));
        for (int i = 0; i <= gaps; i++) {
            // Drawn in from the ends so they stay within the panel.
            float middle = out * Mth.clamp((float) width * i / gaps, BRACKET_WIDTH / 2, width - BRACKET_WIDTH / 2);
            paint.box(middle - BRACKET_WIDTH / 2, -bracket, -bracket, middle + BRACKET_WIDTH / 2, bracket, bracket, BRACKET_COLOUR);
        }
    }

    /** The middle of a panel's trailing edge in the level, a little behind it: where its light is read. */
    private static Vec3 behind(AutopilotBlockEntity autopilot, Direction nose, ControlSurfaces.Panel panel) {
        Vec3 middle = panel.inner().add(panel.outer()).scale(0.5).add(0, 0, -panel.rows() - 0.5);
        return inLevel(autopilot, nose, middle);
    }

    /** A point given to starboard, up and ahead of the middle of the autopilot's block, in the level. */
    private static Vec3 inLevel(AutopilotBlockEntity autopilot, Direction nose, Vec3 point) {
        Vec3 ahead = Vec3.atLowerCornerOf(nose.getNormal());
        Vec3 starboard = Vec3.atLowerCornerOf(nose.getClockWise().getNormal());
        return Vec3.atCenterOf(autopilot.getBlockPos()).add(starboard.scale(point.x)).add(0, point.y, 0).add(ahead.scale(point.z));
    }

    /** Where the hinges' quads go: the buffer, the pose they are drawn in and the light they are lit by. */
    private record Painter(VertexConsumer consumer, PoseStack.Pose pose, int light) {
        /** A box between two corners, every face facing out. */
        void box(float x0, float y0, float z0, float x1, float y1, float z1, int colour) {
            quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, colour);
            quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, colour);
            quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, colour);
            quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, colour);
            quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, colour);
            quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, colour);
        }

        private void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
                          float dx, float dy, float dz, float nx, float ny, float nz, int colour) {
            vertex(ax, ay, az, nx, ny, nz, colour);
            vertex(bx, by, bz, nx, ny, nz, colour);
            vertex(cx, cy, cz, nx, ny, nz, colour);
            vertex(dx, dy, dz, nx, ny, nz, colour);
        }

        private void vertex(float x, float y, float z, float nx, float ny, float nz, int colour) {
            consumer.addVertex(pose, x, y, z)
                .setColor(colour)
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        }
    }

    /**
     * Drawn whether or not the section the autopilot stands in is: the autopilot is up in the cockpit, a hundred blocks
     * and more from the panels, and that section is often off to the side of the view or hidden behind the hull while
     * the wings are in plain sight.
     */
    @Override
    public boolean shouldRenderOffScreen(AutopilotBlockEntity autopilot) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    @Override
    public AABB getRenderBoundingBox(AutopilotBlockEntity autopilot) {
        AABB box = new AABB(autopilot.getBlockPos());
        Direction nose = autopilot.getBlockState().getValue(AutopilotBlock.FACING);
        for (ControlSurfaces.Panel panel : autopilot.surfaces().panels()) {
            for (Vec3 end : new Vec3[]{panel.inner(), panel.outer()}) {
                Vec3 at = inLevel(autopilot, nose, end);
                box = box.minmax(new AABB(at, at).inflate(panel.rows() + 1));
            }
        }
        return box;
    }
}
