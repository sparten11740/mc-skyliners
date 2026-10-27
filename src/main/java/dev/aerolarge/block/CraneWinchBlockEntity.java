package dev.aerolarge.block;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.entity.CraneHook;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The sky crane's winch (see {@link CraneWinchBlock}), on the server: it hangs its hook (see {@link CraneHook}) the
 * first time its lever is put down to lower, and every tick holds it where its rope comes out under her floor,
 * hoisting, holding or lowering it as the lever stands, its drum ratcheting as it turns. It keeps which hook is its
 * own, and who last worked its lever: whoever takes a chest up in its net must be able to break it.
 */
public class CraneWinchBlockEntity extends BlockEntity {
    /** How often the drum's ratchet clicks while it turns, in ticks, and how loud and how high. */
    private static final int RATCHET_TICKS = 3;
    private static final float RATCHET_VOLUME = 0.5F;
    private static final float RATCHET_PITCH = 1.6F;
    /**
     * How long the hook it knows may be missing, in ticks, before it hangs a new one when lowered: the hook's own chunk
     * may load a moment after the winch's.
     */
    private static final int HOOK_MISSING = 100;

    @Nullable
    private UUID hook;
    @Nullable
    private UUID operator;
    /** Ticks the hook it knows has been missing for. */
    private int missing;

    public CraneWinchBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.CRANE_WINCH_ENTITY.get(), pos, state);
    }

    /** Its lever worked by a player, who is now who works it. */
    void workedBy(Player player) {
        operator = player.getUUID();
        setChanged();
    }

    /** Whoever last worked its lever, if they are still about. */
    @Nullable
    public ServerPlayer operator(ServerLevel level) {
        return operator == null ? null : level.getServer().getPlayerList().getPlayer(operator);
    }

    void tick(ServerLevel level) {
        if (!AerolargeConfig.SKY_CRANE.get()) {
            return;
        }
        int notch = getBlockState().getValue(CraneWinchBlock.NOTCH);
        CraneHook held = hook(level);
        if (held == null) {
            missing++;
            if (notch == CraneWinchBlock.LOWER && (hook == null || missing > HOOK_MISSING)) {
                hang(level);
            }
            return;
        }
        missing = 0;
        if (held.hold(level, worldPosition, CraneHook.winchOut(level, worldPosition), notch)) {
            level.setBlock(worldPosition, getBlockState().setValue(CraneWinchBlock.NOTCH, CraneWinchBlock.HOLD), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 1.0F);
        } else if (notch != CraneWinchBlock.HOLD && level.getGameTime() % RATCHET_TICKS == 0) {
            level.playSound(null, worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, RATCHET_VOLUME, RATCHET_PITCH);
        }
    }

    /** Its own hook, if it is loaded. */
    @Nullable
    private CraneHook hook(ServerLevel level) {
        Entity found = hook == null ? null : level.getEntity(hook);
        return found instanceof CraneHook crane && !crane.isRemoved() ? crane : null;
    }

    /** A new hook hung under her floor at the end of its rope's shortest run. */
    private void hang(ServerLevel level) {
        CraneHook hung = CraneHook.hungOn(level, worldPosition, CraneHook.winchOut(level, worldPosition));
        level.addFreshEntity(hung);
        hook = hung.getUUID();
        missing = 0;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (hook != null) {
            tag.putUUID("Hook", hook);
        }
        if (operator != null) {
            tag.putUUID("Operator", operator);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hook = tag.hasUUID("Hook") ? tag.getUUID("Hook") : null;
        operator = tag.hasUUID("Operator") ? tag.getUUID("Operator") : null;
    }
}
