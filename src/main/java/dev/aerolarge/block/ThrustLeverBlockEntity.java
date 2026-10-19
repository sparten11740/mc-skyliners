package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where the relays of a thrust lever's engine are, relative to the lever, so the link holds wherever the airliner
 * stands or flies.
 */
public class ThrustLeverBlockEntity extends BlockEntity implements Control {
    private List<BlockPos> relays = List.of();

    public ThrustLeverBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.THRUST_LEVER_ENTITY.get(), pos, state);
    }

    public void configure(List<BlockPos> relays) {
        this.relays = List.copyOf(relays);
        setChanged();
    }

    @Override
    public void resync() {
        drive(ThrustLeverBlock.power(getBlockState()));
    }

    /** Sets every relay of this lever's engine to the signal, which the thrusters next to them pick up. */
    void drive(int power) {
        if (level != null) {
            Relays.drive(level, worldPosition, relays, power);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Relays", Relays.write(relays));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        relays = Relays.read(tag.getList("Relays", Tag.TAG_INT_ARRAY));
    }
}
