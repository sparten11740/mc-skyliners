package dev.skyliners;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Sent to the players of a dimension: the frame blocks placed by hand there (see {@link HandPlaced}), each with the
 * block placed. A player arriving in the dimension is sent all of them, standing in for any told of before; after
 * that everyone there is sent each change as it happens, those placed and those gone.
 */
public record HandPlacedBlocks(boolean all, Map<BlockPos, Block> placed, List<BlockPos> gone) implements CustomPacketPayload {
    public static final Type<HandPlacedBlocks> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "hand_placed_blocks"));
    private static final StreamCodec<RegistryFriendlyByteBuf, Map<BlockPos, Block>> BLOCKS =
        ByteBufCodecs.map(HashMap::new, BlockPos.STREAM_CODEC, ByteBufCodecs.registry(Registries.BLOCK));
    public static final StreamCodec<RegistryFriendlyByteBuf, HandPlacedBlocks> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, HandPlacedBlocks::all,
        BLOCKS, HandPlacedBlocks::placed,
        BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), HandPlacedBlocks::gone,
        HandPlacedBlocks::new
    );

    @Override
    public Type<HandPlacedBlocks> type() {
        return TYPE;
    }
}
