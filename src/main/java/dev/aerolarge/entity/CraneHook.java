package dev.aerolarge.entity;

import dev.aerolarge.Aboard;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CraneWinchBlock;
import dev.aerolarge.block.CraneWinchBlockEntity;
import dev.aerolarge.compat.SableFlight;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Vector3f;

/**
 * The sky crane's hook (see {@link CraneWinchBlockEntity}): an iron hook under a weighted ball, hung on the winch's
 * rope under a zeppelin's control car, swinging as a pendulum in the world under her as she flies, trailing back the
 * faster she goes. Lowered onto something it may lift, it catches it with a clack: an animal in a canvas sling, a
 * boat or a minecart on chains, or a chest, barrel or cargo crate in a net, with all it holds. Lowered again until its
 * load rests on the ground while she hardly moves, it lets go with a clank, and catches nothing more until it has been
 * hoisted a few blocks clear. Its box is its load's, so the load collides with the land and her hull as it swings.
 *
 * <p>It never catches a player, a villager or a monster, a tamed animal whose owner is not aboard her, nor anything a
 * player rides; a load a player climbs onto is only ever lowered. A chest is taken only where whoever works the winch
 * may break it. Her winch moves it every tick (see {@link #hold}): left without its winch, as when she is taken away,
 * it lets its load down gently and goes.
 *
 * <p>Its rope is drawn from where her winch is as each client draws her, so its swing is sent as its place against the
 * winch rather than in the world, where it would lag behind her (see the client's {@code CraneHookRenderer}).
 */
