package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.GalleyUnitBlock;
import dev.aerolarge.block.HabitatFloorBlock;
import dev.aerolarge.block.HabitatGlassBlock;
import dev.aerolarge.block.HabitatHatchBlock;
import dev.aerolarge.block.HabitatHatchBlockEntity;
import dev.aerolarge.block.LabInstrumentBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The lab, her eighth story, under the observation deck: the glass tank of her tardigrade behind the shaft, lit soft
 * along its top, moss, a pool and pebbles on its floor, a feeding hatch on her right with the creature's vitals over
 * it; lab counters curving round the front, microscopes, sample racks and scan screens on them, stools before the
 * microscopes and by the tank. Laid out by {@link LabPlan}; the hatch keeps the creature, which is made once she
 * stands (see {@link HabitatHatchBlockEntity}). The server config's {@code rocket.lab} leaves the lab out, as
 * {@code rocket.rooms} leaves out every room.
 */
final class RocketLab extends RocketRoom {
    /** Her lab's story, from the entry story up. */
    static final int STORY = 7;

    RocketLab(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public boolean wanted() {
        return super.wanted() && AerolargeConfig.ROCKET_LAB.get();
    }

    @Override
    public void build() {
        LabPlan.Plan plan = LabPlan.of(room(), floor(), top(), wall());
        for (LabPlan.Placed placed : plan.parts()) {
            BlockPos at = placed.at();
            layout.set(at.getX(), at.getY(), at.getZ(), state(placed));
        }
        if (plan.hatch() != null && plan.track() != null) {
            keep(plan.hatch(), plan.track());
        }
    }

    /** The room as the plan sees it: her blocks, the room's rule for furniture, and the ring along the skin. */
    private LabPlan.Room room() {
        return new LabPlan.Room() {
            @Override
            public boolean open(int x, int y, int z) {
                return layout.isOpen(x, y, z);
            }

            @Override
            public boolean free(int x, int y, int z) {
                return RocketLab.this.free(x, y, z);
            }

            @Override
            public boolean within(int x, int y, int z) {
                return inRoom(x, y, z, 0);
            }
        };
    }

    /** The block a part of the plan is. */
    private BlockState state(LabPlan.Placed placed) {
        LabInstrumentBlock instruments = AerolargeRegistry.LAB_INSTRUMENT.get();
        HabitatFloorBlock patches = AerolargeRegistry.HABITAT_FLOOR.get();
        return switch (placed.part()) {
            case TANK_GLASS -> AerolargeRegistry.HABITAT_GLASS.get().panes(placed.panes(), row(placed.at().getY()));
            case HATCH -> AerolargeRegistry.HABITAT_HATCH.get().defaultBlockState().setValue(HabitatHatchBlock.FACING, placed.facing());
            case VITALS -> instruments.of(LabInstrumentBlock.Kind.VITALS, placed.facing());
            case MICROSCOPE -> instruments.of(LabInstrumentBlock.Kind.MICROSCOPE, placed.facing());
            case SAMPLES -> instruments.of(LabInstrumentBlock.Kind.SAMPLES, placed.facing());
            case SCAN -> instruments.of(LabInstrumentBlock.Kind.SCAN, placed.facing());
            case MOSS -> Blocks.MOSS_CARPET.defaultBlockState();
            case POOL -> patches.of(HabitatFloorBlock.Kind.POOL);
            case PEBBLES -> patches.of(HabitatFloorBlock.Kind.PEBBLES);
            case ROCK -> (Math.floorMod(placed.at().getX() + placed.at().getZ(), 2) == 0 ? Blocks.TUFF : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
            case BENCH -> AerolargeRegistry.GALLEY_WORKTOP.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, placed.facing());
            case STOOL -> AerolargeRegistry.BAR_STOOL.get().defaultBlockState();
        };
    }

    /** Which row of the tank's glass a row of the room is. */
    private HabitatGlassBlock.Row row(int y) {
        if (y == floor() + 1) {
            return HabitatGlassBlock.Row.FLOOR;
        }
        return y == top() ? HabitatGlassBlock.Row.TOP : HabitatGlassBlock.Row.MIDDLE;
    }

    /**
     * Has the hatch keep the creature once she stands: its line and where it turns, as offsets from the hatch's middle.
     */
    private void keep(BlockPos hatch, LabPlan.Track track) {
        Vec3 middle = Vec3.atCenterOf(hatch);
        Vec3 from = track.from().subtract(middle);
        Vec3 to = track.to().subtract(middle);
        Vec3 turn = track.turn() == null ? null : track.turn().subtract(middle);
        layout.fit(hatch, (blockEntity, rotation) -> {
            if (blockEntity instanceof HabitatHatchBlockEntity keeper) {
                keeper.configure(from, to, turn, rotation);
            }
        });
    }
}
