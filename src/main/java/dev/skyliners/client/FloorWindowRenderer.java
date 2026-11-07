package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.floorwindow.FloorWindowBlock;
import dev.skyliners.floorwindow.FloorWindowBlockEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws a round floor window (see {@link FloorWindowBlock}), its blocks having no model of their own, as one crisp
 * circle however square its blocks, as the rocket's oculus ring is (see {@link OculusRing}). Every block draws the
 * floor it blends into over its top and underside but for the iris's round hole, opening from the window's middle out
 * as it is told to open (see {@link FloorWindowBlockEntity}): the floor in strips across the block, each strip's ends
 * on the circle itself, cut again wherever the circle turns or crosses the block's edge, so the hole is round without a
 * step. The window's middle block draws what spans the whole window, in flat faces round it: the iris's steel wall down
 * the depth of the floor round the hole, and on down its shaft where it has one; a thin steel ring on top round the
 * edge of the glass, so the window can be found shut; and in the hole the faintly blue glass, flush with the floor, two
 * glints across it. Left out while out of sight.
 */
public class FloorWindowRenderer implements BlockEntityRenderer<FloorWindowBlockEntity> {
    /** How much of the way open or shut the iris comes each tick: all the way in a second and a bit. */
    private static final double IRIS_PER_TICK = 1.0 / 24;
    /** How many flat faces go round the iris's wall, its glass and the ring. */
    private static final int FACES = 64;
    private static final double[] COS = new double[FACES + 1];
    private static final double[] SIN = new double[FACES + 1];
    /** How many strips the floor is cut into across a block at least, so the circle between their ends is straight. */
    private static final int STRIPS = 16;
    /** How wide the steel ring round the glass is, in blocks. */
    private static final double RING = 1.5 / 16;
    /** How high over the block's foot the glass lies, in blocks: just under the floor's top, flush with it. */
    private static final float GLASS_Y = 0.994F;
    /** How thin the ring and the glints stand proud of what they lie on, in blocks. */
    private static final float PROUD = 0.002F;
    /** The smallest iris drawn open, in blocks: any less and it is shut. */
    private static final double SHUT = 0.01;
    /**
     * The glass's two glints, streaks across it from its front left to its back right, so it is seen to be glass to
     * stand on: how far from its middle line each runs, and how wide either side of its line it is, in blocks.
     */
    private static final double[] GLINTS = {-1.4, 0.9};
    private static final double GLINT_HALF = 0.22;
    private static final int STEEL = 0xFF9AA1A8;
    private static final int STEEL_DARK = 0xFF5C636B;
    private static final int GLASS = 0x40A8D8F0;
    private static final int GLINT = 0x70F4FCFF;
    /** How often the light round each block of a window is looked at again, in ticks. */
    private static final long LIGHT_EVERY = 10;
    /** Each block of a window's light at its corners over and under it, and when it was looked at, in ticks. */
    private static final Map<FloorWindowBlockEntity, Lit> LIT = new WeakHashMap<>();
    /** The random a floor's model is asked for its top with: the same every time, so its look is too. */
    private static final RandomSource SAME = RandomSource.create();

    static {
        for (int face = 0; face <= FACES; face++) {
            COS[face] = Math.cos(Mth.TWO_PI * face / FACES);
            SIN[face] = Math.sin(Mth.TWO_PI * face / FACES);
        }
    }

    /** A floor's top: its texture, and the tint it takes, if any. */
    private record Look(TextureAtlasSprite sprite, int tintIndex) {
    }

    /** A block of a window's light at the corners of its top and underside, and when it was looked at, in ticks. */
    private record Lit(Corners over, Corners under, long at) {
    }

    /** A strip of floor across a block, from one point along it to another, and at each where it starts and stops. */
    private record Strip(float near, float far, float nearFrom, float nearTo, float farFrom, float farTo) {
    }

