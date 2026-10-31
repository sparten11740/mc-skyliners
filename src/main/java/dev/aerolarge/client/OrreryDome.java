package dev.aerolarge.client;

import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.OrreryBlock;
import dev.aerolarge.block.OrreryBlockEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * The rocket's dome as the renderer of the orrery hanging in it finds it (see {@link OrreryRenderer}): how far out from
 * her axis it is open at each row, from the ring of ceiling over her cockpit's glass up to the orrery's mount at the tip
 * of her nose, how wide the hole in that ring is, and from that the smooth shape just inside the dome's lining that the
 * planetarium's sky is drawn on (see {@link PlanetariumShell}). Positions are in the mount's own frame, from its block's
 * corner, her axis up the middle of its column.
 *
 * <p>Found on the client by looking in along each row from outside her skin, a few dozen ways round, so that whatever
 * is built against the lining is soon seen and the sky drawn in front of it, while whatever stands out in the room, as
 * the orrery does, is not; looked over again every few seconds while it is drawn, and never further than just outside
 * her skin. Nothing of it is saved, so a rocket built before the planetarium has it too. Where her control head is,
 * whose flight the oculus ring's lamps show (see {@link OculusRing}), is found with it.
 */
final class OrreryDome {
    /** How long a dome is drawn as it was found before it is looked over again, in ticks. */
    private static final int RESCAN_TICKS = 100;
    /** How many ways round each row is looked along, evenly spaced: enough that no corner of the lining is missed. */
    private static final int WAYS = 32;
    private static final float[] WAY_COS = new float[WAYS];
    private static final float[] WAY_SIN = new float[WAYS];
    /** Where her axis runs up through the mount's column, across it and along it, in blocks from its corner. */
    static final float AXIS = 0.5F;
    /** How far out from her axis the look in along the mount's own row starts, in blocks: past her skin at her tip. */
    private static final float TIP_LOOK = 4;
    /**
     * How much further out than her skin on the row over it the look in along a row starts, in blocks: her nose widens
     * by less than that a row on the way down.
     */
    private static final float SKIN_GROWTH = 2;
    /** How many rows under the sun the ring of ceiling over her cockpit is looked for, at most. */
    private static final int FLOOR_UNDER_SUN = 8;
    /**
     * How much narrower than the row over it the hole in the ring of ceiling is at least, in blocks: how the ring is
     * told from the dome, which only widens going down.
     */
    private static final float FLOOR_STEP = 0.75F;
    /** How many rows under the ring of ceiling her cockpit's floor is: her seven rows of glass and the floor's own. */
    private static final int COCKPIT_ROWS = 8;
    /** How far past the dome's skin the camera may be and still be in her cockpit, which is wider, in blocks. */
    private static final float COCKPIT_WIDER = 2;
    /** How far inside every row's open space the planetarium's shape keeps, in blocks, so the lining never shows. */
    private static final float SHELL_INSET = 0.1F;
    /** The narrowest the planetarium's shape is drawn, in blocks, round the rod up to the mount. */
    private static final float NARROWEST = 0.05F;
    /** Each orrery's dome as last found, dropped with the orrery. Only the render thread reaches it. */
    private static final Map<OrreryBlockEntity, OrreryDome> DOMES = new WeakHashMap<>();

    static {
        for (int way = 0; way < WAYS; way++) {
            // Half a step off the four ways, so no look runs exactly along a seam between blocks.
            double angle = Mth.TWO_PI * (way + 0.5) / WAYS;
            WAY_COS[way] = (float) Math.cos(angle);
            WAY_SIN[way] = (float) Math.sin(angle);
        }
    }

    /**
     * A ring of the planetarium's shape: how high it is, and how far out from her axis, in blocks, and how far down the
     * sky's picture it is, from the pole star at the tip (0) to the dome's foot (1).
     */
    record Ring(float y, float radius, float v) {
    }

