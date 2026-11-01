package dev.skyliners.radar;

import dev.skyliners.Skyliners;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to a player near a tower: chunks of land its radar's survey has taken in (see {@link RadarSurvey}), for the
 * player's maps, a few at a time.
 */
public record RadarLand(List<Chunk> chunks) implements CustomPacketPayload {
    /** A chunk of land, by its position (as {@code ChunkPos.toLong}). */
    public record Chunk(long at, LandTile land) {
        static final StreamCodec<FriendlyByteBuf, Chunk> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, Chunk::at,
            LandTile.STREAM_CODEC, Chunk::land,
            Chunk::new
        );
    }

    public static final Type<RadarLand> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "radar_land"));
    public static final StreamCodec<FriendlyByteBuf, RadarLand> STREAM_CODEC = StreamCodec.composite(
        Chunk.STREAM_CODEC.apply(ByteBufCodecs.list()), RadarLand::chunks,
        RadarLand::new
    );

    @Override
    public Type<RadarLand> type() {
        return TYPE;
    }
}