public class CraneHook extends Entity {
    /** What the hook may lift: animals and vehicles, by type. */
    public static final TagKey<EntityType<?>> LOADS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "crane_loads"));
    /** The blocks it may lift in a net, with what they hold: chests, barrels, cargo crates. */
    public static final TagKey<Block> CARGO = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "crane_cargo"));

    private static final EntityDataAccessor<BlockPos> WINCH = SynchedEntityData.defineId(CraneHook.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> FROM_WINCH = SynchedEntityData.defineId(CraneHook.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(CraneHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STRAIN = SynchedEntityData.defineId(CraneHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<BlockState>> CARGO_BLOCK = SynchedEntityData.defineId(CraneHook.class, EntityDataSerializers.OPTIONAL_BLOCK_STATE);
    private static final EntityDataAccessor<Float> LOAD_WIDTH = SynchedEntityData.defineId(CraneHook.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LOAD_HEIGHT = SynchedEntityData.defineId(CraneHook.class, EntityDataSerializers.FLOAT);

    /** The empty hook's size, in blocks. */
    private static final float HOOK_SIZE = 0.5F;
    /** The shortest and longest the rope runs out, from under the car's floor to the hook, in blocks. */
    public static final double SHORTEST = 1.5;
    public static final double LONGEST = 48;
    /** How fast the winch hoists and lowers, in blocks a tick: three blocks a second. */
    private static final double WINDING = 0.15;
    /** How much rope it pays out past a hook resting on something, in blocks: slack, rather than piling up. */
    private static final double SLACK = 0.5;
    /**
     * The swing: gravity on the hook, in blocks a tick each tick, and what the air leaves of its speed through the
     * world each tick, which trails it back some 20 degrees at 14 blocks a second.
     */
    private static final double GRAVITY = 0.08;
    private static final double AIR = 0.96;
    /** How much of a new strain reading the dial takes each tick, smoothing its needle. */
    private static final float STEADYING = 0.25F;
    /**
     * What the crane is rated for: the size of the biggest load it lifts at the dial's full mark hanging still, in
     * cubic blocks (a horse is some three); and what a chest, barrel or crate counts for, empty and full.
     */
    private static final double RATED = 4;
    private static final double CARGO_EMPTY = 1;
    private static final double CARGO_FULL = 2.5;
    /** A chest's slots, by which how full one is counts. */
    private static final int CHEST_SLOTS = 27;
    /**
     * How far round and below its box it catches an animal or vehicle, and how many blocks round and below the block it
     * comes down on it catches a chest, in blocks: hovering, she drifts a block or two, and the hook with her.
     */
    private static final double REACH = 1;
    private static final int CARGO_REACH = 1;
    /** How far it must be hoisted, in blocks, after letting go before it catches again. */
    private static final double CLEARING = 3;
    /**
     * How fast the hook may move, in blocks a tick, for its load to be set down: about walking pace; and the strain
     * under which the rope counts as slack, its load resting.
     */
    private static final double SETTING_DOWN = 0.1;
    private static final float SLACK_STRAIN = 0.05F;
    /** How long, in ticks, the hook waits for its winch before it lets its load down and goes. */
    private static final int WINCH_LOST = 40;
    /** The pitch of the low note a refused chest gives. */
    private static final float REFUSED_PITCH = 0.5F;
    /** How long a living load let down without its winch floats down slowly, in ticks. */
    private static final int SLOW_FALL = 30 * 20;

    /** The hooks this client has, for their winches' drums and dials (see {@link #hanging}). */
    private static final Set<CraneHook> ON_THIS_CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

    /** Where its rope's end was last tick, in the world: its swing is the way it has come since. */
    @Nullable
    private Vec3 lastTop;
    /** Where its winch's rope comes out under her floor, in the world, as last held; and the game time it was. */
    @Nullable
    private Vec3 anchor;
    private long heldAt;
    /** The chest, barrel or crate in its net, as an item with all it held. */
    private ItemStack cargo = ItemStack.EMPTY;
    /** Whether it catches what it is lowered onto; and the rope's length it let go of its last load at. */
    private boolean catching = true;
    private double letGoAt;
    /** Whether it has just caught or let go of a load, so its winch's lever comes back to holding. */
    private boolean stopWinch;

    public CraneHook(EntityType<? extends CraneHook> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    /** A hook hung on a winch, at its rope's shortest under where the rope comes out, given in the world. */
    public static CraneHook hungOn(Level level, BlockPos winch, Vec3 anchor) {
        CraneHook hook = new CraneHook(AerolargeRegistry.CRANE_HOOK.get(), level);
        hook.entityData.set(WINCH, winch);
        hook.entityData.set(LENGTH, (float) SHORTEST);
        hook.setPos(anchor.subtract(0, SHORTEST + HOOK_SIZE, 0));
        hook.anchor = anchor;
        hook.heldAt = level.getGameTime();
        return hook;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(WINCH, BlockPos.ZERO);
        builder.define(FROM_WINCH, new Vector3f(0, (float) -SHORTEST, 0));
        builder.define(LENGTH, (float) SHORTEST);
        builder.define(STRAIN, 0F);
        builder.define(CARGO_BLOCK, Optional.empty());
        builder.define(LOAD_WIDTH, 0F);
        builder.define(LOAD_HEIGHT, 0F);
    }

    /** Where its winch is, among her blocks. */
    public BlockPos winch() {
        return entityData.get(WINCH);
    }

    /** How far its rope runs out, in blocks: the drum turns with it. */
    public float length() {
        return entityData.get(LENGTH);
    }

    /** The strain on the rope as a share of what the crane is rated for, 0 hanging empty: what the dial reads. */
    public float strain() {
        return entityData.get(STRAIN);
    }

    /** Where its rope's end is against the winch's rope's way out, in the world's directions. */
    public Vec3 fromWinch() {
        return new Vec3(entityData.get(FROM_WINCH));
    }

    /** The chest, barrel or crate in its net, for its renderer. */
    public Optional<BlockState> cargoBlock() {
        return entityData.get(CARGO_BLOCK);
    }

    /** Whether it holds a load. */
    public boolean loaded() {
        return !getPassengers().isEmpty() || !cargo.isEmpty() || cargoBlock().isPresent();
    }

    /** How high its rope's end is over the bottom of its box: the hook sits on top of its load. */
    public double hookHeight() {
        return getBbHeight();
    }

    /** The hook a winch has on this client, if it is near; for the winch's drum and dial. */
    @Nullable
    public static CraneHook hanging(Level level, BlockPos winch) {
        for (CraneHook hook : ON_THIS_CLIENT) {
            if (hook.level() == level && !hook.isRemoved() && hook.winch().equals(winch)) {
                return hook;
            }
        }
        return null;
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (level().isClientSide) {
            ON_THIS_CLIENT.add(this);
        }
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        ON_THIS_CLIENT.remove(this);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float width = entityData.get(LOAD_WIDTH);
        float height = entityData.get(LOAD_HEIGHT);
        return width > 0 ? EntityDimensions.scalable(width, height) : EntityDimensions.scalable(HOOK_SIZE, HOOK_SIZE);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (LOAD_WIDTH.equals(accessor) || LOAD_HEIGHT.equals(accessor)) {
            refreshDimensions();
        }
    }

    /**
     * Held by its winch this tick, from where its rope comes out under her floor, in the world, as the winch's lever
     * stands: the rope hoisted, held or lowered. Whether the winch should come back to holding: the hook has caught or
     * let go of a load, the rope runs as short or as long as it goes, or a load is not to be hoisted, too heavy (over
     * the crane's rating hanging still, as a camel is) or ridden by a player, with a low note.
     */
    public boolean hold(ServerLevel level, BlockPos winch, Vec3 anchor, int notch) {
        entityData.set(WINCH, winch);
        this.anchor = anchor;
        heldAt = level.getGameTime();
        double length = length();
        double reach = top().distanceTo(anchor);
        if (notch == CraneWinchBlock.HOIST) {
            if (carriesPlayer() || weight() > 1) {
                level.playSound(null, getX(), getY() + hookHeight(), getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 1.0F, REFUSED_PITCH);
                return true;
            }
            length = Math.max(SHORTEST, Math.min(length, reach + SLACK) - WINDING);
        } else if (notch == CraneWinchBlock.LOWER) {
            length = Math.min(Math.min(LONGEST, reach + SLACK), length + WINDING);
        }
        entityData.set(LENGTH, (float) length);
        catching |= letGoAt - length >= CLEARING;
        boolean atEnd = notch == CraneWinchBlock.HOIST ? length <= SHORTEST : notch == CraneWinchBlock.LOWER && length >= LONGEST;
        boolean stop = stopWinch || notch != CraneWinchBlock.HOLD && atEnd;
        stopWinch = false;
        return stop;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            follow();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (level.getGameTime() - heldAt > WINCH_LOST) {
            letDown(level);
            return;
        }
        if (anchor == null) {
            return;
        }
        swing(anchor);
        if (!loaded() && catching) {
            catchBelow(level);
        } else if (loaded() && resting(level)) {
            setDown(level);
        }
        for (Entity load : getPassengers()) {
            load.fallDistance = 0;
        }
        Vec3 offset = top().subtract(anchor);
        entityData.set(FROM_WINCH, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
    }

    /** On a client: where its winch is now, as this client has her, and the hook where the server last had it. */
    private void follow() {
        Vec3 out = winchOut(level(), winch());
        Vec3 top = out.add(fromWinch());
        setPos(top.x, top.y - hookHeight(), top.z);
    }

    /** Where a winch's rope comes out under her floor, in the world, as she lies now on this side. */
    public static Vec3 winchOut(Level level, BlockPos winch) {
        return SableFlight.toWorld(level, Vec3.atBottomCenterOf(winch).subtract(0, 1, 0));
    }

    /** The rope's end, on top of the hook, in the world. */
    private Vec3 top() {
        return position().add(0, hookHeight(), 0);
    }

    /**
     * One tick of its swing: on the way it was going, slowed by the air and pulled down, then held to the rope's
     * length from its winch, and stopped by the land and her hull in its way.
     */
    private void swing(Vec3 anchor) {
        Vec3 top = top();
        Vec3 last = lastTop == null ? top : lastTop;
        Vec3 next = top.add(top.subtract(last).scale(AIR)).add(0, -GRAVITY, 0);
        Vec3 hanging = next.subtract(anchor);
        double length = length();
        if (hanging.length() > length) {
            next = anchor.add(hanging.normalize().scale(length));
        }
        lastTop = top;
        Vec3 wasGoing = top.subtract(last);
        move(MoverType.SELF, next.subtract(top));
        Vec3 going = top().subtract(top);
        boolean taut = top().distanceTo(anchor) > length - SLACK / 2;
        float strain = taut ? (float) (weight() * going.subtract(wasGoing).add(0, GRAVITY, 0).length() / GRAVITY) : 0;
        entityData.set(STRAIN, Mth.lerp(STEADYING, strain(), strain));
    }

    /** What its load counts for against the crane's rating: an animal's or vehicle's size, or how full a chest is. */
    private double weight() {
        if (!cargo.isEmpty()) {
            return Mth.lerp(fullness(cargo), CARGO_EMPTY, CARGO_FULL) / RATED;
        }
        double weight = 0;
        for (Entity load : getPassengers()) {
            weight += load.getBbWidth() * load.getBbWidth() * load.getBbHeight() / RATED;
        }
        return weight;
    }

    /** How full a chest in the net is, from 0 to 1, by the share of a chest's slots it fills. */
    private static float fullness(ItemStack cargo) {
        ItemContainerContents contents = cargo.get(DataComponents.CONTAINER);
        return contents == null ? 0 : Math.min(1, contents.nonEmptyStream().count() / (float) CHEST_SLOTS);
    }

    /** Whether a player rides its load, or the load's own load: such a load is lowered only, never hoisted. */
    private boolean carriesPlayer() {
        for (Entity riding : getIndirectPassengers()) {
            if (riding instanceof Player) {
                return true;
            }
        }
        return false;
    }

    /** Catches whatever it may lift that it has been lowered onto, an animal or vehicle first, else a chest. */
    private void catchBelow(ServerLevel level) {
        AABB reach = getBoundingBox().inflate(REACH, 0, REACH).expandTowards(0, -REACH, 0);
        List<Entity> under = level.getEntities(this, reach, entity -> mayLift(level, entity));
        if (!under.isEmpty()) {
            lift(level, under.getFirst());
            return;
        }
        BlockPos feet = blockPosition();
        BlockPos.betweenClosedStream(feet.offset(-CARGO_REACH, -1, -CARGO_REACH), feet.offset(CARGO_REACH, 0, CARGO_REACH))
            .filter(pos -> level.getBlockState(pos).is(CARGO))
            .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(position())))
            .map(BlockPos::immutable)
            .ifPresent(at -> net(level, at));
    }

    /** Whether it may lift something: of a kind it lifts, not ridden by a player, and if tamed, its owner aboard. */
    private boolean mayLift(ServerLevel level, Entity entity) {
        if (!entity.getType().is(LOADS) || entity.isPassenger() || entity.hasPassenger(rider -> rider instanceof Player)) {
            return false;
        }
        if (entity instanceof OwnableEntity owned && owned.getOwnerUUID() != null) {
            Player owner = level.getPlayerByUUID(owned.getOwnerUUID());
            BlockEntity winch = level.getBlockEntity(winch());
            return owner != null && winch != null && Aboard.isAboard(owner, winch);
        }
        return true;
    }

    /** An animal or vehicle caught, in its sling or on its chains: its box the hook's, the rope taken up to it. */
    private void lift(ServerLevel level, Entity load) {
        setPos(load.position());
        load.startRiding(this, true);
        resize(load.getBbWidth(), load.getBbHeight());
        lastTop = null;
        caught(level);
    }

    /**
     * A chest, barrel or crate taken up in its net with all it holds, if whoever works the winch may break it there:
     * claims and spawn protection may refuse.
     */
    private void net(ServerLevel level, BlockPos at) {
        BlockEntity winch = level.getBlockEntity(winch());
        ServerPlayer operator = winch instanceof CraneWinchBlockEntity crane ? crane.operator(level) : null;
        BlockState state = level.getBlockState(at);
        if (operator == null || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, at, state, operator)).isCanceled()) {
            catching = false;
            letGoAt = length();
            stopWinch = true;
            level.playSound(null, at, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 1.0F, REFUSED_PITCH);
            return;
        }
        ItemStack carried = new ItemStack(state.getBlock());
        BlockEntity held = level.getBlockEntity(at);
        if (held != null) {
            carried.applyComponents(held.collectComponents());
            Clearable.tryClear(held);
        }
        level.removeBlock(at, false);
        cargo = carried;
        entityData.set(CARGO_BLOCK, Optional.of(single(state)));
        setPos(Vec3.atBottomCenterOf(at));
        resize(1, 1);
        lastTop = null;
        caught(level);
    }

    /** A chest one half of a pair stands alone in the net, and wherever it is set down. */
    private static BlockState single(BlockState state) {
        return state.hasProperty(ChestBlock.TYPE) ? state.setValue(ChestBlock.TYPE, ChestType.SINGLE) : state;
    }

    /** Caught with a clack of its latch and a rattle of chain, the winch's lever back to holding. */
    private void caught(ServerLevel level) {
        stopWinch = true;
        level.playSound(null, getX(), getY() + hookHeight(), getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0F, 0.7F);
        level.playSound(null, getX(), getY() + hookHeight(), getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    private void resize(float width, float height) {
        entityData.set(LOAD_WIDTH, width);
        entityData.set(LOAD_HEIGHT, height);
        refreshDimensions();
    }

    /** Whether its load rests on the ground under a lowering winch while she hardly moves: time to let go of it. */
    private boolean resting(ServerLevel level) {
        BlockEntity winch = level.getBlockEntity(winch());
        boolean lowering = winch != null && winch.getBlockState().getBlock() instanceof CraneWinchBlock
            && winch.getBlockState().getValue(CraneWinchBlock.NOTCH) == CraneWinchBlock.LOWER;
        Vec3 moving = lastTop == null ? Vec3.ZERO : top().subtract(lastTop);
        return lowering && onGround() && moving.horizontalDistance() < SETTING_DOWN && strain() < SLACK_STRAIN;
    }

    /** Its load set down where it rests, with a clank: an animal or vehicle let off, a chest stood there, all in it. */
    private void setDown(ServerLevel level) {
        ejectPassengers();
        placeCargo(level);
        resize(0, 0);
        catching = false;
        letGoAt = length();
        stopWinch = true;
        level.playSound(null, getX(), getY() + hookHeight(), getZ(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 0.7F);
        level.playSound(null, getX(), getY() + hookHeight(), getZ(), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F);
    }

    /** The chest in its net stood where it rests, or, with no room, dropped there with all it held. */
    private void placeCargo(ServerLevel level) {
        if (cargo.isEmpty()) {
            entityData.set(CARGO_BLOCK, Optional.empty());
            return;
        }
        BlockPos at = blockPosition();
        BlockState state = cargoBlock().orElse(Block.byItem(cargo.getItem()).defaultBlockState());
        if (level.getBlockState(at).canBeReplaced()) {
            level.setBlock(at, state, Block.UPDATE_ALL);
            BlockEntity placed = level.getBlockEntity(at);
            if (placed != null) {
                placed.applyComponentsFromItemStack(cargo);
                placed.setChanged();
            }
        } else {
            spawnAtLocation(cargo);
        }
        cargo = ItemStack.EMPTY;
        entityData.set(CARGO_BLOCK, Optional.empty());
    }

    /**
     * Left without its winch, as when she is taken away or her winch broken: its load let down gently, an animal or
     * vehicle floating down slowly, a chest on a care package's parachute, and the hook gone.
     */
    private void letDown(ServerLevel level) {
        for (Entity load : List.copyOf(getPassengers())) {
            load.stopRiding();
            if (load instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL));
            }
        }
        if (!cargo.isEmpty()) {
            level.addFreshEntity(FallingCrate.of(level, position(), getDeltaMovement(), cargo, null));
            cargo = ItemStack.EMPTY;
        }
        discard();
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        return position();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return position();
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /** On a client its place comes from its winch, as this client draws her, never from the server's last word. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Winch", NbtUtils.writeBlockPos(winch()));
        tag.putFloat("Length", length());
        if (!cargo.isEmpty()) {
            tag.put("Cargo", cargo.save(registryAccess()));
            cargoBlock().ifPresent(state -> tag.put("CargoBlock", NbtUtils.writeBlockState(state)));
        }
        tag.putBoolean("Catching", catching);
        tag.putDouble("LetGoAt", letGoAt);
        tag.putFloat("LoadWidth", entityData.get(LOAD_WIDTH));
        tag.putFloat("LoadHeight", entityData.get(LOAD_HEIGHT));
    }

    /** A hook loaded from the save waits for its winch to hold it again, its load as it was. */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        NbtUtils.readBlockPos(tag, "Winch").ifPresent(pos -> entityData.set(WINCH, pos));
        entityData.set(LENGTH, tag.contains("Length") ? tag.getFloat("Length") : (float) SHORTEST);
        cargo = ItemStack.parseOptional(registryAccess(), tag.getCompound("Cargo"));
        if (tag.contains("CargoBlock")) {
            entityData.set(CARGO_BLOCK, Optional.of(NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), tag.getCompound("CargoBlock"))));
        }
        catching = !tag.contains("Catching") || tag.getBoolean("Catching");
        letGoAt = tag.getDouble("LetGoAt");
        resize(tag.getFloat("LoadWidth"), tag.getFloat("LoadHeight"));
        heldAt = level().getGameTime();
    }
}