    private final long found;
    private final double drop;
    /** Whether the ring of ceiling was found under the dome: if not, it has no shape to draw. */
    private final boolean shaped;
    /** The row of the ring of ceiling over her cockpit's glass, from the mount's (a negative number of rows). */
    private final int floor;
    /** How wide the hole in the ring of ceiling is, from her axis to the nearest of its blocks, in blocks. */
    private final float oculus;
    /** How far out from her axis the outside of her skin round the dome is at most, in blocks. */
    private final float outer;
    /** The planetarium's shape, its rings from the dome's foot up to the top one, which is closed over. */
    private final List<Ring> shell;
    /** Where her control head is, in the level; null where none was found. */
    @Nullable
    private final BlockPos autopilot;

    /**
     * A dome found with its ring of ceiling at a row, the hole in it so wide, so wide outside, and each row over that
     * ring up to the mount's open so far out from her axis; none open where the ring was not found.
     */
    private OrreryDome(long found, double drop, int floor, float oculus, float outer, float[] open, @Nullable BlockPos autopilot) {
        this.found = found;
        this.drop = drop;
        this.shaped = open.length > 0;
        this.floor = floor;
        this.oculus = oculus;
        this.outer = outer;
        this.shell = shaped ? shell(floor, open) : List.of();
        this.autopilot = autopilot;
    }

    /** An orrery's dome as last found, or found again if that was a while ago or the orrery has changed. */
    static OrreryDome of(OrreryBlockEntity orrery, Level level) {
        OrreryDome dome = DOMES.get(orrery);
        long now = level.getGameTime();
        if (dome == null || dome.drop != orrery.drop() || now - dome.found >= RESCAN_TICKS || now < dome.found) {
            dome = find(orrery, level, now);
            DOMES.put(orrery, dome);
        }
        return dome;
    }

    /** An orrery's dome as last found, if it has been; null before it is first drawn. */
    static OrreryDome known(OrreryBlockEntity orrery) {
        return DOMES.get(orrery);
    }

    boolean shaped() {
        return shaped;
    }

    int floor() {
        return floor;
    }

    float oculus() {
        return oculus;
    }

    float outer() {
        return outer;
    }

    /** Her autopilot, whose flight the oculus ring's lamps show, if it is still where it was found. */
    @Nullable
    AutopilotBlockEntity autopilot(Level level) {
        return autopilot != null && level.getBlockEntity(autopilot) instanceof AutopilotBlockEntity found ? found : null;
    }

    List<Ring> shell() {
        return shell;
    }

    /**
     * Whether the camera, given in the mount's frame, is in the cockpit or the dome over it, where the planetarium can
     * be seen: any of it is drawn only then. A dome without a shape holds the camera wherever it is.
     */
    boolean holds(Vector3f eye) {
        if (!shaped) {
            return true;
        }
        double out = Math.hypot(eye.x - AXIS, eye.z - AXIS);
        return out <= outer + COCKPIT_WIDER && eye.y >= floor - COCKPIT_ROWS && eye.y <= 1;
    }

    /** The box round the dome and the cockpit under it, in the level, its mount where it is. */
    AABB box(BlockPos mount) {
        double out = outer + COCKPIT_WIDER;
        return new AABB(mount).inflate(out, 0, out).expandTowards(0, floor - COCKPIT_ROWS, 0);
    }

    /**
     * Looks along the rows from the mount's down, row by row, till the ring of ceiling under the sun: the first row
     * under the sun's that is clearly narrower than the one over it, as the dome only widens going down.
     */
    private static OrreryDome find(OrreryBlockEntity orrery, Level level, long now) {
        BlockPos mount = orrery.getBlockPos();
        int sun = -Mth.floor(orrery.drop());
        float[] skin = new float[WAYS];
        Arrays.fill(skin, TIP_LOOK - SKIN_GROWTH);
        List<Float> rows = new ArrayList<>();
        float above = Float.MAX_VALUE;
        float outer = 0;
        for (int row = 0; row >= sun - FLOOR_UNDER_SUN; row--) {
            float open = row(level, mount, row, skin);
            outer = Math.max(outer, widest(skin));
            open = Math.min(open, outer);
            if (row < sun && open < above - FLOOR_STEP) {
                return new OrreryDome(now, orrery.drop(), row, open, outer, bottomUp(rows), controlHead(orrery, level));
            }
            rows.add(open);
            above = open;
        }
        return new OrreryDome(now, orrery.drop(), 0, 0, 0, new float[0], null);
    }

