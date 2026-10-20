package dev.aerolarge.block;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by the coffee machine's panel: brew this drink. Nothing in it is taken on trust: the server only brews for a
 * player who can reach a coffee machine standing there already loaded, and only on one that is ready for an order.
 */
public record BrewCoffee(BlockPos pos, CoffeeDrink drink) implements CustomPacketPayload {
    public static final Type<BrewCoffee> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "brew_coffee"));
    public static final StreamCodec<FriendlyByteBuf, BrewCoffee> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, BrewCoffee::pos,
        NeoForgeStreamCodecs.enumCodec(CoffeeDrink.class), BrewCoffee::drink,
        BrewCoffee::new
    );

    @Override
    public Type<BrewCoffee> type() {
        return TYPE;
    }

    /** On the server: starts the brew, for a player who can work a machine that is ready. */
    public static void handle(BrewCoffee payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.pos();
        // Checked loaded first, so that a position from a client never loads a chunk.
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof CoffeeMachineBlockEntity machine) || !machine.canBeUsedBy(player)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.is(AerolargeRegistry.COFFEE_MACHINE.get()) && state.getValue(CoffeeMachineBlock.BREW) == CoffeeMachineBlock.Brew.READY) {
            CoffeeMachineBlock.brew(level, pos, state, payload.drink());
        }
    }
}
