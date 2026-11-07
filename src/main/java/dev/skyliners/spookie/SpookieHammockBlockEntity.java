package dev.skyliners.spookie;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Turning;
import dev.skyliners.entity.Spookie;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The keeper of the freighter's Spookie (see {@link Spookie}), in its hammock: it makes the Spookie once she stands,
 * makes it again if it goes missing, and every tick hands it its rounds, the stops it walks to in turn and how long it
 * rests at each, bringing it home if it is ever found far from them, as when her blocks are assembled into a flying
 * body and moved away from where it stood.
 *
 * <p>Its rounds are set as she is built (see {@link #configure}), each stop the block over her deck's floor it stands
 * in, as an offset from the hammock turned as she was placed, and turned again with the hammock's facing if it is ever
 * loaded facing another way (see {@link Turning}). The first stop is home, beside the hammock.
 */
public class SpookieHammockBlockEntity extends BlockEntity {
    /**
     * How long a Spookie it has made may be missing before another is made, in ticks: its own chunk may load late. The
     * first is made at once.
     */
    private static final int MISSING = 100;
    /** How far from every stop of its rounds the Spookie may be found before it is brought home, in blocks. */
    private static final double STRAYED = 8;
    /** How often the keeper looks whether the Spookie has strayed, in ticks. */
    private static final int LOOK_EVERY = 20;
    /** How long a Spookie kept by a hammock set down by hand rests beside it at a time, in ticks. */
    private static final int BESIDE_REST = 600;

    @Nullable
    private UUID spookie;
    private List<BlockPos> stops = List.of();
    private List<Integer> rests = List.of();
    /** The rounds as handed to the Spookie: the stops where they are now, made once the keeper first needs them. */
    @Nullable
    private List<Spookie.Stop> rounds;
    private int missing;

    public SpookieHammockBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.SPOOKIE_HAMMOCK_ENTITY.get(), pos, state);
    }

    /**
     * Set as she is built: the Spookie's stops, home first, as offsets from the hammock in her layout, turned as she was
     * placed, and how long it rests at each, in ticks.
     */
    public void configure(List<BlockPos> offsets, List<Integer> restTicks, Rotation rotation) {
        stops = Turning.turn(List.copyOf(offsets), rotation);
        rests = List.copyOf(restTicks);
        rounds = null;
        setChanged();
    }

    /** Set down by hand: it keeps a Spookie standing beside it on the side given, with no rounds to walk. */
    public void keepBeside(Direction side) {
        configure(List.of(BlockPos.ZERO.relative(side)), List.of(BESIDE_REST), Rotation.NONE);
    }

    static void tick(ServerLevel level, SpookieHammockBlockEntity keeper) {
        if (keeper.stops.isEmpty() || keeper.rests.size() != keeper.stops.size()) {
            return;
        }
        Spookie found = keeper.spookie(level);
        if (found == null) {
            if (keeper.spookie == null || ++keeper.missing >= MISSING) {
                keeper.make(level);
            }
            return;
        }
        keeper.missing = 0;
        List<Spookie.Stop> handed = keeper.rounds();
        found.keep(level.getGameTime(), handed);
        if (level.getGameTime() % LOOK_EVERY == 0 && strayed(found.position(), handed)) {
            Vec3 home = handed.get(0).at();
            found.teleportTo(home.x, home.y, home.z);
        }
    }

    /** The rounds where the stops are now, made once: the middle of each stop's block, at its floor. */
    private List<Spookie.Stop> rounds() {
        if (rounds == null) {
            List<Spookie.Stop> made = new ArrayList<>();
            for (int stop = 0; stop < stops.size(); stop++) {
                made.add(new Spookie.Stop(Vec3.atBottomCenterOf(worldPosition.offset(stops.get(stop))), rests.get(stop)));
            }
            rounds = List.copyOf(made);
        }
        return rounds;
    }

    /** Whether the Spookie is further from every stop of its rounds than it can wander: off her deck. */
    private static boolean strayed(Vec3 at, List<Spookie.Stop> handed) {
        return handed.stream().allMatch(stop -> stop.at().distanceTo(at) > STRAYED);
    }

    /** Makes the Spookie at home, beside the hammock. */
    private void make(ServerLevel level) {
        Vec3 home = rounds().get(0).at();
        Spookie made = new Spookie(SkylinersRegistry.SPOOKIE.get(), level);
        made.moveTo(home.x, home.y, home.z, 0, 0);
        made.keep(level.getGameTime(), rounds());
        level.addFreshEntity(made);
        spookie = made.getUUID();
        missing = 0;
        setChanged();
    }

    @Nullable
    private Spookie spookie(ServerLevel level) {
        Entity found = spookie == null ? null : level.getEntity(spookie);
        return found instanceof Spookie alive && alive.isAlive() ? alive : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (spookie != null) {
            tag.putUUID("Spookie", spookie);
        }
        ListTag saved = new ListTag();
        for (int stop = 0; stop < stops.size() && stop < rests.size(); stop++) {
            CompoundTag one = new CompoundTag();
            one.putInt("X", stops.get(stop).getX());
            one.putInt("Y", stops.get(stop).getY());
            one.putInt("Z", stops.get(stop).getZ());
            one.putInt("Rest", rests.get(stop));
            saved.add(one);
        }
        tag.put("Rounds", saved);
        Turning.save(tag, getBlockState());
    }

    /** Loaded again, its stops are turned if its hammock has turned since they were saved. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        spookie = tag.hasUUID("Spookie") ? tag.getUUID("Spookie") : null;
        List<BlockPos> loaded = new ArrayList<>();
        List<Integer> loadedRests = new ArrayList<>();
        ListTag saved = tag.getList("Rounds", Tag.TAG_COMPOUND);
        for (int index = 0; index < saved.size(); index++) {
            CompoundTag stop = saved.getCompound(index);
            loaded.add(new BlockPos(stop.getInt("X"), stop.getInt("Y"), stop.getInt("Z")));
            loadedRests.add(stop.getInt("Rest"));
        }
        stops = Turning.turn(loaded, Turning.since(tag, getBlockState()));
        rests = List.copyOf(loadedRests);
        rounds = null;
    }
}
