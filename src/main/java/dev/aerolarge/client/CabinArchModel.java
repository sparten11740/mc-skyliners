package dev.aerolarge.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.CabinArchBlock;
import dev.aerolarge.client.ArchwayFaces.Face;
import dev.aerolarge.client.ArchwayFaces.Ring;
import dev.aerolarge.client.ArchwayFaces.Stretch;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.FaceInfo;
import net.minecraft.client.renderer.block.model.BakedQuad;
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
import net.neoforged.neoforge.client.model.lighting.QuadLighter;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The model of a piece of a cabin archway's arch ({@link CabinArchBlock}), and of the whole archway in the hand: the
 * faces {@link ArchwayFaces} works out, the arch round and smooth as the cockpit door's, baked once as the models load,
 * so the airliner's many archways cost no more to draw than any other block. Its JSON names the loader, the piece (or,
 * for the archway in the hand, how many blocks either side of its middle it reaches) and the textures of the moulding's
 * bead and groove, the white panelling and the bulkhead's cream: {@code {"loader": "aerolarge:cabin_arch", "part":
 * "narrow_1_1", "textures": {"trim": ..., "groove": ..., "arch": ..., "wall": ..., "particle": ...}}}. The jambs, being
 * straight, are drawn as boxes in their own model (see tools/generate_resources.mjs).
 *
 * <p>The faces along the wall are drawn and lit as a block's sides are, each texture laid over them as over a whole
 * side of the block, and those on the wall's faces are culled against whatever stands against them. The faces round the
 * curve have no side to take their shade from: each corner gets the shade for the way it really faces, so they
 * brighten and darken smoothly round the arch, without corner darkening, as the hull's cut faces do (see
 * {@link ShellModel}). In the hand each face is one flat colour of its texture, lit by the way it faces.
 */
