package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.Windscreen;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the windscreen's renderer needs to draw the glass: the airliner's scale and this block's position in the
 * layout (nose north, before it was lifted onto its wheels), from which every point of the glass is placed relative
 * to it. Both are sent to clients, which build the mesh once and keep it.
 */
public class CockpitWindscreenBlockEntity extends BlockEntity {
    private double scale;
    private BlockPos anchor = BlockPos.ZERO;
    @Nullable
    private Windscreen.Mesh mesh;

    public CockpitWindscreenBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.COCKPIT_WINDSCREEN_ENTITY.get(), pos, state);
    }

    public void configure(double scale, BlockPos anchor) {
        this.scale = scale;
        this.anchor = anchor;
        this.mesh = null;
        setChanged();
    }

    /** The glass to draw, or null until the block knows its airliner. */
    @Nullable
    public Windscreen.Mesh mesh() {
        if (mesh == null && scale > 0) {
            mesh = Windscreen.mesh(scale, anchor.getX(), anchor.getY(), anchor.getZ());
        }
        return mesh;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("Scale", scale);
        tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        scale = tag.getDouble("Scale");
        anchor = NbtUtils.readBlockPos(tag, "Anchor").orElse(BlockPos.ZERO);
        mesh = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
