package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.ZeppelinPilot;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A trim winch's block entity: the rail it hauls the trim car along under the gangway (see {@link TrimWinchBlock}),
 * how far aft of it the car hangs now, and where the trim wheel in the control car is, all set as the zeppelin is
 * built. Flown by hand, it hauls the car to where the wheel is turned, the forward end of the rail all the way one
 * way and the aft end all the way the other; under the autopilot, it hauls the car a little at a time toward the end
 * that lets the autopilot's fore-and-aft split of the hot air run down to nothing (see {@link ZeppelinPilot}), the
 * weight holding the nose level in its place, and turns the wheel to show where it wants the car. The car moves a
 * block at a time, its chain clinking as it is hauled.
 */
public class TrimWinchBlockEntity extends BlockEntity {
    /** How often the car moves a block along the rail, in ticks: a few blocks a second. */
    private static final int STEP_TICKS = 5;
    /**
     * How far the autopilot rolls the car each tick per share of its fore-and-aft split, in blocks, and the split it
     * leaves to its burners, a share.
     */
    private static final double TRIM_LEARN = 0.5;
    private static final double TRIM_DEADBAND = 0.01;
    /** How loud the car's roll is heard at the trim wheel, far forward of it in the control car. */
    private static final float AT_WHEEL = 0.4F;

    private Direction aft = Direction.SOUTH;
    /**
     * How long the rail is, and how far aft of the winch the car hangs, in blocks; nothing before the zeppelin is
     * built.
     */
    private int length;
    private int car;
    @Nullable
    private BlockPos wheel;
    /** Where the autopilot wants the car, in blocks aft of the winch. */
    private double wanted;

    public TrimWinchBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.TRIM_WINCH_ENTITY.get(), pos, state);
    }

    /**
     * Tells it its rail, where its car hangs and where the trim wheel is, as the zeppelin is built (turned as she was
     * placed).
     */
    public void rig(Direction aft, int length, int car, BlockPos wheel) {
        this.aft = aft;
        this.length = length;
        this.car = car;
        this.wheel = wheel;
        wanted = car;
        setChanged();
    }

    /** Once a tick on the server: where the car should be, and a step toward it now and then. */
    void serverTick(ServerLevel level) {
        if (length <= 0) {
            return;
        }
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.zeppelinFraming(level, worldPosition);
        ZeppelinPilot.Report report = autopilot == null || autopilot.phase() == AutopilotBlockEntity.Phase.OFF ? null : autopilot.zeppelinReport();
        double target = report == null ? byHand(level) : byAutopilot(level, report);
        if (level.getGameTime() % STEP_TICKS == 0) {
            roll(level, Long.signum(Math.round(target) - car));
        }
    }

    /** Where the trim wheel says the car should be. */
    private double byHand(ServerLevel level) {
        BlockState state = wheel == null ? null : level.getBlockState(worldPosition.offset(wheel));
        if (state == null || !(state.getBlock() instanceof HelmBlock)) {
            return car;
        }
        wanted = 1 + (HelmBlock.turn(state) + 1) / 2 * (length - 1);
        return wanted;
    }

    /**
     * Where the autopilot wants the car: rolled a little at a time the way that eases its fore-and-aft split, shown on
     * the wheel. Its split less than nothing, the car goes forward: in flight, the weight forward runs it down.
     */
    private double byAutopilot(ServerLevel level, ZeppelinPilot.Report report) {
        double split = report.pitchTrim();
        if (Math.abs(split) > TRIM_DEADBAND) {
            wanted = Mth.clamp(wanted + TRIM_LEARN * split, 1, length);
        }
        BlockState state = wheel == null ? null : level.getBlockState(worldPosition.offset(wheel));
        if (state != null && state.getBlock() instanceof HelmBlock) {
            int notch = HelmBlock.MIDDLE + (int) Math.round(((wanted - 1) / (length - 1) * 2 - 1) * HelmBlock.MOST);
            if (state.getValue(HelmBlock.TURN) != notch) {
                level.setBlock(worldPosition.offset(wheel), state.setValue(HelmBlock.TURN, notch), Block.UPDATE_ALL);
            }
        }
        return wanted;
    }

    /** Rolls the car a block fore or aft along the rail, or not at all, as far as its ends. */
    private void roll(ServerLevel level, int way) {
        int to = car + way;
        if (way == 0 || to < 1 || to > length) {
            return;
        }
        BlockPos from = worldPosition.relative(aft, car);
        BlockPos at = worldPosition.relative(aft, to);
        if (!level.getBlockState(from).is(AerolargeRegistry.TRIM_CAR.get()) || !level.getBlockState(at).isAir()) {
            return;
        }
        level.setBlock(at, level.getBlockState(from), Block.UPDATE_ALL);
        level.setBlock(from, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        car = to;
        setChanged();
        level.playSound(null, at, AerolargeRegistry.TRIM_CAR_ROLL.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
        if (wheel != null) {
            level.playSound(null, worldPosition.offset(wheel), AerolargeRegistry.TRIM_CAR_ROLL.get(), SoundSource.BLOCKS, AT_WHEEL, 1.0F);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Aft", aft.getSerializedName());
        tag.putInt("Length", length);
        tag.putInt("Car", car);
        tag.putDouble("Wanted", wanted);
        if (wheel != null) {
            tag.put("Wheel", NbtUtils.writeBlockPos(wheel));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Direction read = Direction.byName(tag.getString("Aft"));
        aft = read != null && read.getAxis().isHorizontal() ? read : Direction.SOUTH;
        length = tag.getInt("Length");
        car = tag.getInt("Car");
        wanted = tag.contains("Wanted") ? tag.getDouble("Wanted") : car;
        wheel = NbtUtils.readBlockPos(tag, "Wheel").orElse(null);
    }
}
