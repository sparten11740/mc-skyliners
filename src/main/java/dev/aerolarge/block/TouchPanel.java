package dev.aerolarge.block;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the mod's touch panels share, worked out alike on the server and the clients, so that what lights up under a
 * player's crosshair is what touching it does (see the client's {@code TouchPanelArt} for how every panel draws it):
 * the tiles on a panel's face, rectangles measured in the face's own units from its bottom left corner as the viewer
 * sees it, x to the right and y up; layouts that set them out in a grid, a row or a column over part of the face;
 * which tile a touch lands on; and where a hit on a single block's face touches it. A panel says how its tiles are
 * shaped and set out; how they light up is the same on every panel.
 */
public final class TouchPanel {
    private TouchPanel() {
    }

    /** A tile on a panel's face, from its lower left corner to its upper right, in the face's units. */
    public record Tile(double left, double bottom, double right, double top) {
        /** Whether a point on the face, across and up from its lower left corner, is on the tile. */
        public boolean holds(double across, double up) {
            return across >= left && across < right && up >= bottom && up < top;
        }

        public double width() {
            return right - left;
        }

        public double height() {
            return top - bottom;
        }

        public double middleX() {
            return (left + right) / 2;
        }

        public double middleY() {
            return (bottom + top) / 2;
        }

        /** Its shorter side, which a picture on it is sized to. */
        public double side() {
            return Math.min(width(), height());
        }

        /** The tile shrunk by so much on every side, as it is drawn within the gap round it. */
        public Tile inset(double by) {
            return new Tile(left + by, bottom + by, right - by, top - by);
        }
    }

    /** A panel's tiles in order, each known by its place in the list. */
    public record Layout(List<Tile> tiles) {
        public Layout {
            tiles = List.copyOf(tiles);
        }

        /** The tile at a point on the face, across and up from its lower left corner: its place; -1 off every tile. */
        public int at(double across, double up) {
            for (int tile = 0; tile < tiles.size(); tile++) {
                if (tiles.get(tile).holds(across, up)) {
                    return tile;
                }
            }
            return -1;
        }

        /** The tile at a touch on the face; -1 off every tile, or for no touch. */
        public int at(@Nullable WideScreenBlock.Touch touch) {
            return touch == null ? -1 : at(touch.across(), touch.up());
        }

        public Tile get(int tile) {
            return tiles.get(tile);
        }

        public int size() {
            return tiles.size();
        }

        /**
         * So many tiles in rows of so many over an area of the face, the first row at its bottom or at its top, each
         * row from the left; the tiles of a last row that is not full share its whole width, so a lone last tile has
         * all of it.
         */
        public static Layout grid(int count, int columns, Tile area, boolean fromTop) {
            int rows = Math.max(1, Math.ceilDiv(count, columns));
            double rowHeight = area.height() / rows;
            List<Tile> tiles = new ArrayList<>();
            for (int tile = 0; tile < count; tile++) {
                int row = tile / columns;
                int inRow = Math.min(columns, count - row * columns);
                double width = area.width() / inRow;
                double left = area.left() + tile % columns * width;
                double bottom = fromTop ? area.top() - (row + 1) * rowHeight : area.bottom() + row * rowHeight;
                tiles.add(new Tile(left, bottom, left + width, bottom + rowHeight));
            }
            return new Layout(tiles);
        }

        /** So many tiles side by side across an area of the face, from the left. */
        public static Layout row(int count, Tile area) {
            return grid(count, count, area, false);
        }

        /** So many tiles one over another down an area of the face, from the top. */
        public static Layout column(int count, Tile area) {
            return grid(count, 1, area, true);
        }
    }

    /**
     * Where a hit on the face of a single block's panel touches it, in blocks from the face's bottom left corner as the
     * viewer sees it, the panel facing the way given; null for a hit on any other side. Seen from the front, the
     * viewer's right is the panel's counterclockwise side.
     */
    @Nullable
    public static WideScreenBlock.Touch touched(Direction facing, BlockPos pos, BlockHitResult hit) {
        if (hit.getDirection() != facing) {
            return null;
        }
        Vec3 at = hit.getLocation().subtract(Vec3.atCenterOf(pos));
        Direction right = facing.getCounterClockWise();
        return new WideScreenBlock.Touch(at.x * right.getStepX() + at.z * right.getStepZ() + 0.5, at.y + 0.5);
    }
}
