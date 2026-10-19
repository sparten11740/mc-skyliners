package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.ShellGeometry;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/**
 * The model of a skin block cut to the hull's curve ({@link ShellBlock}): for every shape, the faces of the part of
 * the block below its cut, all baked up front, with the "skin" texture laid flat across each face from the side it mostly looks toward. Faces
 * covering a side of the block whole are culled against their neighbour like any block's, the faces cut across it
 * never are, and those covering a side only partly are culled only on a block that does not occlude (see
 * {@link Baked#getQuads}). Its JSON names the loader, the texture and the render type: {@code {"loader": "aerolarge:shell",
 * "textures": {"skin": ..., "particle": ...}, "render_type": ...}}.
 */
public final class ShellModel implements IUnbakedGeometry<ShellModel> {
    public static final ResourceLocation LOADER = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "shell");
    public static final IGeometryLoader<ShellModel> READER = (json, context) -> new ShellModel();

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides) {
        TextureAtlasSprite skin = spriteGetter.apply(context.getMaterial("skin"));
        TextureAtlasSprite particle = spriteGetter.apply(context.getMaterial("particle"));
        ResourceLocation hint = context.getRenderTypeHint();
        RenderTypeGroup group = hint != null ? context.getRenderType(hint) : RenderTypeGroup.EMPTY;
        RenderType renderType = group.isEmpty() ? RenderType.solid() : group.block();
        return new Baked(skin, particle, renderType, context.getTransforms(), context.useAmbientOcclusion());
    }

    private static final class Baked implements IDynamicBakedModel {
        /** For each shape, the quads culled against each side (by side index) and, last, the rest. */
        private final List<List<List<BakedQuad>>> quads = new ArrayList<>(256);
        /** The same, with only the faces covering a side whole culled against it; the rest are drawn always. */
        private final List<List<List<BakedQuad>>> wholeCulled = new ArrayList<>(256);
        private final TextureAtlasSprite particle;
        private final ChunkRenderTypeSet renderTypes;
        private final ItemTransforms transforms;
        private final boolean ambientOcclusion;

        Baked(TextureAtlasSprite skin, TextureAtlasSprite particle, RenderType renderType, ItemTransforms transforms, boolean ambientOcclusion) {
            this.particle = particle;
            this.renderTypes = ChunkRenderTypeSet.of(renderType);
            this.transforms = transforms;
            this.ambientOcclusion = ambientOcclusion;
            int rest = Direction.values().length;
            for (int shape = 0; shape < ShellGeometry.COUNT; shape++) {
                List<List<BakedQuad>> bySide = new ArrayList<>();
                List<List<BakedQuad>> byWholeSide = new ArrayList<>();
                for (int k = 0; k <= rest; k++) {
                    bySide.add(new ArrayList<>());
                    byWholeSide.add(new ArrayList<>());
                }
                for (ShellGeometry.Face face : ShellGeometry.faces(shape)) {
                    Direction side = face.side();
                    List<BakedQuad> baked = bake(face, skin);
                    bySide.get(side == null ? rest : side.get3DDataValue()).addAll(baked);
                    byWholeSide.get(side == null || !ShellGeometry.whole(shape, side) ? rest : side.get3DDataValue()).addAll(baked);
                }
                quads.add(bySide.stream().map(List::copyOf).toList());
                wholeCulled.add(byWholeSide.stream().map(List::copyOf).toList());
            }
        }

        /** A face as quads fanned out from its first corner; a triangle is a quad with its last corner doubled. */
        private static List<BakedQuad> bake(ShellGeometry.Face face, TextureAtlasSprite sprite) {
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
                    builder.addVertex(p[0], p[1], p[2]).setColor(grey, grey, grey, 255).setUv(sprite.getU(uv[0]), sprite.getV(uv[1]))
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

        /**
         * The quads to draw against a side, or the rest. A block that occludes (the skin) does so with plates on its
         * whole sides only (see {@link ShellGeometry#occlusion}); its partly covered sides then have only the plates'
         * edges to be culled by, which a neighbour's matching edges would cover while the faces still show, so those
         * faces are drawn always. One that does not (the glass) culls them by its stepped shape like any block's.
         */
        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
            int shape = state != null && state.hasProperty(ShellBlock.SHAPE) ? state.getValue(ShellBlock.SHAPE) : ShellGeometry.WHOLE;
            List<List<BakedQuad>> culled = (state != null && state.canOcclude() ? wholeCulled : quads).get(shape);
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
