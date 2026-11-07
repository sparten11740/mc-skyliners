package dev.skyliners.client;

import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import dev.skyliners.Skyliners;
import dev.skyliners.block.OvalWindowBlock;
import dev.skyliners.block.OvalWindowPieceBlock;
import dev.skyliners.client.ModelQuads.Corner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The model of a piece of a round or oval window ({@link OvalWindowPieceBlock}), as the rocket's big oval window
 * ({@link OvalWindowBlock}): the inner half of its block, or the whole block for a window set by hand, baked once as
 * the models load, each piece drawing its share of the whole window, measured as its size has it (see {@link
 * OvalWindowPieceBlock.Size}). Outside, the glass is a thin dome bulging out of the skin over the whole oval, steepest
 * at its foot as a bubble's, seated in a steel collar that slopes down from it to the skin, the dark gasket at its
 * inner edge; inside, the same glass is seen as a bowl at the back of an oval recess lined round with the collar's
 * steel, through a hole in the inner face with a steel lip round it. The dome, the collar, the lining and the lip are
 * smooth rings of faces round the oval, never a texture's pixels, so the window's outline is clean near and far. The
 * cover's weave round the oval, outside and in, and its edges are tinted as the cover is, in the ship's livery (see
 * {@code ZeppelinPaint}); the glass and the steel are not. Its JSON names the loader, the window's size (the rocket's
 * if none), the piece, whether the window is set by hand and the textures: the glass over the whole window as seen from
 * outside, the frame over the piece (the weave, cut away under the collar), its inner face (cut away under the lip),
 * the collar's steel, the lip's, the recess's lining and the edges: {@code {"loader": "skyliners:oval_window",
 * "window": "rocket", "part": "frame", "column": 0, "row": 0, "set_by_hand": true, "textures": {"glass": ..., "frame":
 * ..., "inside": ..., "rim": ..., "lip": ..., "reveal": ..., "edge": ..., "particle": ...}}}. Its part is {@code
 * "glass"}, the dome alone, which needs only the glass's texture, or {@code "frame"}, all the rest: a window block's
 * model is NeoForge's composite of the two, the glass drawn translucent and the rest cut out, so the clear hole in the
 * inner face never hides the steel behind it, as a translucent face drawn before what lies behind it would.
 *
 * <p>It is drawn looking north, then turned the way its block state says. Its inner face is lit as a block's side is
 * and culled against what stands against it; the outer edges of hers are always drawn, so no crack opens where the
 * round skin beside it is cut deeper than the window. A window set by hand fills its blocks, its frame on their outer
 * face and its recess running back through them, so it lies flush with the wall on both sides, the glass and the collar
 * standing out of it as hers stand out of her skin: its frame and its edges are lit and culled as its inner face is.
 * The rings each get the shade for the way they really face at each corner, as the cabin archway's curve does (see
 * {@link CabinArchModel}), so the glass and the steel brighten and darken smoothly round the oval. A ring's face is
 * drawn by the piece its middle lies in.
 *
 * <p>With {@code "set_by_hand": true}, the frame of a window set by hand can draw the faces of the wall, floor or roof
 * beside the window in place of the weave, the inner face's wall and the edges, on the very pixels their textures leave
 * them (see {@link OvalWindowWall}).
 */
