package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.FlankScreen;
import dev.aerolarge.airframe.ZeppelinSize;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * A picture board's block entity: what its zeppelin's flank screens show (see {@link PictureBoardBlock}), which of
 * the posters, the map handed to it, when the picture last changed, so every client flickers it over together; and,
 * set when the zeppelin is built, where the screens are from the board: the point on the hull's axis level with their
 * front edge, as an offset from the board, and the way aft along it.
 */
public class PictureBoardBlockEntity extends BlockEntity {
    /** How often the map it holds is sent to the players in its level, in ticks, as a map in a frame is. */
    private static final int MAP_EVERY = 40;

    /** What the screens show. */
    public enum Picture {
        OFF, POSTERS, COLOURS, MAP, FACES, FLIGHT;

        static Picture named(String name) {
            for (Picture picture : values()) {
                if (picture.name().equals(name)) {
                    return picture;
                }
            }
            return OFF;
        }
    }

    /** The posters the screens show in turn, travel posters first and then adverts, each painted and headlined. */
    public enum Poster {
        NETHER, END, JUNGLE, PEAKS, CREEPER_INSURANCE, VILLAGER_BANK, GOLEM_GUARDS, CAKE_BY_AIR;

        /** The name its picture and its headline go by. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private Picture picture = Picture.POSTERS;
    private int poster;
    private ItemStack map = ItemStack.EMPTY;
    private long changed;
    @Nullable
    private BlockPos screens;
    private Direction aft = Direction.SOUTH;
    /** How big its zeppelin's hull is; full size for one saved before sizes were. */
    private ZeppelinSize size = ZeppelinSize.FULL;

    public PictureBoardBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.PICTURE_BOARD_ENTITY.get(), pos, state);
    }

    public Picture picture() {
        return picture;
    }

    public Poster poster() {
        return Poster.values()[Math.floorMod(poster, Poster.values().length)];
    }

    /** The filled map handed to it; empty for none. */
    public ItemStack map() {
        return map;
    }

    /** The game time its picture last changed at. */
    public long changed() {
        return changed;
    }

    /**
     * Where the screens are from it: the point on the hull's axis level with their front edge; null if not built so.
     */
    @Nullable
    public BlockPos screens() {
        return screens;
    }

    /** The way aft along the hull's axis. */
    public Direction aft() {
        return aft;
    }

    /** How big its zeppelin's hull is, which says where on it the screens are (see {@link FlankScreen}). */
    public ZeppelinSize size() {
        return size;
    }

    /**
     * Where the screens are from it and the way aft, as the zeppelin is built (turned as she was placed), and how big
     * her hull is.
     */
    public void placeScreens(BlockPos axis, Direction aft, ZeppelinSize size) {
        screens = axis;
        this.aft = aft;
        this.size = size;
        setChanged();
    }

    /** Shows a picture: the next poster if posters are shown already. */
    void show(Picture shown) {
        if (shown == Picture.POSTERS && picture == Picture.POSTERS) {
            poster = Math.floorMod(poster + 1, Poster.values().length);
        }
        picture = shown;
        pictureChanged();
    }

    /** Takes a filled map, one of a stack, to show; whatever map it held before, handed back. */
    ItemStack hang(ItemStack maps) {
        ItemStack before = map;
        map = maps.copyWithCount(1);
        picture = Picture.MAP;
        pictureChanged();
        return before;
    }

    /** Hands its map back, and the screens go dark. */
    ItemStack unhang() {
        ItemStack before = map;
        map = ItemStack.EMPTY;
        picture = Picture.OFF;
        pictureChanged();
        return before;
    }

    /**
     * Once a tick on the server: every so often, the map it holds is sent to everyone in its level, so their screens
     * show it as it fills in, whether they have held it or not.
     */
    void serverTick() {
        if (map.isEmpty() || !(level instanceof ServerLevel server) || server.getGameTime() % MAP_EVERY != 0) {
            return;
        }
        MapId id = map.get(DataComponents.MAP_ID);
        MapItemSavedData data = id == null ? null : MapItem.getSavedData(id, server);
        if (data == null) {
            return;
        }
        for (ServerPlayer player : server.players()) {
            data.tickCarriedBy(player, map);
            Packet<?> update = data.getUpdatePacket(id, player);
            if (update != null) {
                player.connection.send(update);
            }
        }
    }

    private void pictureChanged() {
        changed = level == null ? 0 : level.getGameTime();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Picture", picture.name());
        tag.putInt("Poster", poster);
        tag.putLong("Changed", changed);
        if (!map.isEmpty()) {
            tag.put("Map", map.save(registries));
        }
        tag.putString("Size", size.name());
        if (screens != null) {
            tag.put("Screens", NbtUtils.writeBlockPos(screens));
        }
        tag.putString("Aft", aft.getSerializedName());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        picture = tag.contains("Picture") ? Picture.named(tag.getString("Picture")) : Picture.POSTERS;
        poster = tag.getInt("Poster");
        changed = tag.getLong("Changed");
        map = tag.contains("Map") ? ItemStack.parseOptional(registries, tag.getCompound("Map")) : ItemStack.EMPTY;
        screens = NbtUtils.readBlockPos(tag, "Screens").orElse(null);
        size = ZeppelinSize.named(tag.getString("Size"));
        Direction read = Direction.byName(tag.getString("Aft"));
        aft = read != null && read.getAxis().isHorizontal() ? read : Direction.SOUTH;
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
