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
 * Which engine a thrust lever works and where that engine's relays are, relative to the lever, so the link holds
 * wherever the airliner stands or flies.
 */
public class ThrustLeverBlockEntity extends BlockEntity implements Control {
    private int engine;
    private List<BlockPos> relays = List.of();

    public ThrustLeverBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.THRUST_LEVER_ENTITY.get(), pos, state);
    }

    public void configure(int engine, List<BlockPos> relays) {
        this.engine = engine;
        this.relays = List.copyOf(relays);
        setChanged();
    }

    /** The engine's number, 1 to 4 from the port wingtip to the starboard one, as on the real aircraft. */
    public int engine() {
        return engine;
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
        tag.putInt("Engine", engine);
        tag.put("Relays", Relays.write(relays));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        engine = tag.getInt("Engine");
        relays = Relays.read(tag.getList("Relays", Tag.TAG_INT_ARRAY));
    }
}
