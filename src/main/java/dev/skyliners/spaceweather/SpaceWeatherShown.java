package dev.skyliners.spaceweather;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Sent to everyone aboard a rocket or a freighter and near her as space weather is told, and again as it is over (see
 * {@link SpaceWeather}), one message each, for their client to show it: the alarm's picture on their screen and her
 * scanner, its meteors or rocks coming over the horizon and streaming past, a flare's glare, and the all-clear's tick.
 *
 * <p>What blows; when it was told, in game time; how long it is told before it is on her, and how long it is on her, in
 * ticks; its seed, from which every client works out the same streaks and rocks; the side of the sky it comes out of,
 * as a bearing in degrees as the game turns a look (see {@link SpaceWeather#toward}); where her middle was in the world
 * as it was told and how fast it moved, in blocks a second, from which it streams past as she flies on, so that she is
 * seen thrown about among it as she is struck; and whether it is over.
 */
public record SpaceWeatherShown(SpaceWeather.Kind kind, long startedAt, int warningTicks, int stormTicks, long seed, float from, Vec3 centre,
                                Vec3 velocity, boolean over) implements CustomPacketPayload {
    public static final Type<SpaceWeatherShown> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "space_weather"));
    public static final StreamCodec<ByteBuf, SpaceWeatherShown> STREAM_CODEC = StreamCodec.of(SpaceWeatherShown::write, SpaceWeatherShown::read);

    private static void write(ByteBuf buffer, SpaceWeatherShown shown) {
        buffer.writeByte(shown.kind().ordinal());
        buffer.writeLong(shown.startedAt());
        buffer.writeShort(shown.warningTicks());
        buffer.writeShort(shown.stormTicks());
        buffer.writeLong(shown.seed());
        buffer.writeFloat(shown.from());
        writeVec(buffer, shown.centre());
        writeVec(buffer, shown.velocity());
        buffer.writeBoolean(shown.over());
    }

    private static SpaceWeatherShown read(ByteBuf buffer) {
        SpaceWeather.Kind kind = SpaceWeather.Kind.of(buffer.readByte());
        long startedAt = buffer.readLong();
        int warningTicks = buffer.readShort();
        int stormTicks = buffer.readShort();
        long seed = buffer.readLong();
        float from = buffer.readFloat();
        Vec3 centre = readVec(buffer);
        Vec3 velocity = readVec(buffer);
        return new SpaceWeatherShown(kind, startedAt, warningTicks, stormTicks, seed, from, centre, velocity, buffer.readBoolean());
    }

    static void writeVec(ByteBuf buffer, Vec3 vec) {
        buffer.writeDouble(vec.x);
        buffer.writeDouble(vec.y);
        buffer.writeDouble(vec.z);
    }

    static Vec3 readVec(ByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    public Type<SpaceWeatherShown> type() {
        return TYPE;
    }
}
