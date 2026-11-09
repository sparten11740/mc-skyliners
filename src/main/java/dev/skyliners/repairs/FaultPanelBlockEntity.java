package dev.skyliners.repairs;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.block.Loaded;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A fault spot's block entity (see {@link FaultPanelBlock}): nothing to save, as whether it is at fault is its block
 * state, but kept track of on the server, so a repair board finds the spots round it to set off, and on clients, so
 * her console's scanner shows those at fault. At fault, it crackles or hisses now and then, on the server, loud
 * enough to be heard across her deck, so a child can track it down by ear, and not at all while she is parked; ticking
 * only while at fault.
 */
public class FaultPanelBlockEntity extends BlockEntity {
    private static final Loaded<FaultPanelBlockEntity> LOADED = new Loaded<>(FaultPanelBlockEntity.class, true);
    /**
     * How often a spot at fault crackles or hisses, in ticks: every quarter of a minute or so, give or take a little so
     * two never keep step, enough to track it down by without it going on and on; and how loud, which sets how far off
     * it is heard: sixteen blocks for each, so across her deck from wall to wall.
     */
    private static final int NOISE_EVERY = 300;
    private static final int NOISE_SPREAD = 100;
    private static final float NOISE_VOLUME = 2.5F;
    /** How loud the first burst of a fault is, as it breaks down, heard further still. */
    private static final float BREAK_VOLUME = 3.0F;

    /** On the server: when it next crackles or hisses, in game time. Not saved: a spot loaded again soon starts. */
    private long nextNoise;

    public FaultPanelBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.FAULT_PANEL_ENTITY.get(), pos, state);
    }

    /** Every fault spot loaded in a level, on the server or a client. */
    public static List<FaultPanelBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** The fault spots loaded within so many blocks of a block in its level, whether at fault or not. */
    public static List<FaultPanelBlockEntity> near(Level level, BlockPos pos, double reach) {
        List<FaultPanelBlockEntity> found = new ArrayList<>();
        for (FaultPanelBlockEntity panel : LOADED.in(level)) {
            if (panel.getBlockPos().closerThan(pos, reach)) {
                found.add(panel);
            }
        }
        return found;
    }

    /** Whether it is at fault now. */
    public boolean faulty() {
        return getBlockState().getValue(FaultPanelBlock.FAULTY);
    }

    /** Sets it off, on the server, if not at fault already: its lamp, its sparks or steam and a first loud burst. */
    void breakDown(ServerLevel level) {
        BlockState state = getBlockState();
        if (!state.getValue(FaultPanelBlock.FAULTY) && state.getBlock() instanceof FaultPanelBlock block) {
            level.setBlock(worldPosition, state.setValue(FaultPanelBlock.FAULTY, true), Block.UPDATE_ALL);
            brokeDown(level, worldPosition, block.kind());
        }
    }

    /** The first loud burst of a fault spot breaking down, on the server. */
    static void brokeDown(ServerLevel level, BlockPos pos, FaultPanelBlock.Kind kind) {
        level.playSound(null, pos, noise(kind), SoundSource.BLOCKS, BREAK_VOLUME, kind == FaultPanelBlock.Kind.PANEL ? 0.8F : 0.9F);
    }

    /** At fault, each tick on the server: its crackle or hiss, now and then, and none while she is parked. */
    void tick(ServerLevel level, FaultPanelBlock.Kind kind) {
        long now = level.getGameTime();
        if (now < nextNoise) {
            return;
        }
        nextNoise = now + NOISE_EVERY + level.getRandom().nextInt(NOISE_SPREAD);
        if (parked(level)) {
            return;
        }
        float pitch = 0.8F + level.getRandom().nextFloat() * 0.4F;
        level.playSound(null, worldPosition, noise(kind), SoundSource.BLOCKS, NOISE_VOLUME, pitch);
    }

    /** Whether it is aboard a freighter switched off; put up by hand it is aboard none, and goes on as ever. */
    private boolean parked(Level level) {
        AutopilotBlockEntity freighter = AutopilotBlockEntity.framedBy(level, worldPosition, Craft.FREIGHTER);
        return freighter != null && freighter.phase() == AutopilotBlockEntity.Phase.OFF;
    }

    /** The sound a fault spot makes at fault: the panel's electric crackle, or the pipe's hiss of steam. */
    private static SoundEvent noise(FaultPanelBlock.Kind kind) {
        return kind == FaultPanelBlock.Kind.PANEL ? SkylinersRegistry.FAULT_CRACKLE.get() : SkylinersRegistry.FAULT_HISS.get();
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
}
