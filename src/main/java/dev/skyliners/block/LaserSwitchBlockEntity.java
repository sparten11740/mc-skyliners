package dev.skyliners.block;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
 * What the laser path clearer's switch works: its turrets, under the nose, along the belly and under the wings (see
 * {@link LaserTurretBlockEntity}), as offsets from the switch, turned with it (see {@link Turning}). Pressed, the
 * switch arms or disarms them all, its button lit while armed: red while the laser clears the way, amber while, low
 * down with the engines idle, every turret holds its fire at blocks, with a soft beep down as it starts holding and up
 * as it clears again. With the laser switched off in the server config the switch only buzzes. An airliner built with
 * the nose's turret alone keeps working it alone.
 */
public class LaserSwitchBlockEntity extends BlockEntity {
    private static final float CLICK_VOLUME = 0.8F;
    /** How often the switch looks whether the turrets hold their fire, in ticks. */
    private static final int LOOK_TICKS = 10;

    private List<BlockPos> turrets = List.of();
    /** Whether its links have been looked at since it was loaded, to mend them (see {@link Turning#mend}). */
    private boolean linksChecked;

    public LaserSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LASER_SWITCH_ENTITY.get(), pos, state);
    }

    /** Wired when the airliner is built: the turrets, as offsets from the switch. */
    public void configure(List<BlockPos> turrets) {
        this.turrets = List.copyOf(turrets);
        linksChecked = true;
        setChanged();
    }

    /** How many turrets it works. */
    public int turretCount() {
        return turrets.size();
    }

    /** Pressed by a pilot: arms or disarms the laser; switched off in the server config, a buzz and why. */
    void press(ServerLevel level, Player player) {
        if (!SkylinersConfig.LASER_PATH_CLEARER.get()) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
            player.displayClientMessage(Component.translatable("message.skyliners.laser.off"), true);
            return;
        }
        boolean armed = !getBlockState().getValue(LaserSwitchBlock.ARMED);
        arm(level, armed);
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, CLICK_VOLUME, armed ? 1.2F : 0.8F);
        player.displayClientMessage(Component.translatable(armed ? "message.skyliners.laser.armed" : "message.skyliners.laser.disarmed"), true);
    }

    /** Arms or disarms every turret it works, its button lit to match. */
    public void arm(ServerLevel level, boolean armed) {
        List<LaserTurretBlockEntity> lasers = turrets(level);
        lasers.forEach(laser -> laser.arm(level, armed));
        BlockState state = getBlockState().setValue(LaserSwitchBlock.ARMED, armed).setValue(LaserSwitchBlock.HOLDING, armed && holding(lasers));
        level.setBlock(worldPosition, state, Block.UPDATE_ALL);
    }

    /** Every {@link #LOOK_TICKS}: the button's light for whether the armed turrets clear the way or hold their fire. */
    void tick() {
        if (!(level instanceof ServerLevel serverLevel) || Math.floorMod(level.getGameTime() + worldPosition.asLong(), LOOK_TICKS) != 0) {
            return;
        }
        BlockState state = getBlockState();
        boolean holding = state.getValue(LaserSwitchBlock.ARMED) && holding(turrets(serverLevel));
        if (holding != state.getValue(LaserSwitchBlock.HOLDING)) {
            serverLevel.setBlock(worldPosition, state.setValue(LaserSwitchBlock.HOLDING, holding), Block.UPDATE_CLIENTS);
            serverLevel.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, CLICK_VOLUME, holding ? 0.7F : 1.4F);
        }
    }

    /** Whether the laser holds its fire at blocks: every turret does, and there is one. */
    private static boolean holding(List<LaserTurretBlockEntity> lasers) {
        return !lasers.isEmpty() && lasers.stream().allMatch(LaserTurretBlockEntity::holding);
    }

    /** The turrets it works that are there. */
    private List<LaserTurretBlockEntity> turrets(ServerLevel level) {
        mendLinks(level);
        return turrets.stream()
            .map(turret -> level.getBlockEntity(worldPosition.offset(turret)) instanceof LaserTurretBlockEntity laser ? laser : null)
            .filter(Objects::nonNull)
            .toList();
    }

    /** Once after loading, mends its links to the turrets if they lead nowhere. */
    private void mendLinks(ServerLevel level) {
        if (!linksChecked && !turrets.isEmpty()) {
            linksChecked = true;
            Turning.mend(level, worldPosition, turrets, state -> state.getBlock() instanceof LaserTurretBlock, this::turnLinks);
        }
    }

    private void turnLinks(Rotation turn) {
        turrets = Turning.turn(turrets, turn);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Turrets", Relays.write(turrets));
        Turning.save(tag, getBlockState());
    }

    /** Reads its turrets, or the one turret an airliner built before had, under the nose. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        if (tag.contains("Turrets")) {
            turrets = Turning.turn(Relays.read(tag.getList("Turrets", Tag.TAG_INT_ARRAY)), turn);
        } else {
            turrets = tag.contains("Turret") ? List.of(Turning.turn(BlockPos.of(tag.getLong("Turret")), turn)) : List.of();
        }
    }
}
