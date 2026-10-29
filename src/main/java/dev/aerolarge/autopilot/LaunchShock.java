package dev.aerolarge.autopilot;

import dev.aerolarge.Aerolarge;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to a player as a rocket's lift-off shockwave reaches them (see {@link RocketLaunchBlast}): how hard it hits
 * them, from 0, heard from beyond its reach, to 1, right by her, for how loud its boom is and how hard their view
 * shakes.
 */
public record LaunchShock(float strength) implements CustomPacketPayload {
    public static final Type<LaunchShock> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "launch_shock"));
    public static final StreamCodec<ByteBuf, LaunchShock> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.FLOAT, LaunchShock::strength,
        LaunchShock::new
    );

    @Override
    public Type<LaunchShock> type() {
        return TYPE;
    }
}
