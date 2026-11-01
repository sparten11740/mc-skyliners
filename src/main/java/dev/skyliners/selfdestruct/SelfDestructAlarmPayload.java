package dev.skyliners.selfdestruct;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to everyone aboard an airliner whose self-destruct button is armed: how many ticks are left before it blows
 * up, for their screen to count the seconds down; none (0) once it is disarmed or the airliner has blown up.
 */
public record SelfDestructAlarmPayload(int ticks) implements CustomPacketPayload {
    public static final SelfDestructAlarmPayload NONE = new SelfDestructAlarmPayload(0);
    public static final Type<SelfDestructAlarmPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "self_destruct_alarm"));
    public static final StreamCodec<ByteBuf, SelfDestructAlarmPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(SelfDestructAlarmPayload::new, SelfDestructAlarmPayload::ticks);

    @Override
    public Type<SelfDestructAlarmPayload> type() {
        return TYPE;
    }
}
