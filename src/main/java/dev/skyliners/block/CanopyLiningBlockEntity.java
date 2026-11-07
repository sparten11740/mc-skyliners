package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the freighter's canopy lining knows (see {@link CanopyLiningBlock}): the cone her canopy's inside is drawn on,
 * a little in from her hull's surface so it stands at the inside of her glass, as where its base's middle is from the
 * middle of this block, in the world's directions, how far it runs toward her bow to its tip and how wide it is at
 * each end; where along it the ring of her frame goes, as a share of its length, and how many ribs run along it, evenly
 * round from her right; and one block of her canopy's glass, whose darkness the lining's glass follows as her window
 * dimmer darkens it. Set once as she is built (see {@link #shape}), turned with the block since, saved and sent to the
 * clients, which draw it.
 */
public class CanopyLiningBlockEntity extends BlockEntity {
    /** Before it is told its shape: none drawn. */
    private static final double NONE = 0;

    private Vec3 base = Vec3.ZERO;
    private double length = NONE;
    private double baseRadius;
    private double tipRadius;
    private double ringAlong;
    private int ribs;
    @Nullable
    private BlockPos glass;

    public CanopyLiningBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.CANOPY_LINING_ENTITY.get(), pos, state);
    }

    /** The cone a canopy's inside is drawn on, as she is built: all in blocks but the ring's share and the ribs' count. */
    public record Cone(Vec3 base, double length, double baseRadius, double tipRadius, double ringAlong, int ribs) {
        /** The cone turned with its ship by a quarter turn about the middle of the block it is kept by. */
        Cone turned(Rotation turn) {
            Vec3 turned = switch (turn) {
                case NONE -> base;
                case CLOCKWISE_90 -> new Vec3(-base.z, base.y, base.x);
                case CLOCKWISE_180 -> new Vec3(-base.x, base.y, -base.z);
                case COUNTERCLOCKWISE_90 -> new Vec3(base.z, base.y, -base.x);
            };
            return new Cone(turned, length, baseRadius, tipRadius, ringAlong, ribs);
        }
    }

    /** Told its cone and the glass to follow, as offsets from this block, turned as she was placed. */
    public void shape(Cone cone, @Nullable BlockPos glass, Rotation rotation) {
        Cone turned = cone.turned(rotation);
        base = turned.base();
        length = turned.length();
        baseRadius = turned.baseRadius();
        tipRadius = turned.tipRadius();
        ringAlong = turned.ringAlong();
        ribs = turned.ribs();
        this.glass = glass == null ? null : glass.rotate(rotation);
        setChanged();
    }

    /** The cone it draws; null before it is told one. */
    @Nullable
    public Cone cone() {
        return length <= NONE ? null : new Cone(base, length, baseRadius, tipRadius, ringAlong, ribs);
    }

    /** The way her canopy points, her bow. */
    public Direction nose() {
        BlockState state = getBlockState();
        return state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    /**
     * How dark her canopy's glass is, from clear (0) to all but black (3), as the block of it the lining follows says;
     * clear where that is not glass that dims.
     */
    public int darkness() {
        if (level == null || glass == null) {
            return 0;
        }
        BlockState pane = level.getBlockState(worldPosition.offset(glass));
        return pane.hasProperty(OvalWindowPieceBlock.DIM) ? pane.getValue(OvalWindowPieceBlock.DIM) : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveShape(tag);
    }

    private void saveShape(CompoundTag tag) {
        tag.putDouble("BaseX", base.x);
        tag.putDouble("BaseY", base.y);
        tag.putDouble("BaseZ", base.z);
        tag.putDouble("Length", length);
        tag.putDouble("BaseRadius", baseRadius);
        tag.putDouble("TipRadius", tipRadius);
        tag.putDouble("RingAlong", ringAlong);
        tag.putInt("Ribs", ribs);
        if (glass != null) {
            tag.putLong("Glass", glass.asLong());
        }
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        Cone cone = new Cone(new Vec3(tag.getDouble("BaseX"), tag.getDouble("BaseY"), tag.getDouble("BaseZ")), tag.getDouble("Length"),
            tag.getDouble("BaseRadius"), tag.getDouble("TipRadius"), tag.getDouble("RingAlong"), tag.getInt("Ribs")).turned(turn);
        base = cone.base();
        length = cone.length();
        baseRadius = cone.baseRadius();
        tipRadius = cone.tipRadius();
        ringAlong = cone.ringAlong();
        ribs = cone.ribs();
        glass = tag.contains("Glass") ? Turning.turn(BlockPos.of(tag.getLong("Glass")), turn) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveShape(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
