package dev.skyliners.item;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.entity.PaperPlane;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A paper plane in the hand: use it to throw it the way you look, sneaking for a gentler throw (see {@link PaperPlane}). */
public class PaperPlaneItem extends Item {
    public PaperPlaneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            level.addFreshEntity(PaperPlane.thrown(level, player));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SkylinersRegistry.PAPER_PLANE_WHOOSH.get(), SoundSource.PLAYERS, 0.6F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        }
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
