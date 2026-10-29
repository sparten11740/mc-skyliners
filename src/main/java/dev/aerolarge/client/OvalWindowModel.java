package dev.aerolarge.client;

import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.OvalWindowBlock;
import dev.aerolarge.client.ModelQuads.Corner;
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
 * The model of a piece of the rocket's oval window ({@link OvalWindowBlock}): the inner half of its block, its glass a
 * thin dome bulging out of the skin over the whole oval, each piece drawing its share of it, baked once as the models
 * load. Outside, the dome rises from the skin's cut, steepest at its foot as a bubble's; inside, the same glass is seen
 * as a bowl at the back of an oval recess, lined round with the rim's steel, through a hole in the inner face with a
 * steel lip. The cover's weave round the oval, outside and in, and its edges are tinted as the cover is, in the ship's
 * livery (see {@code ZeppelinPaint}); the glass, its rim and the steel are not. Its JSON names the loader, the piece
 * and the textures of its glass (its piece of the oval as seen from outside, its weave hidden under the frame), of the
 * frame over it, of the inner face with its hole, of the lip round the hole, of the recess's lining and of its edges:
 * {@code {"loader": "aerolarge:oval_window", "column": 0, "row": 0, "textures": {"glass": ..., "frame": ..., "inside":
 * ..., "lip": ..., "reveal": ..., "edge": ..., "particle": ...}}}.
 *
 * <p>It is drawn looking north, then turned the way its block state says. Its flat faces on the block's sides are lit
 * as a block's sides are and culled against what stands against them; the dome, the bowl and the lining each get the
 * shade for the way they really face at each corner, as the cabin archway's curve does (see {@link CabinArchModel}),
 * so the glass brightens and darkens smoothly round its bulge. Outside the oval the dome lies flat on the skin's cut.
 */
