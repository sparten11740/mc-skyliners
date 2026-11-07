package dev.skyliners.client;

import com.mojang.math.Transformation;
import dev.skyliners.block.OvalWindowBlock;
import dev.skyliners.block.OvalWindowPieceBlock;
import dev.skyliners.client.ModelQuads.Corner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * How an oval window set by hand ({@link OvalWindowBlock.Wall#MATCHED}, or one lying flat) takes on the look of the
 * wall, floor or roof it is set into, all three its wall here. Where the rocket's windows show her cover's weave
 * (outside round the collar, on the inner face round the lip, and on the window's outer edges), such a window shows the
 * faces of the wall beside it instead. They are cut round the collar and the lip just as the weave is, and laid on as
 * the wall lays them, so the wall seems to run on up to the steel.
 *
 * <p>Each piece looks like the nearest block of the wall beside the window, to the left and right of its rows, so a
 * wall of courses or stripes runs on through the window's corners as it does beside them, and a sill or lintel of
 * something else stays out of the frame. A block counts as wall if it is a full block drawn from a model and not
 * see-through in part, as stained glass is. A piece with no such block beside its window, as on a window standing on
 * its own, keeps the weave, as does one too far into the oval to show any wall. The wall is read from the world as the
 * piece's section is built for drawing ({@link Baked#getModelData}), and the faces worked out for each block are kept.
 */
public final class OvalWindowWall {
    /**
     * Where the tint indices of the faces taken from the wall begin: the wall's own index is added to this, so its
     * colour handler colours them (grass, leaves) while the window's own faces keep the livery.
     */
    static final int WALL_TINT = 1000;
    /** The block the piece looks like, from the world. */
    private static final ModelProperty<BlockState> WALL = new ModelProperty<>();
    /** The seed the wall's model picks its variant by: one look for every window, whatever its place. */
    private static final long SEED = 42;
    /** A stretch narrower than this, as a fraction of the block, is left out. */
    private static final float SLIVER = 1e-4F;
    /** How many corners a quad has. */
    private static final int QUAD = 4;

    private OvalWindowWall() {
    }

    /**
     * A window's tint: the wall's own colour on the faces taken from it, the livery's on the rest (see
     * {@link ZeppelinPaint}).
     */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        if (tintIndex < WALL_TINT) {
            return ZeppelinPaint.tint(state, level, pos, tintIndex);
        }
        BlockPos wall = level != null && pos != null ? wallBeside(level, pos, state) : null;
        return wall == null ? -1 : Minecraft.getInstance().getBlockColors().getColor(level.getBlockState(wall), level, wall, tintIndex - WALL_TINT);
    }

    /**
     * The block of the wall beside a piece's window that the piece looks like: the nearest one that is wall; null for
     * none, or for a piece not set into a wall by hand.
     */
    @Nullable
    static BlockPos wallBeside(BlockGetter level, BlockPos pos, BlockState piece) {
        if (!(piece.getBlock() instanceof OvalWindowPieceBlock window) || !window.setByHand(piece)) {
            return null;
        }
        for (BlockPos at : besideWindow(window, pos, piece)) {
            if (isWall(level, at)) {
                return at;
            }
        }
        return null;
    }

    /** The blocks to the left and right of the window's rows, in its wall, nearest the piece first. */
    private static List<BlockPos> besideWindow(OvalWindowPieceBlock window, BlockPos pos, BlockState piece) {
        Direction left = window.left(piece);
        List<BlockPos> beside = new ArrayList<>();
        OvalWindowPieceBlock.Size size = window.size();
        for (int row = 0; row < size.rows(); row++) {
            beside.add(window.pieceAt(pos, piece, 0, row).relative(left));
            beside.add(window.pieceAt(pos, piece, size.columns() - 1, row).relative(left.getOpposite()));
        }
        beside.sort(Comparator.comparingDouble(at -> at.distSqr(pos)));
        return beside;
    }

    /** Whether a block is wall a window can look like: a full block drawn from a model, nowhere see-through in part. */
    private static boolean isWall(BlockGetter level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        return !(state.getBlock() instanceof OvalWindowPieceBlock) && state.getRenderShape() == RenderShape.MODEL && state.isCollisionShapeFullBlock(level, at)
            && !model(state).getRenderTypes(state, RandomSource.create(SEED), ModelData.EMPTY).contains(RenderType.translucent());
    }

    private static BakedModel model(BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }

    /**
     * A stretch of one of a piece's faces that the wall shows on. It records the side the face looks toward, how far
     * along that side's axis the face lies in the block, and the stretch across it, as fractions of the block: a and
     * b run along the other two axes, z and y across a side looking east or west, x and z across one looking up or
     * down, x and y across one looking north or south. Each is lit as a block's side is, and hidden by a block
     * standing against it.
     */
    record Patch(Direction side, float depth, float fromA, float fromB, float toA, float toB) {
        /** The same stretch in the block once the piece is turned the way it looks. */
        Patch turned(Transformation turn) {
            Direction.Axis axis = side.getAxis();
            Vector3f from = turn(turn, point(axis, depth, fromA, fromB));
            Vector3f to = turn(turn, point(axis, depth, toA, toB));
            Direction turnedSide = Direction.rotate(turn.getMatrix(), side);
            Direction.Axis now = turnedSide.getAxis();
            return new Patch(turnedSide, (float) now.choose(from.x(), from.y(), from.z()), Math.min(a(now, from), a(now, to)),
                Math.min(b(now, from), b(now, to)), Math.max(a(now, from), a(now, to)), Math.max(b(now, from), b(now, to)));
        }

        /**
         * The part of a face of the wall's block, on the same side, that falls on this stretch, laid on this face as
         * the wall lays it on its own; null where none does.
         */
        @Nullable
        BakedQuad cut(BakedQuad quad) {
            Direction.Axis axis = side.getAxis();
            int[] data = quad.getVertices();
            float[] a = new float[QUAD];
            float[] b = new float[QUAD];
            float[] u = new float[QUAD];
            float[] v = new float[QUAD];
            for (int k = 0; k < QUAD; k++) {
                int at = k * IQuadTransformer.STRIDE;
                Vector3f corner = new Vector3f(Float.intBitsToFloat(data[at + IQuadTransformer.POSITION]),
                    Float.intBitsToFloat(data[at + IQuadTransformer.POSITION + 1]), Float.intBitsToFloat(data[at + IQuadTransformer.POSITION + 2]));
                a[k] = a(axis, corner);
                b[k] = b(axis, corner);
                u[k] = Float.intBitsToFloat(data[at + IQuadTransformer.UV0]);
                v[k] = Float.intBitsToFloat(data[at + IQuadTransformer.UV0 + 1]);
            }
            float lowA = Math.max(fromA, Math.min(Math.min(a[0], a[1]), Math.min(a[2], a[3])));
            float lowB = Math.max(fromB, Math.min(Math.min(b[0], b[1]), Math.min(b[2], b[3])));
            float highA = Math.min(toA, Math.max(Math.max(a[0], a[1]), Math.max(a[2], a[3])));
            float highB = Math.min(toB, Math.max(Math.max(b[0], b[1]), Math.max(b[2], b[3])));
            float da1 = a[1] - a[0];
            float db1 = b[1] - b[0];
            float da2 = a[2] - a[0];
            float db2 = b[2] - b[0];
            float across = da1 * db2 - da2 * db1;
            if (highA - lowA < SLIVER || highB - lowB < SLIVER || Math.abs(across) < SLIVER) {
                return null;
            }
            TextureAtlasSprite sprite = quad.getSprite();
            float[][] spots = {{lowA, lowB}, {highA, lowB}, {highA, highB}, {lowA, highB}};
            Corner[] corners = new Corner[QUAD];
            for (int k = 0; k < QUAD; k++) {
                float s = ((spots[k][0] - a[0]) * db2 - (spots[k][1] - b[0]) * da2) / across;
                float t = ((spots[k][1] - b[0]) * da1 - (spots[k][0] - a[0]) * db1) / across;
                float atU = u[0] + s * (u[1] - u[0]) + t * (u[2] - u[0]);
                float atV = v[0] + s * (v[1] - v[0]) + t * (v[2] - v[0]);
                corners[k] = new Corner(point(axis, depth, spots[k][0], spots[k][1]), side.step(),
                    (atU - sprite.getU0()) / (sprite.getU1() - sprite.getU0()), (atV - sprite.getV0()) / (sprite.getV1() - sprite.getV0()));
            }
            int tint = quad.isTinted() ? WALL_TINT + quad.getTintIndex() : ModelQuads.NO_TINT;
            return ModelQuads.bake(facingOut(corners), sprite, side, false, tint);
        }

        /** The corners in the order that faces them the way the side looks, so the face is drawn from that side. */
        private Corner[] facingOut(Corner[] corners) {
            Vector3f first = corners[0].at();
            Vector3f made = new Vector3f(corners[1].at()).sub(first).cross(new Vector3f(corners[2].at()).sub(first));
            if (made.dot(side.step()) >= 0) {
                return corners;
            }
            return new Corner[]{corners[3], corners[2], corners[1], corners[0]};
        }
    }

    /** A point in the block on a plane across an axis: so far along the axis, and at (a, b) across it. */
    private static Vector3f point(Direction.Axis axis, float depth, float a, float b) {
        return switch (axis) {
            case X -> new Vector3f(depth, b, a);
            case Y -> new Vector3f(a, depth, b);
            case Z -> new Vector3f(a, b, depth);
        };
    }

    /** How far across a plane square to an axis a point lies, along the first of the other two axes. */
    private static float a(Direction.Axis axis, Vector3f point) {
        return axis == Direction.Axis.X ? point.z() : point.x();
    }

    /** How far across a plane square to an axis a point lies, along the second of the other two axes. */
    private static float b(Direction.Axis axis, Vector3f point) {
        return axis == Direction.Axis.Y ? point.z() : point.y();
    }

    private static Vector3f turn(Transformation turn, Vector3f point) {
        Vector4f placed = new Vector4f(point, 1);
        turn.transformPosition(placed);
        return new Vector3f(placed.x(), placed.y(), placed.z());
    }

    /**
     * A piece of a window that can be set by hand, as baked: drawn as the rocket's are (its model as given), or, with a
     * wall found beside it, its steel alone with the wall's faces on its stretches.
     */
    static final class Baked extends BakedModelWrapper<BakedModel> {
        /** The piece without the faces the wall takes the place of. */
        private final BakedModel steel;
        /** Where on the piece the wall shows, in the block as the piece is turned. */
        private final List<Patch> patches;
        /** By the wall's block, its faces on the piece, by the side (by its index) each is drawn against. */
        private final Map<BlockState, List<List<BakedQuad>>> faces = new ConcurrentHashMap<>();

        Baked(BakedModel weave, BakedModel steel, List<Patch> patches) {
            super(weave);
            this.steel = steel;
            this.patches = patches;
        }

        /** The wall the piece looks like, read from the world; nothing for a piece too far into the oval to show it. */
        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
            BlockPos wall = patches.isEmpty() ? null : wallBeside(level, pos, state);
            return wall == null ? modelData : modelData.derive().with(WALL, level.getBlockState(wall)).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData,
                                        @Nullable RenderType renderType) {
            BlockState wall = extraData.get(WALL);
            if (wall == null) {
                return originalModel.getQuads(state, side, rand, extraData, renderType);
            }
            List<BakedQuad> steelQuads = steel.getQuads(state, side, rand, extraData, renderType);
            List<BakedQuad> wallQuads = side == null ? List.of() : faces.computeIfAbsent(wall, this::wallFaces).get(side.get3DDataValue());
            if (wallQuads.isEmpty()) {
                return steelQuads;
            }
            List<BakedQuad> all = new ArrayList<>(steelQuads);
            all.addAll(wallQuads);
            return all;
        }

        /** The wall's faces on the piece's stretches, by the side they are drawn against. */
        private List<List<BakedQuad>> wallFaces(BlockState wall) {
            BakedModel model = model(wall);
            List<List<BakedQuad>> wallSides = new ArrayList<>();
            List<List<BakedQuad>> bySide = new ArrayList<>();
            for (Direction side : Direction.values()) {
                wallSides.add(model.getQuads(wall, side, RandomSource.create(SEED), ModelData.EMPTY, null));
                bySide.add(new ArrayList<>());
            }
            for (Patch patch : patches) {
                List<BakedQuad> into = bySide.get(patch.side().get3DDataValue());
                wallSides.get(patch.side().get3DDataValue()).stream().map(patch::cut).filter(cut -> cut != null).forEach(into::add);
            }
            return bySide;
        }
    }
}
