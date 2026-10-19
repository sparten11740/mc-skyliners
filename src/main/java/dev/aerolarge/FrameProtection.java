package dev.aerolarge;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Keeps the airliner's frame and controls whole: the blocks in {@code #aerolarge:unbreakable} cannot be mined, broken
 * in creative mode or blown up, so a stray click or a creeper never takes a wing off. A player who sneaks holding a
 * wrench (Create's, or any in {@code #c:tools/wrench}), or sneaks in creative mode, can still take them apart: to clear
 * an airliner away on purpose, or to take back a panel placed in the wrong spot. The cabin floor and everything built
 * inside are not in the tag.
 */
public final class FrameProtection {
    public static final TagKey<Block> UNBREAKABLE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "unbreakable"));
    private static final TagKey<Item> WRENCHES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));

    private FrameProtection() {
    }

    private static boolean protects(BlockState state, Player player) {
        return state.is(UNBREAKABLE) && !(player.isShiftKeyDown() && (player.isCreative() || player.getMainHandItem().is(WRENCHES)));
    }

    /** Mining makes no progress on it. */
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (protects(event.getState(), event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Breaking it outright, as creative mode does, is refused, with a word on how to do it on purpose. */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (protects(event.getState(), event.getPlayer())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("message.aerolarge.unbreakable"), true);
        }
    }

    /** Explosions leave it standing. */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> event.getLevel().getBlockState(pos).is(UNBREAKABLE));
    }
}
