package dev.aerolarge.cabin;

import dev.aerolarge.Aerolarge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent to everyone aboard as the airliner runs into rough air: how rough, from 0 to 1, and for how many ticks. */
public record TurbulencePayload(float strength, int ticks) implements CustomPacketPayload {
    public static final Type<TurbulencePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "turbulence"));
    public static final StreamCodec<ByteBuf, TurbulencePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, TurbulencePayload::strength,
        ByteBufCodecs.VAR_INT, TurbulencePayload::ticks,
        TurbulencePayload::new
    );

    @Override
    public Type<TurbulencePayload> type() {
        return TYPE;
    }
}