    @Override
    public void render(FloorWindowBlockEntity window, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = window.getBlockState();
        boolean middle = window.toMiddle().equals(BlockPos.ZERO);
        float reach = middle ? (float) (FloorWindowBlock.RADIUS + 1 + window.shaft()) : 1;
        if (!(state.getBlock() instanceof FloorWindowBlock) || !Sight.ahead(pose.last(), reach)) {
            return;
        }
        FloorWindowBlockEntity middleBlock = middleOf(window);
        boolean open = middleBlock.getBlockState().getValue(FloorWindowBlock.OPEN);
        double opened = middleBlock.toward(DrawingClock.ticks(partialTick), open, IRIS_PER_TICK);
        double radius = FloorWindowBlock.RADIUS * opened * opened * (3 - 2 * opened);
        PoseStack.Pose at = pose.last();
        Look look = look(window.floor());
        int colour = tint(window, look);
        Lit lit = lit(window, light);
        Corners over = lit.over();
        Corners under = lit.under();
        VertexConsumer cover = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        for (Strip strip : floor(window.toMiddle().getX() + 0.5, window.toMiddle().getZ() + 0.5, radius)) {
            strip(cover, at, over, under, colour, look.sprite(), strip);
        }
        if (!middle) {
            return;
        }
        int lightOver = over.at(0.5F, 0.5F);
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        ring(steel, at, lightOver);
        if (radius < SHUT) {
            return;
        }
        irisWall(steel, at, lightOver, radius);
        shaft(steel, at, lightOver, window.shaft());
        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(FlatQuads.WHITE));
        glass(glass, at, lightOver, radius);
        for (double glint : GLINTS) {
            glint(glass, at, lightOver, radius, glint);
        }
    }

    /**
     * The window's middle block, whose iris every block of the window draws, so all of them open and shut as one, even
     * where one of them was out of sight for a while; the block itself where its middle is gone.
     */
    private static FloorWindowBlockEntity middleOf(FloorWindowBlockEntity window) {
        Level level = window.getLevel();
        return level != null && level.getBlockEntity(window.middle()) instanceof FloorWindowBlockEntity middle ? middle : window;
    }

    /** The middle block draws the whole window; every other block only its own. */
    @Override
    public AABB getRenderBoundingBox(FloorWindowBlockEntity window) {
        AABB own = new AABB(window.getBlockPos());
        double across = FloorWindowBlock.RADIUS + 1;
        return window.toMiddle().equals(BlockPos.ZERO) ? own.inflate(across, 0, across).expandTowards(0, -window.shaft(), 0) : own;
    }

    /**
     * The floor of a block but for the iris's hole round the window's middle, given from the block's corner: strips
     * across the block, cut wherever the circle reaches its furthest along the block or crosses its edge, so each
     * strip's ends follow the circle in a straight line from its one edge to its other.
     */
    private static List<Strip> floor(double middleX, double middleZ, double iris) {
        if (iris < SHUT) {
            return List.of(new Strip(0, 1, 0, 1, 0, 1));
        }
        double[] cuts = cuts(middleX, middleZ, iris);
        List<Strip> strips = new ArrayList<>();
        for (int cut = 0; cut + 1 < cuts.length; cut++) {
            if (cuts[cut + 1] <= cuts[cut]) {
                continue;
            }
            float near = (float) cuts[cut];
            float far = (float) cuts[cut + 1];
            double nearHalf = halfWidth(middleZ, iris, near);
            double farHalf = halfWidth(middleZ, iris, far);
            if (nearHalf <= 0 && farHalf <= 0) {
                strips.add(new Strip(near, far, 0, 1, 0, 1));
                continue;
            }
            strips.add(new Strip(near, far, 0, clamp(middleX - nearHalf), 0, clamp(middleX - farHalf)));
            strips.add(new Strip(near, far, clamp(middleX + nearHalf), 1, clamp(middleX + farHalf), 1));
        }
        return strips;
    }

    /**
     * Where the floor of a block is cut across: its edges, every sixteenth of it, where the circle reaches furthest
     * along the block, and where it crosses the block's two edges along it; in order, each kept within the block.
     */
    private static double[] cuts(double middleX, double middleZ, double iris) {
        double[] cuts = new double[STRIPS + 7];
        int count = 0;
        for (int strip = 0; strip <= STRIPS; strip++) {
            cuts[count++] = (double) strip / STRIPS;
        }
        cuts[count++] = middleZ - iris;
        cuts[count++] = middleZ + iris;
        for (int edge = 0; edge <= 1; edge++) {
            double across = Math.max(0, iris * iris - (edge - middleX) * (edge - middleX));
            cuts[count++] = middleZ - Math.sqrt(across);
            cuts[count++] = middleZ + Math.sqrt(across);
        }
        for (int cut = 0; cut < count; cut++) {
            cuts[cut] = Mth.clamp(cuts[cut], 0, 1);
        }
        Arrays.sort(cuts, 0, count);
        return Arrays.copyOf(cuts, count);
    }

    /** How far either side of the window's middle the iris's hole reaches across a block at a point along it. */
    private static double halfWidth(double middleZ, double iris, double along) {
        double across = iris * iris - (along - middleZ) * (along - middleZ);
        return across > 0 ? Math.sqrt(across) : 0;
    }

    private static float clamp(double across) {
        return (float) Mth.clamp(across, 0, 1);
    }

    /**
     * A strip of the floor, on top lit as what stands over the block is and underneath as what lies under it, smoothly
     * across the block as a floor's own faces are; nothing where it has no width at either end.
     */
    private static void strip(VertexConsumer cover, PoseStack.Pose at, Corners over, Corners under, int colour, TextureAtlasSprite sprite,
                              Strip strip) {
        if (strip.nearTo() <= strip.nearFrom() && strip.farTo() <= strip.farFrom()) {
            return;
        }
        corner(cover, at, over, colour, sprite, strip.nearFrom(), 1, strip.near());
        corner(cover, at, over, colour, sprite, strip.farFrom(), 1, strip.far());
        corner(cover, at, over, colour, sprite, strip.farTo(), 1, strip.far());
        corner(cover, at, over, colour, sprite, strip.nearTo(), 1, strip.near());
        corner(cover, at, under, colour, sprite, strip.nearFrom(), 0, strip.near());
        corner(cover, at, under, colour, sprite, strip.nearTo(), 0, strip.near());
        corner(cover, at, under, colour, sprite, strip.farTo(), 0, strip.far());
        corner(cover, at, under, colour, sprite, strip.farFrom(), 0, strip.far());
    }

    /** A block of a window's light at its corners, looked at again every so often, as light round it seldom changes. */
    private static Lit lit(FloorWindowBlockEntity window, int own) {
        long now = DrawingClock.wholeTicks();
        Lit known = LIT.get(window);
        if (known == null || now - known.at() >= LIGHT_EVERY || now < known.at()) {
            known = new Lit(corners(window, 1, own), corners(window, -1, own), now);
            LIT.put(window, known);
        }
        return known;
    }

    /**
     * The light at the four corners of a block's top or underside, each the mean of the four blocks over or under the
     * floor that meet there, as smooth lighting has it, so the window's floor shades into the floor round it with no
     * edge between blocks; the window's own light everywhere where there is no level.
     */
    private static Corners corners(FloorWindowBlockEntity window, int beside, int own) {
        int[] blocks = new int[9];
        int[] sky = new int[9];
        BlockPos middle = window.getBlockPos().above(beside);
        for (int cell = 0; cell < 9; cell++) {
            BlockPos at = middle.offset(cell % 3 - 1, 0, cell / 3 - 1);
            int light = window.getLevel() == null ? own : LevelRenderer.getLightColor(window.getLevel(), at);
            blocks[cell] = LightTexture.block(light);
            sky[cell] = LightTexture.sky(light);
        }
        return new Corners(cornerMean(blocks), cornerMean(sky));
    }

    /** The mean at each of a block's corners, near left, near right, far left, far right, of a three by three grid. */
    private static float[] cornerMean(int[] grid) {
        float[] corners = new float[4];
        for (int corner = 0; corner < 4; corner++) {
            int x = corner % 2;
            int z = corner / 2;
            corners[corner] = (grid[z * 3 + x] + grid[z * 3 + x + 1] + grid[(z + 1) * 3 + x] + grid[(z + 1) * 3 + x + 1]) / 4F;
        }
        return corners;
    }

    /** The light at the corners of a block's face, block and sky apart: near left, near right, far left, far right. */
    private record Corners(float[] block, float[] sky) {
        /** How far apart two levels of light are in the light map, and where its sky light is packed. */
        private static final float LEVEL = 16;
        private static final int SKY_SHIFT = 16;

        /**
         * The light at a point of the face, from its near left corner, blended from its corners, packed as the light
         * map takes it, in sixteenths of a level, so it shades smoothly between levels.
         */
        int at(float x, float z) {
            return Math.round(blend(block, x, z) * LEVEL) | Math.round(blend(sky, x, z) * LEVEL) << SKY_SHIFT;
        }

        private static float blend(float[] corners, float x, float z) {
            return Mth.lerp(z, Mth.lerp(x, corners[0], corners[1]), Mth.lerp(x, corners[2], corners[3]));
        }
    }

    /** A corner of a strip of the floor, on top or underneath, its texture laid as the block's own top is. */
    private static void corner(VertexConsumer cover, PoseStack.Pose at, Corners light, int colour, TextureAtlasSprite sprite, float x, float y,
                               float z) {
        float u = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * x;
        float v = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * z;
        cover.addVertex(at, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light.at(x, z))
            .setNormal(at, 0, y > 0 ? 1 : -1, 0);
    }

    /** The steel ring round the glass's edge, on top, standing just proud of the floor. */
    private static void ring(VertexConsumer steel, PoseStack.Pose at, int light) {
        double inner = FloorWindowBlock.RADIUS;
        double outer = inner + RING;
        float y = 1 + PROUD;
        for (int face = 0; face < FACES; face++) {
            FlatQuads.quad(steel, at, light, STEEL, 0, 1, 0, x(face, inner), y, z(face, inner), x(face, outer), y, z(face, outer),
                x(face + 1, outer), y, z(face + 1, outer), x(face + 1, inner), y, z(face + 1, inner));
        }
    }

    /** The iris's steel wall round its hole, from the floor's foot up to the glass, facing in. */
    private static void irisWall(VertexConsumer steel, PoseStack.Pose at, int light, double iris) {
        for (int face = 0; face < FACES; face++) {
            FlatQuads.quad(steel, at, light, STEEL_DARK, (float) -COS[face], 0, (float) -SIN[face], x(face, iris), 0, z(face, iris), x(face, iris),
                GLASS_Y, z(face, iris), x(face + 1, iris), GLASS_Y, z(face + 1, iris), x(face + 1, iris), 0, z(face + 1, iris));
        }
    }

    /** The shaft under the window, a steel tube as wide as its glass, so many blocks down from the floor's foot. */
    private static void shaft(VertexConsumer steel, PoseStack.Pose at, int light, int depth) {
        if (depth <= 0) {
            return;
        }
        double out = FloorWindowBlock.RADIUS;
        for (int face = 0; face < FACES; face++) {
            FlatQuads.quad(steel, at, light, STEEL_DARK, (float) -COS[face], 0, (float) -SIN[face], x(face, out), -depth, z(face, out),
                x(face, out), 0,
                z(face, out), x(face + 1, out), 0, z(face + 1, out), x(face + 1, out), -depth, z(face + 1, out));
        }
    }

    /** The glass in the iris's hole, flush with the floor, a wedge for each face round it. */
    private static void glass(VertexConsumer glass, PoseStack.Pose at, int light, double iris) {
        for (int face = 0; face < FACES; face++) {
            FlatQuads.quad(glass, at, light, GLASS, 0, 1, 0, 0.5F, GLASS_Y, 0.5F, x(face, iris), GLASS_Y, z(face, iris), x(face + 1, iris), GLASS_Y,
                z(face + 1, iris), x(face + 1, iris), GLASS_Y, z(face + 1, iris));
        }
    }

    /** A glint across the glass, so far from its middle line, its ends on the iris's edge. */
    private static void glint(VertexConsumer glass, PoseStack.Pose at, int light, double iris, double off) {
        double from = off - GLINT_HALF;
        double to = off + GLINT_HALF;
        if (Math.abs(from) >= iris || Math.abs(to) >= iris) {
            return;
        }
        double fromHalf = Math.sqrt(iris * iris - from * from);
        double toHalf = Math.sqrt(iris * iris - to * to);
        float y = GLASS_Y + PROUD;
        FlatQuads.quad(glass, at, light, GLINT, 0, 1, 0, glintX(from, -fromHalf), y, glintZ(from, -fromHalf), glintX(from, fromHalf), y,
            glintZ(from, fromHalf), glintX(to, toHalf), y, glintZ(to, toHalf), glintX(to, -toHalf), y, glintZ(to, -toHalf));
    }

    /**
     * A point of a glint, from the window's middle block's corner: so far across the glass's middle line, which runs
     * from its front left to its back right, and so far along it.
     */
    private static float glintX(double across, double along) {
        return (float) (0.5 + (across + along) * Mth.SQRT_OF_TWO / 2);
    }

    private static float glintZ(double across, double along) {
        return (float) (0.5 + (along - across) * Mth.SQRT_OF_TWO / 2);
    }

    /** A point round the window's middle, from its middle block's corner: on a face's first corner, so far out. */
    private static float x(int face, double out) {
        return (float) (0.5 + COS[face] * out);
    }

    private static float z(int face, double out) {
        return (float) (0.5 + SIN[face] * out);
    }

    /**
     * The look of a floor's top, its texture and its tint, from its model as it is now, so it follows the textures
     * through a reload of them: a lookup in the models' own table and their list of quads, cheap enough for every block
     * of a window each frame.
     */
    private static Look look(BlockState floor) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(floor);
        SAME.setSeed(0);
        List<BakedQuad> top = model.getQuads(floor, Direction.UP, SAME);
        if (top.isEmpty()) {
            return new Look(model.getParticleIcon(), -1);
        }
        BakedQuad quad = top.get(0);
        return new Look(quad.getSprite(), quad.isTinted() ? quad.getTintIndex() : -1);
    }

    /** The colour a floor's top is drawn in where it stands: white, or its tint there, as grass is. */
    private static int tint(FloorWindowBlockEntity window, Look look) {
        if (look.tintIndex() < 0 || window.getLevel() == null) {
            return 0xFFFFFFFF;
        }
        int rgb = Minecraft.getInstance().getBlockColors().getColor(window.floor(), window.getLevel(), window.getBlockPos(), look.tintIndex());
        return 0xFF000000 | rgb;
    }
}
