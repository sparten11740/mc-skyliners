package dev.aerolarge.block;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.compat.SableFlight;
import java.util.Comparator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The laser path clearer: a turret in the underside of the nose that, armed from the flight deck (see
 * {@link LaserSwitchBlock}), drops out of its hatch and fires rapid short laser bolts at whatever lies in the assembled
 * airliner's flight path ahead (where it is going, or, standing still, where its nose points): hostile mobs first, each
 * bolt hurting one, and otherwise the nearest block in the way, each bolt vaporising a bite round it. Each
 * bolt is a beam drawn on every client near (see the client's {@code LaserBolts}), with sparks where it strikes and a
 * hiss.
 *
 * <p>Low down with the engines idle it holds its fire at blocks, so it never digs into the runway it stands on: below
 * {@link #HOLD_BELOW} blocks over the ground it shoots only hostile mobs, until it is above {@link #CLEAR_ABOVE} again
 * or the engines run (its switch shows which, see {@link LaserSwitchBlockEntity}). With the engines pushing the
 * airliner along, or at whatever stands in its way, it clears the way however low it is. It never vaporises a block
 * standing no higher than the bottom of the airliner's wheels, so the runway under it is safe either way.
 *
 * <p>It never fires at players, villagers, pets or any mob that is not hostile, nor at anything aboard an airliner. It
 * never vaporises a block with a block entity (a chest, a sign), one that cannot be broken, a liquid, or another
 * airliner's blocks; it leaves blocks alone wherever {@code mobGriefing} is off, and asks first, as a player breaking
 * it would, so a claim protecting the land keeps it. What it looks at is bounded: a few rays a tick (see
 * {@link LaserAim}) and a look for mobs every {@link #MOB_LOOK_TICKS}.
 */
public class LaserTurretBlockEntity extends BlockEntity {
    /** How long the turret takes to drop out of its hatch or fold back in, in ticks. */
    public static final int DEPLOY_TICKS = 20;
    /** Slower than this, in blocks a second, the airliner's way ahead is where its nose points. */
    private static final double MIN_SPEED = 2;
    /** How often it looks for hostile mobs in the way, in ticks. */
    private static final int MOB_LOOK_TICKS = 10;
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
    /** A block is vaporised only standing at least this much above the bottom of the airliner's wheels, in blocks. */
    private static final double ABOVE_WHEELS = 0.5;
    /** How far below the turret's block the muzzle is once it is out, and how far forward of its middle, in blocks. */
    private static final double MUZZLE_DROP = 0.6;
    private static final double MUZZLE_FORWARD = 0.5;

    private boolean armed;
    /** Whether, low down with the engines idle, it holds its fire at blocks, shooting only hostile mobs. */
    private boolean holding = true;
    /** How far out of its hatch the turret is, in ticks of {@link #DEPLOY_TICKS}. */
    private int deployed;
    private int sinceBolt;
    private final LaserAim aim = new LaserAim();
    /** Whether the airliner has been measured round the turret since it was armed. */
    private boolean outlined;
    /** The hostile mob it fires at next, if any. */
    @Nullable
    private LivingEntity quarry;

    public LaserTurretBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LASER_TURRET_ENTITY.get(), pos, state);
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
        level.playSound(null, worldPosition, AerolargeRegistry.LASER_CHARGE.get(), SoundSource.BLOCKS, HISS_VOLUME, on ? 1.0F : 0.7F);
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
        if (level instanceof ServerLevel serverLevel && turret.armed && turret.deployed == DEPLOY_TICKS && AerolargeConfig.LASER_PATH_CLEARER.get()) {
            turret.fly(serverLevel);
        }
    }

    /** Out and armed: looks along the way ahead, and fires on its beat. */
    private void fly(ServerLevel level) {
        SableFlight.State state = SableFlight.read(this, nose());
        if (state == null || !outline(level)) {
            return;
        }
        Vec3 muzzle = muzzle(level);
        watchHold(level, muzzle);
        Vec3 path = state.velocity().length() > MIN_SPEED ? state.velocity().normalize() : state.nose();
        double range = AerolargeConfig.LASER_RANGE.get();
        aim.sweep(level, muzzle, path, state.right(), state.up(), range);
        if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), MOB_LOOK_TICKS) == 0) {
            quarry = quarry(level, muzzle, path, state, range);
        }
        if (++sinceBolt >= 20 / AerolargeConfig.LASER_BOLTS_PER_SECOND.get()) {
            sinceBolt = 0;
            fire(level, muzzle, path, range, state);
        }
    }

    /**
     * Holds the fire at blocks below {@link #HOLD_BELOW} over the ground and lets it go again above
     * {@link #CLEAR_ABOVE}, over ground not loaded too, but never while the engines run.
     */
    private void watchHold(ServerLevel level, Vec3 muzzle) {
        BlockPos under = BlockPos.containing(muzzle);
        double ground = level.hasChunkAt(under) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, under.getX(), under.getZ()) : muzzle.y;
        boolean low = muzzle.y - ground < (holding ? CLEAR_ABOVE : HOLD_BELOW);
        boolean hold = low && !enginesRunning(level);
        if (hold != holding) {
            holding = hold;
            changed();
        }
    }

    /** Whether the airliner's engines run, as its autopilot finds them. */
    private boolean enginesRunning(ServerLevel level) {
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.nearest(level, worldPosition);
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
            outlined = true;
        }
        return true;
    }

    /** One bolt: at the hostile mob in the way, if there is one, else at the nearest block in the way, if any. */
    private void fire(ServerLevel level, Vec3 muzzle, Vec3 path, double range, SableFlight.State state) {
        if (quarry != null && quarry.isAlive() && inWay(quarry, muzzle, path, state, range)) {
            Vec3 struck = quarry.getBoundingBox().getCenter();
            quarry.hurt(laser(level), DAMAGE);
            bolt(level, muzzle, struck);
            return;
        }
        boolean mayCut = !holding && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        BlockPos obstacle = mayCut ? aim.next(level) : null;
        double floor = aim.wheels(muzzle, state.up()) + ABOVE_WHEELS;
        if (obstacle != null && vaporise(level, obstacle, floor)) {
            aim.spread(level, obstacle, state.right(), state.up(), muzzle, path, range);
            bolt(level, muzzle, Vec3.atCenterOf(obstacle));
        }
    }

    /** The beam drawn by every client near, sparks where it strikes, and its hiss. */
    private void bolt(ServerLevel level, Vec3 muzzle, Vec3 struck) {
        PacketDistributor.sendToPlayersNear(level, null, muzzle.x, muzzle.y, muzzle.z, BOLTS_SEEN, new LaserBolt(muzzle, struck));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, struck.x, struck.y, struck.z, 12, 0.3, 0.3, 0.3, 0.15);
        level.playSound(null, worldPosition, AerolargeRegistry.LASER_HISS.get(), SoundSource.BLOCKS, HISS_VOLUME, 0.9F + level.getRandom().nextFloat() * 0.2F);
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
    private LivingEntity quarry(ServerLevel level, Vec3 muzzle, Vec3 path, SableFlight.State state, double range) {
        AABB around = new AABB(muzzle, muzzle.add(path.scale(range))).inflate(aim.reach());
        return level.getEntitiesOfClass(LivingEntity.class, around, entity -> hostile(entity) && inWay(entity, muzzle, path, state, range))
            .stream()
            .min(Comparator.comparingDouble(entity -> entity.position().subtract(muzzle).dot(path)))
            .orElse(null);
    }

    /**
     * Whether a mob is in the airliner's way: its feet or the top of its head, so one standing on the runway, its feet
     * below the wheels' hubs, is in the way all the same.
     */
    private boolean inWay(LivingEntity entity, Vec3 muzzle, Vec3 path, SableFlight.State state, double range) {
        Vec3 feet = entity.position();
        Vec3 head = feet.add(0, entity.getBbHeight(), 0);
        return aim.inWay(muzzle, path, state.right(), state.up(), range, feet) || aim.inWay(muzzle, path, state.right(), state.up(), range, head);
    }

    /** Whether the laser fires at an entity: a hostile mob, alive, and not aboard this airliner. */
    private boolean hostile(LivingEntity entity) {
        return entity instanceof Enemy && entity.isAlive() && !Aboard.isAboard(entity, this);
    }

    private static DamageSource laser(ServerLevel level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(AerolargeRegistry.LASER_DAMAGE));
    }

    /** Where the bolts leave from, in the world: the turret's muzzle, out under the nose. */
    private Vec3 muzzle(Level level) {
        return SableFlight.toWorld(level, muzzle(worldPosition, nose()));
    }

    /**
     * Where a turret's bolts leave from among its airliner's blocks, the turret at a position there with its nose
     * pointing the given way: its muzzle, out under the nose. A client tells a turret's bolts from others' by it, and
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
