package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The announcement panel's workings: which autopilot makes its announcements (as an offset turned with the panel, so
 * the link holds wherever the airliner is; see {@link Turning}), and where each announcement stands (see
 * {@link Announcement.Standing}), which the server looks at every {@link #LOOK} ticks and sends the clients as it
 * changes, for them to light, grey or pulse each tile. A panel the blueprint did not link, put up by hand, links itself
 * to the nearest autopilot within the flight deck's reach. A tile pressed asks the autopilot for its announcement,
 * which it only makes if it stands ready: a click says it was taken, a dull one that it was not.
 */
public class AnnouncementPanelBlockEntity extends BlockEntity {
    /** How often the server looks where the announcements stand, in ticks. */
    private static final int LOOK = 5;

    /** Where the autopilot is, from this block; null until linked. */
    @Nullable
    private BlockPos autopilot;
    /** Where each announcement stands, by its tile; kept on the server and sent to the clients. */
    private final Announcement.Standing[] standings = new Announcement.Standing[Announcement.values().length];
    private int sinceLook;

    public AnnouncementPanelBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ANNOUNCEMENT_PANEL_ENTITY.get(), pos, state);
        Arrays.fill(standings, Announcement.Standing.READY);
    }

    /** Links the panel to its autopilot. */
    public void configure(BlockPos offset) {
        autopilot = offset;
        setChanged();
    }

    /** Where an announcement stands, as last sent. */
    public Announcement.Standing standing(Announcement announcement) {
        return standings[announcement.ordinal()];
    }

    /**
     * The autopilot that makes the panel's announcements, if it is there; on the server, an unlinked panel links
     * itself to the nearest one first, if there is one.
     */
    @Nullable
    private AutopilotBlockEntity autopilot() {
        if (level == null) {
            return null;
        }
        if (autopilot == null) {
            AutopilotBlockEntity nearest = level.isClientSide ? null : AutopilotBlockEntity.nearest(level, worldPosition);
            if (nearest != null) {
                configure(nearest.getBlockPos().subtract(worldPosition));
            }
            return nearest;
        }
        return level.getBlockEntity(worldPosition.offset(autopilot)) instanceof AutopilotBlockEntity found ? found : null;
    }

    /** A tile pressed, on the server: asks for its announcement, and says with a click whether it is made. */
    void press(Announcement announcement, ServerPlayer player) {
        AutopilotBlockEntity target = autopilot();
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.aerolarge.announcement_panel.unlinked"), true);
            return;
        }
        boolean made = target.ask(announcement);
        player.level().playSound(null, worldPosition, made ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
            SoundSource.BLOCKS, 0.5F, made ? 1.6F : 0.6F);
        look(target);
    }

    /** Each tick, on the server: every so often, where the announcements stand. */
    void tick() {
        if (++sinceLook < LOOK) {
            return;
        }
        sinceLook = 0;
        AutopilotBlockEntity target = autopilot();
        if (target != null) {
            look(target);
        }
    }

    /** Looks where the announcements stand, and tells the clients if any has moved. */
    private void look(AutopilotBlockEntity target) {
        boolean moved = false;
        for (Announcement announcement : Announcement.values()) {
            Announcement.Standing now = target.standing(announcement);
            moved |= now != standings[announcement.ordinal()];
            standings[announcement.ordinal()] = now;
        }
        if (moved && level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (autopilot != null) {
            tag.put("Autopilot", NbtUtils.writeBlockPos(autopilot));
        }
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Autopilot")) {
            autopilot = Turning.turn(NbtUtils.readBlockPos(tag, "Autopilot").orElse(null), Turning.since(tag, getBlockState()));
        }
        byte[] sent = tag.getByteArray("Standings");
        Announcement.Standing[] all = Announcement.Standing.values();
        for (int i = 0; i < Math.min(sent.length, standings.length); i++) {
            standings[i] = all[Math.floorMod(sent[i], all.length)];
        }
    }

    /** The clients only need where the announcements stand. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        byte[] sent = new byte[standings.length];
        for (int i = 0; i < standings.length; i++) {
            sent[i] = (byte) standings[i].ordinal();
        }
        tag.putByteArray("Standings", sent);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
