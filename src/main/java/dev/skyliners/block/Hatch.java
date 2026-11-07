package dev.skyliners.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks of a ship that make a hatch, taken away as it opens and put back as it shuts, each kept as where it is
 * from what keeps it and what it is: the skin over a zeppelin's or a rocket's airstairs (see {@link BoardingAirstairs})
 * and the floor and belly over the freighter's ramp (see {@link BoardingRamp}).
 */
final class Hatch {
    private final List<AirstairControllerBlockEntity.Step> parts;

    Hatch(List<AirstairControllerBlockEntity.Step> parts) {
        this.parts = List.copyOf(parts);
    }

    /** Takes its blocks away, from what keeps it at a place, where they are still its own. */
    void open(ServerLevel level, BlockPos keeper) {
        for (AirstairControllerBlockEntity.Step part : parts) {
            BlockPos at = keeper.offset(part.offset());
            if (level.getBlockState(at).is(part.state().getBlock())) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Puts its blocks back as they were, where nothing else has taken their place. */
    void close(ServerLevel level, BlockPos keeper) {
        for (AirstairControllerBlockEntity.Step part : parts) {
            BlockPos at = keeper.offset(part.offset());
            BlockState there = level.getBlockState(at);
            if (there.isAir() || there.canBeReplaced()) {
                level.setBlock(at, part.state(), Block.UPDATE_ALL);
            }
        }
    }

    /** Whether a block is one of its own, kept from a place. */
    boolean covers(BlockPos keeper, BlockPos cell) {
        BlockPos from = cell.subtract(keeper);
        return parts.stream().anyMatch(part -> part.offset().equals(from));
    }

    /** Saves its blocks under "Hatch", each block where it is from what keeps it and what it is. */
    void save(CompoundTag tag) {
        ListTag saved = new ListTag();
        for (AirstairControllerBlockEntity.Step part : parts) {
            CompoundTag entry = new CompoundTag();
            entry.put("Offset", NbtUtils.writeBlockPos(part.offset()));
            entry.put("State", NbtUtils.writeBlockState(part.state()));
            saved.add(entry);
        }
        tag.put("Hatch", saved);
    }

    /** A hatch as saved, turned by the quarter turn what keeps it has turned since (see {@link Turning}). */
    static Hatch load(CompoundTag tag, Rotation turn, HolderLookup.Provider registries) {
        List<AirstairControllerBlockEntity.Step> parts = new ArrayList<>();
        for (Tag entry : tag.getList("Hatch", Tag.TAG_COMPOUND)) {
            CompoundTag part = (CompoundTag) entry;
            parts.add(new AirstairControllerBlockEntity.Step(NbtUtils.readBlockPos(part, "Offset").orElse(BlockPos.ZERO),
                NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), part.getCompound("State"))).turned(turn));
        }
        return new Hatch(parts);
    }
}
