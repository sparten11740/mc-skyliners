package dev.skyliners.engineering;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The jump drive's block entity (see {@link JumpDriveBlock}): nothing to save, as the drive's step is its block state,
 * but on the client how far its rings have turned and how fast they turn now, eased toward the speed of the drive's
 * step so they speed up and slow down rather than jump, for the client's {@code JumpDriveRenderer}.
 */
public class JumpDriveBlockEntity extends BlockEntity {
    /**
     * On the client: how far the rings have turned, in turns; how fast they turn now, in turns a tick; and when they
     * were last drawn, on the drawing clock, in ticks.
     */
    private double turned;
    private double speed;
    private double drawnAt = Double.NaN;

    public JumpDriveBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.JUMP_DRIVE_ENTITY.get(), pos, state);
    }

    /**
     * How far the rings have turned at a time on the client's drawing clock, in turns, their speed eased toward the one
     * wanted by so much of the way each tick.
     */
    public double turned(double ticks, double wanted, double ease) {
        double since = Double.isNaN(drawnAt) ? 0 : Math.max(0, Math.min(ticks - drawnAt, 20));
        drawnAt = ticks;
        speed += (wanted - speed) * Math.min(1, ease * since);
        turned = (turned + speed * since) % 1;
        return turned;
    }
}
