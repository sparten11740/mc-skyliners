package dev.aerolarge.christening;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A Christening Bottle: wine in green glass under gold foil, a ribbon round its neck, named at an anvil with the
 * name the zeppelin is to be given. Swung at her nose (see {@link Christening}) it breaks there and christens her,
 * from close by or from the ground under her nose high overhead, for it reaches far; anywhere else on her it only
 * clinks, and whoever swung it is told where it should go.
 */
public class ChristeningBottleItem extends Item {
    /** How far it reaches when swung at a nose, in blocks: from the ground up to a zeppelin's nose and then some. */
    private static final double REACH = 64;

    public ChristeningBottleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return swing(context.getLevel(), context.getPlayer(), context.getItemInHand(), new BlockHitResult(context.getClickLocation(),
            context.getClickedFace(), context.getClickedPos(), context.isInside()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(REACH)), ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, player));
        ItemStack bottle = player.getItemInHand(hand);
        if (hit.getType() == HitResult.Type.MISS) {
            return InteractionResultHolder.pass(bottle);
        }
        return new InteractionResultHolder<>(swing(level, player, bottle, hit), bottle);
    }

    /** Swings it at a block: broken on a zeppelin's nose, she is christened and it is gone. */
    private static InteractionResult swing(Level level, @Nullable Player player, ItemStack bottle, BlockHitResult hit) {
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        Christening.Outcome outcome = Christening.christen(server, hit.getBlockPos(), hit.getLocation(), player.getEyePosition(), player, bottle);
        if (outcome == Christening.Outcome.NOT_HER_NOSE) {
            server.playSound(null, hit.getBlockPos(), SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1.0F, 1.4F);
            player.displayClientMessage(Component.translatable("message.aerolarge.christening.nose"), true);
            return InteractionResult.CONSUME;
        }
        if (outcome != Christening.Outcome.CHRISTENED) {
            return InteractionResult.PASS;
        }
        String name = Christening.name(bottle);
        player.displayClientMessage(name.isEmpty() ? Component.translatable("message.aerolarge.christening.unnamed")
            : Component.translatable("message.aerolarge.christening.named", name), true);
        bottle.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aerolarge.christening_bottle.tooltip"));
    }
}
