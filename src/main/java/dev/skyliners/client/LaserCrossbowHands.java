package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.item.LaserCrossbowItem;
import javax.annotation.Nullable;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * How the laser crossbow is held (see {@link LaserCrossbowItem}). The game poses its own crossbow by its own code and
 * draws anything else being charged as a crossbow at the camera itself, a dark sliver across the view, so this poses it
 * all.
 *
 * <p>In first person it is held low on its side of the view, its muzzle toward the crosshair; raised to aim as it is
 * charged and while it stays charged; kicked back and up as it fires, then lowered again; and swung as anything held
 * is when its holder attacks. Every use of an item drops it out of sight and swings it back up, as if it were taken in
 * hand again: that is left out while it is raised or kicking back, which every use of it is. In third person it is
 * cocked as the game's own crossbow is while it charges, and aimed in both hands while it is charged and a moment after
 * it fires, as the game aims its own charged.
 */
public final class LaserCrossbowHands implements IClientItemExtensions {
    /**
     * Where it is held at rest in first person, as the game holds anything, in blocks from the eye: out to its side,
     * down, ahead; and how far it drops out of sight while it is taken in hand.
     */
    private static final float REST_ACROSS = 0.56F;
    private static final float REST_DOWN = -0.52F;
    private static final float REST_AHEAD = -0.72F;
    private static final float TAKEN_DROP = -0.6F;
    /** How far it is raised to aim, from rest, in blocks: in toward the middle of the view, up and back. */
    private static final float AIM_ACROSS = 0.14F;
    private static final float AIM_UP = 0.07F;
    private static final float AIM_BACK = 0.05F;
    /** How long it kicks back after a shot, in ticks, how far back at first, in blocks, and how far up, in degrees. */
    private static final double KICK_TICKS = 6;
    private static final float KICK_BACK = 0.15F;
    private static final float KICK_UP = 14;
    /** How long it takes to lower from aiming after a shot, in first person, in ticks. */
    private static final double LOWER_TICKS = 8;
    /** How long it is still aimed in both hands after a shot, in third person, in ticks. */
    private static final long AIMED_AFTER_SHOT = 10;
    /** When its own player last fired one from each hand, main hand first, by the client's own count of ticks. */
    private static final double[] FIRED = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
    private LaserCrossbowHands() {
    }

    /** Sets it up on this client, from the client's own setup. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterClientExtensionsEvent event) -> event.registerItem(new LaserCrossbowHands(),
            SkylinersRegistry.LASER_CROSSBOW.get()));
        LaserCrossbowItem.clientFired = LaserCrossbowHands::fired;
    }

    /**
     * Its own player has fired one from a hand, as the use key went down during this tick: it kicks back from the next
     * frame drawn, after the tick.
     */
    private static void fired(InteractionHand hand) {
        FIRED[hand.ordinal()] = DrawingClock.wholeTicks() + 1;
    }

    /** In third person: aimed in both hands while charged and a moment after a shot, else held as anything is. */
    @Nullable
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity holder, InteractionHand hand, ItemStack stack) {
        long shot = LaserCrossbowItem.lastShot(stack);
        boolean justFired = shot > 0 && Math.abs(holder.level().getGameTime() - shot) < AIMED_AFTER_SHOT;
        return LaserCrossbowItem.charged(stack) || justFired ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick,
        float taken, float swing) {
        hold(pose, player, arm, stack, partialTick, taken, swing);
        return true;
    }

    /**
     * Poses it in first person in its holder's hand on one side, so far through this tick, so far through being taken
     * in hand and so far through a swing, from the eye: before its model's own transform for first person.
     */
    private static void hold(PoseStack pose, LivingEntity holder, HumanoidArm arm, ItemStack stack, float partialTick, float taken, float swing) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        InteractionHand hand = arm == holder.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        double sinceShot = Math.max(0, DrawingClock.ticks(partialTick) - FIRED[hand.ordinal()]);
        float kick = sinceShot < KICK_TICKS ? (float) Mth.square(1 - sinceShot / KICK_TICKS) : 0;
        float aim = aim(stack, holder, partialTick, sinceShot);
        boolean steady = aim > 0 || kick > 0;
        float reach = Mth.sqrt(swing);
        if (swing > 0) {
            pose.translate(side * -0.4F * Mth.sin(reach * Mth.PI), 0.2F * Mth.sin(reach * Mth.TWO_PI), -0.2F * Mth.sin(swing * Mth.PI));
        }
        pose.translate(side * REST_ACROSS, REST_DOWN + (steady ? 0 : taken) * TAKEN_DROP, REST_AHEAD);
        if (swing > 0) {
            swung(pose, side, swing, reach);
        }
        pose.translate(-side * AIM_ACROSS * aim, AIM_UP * aim, AIM_BACK * aim + KICK_BACK * kick);
        pose.mulPose(Axis.XP.rotationDegrees(KICK_UP * kick));
    }

    /** The game's own swing of anything held, as its holder attacks. */
    private static void swung(PoseStack pose, int side, float swing, float reach) {
        float early = Mth.sin(swing * swing * Mth.PI);
        float late = Mth.sin(reach * Mth.PI);
        pose.mulPose(Axis.YP.rotationDegrees(side * (45 - early * 20)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * late * -20));
        pose.mulPose(Axis.XP.rotationDegrees(late * -80));
        pose.mulPose(Axis.YP.rotationDegrees(side * -45));
    }

    /**
     * How far it is raised to aim, from 0 at rest to 1: as far as it is through charging, wholly while it is charged,
     * and lowering again after a shot.
     */
    private static float aim(ItemStack stack, LivingEntity holder, float partialTick, double sinceShot) {
        if (LaserCrossbowItem.charged(stack)) {
            return 1;
        }
        float charging = LaserCrossbowItem.charging(stack, holder, partialTick);
        float lowering = (float) Math.max(0, 1 - sinceShot / LOWER_TICKS);
        float raised = Math.max(charging, lowering);
        return raised * raised * (3 - 2 * raised);
    }
}
