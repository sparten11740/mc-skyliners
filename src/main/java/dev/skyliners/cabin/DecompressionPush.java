package dev.skyliners.cabin;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Sent each tick to a player the rushing air takes (see {@link Decompression}): how hard it pushes them, in the world's
 * directions, in blocks a tick, for their client to add to how they move, as a player's movement is their client's.
 */
public record DecompressionPush(double x, double y, double z) implements CustomPacketPayload {
    public static final Type<DecompressionPush> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "decompression_push"));
    public static final StreamCodec<ByteBuf, DecompressionPush> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, DecompressionPush::x,
        ByteBufCodecs.DOUBLE, DecompressionPush::y,
        ByteBufCodecs.DOUBLE, DecompressionPush::z,
        DecompressionPush::new
    );

    DecompressionPush(Vec3 push) {
        this(push.x, push.y, push.z);
    }

    @Override
    public Type<DecompressionPush> type() {
        return TYPE;
    }
}
