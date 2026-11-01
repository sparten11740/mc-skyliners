package dev.skyliners.block;

import dev.skyliners.Skyliners;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Sent to a player who used a coffee machine that is ready for an order: open its panel to pick a drink. */
public record OpenCoffeeMachine(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenCoffeeMachine> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "open_coffee_machine"));
    public static final StreamCodec<FriendlyByteBuf, OpenCoffeeMachine> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, OpenCoffeeMachine::pos,
        OpenCoffeeMachine::new
    );

    @Override
    public Type<OpenCoffeeMachine> type() {
        return TYPE;
    }
}
