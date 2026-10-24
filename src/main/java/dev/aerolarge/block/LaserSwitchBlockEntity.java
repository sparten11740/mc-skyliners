package dev.aerolarge.block;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the laser path clearer's switch works: the turret under the nose (see {@link LaserTurretBlockEntity}), as an
 * offset from the switch, turned with it (see {@link Turning}). Pressed, the switch arms or disarms the turret, its
 * button lit while armed: red while the laser clears the way, amber while, low down, it holds its fire at blocks, with
 * a soft beep down as it starts holding and up as it clears again. With the laser switched off in the server config
 * the switch only buzzes.
 */
public class LaserSwitchBlockEntity extends BlockEntity {
    private static final float CLICK_VOLUME = 0.8F;
    /** How often the switch looks whether the turret holds its fire, in ticks. */
    private static final int LOOK_TICKS = 10;

    @Nullable
    private BlockPos turret;
    /** Whether its link has been looked at since it was loaded, to mend it (see {@link Turning#mend}). */
    private boolean linkChecked;

    public LaserSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LASER_SWITCH_ENTITY.get(), pos, state);
    }

    /** Wired when the airliner is built: the turret, as an offset from the switch. */
    public void configure(BlockPos turret) {
        this.turret = turret;
        linkChecked = true;
        setChanged();
    }

    /** Pressed by a pilot: arms or disarms the laser; switched off in the server config, a buzz and why. */
    void press(ServerLevel level, Player player) {
        if (!AerolargeConfig.LASER_PATH_CLEARER.get()) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
            player.displayClientMessage(Component.translatable("message.aerolarge.laser.off"), true);
            return;
        }
        BlockState state = getBlockState();
        boolean armed = !state.getValue(LaserSwitchBlock.ARMED);
        LaserTurretBlockEntity laser = turret(level);
        if (laser != null) {
            laser.arm(level, armed);
        }
        boolean holding = armed && laser != null && laser.holding();
        level.setBlock(worldPosition, state.setValue(LaserSwitchBlock.ARMED, armed).setValue(LaserSwitchBlock.HOLDING, holding), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, CLICK_VOLUME, armed ? 1.2F : 0.8F);
        player.displayClientMessage(Component.translatable(armed ? "message.aerolarge.laser.armed" : "message.aerolarge.laser.disarmed"), true);
    }

    /** Every {@link #LOOK_TICKS}: the button's light for whether the armed turret clears the way or holds its fire. */
    void tick() {
        if (!(level instanceof ServerLevel serverLevel) || Math.floorMod(level.getGameTime() + worldPosition.asLong(), LOOK_TICKS) != 0) {
            return;
        }
        BlockState state = getBlockState();
        LaserTurretBlockEntity laser = turret(serverLevel);
        boolean holding = state.getValue(LaserSwitchBlock.ARMED) && laser != null && laser.holding();
        if (holding != state.getValue(LaserSwitchBlock.HOLDING)) {
            serverLevel.setBlock(worldPosition, state.setValue(LaserSwitchBlock.HOLDING, holding), Block.UPDATE_CLIENTS);
            serverLevel.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, CLICK_VOLUME, holding ? 0.7F : 1.4F);
        }
    }

    /** The turret it works, if it is there. */
    @Nullable
    private LaserTurretBlockEntity turret(ServerLevel level) {
        mendLink(level);
        return turret != null && level.getBlockEntity(worldPosition.offset(turret)) instanceof LaserTurretBlockEntity laser ? laser : null;
    }

    /** Once after loading, mends its link to the turret if it leads nowhere. */
    private void mendLink(ServerLevel level) {
        if (!linkChecked && turret != null) {
            linkChecked = true;
            Turning.mend(level, worldPosition, List.of(turret), state -> state.getBlock() instanceof LaserTurretBlock, this::turnLink);
        }
    }

    private void turnLink(Rotation turn) {
        turret = Turning.turn(turret, turn);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (turret != null) {
            tag.putLong("Turret", turret.asLong());
        }
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        turret = tag.contains("Turret") ? Turning.turn(BlockPos.of(tag.getLong("Turret")), Turning.since(tag, getBlockState())) : null;
    }
}
