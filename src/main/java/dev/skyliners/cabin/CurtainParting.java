package dev.skyliners.cabin;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.block.CurtainBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * A shut curtain parting round whoever walks through it and falling shut once they have gone, so that nobody ghosts
 * through shut cloth. It holds nobody back: a curtain has no collision, open or shut. Only the columns round them part,
 * so a curtain across a whole hall opens a gap rather than sweeping away. Switched off by {@code cabin.curtainsPart} in
 * the server config.
 *
 * <p>What it parted is kept here on the server, not in the block, so that a curtain drawn open stays open: only what is
 * held here falls shut, and drawing a curtain by hand or the seatbelt signs opening it lets go of it. Nothing is saved:
 * a piece parted as the world closes stays open until it is next drawn, which harms nothing. A piece's position is the
 * block's own, in Sable's plot aboard an assembled ship, so it stays put however she flies.
 */
public final class CurtainParting {
    /** How many columns part either side of the one walked into: enough to pass through without touching the cloth. */
    private static final int REACH = 1;
    /**
     * How long after anyone was last found in a piece it falls shut, in ticks: longer than the second a player standing
     * still can go without being looked at, so a curtain does not close on them and part again.
     */
    private static final int SHUTS_AFTER = 30;
    /** The least time between two swishes on a level, in ticks: walking along a curtain parts it a step at a time. */
    private static final int SWISH_APART = 10;

    private static final Map<Level, Parted> PARTED = new WeakHashMap<>();

    /** What a level has parted, each piece with the tick someone was last near it, and when it last swished. */
    private static final class Parted {
        final Map<BlockPos, Long> lastInside = new HashMap<>();
        long lastSwish = -SWISH_APART;
    }

    private CurtainParting() {
    }

    /** Someone is in a piece of curtain: shut, it parts round them; parted, it stays so while they are there. */
    public static void walkedInto(ServerLevel level, BlockPos pos, BlockState state) {
        if (!SkylinersConfig.CURTAINS_PART.get()) {
            return;
        }
        Parted parted = PARTED.computeIfAbsent(level, parting -> new Parted());
        if (!state.getValue(CurtainBlock.OPEN) || parted.lastInside.containsKey(pos)) {
            part(level, parted, pos);
        }
    }

    /** Parts the columns round a piece, and holds them open from now on, those parted already among them. */
    private static void part(ServerLevel level, Parted parted, BlockPos pos) {
        long now = level.getGameTime();
        List<BlockPos> shut = new ArrayList<>();
        for (BlockPos at : CurtainBlock.columnsAround(level, pos, REACH)) {
            if (isOpen(level, at)) {
                parted.lastInside.computeIfPresent(at, (piece, last) -> now);
            } else {
                parted.lastInside.put(at, now);
                shut.add(at);
            }
        }
        draw(level, parted, shut, true);
    }

    /** Lets go of pieces of curtain drawn some other way, on the server, so that they stay as they were drawn. */
    public static void forget(ServerLevel level, Iterable<BlockPos> pieces) {
        Parted parted = PARTED.get(level);
        if (parted != null) {
            pieces.forEach(parted.lastInside::remove);
        }
    }

    /** After each level's tick on the server: what nobody has been near for a while falls shut. */
    public static void onTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && PARTED.containsKey(level)) {
            shutWhatIsDue(level, PARTED.get(level));
        }
    }

    private static void shutWhatIsDue(ServerLevel level, Parted parted) {
        long now = level.getGameTime();
        List<BlockPos> due = parted.lastInside.entrySet().stream().filter(held -> now - held.getValue() >= SHUTS_AFTER)
            .map(Map.Entry::getKey).toList();
        due.forEach(parted.lastInside::remove);
        draw(level, parted, due.stream().filter(at -> level.isLoaded(at) && isOpen(level, at)).toList(), false);
    }

    private static boolean isOpen(Level level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        return CurtainBlock.isCurtain(state) && state.getValue(CurtainBlock.OPEN);
    }

    /** Draws these pieces with one swish, unless one has just sounded. */
    private static void draw(ServerLevel level, Parted parted, List<BlockPos> pieces, boolean open) {
        if (pieces.isEmpty()) {
            return;
        }
        CurtainBlock.draw(level, pieces, open);
        long now = level.getGameTime();
        if (now - parted.lastSwish >= SWISH_APART) {
            parted.lastSwish = now;
            CurtainBlock.swish(level, pieces.get(0));
        }
    }
}
