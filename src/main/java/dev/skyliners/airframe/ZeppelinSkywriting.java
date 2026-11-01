package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.SkywritingBoardBlock;
import dev.skyliners.block.SkywritingBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Skywriting: the board on the starboard wall of the control car's lounge whose picture buttons have her autopilot fly
 * a shape across the sky (see {@link SkywritingBoardBlock}), told where the smoke streams from: just under her belly
 * at the middle of her lift, the point her autopilot steers, so the smoke draws the shape as she flies it. From her
 * tail, a couple of hundred blocks behind, it would swing wide of the shape in every turn. The board hangs on the
 * car's wall face to face, so it is assembled with the zeppelin.
 */
final class ZeppelinSkywriting implements ZeppelinPiece {
    /** Where the board hangs: how high over the car's floor and how far aft of the lounge's start, in blocks. */
    private static final int UP = 2;
    private static final int AFT = 4;
    /** How far under her belly the smoke streams from, in blocks. */
    private static final int BELOW = 3;

    private final ZeppelinLayout layout;

    ZeppelinSkywriting(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return SkylinersConfig.SKYWRITING.get();
    }

    /** In the control car, the same at every size. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        ZeppelinLayout.Car car = layout.car();
        BlockPos board = new BlockPos(car.half() - 1, car.floor() + UP, car.lounge() + AFT);
        // The car's inside is laid as air, so it is clear of whatever is laid there that is not.
        if (!layout.stateAt(board.getX(), board.getY(), board.getZ()).isAir()) {
            return;
        }
        layout.set(board.getX(), board.getY(), board.getZ(),
            SkylinersRegistry.SKYWRITING_BOARD.get().defaultBlockState().setValue(SkywritingBoardBlock.FACING, Direction.WEST));
        double middle = layout.liftMiddle();
        BlockPos smoke = new BlockPos(0, (int) Math.floor(layout.belly(middle)) - BELOW, (int) Math.floor(middle));
        layout.fit(board, (blockEntity, rotation) -> {
            if (blockEntity instanceof SkywritingBoardBlockEntity rigged) {
                rigged.rig(smoke.subtract(board).rotate(rotation));
            }
        });
    }
}
