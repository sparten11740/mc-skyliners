package dev.aerolarge.item;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.WallLightBlock;
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
 * a halo light (see {@link dev.aerolarge.block.HaloLightBlock}), on a wall it is a wall washer throwing its fan of
 * light up the wall (see {@link WallLightBlock}), and on the ground a floor light shining up (see
 * {@link dev.aerolarge.block.FloorLightBlock}). Each of the three gives it back when broken, and picking one gives it.
 */
public class LightFittingItem extends BlockItem {
    public LightFittingItem(Properties properties) {
        super(AerolargeRegistry.HALO_LIGHT.get(), properties);
    }

    /** The shape the light takes for the face it is put against: the ceiling's, a wall's or the ground's. */
    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState state = switch (face) {
            case DOWN -> AerolargeRegistry.HALO_LIGHT.get().defaultBlockState();
            case UP -> AerolargeRegistry.FLOOR_LIGHT.get().defaultBlockState();
            default -> AerolargeRegistry.WALL_LIGHT.get().defaultBlockState().setValue(WallLightBlock.FACING, face);
        };
        return canPlace(context, state) ? state : null;
    }

    @Override
    public void registerBlocks(Map<Block, Item> blocks, Item item) {
        super.registerBlocks(blocks, item);
        blocks.put(AerolargeRegistry.WALL_LIGHT.get(), item);
        blocks.put(AerolargeRegistry.FLOOR_LIGHT.get(), item);
    }
}
