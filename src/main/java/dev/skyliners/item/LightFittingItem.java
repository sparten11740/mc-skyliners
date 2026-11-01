package dev.skyliners.item;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import dev.skyliners.block.Mounting;
import dev.skyliners.block.WallLightBlock;
import java.util.EnumSet;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Halo Light as a player has it: one light that takes its shape from where it is put. Under a ceiling it hangs as
 * a halo light (see {@link dev.skyliners.block.HaloLightBlock}), on a wall it is a wall washer throwing its fan of
 * light up the wall (see {@link WallLightBlock}), and on the ground a floor light shining up (see
 * {@link dev.skyliners.block.FloorLightBlock}). Each of the three gives it back when broken, and picking one gives it.
 */
public class LightFittingItem extends BlockItem {
    public LightFittingItem(Properties properties) {
        super(SkylinersRegistry.HALO_LIGHT.get(), properties);
    }

    /**
     * The shape the light takes for what it is put against (see {@link Mounting}): hanging from a ceiling, fixed to a
     * wall or standing on the ground, the nearest way the player looks that holds it and leaves it room, and hanging
     * wherever it goes under a light cable, run down for it; none where nothing holds it, as against grass in the open.
     */
    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        if (Mounting.underCable(context)) {
            BlockState halo = SkylinersRegistry.HALO_LIGHT.get().defaultBlockState();
            return canPlace(context, halo) ? halo : null;
        }
        for (Direction toward : Mounting.supports(context, EnumSet.allOf(Mount.class))) {
            BlockState state = shapeAgainst(toward);
            if (canPlace(context, state)) {
                return state;
            }
        }
        return null;
    }

    /** The light's shape against a block the way given from it: over it, under it or beside it. */
    private static BlockState shapeAgainst(Direction toward) {
        return switch (toward) {
            case UP -> SkylinersRegistry.HALO_LIGHT.get().defaultBlockState();
            case DOWN -> SkylinersRegistry.FLOOR_LIGHT.get().defaultBlockState();
            default -> SkylinersRegistry.WALL_LIGHT.get().defaultBlockState().setValue(WallLightBlock.FACING, toward.getOpposite());
        };
    }

    @Override
    public void registerBlocks(Map<Block, Item> blocks, Item item) {
        super.registerBlocks(blocks, item);
        blocks.put(SkylinersRegistry.WALL_LIGHT.get(), item);
        blocks.put(SkylinersRegistry.FLOOR_LIGHT.get(), item);
    }
}
