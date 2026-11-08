package dev.skyliners.stickers;

import dev.skyliners.Aboard;
import dev.skyliners.FirstFlight;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.EdgeOfSpace;
import dev.skyliners.autopilot.VoiceLength;
import dev.skyliners.block.Loaded;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The sticker map's block entity (see {@link StickerMapBlock}), on part 0: which stickers are up and the game time each
 * went up, saved by name and sent to clients whenever one goes up, for the client's {@code StickerMapRenderer} to pop
 * it on with its sparkles, and to shine a row or the whole board the moment it is full.
 *
 * <p>Aboard a ship it fills as she lands: her autopilot says she is down (see {@link #shipLanded}), and a board aboard
 * her puts up the sticker for the kind of land under her middle, if it has not got it; it puts up space itself the
 * moment it is carried up to the edge of space. Each sticker goes up with a fanfare, a full row with a bigger one and
 * the whole board with a celebration, and aboard the freighter her computer has its say once her cockpit is quiet. On
 * the ground, or aboard a ship with no autopilot, nothing lands it anywhere, so using it puts up a sticker for each
 * kind of land whoever uses it has been to, which the game keeps for its Adventuring Time advancement, and space if
 * they have reached it aboard a ship; they pop up one after another.
 */
public class StickerMapBlockEntity extends BlockEntity {
    private static final Loaded<StickerMapBlockEntity> LOADED = new Loaded<>(StickerMapBlockEntity.class);
    /** The advancement whose criteria are the biomes a player has been to, each named by its biome's id. */
    private static final ResourceLocation ADVENTURING_TIME = ResourceLocation.withDefaultNamespace("adventure/adventuring_time");
    /** No such sticker up yet. */
    public static final long NOT_UP = Long.MAX_VALUE;
    /** How long apart stickers put up together pop up one after another, in ticks. */
    public static final int POP_APART = 8;
    /** How loud the fanfares are: heard round her lounge and on along her corridor, sixteen blocks for each. */
    private static final float FANFARE_VOLUME = 2;
    /** How often a board aboard a ship looks whether it has been carried up to the edge of space, in ticks. */
    private static final int SPACE_LOOK = 20;
    /** How long a line of her voice waits for her cockpit to fall quiet at most, in ticks: a minute, then let go. */
    private static final int VOICE_WAIT_MOST = 60 * 20;
    /** How long a line of her voice is given, in ticks, if its sound cannot be read. */
    private static final int VOICE_LONGEST = 5 * 20;
    /** The low note of a board that puts nothing up, and how loud it is. */
    private static final float REFUSED_PITCH = 0.6F;
    private static final float REFUSED_VOLUME = 0.6F;

    /** How big a moment a sticker going up is: one more, a row filled, or the whole board, each its own fanfare. */
    public enum Celebration {
        STICKER(SkylinersRegistry.STICKER_MAP_NEW),
        ROW(SkylinersRegistry.STICKER_MAP_ROW),
        BOARD(SkylinersRegistry.STICKER_MAP_FULL);

        final Holder<SoundEvent> fanfare;

        Celebration(Holder<SoundEvent> fanfare) {
            this.fanfare = fanfare;
        }
    }

    /**
     * What a ship's own voice says as a sticker goes up, by what she is, after her board's fanfare: only the
     * freighter's computer speaks up; any other ship's board has its fanfare alone.
     */
    private static final Map<Craft, Map<Celebration, Holder<SoundEvent>>> SAID = Map.of(Craft.FREIGHTER, Map.of(
        Celebration.STICKER, SkylinersRegistry.FREIGHTER_COMPUTER_STICKER,
        Celebration.ROW, SkylinersRegistry.FREIGHTER_COMPUTER_STICKER_ROW,
        Celebration.BOARD, SkylinersRegistry.FREIGHTER_COMPUTER_STICKER_BOARD));

    /** Each sticker up, by the game time it went up. */
    private final EnumMap<Sticker, Long> up = new EnumMap<>(Sticker.class);
    /**
     * A line of her voice waiting for her cockpit to fall quiet, the autopilot whose cockpit it waits on and since
     * when, on the server; none waiting. Not saved: a board loaded again has nothing left to say.
     */
    @Nullable
    private Holder<SoundEvent> waitingLine;
    @Nullable
    private BlockPos waitingOn;
    private long waitingSince;

    public StickerMapBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.STICKER_MAP_ENTITY.get(), pos, state);
    }

    /** When a sticker went up, in game time; {@link #NOT_UP} for one not up. */
    public long upSince(Sticker sticker) {
        return up.getOrDefault(sticker, NOT_UP);
    }

    /**
     * A ship's autopilot says she is down, on the server: every board aboard her puts up the sticker for the kind of
     * land under her middle, if it has none yet, and her voice, if she has one, says so once, for the first board that
     * put it up, so two boards aboard her never have her say it twice.
     */
    public static void shipLanded(ServerLevel level, AutopilotBlockEntity autopilot) {
        if (!SkylinersConfig.FREIGHTER_STICKER_MAP.get()) {
            return;
        }
        SableFlight.Body body = SableFlight.body(autopilot);
        Vec3 middle = body != null ? body.centre() : SableFlight.toWorld(level, Vec3.atCenterOf(autopilot.getBlockPos()));
        Sticker sticker = Sticker.of(level.getBiome(BlockPos.containing(middle)));
        if (sticker == null) {
            return;
        }
        AutopilotBlockEntity voice = autopilot;
        for (StickerMapBlockEntity board : LOADED.in(level)) {
            if (SableFlight.sameAirliner(board, autopilot) && board.putUp(level, List.of(sticker), voice)) {
                voice = null;
            }
        }
    }

    /**
     * Used by a player, on the server: aboard a ship with an autopilot it only rustles, saying it fills as she lands;
     * anywhere else it puts up what they have been to, or with nothing new sounds a low note.
     */
    void used(ServerLevel level, ServerPlayer player) {
        if (!SkylinersConfig.FREIGHTER_STICKER_MAP.get()) {
            refuse(level, player, "message.skyliners.sticker_map.off");
            return;
        }
        if (AutopilotBlockEntity.shipOf(this) != null) {
            level.playSound(null, worldPosition, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.skyliners.sticker_map.aboard"), true);
            return;
        }
        List<Sticker> earned = travels(player);
        if (!putUp(level, earned, null)) {
            refuse(level, player, "message.skyliners.sticker_map.nothing_new");
        }
    }

    /** Says why nothing went up, with the low note under it for whoever cannot read. */
    private void refuse(ServerLevel level, ServerPlayer player, String key) {
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, REFUSED_VOLUME, REFUSED_PITCH);
        player.displayClientMessage(Component.translatable(key), true);
    }

    /**
     * The stickers a player's own travels earn: one for each kind of land among the biomes they have been to, as the
     * game keeps them for its Adventuring Time advancement, and space if they have been to its edge aboard a ship.
     */
    private static List<Sticker> travels(ServerPlayer player) {
        List<Sticker> earned = new ArrayList<>();
        AdvancementHolder adventuring = player.server.getAdvancements().get(ADVENTURING_TIME);
        Registry<Biome> biomes = player.server.registryAccess().registryOrThrow(Registries.BIOME);
        if (adventuring != null) {
            for (String criterion : player.getAdvancements().getOrStartProgress(adventuring).getCompletedCriteria()) {
                Sticker sticker = stickerOf(biomes, criterion);
                if (sticker != null && !earned.contains(sticker)) {
                    earned.add(sticker);
                }
            }
        }
        if (FirstFlight.EDGE_OF_SPACE.progress(player) > 0) {
            earned.add(Sticker.SPACE);
        }
        return earned;
    }

    /** The sticker for a biome named by its id; null for a name that is not a biome, or a biome no sticker shows. */
    @Nullable
    private static Sticker stickerOf(Registry<Biome> biomes, String id) {
        ResourceLocation name = ResourceLocation.tryParse(id);
        Optional<Holder.Reference<Biome>> biome = name == null ? Optional.empty() : biomes.getHolder(ResourceKey.create(Registries.BIOME, name));
        return biome.map(Sticker::of).orElse(null);
    }

    /**
     * Puts up whichever of the stickers given are not up yet, one after another, with the fanfare of the biggest moment
     * they make; and given a ship with a voice of her own, her line for it waits for her cockpit to fall quiet (none
     * given, the fanfare is all). Whether any went up.
     */
    boolean putUp(ServerLevel level, List<Sticker> stickers, @Nullable AutopilotBlockEntity ship) {
        List<Sticker> fresh = stickers.stream().filter(sticker -> !up.containsKey(sticker)).toList();
        if (fresh.isEmpty()) {
            return false;
        }
        long now = level.getGameTime();
        for (int k = 0; k < fresh.size(); k++) {
            up.put(fresh.get(k), now + (long) k * POP_APART);
        }
        Celebration celebration = celebration(fresh);
        level.playSound(null, worldPosition, celebration.fanfare.value(), SoundSource.BLOCKS, FANFARE_VOLUME, 1.0F);
        Map<Celebration, Holder<SoundEvent>> voice = ship == null ? null : SAID.get(ship.craft());
        if (voice != null) {
            waitingLine = voice.get(celebration);
            waitingOn = ship.getBlockPos();
            waitingSince = now;
        }
        changed();
        return true;
    }

    /** The biggest moment stickers just put up make: the board full, a row of them full, or only more stickers. */
    private Celebration celebration(List<Sticker> fresh) {
        if (up.size() == Sticker.values().length) {
            return Celebration.BOARD;
        }
        for (Sticker sticker : fresh) {
            if (rowFull(sticker.row())) {
                return Celebration.ROW;
            }
        }
        return Celebration.STICKER;
    }

    /** Whether every sticker of a row is up. */
    private boolean rowFull(int row) {
        for (Sticker sticker : Sticker.values()) {
            if (sticker.row() == row && !up.containsKey(sticker)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Each tick, on the server: carried up to the edge of space aboard a ship, it puts up space; and a line of her
     * voice waiting is said once her cockpit falls quiet.
     */
    void tick(ServerLevel level) {
        if (waitingLine != null) {
            sayWaiting(level);
        }
        if ((level.getGameTime() + worldPosition.asLong()) % SPACE_LOOK != 0 || up.containsKey(Sticker.SPACE)
            || !SkylinersConfig.FREIGHTER_STICKER_MAP.get()) {
            return;
        }
        Vec3 middle = Vec3.atCenterOf(worldPosition);
        if (SableFlight.aboard(level, middle) && EdgeOfSpace.reached(SableFlight.toWorld(level, middle).y)) {
            putUp(level, List.of(Sticker.SPACE), AutopilotBlockEntity.shipOf(this));
        }
    }

    /**
     * Says the line of her voice waiting, to everyone aboard wherever they are in her, once nothing else is said in her
     * cockpit, holding its floor while it lasts, so it never talks over her announcements or her ramp; let go unsaid
     * after {@link #VOICE_WAIT_MOST}, or with her autopilot gone.
     */
    private void sayWaiting(ServerLevel level) {
        AutopilotBlockEntity ship = waitingOn != null && level.getBlockEntity(waitingOn) instanceof AutopilotBlockEntity autopilot ? autopilot : null;
        if (ship == null || level.getGameTime() - waitingSince > VOICE_WAIT_MOST) {
            waitingLine = null;
            return;
        }
        if (!ship.quiet()) {
            return;
        }
        Holder<SoundEvent> line = waitingLine;
        waitingLine = null;
        ship.holdFloor(VoiceLength.ticks(line, VOICE_LONGEST));
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : Aboard.players(ship)) {
            player.connection.send(new ClientboundSoundEntityPacket(line, SoundSource.VOICE, player, 1.0F, 1.0F, seed));
        }
    }

    /** Saves, and sends its stickers to the clients. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag stickers = new CompoundTag();
        up.forEach((sticker, since) -> stickers.putLong(sticker.key(), since));
        tag.put("Stickers", stickers);
    }

    /** Read back as saved; a sticker this build does not have is left out. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        up.clear();
        CompoundTag stickers = tag.getCompound("Stickers");
        for (String key : stickers.getAllKeys()) {
            Sticker sticker = Sticker.byKey(key);
            if (sticker != null) {
                up.put(sticker, stickers.getLong(key));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