    /**
     * Where her control head is: where it was from the mount as she was built, if it is still there; otherwise, as for a
     * rocket built before that was kept or turned since, the nearest autopilot to the sun, within its flight deck's reach.
     */
    @Nullable
    private static BlockPos controlHead(OrreryBlockEntity orrery, Level level) {
        BlockPos mount = orrery.getBlockPos();
        BlockPos built = orrery.autopilot();
        if (built != null && level.getBlockEntity(mount.offset(built)) instanceof AutopilotBlockEntity) {
            return mount.offset(built);
        }
        AutopilotBlockEntity nearest = AutopilotBlockEntity.nearest(level, mount.below(Mth.floor(orrery.drop())));
        return nearest != null ? nearest.getBlockPos() : null;
    }

    private static float[] bottomUp(List<Float> topDown) {
        float[] rows = new float[topDown.size()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = topDown.get(topDown.size() - 1 - i);
        }
        return rows;
    }

    private static float widest(float[] skin) {
        float widest = 0;
        for (float out : skin) {
            widest = Math.max(widest, out);
        }
        return widest;
    }

    /**
     * How far out from her axis a row is open, the nearest any way round, in blocks; where her skin was found each way is
     * kept, so the next row down is looked along from just outside it.
     */
    private static float row(Level level, BlockPos mount, int row, float[] skin) {
        float nearest = Float.MAX_VALUE;
        for (int way = 0; way < WAYS; way++) {
            nearest = Math.min(nearest, new Look(level, mount, row, way, skin[way] + SKIN_GROWTH).look(skin));
        }
        return nearest;
    }

    /** Whether a block is solid to the planetarium: anything but air, light and the orrery's own mount. */
    private static boolean solid(Level level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        return state.getRenderShape() != RenderShape.INVISIBLE && !(state.getBlock() instanceof OrreryBlock);
    }

    /** How near a block of a row comes to her axis, in blocks, from where it is from the mount's column. */
    private static float nearness(int x, int z) {
        return (float) Math.hypot(Math.max(0, Math.abs(x) - AXIS), Math.max(0, Math.abs(z) - AXIS));
    }

    /**
     * A look in along a row one way, a block at a time (every block the line passes through, so none is slipped past at
     * a corner), from so far out from her axis toward it.
     */
    private static final class Look {
        private final Level level;
        private final BlockPos mount;
        private final int row;
        private final int way;
        private final float from;
        private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();

        Look(Level level, BlockPos mount, int row, int way, float from) {
            this.level = level;
            this.mount = mount;
            this.row = row;
            this.way = way;
            this.from = from;
        }

