package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A plug door's motor, kept in its bottom left part: where the door is going, open or shut, and how far through its
 * swing it is, ticked alike on the server and on every client, which draws the panel from it. Opening, the doorway is
 * clear at once while the panel lifts, moves out of the frame and swings away; shutting, it swings back and only
 * stops the doorway again once it is home in the frame and latched, and it waits, a chime saying so, while anyone
 * stands where the panel goes back in. A pneumatic hiss as it starts to move, a clunk of its latches as it unlatches
 * and as it shuts.
 *
 * <p>Villagers find their way through it as through a wooden door: while the airliner stands still (or is not
 * assembled at all), it opens for a villager that walks up to it from either side, and shuts again behind it once no
 * villager has been at it for a moment. So passengers board and leave by whatever door they come to; in flight no
 * villager opens it, and a door a villager opened shuts by itself. A door opened by hand, by redstone, by the airstairs
 * or by a jet bridge stays as it was put.
 */
public class PlugDoorBlockEntity extends BlockEntity {
    /** How long the door takes to open or shut, in ticks: a second and a quarter. */
    private static final int SWING_TICKS = 25;
    /** How often it looks for villagers at it, in ticks. */
    private static final int LOOK_TICKS = 10;
    /** How long a door a villager opened stays open once no villager is at it, in ticks. */
    private static final int VILLAGER_LINGER = 40;
    /** How far out from the doorway, either side, a villager is at the door, in blocks. */
    private static final double APPROACH = 1.25;
    /** Faster than this, in blocks a second, the airliner is moving, and no villager opens the door. */
    private static final double MOVING = 0.5;
    /**
     * How far out of the frame the panel moves before it swings away, in blocks: clear of the hull by a little more
     * than the panel is thick.
     */
    public static final float PLUG = 0.3F;
    private static final float HISS_VOLUME = 0.8F;
    private static final float LATCH_VOLUME = 0.9F;
    /** The latches clunk higher coming free than going home. */
    private static final float UNLATCH_PITCH = 1.25F;

    /** Where it is going: open, or shut. */
    private boolean open;
    /** How far through its swing it is, in ticks: 0 shut, {@link #SWING_TICKS} open; and where it was a tick ago. */
    private int ticks;
    private int lastTicks;
    /** Shutting is held up by someone in the way. */
    private boolean held;
    /** Whether any part of the door has a redstone signal. */
    private boolean powered;
    /** Whether a villager opened it, so it shuts again behind it; and how long no villager has been at it, in ticks. */
    private boolean forVillagers;
    private int villagersGone;

