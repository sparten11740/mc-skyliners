package dev.aerolarge.item;

import dev.aerolarge.entity.Trolley;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A folded trolley: use it on a floor to stand it there, stocked, facing the way you look. */
public class TrolleyItem extends Item {
    public TrolleyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!level.isClientSide) {
            Vec3 at = Vec3.atBottomCenterOf(context.getClickedPos().above());
            level.addFreshEntity(Trolley.at(level, at, context.getRotation()));
            context.getItemInHand().consume(1, context.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
