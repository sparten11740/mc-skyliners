package dev.aerolarge.selfdestruct;

import dev.aerolarge.Aerolarge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to each player near an airliner blowing up (see {@link Blast}): how hard their view shakes, from 0 to 1, by how
 * near they are, and how bright the white flash is for them, from 0 to 1, by how near and whether they saw it.
 */
public record BlastPayload(float shake, float flash) implements CustomPacketPayload {
    public static final Type<BlastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "blast"));
    public static final StreamCodec<ByteBuf, BlastPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, BlastPayload::shake,
        ByteBufCodecs.FLOAT, BlastPayload::flash,
        BlastPayload::new
    );

    @Override
    public Type<BlastPayload> type() {
        return TYPE;
    }
}
