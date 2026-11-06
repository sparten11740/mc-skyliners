package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.OvalWindowBlock;
import dev.skyliners.block.ShellBlock;
import dev.skyliners.block.ShellGeometry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A ship cut into blocks round a curved surface of its own before it is put anywhere (see {@link Layout}), as the
 * rocket and the freighter are: the blocks laid so far by position in the layout's own frame, the block entities to set
 * up once they stand, and the air inside the hull, worked out from its surface as it is handed over rather than kept,
 * so whatever stood there is cleared first. Each ship says where its surface is (see {@link SurfaceCut}), the box its
 * air is looked for in and how far back from its frame's origin its front stands, which is where it is handed over
 * from.
 */
abstract class HullLayout implements Layout {
    /** Every block of the ship that is not the air inside its hull, by position in its own frame. */
    final Map<BlockPos, BlockState> cells = new HashMap<>();
    private final Map<BlockPos, Fitting> fittings = new HashMap<>();
    /** How many blocks of air {@link #inOrder} hands over, found once everything else stands. */
    private int air = -1;

    /** The hull's surface in blocks, as its skin is cut by it. */
    abstract SurfaceCut.Surface surface();

    /** The box, in the layout's frame, that the air inside the hull lies within. */
    abstract Box airBox();

    /** How far back from the layout frame's origin, in blocks, its frontmost block stands: where it is handed over. */
    abstract int handedBack();

    /** A box of blocks, from its lowest corner to its highest, both inside it. */
    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    void set(int x, int y, int z, BlockState state) {
        cells.put(new BlockPos(x, y, z), state);
    }

