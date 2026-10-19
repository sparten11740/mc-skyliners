package dev.aerolarge.client;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.airframe.OutsideCamera;
import dev.aerolarge.block.PassengerScreenBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Looking through an assembled airliner's outside cameras from a passenger screen, the way a security camera is
 * watched: using the screen moves the player's view to the first camera (see {@link OutsideCamera}), using it again
 * steps on to the next and, after the last, back to the cabin; sneaking goes straight back. The view rides with the
 * airliner, placed afresh every frame as the airliner is drawn. The player stays where they are, seated or standing,
 * and nothing they do reaches the world while they look: a click only steps the camera on, and the sneak that leaves
 * the view does not also get them up from their seat. The hand, the crosshair and the hotbar give way to the camera's
 * name. The view goes back to the player as soon as the airliner, the screen or the player is gone or out of reach.
 * Only ever for the local player, on the client.
 */
public final class PassengerCameras {
    /** Further than this from the screen, in blocks, the player has left the airliner or been left behind. */
    private static final double REACH = 32;
    /** The pitch of the click as the view changes. */
    private static final float CLICK_PITCH = 1.6F;
    /** The camera's name in the top corner: where it starts, in GUI pixels, the margin of its dark backing around it. */
    private static final int LABEL_X = 10;
    private static final int LABEL_Y = 10;
    private static final int BACKING_MARGIN = 4;
    /** The recording light before the name: its size and the gap after it, in GUI pixels, and ticks it is lit and dark. */
    private static final int LIGHT = 5;
    private static final int LIGHT_GAP = 5;
    private static final int BLINK_TICKS = 10;
    /** How far up from the bottom of the screen the hint about the keys sits, in GUI pixels. */
    private static final int HINT_Y = 40;

    private static final int BACKING = 0x90000000;
    private static final int RECORDING = 0xFFE03030;
    private static final int WHITE = 0xFFF2F6FB;
    private static final int HINT = 0xFFB8C4D0;

    /** The view through a camera, while there is one. */
    @Nullable
    private static Viewing viewing;
    /** Whether the sneak key is kept from the player until it is let go, so leaving a view does not also stand them up. */
    private static boolean holdingSneak;

    private PassengerCameras() {
    }

    /** What is being looked through, from which screen, and what to give back afterwards. */
    private static final class Viewing {
        final PassengerScreenBlockEntity screen;
        final SableClient.Frame frame;
        /** The airframe's blocks per metre. */
        final double scale;
        final Lens lens;
        /** The player's own camera type and the way they looked, given back when they leave the view. */
        final CameraType cameraType;
        final float yRot;
        final float xRot;
        int camera;
        /** Where the camera is among the airliner's blocks and the ways it faces in the airliner's frame. */
        Vec3 point = Vec3.ZERO;
        Vec3 forward = Vec3.ZERO;
        Vec3 up = Vec3.ZERO;
        /** How far the view leans with the airliner this frame, in degrees. */
        float roll;
        /** Whether the use key is still down from the press that got here, which must not step on again. */
        boolean useHeld = true;

        Viewing(PassengerScreenBlockEntity screen, SableClient.Frame frame, double scale, Lens lens, CameraType cameraType, float yRot, float xRot) {
            this.screen = screen;
            this.frame = frame;
            this.scale = scale;
            this.lens = lens;
            this.cameraType = cameraType;
            this.yRot = yRot;
            this.xRot = xRot;
        }

        OutsideCamera camera() {
            return OutsideCamera.values()[camera];
        }

        /** Sets the camera up among the airliner's blocks: its place and the ways it faces, held level in its frame. */
        void mount(int camera) {
            this.camera = camera;
            OutsideCamera mount = camera();
            Vec3 nose = Vec3.atLowerCornerOf(frame.nose().getNormal());
            Vec3 starboard = Vec3.atLowerCornerOf(frame.nose().getClockWise().getNormal());
            Vec3 above = new Vec3(0, 1, 0);
            point = frame.origin().add(starboard.scale(mount.across * scale)).add(above.scale(mount.up * scale)).add(nose.scale(-mount.back * scale));
            double yaw = Math.toRadians(mount.yaw);
            double pitch = Math.toRadians(mount.pitch);
            Vec3 level = nose.scale(Math.cos(yaw)).add(starboard.scale(Math.sin(yaw)));
            forward = level.scale(Math.cos(pitch)).add(above.scale(Math.sin(pitch)));
            up = level.scale(-Math.sin(pitch)).add(above.scale(Math.cos(pitch)));
        }
    }

