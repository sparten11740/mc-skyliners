package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A burning signal flare: how long it has left and the colour of its smoke. While it burns it sends up a tall column
 * of that smoke, seen from far off and high up, and it is kept track of so a care package that lands can say how far
 * from it it came down.
 */
public class SignalFlareBlockEntity extends BlockEntity {
    private static final Loaded<SignalFlareBlockEntity> LOADED = new Loaded<>(SignalFlareBlockEntity.class);
    /** How long a flare burns, in ticks: three minutes. */
    private static final int BURN_TICKS = 3 * 60 * 20;
    /** Puffs of smoke sent up each tick, and ticks between the flare's hiss. */
    private static final int PUFFS = 2;
    private static final int HISS_EVERY = 40;

    private int left = BURN_TICKS;
    private DyeColor colour = DyeColor.RED;

    public SignalFlareBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.SIGNAL_FLARE_ENTITY.get(), pos, state);
    }

    /** Every flare burning in the level. */
    public static List<SignalFlareBlockEntity> all(Level level) {
        return LOADED.in(level);
    }

    public DyeColor colour() {
        return colour;
    }

    public void setColour(DyeColor colour) {
        this.colour = colour;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, SignalFlareBlockEntity flare) {
        if (--flare.left <= 0) {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.0F);
            level.removeBlock(pos, false);
            return;
        }
        if (flare.left % HISS_EVERY == 0) {
            level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.6F);
        }
        flare.setChanged();
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, SignalFlareBlockEntity flare) {
        RandomSource random = level.getRandom();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        ColorParticleOption smoke = ColorParticleOption.create(SkylinersRegistry.FLARE_SMOKE.get(), 0xFF000000 | flare.colour.getTextureDiffuseColor());
        for (int i = 0; i < PUFFS; i++) {
            level.addAlwaysVisibleParticle(smoke, true, x + random.nextGaussian() * 0.1, y, z + random.nextGaussian() * 0.1, 0, 0, 0);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FLAME, x, y + 0.1, z, 0, 0.02, 0);
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
        tag.putInt("Left", left);
        tag.putString("Colour", colour.getSerializedName());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        left = tag.contains("Left") ? tag.getInt("Left") : BURN_TICKS;
        colour = DyeColor.byName(tag.getString("Colour"), DyeColor.RED);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Colour", colour.getSerializedName());
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
