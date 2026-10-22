package dev.aerolarge.block;

import dev.aerolarge.Aerolarge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent to a player who used a tower's radar screen: open the radar's map round it. */
public record OpenTowerRadar(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenTowerRadar> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "open_tower_radar"));
    public static final StreamCodec<FriendlyByteBuf, OpenTowerRadar> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, OpenTowerRadar::pos,
        OpenTowerRadar::new
    );

    @Override
    public Type<OpenTowerRadar> type() {
        return TYPE;
    }
}
