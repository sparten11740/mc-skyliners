package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A sprinkler's block entity: until when it sprays, on the server, so it drops its head again on time even with its
 * valve gone. While it sprays it waters what is round it once a second, wetting dry soil and bringing on what grows
 * there now and then by a stage, a sparkle of green showing where; on the client its jet sweeps round by the client's
 * own count of ticks, a stream of drops thrown out over the beds that splash where they land. The sprinklers loaded are
 * kept track of, so a valve finds the ones it reaches without searching the world.
 */
public class SprinklerBlockEntity extends BlockEntity {
    private static final Loaded<SprinklerBlockEntity> LOADED = new Loaded<>(SprinklerBlockEntity.class);
    /** How far round it a sprinkler waters, in blocks each way, and how many rows up: its bed and what grows in it. */
    private static final int REACH = 4;
    private static final int UP = 1;
    /** How often it waters while it sprays, in ticks, and one plant in how many it brings on a stage each time. */
    private static final int WATER_EVERY = 20;
    private static final int GROW_ONE_IN = 5;
    /** How many green sparkles show where a plant comes on, and how far they spread, in blocks. */
    private static final int SPARKLES = 4;
    private static final double SPARKLE_SPREAD = 0.3;
    /**
     * The jet: how high over its block the popped-up head's nozzle is, in blocks; how far the jet carries and how high
     * over the nozzle it arcs at most, in blocks; how far round it sweeps each tick, in radians, a turn in four
     * seconds; and how many drops it throws a tick.
     */
    private static final double THROW = 3.5;
    private static final double ARC = 1.1;
    /**
     * The popped-up head's middle over its block, the reach of its arm out to the nozzle's tip, in blocks, and how
     * steeply the jet leaves the nozzle, in radians: as steeply as its arc rises at its start, so the arm points the
     * way the water goes.
     */
    public static final double HEAD = 1.25;
    public static final double NOZZLE_REACH = 0.38;
    public static final double LAUNCH = Math.atan(4 * ARC / THROW);
    private static final double SWEEP = 2 * Math.PI / 80;
    private static final int DROPS = 3;
    /** How fast the spit at the nozzle flies out along the jet, in blocks a tick. */
    private static final double SPIT = 0.18;
    /** How far apart the jets of two sprinklers a block apart point, in radians, so no two sweep round in step. */
    private static final double OUT_OF_STEP = 1.3;

    /** The game time its spray stops; none at or before it. */
    private long until;
    /**
     * How many ticks it has sprayed on this client, the clock its jet sweeps by. The game time will not do: the server
     * sets a client's copy of it once a second, so wherever the server runs behind, the jet would snap back.
     */
    private long sweptTicks;

