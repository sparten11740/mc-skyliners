package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A landing target's scores: how close the best parachute landing came to its middle, and whose it was. Every
 * landing within {@link #REACH} blocks of the nearest target is called out to everyone around, with the distance.
 * The targets loaded on the server are kept track of (see {@link Loaded}), so a landing finds its target without
 * searching the world.
 */
public class LandingTargetBlockEntity extends BlockEntity {
    /** How far from a target, across the ground, a landing still counts for it. */
    private static final double REACH = 64;
    /** A landing this close to a target, or closer, is a perfect jump. */
    private static final double PERFECT = 1.0;
    /** Everyone this close to a landing hears how it went. */
    private static final double AUDIENCE = 128;
    /** How far off the target's bell is heard, in blocks: further off, the player it rings for hears it in their ears. */
    private static final double EARSHOT = 32;
    private static final Loaded<LandingTargetBlockEntity> LOADED = new Loaded<>(LandingTargetBlockEntity.class);

    private double best = Double.MAX_VALUE;
    private String bestName = "";
    /** The paper plane thrown nearest to it, and by whom: a record of their own, apart from the parachutists'. */
    private double bestPlane = Double.MAX_VALUE;
    private String bestPlaneName = "";

    public LandingTargetBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LANDING_TARGET_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    /** A parachutist has just landed: scores the landing against the nearest target in reach, if there is one. */
    public static void scoreLanding(ServerPlayer player) {
        LandingTargetBlockEntity nearest = nearest(player.level(), player.position());
        if (nearest != null) {
            nearest.score(player, nearest.distanceTo(player.position()));
        }
    }

    /**
     * A paper plane has just come down on the ground where it lies: scores it for whoever threw it against the nearest
     * target in reach, if there is one, as a landing is scored but with a record of its own.
     */
    public static void scorePlane(ServerPlayer thrower, Vec3 where) {
        LandingTargetBlockEntity nearest = nearest(thrower.level(), where);
        if (nearest != null) {
            nearest.scorePlane(thrower, nearest.distanceTo(where));
        }
    }

    /** The nearest target to a place within {@link #REACH} of it, across the ground; null for none. */
    @Nullable
    private static LandingTargetBlockEntity nearest(Level level, Vec3 where) {
        LandingTargetBlockEntity nearest = null;
        double nearestDistance = REACH;
        for (LandingTargetBlockEntity target : LOADED.in(level)) {
            double distance = target.distanceTo(where);
            if (distance < nearestDistance) {
                nearest = target;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** How far a place is from the target's middle, across the ground. */
    private double distanceTo(Vec3 where) {
        return Math.hypot(where.x - (worldPosition.getX() + 0.5), where.z - (worldPosition.getZ() + 0.5));
    }

    private void score(ServerPlayer player, double distance) {
        boolean record = distance < best;
        if (distance <= PERFECT) {
            FirstFlight.PERFECT_JUMP.award(player);
        }
        if (record) {
            best = distance;
            bestName = player.getGameProfile().getName();
            setChanged();
        }
        callOut(player, record ? "message.aerolarge.landing_target.record" : "message.aerolarge.landing_target.landed", distance, record);
    }

    private void scorePlane(ServerPlayer thrower, double distance) {
        boolean record = distance < bestPlane;
        if (record) {
            bestPlane = distance;
            bestPlaneName = thrower.getGameProfile().getName();
            setChanged();
        }
        callOut(thrower, record ? "message.aerolarge.landing_target.plane_record" : "message.aerolarge.landing_target.plane_landed", distance, record);
    }

    /**
     * Tells everyone near a player how far from the middle their landing or plane came down, with a bell, or a fanfare
     * for a new best, rung at the target and, for a player too far off to hear it there (a plane's thrower, up on a
     * zeppelin), in their own ears too.
     */
    private void callOut(ServerPlayer player, String key, double distance, boolean record) {
        Component message = Component.translatable(key, player.getDisplayName(), String.format(Locale.ROOT, "%.1f", distance));
        ServerLevel serverLevel = (ServerLevel) player.level();
        serverLevel.players().stream()
            .filter(other -> other.distanceToSqr(player) < AUDIENCE * AUDIENCE)
            .forEach(other -> other.sendSystemMessage(message));
        SoundEvent sound = record ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BELL.value();
        serverLevel.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > EARSHOT * EARSHOT) {
            player.playNotifySound(sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /** The best landing and the best paper plane so far, to show when the target is used. */
    Component describe() {
        if (best == Double.MAX_VALUE && bestPlane == Double.MAX_VALUE) {
            return Component.translatable("message.aerolarge.landing_target.none");
        }
        Component landing = best == Double.MAX_VALUE ? Component.empty()
            : Component.translatable("message.aerolarge.landing_target.best", bestName, String.format(Locale.ROOT, "%.1f", best));
        Component plane = bestPlane == Double.MAX_VALUE ? Component.empty()
            : Component.translatable("message.aerolarge.landing_target.best_plane", bestPlaneName, String.format(Locale.ROOT, "%.1f", bestPlane));
        return best == Double.MAX_VALUE || bestPlane == Double.MAX_VALUE ? landing.copy().append(plane)
            : landing.copy().append(Component.literal(" · ")).append(plane);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (best != Double.MAX_VALUE) {
            tag.putDouble("Best", best);
            tag.putString("BestName", bestName);
        }
        if (bestPlane != Double.MAX_VALUE) {
            tag.putDouble("BestPlane", bestPlane);
            tag.putString("BestPlaneName", bestPlaneName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        best = tag.contains("Best") ? tag.getDouble("Best") : Double.MAX_VALUE;
        bestName = tag.getString("BestName");
        // Saved before paper planes were scored: none yet.
        bestPlane = tag.contains("BestPlane") ? tag.getDouble("BestPlane") : Double.MAX_VALUE;
        bestPlaneName = tag.getString("BestPlaneName");
    }

    @Nullable
    static LandingTargetBlockEntity at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LandingTargetBlockEntity target ? target : null;
    }
}
