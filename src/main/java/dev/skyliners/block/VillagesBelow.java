package dev.skyliners.block;

import dev.skyliners.Skyliners;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to everyone aboard an assembled airliner: the villages under it, by the middle of each, for the passenger
 * screens' route map (see {@link VillageSpotter}). Only x and z count; the villages are in the player's own dimension.
 */
public record VillagesBelow(List<BlockPos> villages) implements CustomPacketPayload {
    public static final Type<VillagesBelow> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "villages_below"));
    public static final StreamCodec<FriendlyByteBuf, VillagesBelow> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), VillagesBelow::villages,
        VillagesBelow::new
    );

    @Override
    public Type<VillagesBelow> type() {
        return TYPE;
    }
}