    public PlugDoorBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.PLUG_DOOR_ENTITY.get(), pos, state);
    }

    /** The door any part at this spot belongs to, or null where there is no door or its bottom left part is gone. */
    @Nullable
    public static PlugDoorBlockEntity of(BlockGetter level, BlockPos part) {
        BlockState state = level.getBlockState(part);
        if (!PlugDoorBlock.isDoor(state)) {
            return null;
        }
        return level.getBlockEntity(PlugDoorBlock.controller(state, part)) instanceof PlugDoorBlockEntity door ? door : null;
    }

    /** How far through its swing the door is, 0 shut to 1 open, between the last tick and this one. */
    public float progress(float partialTick) {
        return Mth.lerp(partialTick, lastTicks, ticks) / SWING_TICKS;
    }

    /** How many rows tall the door is. */
    public int rows() {
        return level == null ? 0 : PlugDoorBlock.rows(level, worldPosition);
    }

    /** Someone used the door: it opens, or shuts. */
    public void use() {
        forVillagers = false;
        move(!open);
    }

    /** Opens the door, or shuts it, for what works it: a redstone signal, the airstairs, a jet bridge. */
    public void set(boolean open) {
        forVillagers = false;
        move(open);
    }

    /** A part's neighbours changed: with a redstone signal at any part the door opens, and with none left it shuts. */
    void signal() {
        if (level == null) {
            return;
        }
        boolean signal = false;
        for (BlockPos part : PlugDoorBlock.parts(level, worldPosition)) {
            signal |= level.hasNeighborSignal(part);
        }
        if (signal != powered) {
            powered = signal;
            setChanged();
            set(signal);
        }
    }

    private void move(boolean open) {
        if (open == this.open || level == null) {
            return;
        }
        this.open = open;
        held = false;
        sound(AerolargeRegistry.PLUG_DOOR_HISS.get(), HISS_VOLUME, 1.0F);
        if (open) {
            sound(AerolargeRegistry.PLUG_DOOR_LATCH.get(), LATCH_VOLUME, UNLATCH_PITCH);
            clear(true);
        }
        sync();
    }

    /** A sound from the middle of the doorway. */
    private void sound(SoundEvent sound, float volume, float pitch) {
        if (level != null) {
            Vec3 middle = middle();
            level.playSound(null, middle.x, middle.y, middle.z, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    /** Sets the doorway clear, or stops it: every part's OPEN. */
    private void clear(boolean clear) {
        if (level == null) {
            return;
        }
        for (BlockPos part : PlugDoorBlock.parts(level, worldPosition)) {
            BlockState state = level.getBlockState(part);
            if (state.getValue(PlugDoorBlock.OPEN) != clear) {
                level.setBlock(part, state.setValue(PlugDoorBlock.OPEN, clear), Block.UPDATE_ALL);
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PlugDoorBlockEntity door) {
        if (level instanceof ServerLevel server) {
            door.tick(server);
        }
    }

    private void tick(ServerLevel level) {
        // Spread over the ticks between looks, so an airliner's doors do not all look on the same one.
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), LOOK_TICKS) == 0) {
            villagers(level);
        }
        lastTicks = ticks;
        if (open) {
            if (ticks < SWING_TICKS) {
                ticks++;
                setChanged();
            }
            return;
        }
        if (ticks == 0) {
            // A door left half open by whatever put it there (a command, an older version) settles shut.
            if (getBlockState().getValue(PlugDoorBlock.OPEN)) {
                clear(false);
            }
            return;
        }
        boolean blocked = inTheWay(level);
        if (blocked != held) {
            held = blocked;
            if (held) {
                sound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 0.7F);
            }
            sync();
        }
        if (held) {
            return;
        }
        ticks--;
        setChanged();
        if (ticks == 0) {
            sound(AerolargeRegistry.PLUG_DOOR_LATCH.get(), LATCH_VOLUME, 1.0F);
            clear(false);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, PlugDoorBlockEntity door) {
        door.lastTicks = door.ticks;
        if (door.held) {
            return;
        }
        door.ticks = Math.clamp(door.ticks + (door.open ? 1 : -1), 0, SWING_TICKS);
    }

    /**
     * Opens the door for a villager at it, shut and standing still, and shuts a door it opened for villagers once none
     * has been at it for a while.
     */
    private void villagers(ServerLevel level) {
        boolean near = villagerAt(level);
        villagersGone = near ? 0 : Math.min(villagersGone + LOOK_TICKS, VILLAGER_LINGER);
        if (near && !open && ticks == 0 && SableFlight.velocity(level, middle()).length() <= MOVING) {
            forVillagers = true;
            move(true);
        } else if (open && forVillagers && villagersGone >= VILLAGER_LINGER) {
            forVillagers = false;
            move(false);
        }
    }

    /** Whether a villager on its feet stands at the doorway, on either side of it. */
    private boolean villagerAt(ServerLevel level) {
        AABB approach = across(-1 - APPROACH, APPROACH).inflate(0, 0.5, 0);
        List<Villager> near = level.getEntitiesOfClass(Villager.class, around(level, APPROACH + 1),
            villager -> villager.isAlive() && !villager.isPassenger());
        for (Villager villager : near) {
            if (approach.contains(SableFlight.toPlot(level, worldPosition, villager.position(), false))) {
                return true;
            }
        }
        return false;
    }

    /** Whether anyone stands where the panel goes back into its frame: in it, or just outside, where it comes in from. */
    private boolean inTheWay(ServerLevel level) {
        AABB panel = across(-PlugDoorBlock.THICKNESS / 16.0, PLUG);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, around(level, 1), LivingEntity::isAlive)) {
            if (panel.intersects(inPlot(level, entity))) {
                return true;
            }
        }
        return false;
    }

    /** An entity's box seen in the door's own blocks: where it is aboard an assembled airliner, as the door is. */
    private AABB inPlot(ServerLevel level, Entity entity) {
        Vec3 centre = SableFlight.toPlot(level, worldPosition, entity.getBoundingBox().getCenter(), false);
        return AABB.ofSize(centre, entity.getBbWidth(), entity.getBbHeight(), entity.getBbWidth());
    }

    /** Around the doorway in the world, this far out each way: where to look for anyone at it. */
    private AABB around(ServerLevel level, double reach) {
        Vec3 middle = SableFlight.toWorld(level, middle());
        double across = PlugDoorBlock.COLUMNS / 2.0 + reach;
        return new AABB(middle, middle).inflate(across, PlugDoorBlock.MOST_ROWS / 2.0 + reach, across);
    }

    /** The whole doorway, every block of it. */
    private AABB doorway() {
        Direction right = PlugDoorBlock.right(getBlockState());
        BlockPos corner = worldPosition.relative(right, PlugDoorBlock.COLUMNS - 1).above(Math.max(rows(), 1) - 1);
        return AABB.encapsulatingFullBlocks(worldPosition, corner);
    }

    /**
     * The doorway, as wide and tall as it is, from {@code from} to {@code to} blocks out from the hull's outer face
     * (negative in toward the cabin).
     */
    private AABB across(double from, double to) {
        AABB doorway = doorway();
        Direction out = getBlockState().getValue(PlugDoorBlock.FACING);
        Direction.Axis axis = out.getAxis();
        int step = out.getAxisDirection().getStep();
        double face = step > 0 ? doorway.max(axis) : doorway.min(axis);
        double a = face + step * from;
        double b = face + step * to;
        return switch (axis) {
            case X -> new AABB(Math.min(a, b), doorway.minY, doorway.minZ, Math.max(a, b), doorway.maxY, doorway.maxZ);
            case Z -> new AABB(doorway.minX, doorway.minY, Math.min(a, b), doorway.maxX, doorway.maxY, Math.max(a, b));
            case Y -> doorway;
        };
    }

    /** The middle of the doorway, in the door's own blocks: where it is heard. */
    private Vec3 middle() {
        return doorway().getCenter();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Open", open);
        tag.putInt("Ticks", ticks);
        tag.putBoolean("Held", held);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("ForVillagers", forVillagers);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        open = tag.getBoolean("Open");
        ticks = Math.clamp(tag.getInt("Ticks"), 0, SWING_TICKS);
        lastTicks = ticks;
        held = tag.getBoolean("Held");
        powered = tag.getBoolean("Powered");
        forVillagers = tag.getBoolean("ForVillagers");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
