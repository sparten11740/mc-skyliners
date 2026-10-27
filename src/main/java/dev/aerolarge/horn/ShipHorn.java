package dev.aerolarge.horn;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An airship's horn, sounded from the cord in a zeppelin's control car (see {@link dev.aerolarge.block.HornCordBlock}):
 * a deep two-tone blast heard some 300 blocks off, sounding from the ship as it flies on; the villagers and animals
 * under it turning to look up at it for a few seconds; and, in flight, the blast's echo off the ground below, back
 * the later the higher it flies, so its height is heard, as an airship's echo altimeter told it by the echo of a bang.
 */
public final class ShipHorn {
    /** How loud the blast and its echo are: heard out to 16 blocks for each, so some 320 blocks off. */
    private static final float VOLUME = 20F;
    /**
     * How fast the blast goes down to the ground and its echo back up, in blocks a tick: slower than sound, so the echo
     * of a ship high up comes clearly after the blast, a second for each 100 blocks up.
     */
    private static final double ECHO_SPEED = 10;
    /** No echo is heard from closer to the ground than this, in blocks: it would come with the blast itself. */
    private static final double LEAST_ECHO = 12;
    /** How far out across the ground under the ship villagers and animals hear it and look up, in blocks. */
    private static final double LOOK_REACH = 48;
    /** How long they look up, in ticks, and the most that do at once, so a horn over a crowded farm stays cheap. */
    private static final int LOOK_TICKS = 60;
    private static final int MOST_LOOKING = 48;

    /** An echo yet to come back up, from the ground at a place, at a game time. */
    private record Echo(Vec3 from, long at) {
    }

    /** A villager or an animal looking up at the horn's cord until a game time. */
    private record Look(Mob mob, BlockPos cord, long until) {
    }

    private static final Map<ServerLevel, List<Echo>> ECHOES = new HashMap<>();
    private static final Map<ServerLevel, List<Look>> LOOKS = new HashMap<>();

    private ShipHorn() {
    }

    /** Sounds the horn from its cord, where the cord is (aboard a zeppelin in flight, the plot's). */
    public static void sound(ServerLevel level, BlockPos cord) {
        level.playSound(null, cord, AerolargeRegistry.ZEPPELIN_HORN.get(), SoundSource.BLOCKS, VOLUME, 1.0F);
        Vec3 at = SableFlight.toWorld(level, Vec3.atCenterOf(cord));
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        double height = at.y - ground;
        if (SableFlight.aboard(level, Vec3.atCenterOf(cord)) && height >= LEAST_ECHO) {
            long back = level.getGameTime() + Math.round(2 * height / ECHO_SPEED);
            ECHOES.computeIfAbsent(level, key -> new ArrayList<>()).add(new Echo(new Vec3(at.x, ground, at.z), back));
        }
        lookUp(level, cord, at, ground);
    }

    /** The villagers and animals on the ground under the horn, as many as may, look up at it. */
    private static void lookUp(ServerLevel level, BlockPos cord, Vec3 at, int ground) {
        AABB under = new AABB(at.x - LOOK_REACH, ground - LOOK_REACH / 2, at.z - LOOK_REACH, at.x + LOOK_REACH, at.y, at.z + LOOK_REACH);
        List<Mob> listeners = level.getEntitiesOfClass(Mob.class, under, mob -> mob instanceof AbstractVillager || mob instanceof Animal);
        List<Look> looks = LOOKS.computeIfAbsent(level, key -> new ArrayList<>());
        long until = level.getGameTime() + LOOK_TICKS;
        listeners.stream().limit(MOST_LOOKING).forEach(mob -> looks.add(new Look(mob, cord, until)));
    }

    /** Once a level's tick is over: echoes due come back up, and whoever is looking up keeps their eyes on the ship. */
    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        List<Echo> echoes = ECHOES.get(level);
        if (echoes != null) {
            echoes.stream().filter(echo -> echo.at() <= now).forEach(echo -> echo(level, echo.from()));
            echoes.removeIf(echo -> echo.at() <= now);
        }
        List<Look> looks = LOOKS.get(level);
        if (looks != null) {
            looks.removeIf(look -> look.until() < now || !look.mob().isAlive() || look.mob().isRemoved());
            looks.forEach(look -> lookAt(look.mob(), SableFlight.toWorld(level, Vec3.atCenterOf(look.cord()))));
        }
    }

    /** An echo comes back up from the ground at a place. */
    private static void echo(ServerLevel level, Vec3 from) {
        level.playSound(null, from.x, from.y, from.z, AerolargeRegistry.ZEPPELIN_HORN_ECHO.get(), SoundSource.BLOCKS, VOLUME, 1.0F);
    }

    /** Turns a mob's head to look at a place, as its own look would; set after its tick, so it holds. */
    private static void lookAt(Mob mob, Vec3 target) {
        double dx = target.x - mob.getX();
        double dy = target.y - mob.getEyeY();
        double dz = target.z - mob.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        mob.setYHeadRot(yaw);
        mob.setXRot(Mth.clamp(pitch, -90F, 90F));
    }

    /** Forgets a level's echoes and lookers as it unloads. */
    public static void drop(ServerLevel level) {
        ECHOES.remove(level);
        LOOKS.remove(level);
    }
}
