package dev.aerolarge.client;

import com.mojang.math.Axis;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.entity.Rover;
import dev.aerolarge.item.RoverRemoteItem;
import dev.aerolarge.rover.DriveRover;
import dev.aerolarge.rover.RoverRoute;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Driving the rocket's rover from its remote (see {@link RoverRemoteItem}), on the local client: the player's view
 * goes to a chase camera behind and over the rover, following it round as it turns, and the mouse swings the camera
 * round it; the movement keys steer it, sent to the server as they change and a few times a second besides (see
 * {@link DriveRover}), and jump sounds its horn. The player's body stays where it stood, kept from the keys, ticking as
 * it would looking itself (as {@link PassengerCameras} lends it the view each tick), and sneaking gives them their view
 * back. So does anything hurting them, the remote leaving their hand, the rover going, and the rover being home in its
 * cradle a moment; and the server never lets a driver take fall damage meanwhile.
 *
 * <p>Over the picture: the camera's name and a blinking green light, signal bars that drop as the rover gets further
 * from her, and near the edge of its range a blinking amber arrow pointing back to her and a soft warning tone; a red
 * warning triangle while a monster is near the driver's own body; and which keys do what along the bottom.
 */
public final class RoverDriving {
    /**
     * How far behind the rover the camera stands, how high over its feet it looks at, and how far clear of a block in
     * its way it keeps, in blocks.
     */
    private static final double CAMERA_BACK = 5.5;
    private static final double LOOK_AT = 1.2;
    private static final double CLEAR_OF_BLOCKS = 0.3;
    /** How far down the camera looks at the rover, in degrees, and how far down and up the mouse may swing it. */
    private static final float CAMERA_PITCH = 18;
    private static final float LOWEST_PITCH = -10;
    private static final float HIGHEST_PITCH = 75;
    /** How much of the way round to the rover's heading the camera swings each tick. */
    private static final float FOLLOWING = 0.12F;
    /** How often the keys are sent while they do not change, in ticks: four times a second. */
    private static final int KEEPALIVE = 5;
    /**
     * How long the rover is home in its cradle, in ticks, before the view goes back to the player; and how long a rover
     * in its cradle is waited for to set off once its remote is used, in ticks, before the drive is given up.
     */
    private static final int HOME_TICKS = 40;
    private static final int SETTING_OFF = 20;
    /** How near the edge of its range the warnings begin, in blocks, and how often the warning sounds, in ticks. */
    private static final double WARNING = 12;
    private static final int TONE_TICKS = 20;
    /**
     * The warning tone: how loud, and how much higher it is at the very edge than where the warnings begin; and the
     * low note as a monster comes near the driver's body, its pitch.
     */
    private static final float TONE_VOLUME = 0.6F;
    private static final float TONE_RISE = 0.5F;
    private static final float DANGER_PITCH = 0.6F;
    /** How near the driver's own body a monster warns them, in blocks, and how often that is looked at, in ticks. */
    private static final double DANGER_REACH = 10;
    private static final int DANGER_LOOK = 10;
    /** How often the lights over the picture blink, in ticks. */
    private static final int BLINK_TICKS = 10;
    /** The signal bars: how many, how wide and how far apart, and the tallest, in GUI pixels. */
    private static final int BARS = 4;
    private static final int BAR_WIDTH = 4;
    private static final int BAR_GAP = 2;
    private static final int BAR_TALLEST = 16;
    /** The arrow back to her: its length, its head's half width and how far down the screen it is, in GUI pixels. */
    private static final int ARROW_LENGTH = 26;
    private static final int ARROW_HEAD = 12;
    private static final int ARROW_SHAFT = 3;
    private static final int ARROW_Y = 44;
    /**
     * The warning triangle's size, and where it is from the left and the top; and the mark in it, how far over its base
     * its stroke ends, and its dot's top and bottom, in GUI pixels.
     */
    private static final int TRIANGLE = 26;
    private static final int TRIANGLE_X = 12;
    private static final int TRIANGLE_Y = 40;
    private static final int STROKE_FOOT = 8;
    private static final int DOT_TOP = 5;
    private static final int DOT_BOTTOM = 3;
    /**
     * Where the label and the bars sit from the corners, the margin of their backing, the size of the label's light,
     * and how far up from the bottom the hint is, in GUI pixels.
     */
    private static final int MARGIN = 10;
    private static final int BACKING_MARGIN = 4;
    private static final int LIGHT = 5;
    private static final int HINT_Y = 40;

