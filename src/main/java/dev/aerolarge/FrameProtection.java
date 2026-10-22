package dev.aerolarge;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.aerolarge.block.AutopilotBlockEntity;
import dev.aerolarge.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Keeps the airliner's frame and controls whole: the blocks in {@code #aerolarge:unbreakable} cannot be mined, broken
 * in creative mode or blown up while they are part of an airliner, so a stray click or a creeper never takes a wing
 * off. A player who sneaks holding a wrench (Create's, or any in {@code #c:tools/wrench}), or sneaks in creative mode,
 * can still take them apart: to clear an airliner away on purpose, or to take back a panel placed in the wrong spot.
 * The cabin floor and everything built inside are not in the tag.
 *
 * <p>A block is part of an airliner while it stands within the box round the blocks its blueprint laid, which the
 * airliner's autopilot keeps, or among an assembled airliner's blocks, in its plot; for an airliner built before its
 * autopilot kept that box, inside the Super Glue its blueprint glued it with (which can leave gaps: Create's glue
 * comes out narrower than laid along an airliner built facing east or west). The same blocks laid anywhere else, as the runway's lights
 * and the control tower's glass, lights, panels and seats are, break as any other block does, and so does one a player
 * placed by hand (see {@link HandPlaced}): only what the blueprint laid is held.
 */
public final class FrameProtection {
    public static final TagKey<Block> UNBREAKABLE = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "unbreakable"));
    private static final TagKey<Item> WRENCHES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "tools/wrench"));
    /** How far in from a block's faces the glue it stands in is looked for, so glue round its neighbour is not taken for its own. */
    private static final double GLUE_INSET = 0.25;

    private FrameProtection() {
    }

    private static boolean protects(LevelAccessor level, BlockPos pos, BlockState state, Player player) {
        boolean onPurpose = player.isShiftKeyDown() && (player.isCreative() || player.getMainHandItem().is(WRENCHES));
        return !onPurpose && protects(level, pos, state);
    }

    private static boolean protects(LevelAccessor level, BlockPos pos, BlockState state) {
        return state.is(UNBREAKABLE) && level instanceof Level world && !HandPlaced.at(world, pos, state) && partOfAirliner(world, pos);
    }

    /**
     * Whether a block stands within the frame of an airliner whose autopilot is loaded (see
     * {@link AutopilotBlockEntity#framing}), among an assembled airliner's blocks, or, for one built before the
     * autopilot kept its frame, inside its glue.
     */
    private static boolean partOfAirliner(Level level, BlockPos pos) {
        if (AutopilotBlockEntity.framing(level, pos) || SableFlight.aboard(level, Vec3.atCenterOf(pos))) {
            return true;
        }
        return !level.getEntitiesOfClass(SuperGlueEntity.class, new AABB(pos).deflate(GLUE_INSET)).isEmpty();
    }

    /** Mining makes no progress on it. */
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (event.getPosition().isPresent() && protects(player.level(), event.getPosition().get(), event.getState(), player)) {
            event.setCanceled(true);
        }
    }

    /** Breaking it outright, as creative mode does, is refused, with a word on how to do it on purpose. */
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (protects(event.getLevel(), event.getPos(), event.getState(), event.getPlayer())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable("message.aerolarge.unbreakable"), true);
        }
    }

    /** Explosions leave it standing. */
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> protects(level, pos, level.getBlockState(pos)));
    }
}
