package dev.aerolarge.item;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.RoverBayBlockEntity;
import dev.aerolarge.entity.Rover;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The rover's remote: paired with one rover (by using it on the rover, or on its bay's panel, whose remote button
 * hands one out ready paired), its light shows green. Used, it drives the rover: the holder's view goes to a camera
 * behind and over it, their movement keys steer it and jump sounds its horn, and sneaking gives them their own view
 * back (see the client's {@code RoverDriving}); with the rover still in its cradle, it sends it down first, as the
 * panel does. Used while sneaking, it calls the rover home.
 */
public class RoverRemoteItem extends Item {
    /** How loud and how high the chime is as a remote is paired, and the low note when its rover does not answer. */
    private static final float PAIRED_VOLUME = 0.6F;
    private static final float PAIRED_PITCH = 1.8F;
    private static final float NO_ANSWER_VOLUME = 0.8F;
    private static final float NO_ANSWER_PITCH = 0.5F;

    /**
     * Driving the rover a remote is paired with, by the rover's id, from the client that used it: set by the client, so
     * nothing of the driving is loaded on a server.
     */
    public static Consumer<UUID> clientUse = rover -> { };

    public RoverRemoteItem(Properties properties) {
        super(properties);
    }

    /** The rover a remote is paired with, by its id; null for one not paired. */
    @Nullable
    public static UUID pairing(ItemStack stack) {
        return stack.get(AerolargeRegistry.ROVER_PAIRING.get());
    }

    /** A new remote paired with a rover. */
    public static ItemStack pairedWith(Rover rover) {
        ItemStack remote = new ItemStack(AerolargeRegistry.ROVER_REMOTE.get());
        remote.set(AerolargeRegistry.ROVER_PAIRING.get(), rover.getUUID());
        return remote;
    }

    /** Pairs a remote with a rover, with a chime and a word. */
    public static void pair(ItemStack remote, Rover rover, ServerPlayer player) {
        remote.set(AerolargeRegistry.ROVER_PAIRING.get(), rover.getUUID());
        player.displayClientMessage(Component.translatable("message.aerolarge.rover.paired"), true);
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS,
            PAIRED_VOLUME, PAIRED_PITCH);
    }

    /** Whether a player carries a remote paired with a rover, anywhere about them. */
    public static boolean carries(Player player, UUID rover) {
        for (ItemStack stack : player.getInventory().items) {
            if (paired(stack, rover)) {
                return true;
            }
        }
        return paired(player.getOffhandItem(), rover);
    }

    /** Whether a player holds a remote paired with a rover in either hand. */
    public static boolean inHand(Player player, UUID rover) {
        return paired(player.getMainHandItem(), rover) || paired(player.getOffhandItem(), rover);
    }

    private static boolean paired(ItemStack stack, UUID rover) {
        return stack.is(AerolargeRegistry.ROVER_REMOTE.get()) && rover.equals(pairing(stack));
    }

    /**
     * Used: on the client, the driving begins, unless sneaking; on the server, a rover in its cradle is sent down, and
     * sneaking calls it home. A remote not paired, or whose rover does not answer, says so.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        UUID paired = pairing(stack);
        if (level.isClientSide) {
            if (paired != null && !player.isShiftKeyDown()) {
                clientUse.accept(paired);
            }
            return InteractionResultHolder.success(stack);
        }
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.pass(stack);
        }
        if (paired == null) {
            server.displayClientMessage(Component.translatable("message.aerolarge.rover.unpaired"), true);
            return InteractionResultHolder.success(stack);
        }
        Rover rover = Rover.find(level, paired);
        if (rover == null || !rover.withinReach(server)) {
            server.displayClientMessage(Component.translatable("message.aerolarge.rover.no_answer"), true);
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS,
                NO_ANSWER_VOLUME, NO_ANSWER_PITCH);
        } else if (player.isShiftKeyDown()) {
            callHome(server, rover);
        } else if (rover.state() == Rover.State.STOWED && level.getBlockEntity(rover.bay()) instanceof RoverBayBlockEntity bay) {
            bay.deploy((ServerLevel) level, server);
        }
        return InteractionResultHolder.success(stack);
    }

    /** Calls a rover home from its remote, saying so. */
    private static void callHome(ServerPlayer player, Rover rover) {
        String said = switch (rover.state()) {
            case OUT -> "coming_home";
            case STOWED -> "already_home";
            default -> "busy";
        };
        rover.comeHome(false);
        player.displayClientMessage(Component.translatable("message.aerolarge.rover." + said), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String line = pairing(stack) == null ? "item.aerolarge.rover_remote.unpaired" : "item.aerolarge.rover_remote.paired";
        tooltip.add(Component.translatable(line).withStyle(ChatFormatting.GRAY));
    }
}
