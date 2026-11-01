package dev.skyliners.cabin;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent to everyone aboard as lightning strikes the airliner, for the flash, the cabin's flicker and the screens' static. */
public record LightningStrikePayload() implements CustomPacketPayload {
    public static final LightningStrikePayload INSTANCE = new LightningStrikePayload();
    public static final Type<LightningStrikePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "lightning_strike"));
    public static final StreamCodec<ByteBuf, LightningStrikePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<LightningStrikePayload> type() {
        return TYPE;
    }
}
