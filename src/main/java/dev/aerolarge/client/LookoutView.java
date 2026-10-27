package dev.aerolarge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.LookoutGlassBlockEntity;
import dev.aerolarge.lookout.LookoutSpot;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Looking through the Lookout's Glass (see {@link LookoutGlassBlockEntity}), on this client: the view narrowed to a
 * tenth inside the spyglass's frame, as a spyglass narrows it, the land far below brought near. At its middle a green
 * pennant on its pole stands on the land it would mark, or a red cross shows there is none to mark there (sky, the
 * ship's own hull in the way, or land too far off or not yet loaded). Using marks it: the flag is dropped there for the
 * autopilot's map, a column of white smoke rises from the spot to show where, and the ship's bell answers; with nothing
 * to mark, the glass only clicks. Stepping, jumping or sneaking steps back from it, as does anything that takes the
 * player away: a screen opening, the glass gone, or the ship carrying them off.
 */
public final class LookoutView {
    /** How much of the view the glass leaves, as a spyglass does. */
    private static final float ZOOM = 0.1F;
    /**
     * How far the player's eye may be from the glass while they look through it, in blocks: a little further than a
     * block can be used from, so the view opens wherever the glass was used from.
     */
    private static final double REACH = 5;
    /**
     * How the game turns the view by the mouse, by the cube of its sensitivity times a share and a little more; and
     * how much less that root is made looking through the glass, so the view turns eight times slower, as a spyglass's
     * does, its ten times nearer land not swinging past.
     */
    private static final double TURN_SHARE = 0.6;
    private static final double TURN_LEAST = 0.2;
    private static final double SLOWER = 2;
    /**
     * How far apart the ground is sought along the view, in blocks, close enough not to step over the hull's cover;
     * and how far along it an airliner's blocks are looked for in the way, in blocks: the ship's own hull and any
     * other ship near.
     */
    private static final double MARCH_STEP = 0.5;
    private static final double SHIPS_REACH = 128;
    /**
     * How many puffs of smoke rise from a spot as it is marked, how far apart up the column and how widely about it
     * they start, in blocks, and how fast they rise, in blocks a tick.
     */
    private static final int SMOKE_PUFFS = 6;
    private static final double SMOKE_STEP = 0.6;
    private static final double SMOKE_SPREAD = 0.4;
    private static final double SMOKE_RISE = 0.08;
    /** The glass's click with nothing to mark: how loud, and how low. */
    private static final float NOTHING_VOLUME = 0.6F;
    private static final float NOTHING_PITCH = 0.8F;
    /** How much bigger than the screen's short side the spyglass's frame is drawn, as at its widest. */
    private static final float FRAME_SCALE = 1.125F;
    private static final ResourceLocation FRAME = ResourceLocation.withDefaultNamespace("textures/misc/spyglass_scope.png");
    /**
     * The pennant at the middle: how tall its pole, how long and tall its cloth, and how deep the cut in its swallow
     * tail, in pixels.
     */
    private static final int POLE = 11;
    private static final int CLOTH_LONG = 7;
    private static final int CLOTH_TALL = 5;
    private static final int NOTCH = 2;
    /** How far the red cross reaches either way from the middle, in pixels. */
    private static final int CROSS = 4;
    private static final int GREEN = 0xFF4CD964;
    private static final int RED = 0xFFE5484D;
    private static final int SHADE = 0xC0000000;
    private static final int BLACK = 0xFF000000;
    /** How far down from the top the hint is drawn, clear of the hotbar and the hearts, in pixels, and its colour. */
    private static final int HINT_Y = 12;
    private static final int HINT = 0xFFE8E8E8;

    @Nullable
    private static LookoutGlassBlockEntity looking;
    /** The spot the middle of the view rests on now, if there is one to mark. */
    @Nullable
    private static Vec3 aim;
    /** Whether the use key is still held from using the glass, or from the last mark, so holding it marks only once. */
    private static boolean useHeld;
    /** The game time a spot was last marked, so another is not marked sooner than the glass takes one. */
    private static long markedAt = Long.MIN_VALUE;

    private LookoutView() {
    }

    /** Whether the player looks through this glass now, which is then not drawn for them. */
    static boolean lookingThrough(LookoutGlassBlockEntity glass) {
        return looking == glass;
    }

    /** The player has used a glass: they look through it. */
    public static void look(LookoutGlassBlockEntity glass) {
        Minecraft minecraft = Minecraft.getInstance();
        if (looking != null || minecraft.player == null || minecraft.level == null || !atGlass(minecraft.player, minecraft.level, glass)) {
            return;
        }
        looking = glass;
        useHeld = true;
        aim = null;
        minecraft.player.playSound(SoundEvents.SPYGLASS_USE, 1, 1);
    }

    private static void leave() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (looking != null && player != null) {
            player.playSound(SoundEvents.SPYGLASS_STOP_USING, 1, 1);
        }
        looking = null;
        aim = null;
    }

    /** Once a tick: what the view rests on, and stepping back once anything takes the player from the glass. */
    public static void tick(ClientTickEvent.Post event) {
        LookoutGlassBlockEntity glass = looking;
        if (glass == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !atGlass(player, level, glass) || minecraft.screen != null) {
            leave();
            return;
        }
        if (!minecraft.options.keyUse.isDown()) {
            useHeld = false;
        }
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.zeppelinFraming(level, glass.getBlockPos());
        BoundingBox own = autopilot != null ? autopilot.frame() : null;
        aim = ground(level, player.getEyePosition(), player.getLookAngle(), own);
    }

    /** Whether the player is still at the glass they look through: alive, beside it, and it still standing there. */
    private static boolean atGlass(LocalPlayer player, ClientLevel level, LookoutGlassBlockEntity glass) {
        if (!player.isAlive() || glass.isRemoved() || glass.getLevel() != level) {
            return false;
        }
        Vec3 at = SableClient.toWorld(level, glass.getBlockPos().getCenter());
        return at.distanceTo(player.getEyePosition()) <= REACH;
    }

    /**
     * Where a line from the eye first meets the top of the land, within the glass's range; null where it meets none
     * first, runs into a ship's blocks, its own hull among them, or reaches land not loaded. The land's heights are
     * the world's, which hold none of an assembled ship's blocks, far off in its plot: those are looked for on their
     * own. Before the glass's own zeppelin is assembled, her blocks stand in the world, inside her frame, and the
     * heights there are hers, not the land's: they are passed over, and the land under her can be marked from nowhere.
     */
    @Nullable
    private static Vec3 ground(ClientLevel level, Vec3 eye, Vec3 look, @Nullable BoundingBox own) {
        for (double along = 0; along <= LookoutSpot.RANGE; along += MARCH_STEP) {
            Vec3 point = eye.add(look.scale(along));
            if (along <= SHIPS_REACH && inShip(level, point) || own != null && under(own, point)) {
                return null;
            }
            if (own != null && own.isInside(BlockPos.containing(point))) {
                continue;
            }
            int x = Mth.floor(point.x);
            int z = Mth.floor(point.z);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                return null;
            }
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (point.y <= top) {
                return new Vec3(x + 0.5, top, z + 0.5);
            }
        }
        return null;
    }

    /** Whether a point is under a zeppelin standing in the world, within her frame's reach across but below it. */
    private static boolean under(BoundingBox frame, Vec3 point) {
        int x = Mth.floor(point.x);
        int z = Mth.floor(point.z);
        return point.y < frame.minY() && x >= frame.minX() && x <= frame.maxX() && z >= frame.minZ() && z <= frame.maxZ();
    }

    /**
     * Whether a point in the world is inside a block of an assembled ship that hides what is behind it: the hull's
     * cover does, its windows, its glass floor and the glass itself do not.
     */
    private static boolean inShip(ClientLevel level, Vec3 point) {
        for (SableClient.Around ship : SableClient.around(level, point, 1)) {
            if (inside(level, BlockPos.containing(ship.point()), ship.point())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a point among a ship's blocks is within the bounds of the block there, one that hides what is behind it.
     */
    private static boolean inside(ClientLevel level, BlockPos pos, Vec3 point) {
        BlockState state = level.getBlockState(pos);
        if (!state.canOcclude()) {
            return false;
        }
        VoxelShape shape = state.getShape(level, pos);
        return !shape.isEmpty() && shape.bounds().move(pos).contains(point);
    }

    /**
     * Marks the spot the view rests on, or clicks with none; nothing so soon after the last, as the glass takes none.
     */
    private static void mark(LocalPlayer player, ClientLevel level, LookoutGlassBlockEntity glass) {
        Vec3 spot = aim;
        long now = level.getGameTime();
        if (markedAt != Long.MIN_VALUE && now >= markedAt && now - markedAt < LookoutGlassBlockEntity.SPOT_COOLDOWN) {
            return;
        }
        if (spot == null) {
            player.playSound(SoundEvents.DISPENSER_FAIL, NOTHING_VOLUME, NOTHING_PITCH);
            return;
        }
        markedAt = now;
        PacketDistributor.sendToServer(new LookoutSpot(glass.getBlockPos(), Mth.floor(spot.x), Mth.floor(spot.z)));
        for (int puff = 0; puff < SMOKE_PUFFS; puff++) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, spot.x + level.random.nextGaussian() * SMOKE_SPREAD,
                spot.y + puff * SMOKE_STEP, spot.z + level.random.nextGaussian() * SMOKE_SPREAD, 0, SMOKE_RISE, 0);
        }
    }

    /** Looking through the glass, the view is narrowed to a tenth. */
    public static void fov(ViewportEvent.ComputeFov event) {
        if (looking != null && event.usedConfiguredFov()) {
            event.setFOV(event.getFOV() * ZOOM);
        }
    }

    /** Looking through the glass, the view turns eight times slower by the mouse. */
    public static void turn(CalculatePlayerTurnEvent event) {
        if (looking != null) {
            double root = event.getMouseSensitivity() * TURN_SHARE + TURN_LEAST;
            event.setMouseSensitivity(Math.max(0, (root / SLOWER - TURN_LEAST) / TURN_SHARE));
        }
    }

    /** Clicks do nothing in the world while looking through the glass; using marks the spot. */
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LookoutGlassBlockEntity glass = looking;
        if (glass == null) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isUseItem() && !useHeld && minecraft.player != null && minecraft.level != null) {
            useHeld = true;
            mark(minecraft.player, minecraft.level, glass);
        }
    }

    /** Stepping, jumping or sneaking steps back from the glass. */
    public static void movement(MovementInputUpdateEvent event) {
        Input input = event.getInput();
        if (looking != null && (input.forwardImpulse != 0 || input.leftImpulse != 0 || input.jumping || input.shiftKeyDown)) {
            leave();
        }
    }

    public static void hand(RenderHandEvent event) {
        if (looking != null) {
            event.setCanceled(true);
        }
    }

    public static void highlight(RenderHighlightEvent.Block event) {
        if (looking != null) {
            event.setCanceled(true);
        }
    }

    public static void guiLayer(RenderGuiLayerEvent.Pre event) {
        if (looking != null && event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            event.setCanceled(true);
        }
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        looking = null;
        aim = null;
    }

    /**
     * Over the view, unless the GUI is hidden: the spyglass's frame, dark beyond it; at its middle the pennant or the
     * cross; and along the top, which keys mark and step back.
     */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (looking == null || minecraft.options.hideGui) {
            return;
        }
        frame(graphics);
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2;
        if (aim != null) {
            pennant(graphics, x, y);
        } else {
            cross(graphics, x, y);
        }
        Font font = minecraft.font;
        Component hint = Component.translatable("screen.aerolarge.lookout.hint", minecraft.options.keyUse.getTranslatedKeyMessage(),
            minecraft.options.keyShift.getTranslatedKeyMessage());
        graphics.drawCenteredString(font, hint, x, HINT_Y, HINT);
    }

    /** The spyglass's frame over the middle of the screen, black round it. */
    private static void frame(GuiGraphics graphics) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int size = Mth.floor(Math.min(width, height) * FRAME_SCALE);
        int left = (width - size) / 2;
        int top = (height - size) / 2;
        RenderSystem.enableBlend();
        graphics.blit(FRAME, left, top, 0, 0, size, size, size, size);
        RenderSystem.disableBlend();
        graphics.fill(0, 0, width, top, BLACK);
        graphics.fill(0, top + size, width, height, BLACK);
        graphics.fill(0, top, left, top + size, BLACK);
        graphics.fill(left + size, top, width, top + size, BLACK);
    }

    /** A green pennant whose pole stands on the middle, the spot that would be marked. */
    private static void pennant(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - POLE - 1, x + 2, y + 1, SHADE);
        graphics.fill(x + 1, y - POLE - 1, x + 2 + CLOTH_LONG, y - POLE + CLOTH_TALL + 1, SHADE);
        graphics.fill(x, y - POLE, x + 1, y + 1, GREEN);
        for (int row = 0; row < CLOTH_TALL; row++) {
            int fly = x + 1 + CLOTH_LONG - (row == CLOTH_TALL / 2 ? NOTCH : 0);
            graphics.fill(x + 1, y - POLE + row, fly, y - POLE + row + 1, GREEN);
        }
    }

    /** A red cross on the middle: nothing there to mark. */
    private static void cross(GuiGraphics graphics, int x, int y) {
        for (int step = -CROSS; step <= CROSS; step++) {
            graphics.fill(x + step - 1, y + step - 1, x + step + 2, y + step + 2, SHADE);
            graphics.fill(x + step - 1, y - step - 1, x + step + 2, y - step + 2, SHADE);
        }
        for (int step = -CROSS; step <= CROSS; step++) {
            graphics.fill(x + step, y + step, x + step + 1, y + step + 1, RED);
            graphics.fill(x + step, y - step, x + step + 1, y - step + 1, RED);
        }
    }
}
