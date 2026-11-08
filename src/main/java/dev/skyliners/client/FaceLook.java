package dev.skyliners.client;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * How one face of a block looks, for drawing that face somewhere its own block is not, as a round floor window draws
 * the floor, wall or ceiling it was set into over itself: the face's texture in the block atlas, the tint it takes
 * there, as grass does, and how the texture lies across the face, read from the face of its model as it is now, so it
 * lies just as on the block itself, turned or flipped as its model has it, and follows the textures through a reload of
 * them. Where the texture falls is given for any point of the face, in the block's own frame, from its corner.
 */
record FaceLook(TextureAtlasSprite sprite, int tintIndex, Direction face, float[] uAcross, float[] vAcross) {
    /** The random a model is asked for its face with: the same every time, so its look is too. */
    private static final RandomSource SAME = RandomSource.create();
    /** The smallest a face's corners may span and still say how its texture lies, in blocks squared. */
    private static final float FLAT = 1.0E-6F;

    /** The look of a block's face the way given. */
    static FaceLook of(BlockState block, Direction face) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(block);
        SAME.setSeed(0);
        List<BakedQuad> quads = model.getQuads(block, face, SAME, ModelData.EMPTY, null);
        if (quads.isEmpty()) {
            return plain(model.getParticleIcon(ModelData.EMPTY), -1, face);
        }
        BakedQuad quad = quads.get(0);
        int tint = quad.isTinted() ? quad.getTintIndex() : -1;
        int[] data = quad.getVertices();
        float[] u = across(data, face, IQuadTransformer.UV0);
        float[] v = across(data, face, IQuadTransformer.UV0 + 1);
        return u == null || v == null ? plain(quad.getSprite(), tint, face) : new FaceLook(quad.getSprite(), tint, face, u, v);
    }

    /** A face whose model says nothing of how its texture lies: laid square across it, as a plain cube's top is. */
    private static FaceLook plain(TextureAtlasSprite sprite, int tint, Direction face) {
        return new FaceLook(sprite, tint, face, new float[] {sprite.getU0(), sprite.getU1() - sprite.getU0(), 0},
            new float[] {sprite.getV0(), 0, sprite.getV1() - sprite.getV0()});
    }

    /**
     * How a value at a quad's corners runs across its face, from three of them: where it stands at the face's corner
     * and how much it grows along each of the face's two ways across (see {@link #first} and {@link #second}); null
     * where the quad has no breadth.
     */
    @Nullable
    private static float[] across(int[] data, Direction face, int offset) {
        float[] a = new float[3];
        float[] b = new float[3];
        float[] value = new float[3];
        for (int corner = 0; corner < 3; corner++) {
            int at = corner * IQuadTransformer.STRIDE;
            float x = Float.intBitsToFloat(data[at + IQuadTransformer.POSITION]);
            float y = Float.intBitsToFloat(data[at + IQuadTransformer.POSITION + 1]);
            float z = Float.intBitsToFloat(data[at + IQuadTransformer.POSITION + 2]);
            a[corner] = first(face, x, y, z);
            b[corner] = second(face, x, y, z);
            value[corner] = Float.intBitsToFloat(data[at + offset]);
        }
        float a1 = a[1] - a[0];
        float b1 = b[1] - b[0];
        float a2 = a[2] - a[0];
        float b2 = b[2] - b[0];
        float span = a1 * b2 - a2 * b1;
        if (Math.abs(span) < FLAT) {
            return null;
        }
        float alongA = ((value[1] - value[0]) * b2 - (value[2] - value[0]) * b1) / span;
        float alongB = (a1 * (value[2] - value[0]) - a2 * (value[1] - value[0])) / span;
        return new float[] {value[0] - alongA * a[0] - alongB * b[0], alongA, alongB};
    }

    /** The first way across a face, of a point of it: x across a top, bottom, north or south face, z across the rest. */
    private static float first(Direction face, float x, float y, float z) {
        return face.getAxis() == Direction.Axis.X ? z : x;
    }

    /** The second way across a face, of a point of it: z across a top or bottom face, y across the rest. */
    private static float second(Direction face, float x, float y, float z) {
        return face.getAxis() == Direction.Axis.Y ? z : y;
    }

    /** Where across the texture, in the block atlas, a point of the face falls, from the block's corner. */
    float u(float x, float y, float z) {
        return uAcross[0] + uAcross[1] * first(face, x, y, z) + uAcross[2] * second(face, x, y, z);
    }

    /** Where down the texture, in the block atlas, a point of the face falls, from the block's corner. */
    float v(float x, float y, float z) {
        return vAcross[0] + vAcross[1] * first(face, x, y, z) + vAcross[2] * second(face, x, y, z);
    }

    /** The colour the face is drawn in at a place: white, or its tint there; white where there is no level. */
    int colour(BlockState block, @Nullable Level level, BlockPos at) {
        if (tintIndex < 0 || level == null) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | Minecraft.getInstance().getBlockColors().getColor(block, level, at, tintIndex);
    }
}
