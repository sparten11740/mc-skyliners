package dev.aerolarge.client;

import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.OvalWindowBlock;
import dev.aerolarge.client.ModelQuads.Corner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The model of a piece of the rocket's oval window ({@link OvalWindowBlock}): the inner half of its block, baked once
 * as the models load, each piece drawing its share of the whole window. Outside, the glass is a thin dome bulging out
 * of the skin over the whole oval, steepest at its foot as a bubble's, seated in a steel collar that slopes down from
 * it to the skin, the dark gasket at its inner edge; inside, the same glass is seen as a bowl at the back of an oval
 * recess lined round with the collar's steel, through a hole in the inner face with a steel lip round it. The dome,
 * the collar, the lining and the lip are smooth rings of faces round the oval, never a texture's pixels, so the
 * window's outline is clean near and far. The cover's weave round the oval, outside and in, and its edges are tinted
 * as the cover is, in the ship's livery (see {@code ZeppelinPaint}); the glass and the steel are not. Its JSON names
 * the loader, the piece and the textures: the glass over the whole window as seen from outside, the frame over the
 * piece (the weave, cut away under the collar), its inner face (cut away under the lip), the collar's steel, the lip's,
 * the recess's lining and the edges: {@code {"loader": "aerolarge:oval_window", "part": "frame", "column": 0, "row": 0,
 * "textures": {"glass": ..., "frame": ..., "inside": ..., "rim": ..., "lip": ..., "reveal": ..., "edge": ...,
 * "particle": ...}}}. Its part is {@code "glass"}, the dome alone, which needs only the glass's texture, or
 * {@code "frame"}, all the rest: a window block's model is NeoForge's composite of the two, the glass drawn translucent
 * and the rest cut out, so the clear hole in the inner face never hides the steel behind it, as a translucent face
 * drawn before what lies behind it would.
 *
 * <p>It is drawn looking north, then turned the way its block state says. Its inner face is lit as a block's side is
 * and culled against what stands against it; the window's outer edges are always drawn, so no crack opens where the
 * round skin beside it is cut deeper than the window. The rings each get the shade for the way they really face at
 * each corner, as the cabin archway's curve does (see {@link CabinArchModel}), so the glass and the steel brighten and
 * darken smoothly round the oval. A ring's face is drawn by the piece its middle lies in.
 */
