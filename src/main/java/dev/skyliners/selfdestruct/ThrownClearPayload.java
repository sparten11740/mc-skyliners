package dev.skyliners.selfdestruct;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to each player thrown clear of a ship as it blows up (see {@link SelfDestruct}): their client stops them
 * where it blew up, letting go of the ship's speed it carried them at, as their client is what moves them.
 */
public record ThrownClearPayload() implements CustomPacketPayload {
    public static final ThrownClearPayload INSTANCE = new ThrownClearPayload();
    public static final Type<ThrownClearPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "thrown_clear"));
    public static final StreamCodec<ByteBuf, ThrownClearPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<ThrownClearPayload> type() {
        return TYPE;
    }
}
