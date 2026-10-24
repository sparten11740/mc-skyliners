package dev.aerolarge.autopilot;

import dev.aerolarge.Aerolarge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the autopilot's settings screen: hop the airliner of the autopilot at that position free on its control
 * jets; sent again every tick the button is held, to keep them firing. Nothing in it is taken on trust: the server
 * only acts for a player who can reach an autopilot standing there already loaded, and the autopilot checks again that
 * the airliner is stuck (see {@link AutopilotBlockEntity#unstick}).
 */
public record UnstickAirliner(BlockPos pos) implements CustomPacketPayload {
    public static final Type<UnstickAirliner> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "unstick_airliner"));
    public static final StreamCodec<FriendlyByteBuf, UnstickAirliner> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, UnstickAirliner::pos,
        UnstickAirliner::new
    );

    @Override
    public Type<UnstickAirliner> type() {
        return TYPE;
    }

    /** On the server: hops the airliner free and says so over the hotbar, or says why it cannot. */
    public static void handle(UnstickAirliner payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            AutopilotBlockEntity autopilot = AutopilotBlockEntity.workedBy(player, payload.pos());
            Component said = autopilot != null ? autopilot.unstick() : null;
            if (said != null) {
                player.displayClientMessage(said, true);
            }
        }
    }
}