public final class OvalWindowModel extends SimpleUnbakedGeometry<OvalWindowModel> {
    public static final ResourceLocation LOADER = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "oval_window");
    public static final IGeometryLoader<OvalWindowModel> READER = (json, context) -> read(json);
    /** How many pixels a block is across. */
    private static final float BLOCK = 16;
    /** How far in from its outer side the skin's cut runs through the block, in pixels: the piece lies behind it. */
    private static final float SKIN = BLOCK / 2;
    /**
     * The oval's half width and half height over the whole window, in pixels of its pieces' textures, and how far
     * outside its edge the hole in the inner face and the recess's lining are, as tools/generate_textures.mjs draws
     * them.
     */
    private static final float OVAL_WIDE = 21;
    private static final float OVAL_TALL = 29;
    private static final float HOLE = 2.4F;
    /**
     * How far the dome bulges out of the skin at its middle, in pixels, and how its height falls toward its foot: as
     * this power of the share of the way still to go to its edge, steep at the foot for below 1, as a bubble's.
     */
    private static final float BULGE = 6;
    private static final float PROFILE = 0.6F;
    /** The least share of the way to the edge its slope is worked out at, so it stays finite at the very foot. */
    private static final float STEEPEST = 0.05F;
    /** How many pixels across and up each of the dome's faces is. */
    private static final int CELL = 4;
    /** How many faces the recess's lining has all the way round the window. */
    private static final int LINING = 96;
    /** The piece's sides other than its glass's, drawn in the edge's texture. */
    private static final List<Direction> EDGES = List.of(Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN);
    /** How many corners a quad has. */
    private static final int QUAD = 4;
    /** The tint index of the faces painted as the cover is. */
    private static final int COVER_TINT = 0;
    /** How far in front of the faces they lie on the frame over the glass and the lip inside stand, in pixels. */
    private static final float OVER = 0.02F;

    /** The piece drawn: its column from the left as seen from outside and its row from the top. */
    private final int column;
    private final int row;
    /**
     * The tint of the wall round its oval inside, from {@code "inside_tint"}: the cover's, painted by her livery, or
     * none for a room's own wall.
     */
    private final int insideTint;

    private OvalWindowModel(int column, int row, int insideTint) {
        this.column = column;
        this.row = row;
        this.insideTint = insideTint;
    }

    private static OvalWindowModel read(JsonObject json) {
        return new OvalWindowModel(GsonHelper.getAsInt(json, "column"), GsonHelper.getAsInt(json, "row"),
            GsonHelper.getAsInt(json, "inside_tint", COVER_TINT));
    }

    @Override
    protected void addQuads(IGeometryBakingContext owner, IModelBuilder<?> modelBuilder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelTransform) {
        TextureAtlasSprite glass = spriteGetter.apply(owner.getMaterial("glass"));
        Transformation turn = modelTransform.getRotation().blockCenterToCorner();
        for (int across = 0; across < BLOCK; across += CELL) {
            for (int up = 0; up < BLOCK; up += CELL) {
                domeFace(modelBuilder, glass, across, up, turn);
            }
        }
        for (int face = 0; face < LINING; face++) {
            liningFace(modelBuilder, spriteGetter.apply(owner.getMaterial("reveal")), face, turn);
        }
        flat(modelBuilder, spriteGetter.apply(owner.getMaterial("frame")), SKIN - OVER, Direction.NORTH, COVER_TINT, turn);
        flat(modelBuilder, spriteGetter.apply(owner.getMaterial("lip")), BLOCK + OVER, Direction.SOUTH, ModelQuads.NO_TINT, turn);
        side(modelBuilder, Direction.SOUTH, spriteGetter.apply(owner.getMaterial("inside")), insideTint, turn);
        TextureAtlasSprite edge = spriteGetter.apply(owner.getMaterial("edge"));
        for (Direction side : EDGES) {
            side(modelBuilder, side, edge, COVER_TINT, turn);
        }
    }

    /**
     * A flat face across the whole piece at a depth into the block looking north, in pixels, facing out or in: the
     * frame over the glass outside, or the lip over the inner face; tinted as given.
     */
    private static void flat(IModelBuilder<?> builder, TextureAtlasSprite sprite, float depth, Direction facing, int tint, Transformation turn) {
        float z = depth / BLOCK;
        Vector3f[] corners = facing == Direction.NORTH
            ? new Vector3f[]{new Vector3f(1, 1, z), new Vector3f(1, 0, z), new Vector3f(0, 0, z), new Vector3f(0, 1, z)}
            : new Vector3f[]{new Vector3f(0, 1, z), new Vector3f(0, 0, z), new Vector3f(1, 0, z), new Vector3f(1, 1, z)};
        Corner[] quad = new Corner[QUAD];
        for (int k = 0; k < QUAD; k++) {
            quad[k] = turned(corners[k], facing.step(), 1 - corners[k].x(), 1 - corners[k].y(), turn);
        }
        builder.addUnculledFace(ModelQuads.bake(quad, sprite, nearest(quad), true, tint));
    }

    /**
     * A face of the dome, from its corner nearest the block's west side and its bottom, in pixels, seen from outside;
     * and, where it is inside the hole in the inner face, the same face seen from inside, as the back of the recess.
     */
    private void domeFace(IModelBuilder<?> builder, TextureAtlasSprite glass, int across, int up, Transformation turn) {
        int right = across + CELL;
        int top = up + CELL;
        Corner[] outside = {dome(right, top, turn), dome(right, up, turn), dome(across, up, turn), dome(across, top, turn)};
        builder.addUnculledFace(ModelQuads.bake(outside, glass, nearest(outside), true));
        if (!reachesHole(across, up)) {
            return;
        }
        Corner[] inside = new Corner[QUAD];
        for (int k = 0; k < QUAD; k++) {
            Corner corner = outside[QUAD - 1 - k];
            inside[k] = new Corner(corner.at(), new Vector3f(corner.normal()).negate(), corner.u(), corner.v());
        }
        builder.addUnculledFace(ModelQuads.bake(inside, glass, nearest(inside), true));
    }

    /**
     * A corner of the dome at a point across and up the piece looking north, in pixels: as far out of the skin as the
     * dome bulges there, facing the way it does, its texture as seen from outside.
     */
    private Corner dome(float across, float up, Transformation turn) {
        float right = fromMiddleRight(across);
        float down = fromMiddleDown(up);
        float within = 1 - square(right / OVAL_WIDE) - square(down / OVAL_TALL);
        Vector3f facing = new Vector3f(0, 0, -1);
        if (within > 0) {
            float slope = BULGE * PROFILE * (float) Math.pow(Math.max(within, STEEPEST), PROFILE - 1);
            facing.set(-2 * slope * right / square(OVAL_WIDE), -2 * slope * down / square(OVAL_TALL), -1).normalize();
        }
        float out = within > 0 ? BULGE * (float) Math.pow(within, PROFILE) : 0;
        Vector3f at = new Vector3f(across, up, SKIN - out).div(BLOCK);
        return turned(at, facing, 1 - at.x(), 1 - at.y(), turn);
    }

    /**
     * Whether any of the dome's face from a corner across and up the piece looking north, in pixels, lies inside the
     * hole in the inner face: its point nearest the window's middle does. Where the hole's edge only cuts across a side
     * of the face, all its corners outside, the face is still seen from inside, or the recess would be open there to
     * the sky and the skin's sides.
     */
    private boolean reachesHole(int across, int up) {
        float right = nearestToMiddle(fromMiddleRight(across), fromMiddleRight(across + CELL));
        float down = nearestToMiddle(fromMiddleDown(up), fromMiddleDown(up + CELL));
        return square(right / (OVAL_WIDE + HOLE)) + square(down / (OVAL_TALL + HOLE)) < 1;
    }

    /** How far from the window's middle the nearest point between two distances either side of it or on one side is. */
    private static float nearestToMiddle(float from, float to) {
        return from * to <= 0 ? 0 : Math.min(Math.abs(from), Math.abs(to));
    }

    /** How far right of the window's middle, as seen from outside, a point across the piece is, in pixels. */
    private float fromMiddleRight(float across) {
        return column * BLOCK + BLOCK - across - OvalWindowBlock.COLUMNS * BLOCK / 2;
    }

    /** How far below the window's middle a point up the piece is, in pixels. */
    private float fromMiddleDown(float up) {
        return row * BLOCK + BLOCK - up - OvalWindowBlock.ROWS * BLOCK / 2;
    }

    /**
     * A face of the recess's lining round the hole, from the inner face to the skin's cut, facing in toward the
     * window's middle; drawn by the piece its middle is in, the steel lit as it faces.
     */
    private void liningFace(IModelBuilder<?> builder, TextureAtlasSprite reveal, int face, Transformation turn) {
        double from = 2 * Math.PI * face / LINING;
        double to = 2 * Math.PI * (face + 1) / LINING;
        double middle = (from + to) / 2;
        float wide = OVAL_WIDE + HOLE;
        float tall = OVAL_TALL + HOLE;
        float middleX = OvalWindowBlock.COLUMNS * BLOCK / 2 + wide * (float) Math.cos(middle);
        float middleY = OvalWindowBlock.ROWS * BLOCK / 2 + tall * (float) Math.sin(middle);
        if ((int) Math.floor(middleX / BLOCK) != column || (int) Math.floor(middleY / BLOCK) != row) {
            return;
        }
        Vector3f facing = new Vector3f((float) Math.cos(middle) / wide, (float) Math.sin(middle) / tall, 0).normalize();
        Vector3f first = lining(from, wide, tall);
        Vector3f second = lining(to, wide, tall);
        Vector3f[] corners = {
            new Vector3f(first.x(), first.y(), BLOCK), new Vector3f(second.x(), second.y(), BLOCK),
            new Vector3f(second.x(), second.y(), SKIN), new Vector3f(first.x(), first.y(), SKIN)};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        Vector3f made = new Vector3f(corners[1]).sub(corners[0]).cross(new Vector3f(corners[2]).sub(corners[0]));
        boolean reversed = made.dot(facing) < 0;
        Corner[] quad = new Corner[QUAD];
        for (int k = 0; k < QUAD; k++) {
            int corner = reversed ? QUAD - 1 - k : k;
            quad[k] = turned(corners[corner].div(BLOCK), facing, uv[corner][0], uv[corner][1], turn);
        }
        builder.addUnculledFace(ModelQuads.bake(quad, reveal, nearest(quad), true));
    }

    /** A point round the lining at an angle round the window's middle: across and up the piece looking north. */
    private Vector3f lining(double angle, float wide, float tall) {
        float right = wide * (float) Math.cos(angle);
        float down = tall * (float) Math.sin(angle);
        float across = column * BLOCK + BLOCK - OvalWindowBlock.COLUMNS * BLOCK / 2 - right;
        float up = row * BLOCK + BLOCK - OvalWindowBlock.ROWS * BLOCK / 2 - down;
        return new Vector3f(across, up, 0);
    }

    /**
     * One of the piece's flat sides, lit as a block's side is, culled against what stands against it and tinted as
     * given: its inner face, with the hole the dome is seen through, mirrored so the oval reads whole from inside, and
     * its edges round it.
     */
    private static void side(IModelBuilder<?> builder, Direction side, TextureAtlasSprite sprite, int tint, Transformation turn) {
        float[] bounds = new float[Direction.values().length];
        bounds[FaceInfo.Constants.MIN_X] = 0;
        bounds[FaceInfo.Constants.MIN_Y] = 0;
        bounds[FaceInfo.Constants.MIN_Z] = SKIN / BLOCK;
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
        builder.addCulledFace(turnedSide, ModelQuads.bake(quad, sprite, turnedSide, false, tint));
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
