package dev.skyliners.autopilot;

import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.compat.SableFlight;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The ships let go of because they stand parked with nobody near. A zeppelin or rocket keeps itself loaded once
 * assembled (see {@link AutopilotBlockEntity}), and a loaded ship costs the server its physics wherever it is: little
 * once it has come to rest, but one that never quite does on uneven ground costs tens of milliseconds every tick. So one
 * on the ground with its autopilot off and nobody within {@link #LET_GO_AT} is let go, and Sable unloads it.
 *
 * <p>Sable loads a ship back only once the land under all of it is simulated. A rocket would come back only with someone
 * close under it, and a zeppelin, longer than the land simulated round a player, never would. So where each one stands
 * is noted here, and as anyone comes within {@link #BRING_BACK_AT} the land under it is loaded for it. It takes up
 * keeping itself loaded again as it ticks, and is forgotten here.
 */
public final class ParkedShips extends SavedData {
    private static final String NAME = Skyliners.MOD_ID + "_parked_ships";
    /** How far from everyone a parked ship must be to be let go, in blocks: well past where it comes back. */
    private static final double LET_GO_AT = 600;
    /** How near anyone must come to bring a ship back, in blocks: past the 320 Sable shows one at by default. */
    private static final double BRING_BACK_AT = 400;
    /** How often it looks who has come near, in ticks. */
    private static final int LOOK_EVERY = 20;
    /** How long the land under a ship stays loaded once asked for, in ticks: a few looks, each of which asks again. */
    private static final int KEPT = 60;
    /** How long it asks for a ship that does not come back, in ticks, before waiting for everyone to leave and return. */
    private static final int GIVE_UP = 600;
    private static final TicketType<ChunkPos> LAND_UNDER =
        TicketType.create(Skyliners.MOD_ID + ":parked_ship", Comparator.comparingLong(ChunkPos::toLong), KEPT);

    /** Where each ship let go stands. */
    private final Map<UUID, SableFlight.Footprint> parked = new HashMap<>();
    /** When anyone came near each ship still not back, in game time; not saved. */
    private final Map<UUID, Long> askedSince = new HashMap<>();

    private static ParkedShips of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ParkedShips::new, ParkedShips::load), NAME);
    }

    /**
     * Lets go of the ship an autopilot is part of, parked as it is, if nobody is near it. Whether it did: a ship with
     * someone near, or none at all, is not let go. Asked of a ship already let go it does no harm, as it may be asked
     * again before Sable has unloaded it.
     */
    static boolean letGo(ServerLevel level, BlockEntity autopilot) {
        if (!SkylinersConfig.LET_PARKED_SHIPS_GO.get()) {
            return false;
        }
        SableFlight.Footprint footprint = SableFlight.footprint(autopilot);
        if (footprint == null || anyoneNear(level, footprint, LET_GO_AT)) {
            return false;
        }
        of(level).note(footprint);
        SableFlight.keepLoaded(autopilot, false);
        return true;
    }

    /**
     * Whether nobody is near enough the ship an autopilot is part of to keep it from being let go once parked (see
     * {@link #LET_GO_AT}); false for an autopilot in no ship.
     */
    static boolean nobodyNear(ServerLevel level, BlockEntity autopilot) {
        SableFlight.Footprint footprint = SableFlight.footprint(autopilot);
        return footprint != null && !anyoneNear(level, footprint, LET_GO_AT);
    }

    /** The ship an autopilot is part of is loaded and looks after itself again, or is taken apart: forgets it. */
    static void back(ServerLevel level, BlockEntity autopilot) {
        SableFlight.Footprint footprint = SableFlight.footprint(autopilot);
        if (footprint != null) {
            of(level).forget(footprint.ship());
        }
    }

    /** Now and then: the land under each ship let go that anyone has come near is loaded, until it comes back. */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % LOOK_EVERY != 0) {
            return;
        }
        ParkedShips ships = of(level);
        ships.parked.values().forEach(footprint -> ships.look(level, footprint));
    }

    private void look(ServerLevel level, SableFlight.Footprint footprint) {
        if (!anyoneNear(level, footprint, BRING_BACK_AT)) {
            askedSince.remove(footprint.ship());
            return;
        }
        long now = level.getGameTime();
        if (now - askedSince.computeIfAbsent(footprint.ship(), ship -> now) <= GIVE_UP) {
            loadLandUnder(level, footprint);
        }
    }

    /** Asks for every chunk under a ship, in reach of Sable's look for land simulated under it (a radius of 1). */
    private static void loadLandUnder(ServerLevel level, SableFlight.Footprint footprint) {
        for (int x = footprint.minChunkX(); x <= footprint.maxChunkX(); x++) {
            for (int z = footprint.minChunkZ(); z <= footprint.maxChunkZ(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                level.getChunkSource().addRegionTicket(LAND_UNDER, chunk, 1, chunk);
            }
        }
    }

    private static boolean anyoneNear(ServerLevel level, SableFlight.Footprint footprint, double reach) {
        return level.players().stream().anyMatch(player -> footprint.distanceTo(player.getX(), player.getZ()) <= reach);
    }

    private void note(SableFlight.Footprint footprint) {
        if (!footprint.equals(parked.put(footprint.ship(), footprint))) {
            setDirty();
        }
    }

    private void forget(UUID ship) {
        askedSince.remove(ship);
        if (parked.remove(ship) != null) {
            setDirty();
        }
    }

    private static ParkedShips load(CompoundTag tag, HolderLookup.Provider registries) {
        ParkedShips ships = new ParkedShips();
        for (Tag entry : tag.getList("Parked", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) entry;
            if (saved.hasUUID("Ship")) {
                UUID ship = saved.getUUID("Ship");
                ships.parked.put(ship, new SableFlight.Footprint(ship, saved.getInt("MinX"), saved.getInt("MinZ"), saved.getInt("MaxX"), saved.getInt("MaxZ")));
            }
        }
        return ships;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        parked.values().forEach(footprint -> list.add(saved(footprint)));
        tag.put("Parked", list);
        return tag;
    }

    private static CompoundTag saved(SableFlight.Footprint footprint) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("Ship", footprint.ship());
        entry.putInt("MinX", footprint.minChunkX());
        entry.putInt("MinZ", footprint.minChunkZ());
        entry.putInt("MaxX", footprint.maxChunkX());
        entry.putInt("MaxZ", footprint.maxChunkZ());
        return entry;
    }
}
