package dev.aerolarge.block;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A docking clamp on a zeppelin's flight deck (see {@link DeckClampBlock}), on the server. Twice a second it looks for
 * a plane, another of Sable's flying bodies, come to rest on the deck round it; once one has rested a second it locks
 * it down to her with a fixed joint at its jaws, as Simulated's docking connectors join two ships, so it rides with
 * her however she flies, and every two seconds tops its tanks up with the fuel they hold and fills its batteries. A
 * plane is held by the first clamp that finds it; the others round it close on it too, as the jaws show. A plane
 * heavier than a tenth of her is refused with a low note: her pilot never counts what is joined to her. The deck's
 * release lever opens them all (see {@link CarrierLeverBlock}), and they stay open while it is down. Sable keeps no
 * joint over a save, so the clamp holding a plane locks it again once both are loaded.
 */
public class DeckClampBlockEntity extends BlockEntity {
    /** What a clamp tops a plane's tanks up with: fuels, and water for steam. */
    public static final TagKey<Fluid> FUEL = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "carrier_fuel"));
    private static final Loaded<DeckClampBlockEntity> LOADED = new Loaded<>(DeckClampBlockEntity.class);
    /** The planes held, by their ids, and the clamp that holds each: the clamps under a plane share the one lock. */
    private static final Map<UUID, DeckClampBlockEntity> HOLDING = new HashMap<>();
    /** How often it looks for a plane, in ticks, and how many looks in a row one must rest before it is locked. */
    private static final int LOOK_TICKS = 10;
    private static final int SETTLED_LOOKS = 2;
    /**
     * How far across the deck either way and fore and aft a clamp looks for a plane, and how far over the deck, its
     * wheels' suspension under it, in blocks: the four clamps between them cover the deck's middle, so a plane small or
     * large is found wherever it comes to rest there.
     */
    private static final double ACROSS = 7;
    private static final double REACH = 4;
    /** How fast a plane may still move against her to count as resting on the deck, in blocks a second. */
    private static final double STILL = 0.5;
    /** The heaviest plane it locks, as a share of her mass. */
    private static final double HEAVIEST = 0.1;
    /** How often it refuels a locked plane, in ticks, and how many of the plane's block entities it looks through. */
    private static final int REFUEL_TICKS = 40;
    private static final int MOST_PARTS = 512;
    /** How many looks a clamp that held a plane before a save waits for it to be loaded again. */
    private static final int RELOCK_LOOKS = 20;
    /**
     * The pitch of the low note a refused plane gets; how loud the clamps lock and open, heard once for a plane from
     * the clamp holding it; and the least a tank must take to be heard gurgling, in millibuckets.
     */
    private static final float REFUSED_PITCH = 0.5F;
    private static final float CLAMP_VOLUME = 1.2F;
    private static final int HEARD_FILLING = 250;

    /** The plane its jaws are closed on, and whether it is this clamp that holds the lock. */
    @Nullable
    private UUID plane;
    private boolean holder;
    @Nullable
    private SableFlight.Lock lock;
    /** Whether the deck's release lever is down, keeping it open. */
    private boolean released;
    /** The plane seen resting over it, and for how many looks in a row; and the last plane refused. */
    @Nullable
    private UUID resting;
    private int settled;
    @Nullable
    private UUID refused;
    /** Looks spent waiting for a plane held before a save; the plane's parts with tanks or batteries, in its plot. */
    private int waited;
    private final List<BlockPos> tanks = new ArrayList<>();

    public DeckClampBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.DECK_CLAMP_ENTITY.get(), pos, state);
    }

    /** The clamps loaded in a level, on the server. */
    public static List<DeckClampBlockEntity> loaded(ServerLevel level) {
        return LOADED.in(level);
    }

    /** The deck's release lever put down or back up: down, it opens and stays open; up, it locks planes again. */
    void released(ServerLevel level, boolean down) {
        released = down;
        setChanged();
        if (down) {
            open(level);
        }
    }

    void tick(ServerLevel level) {
        if (!AerolargeConfig.FLYING_CARRIER.get() || released) {
            open(level);
            return;
        }
        if (Math.floorMod(level.getGameTime() + worldPosition.hashCode(), LOOK_TICKS) != 0) {
            return;
        }
        if (plane == null) {
            look(level);
        } else if (holder) {
            hold(level);
        } else if (HOLDING.get(plane) == null) {
            open(level);
        }
    }

    /** Looks for a plane come to rest on the deck round it, and locks it down once it has rested long enough. */
    private void look(ServerLevel level) {
        UUID over = SableFlight.restingOver(this, ACROSS, REACH, STILL);
        settled = over != null && over.equals(resting) ? settled + 1 : 0;
        resting = over;
        if (over == null || settled < SETTLED_LOOKS) {
            return;
        }
        if (HOLDING.containsKey(over)) {
            close(level, over, false);
            return;
        }
        if (SableFlight.massOf(level, over) > HEAVIEST * SableFlight.mass(this)) {
            if (!over.equals(refused)) {
                refused = over;
                level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 1.0F, REFUSED_PITCH);
            }
            return;
        }
        lock = SableFlight.lock(this, over);
        if (lock != null) {
            close(level, over, true);
        }
    }

    /** The jaws closed on a plane with a clang, this clamp holding its lock or another one. */
    private void close(ServerLevel level, UUID onto, boolean holding) {
        plane = onto;
        holder = holding;
        if (holding) {
            HOLDING.put(onto, this);
            findTanks(level);
        }
        level.setBlock(worldPosition, getBlockState().setValue(DeckClampBlock.CLOSED, true), Block.UPDATE_ALL);
        if (holding) {
            level.playSound(null, worldPosition, AerolargeRegistry.CLAMP_LOCK.get(), SoundSource.BLOCKS, CLAMP_VOLUME, 1.0F);
        }
        setChanged();
    }

    /** Holding a plane: locked again after a save once it is about, opened if the lock has gone, and refuelled. */
    private void hold(ServerLevel level) {
        if (lock == null) {
            relock(level);
            return;
        }
        if (!lock.held()) {
            open(level);
            return;
        }
        if (level.getGameTime() % REFUEL_TICKS < LOOK_TICKS) {
            refuel(level);
        }
    }

    /** A plane held before a save locked again as it now lies, once it is loaded; given up after a while. */
    private void relock(ServerLevel level) {
        lock = SableFlight.lock(this, plane);
        if (lock != null) {
            HOLDING.put(plane, this);
            findTanks(level);
        } else if (++waited > RELOCK_LOOKS) {
            open(level);
        }
    }

    /** The plane's parts with tanks or batteries, looked for once when it is locked. */
    private void findTanks(ServerLevel level) {
        tanks.clear();
        for (BlockEntity part : SableFlight.blockEntitiesOf(level, plane, MOST_PARTS)) {
            BlockPos at = part.getBlockPos();
            if (level.getCapability(Capabilities.FluidHandler.BLOCK, at, null) != null || level.getCapability(Capabilities.EnergyStorage.BLOCK, at, null) != null) {
                tanks.add(at.immutable());
            }
        }
    }

    /** Tops the plane's tanks up with the fuel each holds and fills its batteries, with a gurgle if much went in. */
    private void refuel(ServerLevel level) {
        int filled = 0;
        for (BlockPos at : tanks) {
            filled += topUp(level.getCapability(Capabilities.FluidHandler.BLOCK, at, null));
            IEnergyStorage battery = level.getCapability(Capabilities.EnergyStorage.BLOCK, at, null);
            filled += battery != null && battery.receiveEnergy(Integer.MAX_VALUE, false) > 0 ? HEARD_FILLING : 0;
        }
        if (filled >= HEARD_FILLING) {
            level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.4F, 0.8F);
        }
    }

    /** Each tank holding a fuel topped up with it; how much went in, in millibuckets. */
    private static int topUp(@Nullable IFluidHandler handler) {
        int filled = 0;
        for (int tank = 0; handler != null && tank < handler.getTanks(); tank++) {
            FluidStack in = handler.getFluidInTank(tank);
            int room = handler.getTankCapacity(tank) - in.getAmount();
            if (!in.isEmpty() && room > 0 && in.is(FUEL)) {
                filled += handler.fill(in.copyWithAmount(room), IFluidHandler.FluidAction.EXECUTE);
            }
        }
        return filled;
    }

    /** The jaws opened with a clank, letting go of the plane if this clamp held it. */
    private void open(ServerLevel level) {
        if (plane == null && !getBlockState().getValue(DeckClampBlock.CLOSED)) {
            return;
        }
        if (holder) {
            level.playSound(null, worldPosition, AerolargeRegistry.CLAMP_RELEASE.get(), SoundSource.BLOCKS, CLAMP_VOLUME, 1.0F);
        }
        letGo();
        plane = null;
        settled = 0;
        waited = 0;
        level.setBlock(worldPosition, getBlockState().setValue(DeckClampBlock.CLOSED, false), Block.UPDATE_ALL);
        setChanged();
    }

    /** Lets go of the lock it holds, if it holds one. */
    private void letGo() {
        if (holder && plane != null && HOLDING.get(plane) == this) {
            HOLDING.remove(plane);
        }
        if (lock != null) {
            lock.release();
        }
        lock = null;
        holder = false;
        tanks.clear();
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
        letGo();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
        if (plane != null && HOLDING.get(plane) == this) {
            HOLDING.remove(plane);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (plane != null && holder) {
            tag.putUUID("Plane", plane);
        }
        tag.putBoolean("Released", released);
    }

    /** Only the clamp holding a plane keeps it over a save, to lock it again; the others find it again resting. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        plane = tag.hasUUID("Plane") ? tag.getUUID("Plane") : null;
        holder = plane != null;
        released = tag.getBoolean("Released");
    }
}
