package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.Lining;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.ShellGeometry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * The model of a skin block cut to the hull's curve ({@link ShellBlock}), and of the whole panel: for every shape, the
 * faces of the part of the block below its cut, baked the first time the shape is drawn, with the "skin" texture laid
 * flat across each face from the side it mostly looks toward. Faces
 * covering a side of the block whole are culled against their neighbour like any block's, the faces cut across it
 * never are, and those covering a side only partly are culled only on a block that does not occlude (see
 * {@link Baked#getQuads}). Its JSON names the loader, the texture and the render type: {@code {"loader": "aerolarge:shell",
 * "textures": {"skin": ..., "particle": ...}, "render_type": ...}}.
 * <p>
 * Given the lining's textures too ("lining", "lining_ceiling", "lining_floor", "lining_dado", "lining_reveal",
 * "lining_reveal_pair", "lining_reveal_head", "lining_reveal_sill" and "lining_dado_sill"), it draws the faces the
 * block's state says are lined with them instead, each as {@link Lining#look} has it, the reveal down a window's left
 * side as its right side's texture mirrored. What a lined wall takes from what is round it (the dado, a window's
 * reveal) is read from the world as the block's section is built for drawing ({@link Baked#getModelData}).
 */
public final class ShellModel implements IUnbakedGeometry<ShellModel> {
    public static final ResourceLocation LOADER = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "shell");
    public static final IGeometryLoader<ShellModel> READER = (json, context) -> new ShellModel();
    /** What is round a block that its lined walls take their look from, as {@link Lining#context} gives it. */
    private static final ModelProperty<Integer> CONTEXT = new ModelProperty<>();
    private static final Lining.Look[] LOOKS = Lining.Look.values();

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides) {
        TextureAtlasSprite skin = spriteGetter.apply(context.getMaterial("skin"));
        TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
        boolean lining = context.hasMaterial("lining");
        TextureAtlasSprite[] looks = new TextureAtlasSprite[LOOKS.length];
        for (Lining.Look look : LOOKS) {
            looks[look.ordinal()] = look == Lining.Look.SKIN || !lining ? skin : spriteGetter.apply(context.getMaterial(texture(look)));
        }
        ResourceLocation hint = context.getRenderTypeHint();
        RenderTypeGroup group = hint != null ? context.getRenderType(hint) : RenderTypeGroup.EMPTY;
        RenderType renderType = group.isEmpty() ? RenderType.solid() : group.block();
        return new Baked(looks, lining, particle, renderType, context.getTransforms(), context.useAmbientOcclusion());
    }

    /** The texture a look is drawn with, by its name in the model's JSON. */
    private static String texture(Lining.Look look) {
        return switch (look) {
            case SKIN -> "skin";
            case SIDEWALL -> "lining";
            case CEILING -> "lining_ceiling";
            case FLOOR -> "lining_floor";
            case DADO -> "lining_dado";
            case REVEAL, REVEAL_LEFT -> "lining_reveal";
            case REVEAL_PAIR -> "lining_reveal_pair";
            case REVEAL_HEAD -> "lining_reveal_head";
            case REVEAL_SILL -> "lining_reveal_sill";
            case DADO_SILL -> "lining_dado_sill";
        };
    }

    private static final class Baked implements IDynamicBakedModel {
        /** The bits of the key a lined block's quads are kept by that hold its lined faces and what is round it; the shape is lowest. */
        private static final int LINED_SHIFT = 11;
        private static final int CONTEXT_SHIFT = 32;
        /**
         * For each shape, once first drawn: the quads culled against each side (by side index) and, last, the rest;
         * and the same with only the faces covering a side whole culled against it, the rest drawn always. Baked as
         * they are needed, since a block only ever takes some of the shapes; chunks are meshed on several threads at
         * once, and two of them baking the same shape bake the same quads.
         */
        private final AtomicReferenceArray<Shape> shapes = new AtomicReferenceArray<>(ShellGeometry.COUNT);
        /** The same for a shape with some faces lined, by the shape, its faces lined and what is round it. */
        private final Map<Long, Shape> lined = new ConcurrentHashMap<>();
        /** Each look's texture, by {@link Lining.Look#ordinal()}; all the skin without the lining's. */
        private final TextureAtlasSprite[] looks;
        private final boolean lining;
        private final TextureAtlasSprite particle;
        private final ChunkRenderTypeSet renderTypes;
        private final ItemTransforms transforms;
        private final boolean ambientOcclusion;

        private record Shape(List<List<BakedQuad>> quads, List<List<BakedQuad>> wholeCulled) {
        }

        Baked(TextureAtlasSprite[] looks, boolean lining, TextureAtlasSprite particle, RenderType renderType, ItemTransforms transforms,
              boolean ambientOcclusion) {
            this.looks = looks;
            this.lining = lining;
            this.particle = particle;
            this.renderTypes = ChunkRenderTypeSet.of(renderType);
            this.transforms = transforms;
            this.ambientOcclusion = ambientOcclusion;
        }

        private Shape shape(int shape) {
            Shape baked = shapes.get(shape);
            if (baked == null) {
                baked = bake(shape, 0, 0);
                shapes.set(shape, baked);
            }
            return baked;
        }

        private Shape bake(int shape, int linedFaces, int context) {
            int rest = Direction.values().length;
            List<List<BakedQuad>> bySide = new ArrayList<>();
            List<List<BakedQuad>> byWholeSide = new ArrayList<>();
            for (int k = 0; k <= rest; k++) {
                bySide.add(new ArrayList<>());
                byWholeSide.add(new ArrayList<>());
            }
            List<ShellGeometry.Face> faces = ShellGeometry.faces(shape);
            for (int i = 0; i < faces.size(); i++) {
                ShellGeometry.Face face = faces.get(i);
                Direction side = face.side();
                Lining.Look look = Lining.look(face, (linedFaces & 1 << i) != 0, context);
                List<BakedQuad> baked = bake(face, looks[look.ordinal()], look == Lining.Look.REVEAL_LEFT);
                bySide.get(side == null ? rest : side.get3DDataValue()).addAll(baked);
                byWholeSide.get(side == null || !ShellGeometry.whole(shape, side) ? rest : side.get3DDataValue()).addAll(baked);
            }
            return new Shape(bySide.stream().map(List::copyOf).toList(), byWholeSide.stream().map(List::copyOf).toList());
        }

        /**
         * A face as quads fanned out from its first corner; a triangle is a quad with its last corner doubled. Mirrored
         * left to right, the texture is laid on the other way round.
         */
        private static List<BakedQuad> bake(ShellGeometry.Face face, TextureAtlasSprite sprite, boolean mirrored) {
            List<float[]> corners = face.corners();
            float nx = face.nx();
            float ny = face.ny();
            float nz = face.nz();
            Direction facing = face.facing();
            List<BakedQuad> baked = new ArrayList<>();
            for (int i = 1; i + 1 < corners.size(); i += 2) {
                float[][] quad = {corners.getFirst(), corners.get(i), corners.get(i + 1), corners.get(Math.min(i + 2, corners.size() - 1))};
                QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
                builder.setSprite(sprite);
                builder.setDirection(facing);
                boolean flat = face.side() != null;
                // A face on a block side is shaded and darkened in corners like any block's. A face cut across the
                // block has no side to take its shade from: it gets the shade for the way it really faces, so the
                // facets brighten and darken smoothly around the curve, and no corner darkening.
                builder.setShade(flat);
                builder.setHasAmbientOcclusion(flat);
                int grey = flat ? 255 : Math.round(255 * diffuse(nx, ny, nz));
                for (float[] p : quad) {
                    float[] uv = uv(p, facing);
                    float u = mirrored ? 1 - uv[0] : uv[0];
                    builder.addVertex(p[0], p[1], p[2]).setColor(grey, grey, grey, 255).setUv(sprite.getU(u), sprite.getV(uv[1]))
                        .setUv2(0, 0).setNormal(nx, ny, nz);
                }
                baked.add(builder.bakeQuad());
            }
            return baked;
        }

        /**
         * The world's directional shading for a face looking along this unit normal, blended between the sides' own:
         * full from above, half from below, 0.8 north and south, 0.6 east and west.
         */
        private static float diffuse(float nx, float ny, float nz) {
            return nx * nx * 0.6F + nz * nz * 0.8F + ny * ny * (ny > 0 ? 1.0F : 0.5F);
        }

        /** The texture laid flat across a face from the side it mostly looks toward, as a block's own face would be. */
        private static float[] uv(float[] p, Direction facing) {
            return switch (facing) {
                case UP -> new float[]{p[0], p[2]};
                case DOWN -> new float[]{p[0], 1 - p[2]};
                case NORTH -> new float[]{1 - p[0], 1 - p[1]};
                case SOUTH -> new float[]{p[0], 1 - p[1]};
                case WEST -> new float[]{p[2], 1 - p[1]};
                case EAST -> new float[]{1 - p[2], 1 - p[1]};
            };
        }

        /** What is round a block with lined faces, for the look of its walls; nothing for any other. */
        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
            if (!lining) {
                return modelData;
            }
            int context = Lining.context(level, pos, state);
            return context == 0 ? modelData : modelData.derive().with(CONTEXT, context).build();
        }

        /**
         * The quads to draw against a side, or the rest. A block that occludes (the skin) does so with plates on its
         * whole sides only (see {@link ShellGeometry#occlusion}); its partly covered sides then have only the plates'
         * edges to be culled by, which a neighbour's matching edges would cover while the faces still show, so those
         * faces are drawn always. One that does not (the glass) culls them by its stepped shape like any block's.
         */
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
            int shape = state != null ? ShellBlock.shape(state) : ShellGeometry.WHOLE;
            int linedFaces = state != null && lining ? Lining.faces(state) : 0;
            Shape baked;
            if (linedFaces == 0) {
                baked = shape(shape);
            } else {
                Integer around = extraData.get(CONTEXT);
                int context = around != null ? around : 0;
                long key = shape | (long) linedFaces << LINED_SHIFT | (long) context << CONTEXT_SHIFT;
                baked = lined.computeIfAbsent(key, ignored -> bake(shape, linedFaces, context));
            }
            List<List<BakedQuad>> culled = state != null && state.canOcclude() ? baked.wholeCulled() : baked.quads();
            return culled.get(side == null ? Direction.values().length : side.get3DDataValue());
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            return renderTypes;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return ambientOcclusion;
        }

        @Override
        public boolean isGui3d() {
            return true;
        }

        @Override
        public boolean usesBlockLight() {
            return true;
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleIcon() {
            return particle;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }

        @Override
        public ItemTransforms getTransforms() {
            return transforms;
        }
    }
}