    BlockState stateAt(int x, int y, int z) {
        return cells.getOrDefault(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
    }

    /** Whether nothing of the ship stands at the block yet: inside the hull, its open air. */
    boolean isOpen(int x, int y, int z) {
        return !cells.containsKey(new BlockPos(x, y, z));
    }

    /** Whether a block's middle lies inside the hull, a skin's thickness in from its surface. */
    boolean inside(int x, int y, int z) {
        return surface().depth(x, y + 0.5, z) < -1;
    }

    /** Whether a block's middle lies at least so many blocks in from the hull's surface. */
    boolean inFrom(int x, int y, int z, double blocks) {
        return surface().depth(x, y + 0.5, z) < -blocks;
    }

    /**
     * Whether a block lies within the hull's surface at all, its skin included, not out past it as the step before a
     * door does; {@link #inside(int, int, int)} asks for a skin's thickness more.
     */
    boolean withinSurface(BlockPos at) {
        return surface().depth(at.getX(), at.getY() + 0.5, at.getZ()) < 0;
    }

    /** Sets up the block entity at a layout position once it stands, as the fitting says. */
    void fit(BlockPos at, Fitting fitting) {
        fittings.put(at, fitting);
    }

    /** Whether a block is the skin: the cover, cut to the curve or whole, or a window in it. */
    static boolean skin(BlockState state) {
        return state.getBlock() instanceof ShellBlock || state.getBlock() instanceof OvalWindowBlock;
    }

    /**
     * Whether a room sees a block of the cover: on a side, the block there in the hull and open or not filling its place
     * (an LED strip, a light, a stool), and not the skin's own glass; on no side given, on any side.
     */
    boolean seenFromInside(BlockPos at, @Nullable Direction side) {
        if (side == null) {
            for (Direction any : Direction.values()) {
                if (seenFromInside(at, any)) {
                    return true;
                }
            }
            return false;
        }
        BlockPos next = at.relative(side);
        if (surface().depth(next.getX(), next.getY() + 0.5, next.getZ()) >= 0) {
            return false;
        }
        if (isOpen(next.getX(), next.getY(), next.getZ())) {
            return true;
        }
        BlockState there = stateAt(next.getX(), next.getY(), next.getZ());
        return !there.canOcclude() && !skin(there);
    }

    /**
     * A block of the skin turned to glass: cut to the surface as the cover there was, so it sits flush with the cover
     * round it; or left out where it is a whole block behind others, touching the sky on no side, so the glass is one
     * pane thick and shows no darker doubled pane.
     */
    void glaze(int x, int y, int z, BlockState glass) {
        int shape = SurfaceCut.shape(surface(), x - 0.5, y, z - 0.5);
        if (shape == ShellGeometry.WHOLE && !touchesSky(x, y, z)) {
            cells.remove(new BlockPos(x, y, z));
            return;
        }
        set(x, y, z, shape == ShellGeometry.NONE ? glass : ShellBlock.withShape(glass, shape));
    }

    /** Whether a block has the sky beside it on any side. */
    boolean touchesSky(int x, int y, int z) {
        for (Direction side : Direction.values()) {
            if (sky(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a block lies wholly outside the surface, in the sky. */
    private boolean sky(int x, int y, int z) {
        return SurfaceCut.shape(surface(), x - 0.5, y, z - 0.5) == ShellGeometry.NONE && surface().depth(x, y + 0.5, z) > 0;
    }

    /**
     * A creative thruster at a block pushing the way given, at the configured setting with the flames given, and the
     * relay that drives it beside it, on its far side from its nozzle; where the relay stands.
     */
    BlockPos thruster(BlockState engine, int x, int y, int z, Direction pushes, EngineFlames flames) {
        set(x, y, z, AirframePalette.with(engine, "facing", pushes.getSerializedName()));
        fittings.put(new BlockPos(x, y, z), Thrusters.thrust(flames, 1));
        BlockPos relay = new BlockPos(x, y, z).relative(pushes);
        cells.put(relay, SkylinersRegistry.THRUST_RELAY.get().defaultBlockState());
        return relay;
    }

    /** Positions in the layout as offsets from a block, turned with the ship. */
    static List<BlockPos> offsets(BlockPos from, List<BlockPos> positions, Rotation rotation) {
        return positions.stream().map(pos -> pos.subtract(from).rotate(rotation)).toList();
    }

    /** Counts the hull's open air, once everything else stands. */
    void countAir() {
        int[] count = {0};
        forEachAir((x, y, z) -> count[0]++);
        air = count[0];
    }

    /** A block position, handed over. */
    @FunctionalInterface
    private interface Cell {
        void at(int x, int y, int z);
    }

    /** Every block of air inside the hull: its own space, wherever nothing else stands. */
    private void forEachAir(Cell cell) {
        Box box = airBox();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                airRow(cell, box, x, y);
            }
        }
    }

    private void airRow(Cell cell, Box box, int x, int y) {
        for (int z = box.minZ(); z <= box.maxZ(); z++) {
            if (surface().depth(x, y + 0.5, z) < 0 && isOpen(x, y, z)) {
                cell.at(x, y, z);
            }
        }
    }

    /**
     * The hull's air first, so it is cleared before anything goes in, then everything else bottom to top. Handed over
     * as every airframe's blocks are, from its front at z 0 back (see {@link Layout}).
     */
    @Override
    public void inOrder(BiConsumer<BlockPos, BlockState> cell) {
        BlockState open = Blocks.AIR.defaultBlockState();
        forEachAir((x, y, z) -> cell.accept(handed(new BlockPos(x, y, z)), open));
        List<Map.Entry<BlockPos, BlockState>> sorted = new ArrayList<>(cells.entrySet());
        sorted.sort((a, b) -> Integer.compare(a.getKey().getY(), b.getKey().getY()));
        sorted.forEach(entry -> cell.accept(handed(entry.getKey()), entry.getValue()));
    }

    /** A position in the layout's frame as it is handed over, from the front at z 0. */
    private BlockPos handed(BlockPos inFrame) {
        return inFrame.offset(0, 0, handedBack());
    }

    @Override
    public int size() {
        return air + cells.size();
    }

    @Override
    public Map<BlockPos, Fitting> fittings() {
        Map<BlockPos, Fitting> handed = new HashMap<>();
        fittings.forEach((at, fitting) -> handed.put(handed(at), fitting));
        return handed;
    }

    /** Too big for the Physics Assembler, it is assembled through Sable instead, and needs no glue. */
    @Override
    public boolean glued() {
        return false;
    }
}