public final class OvalWindowModel extends SimpleUnbakedGeometry<OvalWindowModel> {
    public static final ResourceLocation LOADER = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "oval_window");
    public static final IGeometryLoader<OvalWindowModel> READER = (json, context) -> read(json);
    /** How many pixels a block is across. */
    private static final float BLOCK = 16;
    /** How far in from its outer side the skin's cut runs through the block, in pixels: the piece lies behind it. */
    private static final float SKIN = BLOCK / 2;
    /** The whole window's width and height, in pixels. */
    private static final float WIDE = OvalWindowBlock.COLUMNS * BLOCK;
    private static final float TALL = OvalWindowBlock.ROWS * BLOCK;
    /** The oval's half width and half height, in pixels, as tools/generate_textures.mjs draws its glass. */
    private static final float OVAL_WIDE = 21;
    private static final float OVAL_TALL = 29;
    /**
     * The collar: how far out from the oval's edge it reaches, how far its inner edge, where the glass sits, stands out
     * of the skin, and how far its outer edge stands off the weave it lies on, in pixels.
     */
    private static final float RIM = 2.6F;
    private static final float COLLAR = 0.8F;
    private static final float COLLAR_FOOT = 0.1F;
    /** How far out from the oval's edge the recess's lining runs, and how wide the lip round the hole is, in pixels. */
    private static final float HOLE = 1.2F;
    private static final float LIP = 1.7F;
    /**
     * How far the dome bulges out of the collar at its middle, in pixels, and how its height falls toward its foot: as
     * this power of the share of the way still to go to its edge, steep at the foot for below 1, as a bubble's.
     */
    private static final float BULGE = 6;
    private static final float PROFILE = 0.6F;
    /** The least share of the way to the edge its slope is worked out at, so it stays finite at the very foot. */
    private static final float STEEPEST = 0.05F;
    /** How far out the dome's rings lie, as shares of the way from its middle to its edge: closer toward its foot. */
    private static final float[] RINGS = {0, 0.35F, 0.6F, 0.78F, 0.9F, 0.96F, 0.99F, 1};
    /** How many faces every ring has all the way round the window. */
    private static final int ROUND = 48;
    /** Where the lining's texture is darkest, from which on the collar's back and its gasket inside are drawn. */
    private static final float DARKEST = 0.9F;
    /** How many corners a quad has. */
    private static final int QUAD = 4;
    /** The tint index of the faces painted as the cover is. */
    private static final int COVER_TINT = 0;
    /** How far in front of the faces they lie on the frame over the skin and the lip inside stand, in pixels. */
    private static final float OVER = 0.02F;

    /** The piece drawn: its column from the left as seen from outside and its row from the top. */
    private final int column;
    private final int row;
    /** Whether this is the piece's glass, rather than all the rest of it. */
    private final boolean glassPart;
    /**
     * The tint of the wall round its oval inside, from {@code "inside_tint"}: the cover's, painted by her livery, or
     * none for a room's own wall.
     */
    private final int insideTint;

    /**
     * A point of the window: how far right of its middle and below it, as seen from outside, and how deep into the
     * block, in pixels.
     */
    private record Point(float right, float down, float depth) {
    }

    private OvalWindowModel(int column, int row, boolean glassPart, int insideTint) {
        this.column = column;
        this.row = row;
        this.glassPart = glassPart;
        this.insideTint = insideTint;
    }

    private static OvalWindowModel read(JsonObject json) {
        return new OvalWindowModel(GsonHelper.getAsInt(json, "column"), GsonHelper.getAsInt(json, "row"),
            "glass".equals(GsonHelper.getAsString(json, "part")), GsonHelper.getAsInt(json, "inside_tint", COVER_TINT));
    }

    @Override
    protected void addQuads(IGeometryBakingContext owner, IModelBuilder<?> modelBuilder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelTransform) {
        Transformation turn = modelTransform.getRotation().blockCenterToCorner();
        if (glassPart) {
            TextureAtlasSprite glass = spriteGetter.apply(owner.getMaterial("glass"));
            for (int step = 0; step < ROUND; step++) {
                for (int band = 0; band + 1 < RINGS.length; band++) {
                    domeFace(modelBuilder, glass, band, step, turn);
                }
            }
            return;
        }
        TextureAtlasSprite rim = spriteGetter.apply(owner.getMaterial("rim"));
        TextureAtlasSprite lip = spriteGetter.apply(owner.getMaterial("lip"));
        TextureAtlasSprite reveal = spriteGetter.apply(owner.getMaterial("reveal"));
        for (int step = 0; step < ROUND; step++) {
            collarFace(modelBuilder, rim, step, turn);
            recessFaces(modelBuilder, reveal, lip, step, turn);
        }
        flat(modelBuilder, spriteGetter.apply(owner.getMaterial("frame")), turn);
        side(modelBuilder, Direction.SOUTH, spriteGetter.apply(owner.getMaterial("inside")), insideTint, true, turn);
        TextureAtlasSprite edge = spriteGetter.apply(owner.getMaterial("edge"));
        for (Direction side : outerEdges()) {
            side(modelBuilder, side, edge, COVER_TINT, false, turn);
        }
    }

    /**
     * A face of the dome between two of its rings, seen from outside, and the same face seen from inside, as the bowl
     * at the back of the recess; its texture is the glass over the whole window as seen from outside.
     */
    private void domeFace(IModelBuilder<?> builder, TextureAtlasSprite glass, int band, int step, Transformation turn) {
        double from = angle(step);
        double to = angle(step + 1);
        Point[] points = {domePoint(RINGS[band], from), domePoint(RINGS[band + 1], from), domePoint(RINGS[band + 1], to),
            domePoint(RINGS[band], to)};
        if (!mine(points)) {
            return;
        }
        Vector3f[] out = {domeNormal(RINGS[band], from), domeNormal(RINGS[band + 1], from), domeNormal(RINGS[band + 1], to),
            domeNormal(RINGS[band], to)};
        float[][] uv = new float[QUAD][];
        Vector3f[] in = new Vector3f[QUAD];
        for (int k = 0; k < QUAD; k++) {
            uv[k] = new float[]{(WIDE / 2 + points[k].right()) / WIDE, (TALL / 2 + points[k].down()) / TALL};
            in[k] = new Vector3f(out[k]).negate();
        }
        face(builder, glass, points, out, uv, turn);
        face(builder, glass, points, in, uv, turn);
    }

    /** A point of the dome, so far out from its middle as a share of the way to its edge and so far round. */
    private static Point domePoint(float out, double angle) {
        float height = BULGE * (float) Math.pow(1 - out * out, PROFILE);
        return new Point(OVAL_WIDE * out * (float) Math.cos(angle), OVAL_TALL * out * (float) Math.sin(angle), SKIN - COLLAR - height);
    }

    /** The way the dome faces outward at a point of it, looking north. */
    private static Vector3f domeNormal(float out, double angle) {
        float within = Math.max(1 - out * out, STEEPEST);
        float slope = BULGE * PROFILE * (float) Math.pow(within, PROFILE - 1);
        float right = OVAL_WIDE * out * (float) Math.cos(angle);
        float down = OVAL_TALL * out * (float) Math.sin(angle);
        return new Vector3f(-2 * slope * right / square(OVAL_WIDE), -2 * slope * down / square(OVAL_TALL), -1).normalize();
    }

    /** A face of the collar, from the gasket at the glass's foot down to its outer edge on the weave. */
    private void collarFace(IModelBuilder<?> builder, TextureAtlasSprite rim, int step, Transformation turn) {
        double from = angle(step);
        double to = angle(step + 1);
        float foot = SKIN - OVER - COLLAR_FOOT;
        Point[] points = {round(0, from, SKIN - COLLAR), round(RIM, from, foot), round(RIM, to, foot), round(0, to, SKIN - COLLAR)};
        if (!mine(points)) {
            return;
        }
        float fall = (foot - (SKIN - COLLAR)) / RIM;
        Vector3f[] normals = new Vector3f[QUAD];
        for (int k = 0; k < QUAD; k++) {
            Vector3f out = outward(k == 0 || k == 1 ? from : to, k == 0 || k == 3 ? 0 : RIM);
            normals[k] = out.mul(fall).add(0, 0, -1).normalize();
        }
        face(builder, rim, points, normals, new float[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}}, turn);
    }

    /**
     * The recess's faces at one step round it, seen from inside: the gasket's back round the bowl's rim, the collar's
     * back between it and the lining, the lining from there to the inner face, and the lip round the hole there.
     */
    private void recessFaces(IModelBuilder<?> builder, TextureAtlasSprite reveal, TextureAtlasSprite lip, int step, Transformation turn) {
        double from = angle(step);
        double to = angle(step + 1);
        float[][] dark = {{0, DARKEST}, {0, 1}, {1, 1}, {1, DARKEST}};
        Point[] gasket = {round(0, from, SKIN - COLLAR), round(0, from, SKIN), round(0, to, SKIN), round(0, to, SKIN - COLLAR)};
        ringFace(builder, reveal, gasket, inward(from, 0), inward(to, 0), dark, turn);
        Point[] back = {round(0, from, SKIN), round(HOLE, from, SKIN), round(HOLE, to, SKIN), round(0, to, SKIN)};
        ringFace(builder, reveal, back, new Vector3f(0, 0, 1), new Vector3f(0, 0, 1), dark, turn);
        Point[] lining = {round(HOLE, from, BLOCK), round(HOLE, from, SKIN), round(HOLE, to, SKIN), round(HOLE, to, BLOCK)};
        ringFace(builder, reveal, lining, inward(from, HOLE), inward(to, HOLE), new float[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}}, turn);
        Point[] lipFace = {round(HOLE, from, BLOCK + OVER), round(HOLE + LIP, from, BLOCK + OVER), round(HOLE + LIP, to, BLOCK + OVER),
            round(HOLE, to, BLOCK + OVER)};
        ringFace(builder, lip, lipFace, new Vector3f(0, 0, 1), new Vector3f(0, 0, 1), new float[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}}, turn);
    }

    /** A face of a ring, if this piece draws it: its first two corners face as at its start, the rest as at its end. */
    private void ringFace(IModelBuilder<?> builder, TextureAtlasSprite sprite, Point[] points, Vector3f atFrom, Vector3f atTo, float[][] uv,
                          Transformation turn) {
        if (mine(points)) {
            face(builder, sprite, points, new Vector3f[]{atFrom, atFrom, atTo, atTo}, uv, turn);
        }
    }

    /** How far round the window a step of its rings is, in radians. */
    private static double angle(int step) {
        return 2 * Math.PI * step / ROUND;
    }

    /** A point round the oval grown by so many pixels each way, so far round, at a depth into the block. */
    private static Point round(float grown, double angle, float depth) {
        return new Point((OVAL_WIDE + grown) * (float) Math.cos(angle), (OVAL_TALL + grown) * (float) Math.sin(angle), depth);
    }

    /** The way out from the window's middle across its face at a point round the oval grown so far, looking north. */
    private static Vector3f outward(double angle, float grown) {
        return new Vector3f(-(float) Math.cos(angle) / (OVAL_WIDE + grown), -(float) Math.sin(angle) / (OVAL_TALL + grown), 0).normalize();
    }

    /** The way in toward the window's middle from a wall of the recess round the oval grown so far, so far round. */
    private static Vector3f inward(double angle, float grown) {
        return outward(angle, grown).negate();
    }

    /** Whether a face is this piece's to draw: its middle lies in this piece's block. */
    private boolean mine(Point[] points) {
        float right = 0;
        float down = 0;
        for (Point point : points) {
            right += point.right() / points.length;
            down += point.down() / points.length;
        }
        return (int) Math.floor((WIDE / 2 + right) / BLOCK) == column && (int) Math.floor((TALL / 2 + down) / BLOCK) == row;
    }

    /**
     * A face of the window from its corners, the way it faces at each and where on its texture each is, turned to face
     * the way its corners say and shaded at each corner for the way it faces there.
     */
    private void face(IModelBuilder<?> builder, TextureAtlasSprite sprite, Point[] points, Vector3f[] normals, float[][] uv, Transformation turn) {
        Vector3f[] at = new Vector3f[QUAD];
        Vector3f facing = new Vector3f();
        for (int k = 0; k < QUAD; k++) {
            at[k] = inPiece(points[k]);
            facing.add(normals[k]);
        }
        Vector3f made = new Vector3f(at[1]).sub(at[0]).cross(new Vector3f(at[2]).sub(at[0]));
        boolean reversed = made.dot(facing) < 0;
        Corner[] quad = new Corner[QUAD];
        for (int k = 0; k < QUAD; k++) {
            int corner = reversed ? QUAD - 1 - k : k;
            quad[k] = turned(at[corner], normals[corner], uv[corner][0], uv[corner][1], turn);
        }
        builder.addUnculledFace(ModelQuads.bake(quad, sprite, nearest(quad), true));
    }

    /** Where a point of the window lies in this piece's block looking north, as a share of the block. */
    private Vector3f inPiece(Point point) {
        float across = column * BLOCK + BLOCK - WIDE / 2 - point.right();
        float up = row * BLOCK + BLOCK - TALL / 2 - point.down();
        return new Vector3f(across, up, point.depth()).div(BLOCK);
    }

    /** The frame: the cover's weave over the skin round the oval, across the whole piece just in front of the skin. */
    private static void flat(IModelBuilder<?> builder, TextureAtlasSprite sprite, Transformation turn) {
        float z = (SKIN - OVER) / BLOCK;
        Vector3f[] corners = {new Vector3f(1, 1, z), new Vector3f(1, 0, z), new Vector3f(0, 0, z), new Vector3f(0, 1, z)};
        Corner[] quad = new Corner[QUAD];
        for (int k = 0; k < QUAD; k++) {
            quad[k] = turned(corners[k], Direction.NORTH.step(), 1 - corners[k].x(), 1 - corners[k].y(), turn);
        }
        builder.addUnculledFace(ModelQuads.bake(quad, sprite, nearest(quad), true, COVER_TINT));
    }

    /** The sides of this piece on the window's outer edge, looking north: none between two of its pieces. */
    private List<Direction> outerEdges() {
        List<Direction> edges = new ArrayList<>();
        if (column == 0) {
            edges.add(Direction.EAST);
        }
        if (column == OvalWindowBlock.COLUMNS - 1) {
            edges.add(Direction.WEST);
        }
        if (row == 0) {
            edges.add(Direction.UP);
        }
        if (row == OvalWindowBlock.ROWS - 1) {
            edges.add(Direction.DOWN);
        }
        return edges;
    }

    /**
     * One of the piece's flat sides, lit as a block's side is and tinted as given: its inner face, with the hole the
     * dome is seen through, mirrored so the oval reads whole from inside, culled against what stands against it; or an
     * edge of the window, always drawn, out from the inner face to the frame's own depth, so no hairline opens between
     * them.
     */
    private static void side(IModelBuilder<?> builder, Direction side, TextureAtlasSprite sprite, int tint, boolean culled, Transformation turn) {
        float[] bounds = new float[Direction.values().length];
        bounds[FaceInfo.Constants.MIN_X] = 0;
        bounds[FaceInfo.Constants.MIN_Y] = 0;
        bounds[FaceInfo.Constants.MIN_Z] = (culled ? SKIN : SKIN - OVER) / BLOCK;
        bounds[FaceInfo.Constants.MAX_X] = 1;
        bounds[FaceInfo.Constants.MAX_Y] = 1;
        bounds[FaceInfo.Constants.MAX_Z] = 1;
        FaceInfo info = FaceInfo.fromFacing(side);
        Corner[] quad = new Corner[QUAD];
        Vector3f facing = side.step();
        for (int k = 0; k < quad.length; k++) {
            FaceInfo.VertexInfo vertex = info.getVertexInfo(k);
            Vector3f at = new Vector3f(bounds[vertex.xFace], bounds[vertex.yFace], bounds[vertex.zFace]);
            float[] uv = uv(at, side);
            quad[k] = turned(at, facing, uv[0], uv[1], turn);
        }
        Direction turnedSide = Direction.rotate(turn.getMatrix(), side);
        if (culled) {
            builder.addCulledFace(turnedSide, ModelQuads.bake(quad, sprite, turnedSide, false, tint));
        } else {
            builder.addUnculledFace(ModelQuads.bake(quad, sprite, turnedSide, false, tint));
        }
    }

    /** The texture over a point of a flat side: the inner face as the glass seen from outside, the edges as a box's. */
    private static float[] uv(Vector3f p, Direction side) {
        return switch (side) {
            case UP -> new float[]{p.x(), p.z()};
            case DOWN -> new float[]{p.x(), 1 - p.z()};
            case WEST -> new float[]{p.z(), 1 - p.y()};
            case EAST -> new float[]{1 - p.z(), 1 - p.y()};
            case NORTH, SOUTH -> new float[]{1 - p.x(), 1 - p.y()};
        };
    }

    /** A corner placed in the block looking north, then turned the way the piece looks. */
    private static Corner turned(Vector3f at, Vector3f facing, float u, float v, Transformation turn) {
        Vector4f placed = new Vector4f(at, 1);
        turn.transformPosition(placed);
        Vector3f normal = new Vector3f(facing);
        turn.transformNormal(normal);
        return new Corner(new Vector3f(placed.x(), placed.y(), placed.z()), normal, u, v);
    }

    /** The side of the block a face mostly looks toward, from the ways it looks at its corners. */
    private static Direction nearest(Corner[] quad) {
        Vector3f sum = new Vector3f();
        for (Corner corner : quad) {
            sum.add(corner.normal());
        }
        return Direction.getNearest(sum.x(), sum.y(), sum.z());
    }

    private static float square(float value) {
        return value * value;
    }
}
