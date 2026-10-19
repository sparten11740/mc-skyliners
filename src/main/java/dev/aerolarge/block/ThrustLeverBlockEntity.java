package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which engine a thrust lever works and where that engine's relays are, relative to the lever, so the link holds
 * wherever the airliner stands or flies.
 */
public class ThrustLeverBlockEntity extends BlockEntity {
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

    /** Sets every relay of this lever's engine to the signal, which the thrusters next to them pick up. */
    void drive(int power) {
        if (level == null) {
            return;
        }
        for (BlockPos offset : relays) {
            BlockPos pos = worldPosition.offset(offset);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ThrustRelayBlock && state.getValue(ThrustRelayBlock.POWER) != power) {
                level.setBlock(pos, state.setValue(ThrustRelayBlock.POWER, power), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Engine", engine);
        ListTag list = new ListTag();
        relays.forEach(offset -> list.add(NbtUtils.writeBlockPos(offset)));
        tag.put("Relays", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        engine = tag.getInt("Engine");
        List<BlockPos> loaded = new ArrayList<>();
        for (Tag entry : tag.getList("Relays", Tag.TAG_INT_ARRAY)) {
            int[] xyz = ((IntArrayTag) entry).getAsIntArray();
            if (xyz.length == 3) {
                loaded.add(new BlockPos(xyz[0], xyz[1], xyz[2]));
            }
        }
        relays = List.copyOf(loaded);
    }
}
