package dev.skyliners.radar;

import dev.skyliners.Skyliners;
import dev.skyliners.block.Loaded;
import dev.skyliners.block.TransponderBlockEntity;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Every transponder of a dimension, for its players' maps. A transponder on the ground is kept in the dimension's saved
 * data, so it shows on the maps however far off it is, its chunk loaded or not, as a beacon in Subnautica does; one
 * aboard an assembled airliner flies with it, so it is only shown while it is loaded, where the airliner is, which it
 * always is while anyone is near enough to fly it. Once a second every player in the dimension is sent them all (see
 * {@link RadarContacts}): a few dozen at most, and the airliners among them move. With them goes where each player
 * of the dimension is, however far off, for their faces on the maps.
 */
public final class Transponders extends SavedData {
    private static final String NAME = Skyliners.MOD_ID + "_transponders";
    /** How often the players are sent the transponders, in ticks. */
    private static final int SEND_EVERY = 20;
    /** Slower than this across the ground, in blocks a second, an airliner shows no heading. */
    private static final double STILL = 1;
    /** The transponders loaded on the server, ground and aboard alike. */
    public static final Loaded<TransponderBlockEntity> LOADED = new Loaded<>(TransponderBlockEntity.class);

    /** What a transponder on the ground shows, by where it stands. */
    private record Ground(MarkerIcon icon, MarkerColour colour) {
    }

    private final Map<BlockPos, Ground> ground = new HashMap<>();

    private static Transponders of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Transponders::new, Transponders::load, null), NAME);
    }

    /**
     * Keeps a loaded transponder in the saved data as it shows now while it stands on the ground, and out of it while
     * it is aboard an assembled airliner (moved there as the airliner was assembled, it is no longer where it stood).
     */
    public static void keep(ServerLevel level, TransponderBlockEntity transponder) {
        Transponders transponders = of(level);
        BlockPos pos = transponder.getBlockPos().immutable();
        if (transponder.airborne()) {
            if (transponders.ground.remove(pos) != null) {
                transponders.setDirty();
            }
            return;
        }
        Ground now = new Ground(transponder.icon(), transponder.colour());
        if (!now.equals(transponders.ground.put(pos, now))) {
            transponders.setDirty();
        }
    }

    /** Takes a transponder off the maps' ground: broken, or moved off (into an airliner being assembled, say). */
    public static void remove(ServerLevel level, BlockPos pos) {
        Transponders transponders = of(level);
        if (transponders.ground.remove(pos) != null) {
            transponders.setDirty();
        }
    }

    /**
     * Each tick: once a second, the loaded transponders are kept in the saved data as they stand (see {@link #keep}),
     * and every player in the dimension is sent where they all are, and where each of the dimension's players is.
     */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % SEND_EVERY != 0) {
            return;
        }
        List<TransponderBlockEntity> loaded = LOADED.in(level);
        loaded.forEach(transponder -> keep(level, transponder));
        if (level.players().isEmpty()) {
            return;
        }
        RadarContacts contacts = new RadarContacts(contacts(level, loaded), PlayerContact.in(level));
        for (ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, contacts);
        }
    }

    /**
     * Every transponder of the dimension: those standing on the ground from the saved data, and those aboard the
     * airliners loaded, where their airliners are now.
     */
    private static List<Contact> contacts(ServerLevel level, List<TransponderBlockEntity> loaded) {
        List<Contact> contacts = new ArrayList<>();
        of(level).ground.forEach((pos, shown) -> contacts.add(standing(pos, shown)));
        for (TransponderBlockEntity transponder : loaded) {
            if (transponder.airborne()) {
                contacts.add(aboard(level, transponder));
            }
        }
        return contacts;
    }

    /** A transponder standing on the ground, where it stands. */
    private static Contact standing(BlockPos pos, Ground shown) {
        Vec3 at = Vec3.atCenterOf(pos);
        return new Contact(pos, at.x, at.y, at.z, shown.icon(), shown.colour(), false, Float.NaN, 0);
    }

    /** A transponder aboard an airliner, where the airliner has it in the world, going the way the airliner goes. */
    private static Contact aboard(ServerLevel level, TransponderBlockEntity transponder) {
        Vec3 plot = Vec3.atCenterOf(transponder.getBlockPos());
        Vec3 at = SableFlight.toWorld(level, plot);
        Vec3 velocity = SableFlight.velocity(level, plot);
        double speed = Math.hypot(velocity.x, velocity.z);
        // A compass heading: 0 north (toward -z), 90 east (+x).
        float heading = speed < STILL ? Float.NaN : (float) Math.floorMod(Math.round(Math.toDegrees(Math.atan2(velocity.x, -velocity.z))), 360L);
        return new Contact(transponder.getBlockPos(), at.x, at.y, at.z, transponder.icon(), transponder.colour(), true, heading, (float) speed);
    }

    private static Transponders load(CompoundTag tag, HolderLookup.Provider registries) {
        Transponders transponders = new Transponders();
        for (Tag each : tag.getList("Ground", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) each;
            BlockPos pos = BlockPos.of(entry.getLong("Pos"));
            MarkerIcon icon = MarkerIcon.named(entry.getString("Icon"), MarkerIcon.FLAG);
            transponders.ground.put(pos, new Ground(icon, MarkerColour.named(entry.getString("Colour"), MarkerColour.RED)));
        }
        return transponders;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        ground.forEach((pos, shown) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Pos", pos.asLong());
            entry.putString("Icon", shown.icon().name());
            entry.putString("Colour", shown.colour().name());
            list.add(entry);
        });
        tag.put("Ground", list);
        return tag;
    }
}
