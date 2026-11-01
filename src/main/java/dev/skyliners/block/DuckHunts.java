package dev.skyliners.block;

import dev.skyliners.Skyliners;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which hidden rubber ducks each player has found aboard each airliner, kept for the whole server, so every airliner
 * has a duck hunt of its own: the ducks a blueprint hides share a hunt, told to each as the airliner is built (see
 * {@link RubberDuckBlockEntity}), and a duck found aboard one airliner counts toward that airliner's hunt only. A
 * player's finds in a hunt are a bit for each duck, duck 1 the lowest.
 */
final class DuckHunts extends SavedData {
    private static final String NAME = Skyliners.MOD_ID + "_duck_hunts";

    /** Each hunt's finders, and the ducks each has found in it. */
    private final Map<UUID, Map<UUID, Integer>> hunts = new HashMap<>();

    /** The server's hunts, kept with the overworld, wherever the airliners fly. */
    static DuckHunts of(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(DuckHunts::new, DuckHunts::load, null), NAME);
    }

    /** Counts a duck, from 1, as found by the player in the hunt; false if they had found it already. */
    boolean find(UUID hunt, UUID player, int duck) {
        Map<UUID, Integer> finders = hunts.computeIfAbsent(hunt, ignored -> new HashMap<>());
        int before = finders.getOrDefault(player, 0);
        int after = before | bit(duck);
        if (after == before) {
            return false;
        }
        finders.put(player, after);
        setDirty();
        return true;
    }

    /** How many of the hunt's ducks the player has found. */
    int found(UUID hunt, UUID player) {
        return Integer.bitCount(hunts.getOrDefault(hunt, Map.of()).getOrDefault(player, 0));
    }

    /** Everyone who has found the duck, from 1, in the hunt. */
    List<UUID> finders(UUID hunt, int duck) {
        return hunts.getOrDefault(hunt, Map.of()).entrySet().stream()
            .filter(finder -> (finder.getValue() & bit(duck)) != 0)
            .map(Map.Entry::getKey)
            .toList();
    }

    private static int bit(int duck) {
        return 1 << (duck - 1);
    }

    private static DuckHunts load(CompoundTag tag, HolderLookup.Provider registries) {
        DuckHunts loaded = new DuckHunts();
        for (Tag each : tag.getList("Hunts", Tag.TAG_COMPOUND)) {
            CompoundTag hunt = (CompoundTag) each;
            Map<UUID, Integer> finders = new HashMap<>();
            for (Tag finder : hunt.getList("Finders", Tag.TAG_COMPOUND)) {
                finders.put(((CompoundTag) finder).getUUID("Player"), ((CompoundTag) finder).getInt("Ducks"));
            }
            loaded.hunts.put(hunt.getUUID("Hunt"), finders);
        }
        return loaded;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        hunts.forEach((hunt, finders) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Hunt", hunt);
            ListTag found = new ListTag();
            finders.forEach((player, ducks) -> {
                CompoundTag finder = new CompoundTag();
                finder.putUUID("Player", player);
                finder.putInt("Ducks", ducks);
                found.add(finder);
            });
            entry.put("Finders", found);
            list.add(entry);
        });
        tag.put("Hunts", list);
        return tag;
    }
}
