package dev.aerolarge.selfdestruct;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.SelfDestructButtonBlockEntity;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.parachute.Parachute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The self-destruct, on the server: the alarm through an airliner whose button is armed, and the airliner blowing up
 * once its countdown runs out, or at once when the button is pressed again. The alarm is for everyone aboard, wherever
 * they are: the siren and the countdown's beeps follow each of them about (as the door alarm does), and each of them
 * sees the seconds left counted down on their screen (see {@link SelfDestructAlarmPayload}).
 *
 * <p>The airliner blows up once its level's tick is over, never while its block entities tick, since its blocks go with
 * it. Everyone aboard first puts on a parachute, whatever they wore on their chest going into their inventory, and is
 * got out of their seat, so they fall clear; the parachute opens by itself as soon as they fall free, and they float
 * down under it. A fall too short for it to open never hurts them. Anything else alive aboard (a villager passenger, a
 * pet) is put out into the world and floats down slowly. Then the airliner is taken away through Sable, all of it at
 * once with everything loose aboard (items on its floors, trolleys, balls, paper planes, frames and pictures) and
 * nothing dropped, and in its place it blows up in a huge fireball (see {@link Blast}). Nothing round it is touched: no
 * block is broken, nothing set alight, nobody hurt.
 */
public final class SelfDestruct {
    /** How long after the blast a first landing does no harm, in ticks: time for a fall from a parked airliner. */
    private static final int PROTECTION = 60 * SharedConstants.TICKS_PER_SECOND;
    /**
     * How long after the blast the parachutes open by themselves, once their wearers fall free, and the latest they are
     * looked at, in ticks: half a second, and three.
     */
    private static final int OPEN_AFTER = 10;
    private static final int OPEN_BY = 60;
    /** How long anything else alive aboard floats down slowly, in ticks: enough to fall from far above any cruise. */
    private static final int SLOW_FALL = 120 * SharedConstants.TICKS_PER_SECOND;
    /** How loud the siren is, following each listener about. */
    private static final float SIREN_VOLUME = 1.0F;
    /** The countdown beep's pitch at the first beep and at the last: rising as the time runs out. */
    private static final float BEEP_LOW = 0.7F;
    private static final float BEEP_HIGH = 2.0F;
    /**
     * How many columns of the airliner are looked down for a block, and the most spots the fireball bursts from: spots
     * on its blocks all over it, so the fire takes the airliner's own shape, wings, tail and all.
     */
    private static final int SPOT_TRIES = 64;
    private static final int SPOTS = 24;
    /** The A380's wingspan in metres, the widest of it, by which the blast is sized to the airliner as it was built. */
    private static final double WINGSPAN = 79.8;
    /** The blast's size at the smallest and the largest, in blocks per metre, whatever the airliner measures. */
    private static final double SMALLEST = 0.5;
    private static final double LARGEST = 4;

    /** The buttons pressed to blow their airliner up this tick, by level: blown up once the level's tick is over. */
    private static final Map<ServerLevel, List<SelfDestructButtonBlockEntity>> PRESSED = new HashMap<>();
    /**
     * Whoever was aboard an airliner as it blew up, by their id, and the game time until which landing never hurts.
     */
    private static final Map<UUID, Long> PROTECTED = new HashMap<>();
    /** Whoever bailed out of an airliner as it blew up, by their id, and the game time it blew up: their parachutes open. */
    private static final Map<UUID, Long> OPENING = new HashMap<>();

    private SelfDestruct() {
    }

    /** The button armed: the siren for everyone aboard, and the seconds left on their screens. */
    public static void alarm(BlockEntity button, int ticks) {
        long seed = seed(button);
        Component armed = Component.translatable("message.aerolarge.self_destruct.alarm");
        for (ServerPlayer player : Aboard.players(button)) {
            player.connection.send(new ClientboundSoundEntityPacket(AerolargeRegistry.SELF_DESTRUCT_SIREN, SoundSource.BLOCKS, player, SIREN_VOLUME, 1.0F, seed));
            PacketDistributor.sendToPlayer(player, new SelfDestructAlarmPayload(ticks));
            player.displayClientMessage(armed, true);
        }
    }

