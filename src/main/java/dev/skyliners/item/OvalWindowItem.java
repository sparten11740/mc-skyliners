package dev.skyliners.item;

import dev.skyliners.block.StandingWindowBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * A round or oval window as a player has it, the Oval Window or the Porthole, set into a wall, a floor or a roof (see
 * {@link StandingWindowBlock#getStateForPlacement}). Set by hand, its glass stands out of the wall and is solid (see
 * {@link dev.skyliners.block.OvalWindowPieceBlock}), so whoever sets it often stands where the dome will reach: it goes
 * in as long as nobody stands in its block itself, the dome pushing them aside.
 */
public class OvalWindowItem extends BlockItem {
    public OvalWindowItem(StandingWindowBlock window, Properties properties) {
        super(window, properties);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        BlockPos pos = context.getClickedPos();
        return (!mustSurvive() || state.canSurvive(context.getLevel(), pos))
            && context.getLevel().isUnobstructed(null, Shapes.block().move(pos.getX(), pos.getY(), pos.getZ()));
    }
}
