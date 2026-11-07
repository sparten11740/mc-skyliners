package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.entity.Tardigrade;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The keeper of the rocket's tardigrade (see {@link Tardigrade}), in the feeding hatch of its tank: it makes the
 * creature once the rocket stands, makes it again if it goes missing, and every tick hands it the line it paces along
 * the tank, where on it it can turn round and where the hatch is, bringing it back to the tank if it is ever found far
 * from it, as when her blocks are assembled into a flying body and moved away from where it stood. Every second it
 * looks how high she is in the world: over the top of the world, in space, the creature curls up into its tun and its
 * vitals screen over the hatch goes still; back under it, it uncurls. And every second it shows on that screen whether
 * the creature sleeps: lying under a moon, its pulse slow, while it does.
 *
 * <p>Its line is set as the rocket is built (see {@link #configure}), as offsets from the hatch's middle turned as she
 * was placed, and turned again with the hatch's facing if it is ever loaded facing another way (see {@link Turning}).
 * The freighter's lounge has a hatch of its own, on a smaller tank, which keeps a small cousin of the creature.
 */
public class HabitatHatchBlockEntity extends BlockEntity {
    /**
     * How long a creature it has made may be missing before another is made, in ticks: its own chunk may load late. The
     * first is made at once.
     */
    private static final int MISSING = 100;
    /** How often the keeper looks how high she is and whether the creature sleeps, in ticks. */
    private static final int LOOK_EVERY = 20;
    /**
     * How high in the world the creature curls up into its tun, over the top of the world where only sky is, and how
     * low it has come back down to uncurl, in blocks.
     */
    private static final double SPACE_FROM = 320;
    private static final double SPACE_LEFT = 300;
    /** How far from its line the creature may be found before it is brought back to the tank, in blocks. */
    private static final double STRAYED = 4;
    /** How far in from the hatch's middle its inside is, in blocks: just behind the glass. */
    private static final double INSIDE = 0.45;

    @Nullable
    private UUID creature;
    /**
     * The creature's line's ends and the point where it turns round, as offsets from the hatch's middle; null before it
     * is set.
     */
    @Nullable
    private Vec3 from;
    @Nullable
    private Vec3 to;
    @Nullable
    private Vec3 turn;
    private boolean curled;
    private int missing;
    /** How many times the rocket's size the creature it makes is: 1 in her lab, a quarter in the freighter's lounge. */
    private double size = 1;

    public HabitatHatchBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.HABITAT_HATCH_ENTITY.get(), pos, state);
    }

    /**
     * Set as the ship is built: the creature's line's ends and the point where it turns round (null where it cannot),
     * as offsets from the hatch's middle in her layout, turned as she was placed, and how many times the rocket's
     * creature's size the one it keeps is (see {@link Tardigrade#setSize}).
     */
    public void configure(Vec3 lineFrom, Vec3 lineTo, @Nullable Vec3 turnPoint, Rotation rotation, double creatureSize) {
        from = turned(lineFrom, rotation);
        to = turned(lineTo, rotation);
        turn = turnPoint == null ? null : turned(turnPoint, rotation);
        size = creatureSize;
        setChanged();
    }

    /** An offset turned by quarter turns about the block's middle, as {@link BlockPos#rotate} turns one. */
    private static Vec3 turned(Vec3 offset, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> new Vec3(-offset.z, offset.y, offset.x);
            case CLOCKWISE_180 -> new Vec3(-offset.x, offset.y, -offset.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(offset.z, offset.y, -offset.x);
            default -> offset;
        };
    }

    static void tick(ServerLevel level, BlockPos pos, HabitatHatchBlockEntity keeper) {
        if (keeper.from == null || keeper.to == null) {
            return;
        }
        Tardigrade found = keeper.creature(level);
        if (found == null) {
            if (keeper.creature == null || ++keeper.missing >= MISSING) {
                keeper.make(level);
            }
            return;
        }
        keeper.missing = 0;
        Vec3 middle = Vec3.atCenterOf(pos);
        Vec3 lineFrom = middle.add(keeper.from);
        Vec3 lineTo = middle.add(keeper.to);
        found.keep(level.getGameTime(), lineFrom, lineTo, keeper.turn == null ? null : middle.add(keeper.turn), keeper.inside());
        if (strayed(found.position(), lineFrom, lineTo)) {
            Vec3 home = keeper.home(middle);
            found.teleportTo(home.x, home.y, home.z);
        }
        if (level.getGameTime() % LOOK_EVERY == 0) {
            keeper.lookHowHigh(level, found);
            keeper.showSleep(level, found);
            keeper.showAway(level, found);
        }
    }

    /** The vitals screen over the hatch shown asleep while the creature sleeps, and awake while it is awake. */
    private void showSleep(ServerLevel level, Tardigrade found) {
        BlockState shown = vitals(level);
        if (shown != null && shown.getValue(LabInstrumentBlock.ASLEEP) != found.isAsleep()) {
            level.setBlock(worldPosition.above(), shown.setValue(LabInstrumentBlock.ASLEEP, found.isAsleep()), Block.UPDATE_ALL);
        }
    }

    /** The vitals screen over the hatch shown with stars streaming past while the creature is away on a jump. */
    private void showAway(ServerLevel level, Tardigrade found) {
        BlockState shown = vitals(level);
        boolean away = found.jump() == Tardigrade.Jump.AWAY;
        if (shown != null && shown.getValue(LabInstrumentBlock.AWAY) != away) {
            level.setBlock(worldPosition.above(), shown.setValue(LabInstrumentBlock.AWAY, away), Block.UPDATE_ALL);
        }
    }

    /** The vitals screen over the hatch as it stands; null if it is not there. */
    @Nullable
    private BlockState vitals(ServerLevel level) {
        BlockState shown = level.getBlockState(worldPosition.above());
        boolean vitals = shown.getBlock() instanceof LabInstrumentBlock && shown.getValue(LabInstrumentBlock.KIND) == LabInstrumentBlock.Kind.VITALS;
        return vitals ? shown : null;
    }

    /** Whether the creature is further from its line than it can wander: out of the tank. */
    private static boolean strayed(Vec3 at, Vec3 lineFrom, Vec3 lineTo) {
        Vec3 along = lineTo.subtract(lineFrom);
        double length = along.lengthSqr();
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, at.subtract(lineFrom).dot(along) / length));
        return at.distanceTo(lineFrom.add(along.scale(t))) > STRAYED;
    }

    /** Where the creature is made or brought back to: where it turns round, else the middle of its line. */
    private Vec3 home(Vec3 middle) {
        return middle.add(turn != null ? turn : from.add(to).scale(0.5));
    }

    /** The hatch's inside, just behind its glass. */
    private Vec3 inside() {
        Direction out = getBlockState().getValue(HabitatHatchBlock.FACING);
        return Vec3.atCenterOf(worldPosition).subtract(Vec3.atLowerCornerOf(out.getNormal()).scale(INSIDE));
    }

    /** Makes the creature at home in the tank, facing the hatch's end of its line. */
    private void make(ServerLevel level) {
        Vec3 middle = Vec3.atCenterOf(worldPosition);
        Vec3 home = home(middle);
        Vec3 along = to.subtract(from);
        Tardigrade made = new Tardigrade(SkylinersRegistry.TARDIGRADE.get(), level);
        made.setSize(size);
        made.moveTo(home.x, home.y, home.z, (float) Math.toDegrees(Math.atan2(along.z, along.x)) - 90, 0);
        made.keep(level.getGameTime(), middle.add(from), middle.add(to), turn == null ? null : middle.add(turn), inside());
        made.setCurled(curled);
        level.addFreshEntity(made);
        creature = made.getUUID();
        missing = 0;
        setChanged();
    }

    @Nullable
    private Tardigrade creature(ServerLevel level) {
        Entity found = creature == null ? null : level.getEntity(creature);
        return found instanceof Tardigrade tardigrade && tardigrade.isAlive() ? tardigrade : null;
    }

    /**
     * How high she is in the world, the hatch's own height there: over the top of the world the creature curls up and
     * its vitals screen goes still; back down well under it, it uncurls and its screen beats again.
     */
    private void lookHowHigh(ServerLevel level, Tardigrade found) {
        double height = SableFlight.toWorld(level, Vec3.atCenterOf(worldPosition)).y;
        boolean nowCurled = curled ? height > SPACE_LEFT : height > SPACE_FROM;
        if (nowCurled == curled && found.isCurled() == curled) {
            return;
        }
        curled = nowCurled;
        found.setCurled(curled);
        BlockState shown = vitals(level);
        if (shown != null) {
            level.setBlock(worldPosition.above(), shown.setValue(LabInstrumentBlock.DORMANT, curled), Block.UPDATE_ALL);
        }
        setChanged();
    }

    /**
     * Calls the creature to the hatch, to be fed or touched; whether it comes (not curled up in its tun nor away
     * through the spore drive, and there to come).
     */
    public boolean call(ServerLevel level, boolean feed) {
        Tardigrade found = creature(level);
        return found != null && found.call(feed);
    }

    /** Calls the creature to the hatch to be fed this; whether it comes, as for {@link #call}. */
    public boolean feed(ServerLevel level, ItemStack food) {
        Tardigrade found = creature(level);
        return found != null && found.feed(food);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (creature != null) {
            tag.putUUID("Creature", creature);
        }
        save(tag, "From", from);
        save(tag, "To", to);
        save(tag, "Turn", turn);
        tag.putBoolean("Curled", curled);
        tag.putDouble("Size", size);
        Turning.save(tag, getBlockState());
    }

    private static void save(CompoundTag tag, String key, @Nullable Vec3 offset) {
        if (offset != null) {
            CompoundTag saved = new CompoundTag();
            saved.putDouble("X", offset.x);
            saved.putDouble("Y", offset.y);
            saved.putDouble("Z", offset.z);
            tag.put(key, saved);
        }
    }

    /** Loaded again, its offsets are turned if its hatch has turned since they were saved. */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        creature = tag.hasUUID("Creature") ? tag.getUUID("Creature") : null;
        Rotation since = Turning.since(tag, getBlockState());
        from = load(tag, "From", since);
        to = load(tag, "To", since);
        turn = load(tag, "Turn", since);
        curled = tag.getBoolean("Curled");
        size = tag.contains("Size") ? tag.getDouble("Size") : 1;
    }

    @Nullable
    private static Vec3 load(CompoundTag tag, String key, Rotation since) {
        if (!tag.contains(key)) {
            return null;
        }
        CompoundTag saved = tag.getCompound(key);
        return turned(new Vec3(saved.getDouble("X"), saved.getDouble("Y"), saved.getDouble("Z")), since);
    }
}
