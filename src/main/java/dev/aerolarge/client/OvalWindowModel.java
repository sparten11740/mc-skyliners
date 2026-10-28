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
 * The model of a piece of the rocket's oval window ({@link OvalWindowBlock}): the inner half of its block, flat inside,
 * and outside its glass bulging out of the skin in a low dome over the whole oval, each piece drawing its share of it,
 * baked once as the models load. Its JSON names the loader, the piece and the textures of its glass (its piece of the
 * oval as seen from outside, mirrored inside so the oval reads whole from there too) and of its edges: {@code
 * {"loader": "aerolarge:oval_window", "column": 0, "row": 0, "textures": {"glass": ..., "edge": ..., "particle":
 * ...}}}.
 *
 * <p>It is drawn looking north, then turned the way its block state says. Its flat faces are lit as a block's sides are
 * and culled against what stands against them; the dome's faces each get the shade for the way they really face at
 * each corner, as the cabin archway's curve does (see {@link CabinArchModel}), so the glass brightens and darkens
 * smoothly round its bulge. Outside the oval the dome lies flat on the skin's cut.
 */
public final class OvalWindowModel extends SimpleUnbakedGeometry<OvalWindowModel> {
    public static final ResourceLocation LOADER = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "oval_window");
    public static final IGeometryLoader<OvalWindowModel> READER = (json, context) -> read(json);
    /** How many pixels a block is across. */
    private static final float BLOCK = 16;
    /** How far in from its outer side the skin's cut runs through the block, in pixels: the piece lies behind it. */
    private static final float SKIN = BLOCK / 2;
    /**
     * The oval's half width and half height over the whole window, in pixels of its pieces' textures, as
     * tools/generate_textures.mjs draws it.
     */
    private static final float OVAL_WIDE = 21;
    private static final float OVAL_TALL = 29;
    /** How far the dome bulges out of the skin at its middle, in pixels. */
    private static final float BULGE = 4;
    /** How many pixels across and up each of the dome's faces is. */
    private static final int CELL = 4;
    /** The piece's sides other than its glass's, drawn in the edge's texture. */
    private static final List<Direction> EDGES = List.of(Direction.EAST, Direction.WEST, Direction.UP, Direction.DOWN);
    /** How many corners a quad has. */
    private static final int QUAD = 4;

    /** The piece drawn: its column from the left as seen from outside and its row from the top. */
    private final int column;
    private final int row;

    private OvalWindowModel(int column, int row) {
        this.column = column;
        this.row = row;
    }

    private static OvalWindowModel read(JsonObject json) {
        return new OvalWindowModel(GsonHelper.getAsInt(json, "column"), GsonHelper.getAsInt(json, "row"));
    }

    @Override
    protected void addQuads(IGeometryBakingContext owner, IModelBuilder<?> modelBuilder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelTransform) {
        TextureAtlasSprite glass = spriteGetter.apply(owner.getMaterial("glass"));
        TextureAtlasSprite edge = spriteGetter.apply(owner.getMaterial("edge"));
        Transformation turn = modelTransform.getRotation().blockCenterToCorner();
        Direction out = Direction.rotate(turn.getMatrix(), Direction.NORTH);
        for (int across = 0; across < BLOCK; across += CELL) {
            for (int up = 0; up < BLOCK; up += CELL) {
                modelBuilder.addUnculledFace(ModelQuads.bake(domeFace(across, up, turn), glass, out, true));
            }
        }
        side(modelBuilder, Direction.SOUTH, glass, turn);
        for (Direction side : EDGES) {
            side(modelBuilder, side, edge, turn);
        }
    }

    /** A face of the dome, from its corner nearest the block's west side and its bottom, in pixels. */
    private Corner[] domeFace(int across, int up, Transformation turn) {
        int right = across + CELL;
        int top = up + CELL;
        return new Corner[]{dome(right, top, turn), dome(right, up, turn), dome(across, up, turn), dome(across, top, turn)};
    }

    /**
     * A corner of the dome at a point across and up the piece looking north, in pixels: as far out of the skin as the
     * dome bulges there, facing the way it does, its texture as seen from outside.
     */
    private Corner dome(float across, float up, Transformation turn) {
        float right = column * BLOCK + BLOCK - across - OvalWindowBlock.COLUMNS * BLOCK / 2;
        float down = row * BLOCK + BLOCK - up - OvalWindowBlock.ROWS * BLOCK / 2;
        float within = 1 - square(right / OVAL_WIDE) - square(down / OVAL_TALL);
        Vector3f facing = within > 0
            ? new Vector3f(-2 * BULGE * right / square(OVAL_WIDE), -2 * BULGE * down / square(OVAL_TALL), -1).normalize()
            : new Vector3f(0, 0, -1);
        Vector3f at = new Vector3f(across, up, SKIN - BULGE * Math.max(within, 0)).div(BLOCK);
        return turned(at, facing, 1 - at.x(), 1 - at.y(), turn);
    }

    /**
     * One of the piece's flat sides, lit as a block's side is and culled against what stands against it: its glass
     * inside, mirrored so the oval reads whole from there, and its edges round it.
     */
    private static void side(IModelBuilder<?> builder, Direction side, TextureAtlasSprite sprite, Transformation turn) {
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
        builder.addCulledFace(turnedSide, ModelQuads.bake(quad, sprite, turnedSide, false));
    }

    /** The texture over a point of a flat side: the glass inside as seen from outside, the edges as a box's sides. */
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

    private static float square(float value) {
        return value * value;
    }
}
