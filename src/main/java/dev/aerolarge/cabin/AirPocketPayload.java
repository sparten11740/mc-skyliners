package dev.aerolarge.cabin;

import dev.aerolarge.Aerolarge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent to everyone aboard as the airliner drops into an air pocket: how deep, about 1 (deeper in a thunderstorm). */
public record AirPocketPayload(float depth) implements CustomPacketPayload {
    public static final Type<AirPocketPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "air_pocket"));
    public static final StreamCodec<ByteBuf, AirPocketPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, AirPocketPayload::depth,
        AirPocketPayload::new
    );

    @Override
    public Type<AirPocketPayload> type() {
        return TYPE;
    }
}
