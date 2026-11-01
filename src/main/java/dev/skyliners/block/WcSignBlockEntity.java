package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a WC sign watches: the room inside its WC, two opposite corners of it held as offsets from the sign, so they
 * hold once the airliner is turned, assembled and flown. A few times a second it looks whether anyone is in the room
 * (anyone alive, a player or a villager alike, their middle inside it), and only when the answer changes does it light
 * the sign the other colour. One look is a single query of the entities round the room, so a sign costs next to
 * nothing. The airliner's blueprint gives each sign its room; one put up by hand watches the room behind the wall it is
 * set into, as a sign over a WC's door would have it.
 */
public class WcSignBlockEntity extends BlockEntity {
    /** How often the sign looks into its room, in ticks: it changes colour within half a second of someone going in. */
    private static final int LOOK_TICKS = 10;
    /**
     * The room behind a sign put up by hand: from the wall it is set into back this many blocks, a block either side
     * of it, and from the door's two rows under it up to its own row.
     */
    private static final int ROOM_DEPTH = 3;
    private static final int ROOM_SIDE = 1;
    private static final int DOOR_ROWS = 2;

    /** The room's corners, as offsets from the sign; null until the blueprint sets them. */
    @Nullable
    private BlockPos from;
    @Nullable
    private BlockPos to;

    public WcSignBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.WC_SIGN_ENTITY.get(), pos, state);
    }

    /** Hands the sign the room it watches, its corners (every block from one to the other) as offsets from the sign. */
    public void watch(BlockPos from, BlockPos to) {
        this.from = from;
        this.to = to;
        setChanged();
    }

    void tick() {
        // Each sign looks on a tick of its own, so the airliner's signs do not all look at once.
        if (!(level instanceof ServerLevel server) || Math.floorMod(server.getGameTime() + worldPosition.hashCode(), LOOK_TICKS) != 0) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof WcSignBlock)) {
            return;
        }
        boolean occupied = anyoneIn(server, room(state.getValue(WcSignBlock.FACING)));
        if (occupied != state.getValue(WcSignBlock.OCCUPIED)) {
            server.setBlock(worldPosition, state.setValue(WcSignBlock.OCCUPIED, occupied), Block.UPDATE_CLIENTS);
        }
    }

    /** The room, among the sign's own blocks: as the blueprint set it, or behind the wall the sign faces out of. */
    private AABB room(Direction facing) {
        if (from != null && to != null) {
            return AABB.encapsulatingFullBlocks(worldPosition.offset(from), worldPosition.offset(to));
        }
        Direction back = facing.getOpposite();
        Direction side = back.getClockWise();
        return AABB.encapsulatingFullBlocks(
            worldPosition.relative(back).relative(side, -ROOM_SIDE).below(DOOR_ROWS),
            worldPosition.relative(back, ROOM_DEPTH).relative(side, ROOM_SIDE));
    }

    /**
     * Whether anyone is in the room, given among the sign's own blocks. Aboard an assembled airliner the sign lives in
     * Sable's plot while whoever is aboard is in the world, so the world is searched round where the room is now, as
     * far as its corners reach however the airliner is turned, and each one found is looked at in the airliner's frame.
     */
    private boolean anyoneIn(ServerLevel level, AABB room) {
        Vec3 middle = SableFlight.toWorld(level, room.getCenter());
        double reach = Math.sqrt(room.getXsize() * room.getXsize() + room.getYsize() * room.getYsize() + room.getZsize() * room.getZsize()) / 2;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(middle, middle).inflate(reach), WcSignBlockEntity::someone)) {
            if (room.contains(SableFlight.toPlot(level, worldPosition, entity.getBoundingBox().getCenter(), false))) {
                return true;
            }
        }
        return false;
    }

    /** Someone who takes a WC: anything alive, but not a spectator or an armour stand. */
    private static boolean someone(LivingEntity entity) {
        return entity.isAlive() && !entity.isSpectator() && !(entity instanceof ArmorStand);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (from != null && to != null) {
            tag.put("RoomFrom", NbtUtils.writeBlockPos(from));
            tag.put("RoomTo", NbtUtils.writeBlockPos(to));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        from = NbtUtils.readBlockPos(tag, "RoomFrom").orElse(null);
        to = NbtUtils.readBlockPos(tag, "RoomTo").orElse(null);
    }
}