public final class OvalWindowModel extends SimpleUnbakedGeometry<OvalWindowModel> {
    public static final ResourceLocation LOADER = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "oval_window");
    public static final IGeometryLoader<OvalWindowModel> READER = (json, context) -> read(json);
    /** How many pixels a block is across. */
    private static final float BLOCK = 16;
    /**
     * How far in from its outer side the skin's cut runs through the block, in pixels: her pieces lie behind it, and
     * the frame of one set by hand on the block's outer side.
     */
    private static final float SKIN = BLOCK / 2;
    /** How far the collar's outer edge stands off the weave it lies on, in pixels. */
    private static final float COLLAR_FOOT = 0.1F;
    /** How the dome's height falls toward its foot, as the block measures it (see OvalWindowPieceBlock). */
    private static final float PROFILE = OvalWindowPieceBlock.PROFILE;
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
    /**
     * How far in from its sprite's sides a ring's face takes its texture across the ring, as a share of the texture:
     * half a texel of a block texture, so the edge between two faces, crossing the screen at a slant, never picks up
     * the sprite beside it in the atlas, as a dotted hairline along the window's diagonals.
     */
    private static final float INSET = 1 / 32F;
    /** Where on its texture each corner of a ring's face lies: across the whole texture, and from its darkest on. */
    private static final float[][] STRIP = inset(new float[][]{{0, 0}, {0, 1}, {1, 1}, {1, 0}});
    private static final float[][] DARK_STRIP = inset(new float[][]{{0, DARKEST}, {0, 1}, {1, 1}, {1, DARKEST}});

    /**
     * How big its window is and how its glass and steel are measured round its oval, from {@code "window"}: the
     * rocket's for none (see OvalWindowPieceBlock.Size).
     */
    private final OvalWindowPieceBlock.Size size;
    /** The whole window's width and height, in pixels. */
    private final float wide;
    private final float tall;
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
     * Whether it is a piece of a window set by hand, which fills its block and whose frame looks like the wall it is
     * set into (see OvalWindowWall).
     */
    private final boolean setByHand;

    /**
     * A point of the window: how far right of its middle and below it, as seen from outside, and how deep into the
     * block, in pixels.
     */
    private record Point(float right, float down, float depth) {
    }

    private OvalWindowModel(OvalWindowPieceBlock.Size size, int column, int row, boolean glassPart, int insideTint, boolean setByHand) {
        this.size = size;
        this.wide = size.columns() * BLOCK;
        this.tall = size.rows() * BLOCK;
        this.column = column;
        this.row = row;
        this.glassPart = glassPart;
        this.insideTint = insideTint;
        this.setByHand = setByHand;
    }

    private static OvalWindowModel read(JsonObject json) {
        OvalWindowPieceBlock.Size size = OvalWindowPieceBlock.Size.named(GsonHelper.getAsString(json, "window", OvalWindowPieceBlock.Size.ROCKET.name()));
        return new OvalWindowModel(size, GsonHelper.getAsInt(json, "column"), GsonHelper.getAsInt(json, "row"),
            "glass".equals(GsonHelper.getAsString(json, "part")), GsonHelper.getAsInt(json, "inside_tint", COVER_TINT),
            GsonHelper.getAsBoolean(json, "set_by_hand", false));
    }

    /**
     * The piece as the rocket's windows have it; for the frame of a window set into a wall by hand, that and its steel
     * alone, with where the wall shows on it, to look like the wall beside it once one is found.
     */
    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides) {
        BakedModel weave = super.bake(context, baker, spriteGetter, modelState, overrides);
        if (glassPart || !setByHand) {
            return weave;
        }
        Transformation turn = modelState.getRotation().blockCenterToCorner();
        ResourceLocation hint = context.getRenderTypeHint();
        IModelBuilder<?> steel = IModelBuilder.of(context.useAmbientOcclusion(), context.useBlockLight(), context.isGui3d(), context.getTransforms(),
            overrides, spriteGetter.apply(context.getMaterial("particle")), hint != null ? context.getRenderType(hint) : RenderTypeGroup.EMPTY);
        addSteel(context, steel, spriteGetter, turn);
        return new OvalWindowWall.Baked(weave, steel.build(), wallPatches(context, spriteGetter, turn));
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
        addSteel(owner, modelBuilder, spriteGetter, turn);
        TextureAtlasSprite frame = spriteGetter.apply(owner.getMaterial("frame"));
        if (setByHand) {
            side(modelBuilder, Direction.NORTH, frame, COVER_TINT, true, turn);
        } else {
            flat(modelBuilder, frame, turn);
        }
        side(modelBuilder, Direction.SOUTH, spriteGetter.apply(owner.getMaterial("inside")), insideTint, true, turn);
        TextureAtlasSprite edge = spriteGetter.apply(owner.getMaterial("edge"));
        for (Direction side : outerEdges()) {
            side(modelBuilder, side, edge, COVER_TINT, setByHand, turn);
        }
    }

    /** The frame's steel: its collar outside; inside, the recess's lining, the gasket's and collar's backs, the lip. */
    private void addSteel(IGeometryBakingContext owner, IModelBuilder<?> modelBuilder, Function<Material, TextureAtlasSprite> spriteGetter,
                          Transformation turn) {
        TextureAtlasSprite rim = spriteGetter.apply(owner.getMaterial("rim"));
        TextureAtlasSprite lip = spriteGetter.apply(owner.getMaterial("lip"));
        TextureAtlasSprite reveal = spriteGetter.apply(owner.getMaterial("reveal"));
        for (int step = 0; step < ROUND; step++) {
            collarFace(modelBuilder, rim, step, turn);
            recessFaces(modelBuilder, reveal, lip, step, turn);
        }
    }

    /**
     * Where a wall the window is set into shows on the piece, turned the way it looks: the frame on its outer face, on
     * the pixels the weave's texture leaves; the inner face, on the pixels the wall's texture there leaves, both hidden
     * by a block against them; and the window's outer edges, hidden by the wall beside them.
     */
    private List<OvalWindowWall.Patch> wallPatches(IGeometryBakingContext owner, Function<Material, TextureAtlasSprite> spriteGetter,
                                                   Transformation turn) {
        List<OvalWindowWall.Patch> patches = new ArrayList<>();
        SpriteContents frame = spriteGetter.apply(owner.getMaterial("frame")).contents();
        SpriteContents inside = spriteGetter.apply(owner.getMaterial("inside")).contents();
        for (int y = 0; y < frame.height(); y++) {
            shownRow(patches, frame, y, Direction.NORTH, 0);
        }
        for (int y = 0; y < inside.height(); y++) {
            shownRow(patches, inside, y, Direction.SOUTH, 1);
        }
        for (Direction side : outerEdges()) {
            float depth = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
            patches.add(new OvalWindowWall.Patch(side, depth, 0, 0, 1, 1));
        }
        return patches.stream().map(patch -> patch.turned(turn)).toList();
    }

    /**
     * The runs of pixels a flat face's texture leaves along one of its rows, each a stretch of that face looking north
     * or south, its texture laid as on the frame and the inner face: across from the block's east side, down from its
     * top. Each is lit as a block's side is and hidden by a block against it.
     */
    private static void shownRow(List<OvalWindowWall.Patch> into, SpriteContents texture, int y, Direction side, float depth) {
        int wide = texture.width();
        float top = 1 - (float) y / texture.height();
        float bottom = 1 - (float) (y + 1) / texture.height();
        int from = 0;
        for (int x = 0; x <= wide; x++) {
            if (x < wide && !texture.isTransparent(0, x, y)) {
                continue;
            }
            if (x > from) {
                into.add(new OvalWindowWall.Patch(side, depth, 1 - (float) x / wide, bottom, 1 - (float) from / wide, top));
            }
            from = x + 1;
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
            uv[k] = new float[]{(wide / 2 + points[k].right()) / wide, (tall / 2 + points[k].down()) / tall};
            in[k] = new Vector3f(out[k]).negate();
        }
        face(builder, glass, points, out, uv, turn);
        face(builder, glass, points, in, uv, turn);
    }

    /** A point of the dome, so far out from its middle as a share of the way to its edge and so far round. */
    private Point domePoint(float out, double angle) {
        float height = size.bulge() * (float) Math.pow(1 - out * out, PROFILE);
        return new Point(size.ovalWide() * out * (float) Math.cos(angle), size.ovalTall() * out * (float) Math.sin(angle), skin() - size.collar() - height);
    }

    /**
     * How far in from the block's outer side the frame lies, the glass's collar on it, in pixels: at the skin's cut in
     * hers, on the block's outer side in a window set by hand.
     */
    private float skin() {
        return setByHand ? 0 : SKIN;
    }

    /** The way the dome faces outward at a point of it, looking north. */
    private Vector3f domeNormal(float out, double angle) {
        float within = Math.max(1 - out * out, STEEPEST);
        float slope = size.bulge() * PROFILE * (float) Math.pow(within, PROFILE - 1);
        float right = size.ovalWide() * out * (float) Math.cos(angle);
        float down = size.ovalTall() * out * (float) Math.sin(angle);
        return new Vector3f(-2 * slope * right / square(size.ovalWide()), -2 * slope * down / square(size.ovalTall()), -1).normalize();
    }

    /** A face of the collar, from the gasket at the glass's foot down to its outer edge on the weave. */
    private void collarFace(IModelBuilder<?> builder, TextureAtlasSprite rim, int step, Transformation turn) {
        double from = angle(step);
        double to = angle(step + 1);
        float foot = skin() - OVER - COLLAR_FOOT;
        float reach = size.rim();
        float seat = skin() - size.collar();
        Point[] points = {round(0, from, seat), round(reach, from, foot), round(reach, to, foot), round(0, to, seat)};
        if (!mine(points)) {
            return;
        }
        float fall = (foot - seat) / reach;
        Vector3f[] normals = new Vector3f[QUAD];
        for (int k = 0; k < QUAD; k++) {
            Vector3f out = outward(k == 0 || k == 1 ? from : to, k == 0 || k == 3 ? 0 : reach);
            normals[k] = out.mul(fall).add(0, 0, -1).normalize();
        }
        face(builder, rim, points, normals, STRIP, turn);
    }

    /**
     * The recess's faces at one step round it, seen from inside: the gasket's back round the bowl's rim, the collar's
     * back between it and the lining, the lining from there to the inner face, and the lip round the hole there.
     */
    private void recessFaces(IModelBuilder<?> builder, TextureAtlasSprite reveal, TextureAtlasSprite lip, int step, Transformation turn) {
        double from = angle(step);
        double to = angle(step + 1);
        float skin = skin();
        float collar = size.collar();
        float hole = size.hole();
        float lipped = hole + size.lip();
        Point[] gasket = {round(0, from, skin - collar), round(0, from, skin), round(0, to, skin), round(0, to, skin - collar)};
        ringFace(builder, reveal, gasket, inward(from, 0), inward(to, 0), DARK_STRIP, turn);
        Point[] back = {round(0, from, skin), round(hole, from, skin), round(hole, to, skin), round(0, to, skin)};
        ringFace(builder, reveal, back, new Vector3f(0, 0, 1), new Vector3f(0, 0, 1), DARK_STRIP, turn);
        Point[] lining = {round(hole, from, BLOCK), round(hole, from, skin), round(hole, to, skin), round(hole, to, BLOCK)};
        ringFace(builder, reveal, lining, inward(from, hole), inward(to, hole), STRIP, turn);
        Point[] lipFace = {round(hole, from, BLOCK + OVER), round(lipped, from, BLOCK + OVER), round(lipped, to, BLOCK + OVER),
            round(hole, to, BLOCK + OVER)};
        ringFace(builder, lip, lipFace, new Vector3f(0, 0, 1), new Vector3f(0, 0, 1), STRIP, turn);
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
    private Point round(float grown, double angle, float depth) {
        return new Point((size.ovalWide() + grown) * (float) Math.cos(angle), (size.ovalTall() + grown) * (float) Math.sin(angle), depth);
    }

    /** The way out from the window's middle across its face at a point round the oval grown so far, looking north. */
    private Vector3f outward(double angle, float grown) {
        return new Vector3f(-(float) Math.cos(angle) / (size.ovalWide() + grown), -(float) Math.sin(angle) / (size.ovalTall() + grown), 0).normalize();
    }

    /** The way in toward the window's middle from a wall of the recess round the oval grown so far, so far round. */
    private Vector3f inward(double angle, float grown) {
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
        return (int) Math.floor((wide / 2 + right) / BLOCK) == column && (int) Math.floor((tall / 2 + down) / BLOCK) == row;
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
        float across = column * BLOCK + BLOCK - wide / 2 - point.right();
        float up = row * BLOCK + BLOCK - tall / 2 - point.down();
        return new Vector3f(across, up, point.depth()).div(BLOCK);
    }

    /** Her frame: the cover's weave over the skin round the oval, across the whole piece just in front of the skin. */
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
        if (column == size.columns() - 1) {
            edges.add(Direction.WEST);
        }
        if (row == 0) {
            edges.add(Direction.UP);
        }
        if (row == size.rows() - 1) {
            edges.add(Direction.DOWN);
        }
        return edges;
    }

    /**
     * One of the piece's flat sides, lit as a block's side is and tinted as given: its inner face, with the hole the
     * dome is seen through, mirrored so the oval reads whole from inside, culled against what stands against it; the
     * frame on the outer face of a window set by hand, culled so; or an edge of the window, out from the inner face to
     * the frame's own depth, so no hairline opens between them, always drawn on hers and culled on one set by hand.
     */
    private void side(IModelBuilder<?> builder, Direction side, TextureAtlasSprite sprite, int tint, boolean culled, Transformation turn) {
        float[] bounds = new float[Direction.values().length];
        bounds[FaceInfo.Constants.MIN_X] = 0;
        bounds[FaceInfo.Constants.MIN_Y] = 0;
        bounds[FaceInfo.Constants.MIN_Z] = setByHand ? 0 : (SKIN - OVER) / BLOCK;
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

    /** Texture corners moved in from the sprite's sides across the ring by {@link #INSET}. */
    private static float[][] inset(float[][] uv) {
        float[][] inset = new float[uv.length][];
        for (int k = 0; k < uv.length; k++) {
            inset[k] = new float[]{INSET + uv[k][0] * (1 - 2 * INSET), uv[k][1]};
        }
        return inset;
    }

    private static float square(float value) {
        return value * value;
    }
}