    /**
     * A beat of the countdown, this many ticks before the time is up, this share of the way through it: a beep for
     * everyone aboard, higher as it goes, and the seconds left on their screens again, for anyone who has come aboard.
     */
    public static void countdown(BlockEntity button, int ticks, float through) {
        float pitch = Mth.lerp(through, BEEP_LOW, BEEP_HIGH);
        long seed = seed(button);
        for (ServerPlayer player : Aboard.players(button)) {
            player.connection.send(new ClientboundSoundEntityPacket(SoundEvents.NOTE_BLOCK_PLING, SoundSource.BLOCKS, player, 1.0F, pitch, seed));
            PacketDistributor.sendToPlayer(player, new SelfDestructAlarmPayload(ticks));
        }
    }

    /** A seed for a sound everyone aboard hears alike, from the button's level. */
    private static long seed(BlockEntity button) {
        return button.getLevel() == null ? 0 : button.getLevel().getRandom().nextLong();
    }

    /** The button disarmed, or taken away: the siren stops and the countdown goes from everyone's screen. */
    public static void silence(BlockEntity button) {
        ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(AerolargeRegistry.SELF_DESTRUCT_SIREN.getId(), SoundSource.BLOCKS);
        for (ServerPlayer player : Aboard.players(button)) {
            player.connection.send(stop);
            PacketDistributor.sendToPlayer(player, SelfDestructAlarmPayload.NONE);
        }
    }

    /** Pressed while armed, or the countdown run out: the button's airliner blows up once the level's tick is over. */
    public static void detonate(ServerLevel level, SelfDestructButtonBlockEntity button) {
        List<SelfDestructButtonBlockEntity> pressed = PRESSED.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (!pressed.contains(button)) {
            pressed.add(button);
        }
    }

    /** Once a level's tick is over: the airliners pressed to blow up do, and the blasts already going on go on. */
    public static void tick(ServerLevel level) {
        List<SelfDestructButtonBlockEntity> pressed = PRESSED.remove(level);
        if (pressed != null) {
            pressed.forEach(button -> blowUp(level, button));
        }
        Blast.tick(level);
        long now = level.getGameTime();
        PROTECTED.values().removeIf(until -> until < now);
        OPENING.entrySet().removeIf(bailed -> opened(level, bailed.getKey(), bailed.getValue()));
    }

    /**
     * Opens the parachute of a player who bailed out as an airliner blew up at the given game time, once they fall free
     * in this level; whether that is done with, opened or too long ago to try any more.
     */
    private static boolean opened(ServerLevel level, UUID id, long blewUp) {
        long now = level.getGameTime();
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
        if (now < blewUp + OPEN_AFTER || player == null || player.level() != level || !Parachute.canOpen(player)) {
            return now > blewUp + OPEN_BY;
        }
        Parachute.open(player);
        return true;
    }

    /** Forgets a level going away, with its blasts. */
    public static void drop(ServerLevel level) {
        PRESSED.remove(level);
        Blast.drop(level);
    }

    /**
     * Forgets everyone who bailed out, as the server stops: their game times mean nothing in the next world opened.
     */
    public static void forget() {
        PROTECTED.clear();
        OPENING.clear();
    }

    /** A first landing after the blast never hurts whoever was aboard. */
    public static void onFall(LivingFallEvent event) {
        LivingEntity faller = event.getEntity();
        if (faller.level().isClientSide) {
            return;
        }
        Long until = PROTECTED.remove(faller.getUUID());
        if (until != null && faller.level().getGameTime() <= until) {
            event.setCanceled(true);
        }
    }

    /**
     * Blows the button's airliner up, if it still may: everyone off it, the airliner taken away, and its blast in its
     * place, round the spots its blocks were.
     */
    private static void blowUp(ServerLevel level, SelfDestructButtonBlockEntity button) {
        if (button.isRemoved() || !button.mayBlowUp()) {
            return;
        }
        BoundingBox blocks = SableFlight.blocks(level, Vec3.atCenterOf(button.getBlockPos()));
        if (blocks == null) {
            return;
        }
        Vec3 centre = SableFlight.toWorld(level, blocks.getCenter().getCenter());
        List<Vec3> spots = burning(level, blocks);
        List<LivingEntity> others = othersAboard(level, button, blocks, centre);
        silence(button);
        Aboard.players(button).forEach(SelfDestruct::bailOut);
        others.forEach(SelfDestruct::floatDown);
        // Sable would put whatever else is aboard out into the world, to fall out of the sky: it goes with the airliner.
        level.getEntities((Entity) null, AABB.of(blocks), entity -> !(entity instanceof LivingEntity)).forEach(Entity::discard);
        if (SableFlight.remove(button)) {
            double size = Mth.clamp(Math.max(blocks.getXSpan(), blocks.getZSpan()) / WINGSPAN, SMALLEST, LARGEST);
            Blast.start(level, centre, spots, size);
        }
    }

