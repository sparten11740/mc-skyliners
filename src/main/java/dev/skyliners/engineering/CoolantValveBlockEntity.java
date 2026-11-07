package dev.skyliners.engineering;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The coolant valve's block entity (see {@link CoolantValveBlock}): nothing to save, as its pressure is its block
 * state, but on the client how far its wheel has turned and its gauge's needle risen, each eased toward where the
 * pressure has them, so the wheel turns round and the needle swings rather than jump, for the client's {@code
 * CoolantValveRenderer}.
 */
public class CoolantValveBlockEntity extends BlockEntity {
    /**
     * On the client: how far the wheel has turned, in quarter turns, which is how far the needle has risen, in steps of
     * pressure; and when they were last drawn, on the drawing clock, in ticks.
     */
    private double shown;
    private double drawnAt = Double.NaN;

    public CoolantValveBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.COOLANT_VALVE_ENTITY.get(), pos, state);
    }

    /**
     * Brings the wheel and needle on toward the pressure, by so much of the way each tick, at a time on the client's
     * drawing clock; gives where they stand, in steps of pressure.
     */
    public double toward(double ticks, int pressure, double ease) {
        double since = Double.isNaN(drawnAt) ? 1 / ease : Math.max(0, Math.min(ticks - drawnAt, 20));
        drawnAt = ticks;
        shown += (pressure - shown) * Math.min(1, ease * since);
        return shown;
    }
}
