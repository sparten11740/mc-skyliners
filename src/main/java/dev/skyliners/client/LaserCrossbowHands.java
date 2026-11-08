package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.item.LaserCrossbowItem;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * How the laser crossbow is held (see {@link LaserCrossbowItem}). The game poses its own crossbow by its own code, so
 * this poses it all.
 *
 * <p>In first person it is held low on its side of the view, its muzzle toward the crosshair; raised to aim as it fires,
 * kicked back and up by each shot, and lowered again a moment after the last, so held down it stays raised, bucking
 * with each shot; and swung as anything held is when its holder attacks. Every use of an item drops it out of sight
 * and swings it back up, as if it were taken in hand again: that is left out while it is raised or kicking back, which
 * every use of it is. In third person it is aimed in both hands a moment after each shot, as the game aims its own
 * crossbow charged.
 *
 * <p>It also says where its muzzle is drawn, which its bolts are drawn leaving (see {@link LaserBolts}).
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
    /**
     * How long it kicks back after a shot, in ticks, as long as the game takes to repeat a held use, how far back at
     * first, in blocks, and how far up, in degrees.
     */
    private static final double KICK_TICKS = 4;
    private static final float KICK_BACK = 0.1F;
    private static final float KICK_UP = 9;
    /** How long it stays raised after a shot, and how long it then takes to lower, in first person, in ticks. */
    private static final double RAISED_TICKS = 10;
    private static final double LOWER_TICKS = 8;
    /** How long it is still aimed in both hands after a shot, in third person, in ticks. */
    private static final long AIMED_AFTER_SHOT = 10;
    /** How much the hand lags behind the view as it turns, as the game has it, as a share of the turn. */
    private static final float SWAY = 0.1F;
    /** The field of view the game draws the hand at, in degrees, whatever the player's own. */
    private static final double HAND_FOV = 70;
    /** When its own player last fired one from each hand, main hand first, by the client's own count of ticks. */
    private static final double[] FIRED = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
    /**
     * Where its muzzle is in its model, in the model's pixels: the face of the emitter at the front of its rail, as
     * tools/generate_resources.mjs draws it.
     */
    private static final Vector3f MUZZLE = new Vector3f(8, 9.25F, -1.75F);
    /**
     * Where its muzzle is held in third person, aimed in both hands, from the holder's eyes: so far ahead along their
     * look and so far down, in blocks.
     */
    private static final double HELD_AHEAD = 0.95;
    private static final double HELD_DOWN = 0.18;
    /**
     * How near its holder's eyes the camera must be for them to be seeing through their own eyes, with the crossbow
     * drawn in first person, in blocks: as near as the eyes are drawn, moving as they crouch or stand.
     */
    private static final double AT_THE_EYES = 0.5;

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

    /** In third person: aimed in both hands a moment after a shot, else held as anything is. */
    @Nullable
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity holder, InteractionHand hand, ItemStack stack) {
        long shot = LaserCrossbowItem.lastShot(stack);
        boolean justFired = shot > 0 && Math.abs(holder.level().getGameTime() - shot) < AIMED_AFTER_SHOT;
        return justFired ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick,
        float taken, float swing) {
        hold(pose, player, arm, partialTick, taken, swing);
        return true;
    }

    /**
     * Poses it in first person in its holder's hand on one side, so far through this tick, so far through being taken
     * in hand and so far through a swing, from the eye: before its model's own transform for first person.
     */
    private static void hold(PoseStack pose, LivingEntity holder, HumanoidArm arm, float partialTick, float taken, float swing) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        InteractionHand hand = arm == holder.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        double sinceShot = Math.max(0, DrawingClock.ticks(partialTick) - FIRED[hand.ordinal()]);
        float kick = sinceShot < KICK_TICKS ? (float) Mth.square(1 - sinceShot / KICK_TICKS) : 0;
        float aim = aim(sinceShot);
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

    /** How far it is raised to aim, from 0 at rest to 1, so many ticks after its last shot: up, then lowering again. */
    private static float aim(double sinceShot) {
        float raised = (float) Mth.clamp(1 - (sinceShot - RAISED_TICKS) / LOWER_TICKS, 0, 1);
        return raised * raised * (3 - 2 * raised);
    }

    /**
     * Where the muzzle of the laser crossbow someone holds is drawn, in the world, so far through this tick, as the
     * camera given sees the world through the projection given; null when they hold none.
     */
    @Nullable
    static Vec3 muzzle(Entity holder, Camera camera, Matrix4f projection, float partialTick) {
        InteractionHand hand = holder instanceof LivingEntity living ? handHolding(living) : null;
        if (hand == null) {
            return null;
        }
        LivingEntity living = (LivingEntity) holder;
        Vec3 eye = living.getEyePosition(partialTick);
        if (holder instanceof LocalPlayer player && seenThroughEyes(player, camera, eye)) {
            return firstPersonMuzzle(player, hand, camera, projection, partialTick);
        }
        return eye.add(living.getViewVector(partialTick).scale(HELD_AHEAD)).subtract(0, HELD_DOWN, 0);
    }

    /** Whether its own player sees through their own eyes, their crossbow drawn in first person. */
    private static boolean seenThroughEyes(LocalPlayer player, Camera camera, Vec3 eye) {
        Minecraft minecraft = Minecraft.getInstance();
        return player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson() && !camera.isDetached()
            && camera.getPosition().distanceToSqr(eye) < AT_THE_EYES * AT_THE_EYES;
    }

    /** The hand that holds a laser crossbow, the main hand first; null when neither does. */
    @Nullable
    private static InteractionHand handHolding(LivingEntity holder) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (holder.getItemInHand(hand).getItem() instanceof LaserCrossbowItem) {
                return hand;
            }
        }
        return null;
    }

    /**
     * Where the muzzle of the crossbow its own player holds in first person is drawn, in the world: posed in their
     * hand as it is drawn there, lagging the view as the hand does, and taken out to where it shows on screen, the hand
     * being drawn at a field of view of its own.
     */
    private static Vec3 firstPersonMuzzle(LocalPlayer player, InteractionHand hand, Camera camera, Matrix4f projection, float partialTick) {
        PoseStack pose = new PoseStack();
        float lagPitch = Mth.lerp(partialTick, player.xBobO, player.xBob);
        float lagYaw = Mth.lerp(partialTick, player.yBobO, player.yBob);
        pose.mulPose(Axis.XP.rotationDegrees((player.getViewXRot(partialTick) - lagPitch) * SWAY));
        pose.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partialTick) - lagYaw) * SWAY));
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        hold(pose, player, arm, partialTick, 0, 0);
        ItemStack stack = player.getItemInHand(hand);
        boolean left = arm == HumanoidArm.LEFT;
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, player.level(), player, player.getId());
        ClientHooks.handleCameraTransforms(pose, model, left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            left);
        pose.translate(-0.5F, -0.5F, -0.5F);
        Vector3f view = pose.last().pose().transformPosition(new Vector3f(MUZZLE).div(16));
        float widen = (float) (1 / (projection.m11() * Math.tan(Math.toRadians(HAND_FOV / 2))));
        Vector3f turned = camera.rotation().transform(new Vector3f(view.x * widen, view.y * widen, view.z));
        return camera.getPosition().add(turned.x, turned.y, turned.z);
    }
}
