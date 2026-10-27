package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.PictureBoardBlock;
import dev.aerolarge.block.PictureBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The flank screens (see {@link FlankScreen}) and the picture board that chooses what they show (see
 * {@link PictureBoardBlock}), standing on the upper deck's floor at its aft end, to port, looking forward along the
 * promenade. The board draws the screens, so it is told as it is built where they are from it. It stands on the floor
 * face to face, so it is assembled with the zeppelin, and holds nothing up.
 */
final class ZeppelinFlankScreen implements ZeppelinPiece {
    /** How far to port of the hull's middle the board stands, in blocks. */
    private static final int PORT = 3;

    private final ZeppelinLayout layout;

    ZeppelinFlankScreen(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.FLANK_SCREEN.get();
    }

    /** At every size, the screens laid where each size has room for them (see {@link FlankScreen}). */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        int z = layout.deckBack();
        int y = layout.upperDeckRow() + 1;
        BlockPos board = new BlockPos(-PORT, y, z);
        if (!layout.isOpen(board.getX(), y, z) || layout.isOpen(board.getX(), y - 1, z)) {
            return;
        }
        layout.set(board.getX(), y, z, AerolargeRegistry.PICTURE_BOARD.get().defaultBlockState().setValue(PictureBoardBlock.FACING, Direction.NORTH));
        BlockPos screens = new BlockPos(0, layout.axis(), FlankScreen.of(layout.hullSize()).front()).subtract(board);
        ZeppelinSize size = layout.hullSize();
        layout.fit(board, (blockEntity, rotation) -> {
            if (blockEntity instanceof PictureBoardBlockEntity picture) {
                picture.placeScreens(screens.rotate(rotation), rotation.rotate(Direction.SOUTH), size);
            }
        });
    }
}
