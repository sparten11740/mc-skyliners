package dev.skyliners.bowling;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A bowling ball in the hand, deep blue swirled with nebula purple, three holes for the fingers: use it to bowl it
 * along the floor the way you look, with a rumble as it rolls away (see {@link BowlingBall}), gently if you sneak.
 */
public class BowlingBallItem extends Item {
    /** How loud the rumble of a bowl is, and its pitch, lower for a gentle one. */
    private static final float ROLL_VOLUME = 0.9F;
    private static final float ROLL_PITCH = 1.0F;
    private static final float GENTLE_PITCH = 0.85F;

    public BowlingBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            level.addFreshEntity(BowlingBall.bowled(level, player));
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SkylinersRegistry.BOWLING_ROLL.get(), SoundSource.PLAYERS,
                ROLL_VOLUME, player.isShiftKeyDown() ? GENTLE_PITCH : ROLL_PITCH);
        }
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
