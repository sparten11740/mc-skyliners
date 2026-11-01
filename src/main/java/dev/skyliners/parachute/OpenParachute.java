package dev.skyliners.parachute;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent by a client whose player pressed jump while falling with a parachute on: open it. */
public record OpenParachute() implements CustomPacketPayload {
    public static final OpenParachute INSTANCE = new OpenParachute();
    public static final Type<OpenParachute> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "open_parachute"));
    public static final StreamCodec<ByteBuf, OpenParachute> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<OpenParachute> type() {
        return TYPE;
    }
}
