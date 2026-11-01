package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A flag halyard's block entity: the banner flown as the ship's colours, if any, and when it was hoisted, so every
 * client sees it rise up the staff together and knows when it is closed up at the top. Kept track of on the clients
 * too, so a zeppelin's fins are painted with the colours flown over its Sky Deck.
 */
public class FlagHalyardBlockEntity extends BlockEntity {
    private static final Loaded<FlagHalyardBlockEntity> LOADED = new Loaded<>(FlagHalyardBlockEntity.class, true);
    /** How long the colours take to run up the staff, in ticks. */
    public static final int HOIST_TICKS = 40;

    private ItemStack banner = ItemStack.EMPTY;
    /** The game time the colours were hoisted at; long ago for colours flown before it was kept. */
    private long hoisted;

    public FlagHalyardBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.FLAG_HALYARD_ENTITY.get(), pos, state);
    }

    /** The halyards loaded in a level, on the server or on this client. */
    public static List<FlagHalyardBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** Whether colours are bent on, rising or flying. */
    public boolean flying() {
        return !banner.isEmpty();
    }

    /** The colours' banner, a copy of it; empty for none. */
    public ItemStack colours() {
        return banner.copy();
    }

    /** The colours' banner's base colour. */
    public DyeColor base() {
        return banner.getItem() instanceof BannerItem item ? item.getColor() : DyeColor.WHITE;
    }

    /** The patterns laid on the colours' banner, bottom first. */
    public BannerPatternLayers patterns() {
        return banner.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
    }

    /** How far up the staff the colours have run at a time, 0 at its foot to 1 closed up at its top. */
    public float hoist(double time) {
        return (float) Math.min(1, (time - hoisted) / HOIST_TICKS);
    }

    /** How many ticks the colours have still to rise before they are closed up at the top. */
    public long hoistLeft() {
        return level == null ? 0 : Math.max(0, hoisted + HOIST_TICKS - level.getGameTime());
    }

    /** Whether colours fly closed up at the top of the staff. */
    public boolean closedUp() {
        return flying() && hoistLeft() == 0;
    }

    /** Bends one of a stack of banners on and runs it up the staff. */
    void hoist(ItemStack banners) {
        banner = banners.copyWithCount(1);
        hoisted = level == null ? 0 : level.getGameTime();
        changed();
    }

    /** Strikes the colours: the banner, taken down. */
    ItemStack strike() {
        ItemStack struck = banner;
        banner = ItemStack.EMPTY;
        changed();
        return struck;
    }

    private void changed() {
        setChanged();
        if (level != null) {
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
        if (!banner.isEmpty()) {
            tag.put("Banner", banner.save(registries));
        }
        tag.putLong("Hoisted", hoisted);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        banner = tag.contains("Banner") ? ItemStack.parseOptional(registries, tag.getCompound("Banner")) : ItemStack.EMPTY;
        hoisted = tag.getLong("Hoisted");
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
