package dev.skyliners.turret;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.HeadTurn;
import dev.skyliners.block.LaserBolt;
import dev.skyliners.block.Loaded;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.item.LaserFire;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The freighter's laser cannon on her top dome (see {@link LaserCannonBlock}): twin barrels on a swivel, aimed by
 * whoever sits in the gunner's seat under it, its barrels following where they look, from level to near straight up,
 * all the way round. Holding attack fires it (see {@link GunnerTrigger}): bolts from alternate barrels four times a
 * second, each a beam drawn by every client near, with sparks where it strikes, hurting hostile mobs only, never
 * players, villagers, pets, animals or anyone aboard her, and never a block. Twenty bolts in a row and it overheats a
 * moment, hissing. A gunner sitting down hears her computer say the turret is ready.
 *
 * <p>The server works out the aim each tick: what the gunner looks at, along their look from just past her dome, and
 * the way to it from the cannon's pivot, turned into her frame, so its bolts land under their crosshair at any range.
 * It tells the clients now and then, as it moves; each client eases its barrels toward it (see {@link HeadTurn}).
 */
public class LaserCannonBlockEntity extends BlockEntity {
    private static final Loaded<LaserCannonBlockEntity> LOADED = new Loaded<>(LaserCannonBlockEntity.class, true);
    /**
     * How far from the cannon's middle its gunner's eyes may be, seated, in blocks: the seat under her dome's glass.
     */
    public static final double GUNNER_REACH = 4;
    /** How far its barrels tilt, in degrees up from level: from level, so it never fires into her own hull, to this. */
    private static final float HIGHEST = 85;
    /**
     * Where its barrels pivot, in blocks up from its block's foot; how long they are from the pivot to their muzzles,
     * and how far either side of its middle line they stand, in blocks.
     */
    public static final double PIVOT_UP = 0.55;
    public static final double BARREL = 1.9;
    public static final double BARRELS_APART = 0.28;
    /**
     * How far past the gunner's distance from the pivot their look is traced from, in blocks: clear of her dome's glass
     * and the cannon itself, so it finds what they look at outside her.
     */
    private static final double CLEAR_OF_HER = 1.5;
    /**
     * How often it fires while its trigger is held, in ticks a bolt, and how long a held trigger lasts unheard, in
     * ticks.
     */
    private static final int BOLT_EVERY = 5;
    private static final int TRIGGER_LAPSES = 10;
    /** How many bolts in a row overheat it, for how long, in ticks, and how long a pause cools it, in ticks. */
    private static final int MOST_IN_A_ROW = 20;
    private static final int OVERHEATED_TICKS = 40;
    private static final int COOLS_AFTER = 20;
    /**
     * How far its bolts reach, in blocks, how much one hurts a hostile mob, in half hearts, and how far off it is seen.
     */
    private static final double RANGE = 64;
    private static final float DAMAGE = 6;
    private static final double BOLTS_SEEN = 160;
    /** How often, at most, its aim is sent to the clients, in ticks, and how far it must have moved, in degrees. */
    private static final int AIM_SENT_EVERY = 4;
    private static final float AIM_SENT_FROM = 1;
    /** How fast the clients' barrels follow its aim, a share of the way a tick, and at most so many degrees a tick. */
    private static final float SWING_EASE = 0.45F;
    private static final float SWING_STEP = 20;
    /** How loud a bolt, its overheating and her computer's word are, as shares of full. */
    private static final float SHOT_VOLUME = 1.4F;
    private static final float OVERHEAT_VOLUME = 0.8F;
    private static final float VOICE_VOLUME = 0.8F;

