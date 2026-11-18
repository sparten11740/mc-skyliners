package dev.skyliners.block;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The laser path clearer: turrets in the underside of the nose, along the belly and under the wings that, armed from
 * the flight deck (see {@link LaserSwitchBlock}), drop out of their hatches and fire rapid short laser bolts at
 * whatever lies in the assembled airliner's flight path ahead of each (where it is going, or, standing still, where its
 * nose points): hostile mobs first, each bolt hurting one, and otherwise the nearest block in the way, each bolt
 * vaporising a bite round it. On the ground each looks all round it too, so what stands about her, under a wing or by
 * her tail, is cleared as well as what is in front of her. Each bolt is a beam drawn on every client near (see the
 * client's {@code LaserBolts}), with sparks where it strikes and a hiss.
 *
 * <p>Low down with the engines idle it holds its fire at blocks, so it never digs into the runway it stands on: below
 * {@link #HOLD_BELOW} blocks over the ground it shoots only hostile mobs, until it is above {@link #CLEAR_ABOVE} again
 * or the engines run (its switch shows which, see {@link LaserSwitchBlockEntity}). With the engines pushing the
 * airliner along, or at whatever stands in its way, it clears the way however low it is. It never vaporises a block
 * standing no higher than the ground under the airliner's lowest wheel, her nose up as she lifts off or not, so the
 * runway under her is safe either way.
 *
 * <p>It never fires at players, villagers, pets or any mob that is not hostile, nor at anything aboard an airliner. It
 * never vaporises a block with a block entity (a chest, a sign), one that cannot be broken, a liquid, or another
 * airliner's blocks; it leaves blocks alone wherever {@code mobGriefing} is off, and asks first, as a player breaking
 * it would, so a claim protecting the land keeps it. What it looks at is bounded: a few rays a tick (see
 * {@link LaserLook}), none while it holds its fire at blocks, and a look for mobs every {@link #MOB_LOOK_TICKS}.
 */
public class LaserTurretBlockEntity extends BlockEntity {
    /** How long the turret takes to drop out of its hatch or fold back in, in ticks. */
    public static final int DEPLOY_TICKS = 20;
    /** Slower than this, in blocks a second, the airliner's way ahead is where its nose points. */
    private static final double MIN_SPEED = 2;
    /** How often it looks for hostile mobs in the way, in ticks. */
    private static final int MOB_LOOK_TICKS = 10;
    /** How often it looks how high it is over the ground and whether her engines run, in ticks. */
    private static final int HOLD_LOOK_TICKS = 10;
    /** How much a bolt hurts a hostile mob, in half hearts: two or three bolts for a zombie. */
    private static final float DAMAGE = 9;
    /**
     * How far round the block it strikes a bolt vaporises, in blocks every way: a bite of at most 27 blocks, the struck
     * one in the middle of a cube three across. A whole cube, so bites side by side leave nothing standing between
     * them.
     */
    private static final int BITE = 1;
    /** How far from the muzzle players are sent each bolt to draw, in blocks. */
    private static final double BOLTS_SEEN = 160;
    /** How loud the hiss of a bolt is: past 1 it carries further, 16 blocks for each unit, so 2 reaches the cabin. */
    private static final float HISS_VOLUME = 2.0F;
    /**
     * Below this height of the muzzle over the ground, in blocks, it holds its fire at blocks while the engines idle,
     * and it fires at them again only once above the higher height: apart, so it does not flick on and off at the edge.
     */
    private static final double HOLD_BELOW = 15;
    private static final double CLEAR_ABOVE = 25;
    /** A block is vaporised only standing at least this much above the ground under her wheels, in blocks. */
    private static final double ABOVE_WHEELS = 0.5;
    /**
     * How far out from the middle of the airliner her wheels are looked for along the bottom of her, in blocks to
     * either side: past her wing gear, short of her engines.
     */
    private static final int WHEELS_ACROSS = 16;
    /**
     * How far below her lowest row of blocks, the wheels' mounts, the bottom of her tires is at least, in blocks:
     * further as her suspension lets them down.
     */
    private static final double TIRES_BELOW = 1;
    /**
     * How far above the bottom of her lowest tire as reckoned the ground under it is looked for, and how far below, in
     * blocks: round where her suspension lets it down to, and no further, so a roof over her is never taken for it.
     */
    private static final int GROUND_ABOVE = 2;
    private static final int GROUND_BELOW = 8;
    /** How far below the turret's block the muzzle is once it is out, and how far forward of its middle, in blocks. */
    private static final double MUZZLE_DROP = 0.6;
    private static final double MUZZLE_FORWARD = 0.5;

    private boolean armed;
    /** Whether, low down with the engines idle, it holds its fire at blocks, shooting only hostile mobs. */
    private boolean holding = true;
    /** How far out of its hatch the turret is, in ticks of {@link #DEPLOY_TICKS}. */
    private int deployed;
    /** Where it looks along the way ahead. */
    private final LaserAim aim = new LaserAim();
    /**
     * Where it looks all round on the ground: what it saw forgotten once she is up and away, as it is soon far off,
     * never to be fired at as she next comes down.
     */
    private final LaserRing ring = new LaserRing();
    /** Whether it is low over the ground, on it or nearly, as it last looked: there it looks all round too. */
    private boolean low = true;
    /** Whether the airliner has been measured round the turret since it was armed. */
    private boolean outlined;
    /** Where the bottom of each of her tires is from the muzzle, as she was measured. */
    private List<Tire> tires = List.of();
    /** The hostile mob it fires at next, if any. */
    @Nullable
    private LivingEntity quarry;

    public LaserTurretBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LASER_TURRET_ENTITY.get(), pos, state);
    }

    /** Arms or disarms it, from the flight deck: armed, it drops out of its hatch with a rising whine, and starts firing. */
    public void arm(ServerLevel level, boolean on) {
        if (armed == on) {
            return;
        }
        armed = on;
        outlined = false;
        quarry = null;
        aim.forget();
        ring.forget();
        level.playSound(null, worldPosition, SkylinersRegistry.LASER_CHARGE.get(), SoundSource.BLOCKS, HISS_VOLUME, on ? 1.0F : 0.7F);
        changed();
    }

    public boolean armed() {
        return armed;
    }

    /** Whether, armed but low down with the engines idle, it holds its fire at blocks, shooting only hostile mobs. */
    public boolean holding() {
        return holding;
    }

    /** How far out of its hatch the turret is, from 0 in to 1 out, between ticks as drawn. */
    public float deployed(float partialTick) {
        boolean moving = armed ? deployed < DEPLOY_TICKS : deployed > 0;
        float between = moving ? (armed ? partialTick : -partialTick) : 0;
        return Math.clamp((deployed + between) / DEPLOY_TICKS, 0, 1);
    }

    /** One tick nearer out while armed, nearer in while not. */
    private void deploy() {
        deployed = Math.clamp(deployed + (armed ? 1 : -1), 0, DEPLOY_TICKS);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LaserTurretBlockEntity turret) {
        turret.deploy();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserTurretBlockEntity turret) {
        turret.deploy();
        if (level instanceof ServerLevel serverLevel && turret.armed && turret.deployed == DEPLOY_TICKS && SkylinersConfig.LASER_PATH_CLEARER.get()) {
            turret.fly(serverLevel);
        }
    }

    /**
     * Out and armed: looks along the way ahead, and fires on its beat. Low over the ground it looks all round too,
     * every other tick, so on the ground it clears whatever stands about her as well as what is ahead: a house under
     * her wing, a tree by her tail. While it holds its fire at blocks it looks only for mobs. Its looks and its beat
     * fall on ticks of its own, so an airliner's turrets take turns rather than all firing on one tick.
     */
    private void fly(ServerLevel level) {
        SableFlight.State state = SableFlight.read(this, nose());
        boolean justArmed = !outlined;
        if (state == null || !outline(level)) {
            return;
        }
        Vec3 muzzle = muzzle(level);
        if (justArmed || onBeat(level, HOLD_LOOK_TICKS)) {
            watchHold(level, muzzle);
        }
        Vec3 path = state.velocity().length() > MIN_SPEED ? state.velocity().normalize() : state.nose();
        LaserLook.Lookout look = new LaserLook.Lookout(muzzle, path, state.right(), state.up(), SkylinersConfig.LASER_RANGE.get());
        if (clearing(level)) {
            (low && level.getGameTime() % 2 == 0 ? ring : aim).sweep(level, look);
        }
        if (onBeat(level, MOB_LOOK_TICKS)) {
            quarry = quarry(level, look);
        }
        if (onBeat(level, 20 / SkylinersConfig.LASER_BOLTS_PER_SECOND.get())) {
            fire(level, look);
        }
    }

    /** Whether this tick is one of the turret's own, once every so many: each turret's fall on ticks of their own. */
    private boolean onBeat(Level level, int every) {
        return Math.floorMod(level.getGameTime() + worldPosition.hashCode(), every) == 0;
    }

    /** Whether it may fire at blocks: not holding its fire, and only where mobs may change the land. */
    private boolean clearing(ServerLevel level) {
        return !holding && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /**
     * Notes whether it is low over the ground, where it looks all round too, and holds the fire at blocks below
     * {@link #HOLD_BELOW} over the ground and lets it go again above {@link #CLEAR_ABOVE}, over ground not loaded too,
     * but never while the engines run.
     */
    private void watchHold(ServerLevel level, Vec3 muzzle) {
        BlockPos under = BlockPos.containing(muzzle);
        double ground = level.hasChunkAt(under) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, under.getX(), under.getZ()) : muzzle.y;
        low = muzzle.y - ground < (holding ? CLEAR_ABOVE : HOLD_BELOW);
        if (!low) {
            ring.forget();
        }
        boolean hold = low && !enginesRunning();
        if (hold != holding) {
            holding = hold;
            aim.forget();
            ring.forget();
            changed();
        }
    }

    /**
     * Whether the airliner's engines run, as her autopilot finds them: the autopilot of the ship the turret is part of,
     * however far from the flight deck it is, out under a wing or back under the tail.
     */
    private boolean enginesRunning() {
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.shipOf(this);
        return autopilot != null && autopilot.enginesRunning();
    }

    /** Measures the airliner round the turret once after arming; whether it is measured. */
    private boolean outline(ServerLevel level) {
        if (!outlined) {
            BoundingBox blocks = SableFlight.blocks(level, Vec3.atCenterOf(worldPosition));
            if (blocks == null) {
                return false;
            }
            aim.outline(blocks, worldPosition, nose(), worldPosition.getY() - MUZZLE_DROP);
            ring.outline(blocks, worldPosition, nose(), worldPosition.getY() - MUZZLE_DROP);
            tires = tires(level, blocks);
            outlined = true;
        }
        return true;
    }

    /** The bottom of a tire from the muzzle, in the airliner's own frame: along her nose, to her right and up. */
    private record Tire(double ahead, double right, double up) {
    }

    /**
     * Where the bottom of each of her tires is from the muzzle: under each block along the bottom of her, near her
     * middle, which with the gear down are the wheels' mounts. None found, as with the gear up, the bottom of her
     * straight under the muzzle stands in for them.
     */
    private List<Tire> tires(ServerLevel level, BoundingBox blocks) {
        Direction nose = nose();
        Direction across = nose.getClockWise();
        double up = blocks.minY() - TIRES_BELOW - (worldPosition.getY() - MUZZLE_DROP);
        int middleX = (blocks.minX() + blocks.maxX()) / 2;
        int middleZ = (blocks.minZ() + blocks.maxZ()) / 2;
        int outX = across.getAxis() == Direction.Axis.X ? WHEELS_ACROSS : blocks.maxX() - middleX + 1;
        int outZ = across.getAxis() == Direction.Axis.Z ? WHEELS_ACROSS : blocks.maxZ() - middleZ + 1;
        List<Tire> found = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(middleX - outX, blocks.minY(), middleZ - outZ, middleX + outX, blocks.minY(), middleZ + outZ)) {
            if (!level.getBlockState(at).isAir()) {
                int x = at.getX() - worldPosition.getX();
                int z = at.getZ() - worldPosition.getZ();
                found.add(new Tire(x * nose.getStepX() + z * nose.getStepZ() - MUZZLE_FORWARD, x * across.getStepX() + z * across.getStepZ(), up));
            }
        }
        return found.isEmpty() ? List.of(new Tire(0, 0, up)) : List.copyOf(found);
    }

    /**
     * The height of the ground under the airliner's lowest wheel, however she is pitched or banked: her rear wheels as
     * she lifts her nose off the runway, where the plane of her wheels under a turret behind them would run down into
     * it. Her tires reach below their mounts by however far her suspension lets them, so the ground they stand on is
     * measured, looked for down from just over them, not reckoned from her blocks; where none is found near them, in
     * the air or over ground not loaded, the bottom of the tire as reckoned.
     */
    private double ground(ServerLevel level, LaserLook.Lookout look) {
        Vec3 nose = look.up().cross(look.right());
        Vec3 lowest = tires.stream()
            .map(tire -> look.muzzle().add(nose.scale(tire.ahead())).add(look.right().scale(tire.right())).add(look.up().scale(tire.up())))
            .min(Comparator.comparingDouble(Vec3::y))
            .orElse(look.muzzle());
        BlockPos under = BlockPos.containing(lowest);
        if (!level.hasChunkAt(under)) {
            return lowest.y;
        }
        for (BlockPos at = under.above(GROUND_ABOVE); at.getY() >= under.getY() - GROUND_BELOW; at = at.below()) {
            VoxelShape shape = level.getBlockState(at).getCollisionShape(level, at);
            if (!shape.isEmpty()) {
                return at.getY() + shape.max(Direction.Axis.Y);
            }
        }
        return lowest.y;
    }

    /**
     * One bolt: at the hostile mob in the way, if there is one, else at the nearest block in the way ahead, if any, and
     * on the ground at the nearest all round otherwise.
     */
    private void fire(ServerLevel level, LaserLook.Lookout look) {
        if (quarry != null && quarry.isAlive() && inWay(quarry, look)) {
            Vec3 struck = quarry.getBoundingBox().getCenter();
            quarry.hurt(laser(level), DAMAGE);
            bolt(level, look.muzzle(), struck);
            return;
        }
        if (!clearing(level)) {
            return;
        }
        double floor = ground(level, look) + ABOVE_WHEELS;
        if (!cut(level, aim, look, floor) && low) {
            cut(level, ring, look, floor);
        }
    }

    /**
     * Vaporises the next block one look has found that may go, standing above the floor (a height in the world), the
     * clearing spreading round it; whether one went. Those that may not are passed over in the same bolt, the ground
     * far off where a level ray meets it among them, so none of its bolts is spent on nothing.
     */
    private boolean cut(ServerLevel level, LaserLook way, LaserLook.Lookout look, double floor) {
        for (BlockPos obstacle = way.next(level); obstacle != null; obstacle = way.next(level)) {
            if (vaporise(level, obstacle, floor)) {
                way.spread(level, obstacle, look, floor);
                bolt(level, look.muzzle(), Vec3.atCenterOf(obstacle));
                return true;
            }
        }
        return false;
    }

    /** The beam drawn by every client near, sparks where it strikes, and its hiss. */
    private void bolt(ServerLevel level, Vec3 muzzle, Vec3 struck) {
        PacketDistributor.sendToPlayersNear(level, null, muzzle.x, muzzle.y, muzzle.z, BOLTS_SEEN, new LaserBolt(muzzle, struck, LaserBolt.RED));
        level.playSound(null, worldPosition, SkylinersRegistry.LASER_HISS.get(), SoundSource.BLOCKS, HISS_VOLUME, 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    /**
     * Vaporises the block struck and what may go of the bite round it, the struck one with its breaking dust and sound,
     * none of them standing no higher than the floor (a height in the world); whether the struck one went.
     */
    private static boolean vaporise(ServerLevel level, BlockPos struck, double floor) {
        if (!mayVaporise(level, struck, floor)) {
            return false;
        }
        level.destroyBlock(struck, false);
        for (BlockPos around : BlockPos.betweenClosed(struck.offset(-BITE, -BITE, -BITE), struck.offset(BITE, BITE, BITE))) {
            if (!around.equals(struck) && mayVaporise(level, around, floor)) {
                level.removeBlock(around, false);
            }
        }
        return true;
    }

    /**
     * Whether a bolt may vaporise a block: standing higher than the floor (a height in the world), solid, breakable, no
     * block entity, no liquid, not among an airliner's blocks, and not kept by whatever protects the land (asked as a
     * player breaking it would be).
     */
    private static boolean mayVaporise(ServerLevel level, BlockPos pos, double floor) {
        BlockState state = level.getBlockState(pos);
        boolean solid = !state.isAir() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, pos) >= 0;
        if (pos.getY() + 1 <= floor || !solid || state.hasBlockEntity()) {
            return false;
        }
        if (SableFlight.aboard(level, Vec3.atCenterOf(pos))) {
            return false;
        }
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, FakePlayerFactory.getMinecraft(level))).isCanceled();
    }

    /** The nearest hostile mob in the way, not aboard an airliner; null for none. */
    @Nullable
    private LivingEntity quarry(ServerLevel level, LaserLook.Lookout look) {
        AABB around = new AABB(look.muzzle(), look.muzzle().add(look.path().scale(look.range()))).inflate(aim.reach());
        return level.getEntitiesOfClass(LivingEntity.class, around, entity -> hostile(entity) && inWay(entity, look))
            .stream()
            .min(Comparator.comparingDouble(entity -> entity.position().subtract(look.muzzle()).dot(look.path())))
            .orElse(null);
    }

    /**
     * Whether a mob is in the airliner's way: its feet or the top of its head, so one standing on the runway, its feet
     * below the wheels' hubs, is in the way all the same.
     */
    private boolean inWay(LivingEntity entity, LaserLook.Lookout look) {
        Vec3 feet = entity.position();
        return aim.inWay(look, feet) || aim.inWay(look, feet.add(0, entity.getBbHeight(), 0));
    }

    /** Whether the laser fires at an entity: a hostile mob, alive, and not aboard this airliner. */
    private boolean hostile(LivingEntity entity) {
        return entity instanceof Enemy && entity.isAlive() && !Aboard.isAboard(entity, this);
    }

    private static DamageSource laser(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SkylinersRegistry.LASER_DAMAGE));
    }

    /** Where the bolts leave from, in the world: the turret's muzzle, out under its hatch. */
    private Vec3 muzzle(Level level) {
        return SableFlight.toWorld(level, muzzle(worldPosition, nose()));
    }

    /**
     * Where a turret's bolts leave from among its airliner's blocks, the turret at a position there with its nose
     * pointing the given way: its muzzle, out under its hatch. A client tells a turret's bolts from others' by it, and
     * draws them from the tip of its barrel as it is turned instead.
     */
    public static Vec3 muzzle(BlockPos turret, Direction nose) {
        return Vec3.atCenterOf(turret).add(nose.getStepX() * MUZZLE_FORWARD, -MUZZLE_DROP - 0.5, nose.getStepZ() * MUZZLE_FORWARD);
    }

    private Direction nose() {
        return getBlockState().getValue(LaserTurretBlock.FACING);
    }

    /** Saves, and tells the clients whether it is armed. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Armed", armed);
        tag.putBoolean("Holding", holding);
        tag.putInt("Deployed", deployed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        armed = tag.getBoolean("Armed");
        // Saved before it held its fire low down: held until it has looked at its height.
        holding = !tag.contains("Holding") || tag.getBoolean("Holding");
        deployed = tag.getInt("Deployed");
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
