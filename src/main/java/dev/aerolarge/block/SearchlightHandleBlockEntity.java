package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A searchlight handle's block entity: where its searchlight hangs, set as the zeppelin is built (turned as she was
 * placed), and who has taken it, if anyone. While taken, the light swings to wherever its operator looks, turned into
 * the ship's frame, each tick; walking away from the handle lets it go. Who has it is not saved: a handle taken when
 * the world is closed is found let go.
 */
public class SearchlightHandleBlockEntity extends BlockEntity {
    /** How far from the handle its operator's eyes may be while they work it, in blocks. */
    private static final double REACH = 4;

    @Nullable
    private BlockPos light;
    @Nullable
    private UUID operator;

    public SearchlightHandleBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.SEARCHLIGHT_HANDLE_ENTITY.get(), pos, state);
    }

    /** Tells it where its searchlight hangs, from the handle, as the zeppelin is built. */
    public void rig(BlockPos light) {
        this.light = light;
        setChanged();
    }

    /** Used by a player: taken, switching the light on; let go by them; or, sneaking, the light switched off. */
    void use(ServerPlayer player) {
        SearchlightBlockEntity searchlight = searchlight();
        if (searchlight == null || level == null) {
            return;
        }
        if (player.isShiftKeyDown()) {
            operator = null;
            searchlight.light(false);
        } else if (player.getUUID().equals(operator)) {
            operator = null;
        } else {
            operator = player.getUUID();
            searchlight.light(true);
        }
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, operator != null ? 1.2F : 0.9F);
    }

    /** Once a tick on the server: the light swung to where its operator looks, or let go once they are gone. */
    void serverTick(ServerLevel level) {
        if (operator == null) {
            return;
        }
        Player player = level.getPlayerByUUID(operator);
        SearchlightBlockEntity searchlight = searchlight();
        if (player == null || searchlight == null || !atHandle(level, player)) {
            operator = null;
            return;
        }
        searchlight.point(SableFlight.turnToPlot(level, searchlight.getBlockPos(), player.getLookAngle()));
    }

    /** Whether a player is still at the handle, both seen in the world. */
    private boolean atHandle(ServerLevel level, Player player) {
        Vec3 at = SableFlight.toWorld(level, Vec3.atCenterOf(worldPosition));
        return player.isAlive() && at.distanceTo(player.getEyePosition()) <= REACH;
    }

    @Nullable
    private SearchlightBlockEntity searchlight() {
        if (light == null || level == null) {
            return null;
        }
        return level.getBlockEntity(worldPosition.offset(light)) instanceof SearchlightBlockEntity searchlight ? searchlight : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (light != null) {
            tag.put("Light", NbtUtils.writeBlockPos(light));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        light = NbtUtils.readBlockPos(tag, "Light").orElse(null);
    }
}
