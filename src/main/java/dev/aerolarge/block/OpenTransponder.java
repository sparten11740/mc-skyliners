package dev.aerolarge.block;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.radar.MarkerColour;
import dev.aerolarge.radar.MarkerIcon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/** Sent to a player who used a transponder: open its panel, showing the picture and colour it shows now. */
public record OpenTransponder(BlockPos pos, MarkerIcon icon, MarkerColour colour) implements CustomPacketPayload {
    public static final Type<OpenTransponder> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "open_transponder"));
    public static final StreamCodec<FriendlyByteBuf, OpenTransponder> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, OpenTransponder::pos,
        NeoForgeStreamCodecs.enumCodec(MarkerIcon.class), OpenTransponder::icon,
        NeoForgeStreamCodecs.enumCodec(MarkerColour.class), OpenTransponder::colour,
        OpenTransponder::new
    );

    @Override
    public Type<OpenTransponder> type() {
        return TYPE;
    }
}