    /**
     * Spots on the airliner's blocks, where it is in the world: the top block of each of a few columns of the box round
     * it picked at random, so its fuselage, wings and tail all burn.
     */
    private static List<Vec3> burning(ServerLevel level, BoundingBox blocks) {
        List<Vec3> spots = new ArrayList<>();
        RandomSource random = level.getRandom();
        for (int i = 0; i < SPOT_TRIES && spots.size() < SPOTS; i++) {
            BlockPos top = top(level, blocks, blocks.minX() + random.nextInt(blocks.getXSpan()), blocks.minZ() + random.nextInt(blocks.getZSpan()));
            if (top != null) {
                spots.add(SableFlight.toWorld(level, Vec3.atCenterOf(top)));
            }
        }
        return spots;
    }

    /** The highest block of the airliner in a column of its box; null if the column is empty. */
    @Nullable
    private static BlockPos top(ServerLevel level, BoundingBox blocks, int x, int z) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, blocks.maxY(), z);
        for (; at.getY() >= blocks.minY(); at.move(0, -1, 0)) {
            if (!level.getBlockState(at).isAir()) {
                return at.immutable();
            }
        }
        return null;
    }

    /**
     * Everything alive aboard that is not a player: whatever lives among the airliner's blocks, in its plot (a villager
     * in its seat), and whatever rides it in the world round it.
     */
    private static List<LivingEntity> othersAboard(ServerLevel level, BlockEntity button, BoundingBox blocks, Vec3 centre) {
        AABB plot = AABB.of(blocks);
        List<LivingEntity> others = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, plot, entity -> !(entity instanceof Player)));
        double reach = Math.sqrt(plot.getXsize() * plot.getXsize() + plot.getYsize() * plot.getYsize() + plot.getZsize() * plot.getZsize()) / 2;
        AABB round = new AABB(centre, centre).inflate(reach);
        others.addAll(level.getEntitiesOfClass(LivingEntity.class, round, entity -> !(entity instanceof Player) && Aboard.isAboard(entity, button)));
        return others;
    }

    /**
     * A player aboard as the airliner blows up: out of their seat, where they sat in the world, brought to a stop there
     * (see {@link #stop}), a parachute on, and a first landing that does not hurt.
     */
    private static void bailOut(ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }
        Vec3 at = SableFlight.toWorld(player.level(), player.position());
        boolean seated = player.isPassenger();
        player.stopRiding();
        if (seated || SableFlight.inPlot(player)) {
            player.teleportTo(at.x, at.y, at.z);
        }
        stop(player);
        wearParachute(player);
        player.fallDistance = 0;
        PROTECTED.put(player.getUUID(), player.level().getGameTime() + PROTECTION);
        OPENING.put(player.getUUID(), player.level().getGameTime());
        player.displayClientMessage(Component.translatable("message.aerolarge.self_destruct.parachute"), true);
    }

    /** Puts a parachute on the player unless they wear one, whatever they wore on their chest going into their bag. */
    private static void wearParachute(ServerPlayer player) {
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        if (worn.is(AerolargeRegistry.PARACHUTE.get())) {
            return;
        }
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(AerolargeRegistry.PARACHUTE.get()));
        if (!worn.isEmpty()) {
            player.getInventory().placeItemBackInInventory(worn);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /**
     * Brings someone thrown clear of the airliner to a stop where it blew up. Carried on at its speed, which Sable keeps
     * on whatever leaves it, they sailed on some 400 blocks ahead of the fireball, which stays where it blew up: from a
     * flight deck at cruise, the airliner only vanished. A player's own client moves them, so it is told to stop them
     * too (see {@link ThrownClearPayload}).
     */
    private static void stop(LivingEntity entity) {
        SableFlight.letGo(entity);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
        if (entity instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, ThrownClearPayload.INSTANCE);
        }
    }

    /** Anything else alive aboard: out of its seat, out into the world, stopped, floating down slowly. */
    private static void floatDown(LivingEntity entity) {
        entity.stopRiding();
        SableFlight.sendOut(entity);
        stop(entity);
        entity.fallDistance = 0;
        entity.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL));
        PROTECTED.put(entity.getUUID(), entity.level().getGameTime() + PROTECTION);
    }
}
