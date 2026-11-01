package dev.skyliners.parachute;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to everyone near a player whose safety canopy opens or closes (see {@link SafetyCanopy}): whose it is, and
 * whether it is open, for their clients to draw it and for the faller's own to glide under it.
 */
public record SafetyCanopyPayload(UUID player, boolean open) implements CustomPacketPayload {
    public static final Type<SafetyCanopyPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "safety_canopy"));
    public static final StreamCodec<ByteBuf, SafetyCanopyPayload> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, SafetyCanopyPayload::player,
        ByteBufCodecs.BOOL, SafetyCanopyPayload::open,
        SafetyCanopyPayload::new
    );

    @Override
    public Type<SafetyCanopyPayload> type() {
        return TYPE;
    }
}
