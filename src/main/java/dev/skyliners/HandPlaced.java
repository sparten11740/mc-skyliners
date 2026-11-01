package dev.skyliners;

import dev.skyliners.compat.SableFlight;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The frame blocks players placed by hand, kept with each dimension, which {@link FrameProtection} leaves be: a nav
 * light or a panel put on a finished airliner comes off again as it went on, while those its blueprint laid stay put.
 * A block counts as placed by hand when a player places it from its item, or a Create deployer does, which places as a
 * player does. The blueprints lay theirs straight into the world, as a schematicannon does; the control tower's does
 * so within the use of the blueprint, but the blueprint is no block's item. Each is kept by its position with the
 * block placed there, and only counts while that block still stands there: broken, blown up or replaced, it is
 * forgotten a few seconds later.
 *
 * <p>Assembled, an airliner's blocks move out of the world into its plot, so in the tick after one is assembled, and
 * once a second besides, each block placed by hand that has gone is looked for among an airliner's blocks at the same
 * place in the world, and followed there if it is found; one split off an assembled airliner into another is followed
 * the same way (see {@link SableFlight#movedAboard}). An airliner taken apart again loses them: back in the world, the
 * blocks placed by hand on it are taken for the blueprint's.
 *
 * <p>A client works out on its own whether mining makes progress, so every player is told of those in their dimension
 * (see {@link HandPlacedBlocks}).
 */
public final class HandPlaced extends SavedData {
    private static final String NAME = Skyliners.MOD_ID + "_hand_placed";
    /** How often the blocks placed by hand are looked over, in ticks. */
    private static final int LOOK_EVERY = 20;
    /** How long one can be gone, in ticks, before it is forgotten. */
    private static final long FORGET_AFTER = 100;

    /** What this client was last told of, and the level it was told of in; unused on a server. */
    private static Map<BlockPos, Block> told = Map.of();
    private static WeakReference<Level> toldIn = new WeakReference<>(null);

    /** The block placed by hand at each position. */
    private final Map<BlockPos, Block> placed = new HashMap<>();
    /** When each found gone was first found so, in game time; not saved. */
    private final Map<BlockPos, Long> goneSince = new HashMap<>();

    private static HandPlaced of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(HandPlaced::new, HandPlaced::load, null), NAME);
    }

    /**
     * Whether the block standing at a position was placed there by hand, as far as this side knows: the server keeps
     * them, a client was told of them.
     */
    public static boolean at(Level level, BlockPos pos, BlockState state) {
        Block block = known(level).get(pos);
        return block != null && state.is(block);
    }

    private static Map<BlockPos, Block> known(Level level) {
        if (level instanceof ServerLevel server) {
            return of(server).placed;
        }
        return toldIn.get() == level ? told : Map.of();
    }

    /** Marks the frame blocks a player has just placed by hand from their item, a door's upper half with it. */
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<BlockSnapshot> snapshots = event instanceof BlockEvent.EntityMultiPlaceEvent multi
            ? multi.getReplacedBlockSnapshots() : List.of(event.getBlockSnapshot());
        Map<BlockPos, Block> marked = new HashMap<>();
        for (BlockSnapshot snapshot : snapshots) {
            BlockState state = level.getBlockState(snapshot.getPos());
            if (FrameProtection.held(state) && holds(player, state.getBlock())) {
                marked.put(snapshot.getPos().immutable(), state.getBlock());
            }
        }
        of(level).change(level, marked, List.of());
    }

    /** Whether the player holds the block's own item in either hand, as placing it by hand takes. */
    private static boolean holds(Player player, Block block) {
        return places(player.getMainHandItem(), block) || places(player.getOffhandItem(), block);
    }

    private static boolean places(ItemStack stack, Block block) {
        return stack.getItem() instanceof BlockItem item && item.getBlock() == block;
    }

    /**
     * Tells a player arriving in a dimension, by logging in, changing dimension or respawning, of every block placed by
     * hand there.
     */
    public static void onArrive(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new HandPlacedBlocks(true, Map.copyOf(of(player.serverLevel()).placed), List.of()));
        }
    }

    /** On a client: what the server told of the dimension the player is in. */
    public static void told(Level level, HandPlacedBlocks payload) {
        if (payload.all() || toldIn.get() != level) {
            told = new HashMap<>();
            toldIn = new WeakReference<>(level);
        }
        payload.gone().forEach(told::remove);
        told.putAll(payload.placed());
    }

    /** Each tick: once a second, looks over the blocks placed by hand in the dimension (see {@link #lookOver}). */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % LOOK_EVERY == 0) {
            of(level).lookOver(level);
        }
    }

    /** In the tick after airliners were assembled in the dimension: follows the blocks they took along at once. */
    public static void assembled(ServerLevel level) {
        of(level).lookOver(level);
    }

    /**
     * As an airliner goes for good: forgets the blocks placed by hand in its plot, so that the next airliner given the
     * plot does not find them among its own.
     */
    public static void gone(ServerLevel level, Predicate<BlockPos> inPlot) {
        HandPlaced hand = of(level);
        hand.change(level, Map.of(), hand.placed.keySet().stream().filter(inPlot).toList());
    }

    /**
     * Follows those whose block has gone into an airliner assembled round it, and forgets the rest that have been gone
     * a while; one whose chunk is not loaded is left be until it is.
     */
    private void lookOver(ServerLevel level) {
        long now = level.getGameTime();
        Map<BlockPos, Block> moved = new HashMap<>();
        List<BlockPos> gone = new ArrayList<>();
        for (Map.Entry<BlockPos, Block> mark : placed.entrySet()) {
            BlockPos pos = mark.getKey();
            Block block = mark.getValue();
            if (stands(level, pos, block)) {
                goneSince.remove(pos);
                continue;
            }
            BlockPos aboard = SableFlight.movedAboard(level, pos, state -> state.is(block));
            if (aboard != null) {
                moved.put(aboard, block);
                gone.add(pos);
            } else if (now - goneSince.computeIfAbsent(pos, ignored -> now) >= FORGET_AFTER) {
                gone.add(pos);
            }
        }
        change(level, moved, gone);
    }

    /** Whether the block still stands at a position; taken as so while its chunk is not loaded, never loaded just to look. */
    private static boolean stands(ServerLevel level, BlockPos pos, Block block) {
        LevelChunk chunk = level.getChunkSource().getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), false);
        return chunk == null || chunk.getBlockState(pos).is(block);
    }

    /** Forgets the gone, then marks the placed, and tells everyone in the dimension. */
    private void change(ServerLevel level, Map<BlockPos, Block> marked, List<BlockPos> gone) {
        if (marked.isEmpty() && gone.isEmpty()) {
            return;
        }
        gone.forEach(pos -> {
            placed.remove(pos);
            goneSince.remove(pos);
        });
        placed.putAll(marked);
        marked.keySet().forEach(goneSince::remove);
        setDirty();
        PacketDistributor.sendToPlayersInDimension(level, new HandPlacedBlocks(false, Map.copyOf(marked), List.copyOf(gone)));
    }

    private static HandPlaced load(CompoundTag tag, HolderLookup.Provider registries) {
        HandPlaced hand = new HandPlaced();
        for (Tag each : tag.getList("Placed", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) each;
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("Block"));
            Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
            block.ifPresent(found -> hand.placed.put(BlockPos.of(entry.getLong("Pos")), found));
        }
        return hand;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        placed.forEach((pos, block) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pos.asLong());
            entry.putString("Block", BuiltInRegistries.BLOCK.getKey(block).toString());
            list.add(entry);
        });
        tag.put("Placed", list);
        return tag;
    }
}
