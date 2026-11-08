package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.floorwindow.FloorWindowBlock;
import dev.skyliners.floorwindow.FloorWindowBlockEntity;
import dev.skyliners.floorwindow.WindowDisc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a round floor window (see {@link FloorWindowBlock}), its blocks having no model of their own, as one crisp
 * circle however square its blocks, as the rocket's oculus ring is (see {@link OculusRing}). Each block is drawn as if
 * it lay in a floor, its face up, and turned to the face it was set into, so a window in a wall or a ceiling is the
 * same window looking out another way. Every block draws the floor it blends into over its face and its back but for
 * the iris's round hole, opening from the window's middle out as it is told to open (see {@link
 * FloorWindowBlockEntity}): the floor in strips across the block, each strip's ends on the circle itself, cut again
 * wherever the circle turns or crosses the block's edge, so the hole is round without a step. The window's middle
 * block draws what spans the whole window, in flat faces round it: the iris's steel wall down the depth of the floor round the hole, and on down its
 * shaft where it has one; a thin steel ring round the edge of the glass, so the window can be found shut; and in the
 * hole the faintly blue glass, flush with the floor, two glints across it. Its faces are shaded as the world's blocks
 * are, by the way they face, so it matches the floor round it. Left out while out of sight.
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
     * stand on: how far from its middle line each runs, and how wide either side of its line it is, in blocks, on a
     * full-sized window, and nearer and thinner on a smaller one.
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
    /** How each way a window can look out turns it from lying in a floor. */
    private static final Map<Direction, Frame> FRAMES = new EnumMap<>(Direction.class);

    static {
        for (int face = 0; face <= FACES; face++) {
            COS[face] = Math.cos(Mth.TWO_PI * face / FACES);
            SIN[face] = Math.sin(Mth.TWO_PI * face / FACES);
        }
        for (Direction out : Direction.values()) {
            FRAMES.put(out, Frame.of(out));
        }
    }

    /**
     * How a window looking out one way is turned from lying in a floor: the turn itself; the ways in the world its own
     * x and z run, and its face; and, in its own frame, the way that is up in the world, which its flat faces are lit
     * from, so each is as bright as the world's own blocks facing its way.
     */
    private record Frame(Quaternionf turn, Direction alongX, Direction alongZ, Direction out, Vector3f worldUp) {
        static Frame of(Direction out) {
            Quaternionf turn = out.getRotation();
            Vector3f x = turn.transform(new Vector3f(1, 0, 0));
            Vector3f z = turn.transform(new Vector3f(0, 0, 1));
            Vector3f up = new Quaternionf(turn).conjugate().transform(new Vector3f(0, 1, 0));
            return new Frame(turn, Direction.getNearest(x.x, x.y, x.z), Direction.getNearest(z.x, z.y, z.z), out, up);
        }

        /** Turns a pose at a block's corner so that what is drawn after lies as in a floor, looking out this way. */
        void apply(PoseStack pose) {
            pose.translate(0.5F, 0.5F, 0.5F);
            pose.mulPose(turn);
            pose.translate(-0.5F, -0.5F, -0.5F);
        }

        /** A point of the block in its own frame, from its corner, in the block's frame in the world. */
        Vector3f toWorld(float x, float y, float z) {
            return turn.transform(new Vector3f(x - 0.5F, y - 0.5F, z - 0.5F)).add(0.5F, 0.5F, 0.5F);
        }

        /** How far across the block, in its own frame from its corner, an offset in half blocks reaches along x. */
        float x(BlockPos halfBlocks) {
            return 0.5F + WindowDisc.halfBlocks(halfBlocks, alongX) / 2F;
        }

        /** The same along z. */
        float z(BlockPos halfBlocks) {
            return 0.5F + WindowDisc.halfBlocks(halfBlocks, alongZ) / 2F;
        }
    }

    /** A block of a window's light at the corners of its face and its back, and when it was looked at, in ticks. */
    private record Lit(Corners over, Corners under, long at) {
    }

    /**
     * The floor's look over a face of a block, in the window's own frame, which the strips are drawn in: its texture as
     * it lies on that face of the floor in the world, where its corner falls and how far it runs along x and z, in the
     * block atlas, the colour it is drawn in, tinted and shaded, and the light at its corners.
     */
    private record Cover(float u, float uX, float uZ, float v, float vX, float vZ, int colour, Corners light) {
        /** The floor's look over the block's face, y 1 in its own frame, or its back, y 0, looking out the way given. */
        static Cover of(FloorWindowBlockEntity window, Frame frame, Direction face, float y, Corners light) {
            BlockState floor = window.floor();
            Level level = window.getLevel();
            FaceLook look = FaceLook.of(floor, face);
            int colour = shaded(look.colour(floor, level, window.getBlockPos()), shade(level, face));
            Vector3f corner = frame.toWorld(0, y, 0);
            Vector3f alongX = frame.toWorld(1, y, 0);
            Vector3f alongZ = frame.toWorld(0, y, 1);
            float u = look.u(corner.x, corner.y, corner.z);
            float v = look.v(corner.x, corner.y, corner.z);
            return new Cover(u, look.u(alongX.x, alongX.y, alongX.z) - u, look.u(alongZ.x, alongZ.y, alongZ.z) - u, v,
                look.v(alongX.x, alongX.y, alongX.z) - v, look.v(alongZ.x, alongZ.y, alongZ.z) - v, colour, light);
        }
    }

    /** A strip of floor across a block, from one point along it to another, and at each where it starts and stops. */
    private record Strip(float near, float far, float nearFrom, float nearTo, float farFrom, float farTo) {
    }

    @Override
    public void render(FloorWindowBlockEntity window, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = window.getBlockState();
        boolean middle = window.toMiddle().equals(BlockPos.ZERO);
        double full = window.radius();
        float reach = middle ? (float) (full + 1 + window.shaft()) : 1;
        if (!(state.getBlock() instanceof FloorWindowBlock) || !Sight.ahead(pose.last(), reach)) {
            return;
        }
        FloorWindowBlockEntity middleBlock = middleOf(window);
        boolean open = middleBlock.getBlockState().getValue(FloorWindowBlock.OPEN);
        double opened = middleBlock.toward(DrawingClock.ticks(partialTick), open, IRIS_PER_TICK);
        double radius = full * opened * opened * (3 - 2 * opened);
        Frame frame = FRAMES.get(FloorWindowBlock.out(state));
        Lit lit = lit(window, frame, light);
        VertexConsumer cover = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();
        frame.apply(pose);
        PoseStack.Pose at = pose.last();
        float middleX = frame.x(window.toCentre());
        float middleZ = frame.z(window.toCentre());
        floor(cover, at, window, frame, lit, middleX, middleZ, radius);
        if (middle) {
            middleParts(buffers, at, window, frame, lit.over().at(middleX, middleZ), middleX, middleZ, radius);
        }
        pose.popPose();
    }

    /**
     * What the window's middle block draws across the whole window, from the window's middle so far across its own
     * frame: the ring, and while open the iris's wall, the shaft under it, the glass and its glints.
     */
    private static void middleParts(MultiBufferSource buffers, PoseStack.Pose at, FloorWindowBlockEntity window, Frame frame, int light,
                                    float middleX, float middleZ, double radius) {
        int shade = shade(window.getLevel(), frame.out());
        VertexConsumer steel = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        Round round = new Round(middleX, middleZ, frame.worldUp());
        ring(steel, at, light, shaded(STEEL, shade), round, window.radius());
        if (radius < SHUT) {
            return;
        }
        irisWall(steel, at, light, round, radius);
        shaft(steel, at, light, round, window.radius(), window.shaft());
        VertexConsumer glass = buffers.getBuffer(RenderType.entityTranslucent(FlatQuads.WHITE));
        glass(glass, at, light, shaded(GLASS, shade), round, radius);
        double scale = window.radius() / FloorWindowBlock.RADIUS;
        for (double glint : GLINTS) {
            glint(glass, at, light, shaded(GLINT, shade), round, radius, glint * scale, GLINT_HALF * scale);
        }
    }

    /**
     * The window's middle, so far across its middle block's own frame from its corner, and the way up in the world in
     * that frame, which its flat faces are lit from.
     */
    private record Round(float x, float z, Vector3f up) {
        /** A point round the middle: on a face's first corner, so far out. */
        float x(int face, double out) {
            return (float) (x + COS[face] * out);
        }

        float z(int face, double out) {
            return (float) (z + SIN[face] * out);
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
        double across = window.radius() + 1;
        return window.toMiddle().equals(BlockPos.ZERO) ? own.inflate(across).expandTowards(0, -window.shaft(), 0) : own;
    }

    /**
     * The floor over a block's face and its back, in the window's own frame, but for the iris's hole round the window's
     * middle, so far across from the block's corner.
     */
    private static void floor(VertexConsumer cover, PoseStack.Pose at, FloorWindowBlockEntity window, Frame frame, Lit lit, float middleX,
                              float middleZ, double radius) {
        Cover over = Cover.of(window, frame, frame.out(), 1, lit.over());
        Cover under = Cover.of(window, frame, frame.out().getOpposite(), 0, lit.under());
        for (Strip strip : strips(middleX, middleZ, radius)) {
            strip(cover, at, frame.worldUp(), over, under, strip);
        }
    }

    /**
     * The floor of a block but for the iris's hole round the window's middle, given from the block's corner: strips
     * across the block, cut wherever the circle reaches its furthest along the block or crosses its edge, so each
     * strip's ends follow the circle in a straight line from its one edge to its other.
     */
    private static List<Strip> strips(double middleX, double middleZ, double iris) {
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
     * A strip of the floor, on the face lit as what stands before the block is and on the back as what lies behind it,
     * smoothly across the block as a floor's own faces are; nothing where it has no width at either end.
     */
    private static void strip(VertexConsumer cover, PoseStack.Pose at, Vector3f up, Cover over, Cover under, Strip strip) {
        if (strip.nearTo() <= strip.nearFrom() && strip.farTo() <= strip.farFrom()) {
            return;
        }
        corner(cover, at, up, over, strip.nearFrom(), 1, strip.near());
        corner(cover, at, up, over, strip.farFrom(), 1, strip.far());
        corner(cover, at, up, over, strip.farTo(), 1, strip.far());
        corner(cover, at, up, over, strip.nearTo(), 1, strip.near());
        corner(cover, at, up, under, strip.nearFrom(), 0, strip.near());
        corner(cover, at, up, under, strip.nearTo(), 0, strip.near());
        corner(cover, at, up, under, strip.farTo(), 0, strip.far());
        corner(cover, at, up, under, strip.farFrom(), 0, strip.far());
    }

    /**
     * A corner of a strip of the floor, on the face or the back, in the window's own frame, its texture laid as the
     * floor's own is on that face, lit from the world's up so its shade is its colour's alone.
     */
    private static void corner(VertexConsumer cover, PoseStack.Pose at, Vector3f up, Cover face, float x, float y, float z) {
        float u = face.u() + face.uX() * x + face.uZ() * z;
        float v = face.v() + face.vX() * x + face.vZ() * z;
        cover.addVertex(at, x, y, z).setColor(face.colour()).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(face.light().at(x, z))
            .setNormal(at, up.x, up.y, up.z);
    }

    /** A block of a window's light at its corners, looked at again every so often, as light round it seldom changes. */
    private static Lit lit(FloorWindowBlockEntity window, Frame frame, int own) {
        long now = DrawingClock.wholeTicks();
        Lit known = LIT.get(window);
        if (known == null || now - known.at() >= LIGHT_EVERY || now < known.at()) {
            BlockPos pos = window.getBlockPos();
            Level level = window.getLevel();
            Corners over = corners(level, pos.relative(frame.out()), frame.alongX(), frame.alongZ(), own);
            Corners under = corners(level, pos.relative(frame.out().getOpposite()), frame.alongX(), frame.alongZ(), own);
            known = new Lit(over, under, now);
            LIT.put(window, known);
        }
        return known;
    }

    /**
     * The light at the four corners of a face of a block, each the mean of the four blocks before the face that meet
     * there, as smooth lighting has it, so the window's floor shades into the floor round it with no edge between
     * blocks: in a three by three grid round the block before the face, running the two ways across it given; the
     * window's own light everywhere where there is no level.
     */
    private static Corners corners(@Nullable Level level, BlockPos before, Direction first, Direction second, int own) {
        int[] blocks = new int[9];
        int[] sky = new int[9];
        for (int cell = 0; cell < 9; cell++) {
            BlockPos at = before.relative(first, cell % 3 - 1).relative(second, cell / 3 - 1);
            int light = level == null ? own : LevelRenderer.getLightColor(level, at);
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

    /**
     * How bright the world draws a block's face facing the way given, out of 255: full facing up, darker facing
     * sideways and darkest facing down, as the level has it; full where there is no level.
     */
    private static int shade(@Nullable Level level, Direction facing) {
        return level == null ? 255 : Math.round(level.getShade(facing, true) * 255);
    }

    /** A colour shaded by so much out of 255, its alpha kept. */
    private static int shaded(int colour, int shade) {
        return FastColor.ARGB32.color(FastColor.ARGB32.alpha(colour), FastColor.ARGB32.red(colour) * shade / 255,
            FastColor.ARGB32.green(colour) * shade / 255, FastColor.ARGB32.blue(colour) * shade / 255);
    }

    /** The steel ring round the glass's edge, on the face, standing just proud of the floor. */
    private static void ring(VertexConsumer steel, PoseStack.Pose at, int light, int colour, Round round, double inner) {
        double outer = inner + RING;
        float y = 1 + PROUD;
        Vector3f up = round.up();
        for (int face = 0; face < FACES; face++) {
            FlatQuads.quad(steel, at, light, colour, up.x, up.y, up.z, round.x(face, inner), y, round.z(face, inner), round.x(face, outer), y,
                round.z(face, outer), round.x(face + 1, outer), y, round.z(face + 1, outer), round.x(face + 1, inner), y, round.z(face + 1, inner));
        }
    }

    /** The iris's steel wall round its hole, from the floor's back up to the glass, facing in. */
    private static void irisWall(VertexConsumer steel, PoseStack.Pose at, int light, Round round, double iris) {
        tube(steel, at, light, round, iris, 0, GLASS_Y);
    }

    /** The shaft under the window, a steel tube as wide as its glass, so many blocks down from the floor's back. */
    private static void shaft(VertexConsumer steel, PoseStack.Pose at, int light, Round round, double out, int depth) {
        if (depth > 0) {
            tube(steel, at, light, round, out, -depth, 0);
        }
    }

    /**
     * A steel tube round the window's middle, so far out, from one height to another, facing in, each corner lit as
     * the tube's own round face there is, so it shades smoothly round without showing its flat faces.
     */
    private static void tube(VertexConsumer steel, PoseStack.Pose at, int light, Round round, double out, float foot, float head) {
        for (int face = 0; face < FACES; face++) {
            tubeCorner(steel, at, light, round, face, out, foot);
            tubeCorner(steel, at, light, round, face, out, head);
            tubeCorner(steel, at, light, round, face + 1, out, head);
            tubeCorner(steel, at, light, round, face + 1, out, foot);
        }
    }

    /** A corner of a tube's face, on a face's first corner round it, so far out and so high, facing in. */
    private static void tubeCorner(VertexConsumer steel, PoseStack.Pose at, int light, Round round, int face, double out, float y) {
        steel.addVertex(at, round.x(face, out), y, round.z(face, out)).setColor(STEEL_DARK).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(at, (float) -COS[face], 0, (float) -SIN[face]);
    }

    /** The glass in the iris's hole, flush with the floor, a wedge for each face round it. */
    private static void glass(VertexConsumer glass, PoseStack.Pose at, int light, int colour, Round round, double iris) {
        Vector3f up = round.up();
        for (int face = 0; face < FACES; face++) {
            FlatQuads.quad(glass, at, light, colour, up.x, up.y, up.z, round.x(), GLASS_Y, round.z(), round.x(face, iris), GLASS_Y,
                round.z(face, iris), round.x(face + 1, iris), GLASS_Y, round.z(face + 1, iris), round.x(face + 1, iris), GLASS_Y, round.z(face + 1, iris));
        }
    }

    /** A glint across the glass, so far from its middle line and so wide either side of it, its ends on the iris's edge. */
    private static void glint(VertexConsumer glass, PoseStack.Pose at, int light, int colour, Round round, double iris, double off, double half) {
        double from = off - half;
        double to = off + half;
        if (Math.abs(from) >= iris || Math.abs(to) >= iris) {
            return;
        }
        double fromHalf = Math.sqrt(iris * iris - from * from);
        double toHalf = Math.sqrt(iris * iris - to * to);
        float y = GLASS_Y + PROUD;
        Vector3f up = round.up();
        FlatQuads.quad(glass, at, light, colour, up.x, up.y, up.z, glintX(round, from, -fromHalf), y, glintZ(round, from, -fromHalf),
            glintX(round, from, fromHalf), y, glintZ(round, from, fromHalf), glintX(round, to, toHalf), y, glintZ(round, to, toHalf),
            glintX(round, to, -toHalf), y, glintZ(round, to, -toHalf));
    }

    /**
     * A point of a glint, from the window's middle block's corner: so far across the glass's middle line, which runs
     * from its front left to its back right, and so far along it.
     */
    private static float glintX(Round round, double across, double along) {
        return (float) (round.x() + (across + along) * Mth.SQRT_OF_TWO / 2);
    }

    private static float glintZ(Round round, double across, double along) {
        return (float) (round.z() + (along - across) * Mth.SQRT_OF_TWO / 2);
    }
}
