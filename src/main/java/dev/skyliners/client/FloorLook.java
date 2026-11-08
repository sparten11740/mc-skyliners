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

/**
 * How a block's top looks, for drawing a floor somewhere its own block is not, as the freighter's ramp does over its
 * hatch (the round floor window, drawing any face, reads {@link FaceLook}): its top's texture in the block atlas, and
 * the tint it takes there, as grass does. Read from its model as it is now, so it follows the textures through a reload
 * of them: a lookup in the models' own table and their list of quads, cheap enough to do each frame.
 */
record FloorLook(TextureAtlasSprite sprite, int tintIndex) {
    /** The random a model is asked for its top with: the same every time, so its look is too. */
    private static final RandomSource SAME = RandomSource.create();

    /** The look of a block's top. */
    static FloorLook of(BlockState floor) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(floor);
        SAME.setSeed(0);
        List<BakedQuad> top = model.getQuads(floor, Direction.UP, SAME);
        if (top.isEmpty()) {
            return new FloorLook(model.getParticleIcon(), -1);
        }
        BakedQuad quad = top.get(0);
        return new FloorLook(quad.getSprite(), quad.isTinted() ? quad.getTintIndex() : -1);
    }

    /** The colour the top is drawn in at a place: white, or its tint there; white where there is no level. */
    int colour(BlockState floor, @Nullable Level level, BlockPos at) {
        if (tintIndex < 0 || level == null) {
            return 0xFFFFFFFF;
        }
        return 0xFF000000 | Minecraft.getInstance().getBlockColors().getColor(floor, level, at, tintIndex);
    }

    /** A place across the texture, from its left edge as a share of its width, in the block atlas. */
    float u(float across) {
        return sprite.getU0() + (sprite.getU1() - sprite.getU0()) * across;
    }

    /** A place down the texture, from its top edge as a share of its height, in the block atlas. */
    float v(float down) {
        return sprite.getV0() + (sprite.getV1() - sprite.getV0()) * down;
    }
}
