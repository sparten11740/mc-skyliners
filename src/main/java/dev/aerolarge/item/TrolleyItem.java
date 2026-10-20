package dev.aerolarge.item;

import dev.aerolarge.entity.Trolley;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A folded trolley: use it on a floor to stand it there, stocked, facing the way you look. On an assembled airliner's
 * floor it stands among the airliner's blocks, where the floor is (see {@link Trolley}), turned into the airliner's
 * frame to face the way you look.
 */
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
            BlockPos floor = context.getClickedPos();
            Player player = context.getPlayer();
            float yaw = player == null ? context.getRotation() : Trolley.yaw(Trolley.across(level, floor, player.getLookAngle()));
            level.addFreshEntity(Trolley.at(level, Vec3.atBottomCenterOf(floor.above()), yaw));
            context.getItemInHand().consume(1, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