    private static final int BACKING = 0x90000000;
    private static final int LIVE = 0xFF40E060;
    private static final int WHITE = 0xFFF2F6FB;
    private static final int HINT = 0xFFB8C4D0;
    private static final int AMBER = 0xFFFFB020;
    private static final int RED = 0xFFE03030;
    private static final int BAR_EMPTY = 0x60FFFFFF;

    /** The drive, while there is one. */
    @Nullable
    private static Driving driving;
    /** A rover in its cradle whose remote was just used, waited for to set off, and how long it has been waited for. */
    @Nullable
    private static Rover waiting;
    private static int waitingTicks;
    /** Whether the sneak key is kept from the player until it is let go, so leaving does not also crouch them. */
    private static boolean holdingSneak;

    private RoverDriving() {
    }

    /** What is driven, from where, and what to give back afterwards. */
    private static final class Driving {
        final Rover rover;
        final UUID id;
        final Lens lens;
        final CameraType cameraType;
        final float yRot;
        final float xRot;
        /** The camera's heading, swinging round after the rover's, in degrees, this tick and the last. */
        float heading;
        float lastHeading;
        /** What was last sent, and how many ticks ago. */
        float forward;
        float turn;
        boolean honk;
        int sinceSent;
        int homeTicks;
        int ticks;
        /** Whether a monster is near the driver's body, and how far the rover is from her, in blocks. */
        boolean danger;
        double fromHer;
        /** Which way she is from the rover, as a heading, in degrees. */
        float toHer;

        Driving(Rover rover, Lens lens, CameraType cameraType, float yRot, float xRot) {
            this.rover = rover;
            this.id = rover.getUUID();
            this.lens = lens;
            this.cameraType = cameraType;
            this.yRot = yRot;
            this.xRot = xRot;
            this.heading = rover.getYRot();
            this.lastHeading = heading;
        }
    }