        /**
         * Through her skin to the open dome inside: how near her axis the blocks of her lining come round where it was
         * left, in blocks; nothing where her lining reaches her axis, as the ring of ceiling does on a small rocket,
         * and as far as can be where nothing was met at all. Where her skin was met is kept, or from further out yet
         * where it was not.
         */
        float look(float[] skin) {
            double dx = -WAY_COS[way];
            double dz = -WAY_SIN[way];
            double px = AXIS - dx * from;
            double pz = AXIS - dz * from;
            int x = Mth.floor(px);
            int z = Mth.floor(pz);
            double stepX = Math.abs(1 / dx);
            double stepZ = Math.abs(1 / dz);
            double nextX = (dx > 0 ? x + 1 - px : px - x) * stepX;
            double nextZ = (dz > 0 ? z + 1 - pz : pz - z) * stepZ;
            double gone = 0;
            boolean inSkin = false;
            int lastX = x;
            int lastZ = z;
            while (gone <= from) {
                boolean solid = solidAt(x, z);
                if (!solid && inSkin) {
                    return nearestAround(lastX, lastZ);
                }
                if (solid && !inSkin) {
                    skin[way] = (float) (from - gone);
                }
                inSkin |= solid;
                lastX = x;
                lastZ = z;
                if (nextX < nextZ) {
                    gone = nextX;
                    nextX += stepX;
                    x += dx > 0 ? 1 : -1;
                } else {
                    gone = nextZ;
                    nextZ += stepZ;
                    z += dz > 0 ? 1 : -1;
                }
            }
            if (!inSkin) {
                skin[way] = from;
            }
            return inSkin ? 0 : Float.MAX_VALUE;
        }

        /**
         * The nearest to her axis of the solid blocks round the last of her lining met, itself among them: the lining
         * steps round in blocks, and one of its corners between two looks may come in nearer than either.
         */
        private float nearestAround(int liningX, int liningZ) {
            float nearest = nearness(liningX, liningZ);
            for (int i = 0; i < 9; i++) {
                int x = liningX + i % 3 - 1;
                int z = liningZ + i / 3 - 1;
                float near = nearness(x, z);
                if (near < nearest && solidAt(x, z)) {
                    nearest = near;
                }
            }
            return nearest;
        }

        private boolean solidAt(int x, int z) {
            return solid(level, at.set(mount.getX() + x, mount.getY() + row, mount.getZ() + z));
        }
    }

    /**
     * The planetarium's shape inside rows so far open: a ring at each seam between two rows as far out as the narrower
     * of them leaves room for, less the inset, and at the top under the mount's ceiling; then only as many of them as
     * keep it inside every row, each straight run between two of them passing inside the rings left out. Each is given
     * its place down the sky's picture by how far along the shape it is from the middle of its top.
     */
    private static List<Ring> shell(int floor, float[] open) {
        int rows = open.length;
        float[] y = new float[rows + 1];
        float[] room = new float[rows + 1];
        for (int seam = 0; seam <= rows; seam++) {
            y[seam] = floor + 1 + seam;
            float under = open[Math.max(0, seam - 1)];
            float over = open[Math.min(rows - 1, seam)];
            room[seam] = Math.max(NARROWEST, Math.min(under, over) - SHELL_INSET);
        }
        y[rows] -= SHELL_INSET;
        List<Integer> kept = new ArrayList<>(List.of(0));
        int from = 0;
        for (int to = 2; to <= rows; to++) {
            if (!straight(y, room, from, to)) {
                from = to - 1;
                kept.add(from);
            }
        }
        kept.add(rows);
        float length = room[rows];
        for (int i = 1; i < kept.size(); i++) {
            length += (float) Math.hypot(y[kept.get(i)] - y[kept.get(i - 1)], room[kept.get(i)] - room[kept.get(i - 1)]);
        }
        List<Ring> rings = new ArrayList<>();
        float along = length;
        for (int i = 0; i < kept.size(); i++) {
            int seam = kept.get(i);
            if (i > 0) {
                int below = kept.get(i - 1);
                along -= (float) Math.hypot(y[seam] - y[below], room[seam] - room[below]);
            }
            rings.add(new Ring(y[seam], room[seam], along / length));
        }
        return List.copyOf(rings);
    }

    /** Whether a straight run from one seam's ring to another's passes inside the rooms at every seam between them. */
    private static boolean straight(float[] y, float[] room, int from, int to) {
        for (int seam = from + 1; seam < to; seam++) {
            float share = (y[seam] - y[from]) / (y[to] - y[from]);
            if (room[from] + (room[to] - room[from]) * share > room[seam]) {
                return false;
            }
        }
        return true;
    }
}
