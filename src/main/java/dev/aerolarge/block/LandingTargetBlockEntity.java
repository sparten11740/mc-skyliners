package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A landing target's scores: how close the best parachute landing came to its middle, and whose it was. Every
 * landing within {@link #REACH} blocks of the nearest target is called out to everyone around, with the distance.
 * The targets loaded on the server are kept track of, so a landing finds its target without searching the world.
 */
public class LandingTargetBlockEntity extends BlockEntity {
    /** How far from a target, across the ground, a landing still counts for it. */
    private static final double REACH = 64;
    /** Everyone this close to a landing hears how it went. */
    private static final double AUDIENCE = 128;
    private static final Map<Level, Set<BlockPos>> LOADED = new WeakHashMap<>();

    private double best = Double.MAX_VALUE;
    private String bestName = "";

    public LandingTargetBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LANDING_TARGET_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            LOADED.computeIfAbsent(level, ignored -> new HashSet<>()).add(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        forget();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        forget();
    }

    private void forget() {
        Set<BlockPos> targets = level == null ? null : LOADED.get(level);
        if (targets != null) {
            targets.remove(worldPosition);
        }
    }

    /** A parachutist has just landed: scores the landing against the nearest target in reach, if there is one. */
    public static void scoreLanding(ServerPlayer player) {
        LandingTargetBlockEntity nearest = null;
        double nearestDistance = REACH;
        for (BlockPos pos : LOADED.getOrDefault(player.level(), Set.of())) {
            double distance = Math.hypot(player.getX() - (pos.getX() + 0.5), player.getZ() - (pos.getZ() + 0.5));
            if (distance < nearestDistance && player.level().getBlockEntity(pos) instanceof LandingTargetBlockEntity target) {
                nearest = target;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            nearest.score(player, nearestDistance);
        }
    }

    private void score(ServerPlayer player, double distance) {
        boolean record = distance < best;
        String metres = String.format(Locale.ROOT, "%.1f", distance);
        Component message = Component.translatable(record ? "message.aerolarge.landing_target.record" : "message.aerolarge.landing_target.landed",
            player.getDisplayName(), metres);
        if (record) {
            best = distance;
            bestName = player.getGameProfile().getName();
            setChanged();
        }
        ServerLevel serverLevel = (ServerLevel) player.level();
        serverLevel.players().stream()
            .filter(other -> other.distanceToSqr(player) < AUDIENCE * AUDIENCE)
            .forEach(other -> other.sendSystemMessage(message));
        serverLevel.playSound(null, worldPosition, record ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** The best landing so far, to show when the target is used. */
    Component describe() {
        return best == Double.MAX_VALUE ? Component.translatable("message.aerolarge.landing_target.none")
            : Component.translatable("message.aerolarge.landing_target.best", bestName, String.format(Locale.ROOT, "%.1f", best));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (best != Double.MAX_VALUE) {
            tag.putDouble("Best", best);
            tag.putString("BestName", bestName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        best = tag.contains("Best") ? tag.getDouble("Best") : Double.MAX_VALUE;
        bestName = tag.getString("BestName");
    }

    @Nullable
    static LandingTargetBlockEntity at(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof LandingTargetBlockEntity target ? target : null;
    }
}
