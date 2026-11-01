package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A cockpit door's swing, kept in its bottom left part: where the door is going, open or shut, and how far through its
 * swing it is, ticked alike on the server and on every client, which draws the panel from it. Opening, the doorway is
 * clear at once while the panel swings out; shutting, it swings back and only stops the doorway again once it is
 * shut, and it waits while anyone stands in the doorway. The heavy clunk of a reinforced door unlatching and shutting.
 * Locked from the flight deck, it does not open from the aisle: whoever tries it there hears it rattle against its
 * lock, and the lamp over its handles glows red rather than green.
 */
public class CockpitDoorBlockEntity extends BlockEntity {
    /** How long the door takes to open or shut, in ticks: a second. */
    private static final int SWING_TICKS = 20;
    /** How far out past the doorway's outer face anyone still stands in the way of the door shutting, in blocks. */
    private static final double IN_THE_WAY = 0.4;
    /** The pitch of the door's clunk, a heavy door's, and of its bolt's click, a small bolt's. */
    private static final float DOOR_PITCH = 0.9F;
    private static final float BOLT_PITCH = 1.4F;

    /** Where it is going: open, or shut. */
    private boolean open;
    /** How far through its swing it is, in ticks: 0 shut, {@link #SWING_TICKS} open; and where it was a tick ago. */
    private int ticks;
    private int lastTicks;
    /** Shutting is held up by someone in the doorway. */
    private boolean held;
    /** Locked from the flight deck, so it does not open from the aisle. */
    private boolean locked;

    public CockpitDoorBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.COCKPIT_DOOR_ENTITY.get(), pos, state);
    }

    /** The door any part at this spot belongs to, or null where there is no door or its bottom left part is gone. */
    @Nullable
    public static CockpitDoorBlockEntity of(BlockGetter level, BlockPos part) {
        BlockState state = level.getBlockState(part);
        if (!CockpitDoorBlock.isDoor(state)) {
            return null;
        }
        return level.getBlockEntity(CockpitDoorBlock.controller(state, part)) instanceof CockpitDoorBlockEntity door ? door : null;
    }

    /** How far through its swing the door is, 0 shut to 1 open, between the last tick and this one. */
    public float progress(float partialTick) {
        return Mth.lerp(partialTick, lastTicks, ticks) / SWING_TICKS;
    }

    /** How many rows tall the door is. */
    public int rows() {
        return level == null ? 0 : CockpitDoorBlock.rows(level, worldPosition);
    }

    /** Whether it is locked from the flight deck, which its lamp shows. */
    public boolean locked() {
        return locked;
    }

    /** Whether it is open or opening. */
    public boolean opening() {
        return open;
    }

    /** Locked or unlocked from the flight deck, with the click of its bolt; whoever did it is told which. */
    public void toggleLock(Player player) {
        locked = !locked;
        sound(locked ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN, BOLT_PITCH);
        player.displayClientMessage(Component.translatable("message.skyliners.cockpit_door." + (locked ? "locked" : "unlocked")), true);
        sync();
    }

    /** Tried from the aisle while locked: it rattles against its lock, and whoever tried it is told why. */
    public void rattle(Player player) {
        sound(SoundEvents.CHEST_LOCKED, 1.0F);
        player.displayClientMessage(Component.translatable("message.skyliners.cockpit_door.locked_out"), true);
    }

    /** Someone used the door: it opens, or shuts. */
    public void use() {
        if (level == null) {
            return;
        }
        open = !open;
        held = false;
        sound(open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, DOOR_PITCH);
        if (open) {
            clear(true);
        }
        sync();
    }

    /** A sound from the middle of the doorway, at this pitch. */
    private void sound(SoundEvent sound, float pitch) {
        if (level != null) {
            Vec3 middle = doorway().getCenter();
            level.playSound(null, middle.x, middle.y, middle.z, sound, SoundSource.BLOCKS, 1.0F, pitch);
        }
    }

    /** Sets the doorway clear, or stops it: every part's OPEN. */
    private void clear(boolean clear) {
        if (level == null) {
            return;
        }
        for (BlockPos part : CockpitDoorBlock.parts(level, worldPosition)) {
            BlockState state = level.getBlockState(part);
            if (state.getValue(CockpitDoorBlock.OPEN) != clear) {
                level.setBlock(part, state.setValue(CockpitDoorBlock.OPEN, clear), Block.UPDATE_ALL);
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CockpitDoorBlockEntity door) {
        if (level instanceof ServerLevel server) {
            door.tick(server);
        }
    }

    private void tick(ServerLevel level) {
        lastTicks = ticks;
        if (open) {
            if (ticks < SWING_TICKS) {
                ticks++;
                setChanged();
            }
            return;
        }
        if (ticks == 0) {
            // A door left half open by whatever put it there (a command, say) settles shut.
            if (getBlockState().getValue(CockpitDoorBlock.OPEN)) {
                clear(false);
            }
            return;
        }
        boolean blocked = inTheWay(level);
        if (blocked != held) {
            held = blocked;
            sync();
        }
        if (held) {
            return;
        }
        ticks--;
        setChanged();
        if (ticks == 0) {
            clear(false);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, CockpitDoorBlockEntity door) {
        door.lastTicks = door.ticks;
        if (!door.held) {
            door.ticks = Math.clamp(door.ticks + (door.open ? 1 : -1), 0, SWING_TICKS);
        }
    }

    /** Whether anyone stands in the doorway, or just outside it where the shut panel would be. */
    private boolean inTheWay(ServerLevel level) {
        AABB doorway = doorway();
        Direction out = getBlockState().getValue(CockpitDoorBlock.FACING);
        AABB way = doorway.expandTowards(out.getStepX() * IN_THE_WAY, 0, out.getStepZ() * IN_THE_WAY);
        // Looked for in the world round the doorway, then seen in the door's own blocks: aboard an assembled airliner
        // those are not where the world has them.
        Vec3 middle = SableFlight.toWorld(level, way.getCenter());
        AABB around = new AABB(middle, middle).inflate(CockpitDoorBlock.MOST_ROWS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, around, LivingEntity::isAlive)) {
            Vec3 centre = SableFlight.toPlot(level, worldPosition, entity.getBoundingBox().getCenter(), false);
            if (way.intersects(AABB.ofSize(centre, entity.getBbWidth(), entity.getBbHeight(), entity.getBbWidth()))) {
                return true;
            }
        }
        return false;
    }

    /** The whole doorway, every block of it, frame and all, in the door's own blocks. */
    private AABB doorway() {
        BlockState state = getBlockState();
        Direction right = CockpitDoorBlock.right(state);
        BlockPos corner = worldPosition.relative(right, CockpitDoorBlock.columns(state) - 1).above(Math.max(rows(), 1) - 1);
        return AABB.encapsulatingFullBlocks(worldPosition, corner);
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
        tag.putBoolean("Locked", locked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        open = tag.getBoolean("Open");
        int count = ToldTicks.count(level, ticks, Math.clamp(tag.getInt("Ticks"), 0, SWING_TICKS), open);
        lastTicks = count == ticks ? lastTicks : count;
        ticks = count;
        held = tag.getBoolean("Held");
        // Saved before it could be locked: unlocked.
        locked = tag.getBoolean("Locked");
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