    /** Where its barrels point, in degrees: round from her bow, as Minecraft turns, and up from level. */
    private float yaw;
    private float pitch;
    /** The aim the clients were last told, and the game tick they were told it. */
    private float toldYaw;
    private float toldPitch;
    private long toldAt;
    /** Who sits at it, on the server; none while the seat is empty. */
    @Nullable
    private Player gunner;
    /** Whether its trigger is held, and the game tick its gunner last said so. */
    private boolean triggerHeld;
    private long heardAt;
    /**
     * The game tick of its last bolt, how many in a row, the game tick until which it is too hot, and which barrel is
     * next.
     */
    private long lastBolt;
    private int inARow;
    private long hotUntil;
    private boolean leftBarrel;
    /** Its barrels as drawn on a client, easing toward its aim. */
    private final HeadTurn shownYaw = new HeadTurn(SWING_EASE, SWING_STEP, true);
    private final HeadTurn shownPitch = new HeadTurn(SWING_EASE, SWING_STEP, false);

    /** Hears its servos on a client each tick; the client sets it, so the server never loads what it reaches for. */
    public static Consumer<LaserCannonBlockEntity> clientTicker = cannon -> { };

    public LaserCannonBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LASER_CANNON_ENTITY.get(), pos, state);
    }

    /** The laser cannons loaded in a level, on the server or a client. */
    public static List<LaserCannonBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** Its trigger held or let go by a player, on the server: only the player seated at it works it. */
    static void trigger(ServerPlayer player, boolean held) {
        for (LaserCannonBlockEntity cannon : LOADED.in(player.serverLevel())) {
            if (cannon.gunner == player) {
                cannon.triggerHeld = held;
                cannon.heardAt = player.serverLevel().getGameTime();
            }
        }
    }

    /** Each tick on the server: who sits at it, where they look, and its bolts while its trigger is held. */
    void serverTick(ServerLevel level) {
        Player seated = seatedAt(level);
        if (seated != null && seated != gunner) {
            level.playSound(null, worldPosition, SkylinersRegistry.FREIGHTER_COMPUTER_TURRET_READY.get(), SoundSource.BLOCKS, VOICE_VOLUME, 1.0F);
        }
        gunner = seated;
        if (gunner == null) {
            triggerHeld = false;
            return;
        }
        aimAt(level, gunner);
        tellAim(level);
        fire(level);
    }

    /** The player sitting under it, seated, their eyes within {@link #GUNNER_REACH} of it; none for nobody. */
    @Nullable
    private Player seatedAt(ServerLevel level) {
        Vec3 middle = Vec3.atCenterOf(worldPosition);
        for (Player player : level.players()) {
            boolean near = middle.distanceTo(SableFlight.toPlot(level, worldPosition, player.getEyePosition(), false)) <= GUNNER_REACH;
            if (near && player.isPassenger() && !player.isSpectator()) {
                return player;
            }
        }
        return null;
    }

    /**
     * Turns its barrels from its pivot toward what its gunner looks at, in her frame, kept from level up to
     * {@link #HIGHEST}: their look traced from just past her dome, or as far as its bolts reach if it meets nothing.
     */
    private void aimAt(ServerLevel level, Player gunner) {
        Vec3 eye = gunner.getEyePosition();
        Vec3 look = gunner.getLookAngle();
        Vec3 pivot = SableFlight.toWorld(level, pivot());
        Vec3 past = eye.add(look.scale(eye.distanceTo(pivot) + CLEAR_OF_HER));
        Vec3 target = LaserFire.trace(level, gunner, past, look, RANGE, entity -> !Aboard.isAboard(entity, this)).getLocation();
        Vec3 way = SableFlight.turnToPlot(level, worldPosition, target.subtract(pivot)).normalize();
        yaw = (float) Math.toDegrees(Math.atan2(-way.x, way.z));
        pitch = Mth.clamp((float) Math.toDegrees(Math.asin(Mth.clamp(way.y, -1, 1))), 0, HIGHEST);
    }

    /** Where its barrels pivot, among her blocks. */
    private Vec3 pivot() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, PIVOT_UP, 0);
    }

    /** Tells the clients where it aims, now and then, as it moves. */
    private void tellAim(ServerLevel level) {
        long now = level.getGameTime();
        boolean moved = Math.abs(Mth.wrapDegrees(yaw - toldYaw)) >= AIM_SENT_FROM || Math.abs(pitch - toldPitch) >= AIM_SENT_FROM;
        if (moved && now - toldAt >= AIM_SENT_EVERY) {
            toldYaw = yaw;
            toldPitch = pitch;
            toldAt = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Fires a bolt if its trigger is held, it is due and it is not too hot; cools once it has rested. */
    private void fire(ServerLevel level) {
        long now = level.getGameTime();
        boolean held = triggerHeld && now - heardAt <= TRIGGER_LAPSES && SkylinersConfig.FREIGHTER_TURRETS.get();
        if (!held || now < hotUntil) {
            inARow = now - lastBolt > COOLS_AFTER ? 0 : inARow;
            return;
        }
        if (now - lastBolt < BOLT_EVERY) {
            return;
        }
        lastBolt = now;
        bolt(level);
        if (++inARow >= MOST_IN_A_ROW) {
            inARow = 0;
            hotUntil = now + OVERHEATED_TICKS;
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, OVERHEAT_VOLUME, 0.8F);
        }
    }

    /**
     * A bolt from the muzzle of its next barrel along its aim: hostile mobs hurt, never anyone aboard her; drawn for
     * every client near, sparks where it strikes, and its shot heard.
     */
    private void bolt(ServerLevel level) {
        Vec3 way = way(yaw, pitch);
        Vec3 across = way.cross(new Vec3(0, 1, 0)).normalize().scale(leftBarrel ? -BARRELS_APART : BARRELS_APART);
        leftBarrel = !leftBarrel;
        Vec3 muzzle = pivot().add(way.scale(BARREL)).add(across);
        Vec3 from = SableFlight.toWorld(level, muzzle);
        Vec3 along = SableFlight.toWorld(level, muzzle.add(way)).subtract(from);
        HitResult hit = LaserFire.trace(level, gunner, from, along, RANGE, entity -> !Aboard.isAboard(entity, this));
        LaserFire.strike(level, gunner, from, hit, DAMAGE, LaserBolt.RED, BOLTS_SEEN);
        level.playSound(null, from.x, from.y, from.z, SkylinersRegistry.BALL_TURRET_SHOT.get(), SoundSource.BLOCKS, SHOT_VOLUME,
            0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    /** The way its barrels point at an aim, in her frame. */
    public static Vec3 way(float yaw, float pitch) {
        double turn = Math.toRadians(yaw);
        double tilt = Math.toRadians(pitch);
        return new Vec3(-Math.sin(turn) * Math.cos(tilt), Math.sin(tilt), Math.cos(turn) * Math.cos(tilt));
    }

    /** Each tick on a client: its barrels a tick's way toward its aim, its servos heard as they swing. */
    void clientTick() {
        shownYaw.tick();
        shownPitch.tick();
        shownYaw.toward(yaw);
        shownPitch.toward(pitch);
        clientTicker.accept(this);
    }

    /** How far its barrels swung this last tick on a client, round and up together, in degrees. */
    public float swing() {
        return Math.abs(shownYaw.at(1) - shownYaw.at(0)) + Math.abs(shownPitch.at(1) - shownPitch.at(0));
    }

    /** How far round its barrels are drawn at a partial tick, in degrees, as Minecraft turns. */
    public float shownYaw(float partialTick) {
        return shownYaw.at(partialTick);
    }

    /** How far up its barrels are drawn at a partial tick, in degrees. */
    public float shownPitch(float partialTick) {
        return shownPitch.at(partialTick);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    /** Gone, it lets go of its gunner, so it never keeps a player who has left. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
        gunner = null;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
        gunner = null;
    }

    /** Only its aim goes to the clients; nothing of it is saved, the aim following whoever next sits at it. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        yaw = tag.getFloat("Yaw");
        pitch = Mth.clamp(tag.getFloat("Pitch"), 0, HIGHEST);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
