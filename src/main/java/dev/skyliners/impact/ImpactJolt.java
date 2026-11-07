package dev.skyliners.impact;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to everyone aboard a ship as she runs into something (see {@link Impacts}): how hard, from 0, a bump, to 1, the
 * hardest smash, for how hard their view is jolted.
 */
public record ImpactJolt(float strength) implements CustomPacketPayload {
    public static final Type<ImpactJolt> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "impact_jolt"));
    public static final StreamCodec<ByteBuf, ImpactJolt> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, ImpactJolt::strength,
        ImpactJolt::new
    );

    @Override
    public Type<ImpactJolt> type() {
        return TYPE;
    }
}
