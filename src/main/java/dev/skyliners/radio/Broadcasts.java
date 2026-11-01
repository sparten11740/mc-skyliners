package dev.skyliners.radio;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.RadioBlockEntity;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Speaking over the airliner's PA and the radio. Using the flight deck's handset makes the player's next chat line an
 * announcement: everyone aboard hears the chime and sees it as a title, under the captain's name. Sneaking with the
 * handset, or using a tower radio, makes it a radio call instead: heard, with a crackle, by everyone near any radio in
 * range, the handsets of airliners in flight included. Either way the line leaves ordinary chat.
 */
public final class Broadcasts {
    /** How long the handset or radio stays keyed for the next line, in ticks. */
    private static final int KEYED_TICKS = 30 * 20;
    /** How far a radio call carries, and how close to a radio a player has to be to hear it, in blocks. */
    private static final double RANGE = 4096;
    private static final double HEARING = 16;
    /** The most of an announcement shown on screen, in characters; the rest is cut, and read in full in chat. */
    private static final int SUBTITLE_LENGTH = 48;

    /** What a keyed handset or radio sends the next line as. */
    public enum Kind {
        ANNOUNCEMENT, RADIO
    }

    private record Keyed(Kind kind, Level level, BlockPos source, long until) {
    }

    private static final Map<UUID, Keyed> KEYED = new HashMap<>();

    private Broadcasts() {
    }

    /** The player used a handset or radio: their next chat line goes out as the given kind; used again, it does not. */
    public static void key(ServerPlayer player, RadioBlockEntity source, Kind kind) {
        Keyed was = KEYED.remove(player.getUUID());
        if (was != null && was.kind() == kind && was.source().equals(source.getBlockPos())) {
            player.displayClientMessage(Component.translatable("message.skyliners.radio.released"), true);
            return;
        }
        KEYED.put(player.getUUID(), new Keyed(kind, source.getLevel(), source.getBlockPos(), player.level().getGameTime() + KEYED_TICKS));
        player.displayClientMessage(Component.translatable(kind == Kind.ANNOUNCEMENT
            ? "message.skyliners.radio.keyed_announcement" : "message.skyliners.radio.keyed_radio"), true);
    }

    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        Keyed keyed = KEYED.remove(player.getUUID());
        if (keyed == null || keyed.level() != player.level() || player.level().getGameTime() > keyed.until()
            || !(keyed.level().getBlockEntity(keyed.source()) instanceof RadioBlockEntity source)) {
            return;
        }
        event.setCanceled(true);
        String text = event.getRawText();
        if (keyed.kind() == Kind.ANNOUNCEMENT) {
            announce(player, source, text);
        } else {
            call(player, source, text);
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        KEYED.remove(event.getEntity().getUUID());
    }

    /** The PA: chime, title and a line in chat for everyone aboard, the speaker too. */
    private static void announce(ServerPlayer speaker, RadioBlockEntity handset, String text) {
        Component title = Component.translatable("title.skyliners.captain", speaker.getDisplayName()).withStyle(ChatFormatting.GOLD);
        Component subtitle = Component.literal(text.length() > SUBTITLE_LENGTH ? text.substring(0, SUBTITLE_LENGTH - 3) + "..." : text);
        Component line = Component.translatable("chat.skyliners.announcement", speaker.getDisplayName(), text).withStyle(ChatFormatting.GOLD);
        Set<ServerPlayer> listeners = new LinkedHashSet<>(Aboard.players(handset));
        listeners.add(speaker);
        for (ServerPlayer listener : listeners) {
            listener.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            listener.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            listener.connection.send(new ClientboundSetTitleTextPacket(title));
            listener.sendSystemMessage(line);
            hear(listener, SkylinersRegistry.PA_CHIME);
        }
    }

    /** A radio call: heard near every radio in range of the one it was made on, with the speaker always hearing it. */
    private static void call(ServerPlayer speaker, RadioBlockEntity radio, String text) {
        ServerLevel level = (ServerLevel) radio.getLevel();
        Vec3 from = radio.whereInWorld();
        Set<ServerPlayer> listeners = new LinkedHashSet<>();
        listeners.add(speaker);
        for (RadioBlockEntity other : RadioBlockEntity.all(level)) {
            Vec3 at = other.whereInWorld();
            if (at.distanceToSqr(from) > RANGE * RANGE) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (player.position().distanceToSqr(at) < HEARING * HEARING) {
                    listeners.add(player);
                }
            }
        }
        Component line = Component.translatable("chat.skyliners.radio", speaker.getDisplayName(), text).withStyle(ChatFormatting.GREEN);
        for (ServerPlayer listener : listeners) {
            listener.sendSystemMessage(line);
            hear(listener, SkylinersRegistry.RADIO_CRACKLE);
        }
    }

    /** Plays the sound to the one player, following them about. */
    private static void hear(ServerPlayer player, Holder<SoundEvent> sound) {
        player.connection.send(new ClientboundSoundEntityPacket(sound, SoundSource.VOICE, player, 1.0F, 1.0F, player.getRandom().nextLong()));
    }
}
