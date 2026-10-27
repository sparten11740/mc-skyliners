package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * An engine repeater's block entity: which of the zeppelin's engines it stands by, set as she is built, when its last
 * order came and how long the engine crew leave it for an engineer. Its block shows the order, and whether it is
 * answered (see {@link EngineRepeaterBlock}). The engine follows the control car only while its order is answered: a
 * new order holds it as it was until an engineer answers, or the engine crew do. Rung by hand from the control car's
 * telegraphs, the crew leave it a few seconds for an engineer; rung by the autopilot, they answer it at once, as the
 * autopilot flies the engines by small changes a lag would set swinging.
 */
public class EngineRepeaterBlockEntity extends BlockEntity {
    private static final Loaded<EngineRepeaterBlockEntity> LOADED = new Loaded<>(EngineRepeaterBlockEntity.class);
    /**
     * How long an order waits before the engine crew answer it, rung by hand and rung by the autopilot, and how often
     * it rings meanwhile, in ticks.
     */
    private static final int CREW_ANSWERS_BY_HAND = 60;
    private static final int CREW_ANSWERS_AUTOPILOT = 8;
    private static final int RING_EVERY = 20;
    /**
     * How much nearer to the engine's thrust, as a share of full thrust, another order must be to be rung, so a thrust
     * between two orders does not ring back and forth, as the control car's telegraphs do.
     */
    private static final double RING_MARGIN = 0.05;
    /** How loud its bell is, heard 16 blocks off for each, so from the ground under the zeppelin. */
    private static final float BELL_VOLUME = 3;

    /** The engine it stands by, from 0; less than nothing before the zeppelin is built. */
    private int engine = -1;
    private long ordered;
    private int crewAnswers = CREW_ANSWERS_BY_HAND;

    public EngineRepeaterBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ENGINE_REPEATER_ENTITY.get(), pos, state);
    }

    /** Tells it which engine it stands by, as the zeppelin is built. */
    public void standBy(int engine) {
        this.engine = engine;
        setChanged();
    }

    /** The repeater standing by an engine of the zeppelin whose frame is given; null for none. */
    @Nullable
    public static EngineRepeaterBlockEntity standingBy(Level level, BoundingBox frame, int engine) {
        for (EngineRepeaterBlockEntity repeater : LOADED.in(level)) {
            if (repeater.engine == engine && frame.isInside(repeater.getBlockPos())) {
                return repeater;
            }
        }
        return null;
    }

    /**
     * The engine is asked for a thrust, as a share of full thrust (less than nothing astern), by hand or by the
     * autopilot: rung to the order nearest it once that is nearer by a margin than the one it shows; whether the
     * engine may follow, its order answered.
     */
    public boolean ask(double thrust, boolean byHand) {
        BlockState state = getBlockState();
        TelegraphBlock.Order now = state.getValue(EngineRepeaterBlock.ORDER);
        TelegraphBlock.Order nearest = TelegraphBlock.Order.nearest(thrust);
        if (level != null && Math.abs(thrust - nearest.thrust) + RING_MARGIN < Math.abs(thrust - now.thrust)) {
            level.setBlock(worldPosition, state.setValue(EngineRepeaterBlock.ORDER, nearest).setValue(EngineRepeaterBlock.ANSWERED, false), Block.UPDATE_ALL);
            ordered = level.getGameTime();
            crewAnswers = byHand ? CREW_ANSWERS_BY_HAND : CREW_ANSWERS_AUTOPILOT;
            ring(level, worldPosition, nearest);
            setChanged();
            return false;
        }
        return state.getValue(EngineRepeaterBlock.ANSWERED);
    }

    /**
     * Once a tick on the server: an order left unanswered rings now and then, and the engine crew answer it in the end.
     */
    void serverTick(ServerLevel level) {
        BlockState state = getBlockState();
        if (state.getValue(EngineRepeaterBlock.ANSWERED)) {
            return;
        }
        long waited = level.getGameTime() - ordered;
        if (waited >= crewAnswers || waited < 0) {
            EngineRepeaterBlock.answer(level, worldPosition, state);
        } else if (waited > 0 && waited % RING_EVERY == 0) {
            ring(level, worldPosition, state.getValue(EngineRepeaterBlock.ORDER));
        }
    }

    /** Its gong, struck twice for an order as the control car's telegraph is, a little higher the faster the order. */
    static void ring(Level level, BlockPos pos, TelegraphBlock.Order order) {
        level.playSound(null, pos, AerolargeRegistry.TELEGRAPH_RING.get(), SoundSource.BLOCKS, BELL_VOLUME, 0.9F + 0.04F * order.ordinal());
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
        tag.putInt("Engine", engine);
        tag.putLong("Ordered", ordered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        engine = tag.contains("Engine") ? tag.getInt("Engine") : -1;
        ordered = tag.getLong("Ordered");
    }
}
