package dev.aerolarge.autopilot;

import dev.aerolarge.Aerolarge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to a player who used the autopilot or its screen: open the autopilot's settings screen, filled in with what it
 * flies now (or would fly if engaged now), whether engaging it would take off, and the lowest and highest heights it
 * can be set to hold there.
 */
public record OpenAutopilotScreen(BlockPos pos, AutopilotBlockEntity.Settings settings, boolean takeoff, int minAltitude, int maxAltitude)
    implements CustomPacketPayload {
    public static final Type<OpenAutopilotScreen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "open_autopilot_screen"));
    public static final StreamCodec<FriendlyByteBuf, OpenAutopilotScreen> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, OpenAutopilotScreen::pos,
        AutopilotBlockEntity.Settings.STREAM_CODEC, OpenAutopilotScreen::settings,
        ByteBufCodecs.BOOL, OpenAutopilotScreen::takeoff,
        ByteBufCodecs.VAR_INT, OpenAutopilotScreen::minAltitude,
        ByteBufCodecs.VAR_INT, OpenAutopilotScreen::maxAltitude,
        OpenAutopilotScreen::new
    );

    @Override
    public Type<OpenAutopilotScreen> type() {
        return TYPE;
    }
}
