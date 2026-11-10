package dev.skyliners.spaceweather;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to everyone shown the space weather blowing round a rocket or a freighter as a rock of it comes close enough to
 * be a real flying body of blocks (see {@link RockBodies}), and again as it stops being one: their client draws the
 * rock until the body has reached it, and draws it again, or not at all, afterwards. Which weather it is of, by when it
 * was told, in game time; its number among the weather's rocks, a piece cracked off one having a number of its own; the
 * body's id; how it stands now; and how big it is, in blocks across, for a cannon's sight to know a piece by.
 */
public record RockReal(long startedAt, int id, UUID body, Stage stage, float size) implements CustomPacketPayload {
    public static final Type<RockReal> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "rock_real"));
    public static final StreamCodec<ByteBuf, RockReal> STREAM_CODEC = StreamCodec.of(RockReal::write, RockReal::read);

    /**
     * How a rock stands: a real body; let go whole, drawn again along its way; or let go worn or knocked, drawn no
     * more.
     */
    public enum Stage {
        BODY, DRAWN, GONE;

        static Stage of(int index) {
            Stage[] stages = values();
            return index >= 0 && index < stages.length ? stages[index] : GONE;
        }
    }

    private static void write(ByteBuf buffer, RockReal told) {
        buffer.writeLong(told.startedAt());
        buffer.writeInt(told.id());
        UUIDUtil.STREAM_CODEC.encode(buffer, told.body());
        buffer.writeByte(told.stage().ordinal());
        buffer.writeFloat(told.size());
    }

    private static RockReal read(ByteBuf buffer) {
        long startedAt = buffer.readLong();
        int id = buffer.readInt();
        UUID body = UUIDUtil.STREAM_CODEC.decode(buffer);
        Stage stage = Stage.of(buffer.readByte());
        return new RockReal(startedAt, id, body, stage, buffer.readFloat());
    }

    @Override
    public Type<RockReal> type() {
        return TYPE;
    }
}
