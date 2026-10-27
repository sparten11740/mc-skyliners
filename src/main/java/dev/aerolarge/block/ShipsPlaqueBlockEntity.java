package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
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
 * A ship's plaque's block entity: the name the zeppelin was christened with, empty for none, her crest, the banner
 * flown as her colours when she was, who christened her and when, so every client paints her crest and name on her
 * flanks together, sweeping back from the nose. Kept track of on the clients too, so her flanks are painted.
 */
public class ShipsPlaqueBlockEntity extends BlockEntity {
    private static final Loaded<ShipsPlaqueBlockEntity> LOADED = new Loaded<>(ShipsPlaqueBlockEntity.class, true);

    private String name = "";
    private ItemStack crest = ItemStack.EMPTY;
    /** Whether she has been christened at all, with or without a name; who christened her, and at what game time. */
    private boolean christened;
    private String by = "";
    private long when;

    public ShipsPlaqueBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.SHIPS_PLAQUE_ENTITY.get(), pos, state);
    }

    /** The plaques loaded in a level, on the server or on this client. */
    public static List<ShipsPlaqueBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    public boolean christened() {
        return christened;
    }

    /** The name she was christened with; empty for none. */
    public String name() {
        return name;
    }

    /** The name of whoever christened her. */
    public String by() {
        return by;
    }

    /** The game time she was christened at. */
    public long when() {
        return when;
    }

    /** Whether she has a crest. */
    public boolean hasCrest() {
        return !crest.isEmpty();
    }

    /** Her crest's base colour. */
    public DyeColor crestBase() {
        return crest.getItem() instanceof BannerItem item ? item.getColor() : DyeColor.WHITE;
    }

    /** The patterns laid on her crest, bottom first. */
    public BannerPatternLayers crestPatterns() {
        return crest.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
    }

    /** Christens her: the name she is given, her colours, a banner or nothing, to make her crest of, and by whom. */
    public void christen(String name, ItemStack colours, String by) {
        this.name = name;
        crest = colours.copyWithCount(1);
        this.by = by;
        when = level == null ? 0 : level.getGameTime();
        christened = true;
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
        tag.putBoolean("Christened", christened);
        tag.putString("Name", name);
        tag.putString("By", by);
        tag.putLong("When", when);
        if (!crest.isEmpty()) {
            tag.put("Crest", crest.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        christened = tag.getBoolean("Christened");
        name = tag.getString("Name");
        by = tag.getString("By");
        when = tag.getLong("When");
        crest = tag.contains("Crest") ? ItemStack.parseOptional(registries, tag.getCompound("Crest")) : ItemStack.EMPTY;
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
