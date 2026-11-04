package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.RobotBartenderBlock;
import dev.skyliners.block.RobotBartenderBlockEntity;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the robot bartender moving (see {@link RobotBartenderBlock}): its pedestal and torso are its block's own model,
 * and over them this draws its head, its visor's glow, its arms and what it holds, each a state of the block's PART,
 * drawn as the model faces north and turned about its own joint, the whole then turned the way the bartender faces.
 * Idle, it polishes a glass: the glass held up before its chest in its left hand, the cloth in its right circling over
 * it. Its head turns toward whoever is nearest (see {@link #look}) and its visor blinks now and then, twice in a row
 * now and again. Told of a sale (see {@link RobotBartenderBlockEntity#servedAt}), it holds the glass out over the bar,
 * shakes a cocktail shaker over its shoulder for a second and a half, tips it over the glass to pour, the glass
 * filling, sets the drink down on the bar in front of it, where it stands a few seconds, even as the next sale is
 * served, and goes back to polishing with a fresh glass. Each arm is held as an {@link Arm} at each step, and between
 * steps it moves smoothly from one to the next.
 */
public class RobotBartenderRenderer implements BlockEntityRenderer<RobotBartenderBlockEntity> {
    /**
     * Its joints in its model drawn facing north, in pixels (see tools/generate_resources.mjs): the neck its head turns
     * about; the shoulders, either side, and the elbows under them; and the middle of each hand, which what it holds
     * is kept upright about.
     */
    private static final float NECK_X = 8;
    private static final float NECK_Y = 24;
    private static final float NECK_Z = 7;
    private static final float LEFT_SHOULDER_X = 3.5F;
    private static final float RIGHT_SHOULDER_X = 12.5F;
    private static final float SHOULDER_Y = 21.5F;
    private static final float ARM_Z = 7;
    private static final float ELBOW_Y = 16.5F;
    private static final float HAND_Y = 16.5F;
    private static final float HAND_Z = 1;
    /** How far its head turns either way and tilts up or down at most, in degrees. */
    private static final float MOST_TURN = 70;
    private static final float MOST_TILT = 25;
    /** How far it tilts its head down to watch the glass as it pours and sets the drink down, in degrees. */
    private static final float WATCH_GLASS = -22;
    /** How near a player has to be for its head to turn toward them, in blocks. */
    private static final double LOOK_RANGE = 6;
    /** How far its head has to come round to someone new for its servo to whirr, in degrees, and how quietly. */
    private static final float WHIRR_TURN = 20;
    private static final float WHIRR_VOLUME = 0.6F;
    /** How long after one whirr on this client the next may begin, in ticks, whichever bartenders they come from. */
    private static final int WHIRR_APART = 40;
    /** How often it blinks, in ticks; how long a blink lasts; and every how many blinks it blinks twice. */
    private static final int BLINK_PERIOD = 90;
    private static final int BLINK_TICKS = 3;
    private static final int DOUBLE_BLINK_EVERY = 3;
    /** Spreads bartenders' blinks apart: ticks of their beat for each step of a position's hash. */
    private static final int BEAT_MASK = 0xFF;
    /**
     * How far the polishing cloth circles over the glass, in degrees of the arm's swing and bend, and how fast, in
     * radians a tick.
     */
    private static final float POLISH_CIRCLE = 6;
    private static final float POLISH_SPEED = 0.35F;
    /**
     * How far the shaker is shaken, in degrees of the arm's raise and bend, and how fast, in radians a tick: six times
     * a second.
     */
    private static final float SHAKE_RAISE = 10;
    private static final float SHAKE_BEND = 14;
    private static final float SHAKE_SPEED = 1.9F;
    /** How far the shaker leans back over the shoulder as it is shaken, and rolls in to pour, in degrees. */
    private static final float SHAKER_LEAN = 50;
    private static final float POUR_ROLL = 100;
    /**
     * The steps of a serve, in ticks after the sale: the shaking pose reached, the shaking over (a second and a half
     * after the sale), the pour reached, the glass filled, the pour over, the drink on the bar, let go, and back to
     * polishing; and how long the drink stands on the bar before it is taken.
     */
    private static final float SHAKING = 5;
    private static final float SHAKEN = 30;
    private static final float POURING = 36;
    private static final float FILLED = 40;
    private static final float POURED = 44;
    private static final float SET_DOWN = 52;
    private static final float LET_GO = 56;
    private static final float SERVED = RobotBartenderBlockEntity.SERVE_TICKS;
    private static final float ON_THE_BAR = 100;
    /**
     * How each arm is held through a serve: polishing, the left holding the glass up before the chest and the right
     * the cloth over it; the left holding the glass out over the bar; the right shaking at the shoulder, tipping the
     * shaker over the glass, and lowered as the left sets the drink down on the bar, reaching forward and down.
     */
    private static final Arm LEFT_POLISHING = new Arm(12, 22, 28);
    private static final Arm RIGHT_POLISHING = new Arm(16, 32, 26);
    private static final Arm LEFT_HOLDING_OUT = new Arm(10, 14, -4);
    private static final Arm RIGHT_SHAKING = new Arm(55, 10, 70);
    private static final Arm RIGHT_POURING = new Arm(49, 14, 0);
    private static final Arm RIGHT_LOWERED = new Arm(8, 12, 20);
    private static final Arm LEFT_SETTING_DOWN = new Arm(40, 10, -46);
    /** How far round its middle anything of it is drawn, in blocks, the drink on the bar in front of it with it. */
    private static final float REACH = 1.5F;
    /** How high over its block's foot its middle is, in blocks: its chest. */
    private static final float MIDDLE = 1.2F;

    private final BlockRenderDispatcher blocks;

    /** When the last whirr on this client began, by the drawing clock. */
    private static long lastWhirr = -WHIRR_APART;

    public RobotBartenderRenderer(BlockEntityRendererProvider.Context context) {
        this.blocks = context.getBlockRenderDispatcher();
    }

    /**
     * How an arm is held, in degrees: raised forward and up from hanging straight down, swung in across the chest, and
     * bent at the elbow, up from the forearm pointing straight ahead.
     */
    private record Arm(float raise, float inward, float bend) {
        Arm toward(Arm to, float share) {
            return new Arm(Mth.lerp(share, raise, to.raise), Mth.lerp(share, inward, to.inward), Mth.lerp(share, bend, to.bend));
        }

        Arm plus(float moreRaise, float moreInward, float moreBend) {
            return new Arm(raise + moreRaise, inward + moreInward, bend + moreBend);
        }
    }

    /** Which arm: where its shoulder is across the model, and which way it swings in across the chest. */
    private enum Side {
        LEFT(LEFT_SHOULDER_X, -1, RobotBartenderBlock.Part.LEFT_UPPER_ARM, RobotBartenderBlock.Part.LEFT_FOREARM),
        RIGHT(RIGHT_SHOULDER_X, 1, RobotBartenderBlock.Part.RIGHT_UPPER_ARM, RobotBartenderBlock.Part.RIGHT_FOREARM);

        final float shoulderX;
        /** The sign of a turn about the vertical that swings it in toward the middle. */
        final float in;
        final RobotBartenderBlock.Part upperArm;
        final RobotBartenderBlock.Part forearm;

        Side(float shoulderX, float in, RobotBartenderBlock.Part upperArm, RobotBartenderBlock.Part forearm) {
            this.shoulderX = shoulderX;
            this.in = in;
            this.upperArm = upperArm;
            this.forearm = forearm;
        }
    }

    /**
     * Where the bartender is in its serve, at a moment: how each arm is held, what each hand holds (null for nothing),
     * how far the shaker leans back and rolls in, and whether the drink it served stands on the bar.
     */
    private record Moves(Arm left, Arm right, @Nullable RobotBartenderBlock.Part leftHolds, @Nullable RobotBartenderBlock.Part rightHolds,
        float lean, float roll, boolean onTheBar) {
    }

    @Override
    public void render(RobotBartenderBlockEntity bartender, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = bartender.getLevel();
        if (level == null || !Sight.ahead(pose.last(), 0.5F, MIDDLE, 0.5F, REACH)) {
            return;
        }
        BlockState state = bartender.getBlockState();
        Direction facing = state.getValue(RobotBartenderBlock.FACING);
        BlockState north = state.setValue(RobotBartenderBlock.FACING, Direction.NORTH);
        float time = (float) DrawingClock.ticks(partialTick);
        Moves moves = moves(time, (float) (DrawingClock.gameTime(partialTick) - bartender.servedAt()));
        int light = SableClient.light(level, bartender.getBlockPos().above());
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(180 - facing.toYRot()), 0.5F, 0, 0.5F);
        pose.pushPose();
        pose.rotateAround(Axis.YP.rotationDegrees(bartender.headYaw(partialTick)), NECK_X / 16, NECK_Y / 16, NECK_Z / 16);
        pose.rotateAround(Axis.XP.rotationDegrees(bartender.headPitch(partialTick)), NECK_X / 16, NECK_Y / 16, NECK_Z / 16);
        part(north, RobotBartenderBlock.Part.HEAD, pose, buffers, light);
        if (!blinking(DrawingClock.wholeTicks() + (bartender.getBlockPos().hashCode() & BEAT_MASK))) {
            part(north, RobotBartenderBlock.Part.VISOR, pose, buffers, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
        arm(north, Side.LEFT, moves.left(), moves.leftHolds(), 0, 0, pose, buffers, light);
        arm(north, Side.RIGHT, moves.right(), moves.rightHolds(), moves.lean(), moves.roll(), pose, buffers, light);
        if (moves.onTheBar() || onTheBar(time - bartender.servedBefore())) {
            pose.pushPose();
            toHand(Side.LEFT, LEFT_SETTING_DOWN, pose);
            upright(Side.LEFT, LEFT_SETTING_DOWN, 0, 0, pose);
            part(north, RobotBartenderBlock.Part.DRINK, pose, buffers, light);
            pose.popPose();
        }
        pose.popPose();
    }

    /** One part of the bartender, drawn where the pose has it. */
    private void part(BlockState north, RobotBartenderBlock.Part part, PoseStack pose, MultiBufferSource buffers, int light) {
        RubberDuckRenderer.draw(blocks, north.setValue(RobotBartenderBlock.PART, part), pose, buffers, light, OverlayTexture.NO_OVERLAY);
    }

    /** An arm, held as given, and what its hand holds, kept upright, leaning back and rolling in as given. */
    private void arm(BlockState north, Side side, Arm arm, @Nullable RobotBartenderBlock.Part holds, float lean, float roll, PoseStack pose,
        MultiBufferSource buffers, int light) {
        pose.pushPose();
        toShoulder(side, arm, pose);
        part(north, side.upperArm, pose, buffers, light);
        toElbow(side, arm, pose);
        part(north, side.forearm, pose, buffers, light);
        if (holds != null) {
            upright(side, arm, lean, roll, pose);
            part(north, holds, pose, buffers, light);
        }
        pose.popPose();
    }

    /** Turns the pose as the arm swings from its shoulder: in across the chest, and raised. */
    private static void toShoulder(Side side, Arm arm, PoseStack pose) {
        pose.translate(side.shoulderX / 16, SHOULDER_Y / 16, ARM_Z / 16);
        pose.mulPose(Axis.YP.rotationDegrees(side.in * arm.inward()));
        pose.mulPose(Axis.XP.rotationDegrees(arm.raise()));
        pose.translate(-side.shoulderX / 16, -SHOULDER_Y / 16, -ARM_Z / 16);
    }

    /** Turns the pose, swung from its shoulder, as the forearm bends at the elbow. */
    private static void toElbow(Side side, Arm arm, PoseStack pose) {
        pose.rotateAround(Axis.XP.rotationDegrees(arm.bend()), side.shoulderX / 16, ELBOW_Y / 16, ARM_Z / 16);
    }

    /** The pose taken to the hand of an arm held as given, for what it holds. */
    private static void toHand(Side side, Arm arm, PoseStack pose) {
        toShoulder(side, arm, pose);
        toElbow(side, arm, pose);
    }

    /**
     * Keeps what the hand holds upright however the arm is raised and bent, then leans it back and rolls it in toward
     * the middle by the degrees given, about the middle of the hand.
     */
    private static void upright(Side side, Arm arm, float lean, float roll, PoseStack pose) {
        float x = side.shoulderX / 16;
        pose.rotateAround(Axis.XP.rotationDegrees(lean - arm.raise() - arm.bend()), x, HAND_Y / 16, HAND_Z / 16);
        pose.rotateAround(Axis.ZP.rotationDegrees(side.in * roll), x, HAND_Y / 16, HAND_Z / 16);
    }

    /** Whether the visor is dark in a blink at this tick of the bartender's own beat: once a period, at times twice. */
    private static boolean blinking(long beat) {
        long into = Math.floorMod(beat, BLINK_PERIOD);
        boolean twice = Math.floorDiv(beat, BLINK_PERIOD) % DOUBLE_BLINK_EVERY == 0;
        return into < BLINK_TICKS || (twice && into >= 2 * BLINK_TICKS && into < 3 * BLINK_TICKS);
    }

    /** Where the bartender is in its serve this many ticks after the sale, and how it polishes the glass otherwise. */
    private static Moves moves(float time, float age) {
        float circle = time * POLISH_SPEED;
        Arm polishing = RIGHT_POLISHING.plus(0, POLISH_CIRCLE * Mth.sin(circle), POLISH_CIRCLE * Mth.cos(circle));
        boolean onTheBar = onTheBar(age);
        if (age < 0 || age >= SERVED) {
            return new Moves(LEFT_POLISHING, polishing, RobotBartenderBlock.Part.GLASS, RobotBartenderBlock.Part.CLOTH, 0, 0, onTheBar);
        }
        if (age < SHAKEN) {
            // Shaken hardest between easing in and easing out, so it leaves the pose and comes back to it smoothly.
            float shake = Mth.sin(age * SHAKE_SPEED) * eased(age / SHAKING) * eased((SHAKEN - age) / SHAKING);
            Arm shaking = RIGHT_SHAKING.plus(SHAKE_RAISE * shake, 0, SHAKE_BEND * shake);
            float into = eased(age / SHAKING);
            return new Moves(LEFT_POLISHING.toward(LEFT_HOLDING_OUT, into), polishing.toward(shaking, into), RobotBartenderBlock.Part.GLASS,
                RobotBartenderBlock.Part.SHAKER, SHAKER_LEAN * into, 0, false);
        }
        if (age < POURED) {
            float into = eased((age - SHAKEN) / (POURING - SHAKEN));
            RobotBartenderBlock.Part glass = age < FILLED ? RobotBartenderBlock.Part.GLASS : RobotBartenderBlock.Part.DRINK;
            return new Moves(LEFT_HOLDING_OUT, RIGHT_SHAKING.toward(RIGHT_POURING, into), glass, RobotBartenderBlock.Part.SHAKER,
                SHAKER_LEAN * (1 - into), POUR_ROLL * into, false);
        }
        if (age < LET_GO) {
            float into = eased((age - POURED) / (SET_DOWN - POURED));
            return new Moves(LEFT_HOLDING_OUT.toward(LEFT_SETTING_DOWN, into), RIGHT_POURING.toward(RIGHT_LOWERED, into), RobotBartenderBlock.Part.DRINK,
                RobotBartenderBlock.Part.SHAKER, 0, POUR_ROLL * (1 - into), false);
        }
        float into = eased((age - LET_GO) / (SERVED - LET_GO));
        return new Moves(LEFT_SETTING_DOWN.toward(LEFT_POLISHING, into), RIGHT_LOWERED.toward(polishing, into), null, RobotBartenderBlock.Part.CLOTH, 0, 0,
            onTheBar);
    }

    /** Whether the drink of a serve begun this many ticks ago stands on the bar: set down and not yet taken. */
    private static boolean onTheBar(float age) {
        return age >= LET_GO && age < ON_THE_BAR;
    }

    /** A share of the way from one pose to the next, eased in and out so it starts and stops gently; 1 past the end. */
    private static float eased(float share) {
        float clamped = Mth.clamp(share, 0, 1);
        return clamped * clamped * (3 - 2 * clamped);
    }

    /**
     * Each tick of a bartender on the client: its head turns toward the nearest player within {@link #LOOK_RANGE}
     * blocks, or ahead with nobody near, or down at the glass as it pours and sets the drink down; and its servo whirrs
     * as it turns to someone new. The players are in the world, the bartender among an airliner's blocks once it flies,
     * so each player is found among its blocks first, by Sable's own inverse of how they lie. The inverse of that as a
     * matrix will not do: those blocks lie twenty million blocks out, where the rounding on a ship leaning in flight
     * moves a player standing still by ten blocks from tick to tick, in and out of reach, each return someone new to
     * whirr at.
     */
    public static void look(RobotBartenderBlockEntity bartender) {
        Level level = bartender.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = bartender.getBlockPos();
        Direction facing = bartender.getBlockState().getValue(RobotBartenderBlock.FACING);
        Vec3 neck = Vec3.atLowerCornerOf(pos).add(fromModel(facing, new Vec3(NECK_X / 16 - 0.5, NECK_Y / 16, NECK_Z / 16 - 0.5)).add(0.5, 0, 0.5));
        Player nearest = nearest(level, pos, neck);
        float age = level.getGameTime() - bartender.servedAt();
        if (age >= POURING && age < LET_GO) {
            bartender.turnHead(0, WATCH_GLASS);
            return;
        }
        if (nearest == null) {
            bartender.lookAt(null);
            bartender.turnHead(0, 0);
            return;
        }
        Vec3 way = toModel(facing, SableClient.toPlot(level, pos, nearest.getEyePosition(), false).subtract(neck));
        float yaw = Mth.clamp((float) Math.toDegrees(Math.atan2(-way.x, -way.z)), -MOST_TURN, MOST_TURN);
        float pitch = Mth.clamp((float) Math.toDegrees(Math.atan2(way.y, Math.hypot(way.x, way.z))), -MOST_TILT, MOST_TILT);
        UUID player = nearest.getUUID();
        if (!player.equals(bartender.lookingAt()) && Math.abs(yaw - bartender.headYaw(1)) > WHIRR_TURN) {
            whirr(level, neck);
        }
        bartender.lookAt(player);
        bartender.turnHead(yaw, pitch);
    }

    /**
     * A servo's whirr, unless one began too lately on this client, from this bartender or another: a crowd of them all
     * turn as someone walks among them, and one whirr now and then stands for them all. Heard where the bartender is in
     * the world: aboard an airliner in flight its block is in Sable's plot.
     */
    private static void whirr(Level level, Vec3 neck) {
        long now = DrawingClock.wholeTicks();
        if (now - lastWhirr < WHIRR_APART) {
            return;
        }
        lastWhirr = now;
        Vec3 heard = SableClient.toWorld(level, neck);
        level.playLocalSound(heard.x, heard.y, heard.z, SkylinersRegistry.BARTENDER_SERVO.get(), SoundSource.BLOCKS, WHIRR_VOLUME, 1.0F, false);
    }

    /** The nearest player, not a spectator, whose eyes are in reach of the neck, both among the airliner's blocks. */
    @Nullable
    private static Player nearest(Level level, BlockPos pos, Vec3 neck) {
        Player nearest = null;
        double best = LOOK_RANGE * LOOK_RANGE;
        for (Player player : level.players()) {
            double distance = SableClient.toPlot(level, pos, player.getEyePosition(), false).distanceToSqr(neck);
            if (!player.isSpectator() && distance < best) {
                nearest = player;
                best = distance;
            }
        }
        return nearest;
    }

    /** A way in the bartender's model, drawn facing north, turned the way it faces among the blocks. */
    private static Vec3 fromModel(Direction facing, Vec3 way) {
        return way.yRot((float) Math.toRadians(180 - facing.toYRot()));
    }

    /** A way among the blocks turned into the bartender's model, drawn facing north. */
    private static Vec3 toModel(Direction facing, Vec3 way) {
        return way.yRot((float) -Math.toRadians(180 - facing.toYRot()));
    }

    /** Its head reaches into the block above, and the drink it sets down onto the bar in front of it. */
    @Override
    public AABB getRenderBoundingBox(RobotBartenderBlockEntity bartender) {
        return new AABB(bartender.getBlockPos()).expandTowards(0, 1, 0).inflate(1, 0, 1);
    }
}
