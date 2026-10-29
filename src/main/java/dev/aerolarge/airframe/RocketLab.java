package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BioreactorBlock;
import dev.aerolarge.block.GalleyUnitBlock;
import dev.aerolarge.block.GalleyUnitBlockEntity;
import dev.aerolarge.block.HabitatFloorBlock;
import dev.aerolarge.block.HabitatGlassBlock;
import dev.aerolarge.block.HabitatHatchBlock;
import dev.aerolarge.block.HabitatHatchBlockEntity;
import dev.aerolarge.block.LabGrowerBlock;
import dev.aerolarge.block.LabInstrumentBlock;
import dev.aerolarge.block.LabPartitionBlock;
import dev.aerolarge.block.LavatoryBasinBlock;
import dev.aerolarge.block.SampleCentrifugeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * The lab, her eighth story, under the observation deck: the glass tank of her tardigrade behind the shaft, lit soft
 * along its top, moss, a pool and pebbles on its floor, a feeding hatch on her right with the creature's vitals over
 * it; lab counters curving round the front, microscopes, a centrifuge, sample racks, jars and scan screens on them,
 * stools before the microscopes and by the tank; on her left the food lab behind its glazed partitions, bioreactors
 * growing steaks, algae columns and hydroponic racks glowing, piped up into the ceiling; on her right the wet bench,
 * a sink, cabinets and lab benches. Laid out by {@link LabPlan} and {@link LabBays}; the hatch keeps the creature,
 * which is made once she stands (see {@link HabitatHatchBlockEntity}). The server config's {@code rocket.lab} leaves
 * the lab out, as {@code rocket.rooms} leaves out every room.
 */
final class RocketLab extends RocketRoom {
    /** Her lab's story, from the entry story up. */
    static final int STORY = 7;
    /** How much moss the wet bench's first cabinet holds, to feed the tardigrade with: a stack of carpets. */
    private static final int FEED = 16;

    RocketLab(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public boolean wanted() {
        return super.wanted() && AerolargeConfig.ROCKET_LAB.get();
    }

    @Override
    public void build() {
        LabPlan.Plan plan = LabPlan.of(room(), floor(), top(), wall(), scatter());
        for (LabPlan.Placed placed : plan.parts()) {
            BlockPos at = placed.at();
            layout.set(at.getX(), at.getY(), at.getZ(), state(placed));
            if (placed.part() == LabPlan.Part.FEED_CABINET) {
                layout.fit(at, RocketLab::stockFeed);
            }
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
            case TANK_GLASS -> AerolargeRegistry.HABITAT_GLASS.get().panes(placed.sides(), row(placed.at().getY()));
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
            case CENTRIFUGE -> AerolargeRegistry.SAMPLE_CENTRIFUGE.get().defaultBlockState().setValue(SampleCentrifugeBlock.FACING, placed.facing());
            case SPECIMENS -> instruments.of(LabInstrumentBlock.Kind.SPECIMENS, placed.facing());
            case BIOREACTOR -> AerolargeRegistry.BIOREACTOR.get().defaultBlockState().setValue(BioreactorBlock.FACING, placed.facing());
            case HYDROPONICS -> AerolargeRegistry.HYDROPONIC_RACK.get().defaultBlockState().setValue(LabGrowerBlock.FACING, placed.facing());
            case ALGAE -> AerolargeRegistry.ALGAE_COLUMN.get().defaultBlockState().setValue(LabGrowerBlock.FACING, placed.facing());
            case TOP -> upperHalf(placed.at());
            case PARTITION -> AerolargeRegistry.LAB_PARTITION.get().of(partitionRow(placed.at().getY()), placed.facing());
            case PIPE -> AerolargeRegistry.LAB_PIPE.get().running(placed.sides());
            case CABINET, FEED_CABINET -> AerolargeRegistry.GALLEY_STOWAGE.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, placed.facing());
            case SINK -> AerolargeRegistry.LAVATORY_BASIN.get().defaultBlockState().setValue(LavatoryBasinBlock.FACING, placed.facing());
        };
    }

    /** The upper half of the two blocks tall machine or sink laid under a block, as its lower half is. */
    private BlockState upperHalf(BlockPos at) {
        return layout.stateAt(at.getX(), at.getY() - 1, at.getZ()).setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
    }

    /** Which row of a partition a row of the room is. */
    private LabPartitionBlock.Row partitionRow(int y) {
        if (y == floor() + 1) {
            return LabPartitionBlock.Row.BOTTOM;
        }
        return y == floor() + LabBays.PARTITION_ROWS ? LabPartitionBlock.Row.TOP : LabPartitionBlock.Row.MIDDLE;
    }

    /** The wet bench's first cabinet, stocked once it stands with moss to feed the tardigrade through its hatch. */
    private static void stockFeed(BlockEntity blockEntity, Rotation rotation) {
        if (blockEntity instanceof GalleyUnitBlockEntity cabinet) {
            cabinet.setItem(0, new ItemStack(Items.MOSS_CARPET, FEED));
        }
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
