package dev.skyliners.radar;

import dev.skyliners.compat.SableFlight;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * A player as the maps show them: who, by their id and name, and where they are in the world. Their face is the
 * client's to find, among the players it is told of for its player list.
 */
public record PlayerContact(UUID id, String name, double x, double z) implements OnTheMap {
    public static final StreamCodec<ByteBuf, PlayerContact> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, PlayerContact::id,
        ByteBufCodecs.STRING_UTF8, PlayerContact::name,
        ByteBufCodecs.DOUBLE, PlayerContact::x,
        ByteBufCodecs.DOUBLE, PlayerContact::z,
        PlayerContact::new
    );

    /** Every player of a dimension but its spectators, who are not there to be seen. */
    public static List<PlayerContact> in(ServerLevel level) {
        return level.players().stream().filter(player -> !player.isSpectator()).map(player -> of(level, player)).toList();
    }

    /** A player where they are in the world: aboard an airliner, where the airliner has them. */
    private static PlayerContact of(ServerLevel level, ServerPlayer player) {
        Vec3 at = SableFlight.toWorld(level, player.position());
        return new PlayerContact(player.getUUID(), player.getGameProfile().getName(), at.x, at.z);
    }
}
