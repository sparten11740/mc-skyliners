package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lift door's block entity, which only the clients make use of: as its door sets off sliding open or shut, the game
 * tick it set off, so that its renderer draws the pane on its way, easing off and in. It keeps nothing: a door whose
 * slide began before it was loaded slides from where it is seen first.
 */
public class LiftDoorBlockEntity extends BlockEntity {
    /** The game tick its slide set off, or none while it stands still. */
    private long setOff = Long.MIN_VALUE;

    public LiftDoorBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LIFT_DOOR_ENTITY.get(), pos, state);
    }

    /** A door set sliding, or sliding the other way, sets off now. */
    @Override
    public void setBlockState(BlockState state) {
        BlockState was = getBlockState();
        boolean settingOff = state.getValue(LiftDoorBlock.SLIDING)
            && (!was.getValue(LiftDoorBlock.SLIDING) || was.getValue(LiftDoorBlock.OPEN) != state.getValue(LiftDoorBlock.OPEN));
        super.setBlockState(state);
        if (settingOff && level != null) {
            setOff = level.getGameTime();
        }
    }

    /** How far open its pane stands at this partial tick, from 0 shut to 1 open, eased off and in. */
    public double openness(float partialTick) {
        BlockState state = getBlockState();
        boolean opening = state.getValue(LiftDoorBlock.OPEN);
        if (!state.getValue(LiftDoorBlock.SLIDING) || level == null) {
            return opening ? 1 : 0;
        }
        if (setOff == Long.MIN_VALUE) {
            setOff = level.getGameTime();
        }
        double done = Mth.clamp((level.getGameTime() - setOff + partialTick) / LiftMachineBlockEntity.DOOR_TICKS, 0, 1);
        double eased = done * done * (3 - 2 * done);
        return opening ? eased : 1 - eased;
    }
}
