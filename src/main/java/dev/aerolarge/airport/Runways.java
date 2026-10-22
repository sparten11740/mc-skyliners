package dev.aerolarge.airport;

import dev.aerolarge.Aerolarge;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The runways laid down by their blueprint in a dimension (see {@link RunwayBuild}), kept with the world, so that an
 * autopilot flying to a pin near one lands on it instead of looking for open land.
 */
public final class Runways extends SavedData {
    private static final String NAME = Aerolarge.MOD_ID + "_runways";

    /**
     * A runway laid down: the middle of its start, in its surface's layer, the way it runs from there, how long it is
     * and how far out either side of its centre line it reaches, shoulders and all.
     */
    public record Built(BlockPos start, Direction along, int length, int halfWidth) {
        /** Its surface's height, the top of the blocks it is laid in. */
        public int elevation() {
            return start.getY() + 1;
        }

        /** The middle of its centre line, in world x and z. */
        public double middleX() {
            return start.getX() + 0.5 + along.getStepX() * length / 2.0;
        }

        public double middleZ() {
            return start.getZ() + 0.5 + along.getStepZ() * length / 2.0;
        }
    }

    private final List<Built> runways = new ArrayList<>();

    private static Runways of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Runways::new, Runways::load, null), NAME);
    }

    static void add(ServerLevel level, Built runway) {
        Runways all = of(level);
        all.runways.add(runway);
        all.setDirty();
    }

    /** Every runway laid down in the dimension. */
    public static List<Built> in(ServerLevel level) {
        return List.copyOf(of(level).runways);
    }

    /** The runway laid down nearest a place whose middle is within a distance of it, in blocks; null for none. */
    @Nullable
    public static Built near(ServerLevel level, double x, double z, double within) {
        Built nearest = null;
        double best = within;
        for (Built runway : of(level).runways) {
            double distance = Math.hypot(runway.middleX() - x, runway.middleZ() - z);
            if (distance <= best) {
                best = distance;
                nearest = runway;
            }
        }
        return nearest;
    }

    private static Runways load(CompoundTag tag, HolderLookup.Provider registries) {
        Runways all = new Runways();
        for (Tag each : tag.getList("Runways", Tag.TAG_COMPOUND)) {
            CompoundTag runway = (CompoundTag) each;
            Direction along = Direction.from2DDataValue(runway.getInt("Along"));
            all.runways.add(new Built(BlockPos.of(runway.getLong("Start")), along, runway.getInt("Length"), runway.getInt("HalfWidth")));
        }
        return all;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Built runway : runways) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Start", runway.start().asLong());
            entry.putInt("Along", runway.along().get2DDataValue());
            entry.putInt("Length", runway.length());
            entry.putInt("HalfWidth", runway.halfWidth());
            list.add(entry);
        }
        tag.put("Runways", list);
        return tag;
    }
}
