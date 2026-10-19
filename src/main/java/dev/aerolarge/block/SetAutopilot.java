package dev.aerolarge.block;

import dev.aerolarge.Aerolarge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the autopilot's settings screen: fly these settings, engaging the autopilot at that position if it is off,
 * or switch it off. Nothing in it is taken on trust: the server only acts for a player who can reach an autopilot
 * standing there already loaded, and the autopilot holds every setting to its own limits (see
 * {@link AutopilotBlockEntity#set}).
 */
public record SetAutopilot(BlockPos pos, Action action, AutopilotBlockEntity.Settings settings) implements CustomPacketPayload {
    /** What the screen asks: fly the settings, or switch off. */
    public enum Action {
        FLY, OFF
    }

    public static final Type<SetAutopilot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "set_autopilot"));
    public static final StreamCodec<FriendlyByteBuf, SetAutopilot> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetAutopilot::pos,
        NeoForgeStreamCodecs.enumCodec(Action.class), SetAutopilot::action,
        AutopilotBlockEntity.Settings.STREAM_CODEC, SetAutopilot::settings,
        SetAutopilot::new
    );

    @Override
    public Type<SetAutopilot> type() {
        return TYPE;
    }

    /** On the server: sets the autopilot and says what it does, for a player who can work it. */
    public static void handle(SetAutopilot payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        Level level = player.level();
        BlockPos pos = payload.pos();
        // Checked loaded first, so that a position from a client never loads a chunk.
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof AutopilotBlockEntity autopilot) || !autopilot.canBeWorkedBy(player)) {
            return;
        }
        // In chat: the action bar is the autopilot's status line while it flies, and would cover this at once.
        player.displayClientMessage(autopilot.set(payload.action(), payload.settings()), false);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.4F);
    }
}