public final class CabinArchModel extends SimpleUnbakedGeometry<CabinArchModel> {
    public static final ResourceLocation LOADER = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "cabin_arch");
    public static final IGeometryLoader<CabinArchModel> READER = (json, context) -> read(json);
    /** How many pixels a block is across. */
    private static final float BLOCK = 16;
    /**
     * Where on its texture each face of the archway in the hand takes its one flat colour from, across and down it, as
     * a fraction of the texture: clear of the partition's seams.
     */
    private static final float SWATCH = 4.5F / 16;
    /** How many corners a quad has. */
    private static final int QUAD = 4;

    /** The piece drawn; null for the whole archway in the hand. */
    @Nullable
    private final CabinArchBlock.Part part;
    /** How many blocks either side of its middle the archway's opening reaches. */
    private final int span;

    private CabinArchModel(@Nullable CabinArchBlock.Part part, int span) {
        this.part = part;
        this.span = span;
    }

    /**
     * The model its JSON describes: a piece of an archway's arch, by the name its block state gives it, or a whole
     * archway, by how far either side of its middle its opening reaches.
     */
    private static CabinArchModel read(JsonObject json) {
        if (!json.has("part")) {
            return new CabinArchModel(null, GsonHelper.getAsInt(json, "span"));
        }
        String name = GsonHelper.getAsString(json, "part");
        CabinArchBlock.Part part = CabinArchBlock.PART.getValue(name).filter(found -> found != CabinArchBlock.Part.JAMB)
            .orElseThrow(() -> new JsonParseException("No piece of a cabin arch's arch is called " + name));
        return new CabinArchModel(part, part.span());
    }

    @Override
    protected void addQuads(IGeometryBakingContext owner, IModelBuilder<?> modelBuilder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelTransform) {
        Map<Ring, TextureAtlasSprite> sprites = new EnumMap<>(Ring.class);
        for (Ring ring : Ring.values()) {
            sprites.put(ring, spriteGetter.apply(owner.getMaterial(ring.texture)));
        }
        if (part == null) {
            whole(modelBuilder, sprites);
        } else {
            piece(modelBuilder, sprites, modelTransform.getRotation().blockCenterToCorner());
        }
    }

    /**
     * The piece, drawn facing north (out from the archway's middle to the north, the wall's faces east and west, as its
     * block state's models are), then turned the way its block state says.
     */
    private void piece(IModelBuilder<?> builder, Map<Ring, TextureAtlasSprite> sprites, Transformation turn) {
        Stretch stretch = new Stretch(BLOCK * part.out() - BLOCK / 2, BLOCK * (part.over() - 1), BLOCK, BLOCK);
        for (Face face : ArchwayFaces.of(span, stretch, false)) {
            Direction facing = facing(face);
            List<Corner> corners = new ArrayList<>();
            for (int i = 0; i < face.corners().size(); i++) {
                corners.add(corner(face.corners().get(i), face.normals().get(i), facing, turn));
            }
            boolean onWallFace = face.alongWall() && face.ring().inset == 0;
            add(builder, corners, sprites.get(face.ring()), !face.alongWall(), onWallFace);
        }
    }

    /**
     * A corner of a face of a piece, from where it is in the wall: placed in the block drawn facing north (across the
     * wall running north, up it up, through it east), the texture laid over it as over the whole side of the block the
     * face mostly looks toward, and then turned the way the piece faces.
     */
    private static Corner corner(double[] p, double[] n, Direction facing, Transformation turn) {
        Vector3f at = new Vector3f((float) p[2], (float) p[1], BLOCK - (float) p[0]).div(BLOCK);
        float[] uv = uv(at, facing);
        Vector4f turned = new Vector4f(at, 1);
        turn.transformPosition(turned);
        Vector3f normal = inBlock(n);
        turn.transformNormal(normal);
        return new Corner(new Vector3f(turned.x(), turned.y(), turned.z()), normal, uv[0], uv[1]);
    }

    /** A way across, up and through the wall as it is in the block drawn facing north. */
    private static Vector3f inBlock(double[] n) {
        return new Vector3f((float) n[2], (float) n[1], (float) -n[0]);
    }

    /**
     * The whole archway in the hand, as one goes up put down by hand, seen from the front: the wall shrunk to fit the
     * block, standing across its middle, its edges drawn too, and each face one flat colour of its texture.
     */
    private void whole(IModelBuilder<?> builder, Map<Ring, TextureAtlasSprite> sprites) {
        float width = BLOCK * (2 * span + 3);
        float height = BLOCK * (CabinArchBlock.JAMB_ROWS + CabinArchBlock.rows(span));
        float scale = 1 / Math.max(width, height);
        float lift = (1 - height * scale) / 2;
        Stretch stretch = new Stretch(-width / 2, -BLOCK * CabinArchBlock.JAMB_ROWS, width, height);
        for (Face face : ArchwayFaces.of(span, stretch, true)) {
            List<Corner> corners = new ArrayList<>();
            for (int i = 0; i < face.corners().size(); i++) {
                double[] p = face.corners().get(i);
                double[] n = face.normals().get(i);
                Vector3f at = new Vector3f((float) p[0] * scale, lift + (float) p[1] * scale, 0.5F + (float) (p[2] - ArchwayFaces.WALL / 2) * scale);
                corners.add(new Corner(at, new Vector3f((float) n[0], (float) n[1], (float) n[2]), SWATCH, SWATCH));
            }
            add(builder, corners, sprites.get(face.ring()), false, false);
        }
    }

    /**
     * Adds a face's quads, fanned out from its first corner, a triangle being a quad with its last corner doubled.
     * Along the wall it is lit as a block's side is, culled against what stands on the side it faces if it lies on the
     * block's side; round the curve each corner is shaded for the way it faces.
     */
    private static void add(IModelBuilder<?> builder, List<Corner> corners, TextureAtlasSprite sprite, boolean round, boolean culled) {
        Direction side = facing(corners);
        for (int i = 1; i + 1 < corners.size(); i += 2) {
            Corner[] quad = {corners.getFirst(), corners.get(i), corners.get(i + 1), corners.get(Math.min(i + 2, corners.size() - 1))};
            BakedQuad baked = bake(round ? quad : startingAsSides(quad, side), sprite, side, round);
            if (culled) {
                builder.addCulledFace(side, baked);
            } else {
                builder.addUnculledFace(baked);
            }
        }
    }

    /**
     * A quad baked from its corners: lit as a block's side is, or, round the curve, with each corner's shade for the
     * way it faces baked into its colour and no other shading or corner darkening.
     */
    private static BakedQuad bake(Corner[] quad, TextureAtlasSprite sprite, Direction side, boolean round) {
        QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
        builder.setSprite(sprite);
        builder.setDirection(side);
        builder.setShade(!round);
        builder.setHasAmbientOcclusion(!round);
        for (Corner corner : quad) {
            Vector3f n = corner.normal();
            int grey = round ? Math.round(255 * QuadLighter.calculateShade(n.x(), n.y(), n.z(), false)) : 255;
            builder.addVertex(corner.at().x(), corner.at().y(), corner.at().z()).setColor(grey, grey, grey, 255)
                .setUv(sprite.getU(corner.u()), sprite.getV(corner.v())).setUv2(0, 0).setNormal(n.x(), n.y(), n.z());
        }
        return builder.bakeQuad();
    }

    /**
     * A quad of a block's side with its corners turned round, keeping their order, to start where the world's lighting
     * takes a side's quad to start (see {@link FaceInfo}): so the light and corner darkening it works out for each
     * corner of the quad's bounds lands on the quad's corner nearest that, as on a box's face.
     */
    private static Corner[] startingAsSides(Corner[] quad, Direction side) {
        float[] bounds = bounds(quad);
        FaceInfo info = FaceInfo.fromFacing(side);
        int best = 0;
        float bestMiss = Float.MAX_VALUE;
        for (int shift = 0; shift < QUAD; shift++) {
            float miss = 0;
            for (int k = 0; k < QUAD; k++) {
                FaceInfo.VertexInfo vertex = info.getVertexInfo(k);
                miss += quad[(k + shift) % QUAD].at().distanceSquared(bounds[vertex.xFace], bounds[vertex.yFace], bounds[vertex.zFace]);
            }
            if (miss < bestMiss) {
                bestMiss = miss;
                best = shift;
            }
        }
        Corner[] turned = new Corner[QUAD];
        for (int k = 0; k < QUAD; k++) {
            turned[k] = quad[(k + best) % QUAD];
        }
        return turned;
    }

    /** How far a quad reaches each way, by the index of the side it reaches toward, as {@link FaceInfo} reads them. */
    private static float[] bounds(Corner[] quad) {
        float[] bounds = new float[Direction.values().length];
        bounds[FaceInfo.Constants.MIN_X] = bounds[FaceInfo.Constants.MIN_Y] = bounds[FaceInfo.Constants.MIN_Z] = Float.MAX_VALUE;
        bounds[FaceInfo.Constants.MAX_X] = bounds[FaceInfo.Constants.MAX_Y] = bounds[FaceInfo.Constants.MAX_Z] = -Float.MAX_VALUE;
        for (Corner corner : quad) {
            Vector3f at = corner.at();
            bounds[FaceInfo.Constants.MIN_X] = Math.min(bounds[FaceInfo.Constants.MIN_X], at.x());
            bounds[FaceInfo.Constants.MIN_Y] = Math.min(bounds[FaceInfo.Constants.MIN_Y], at.y());
            bounds[FaceInfo.Constants.MIN_Z] = Math.min(bounds[FaceInfo.Constants.MIN_Z], at.z());
            bounds[FaceInfo.Constants.MAX_X] = Math.max(bounds[FaceInfo.Constants.MAX_X], at.x());
            bounds[FaceInfo.Constants.MAX_Y] = Math.max(bounds[FaceInfo.Constants.MAX_Y], at.y());
            bounds[FaceInfo.Constants.MAX_Z] = Math.max(bounds[FaceInfo.Constants.MAX_Z], at.z());
        }
        return bounds;
    }

    /** The texture laid over a point of a face as over the whole side of the block it mostly looks toward. */
    private static float[] uv(Vector3f p, Direction facing) {
        return switch (facing) {
            case UP -> new float[]{p.x(), p.z()};
            case DOWN -> new float[]{p.x(), 1 - p.z()};
            case NORTH -> new float[]{1 - p.x(), 1 - p.y()};
            case SOUTH -> new float[]{p.x(), 1 - p.y()};
            case WEST -> new float[]{p.z(), 1 - p.y()};
            case EAST -> new float[]{1 - p.z(), 1 - p.y()};
        };
    }

    /** The side of the block drawn facing north that a face of the wall mostly looks toward, before it is turned. */
    private static Direction facing(Face face) {
        Vector3f sum = new Vector3f();
        for (double[] normal : face.normals()) {
            sum.add(inBlock(normal));
        }
        return Direction.getNearest(sum.x(), sum.y(), sum.z());
    }

    /** The side of the block a face mostly looks toward, from the ways it looks at its corners. */
    private static Direction facing(List<Corner> corners) {
        Vector3f sum = new Vector3f();
        for (Corner corner : corners) {
            sum.add(corner.normal());
        }
        return Direction.getNearest(sum.x(), sum.y(), sum.z());
    }

    /**
     * A corner of a quad: where it is in the block, the way the face looks there, and where on the texture it takes its
     * colour from, across and down it as a fraction of the texture.
     */
    private record Corner(Vector3f at, Vector3f normal, float u, float v) {
    }
}
