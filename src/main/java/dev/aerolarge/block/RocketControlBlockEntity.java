package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.RocketControls;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What one of the controls on a rocket's centre console works (see {@link RocketControlBlock}): her autopilot, as an
 * offset from the control turned with it (see {@link Turning}), set as she is built; a control put down by hand links
 * itself to the nearest autopilot within the flight deck's reach as it is first used or looked at. Every {@link
 * #LOOK_TICKS} ticks it reads what her autopilot shows and has its block show it, setting its block only when its lamps
 * change, so the clients are sent only that.
 */
public class RocketControlBlockEntity extends BlockEntity {
    /** How often it reads what her autopilot shows, in ticks. */
    private static final int LOOK_TICKS = 4;
    /**
     * The least time between two presses that each count, in ticks: longer than the four ticks the game repeats a held
     * use key at, so holding it down never flips a switch back and forth.
     */
    private static final int PRESS_GAP = 6;
    /** How long a blink lasts, in ticks: a refusal's red, or the abort handle pulled out. */
    private static final int BLINK_TICKS = 8;
    /** How loud and how low the note of a control linked to no rocket's autopilot is. */
    private static final float UNLINKED_VOLUME = 0.7F;
    private static final float UNLINKED_PITCH = 0.5F;
    /** Long before any game time: never. */
    private static final long NEVER = Long.MIN_VALUE / 2;

    /** Where her autopilot is, from this block; null until linked. */
    @Nullable
    private BlockPos autopilot;
    /** Whether its link has been looked at since it was loaded, to mend it (see {@link Turning#mend}). */
    private boolean linkChecked;
    /** When it was last pressed, and until when it blinks, in game time; neither saved. */
    private long lastPress = NEVER;
    private long blinkUntil = NEVER;

    public RocketControlBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ROCKET_CONTROL_ENTITY.get(), pos, state);
    }

    /** Links the control to her autopilot, as an offset from the control, as she is built. */
    public void link(BlockPos offset) {
        autopilot = offset;
        linkChecked = true;
        setChanged();
    }

    /**
     * Her autopilot, if it is there; an unlinked control links itself to the nearest one first, if there is one, on the
     * server.
     */
    @Nullable
    private AutopilotBlockEntity autopilot(ServerLevel level) {
        if (autopilot == null) {
            AutopilotBlockEntity nearest = AutopilotBlockEntity.nearest(level, worldPosition);
            if (nearest != null) {
                link(nearest.getBlockPos().subtract(worldPosition));
            }
            return nearest;
        }
        mendLink(level);
        BlockPos at = worldPosition.offset(autopilot);
        return level.isLoaded(at) && level.getBlockEntity(at) instanceof AutopilotBlockEntity found ? found : null;
    }

    /** Once after loading, mends its link to her autopilot if it leads nowhere. */
    private void mendLink(ServerLevel level) {
        if (!linkChecked && autopilot != null) {
            linkChecked = true;
            Turning.mend(level, worldPosition, List.of(autopilot), state -> state.is(AerolargeRegistry.AUTOPILOT.get()), this::turnLink);
        }
    }

    private void turnLink(Rotation turn) {
        autopilot = Turning.turn(autopilot, turn);
        setChanged();
    }

    /** Used by a player, on the server: what its block does with a use, unless it comes too soon after the last. */
    void press(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        boolean held = now - lastPress < PRESS_GAP;
        lastPress = now;
        if (!held && getBlockState().getBlock() instanceof RocketControlBlock block) {
            block.use(level, worldPosition, getBlockState(), this, player);
        }
    }

    /**
     * Asks her autopilot to work its control for a player, which answers them (see
     * {@link AutopilotBlockEntity#rocketControl}); linked to none, a low note and why. It blinks as its block has it,
     * and shows what it does at once. Whether it was taken.
     */
    boolean ask(ServerLevel level, ServerPlayer player) {
        if (!(getBlockState().getBlock() instanceof RocketControlBlock block)) {
            return false;
        }
        AutopilotBlockEntity linked = autopilot(level);
        boolean taken = linked != null && linked.rocketControl(block.control(), player);
        if (linked == null) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, UNLINKED_VOLUME, UNLINKED_PITCH);
            player.displayClientMessage(Component.translatable("message.aerolarge.rocket_controls.no_autopilot"), true);
        }
        if (block.blinksAfter(taken)) {
            blinkUntil = level.getGameTime() + BLINK_TICKS;
        }
        refresh(level);
        return taken;
    }

    /** Every {@link #LOOK_TICKS}, on the server: shows what her autopilot shows. */
    void tick(ServerLevel level) {
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), LOOK_TICKS) == 0) {
            refresh(level);
        }
    }

    /** Its block shows what her autopilot shows now, set only if that changes it, with the sound of the change. */
    private void refresh(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof RocketControlBlock block)) {
            return;
        }
        AutopilotBlockEntity linked = autopilot(level);
        RocketControls.Panel panel = linked != null ? linked.rocketPanel() : RocketControls.Panel.NONE;
        BlockState shown = block.shown(state, panel, level.getGameTime() < blinkUntil);
        if (shown != state) {
            level.setBlock(worldPosition, shown, Block.UPDATE_CLIENTS);
            block.changed(level, worldPosition, state, shown);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (autopilot != null) {
            tag.putLong("Autopilot", autopilot.asLong());
        }
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        autopilot = tag.contains("Autopilot") ? Turning.turn(BlockPos.of(tag.getLong("Autopilot")), Turning.since(tag, getBlockState())) : null;
    }
}