    /**
     * The local player has used a remote paired with a rover: the drive begins, if the rover is here to drive; with the
     * rover still in its cradle, once the server has sent it on its way down, if it does so within a moment.
     */
    public static void use(UUID id) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Rover rover = level == null ? null : find(level, id);
        if (rover == null || driving != null) {
            return;
        }
        if (rover.state() == Rover.State.STOWED) {
            waiting = rover;
            waitingTicks = 0;
            return;
        }
        begin(minecraft, rover);
    }

    /** The drive begun: the view to the rover's camera. */
    private static void begin(Minecraft minecraft, Rover rover) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        waiting = null;
        if (player == null || level == null || minecraft.getCameraEntity() != player) {
            return;
        }
        driving = new Driving(rover, new Lens(level, 0), minecraft.options.getCameraType(), player.getYRot(), player.getXRot());
        holdingSneak = true;
        place(driving, minecraft.getTimer().getGameTimeDeltaPartialTick(true));
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(driving.lens);
        heard(level, driving, AerolargeRegistry.ROVER_START.get());
    }

    @Nullable
    private static Rover find(ClientLevel level, UUID id) {
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof Rover rover && id.equals(rover.getUUID())) {
                return rover;
            }
        }
        return null;
    }

    /** Gives the player their own view back, as it was, the rover stopped; unless something else has taken it over. */
    private static void leave(Minecraft minecraft) {
        Driving drive = driving;
        if (drive == null) {
            return;
        }
        driving = null;
        PacketDistributor.sendToServer(new DriveRover(drive.rover.getId(), 0, 0, false));
        minecraft.options.setCameraType(drive.cameraType);
        LocalPlayer player = minecraft.player;
        Entity camera = minecraft.getCameraEntity();
        if (player != null && (camera == drive.lens || camera == player)) {
            minecraft.setCameraEntity(player);
            player.setYRot(drive.yRot);
            player.setXRot(drive.xRot);
            player.yRotO = drive.yRot;
            player.xRotO = drive.xRot;
        }
        if (minecraft.level != null) {
            heard(minecraft.level, drive, AerolargeRegistry.ROVER_STOP.get());
        }
    }

    /** Before each tick the view goes back to the player, so they tick as they would looking themselves. */
    public static void lend(ClientTickEvent.Pre event) {
        Driving drive = driving;
        Minecraft minecraft = Minecraft.getInstance();
        if (drive == null || minecraft.player == null) {
            return;
        }
        if (minecraft.getCameraEntity() != drive.lens) {
            leave(minecraft);
            return;
        }
        minecraft.setCameraEntity(minecraft.player);
    }

    /**
     * At the end of each tick: leaves once anything the drive needs is gone; otherwise the keys are sent, the camera
     * swings round after the rover, the warnings are worked out, and the lens takes the view back.
     */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (waiting != null) {
            waitFor(minecraft, waiting);
        }
        Driving drive = driving;
        if (drive == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (lost(minecraft, player, drive)) {
            leave(minecraft);
            return;
        }
        minecraft.setCameraEntity(drive.lens);
        drive.ticks++;
        drive.lastHeading = drive.heading;
        drive.heading += Mth.wrapDegrees(drive.rover.getYRot() - drive.heading) * FOLLOWING;
        steer(minecraft.options, drive);
        watch(minecraft, player, drive);
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    /** A rover in its cradle waited for: the drive begins as it sets off, and is given up if it does not soon. */
    private static void waitFor(Minecraft minecraft, Rover rover) {
        if (rover.isRemoved() || ++waitingTicks > SETTING_OFF) {
            waiting = null;
        } else if (rover.state() != Rover.State.STOWED) {
            begin(minecraft, rover);
        }
    }

    /** Whether the drive is over: the player hurt or gone, the remote out of hand, the rover gone or home a moment. */
    private static boolean lost(Minecraft minecraft, @Nullable LocalPlayer player, Driving drive) {
        Entity camera = minecraft.getCameraEntity();
        if (player == null || !player.isAlive() || player.hurtTime > 0 || drive.rover.isRemoved() || player.level() != drive.rover.level()
            || camera != player && camera != drive.lens || !RoverRemoteItem.inHand(player, drive.id)) {
            return true;
        }
        drive.homeTicks = drive.rover.state() == Rover.State.STOWED ? drive.homeTicks + 1 : 0;
        return drive.homeTicks > HOME_TICKS;
    }

    /** The keys read and sent as they change, and every so often besides; the horn heard here as it is pressed. */
    private static void steer(Options options, Driving drive) {
        float forward = (options.keyUp.isDown() ? 1 : 0) - (options.keyDown.isDown() ? 1 : 0);
        float turn = (options.keyRight.isDown() ? 1 : 0) - (options.keyLeft.isDown() ? 1 : 0);
        boolean honk = options.keyJump.isDown();
        if (honk && !drive.honk && drive.rover.state() == Rover.State.OUT && drive.rover.level() instanceof ClientLevel level) {
            heard(level, drive, AerolargeRegistry.ROVER_HORN.get());
        }
        boolean changed = forward != drive.forward || turn != drive.turn || honk != drive.honk;
        if (changed || ++drive.sinceSent >= KEEPALIVE) {
            PacketDistributor.sendToServer(new DriveRover(drive.rover.getId(), forward, turn, honk));
            drive.sinceSent = 0;
        }
        drive.forward = forward;
        drive.turn = turn;
        drive.honk = honk;
    }

    /**
     * How far the rover is from her and which way she is, the warning tone near the edge of its range; and whether a
     * monster is near the driver's body, looked at every so often, with a low note as one comes.
     */
    private static void watch(Minecraft minecraft, LocalPlayer player, Driving drive) {
        Rover rover = drive.rover;
        Vec3 at = RoverRenderer.drawn(rover.level(), rover.position(), 1);
        Vec3 her = RoverRenderer.drawn(rover.level(), RoverRoute.hinge(rover.bay()), 1);
        drive.fromHer = Math.hypot(her.x - at.x, her.z - at.z);
        drive.toHer = (float) Math.toDegrees(Math.atan2(-(her.x - at.x), her.z - at.z));
        double edge = drive.fromHer - (Rover.RANGE - WARNING);
        if (edge > 0 && drive.ticks % TONE_TICKS == 0) {
            float nearing = (float) Mth.clamp(edge / WARNING, 0, 1);
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AerolargeRegistry.ROVER_RANGE.get(), 1 + nearing * TONE_RISE, TONE_VOLUME));
        }
        if (drive.ticks % DANGER_LOOK == 0) {
            boolean danger = !player.level().getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(DANGER_REACH)).isEmpty();
            if (danger && !drive.danger) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AerolargeRegistry.ROVER_RANGE.get(), DANGER_PITCH));
            }
            drive.danger = danger;
        }
    }

    /** A sound of the rover's heard here, at the rover: the driver's ears are with the camera. */
    private static void heard(ClientLevel level, Driving drive, SoundEvent sound) {
        Vec3 at = RoverRenderer.drawn(level, drive.rover.position(), 1);
        level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.NEUTRAL, 1.0F, 1.0F, false);
    }

    /** Before each frame is drawn, the lens goes behind and over the rover as it is drawn now. */
    public static void frame(RenderFrameEvent.Pre event) {
        Driving drive = driving;
        if (drive != null) {
            place(drive, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }
    }

    /**
     * Puts the lens behind and over the rover, looking at it, as far round it as the camera has swung after its heading
     * and the mouse has swung it, and no further out than the blocks round it let it.
     */
    private static void place(Driving drive, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Rover rover = drive.rover;
        Vec3 target = RoverRenderer.drawn(rover.level(), rover.getPosition(partialTick), partialTick).add(0, LOOK_AT, 0);
        float swung = player == null ? 0 : player.getViewYRot(partialTick) - drive.yRot;
        float tilted = player == null ? 0 : player.getViewXRot(partialTick) - drive.xRot;
        float yaw = Mth.rotLerp(partialTick, drive.lastHeading, drive.heading) + swung;
        float pitch = Mth.clamp(CAMERA_PITCH + tilted, LOWEST_PITCH, HIGHEST_PITCH);
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 camera = target.subtract(look.scale(CAMERA_BACK));
        HitResult blocked = rover.level().clip(new ClipContext(target, camera, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (blocked.getType() != HitResult.Type.MISS) {
            camera = blocked.getLocation().add(look.scale(CLEAR_OF_BLOCKS));
        }
        Lens lens = drive.lens;
        lens.setPos(camera);
        lens.xo = camera.x;
        lens.yo = camera.y;
        lens.zo = camera.z;
        lens.setYRot(yaw);
        lens.yRotO = yaw;
        lens.setXRot(pitch);
        lens.xRotO = pitch;
    }

    /** Clicks do nothing in the world while driving. */
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        if (driving != null) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    /**
     * The player does not walk or jump while they drive: the keys steer the rover instead. Sneaking leaves, and never
     * reaches the player while they drive or until they let go of it afterwards.
     */
    public static void movement(MovementInputUpdateEvent event) {
        Input input = event.getInput();
        if (driving != null) {
            input.leftImpulse = 0;
            input.forwardImpulse = 0;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
        }
        if (!input.shiftKeyDown) {
            holdingSneak = false;
            return;
        }
        if (driving != null && !holdingSneak) {
            holdingSneak = true;
            leave(Minecraft.getInstance());
        }
        if (holdingSneak) {
            input.shiftKeyDown = false;
        }
    }

    public static void hand(RenderHandEvent event) {
        if (driving != null) {
            event.setCanceled(true);
        }
    }

    public static void highlight(RenderHighlightEvent.Block event) {
        if (driving != null) {
            event.setCanceled(true);
        }
    }

    public static void guiLayer(RenderGuiLayerEvent.Pre event) {
        if (driving != null && event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            event.setCanceled(true);
        }
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        leave(Minecraft.getInstance());
        waiting = null;
        holdingSneak = false;
    }

    /** Over the rover camera's picture, unless the GUI is hidden. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Driving drive = driving;
        Minecraft minecraft = Minecraft.getInstance();
        if (drive == null || minecraft.options.hideGui) {
            return;
        }
        boolean blink = drive.ticks / BLINK_TICKS % 2 == 0;
        label(graphics, minecraft.font, blink);
        bars(graphics, drive);
        if (drive.fromHer > Rover.RANGE - WARNING && blink) {
            arrow(graphics, drive, minecraft.player == null ? 0 : minecraft.player.getViewYRot(deltaTracker.getGameTimeDeltaPartialTick(true)));
        }
        if (drive.danger && blink) {
            triangle(graphics);
        }
        Options options = minecraft.options;
        Component hint = Component.translatable("screen.aerolarge.rover.hint", options.keyUp.getTranslatedKeyMessage(), options.keyLeft.getTranslatedKeyMessage(),
            options.keyDown.getTranslatedKeyMessage(), options.keyRight.getTranslatedKeyMessage(), options.keyJump.getTranslatedKeyMessage(),
            options.keyShift.getTranslatedKeyMessage());
        graphics.drawCenteredString(minecraft.font, hint, graphics.guiWidth() / 2, graphics.guiHeight() - HINT_Y, HINT);
    }

    /** The camera's name in the top left corner behind a blinking green light: a live picture. */
    private static void label(GuiGraphics graphics, Font font, boolean lit) {
        Component name = Component.translatable("screen.aerolarge.rover.camera");
        int textX = MARGIN + LIGHT + BACKING_MARGIN;
        graphics.fill(MARGIN - BACKING_MARGIN, MARGIN - BACKING_MARGIN, textX + font.width(name) + BACKING_MARGIN, MARGIN + font.lineHeight + BACKING_MARGIN,
            BACKING);
        if (lit) {
            int lightY = MARGIN + (font.lineHeight - LIGHT) / 2;
            graphics.fill(MARGIN, lightY, MARGIN + LIGHT, lightY + LIGHT, LIVE);
        }
        graphics.drawString(font, name, textX, MARGIN, WHITE, false);
    }

    /** The signal bars in the top right corner, fewer lit the further the rover is from her, red at the last. */
    private static void bars(GuiGraphics graphics, Driving drive) {
        int lit = (int) Math.ceil(BARS * Mth.clamp(1 - drive.fromHer / Rover.RANGE, 0, 1));
        int colour = lit <= 1 ? RED : lit == 2 ? AMBER : LIVE;
        int right = graphics.guiWidth() - MARGIN;
        int left = right - BARS * (BAR_WIDTH + BAR_GAP) + BAR_GAP;
        graphics.fill(left - BACKING_MARGIN, MARGIN - BACKING_MARGIN, right + BACKING_MARGIN, MARGIN + BAR_TALLEST + BACKING_MARGIN, BACKING);
        for (int bar = 0; bar < BARS; bar++) {
            int x = left + bar * (BAR_WIDTH + BAR_GAP);
            int height = BAR_TALLEST * (bar + 1) / BARS;
            graphics.fill(x, MARGIN + BAR_TALLEST - height, x + BAR_WIDTH, MARGIN + BAR_TALLEST, bar < lit ? colour : BAR_EMPTY);
        }
    }

    /** A big amber arrow at the top of the picture pointing back to her, as the camera looks. */
    private static void arrow(GuiGraphics graphics, Driving drive, float playerYaw) {
        float cameraYaw = drive.heading + playerYaw - drive.yRot;
        float turn = Mth.wrapDegrees(drive.toHer - cameraYaw);
        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2F, ARROW_Y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(turn));
        graphics.fill(-ARROW_SHAFT, 0, ARROW_SHAFT, ARROW_LENGTH / 2, AMBER);
        for (int row = 0; row < ARROW_HEAD; row++) {
            int half = ARROW_HEAD - row;
            graphics.fill(-half, -row, half, -row + 1, AMBER);
        }
        graphics.pose().popPose();
    }

    /** A red warning triangle with a white mark in it, at the picture's left: something is near the driver's body. */
    private static void triangle(GuiGraphics graphics) {
        for (int row = 0; row < TRIANGLE; row++) {
            int half = row / 2 + 1;
            int x = TRIANGLE_X + TRIANGLE / 2;
            graphics.fill(x - half, TRIANGLE_Y + row, x + half, TRIANGLE_Y + row + 1, RED);
        }
        int middle = TRIANGLE_X + TRIANGLE / 2;
        int base = TRIANGLE_Y + TRIANGLE;
        graphics.fill(middle - 1, TRIANGLE_Y + TRIANGLE / 3, middle + 1, base - STROKE_FOOT, WHITE);
        graphics.fill(middle - 1, base - DOT_TOP, middle + 1, base - DOT_BOTTOM, WHITE);
    }
}