    /**
     * What the view is drawn from: an entity of no size that is never added to the world, its eyes as high as the
     * player's, so the view does not slide while the camera eases from the one's eye height to the other's.
     */
    private static final class Lens extends Marker {
        private final float eyes;

        Lens(Level level, float eyes) {
            super(EntityType.MARKER, level);
            this.eyes = eyes;
            refreshDimensions();
        }

        @Override
        public EntityDimensions getDimensions(Pose pose) {
            return EntityDimensions.fixed(0, 0).withEyeHeight(eyes);
        }
    }

    /** The local player has used a passenger screen: look through the first camera, if the airliner has them. */
    public static void use(PassengerScreenBlockEntity screen) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || viewing != null) {
            return;
        }
        SableClient.Frame frame = SableClient.frame(screen);
        if (frame == null) {
            boolean assembled = SableClient.ride(screen) != null;
            player.displayClientMessage(Component.translatable(assembled ? "message.aerolarge.passenger_screen.no_cameras"
                : "message.aerolarge.passenger_screen.not_assembled"), true);
            return;
        }
        // The server's settings, the airliner's scale among them, reach the client as it joins.
        Viewing view = new Viewing(screen, frame, AerolargeConfig.SCALE.get(), new Lens(player.level(), player.getEyeHeight()),
            minecraft.options.getCameraType(), player.getYRot(), player.getXRot());
        view.mount(0);
        if (!place(view, minecraft.getTimer().getGameTimeDeltaPartialTick(true))) {
            return;
        }
        viewing = view;
        holdingSneak = true;
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(view.lens);
        click(minecraft);
    }

    /** On to the next camera, or back to the cabin after the last. */
    private static void next(Minecraft minecraft, Viewing view) {
        click(minecraft);
        if (view.camera + 1 < OutsideCamera.values().length) {
            view.mount(view.camera + 1);
        } else {
            leave(minecraft);
        }
    }

    /** Gives the player their own view back, as it was; unless something else has taken the view over meanwhile. */
    private static void leave(Minecraft minecraft) {
        Viewing view = viewing;
        if (view == null) {
            return;
        }
        viewing = null;
        minecraft.options.setCameraType(view.cameraType);
        LocalPlayer player = minecraft.player;
        if (player != null && minecraft.getCameraEntity() == view.lens) {
            minecraft.setCameraEntity(player);
            player.setYRot(view.yRot);
            player.setXRot(view.xRot);
            player.yRotO = view.yRot;
            player.xRotO = view.xRot;
        }
    }

    private static void click(Minecraft minecraft) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, CLICK_PITCH));
    }

    /**
     * Puts the lens where the camera is as the airliner is drawn at this partial tick, facing as it does and leaning
     * with it; false once no airliner holds the camera.
     */
    private static boolean place(Viewing view, float partialTick) {
        SableClient.View seen = SableClient.view(view.point, view.forward, view.up, partialTick);
        if (seen == null) {
            return false;
        }
        Vec3 forward = seen.forward();
        float yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        float pitch = (float) Math.toDegrees(-Math.asin(Mth.clamp(forward.y, -1, 1)));
        // Up and right for a camera looking that way held level, as Minecraft turns its camera; the camera's own up
        // leans from the one toward the other by its roll.
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        Vec3 right = new Vec3(-Math.cos(yawRadians), 0, -Math.sin(yawRadians));
        Vec3 level = new Vec3(-Math.sin(pitchRadians) * Math.sin(yawRadians), Math.cos(pitchRadians), Math.sin(pitchRadians) * Math.cos(yawRadians));
        view.roll = (float) Math.toDegrees(Math.atan2(seen.up().dot(right), seen.up().dot(level)));

        Lens lens = view.lens;
        Vec3 feet = seen.eye().subtract(0, lens.getEyeHeight(), 0);
        lens.setPos(feet);
        lens.xo = feet.x;
        lens.yo = feet.y;
        lens.zo = feet.z;
        lens.setYRot(yaw);
        lens.yRotO = yaw;
        lens.setXRot(pitch);
        lens.xRotO = pitch;
        return true;
    }

    /** Each tick: leaves the view once anything it needs is gone, and keeps the camera in first person. */
    public static void tick(ClientTickEvent.Post event) {
        Viewing view = viewing;
        if (view == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean lost = player == null || !player.isAlive() || player.level() != view.lens.level() || view.screen.isRemoved()
            || minecraft.getCameraEntity() != view.lens || !withinReach(player, view.screen);
        if (lost) {
            leave(minecraft);
            return;
        }
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        if (!minecraft.options.keyUse.isDown()) {
            view.useHeld = false;
        }
    }

    /**
     * Whether the player is still near the screen, both as seen in the world: either may have its place among the
     * airliner's blocks rather than in the world.
     */
    private static boolean withinReach(LocalPlayer player, PassengerScreenBlockEntity screen) {
        Level level = player.level();
        Vec3 here = SableClient.toWorld(level, player.position());
        return here.distanceToSqr(SableClient.toWorld(level, screen.getBlockPos().getCenter())) <= REACH * REACH;
    }

    /** Before each frame is drawn, the lens goes where the camera is now. */
    public static void frame(RenderFrameEvent.Pre event) {
        Viewing view = viewing;
        if (view != null && !place(view, event.getPartialTick().getGameTimeDeltaPartialTick(true))) {
            leave(Minecraft.getInstance());
        }
    }

    /** The view leans with the airliner. */
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        Viewing view = viewing;
        if (view != null && event.getCamera().getEntity() == view.lens) {
            event.setRoll(event.getRoll() + view.roll);
        }
    }

    /** Each camera sees through its own lens. */
    public static void fov(ViewportEvent.ComputeFov event) {
        Viewing view = viewing;
        if (view != null && event.usedConfiguredFov() && event.getCamera().getEntity() == view.lens) {
            event.setFOV(view.camera().fov);
        }
    }

    /** Clicks do nothing in the world while looking through a camera; using steps on to the next one. */
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        Viewing view = viewing;
        if (view == null) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isUseItem() && !view.useHeld) {
            view.useHeld = true;
            next(Minecraft.getInstance(), view);
        }
    }

    /**
     * Sneaking leaves the view, and never reaches the player while they look or until they let go of it afterwards:
     * seated, it would get them up.
     */
    public static void movement(MovementInputUpdateEvent event) {
        Input input = event.getInput();
        if (!input.shiftKeyDown) {
            holdingSneak = false;
            return;
        }
        if (viewing != null && !holdingSneak) {
            holdingSneak = true;
            leave(Minecraft.getInstance());
        }
        if (holdingSneak) {
            input.shiftKeyDown = false;
        }
    }

    public static void hand(RenderHandEvent event) {
        if (viewing != null) {
            event.setCanceled(true);
        }
    }

    public static void highlight(RenderHighlightEvent.Block event) {
        if (viewing != null) {
            event.setCanceled(true);
        }
    }

    public static void guiLayer(RenderGuiLayerEvent.Pre event) {
        if (viewing != null && event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            event.setCanceled(true);
        }
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        leave(Minecraft.getInstance());
        holdingSneak = false;
    }

    /**
     * Over the camera's picture, unless the GUI is hidden: its number and name in the top corner behind a blinking
     * recording light, and which keys step on and go back along the bottom.
     */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Viewing view = viewing;
        Minecraft minecraft = Minecraft.getInstance();
        if (view == null || minecraft.options.hideGui) {
            return;
        }
        Font font = minecraft.font;
        Component name = Component.translatable("screen.aerolarge.passenger_screen.camera", view.camera + 1, Component.translatable(view.camera().label()));
        int textX = LABEL_X + LIGHT + LIGHT_GAP;
        graphics.fill(LABEL_X - BACKING_MARGIN, LABEL_Y - BACKING_MARGIN, textX + font.width(name) + BACKING_MARGIN, LABEL_Y + font.lineHeight + BACKING_MARGIN,
            BACKING);
        if (minecraft.level == null || minecraft.level.getGameTime() / BLINK_TICKS % 2 == 0) {
            int lightY = LABEL_Y + (font.lineHeight - LIGHT) / 2;
            graphics.fill(LABEL_X, lightY, LABEL_X + LIGHT, lightY + LIGHT, RECORDING);
        }
        graphics.drawString(font, name, textX, LABEL_Y, WHITE, false);
        Component hint = Component.translatable("screen.aerolarge.passenger_screen.camera_hint",
            minecraft.options.keyUse.getTranslatedKeyMessage(), minecraft.options.keyShift.getTranslatedKeyMessage());
        graphics.drawCenteredString(font, hint, graphics.guiWidth() / 2, graphics.guiHeight() - HINT_Y, HINT);
    }
}
