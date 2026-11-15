package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where the relays of a thrust lever's engine are, relative to the lever and turned with it (see {@link Turning}), so
 * the link holds wherever the airliner stands or flies, whichever way it is set down.
 */
public class ThrustLeverBlockEntity extends BlockEntity implements Control {
    private List<BlockPos> relays = List.of();
    /** Whether its links have been looked at since it was loaded, to mend them (see {@link Turning#mend}). */
    private boolean linksChecked;

    public ThrustLeverBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.THRUST_LEVER_ENTITY.get(), pos, state);
    }

    public void configure(List<BlockPos> relays) {
        this.relays = List.copyOf(relays);
        linksChecked = true;
        setChanged();
    }

    @Override
    public void resync() {
        drive(ThrustLeverBlock.power(getBlockState()));
    }

    /**
     * Sets every relay of this lever's engine to the signal, which the thrusters next to them pick up; aboard an
     * assembled airliner, her autopilot winds her engines up to it instead (see {@link AutopilotBlockEntity#spools}).
     */
    void drive(int power) {
        if (level != null && !AutopilotBlockEntity.spools(level, worldPosition)) {
            mendLinks();
            Relays.drive(level, worldPosition, relays, power);
        }
    }

    /** Once after loading, as the lever is first worked, mends its links to the relays if they lead nowhere. */
    private void mendLinks() {
        if (!linksChecked && level != null) {
            linksChecked = true;
            Turning.mend(level, worldPosition, relays, Relays::isRelay, this::turnLinks);
        }
    }

    /** Turns the links to the relays, mending them. */
    private void turnLinks(Rotation turn) {
        relays = Turning.turn(relays, turn);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Relays", Relays.write(relays));
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        relays = Turning.turn(Relays.read(tag.getList("Relays", Tag.TAG_INT_ARRAY)), Turning.since(tag, getBlockState()));
    }
}
