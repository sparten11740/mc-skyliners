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
 * What the freighter's canopy lining knows (see {@link CanopyLiningBlock}): the nose her canopy is drawn on, told at
 * the inside of her blocks, a little in from her hull's surface, as where its base's middle is from the middle of this
 * block, in the world's directions, how far it runs toward her bow to its flat front and how wide it is at each end;
 * whether it narrows to the front in a straight cone, as the first smooth canopies were built, or rounds in over a
 * shoulder, a dome; how thick her skin is out to her surface, where her glass is drawn; where along it a ring of her
 * frame goes, as a share of its length, if any, and how many ribs run along it, evenly round from straight up; and one
 * block of her canopy's glass, whose darkness the lining's glass follows as her window dimmer darkens it. Set once as
 * she is built (see {@link #shape}), turned with the block since, saved and sent to the clients, which draw it.
 */
public class CanopyLiningBlockEntity extends BlockEntity {
    /** Before it is told its shape: none drawn. */
    private static final double NONE = 0;
    /** A ring's share of the length for a canopy with none round it. */
    public static final double NO_RING = -1;
    /**
     * How thick her skin is out to her surface on a lining saved before it said so, in blocks: the first linings were
     * all told their nose this far in from her surface.
     */
    private static final double FIRST_SKIN = 0.6;

    private Vec3 base = Vec3.ZERO;
    private double length = NONE;
    private double baseRadius;
    private double tipRadius;
    private double ringAlong = NO_RING;
    private int ribs;
    private double skin = FIRST_SKIN;
    private boolean rounded;
    @Nullable
    private BlockPos glass;

    public CanopyLiningBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.CANOPY_LINING_ENTITY.get(), pos, state);
    }

    /**
     * The nose a canopy is drawn on, as she is built, told at the inside of her blocks with the skin out to her surface:
     * all in blocks but the ring's share (below zero for none), the ribs' count and whether its shoulder is rounded, a
     * dome, rather than a straight cone.
     */
    public record Cone(Vec3 base, double length, double baseRadius, double tipRadius, double ringAlong, int ribs, double skin,
                       boolean rounded) {
        /** The nose turned with its ship by a quarter turn about the middle of the block it is kept by. */
        Cone turned(Rotation turn) {
            Vec3 turned = switch (turn) {
                case NONE -> base;
                case CLOCKWISE_90 -> new Vec3(-base.z, base.y, base.x);
                case CLOCKWISE_180 -> new Vec3(-base.x, base.y, -base.z);
                case COUNTERCLOCKWISE_90 -> new Vec3(base.z, base.y, -base.x);
            };
            return new Cone(turned, length, baseRadius, tipRadius, ringAlong, ribs, skin, rounded);
        }

        /** Whether a ring of her frame goes round it. */
        public boolean ringed() {
            return ringAlong > 0 && ringAlong < 1;
        }
    }

    /** Told its nose and the glass to follow, as offsets from this block, turned as she was placed. */
    public void shape(Cone cone, @Nullable BlockPos glass, Rotation rotation) {
        keep(cone.turned(rotation));
        this.glass = glass == null ? null : glass.rotate(rotation);
        setChanged();
    }

    private void keep(Cone cone) {
        base = cone.base();
        length = cone.length();
        baseRadius = cone.baseRadius();
        tipRadius = cone.tipRadius();
        ringAlong = cone.ringAlong();
        ribs = cone.ribs();
        skin = cone.skin();
        rounded = cone.rounded();
    }

    /** The nose it draws; null before it is told one. */
    @Nullable
    public Cone cone() {
        return length <= NONE ? null : new Cone(base, length, baseRadius, tipRadius, ringAlong, ribs, skin, rounded);
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
        tag.putDouble("Skin", skin);
        tag.putBoolean("Rounded", rounded);
        if (glass != null) {
            tag.putLong("Glass", glass.asLong());
        }
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        double savedSkin = tag.contains("Skin") ? tag.getDouble("Skin") : FIRST_SKIN;
        keep(new Cone(new Vec3(tag.getDouble("BaseX"), tag.getDouble("BaseY"), tag.getDouble("BaseZ")), tag.getDouble("Length"),
            tag.getDouble("BaseRadius"), tag.getDouble("TipRadius"), tag.getDouble("RingAlong"), tag.getInt("Ribs"), savedSkin,
            tag.getBoolean("Rounded")).turned(turn));
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