    public SprinklerBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.SPRINKLER_ENTITY.get(), pos, state);
    }

    /**
     * The sprinklers loaded in a level so many blocks each way from a spot at most, and so many rows over or under it,
     * nearest first.
     */
    public static List<SprinklerBlockEntity> near(Level level, BlockPos at, int reach, int rows) {
        return LOADED.in(level).stream()
            .filter(sprinkler -> within(sprinkler.getBlockPos(), at, reach, rows))
            .sorted(Comparator.comparingDouble(sprinkler -> sprinkler.getBlockPos().distSqr(at)))
            .toList();
    }

    private static boolean within(BlockPos pos, BlockPos at, int reach, int rows) {
        return Math.abs(pos.getX() - at.getX()) <= reach && Math.abs(pos.getZ() - at.getZ()) <= reach && Math.abs(pos.getY() - at.getY()) <= rows;
    }

    /** Sprays until the game time given, its head popped up for every client to see. */
    public void spray(ServerLevel level, long until) {
        this.until = until;
        setChanged();
        if (!getBlockState().getValue(SprinklerBlock.SPRAYING)) {
            level.setBlock(worldPosition, getBlockState().setValue(SprinklerBlock.SPRAYING, true), Block.UPDATE_CLIENTS);
        }
    }

    /** Once a tick on the server while it sprays: what is round it watered once a second, and its head down on time. */
    void serverTick(ServerLevel level, BlockState state) {
        if (!state.getValue(SprinklerBlock.SPRAYING)) {
            return;
        }
        long left = until - level.getGameTime();
        if (left <= 0) {
            level.setBlock(worldPosition, state.setValue(SprinklerBlock.SPRAYING, false), Block.UPDATE_CLIENTS);
        } else if (left % WATER_EVERY == 0) {
            water(level);
        }
    }

    /** Waters the bed round it: dry soil wetted, and now and then a plant brought on a stage. */
    private void water(ServerLevel level) {
        BlockPos from = worldPosition.offset(-REACH, 0, -REACH);
        BlockPos to = worldPosition.offset(REACH, UP, REACH);
        for (BlockPos at : BlockPos.betweenClosed(from, to)) {
            waterAt(level, at.immutable());
        }
    }

    private static void waterAt(ServerLevel level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        if (state.getBlock() instanceof FarmBlock && state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
            level.setBlock(at, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            return;
        }
        BlockState grown = grown(state);
        if (grown != null && level.getRandom().nextInt(GROW_ONE_IN) == 0) {
            level.setBlock(at, grown, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, SPARKLES, SPARKLE_SPREAD,
                SPARKLE_SPREAD, SPARKLE_SPREAD, 0);
        }
    }

    /**
     * What a plant becomes a stage on: a crop, a pumpkin's or melon's vine, or a berry bush; null for anything else, or
     * for one fully grown.
     */
    @Nullable
    private static BlockState grown(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state) ? null : crop.getStateForAge(crop.getAge(state) + 1);
        }
        if (state.getBlock() instanceof StemBlock) {
            int age = state.getValue(StemBlock.AGE);
            return age < StemBlock.MAX_AGE ? state.setValue(StemBlock.AGE, age + 1) : null;
        }
        if (state.getBlock() instanceof SweetBerryBushBlock) {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            return age < SweetBerryBushBlock.MAX_AGE ? state.setValue(SweetBerryBushBlock.AGE, age + 1) : null;
        }
        return null;
    }

    /**
     * Once a tick on the client while it sprays: the jet sweeping round from the popped-up head, drops thrown out along
     * it that fall onto the beds and splash, and a spit of water at the nozzle.
     */
    void clientTick(Level level, BlockState state) {
        if (!state.getValue(SprinklerBlock.SPRAYING)) {
            return;
        }
        sweptTicks++;
        RandomSource random = level.getRandom();
        double angle = jetAngle(0);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double tip = NOZZLE_REACH * Math.cos(LAUNCH);
        double x = worldPosition.getX() + 0.5 + cos * tip;
        double y = worldPosition.getY() + HEAD + NOZZLE_REACH * Math.sin(LAUNCH);
        double z = worldPosition.getZ() + 0.5 + sin * tip;
        for (int i = 0; i < DROPS; i++) {
            double out = THROW * random.nextDouble();
            double rise = 4 * ARC * (out / THROW) * (1 - out / THROW);
            level.addParticle(ParticleTypes.FALLING_WATER, x + cos * out, y + rise, z + sin * out, 0, 0, 0);
        }
        level.addParticle(ParticleTypes.SPLASH, x, y, z, cos * SPIT, 0, sin * SPIT);
    }

    /**
     * Which way its jet points so far through a tick, in radians round from east toward south: sweeping round, each
     * sprinkler out of step with the next. Its renderer turns the nozzle by it too, in a float, so it is kept within
     * one turn: aboard an assembled ship, whose blocks lie millions of blocks out, a float would hold it only to within
     * radians.
     */
    public double jetAngle(float partialTick) {
        return ((sweptTicks + partialTick) * SWEEP + (worldPosition.getX() + 2 * worldPosition.getZ()) * OUT_OF_STEP) % (2 * Math.PI);
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("SprayUntil", until);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        until = tag.contains("SprayUntil") ? tag.getLong("SprayUntil") : 0;
    }
}
