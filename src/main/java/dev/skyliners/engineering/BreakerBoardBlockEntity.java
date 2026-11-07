package dev.skyliners.engineering;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.LedStripBlock;
import dev.skyliners.block.Turning;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The breaker board's block entity (see {@link BreakerBoardBlock}): where each of her rooms' LED panels is, as an
 * offset from the board turned as she was placed (see {@link Turning}), and how bright each room's strips were when its
 * switch last put them out, to bring them back as they were. Whether a switch is on is whether its room's strips are
 * lit, as its panel has them, so a room put out at its own panel shows off here too. The offsets go to clients, whose
 * {@code BreakerBoardRenderer} reads each room's colour and whether it is lit from its panel.
 */
public class BreakerBoardBlockEntity extends BlockEntity {
    /** What the client is told as a switch is flipped, its sparks flying: the switch's number goes with it. */
    private static final int FLIPPED = 1;
    /** How many sparks fly from a switch flipped, and how loud its clack and its hum are. */
    private static final int SPARKS = 6;
    private static final float CLACK_VOLUME = 0.9F;
    private static final float HUM_VOLUME = 0.6F;
    /** How bright a room's strips come back on when nothing remembers how bright they were. */
    private static final int BACK_ON = 2;

    /** Each room's panel as an offset from the board, in the order of its switches; null where the room has none. */
    private final List<BlockPos> panels = new ArrayList<>();
    private int[] remembered = new int[0];

    public BreakerBoardBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.BREAKER_BOARD_ENTITY.get(), pos, state);
    }

    /**
     * Set as she is built: each room's panel, in the order of the switches, as offsets from the board in her layout,
     * turned as she was placed; null where a room has none.
     */
    public void wire(List<BlockPos> offsets, Rotation rotation) {
        panels.clear();
        offsets.forEach(offset -> panels.add(Turning.turn(offset, rotation)));
        remembered = new int[panels.size()];
        Arrays.fill(remembered, BACK_ON);
        setChanged();
    }

    /** The LED panel of a switch's room, as it stands in the level; null where there is none, or it is gone. */
    @Nullable
    public LedPanelBlockEntity panel(int switchNumber) {
        if (level == null || switchNumber < 0 || switchNumber >= panels.size() || panels.get(switchNumber) == null) {
            return null;
        }
        return level.getBlockEntity(worldPosition.offset(panels.get(switchNumber))) instanceof LedPanelBlockEntity panel ? panel : null;
    }

    /**
     * Flips a switch, on the server: its room's strips go out, remembering how bright they were, or come back on as
     * they were, with a clack, sparks and the power's hum falling or rising.
     */
    void flip(ServerLevel level, int switchNumber) {
        LedPanelBlockEntity panel = panel(switchNumber);
        if (panel == null || switchNumber >= remembered.length) {
            level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, CLACK_VOLUME, 0.6F);
            return;
        }
        int brightness = panel.next().brightness();
        if (brightness > 0) {
            remembered[switchNumber] = brightness;
        }
        int now = brightness > 0 ? 0 : Math.max(1, Math.min(remembered[switchNumber], LedStripBlock.BRIGHTEST));
        panel.change(setting -> setting.withBrightness(now), 0);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, CLACK_VOLUME, now > 0 ? 0.9F : 0.7F);
        level.playSound(null, worldPosition, now > 0 ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, HUM_VOLUME,
            1.2F);
        level.blockEvent(worldPosition, getBlockState().getBlock(), FLIPPED, switchNumber);
    }

    /** On the client: sparks from the switch flipped, where its block is in the world as she flies. */
    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != FLIPPED) {
            return false;
        }
        if (level == null || !level.isClientSide) {
            return true;
        }
        Direction facing = getBlockState().getValue(BreakerBoardBlock.FACING);
        Direction right = facing.getCounterClockWise();
        double across = BreakerBoardBlock.SIDE_IN + (param % BreakerBoardBlock.ACROSS + 0.5) * (1 - 2 * BreakerBoardBlock.SIDE_IN)
            / BreakerBoardBlock.ACROSS;
        double up = BreakerBoardBlock.TOP - (param / BreakerBoardBlock.ACROSS + 0.5) * (BreakerBoardBlock.TOP - BreakerBoardBlock.FOOT)
            / BreakerBoardBlock.DOWN;
        Vec3 middle = Vec3.atCenterOf(worldPosition).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.4))
            .add(Vec3.atLowerCornerOf(right.getNormal()).scale(across - 0.5)).add(0, up - 0.5, 0);
        Vec3 at = SableFlight.toWorld(level, middle);
        for (int spark = 0; spark < SPARKS; spark++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, facing.getStepX() * 0.05 + level.random.nextGaussian() * 0.03,
                level.random.nextDouble() * 0.05, facing.getStepZ() * 0.05 + level.random.nextGaussian() * 0.03);
        }
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag saved = new ListTag();
        for (int room = 0; room < panels.size(); room++) {
            CompoundTag one = new CompoundTag();
            BlockPos offset = panels.get(room);
            if (offset != null) {
                one.putInt("X", offset.getX());
                one.putInt("Y", offset.getY());
                one.putInt("Z", offset.getZ());
            }
            one.putInt("Remembered", room < remembered.length ? remembered[room] : BACK_ON);
            saved.add(one);
        }
        tag.put("Rooms", saved);
        Turning.save(tag, getBlockState());
    }

    /** Loaded again, its offsets are turned if the board has turned since they were saved. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation since = Turning.since(tag, getBlockState());
        ListTag saved = tag.getList("Rooms", Tag.TAG_COMPOUND);
        panels.clear();
        remembered = new int[saved.size()];
        for (int room = 0; room < saved.size(); room++) {
            CompoundTag one = saved.getCompound(room);
            BlockPos offset = one.contains("X") ? new BlockPos(one.getInt("X"), one.getInt("Y"), one.getInt("Z")) : null;
            panels.add(Turning.turn(offset, since));
            remembered[room] = one.contains("Remembered") ? one.getInt("Remembered") : BACK_ON;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
