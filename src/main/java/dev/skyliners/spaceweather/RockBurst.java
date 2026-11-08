package dev.skyliners.spaceweather;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Sent to everyone shown the space weather blowing round a rocket or a freighter as a rock of it is shot to pieces by a
 * laser cannon (see {@link SpaceRocks}), one message a rock, so that every client agrees it is gone: it bursts into
 * tumbling chunks and dust with a crunch where it was, and is drawn no more. Which weather it was of, by when it was
 * told, in game time; its number among the weather's rocks; where it was, in the world; and the way the shot that broke
 * it travelled, of length 1, which its chunks fly on along.
 */
public record RockBurst(long startedAt, int id, Vec3 at, Vec3 way) implements CustomPacketPayload {
    public static final Type<RockBurst> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "rock_burst"));
    public static final StreamCodec<ByteBuf, RockBurst> STREAM_CODEC = StreamCodec.of(RockBurst::write, RockBurst::read);

    private static void write(ByteBuf buffer, RockBurst burst) {
        buffer.writeLong(burst.startedAt());
        buffer.writeInt(burst.id());
        SpaceWeatherShown.writeVec(buffer, burst.at());
        buffer.writeFloat((float) burst.way().x);
        buffer.writeFloat((float) burst.way().y);
        buffer.writeFloat((float) burst.way().z);
    }

    private static RockBurst read(ByteBuf buffer) {
        long startedAt = buffer.readLong();
        int id = buffer.readInt();
        Vec3 at = SpaceWeatherShown.readVec(buffer);
        return new RockBurst(startedAt, id, at, new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat()));
    }

    @Override
    public Type<RockBurst> type() {
        return TYPE;
    }
}
