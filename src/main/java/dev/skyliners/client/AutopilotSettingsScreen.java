package dev.skyliners.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.AutopilotBlockEntity.Button;
import dev.skyliners.autopilot.AutopilotBlockEntity.CircleSize;
import dev.skyliners.autopilot.AutopilotBlockEntity.Lateral;
import dev.skyliners.autopilot.AutopilotBlockEntity.Phase;
import dev.skyliners.autopilot.AutopilotBlockEntity.Settings;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.EdgeOfSpace;
import dev.skyliners.autopilot.OpenAutopilotScreen;
import dev.skyliners.autopilot.SetAutopilot;
import dev.skyliners.autopilot.UnstickAirliner;
import dev.skyliners.block.CockpitKeys;
import dev.skyliners.block.CockpitKeys.Dial;
import dev.skyliners.client.PanelArt.CloseButton;
import dev.skyliners.client.PanelArt.Icon;
import dev.skyliners.client.PanelArt.IconButton;
import dev.skyliners.client.PanelArt.Look;
import dev.skyliners.client.PanelArt.Pictures;
import dev.skyliners.client.PanelArt.ValueBox;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The autopilot's settings, opened by using the autopilot or its screen on the flight deck: a panel in the flight
 * deck's glass-cockpit colours (see {@link PanelArt}) shaped as the flight deck's own controls are (see
 * {@link AutopilotDeskArt}), so that a player who cannot read yet can fly too. In the title bar, what the autopilot is
 * doing, with a lamp: green while engaged, amber while off, dark until the airliner is assembled. Under it, on glass,
 * how the airliner flies now, or what engaging does. Then the controls, in the order the pilots' flight displays
 * read: a speedometer, its needle at the speed now, set by clicking or dragging round it; the steering, a fan seen
 * from above with the airliner nose up, clicked where to fly, over the turns, like road signs, tight and gentle either
 * way round a circle and straight on, the one chosen lit; and the sky from the side, clicked or dragged to set the
 * height the airliner flies at, faintly where it flies now. Pointed at, each shows a ghost of what a click there would
 * set. Each also has a box to type its number into. Under the steering, the pin: chosen, the fan and the turns give
 * way to a map to drop the pin on (see {@link DestinationMap}), with how tightly to circle it and whether to land at
 * it beside; the height and speed stay. Along the bottom the big buttons: switched off, engaging flies the settings
 * (taking off first on the ground); engaged, they can be applied, or the autopilot switched off. In the air it can
 * also be told to land; stuck on the ground with its engines running, the airliner can be hopped free in that button's
 * place instead, for as long as it is held. The server checks it all again (see {@link SetAutopilot} and
 * {@link UnstickAirliner}). The game goes on behind it, and it closes only with the close button in its title bar (or
 * once the autopilot is gone), so it can stay open to watch the autopilot fly.
 */
public class AutopilotSettingsScreen extends Screen {
    /** The panel's size, the space inside its bezel, and the width of what is on its face. */
    private static final int WIDTH = 360;
    private static final int HEIGHT = 232;
    private static final int PADDING = 8;
    private static final int CONTENT = WIDTH - 2 * (PanelArt.BORDER + PADDING);
    /** From the panel's top: the glass with its line, the controls, and the big buttons. */
    private static final int GLASS_Y = 34;
    private static final int GLASS_HEIGHT = 14;
    private static final int LINE_Y = GLASS_Y + 3;
    private static final int CONTROLS_Y = 54;
    private static final int ACTIONS_Y = 182;
    /** Inside the glass: the space before its line, and how far along it its values start. */
    private static final int GLASS_INSET = 4;
    private static final int VALUE_X = 50;
    /** The controls: the speedometer's width and the sky's, the gap between columns, and the height of both. */
    private static final int SPEEDOMETER_WIDTH = 84;
    private static final int SKY_WIDTH = 72;
    private static final int COLUMN_GAP = 8;
    private static final int GAUGE_HEIGHT = 88;
    /**
     * The steering: the fan's height, the row of turns under it, how far down it starts, how tall it is and the gap
     * between turns; the strip under them, for the pin, how far down it starts and how tall it is.
     */
    private static final int FAN_HEIGHT = 64;
    private static final int TURNS_Y = 68;
    private static final int TURN_HEIGHT = 22;
    private static final int TURN_GAP = 2;
    private static final int STRIP_Y = 94;
    private static final int STRIP_HEIGHT = 18;
    private static final int STRIP_TILE = 26;
    /** The boxes the numbers are typed into: their width and height, and the gap over them. */
    private static final int BOX_WIDTH = 44;
    private static final int BOX_HEIGHT = 16;
    private static final int BOX_GAP = 4;
    /** The big buttons along the bottom: their height and the space between them. */
    private static final int ACTION_HEIGHT = 36;
    private static final int ACTION_GAP = 6;
    /** The close button's distance in from the corner of the title bar, the state's from it and the lamp's from that. */
    private static final int CLOSE_INSET = 3;
    private static final int STATE_GAP = 6;
    private static final int LAMP_GAP = 7;
    /** The lamp: out until the airliner is assembled; engaged, pulsing between this brightness and full, this slowly (ms). */
    private static final float LAMP_OUT = 0.35F;
    private static final float LAMP_LOW = 0.7F;
    private static final float LAMP_PERIOD = 2000;
    /** What can be typed into a setting: a whole number of up to four digits, perhaps below zero. */
    private static final Pattern NUMBER = Pattern.compile("-?\\d{0,4}");
    private static final int MAX_LENGTH = 5;
    /** What is typed: light, or red while it cannot be read or is out of range (it is brought into range when sent). */
    private static final int TYPED = 0xFFE0E0E0;
    private static final int UNREADABLE = 0xFFFF5555;
    /** The highest compass bearing; bearings go round past it. */
    private static final int LAST_BEARING = 359;
    /**
     * The speedometer in its control, in pixels: its middle, how far down, and its radius; how thick its band is; how
     * long its ticks are, the numbered ones and the rest; how far in from the band its numbers are; and the bug's size.
     * Its scale is the flight deck's (see {@link AutopilotDeskArt}).
     */
    private static final int DIAL_MIDDLE = 46;
    private static final int DIAL_RADIUS = 34;
    private static final int BAND = 5;
    private static final int LONG_TICK = 6;
    private static final int SHORT_TICK = 3;
    private static final int SPEED_NUMBERS_IN = 13;
    private static final int BUG = 5;
    /** The round steps the speed dial's marks are spaced in, for whatever speeds it shows, in blocks a second. */
    private static final int[] NICE_STEPS = {1, 2, 5, 10, 20, 30, 50, 100};
    /**
     * The speedometer's bug: how far apart its strokes fan, in radians for each pixel out from the band; and the room
     * under its number at the dial's foot, in pixels.
     */
    private static final double BUG_SPREAD = 0.035;
    private static final int SPEED_NUMBER_UP = 12;
    /**
     * The steering fan, in pixels: its radius; its ticks' lengths, the long ones and the rest; north's mark, how far in
     * from the rim it starts and ends; the bug's radius; and how far up from the fan's foot the airliner's nose is.
     */
    private static final int FAN_RADIUS = 60;
    private static final int FAN_LONG = 8;
    private static final int FAN_SHORT = 4;
    private static final int NORTH_FROM = 14;
    private static final int NORTH_TO = 5;
    private static final int FAN_BUG = 4;
    private static final int NOSE = 16;
    /** The path from the nose to the bug: how far up the fan it bends, as a share of its radius, and its pieces. */
    private static final double PATH_BEND = 0.7;
    private static final int PATH_PIECES = 16;
    /**
     * A rocket's way up and back down where she rose, drawn on the fan in pixels: how far either side of her its two
     * legs run, how far over the fan's foot it turns at the top, its arrowheads' size, and the turn's pieces.
     */
    private static final int TRIP_HALF_WIDTH = 13;
    private static final int TRIP_TOP = 40;
    private static final int TRIP_ARROW = 4;
    private static final int TRIP_PIECES = 12;
    /** Her launch pad under her at the fan's foot, in pixels: how far it reaches either side of her, and how thick. */
    private static final int PAD_HALF_WIDTH = 18;
    private static final int PAD_HEIGHT = 2;
    /**
     * The sky in its control, in pixels: its frame, the ground along its bottom, the room above and below its scale,
     * where across it its airliners fly, and how far from the height set the airliner must be for the climb or
     * descent to it to be drawn.
     */
    private static final int SKY_FRAME = 2;
    private static final int GROUND = 8;
    private static final int SKY_ROOM = 6;
    private static final int SKY_AIRLINER_X = 8;
    private static final int CLIMB_DRAWN = 6;
    /**
     * The sky's ticks up its side, the numbered ones and the rest, and the gap between a number and its tick, in
     * pixels.
     */
    private static final int HEIGHT_LONG = 5;
    private static final int HEIGHT_SHORT = 3;
    private static final int HEIGHT_NUMBER_GAP = 7;
    /**
     * How far apart the sky's numbers are at the least, in pixels, and the multiples of the flight deck's step between
     * them it may take instead, the least that keeps them that far apart: a rocket's sky, reaching up into space, is
     * numbered more sparsely than an airliner's, its ticks as many to a number.
     */
    private static final int NUMBERS_APART = PanelArt.GLYPH_HEIGHT + 3;
    private static final int[] NUMBERS_SPREAD = {1, 2, 5, 10, 20};
    /**
     * Space over a rocket's sky: as near black as the sky goes up there, its stars, bright and faint, and the thin blue
     * glow of the air along its foot, as the planet's rim shows it seen from space, and how far that glow spreads down
     * into the sky, in pixels.
     */
    private static final int SPACE = 0xFF020306;
    private static final int STAR = 0xFFF2F2F2;
    private static final int FAINT_STAR = 0xFF7A86A3;
    private static final int SPACE_GLOW = 0xFF6FB4FF;
    private static final int SPACE_GLOW_FADING = 0x806FB4FF;
    private static final int SPACE_GLOW_DEPTH = 3;
    /** Where space's stars are, as shares of its width and its height; every other one is faint. */
    private static final float[][] STARS = {{0.08F, 0.55F}, {0.21F, 0.18F}, {0.33F, 0.78F}, {0.42F, 0.36F}, {0.51F, 0.9F},
        {0.6F, 0.12F}, {0.69F, 0.62F}, {0.8F, 0.3F}, {0.88F, 0.82F}, {0.36F, 0.04F}};
    /** How bright the airliner where a click in the sky would put it is, as a share of the one set. */
    private static final float SKY_GHOST = 0.55F;
    /** How loud the notes and the whoosh of setting the autopilot here are, as a share of full. */
    private static final float SET_VOLUME = 0.4F;

    private static final int GLASS = 0xFF05080D;
    private static final int PICTURE_FAINT = 0xFF6A7380;

    private static final Icon APPLY = Icon.of("autopilot/apply");
    private static final Icon DISENGAGE = Icon.of("autopilot/disengage");
    private static final Icon UNSTUCK = Icon.of("autopilot/unstuck");
    private static final Icon PIN = Icon.of("autopilot/destination");

    /**
     * The ways it can fly, along the bottom of the steering, like road signs, hardest left out to hardest right: the
     * flight deck's turns (see {@link AutopilotBlockEntity.Button}), each with its picture of what it flies.
     */
    private enum Turn {
        TIGHT_LEFT(Button.TIGHT_LEFT, Pictures::tightLeft),
        GENTLE_LEFT(Button.GENTLE_LEFT, Pictures::gentleLeft),
        STRAIGHT(Button.STRAIGHT, Pictures::straightOn),
        GENTLE_RIGHT(Button.GENTLE_RIGHT, Pictures::gentleRight),
        TIGHT_RIGHT(Button.TIGHT_RIGHT, Pictures::tightRight);

        final Button button;
        final Function<Pictures, Icon> picture;

        Turn(Button button, Function<Pictures, Icon> picture) {
            this.button = button;
            this.picture = picture;
        }

        Component label() {
            return Component.translatable("screen.skyliners.autopilot_settings.turn." + name().toLowerCase(Locale.ROOT));
        }
    }

    private final BlockPos pos;
    /** Whether engaging takes off, so the button says so. */
    private final boolean takeoff;
    /** Whether the airliner is not assembled but engaging has its Physics Assembler assemble it, so it may engage. */
    private final boolean assembles;
    /**
     * How hard gravity pulls where the autopilot is, in blocks a second each second: the size of a circle follows from
     * it.
     */
    private final float gravity;
    /**
     * Whether engaging has just had the Physics Assembler set to assembling the airliner: its button stays greyed out
     * from the moment it is pressed until the autopilot, moved aboard the assembled airliner, closes the screen, or the
     * server says it could not assemble it (see {@link #assemblyFailed}).
     */
    private boolean assembling;
    private Lateral lateral;
    private CircleSize circle;
    /** Whether, flying to the pin, it lands near it rather than circling it. */
    private boolean landAtPin;
    private final Typed speed;
    private final Typed heading;
    private final Typed altitude;
    /** The panel's top left corner on the screen. */
    private int left;
    private int top;
    private SteeringFan fan;
    private final List<TurnTile> turns = new ArrayList<>();
    private PinTile pinTile;
    private PinCircle pinCircle;
    private LandToggle landToggle;
    private IconButton engage;
    private IconButton apply;
    private IconButton disengage;
    private IconButton land;
    private IconButton unstuck;
    /** Whether Unstuck is held down, keeping the control jets firing. */
    private boolean hopping;
    /** The map the pin is dropped on, in the steering's place while it flies to the pin. */
    private final DestinationMap map;
    /** The heights and speeds it can be set to, and where its speed dial turns amber and red. */
    private final OpenAutopilotScreen.Limits limits;

    private AutopilotSettingsScreen(OpenAutopilotScreen opened) {
        super(Component.translatable("screen.skyliners.autopilot_settings.title"));
        Settings settings = opened.settings();
        pos = opened.pos();
        takeoff = opened.takeoff();
        assembles = opened.assembles();
        gravity = opened.gravity();
        lateral = settings.lateral();
        circle = settings.circle();
        landAtPin = settings.landAtPin();
        limits = opened.limits();
        speed = new Typed("speed", PanelArt.AMBER, settings.speed(), limits.minSpeed(), limits.maxSpeed(), false);
        heading = new Typed("heading", PanelArt.CYAN, settings.heading(), 0, LAST_BEARING, true);
        altitude = new Typed("altitude", PanelArt.GREEN, settings.altitude(), limits.minAltitude(), limits.maxAltitude(), false);
        BlockPos pin = lateral == Lateral.DESTINATION ? settings.destination() : null;
        map = new DestinationMap(steeringWidth(), TURNS_Y + TURN_HEIGHT, pos, this::autopilot, pin, this::circleRadius);
    }

    /** Opens the settings as the server sent them, over whatever else is open. */
    public static void open(OpenAutopilotScreen opened) {
        Minecraft.getInstance().setScreen(new AutopilotSettingsScreen(opened));
    }

    /** The autopilot at a position could not have its airliner assembled: its screen, if open, can engage again. */
    public static void assemblyFailed(BlockPos pos) {
        if (Minecraft.getInstance().screen instanceof AutopilotSettingsScreen screen && screen.pos.equals(pos)) {
            screen.assembling = false;
        }
    }

    private static int steeringWidth() {
        return CONTENT - SPEEDOMETER_WIDTH - SKY_WIDTH - 2 * COLUMN_GAP;
    }

    /** The circle round the pin as chosen, at the speed set, in blocks, for the map to draw; none for a rocket. */
    private int circleRadius() {
        return rocket() ? 0 : (int) Math.round(circle.radius(speed.value(), gravity));
    }

    /** Whether the autopilot flies a rocket, which circles nothing, and holds no heading without a pin. */
    private boolean rocket() {
        AutopilotBlockEntity autopilot = autopilot();
        return autopilot != null && autopilot.craft() == Craft.ROCKET;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        turns.clear();
        int x = left + PanelArt.BORDER + PADDING;
        int y = top + CONTROLS_Y;
        int boxY = y + GAUGE_HEIGHT + BOX_GAP;
        addRenderableWidget(new SpeedDial(x, y));
        speed.place(x + (SPEEDOMETER_WIDTH - BOX_WIDTH) / 2, boxY);
        int steeringX = x + SPEEDOMETER_WIDTH + COLUMN_GAP;
        int steering = steeringWidth();
        fan = addRenderableWidget(new SteeringFan(steeringX, y, steering));
        int turnWidth = (steering - (Turn.values().length - 1) * TURN_GAP) / Turn.values().length;
        for (Turn turn : Turn.values()) {
            turns.add(addRenderableWidget(new TurnTile(steeringX + turn.ordinal() * (turnWidth + TURN_GAP), y + TURNS_Y, turnWidth, turn)));
        }
        map.setPosition(steeringX, y);
        addRenderableWidget(map);
        int stripY = y + STRIP_Y;
        pinTile = addRenderableWidget(new PinTile(steeringX, stripY));
        pinCircle = addRenderableWidget(new PinCircle(steeringX + STRIP_TILE + TURN_GAP, stripY));
        landToggle = addRenderableWidget(new LandToggle(steeringX + steering - STRIP_TILE, stripY));
        heading.place(steeringX + (steering - BOX_WIDTH) / 2, stripY + (STRIP_HEIGHT - BOX_HEIGHT) / 2);
        int skyX = steeringX + steering + COLUMN_GAP;
        addRenderableWidget(new SkyColumn(skyX, y));
        altitude.place(skyX + (SKY_WIDTH - BOX_WIDTH) / 2, boxY);

        // Along the bottom in three places: engage across the first two, or apply and disengage in them once engaged;
        // then land, or hop the airliner free. Closing is the close button in the title bar.
        int buttonWidth = (CONTENT - 2 * ACTION_GAP) / 3;
        int buttonY = top + ACTIONS_Y;
        Component engageLabel = Component.translatable(takeoff ? "screen.skyliners.autopilot_settings.take_off" : "screen.skyliners.autopilot_settings.engage");
        engage = addRenderableWidget(new IconButton(x, buttonY, 2 * buttonWidth + ACTION_GAP, ACTION_HEIGHT, engageLabel,
            takeoff ? pictures().takeOff() : pictures().cruise(), PanelArt.GREEN, true, this::engage));
        apply = addRenderableWidget(new IconButton(x, buttonY, buttonWidth, ACTION_HEIGHT, Component.translatable("screen.skyliners.autopilot_settings.apply"),
            APPLY, PanelArt.GREEN, false, () -> send(SetAutopilot.Action.FLY)));
        disengage = addRenderableWidget(new IconButton(x + buttonWidth + ACTION_GAP, buttonY, buttonWidth, ACTION_HEIGHT,
            Component.translatable("screen.skyliners.autopilot_settings.disengage"), DISENGAGE, PanelArt.RED, false, () -> send(SetAutopilot.Action.OFF)));
        int lastX = x + 2 * (buttonWidth + ACTION_GAP);
        land = addRenderableWidget(new IconButton(lastX, buttonY, buttonWidth, ACTION_HEIGHT,
            Component.translatable("screen.skyliners.autopilot_settings.land"), pictures().land(), PanelArt.AMBER, false, () -> send(SetAutopilot.Action.LAND)));
        unstuck = addRenderableWidget(new IconButton(lastX, buttonY, buttonWidth, ACTION_HEIGHT,
            Component.translatable("screen.skyliners.autopilot_settings.unstuck"), UNSTUCK, PanelArt.CYAN, true, this::unstick));
        int closeX = left + WIDTH - PanelArt.BORDER - CLOSE_INSET - CloseButton.SIZE;
        addRenderableWidget(new CloseButton(closeX, top + PanelArt.BORDER + CLOSE_INSET, this::onClose));
        refresh();
    }

    /** The autopilot, as this client has it, if it is still there. */
    @Nullable
    private AutopilotBlockEntity autopilot() {
        Level level = minecraft != null ? minecraft.level : null;
        return level != null && level.getBlockEntity(pos) instanceof AutopilotBlockEntity autopilot ? autopilot : null;
    }

    /** The pictures of what the autopilot flies, the airliner's or the zeppelin's. */
    private Pictures pictures() {
        AutopilotBlockEntity autopilot = autopilot();
        return Pictures.of(autopilot != null ? autopilot.craft() : Craft.AIRLINER);
    }

    private static Direction nose(AutopilotBlockEntity autopilot) {
        return autopilot.getBlockState().getValue(AutopilotBlock.FACING);
    }

    /** How the airliner flies now, while it is assembled; null otherwise. */
    @Nullable
    private SableClient.Attitude attitude(float partialTick) {
        AutopilotBlockEntity autopilot = autopilot();
        return autopilot != null ? SableClient.attitude(autopilot, nose(autopilot), partialTick) : null;
    }

    /**
     * The compass bearing the airliner flies on now, or, until it is assembled, the way its nose points, as engaging
     * takes off on: never the heading set, which the steering fan sets from it.
     */
    private double bearingNow(float partialTick) {
        SableClient.Attitude attitude = attitude(partialTick);
        if (attitude != null) {
            return attitude.heading();
        }
        AutopilotBlockEntity autopilot = autopilot();
        return autopilot != null ? AutopilotBlockEntity.bearing(nose(autopilot).toYRot()) : heading.value();
    }

    /** Shows the buttons for the autopilot as it is now, and the steering or the map for the way it flies. */
    private void refresh() {
        AutopilotBlockEntity autopilot = autopilot();
        boolean assembled = autopilot != null && SableClient.attitude(autopilot, nose(autopilot), 1) != null;
        Phase phase = autopilot != null ? autopilot.phase() : Phase.OFF;
        boolean on = phase != Phase.OFF;
        // Set to fly to a pin, it needs one dropped first.
        boolean toPin = lateral == Lateral.DESTINATION;
        boolean pinned = !toPin || map.pin() != null;
        engage.visible = !on;
        engage.active = (assembled || assembles) && pinned && !assembling;
        apply.visible = on;
        // Braking to a stop once down it takes no new settings; coming in to land, applying calls the landing off and
        // it climbs back to the height set, so Apply stays lit; otherwise only something set anew is worth applying.
        apply.active = phase != Phase.ROLLOUT && pinned && autopilot != null && (autopilot.comingIn() || setAnew(autopilot));
        disengage.visible = on;
        // Only in the air, and not while it comes in to land already.
        boolean flying = phase == Phase.CLIMB || phase == Phase.DESCENT || phase == Phase.CRUISE;
        land.active = assembled && (on ? flying && !autopilot.comingIn() : !takeoff);
        // Stuck on the ground with its engines running, where landing is no use, it can be hopped free instead.
        unstuck.visible = hopping || assembled && autopilot.stuck();
        land.visible = !unstuck.visible;
        // Flying to a pin, the map takes the steering's place, with how to circle it and whether to land there beside;
        // the height and speed stay. A rocket circles nothing and always comes down on the pin, so she has neither, nor
        // any circle tile beside straight on; and with no pin she comes back down where she rose whatever the heading,
        // so her fan shows her way up and back and takes no heading.
        boolean rocket = rocket();
        map.visible = toPin;
        pinCircle.visible = toPin && !rocket;
        landToggle.visible = toPin && !rocket;
        fan.visible = !toPin;
        fan.active = !rocket;
        turns.forEach(turn -> turn.visible = !toPin && (!rocket || turn.turn == Turn.STRAIGHT));
        heading.setVisible(!toPin && !rocket);
    }

    /**
     * Whether anything on the panel differs from what the autopilot flies now, as the server would take it (see
     * {@link AutopilotBlockEntity#set}): the way it steers, the height or the speed, and then what that way flies: the
     * heading, how tightly it circles, or the pin, how tightly it circles it and whether it lands there.
     */
    private boolean setAnew(AutopilotBlockEntity autopilot) {
        boolean same = lateral == autopilot.lateral() && altitude.value() == Math.round(autopilot.targetAltitude())
            && speed.value() == Math.round(autopilot.targetSpeed());
        if (!same) {
            return true;
        }
        return switch (lateral) {
            case HEADING -> autopilot.craft() != Craft.ROCKET && heading.value() != AutopilotBlockEntity.bearing(autopilot.targetHeading());
            case ORBIT_LEFT, ORBIT_RIGHT -> autopilot.craft() != Craft.ROCKET && circle != autopilot.circle();
            case DESTINATION -> pinMoved(autopilot) || autopilot.craft() != Craft.ROCKET && (circle != autopilot.circle() || landAtPin != autopilot.landAtPin());
        };
    }

    /** Whether the pin on the map is not where the autopilot flies to. */
    private boolean pinMoved(AutopilotBlockEntity autopilot) {
        BlockPos pin = map.pin();
        return pin == null || pin.getX() != Mth.floor(autopilot.orbitX()) || pin.getZ() != Mth.floor(autopilot.orbitZ());
    }

    /** Closes only once the autopilot is gone; else it stays open until its close button is pressed. */
    @Override
    public void tick() {
        if (autopilot() == null || minecraft == null || minecraft.player == null) {
            onClose();
            return;
        }
        refresh();
        if (hopping) {
            hopping = leftHeld();
        }
        if (hopping) {
            PacketDistributor.sendToServer(new UnstickAirliner(pos));
        }
    }

    /** Only its close button closes it, not the escape key: it is left open to watch the autopilot fly. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void removed() {
        map.close();
        super.removed();
    }

    /**
     * Engages the autopilot with the settings as they stand; with the airliner not yet assembled, greys the button out
     * at once while the Physics Assembler assembles it, so it is not asked twice.
     */
    private void engage() {
        AutopilotBlockEntity autopilot = autopilot();
        assembling = assembles && (autopilot == null || SableClient.attitude(autopilot, nose(autopilot), 1) == null);
        refresh();
        send(SetAutopilot.Action.FLY);
    }

    /** Sends the settings as they stand: to fly them, to land or with the autopilot switched off. */
    private void send(SetAutopilot.Action action) {
        BlockPos pin = map.pin() != null ? map.pin() : BlockPos.ZERO;
        Settings settings = new Settings(speed.value(), altitude.value(), heading.value(), lateral, circle, pin, landAtPin);
        PacketDistributor.sendToServer(new SetAutopilot(pos, action, settings));
    }

    /**
     * Asks for the airliner to be hopped free; clicked, asks again every tick the button is held, to keep the jets
     * firing (see {@link #tick}).
     */
    private void unstick() {
        PacketDistributor.sendToServer(new UnstickAirliner(pos));
        hopping = leftHeld();
    }

    /**
     * Whether the left mouse button is down, asked of the window itself: the game's own note of it is not kept while a
     * screen is open, which takes the clicks.
     */
    private boolean leftHeld() {
        return minecraft != null
            && GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // Enter, while no button has the keyboard, flies the settings, as the button there would.
        boolean enter = keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER;
        if (enter && apply.visible && apply.active) {
            send(SetAutopilot.Action.FLY);
            return true;
        }
        if (enter && engage.visible && engage.active) {
            engage();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The world dimmed behind it, as behind a container, rather than blurred: the flight goes on.
        renderTransparentBackground(graphics);
        PanelArt.drawFrame(graphics, left, top, left + WIDTH, top + HEIGHT);
        PanelArt.drawTitleBar(graphics, font, pictures().emblem(), title, left, top, left + WIDTH);
        int x = left + PanelArt.BORDER + PADDING;
        PanelArt.drawGlass(graphics, x, top + GLASS_Y, x + CONTENT, top + GLASS_Y + GLASS_HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // What it is doing, as its screen on the flight deck says it, by a lamp, at the end of the title bar.
        AutopilotBlockEntity autopilot = autopilot();
        Level level = autopilot != null ? autopilot.getLevel() : null;
        SableClient.Attitude attitude = attitude(partialTick);
        boolean on = AutopilotScreenRenderer.engaged(autopilot);
        Component state = AutopilotScreenRenderer.state(autopilot, attitude);
        int stateColour = attitude == null ? PanelArt.GREY : on ? PanelArt.GREEN : PanelArt.AMBER;
        int stateX = left + WIDTH - PanelArt.BORDER - CLOSE_INSET - CloseButton.SIZE - STATE_GAP - font.width(state);
        int titleMiddle = top + PanelArt.BORDER + PanelArt.TITLE_HEIGHT / 2;
        graphics.drawString(font, state, stateX, titleMiddle - PanelArt.GLYPH_HEIGHT / 2, stateColour, false);
        float brightness = attitude == null ? LAMP_OUT : on ? Mth.lerp(PanelArt.pulse(LAMP_PERIOD), LAMP_LOW, 1F) : 1F;
        PanelArt.drawLamp(graphics, stateX - LAMP_GAP, titleMiddle, stateColour, brightness);

        // On the glass, how the airliner flies, or what engaging does.
        int textX = left + PanelArt.BORDER + PADDING + GLASS_INSET;
        if (attitude != null && level != null && on) {
            Vec3 here = SableClient.toWorld(level, Vec3.atCenterOf(pos));
            graphics.drawString(font, Component.translatable("screen.skyliners.autopilot_settings.now"), textX, top + LINE_Y, PanelArt.GREY, false);
            graphics.drawString(font, AutopilotScreenRenderer.now(attitude, here), textX + VALUE_X, top + LINE_Y, PanelArt.WHITE, false);
        } else if (attitude != null) {
            String hint = takeoff ? "screen.skyliners.autopilot_settings.take_off_hint" : "screen.skyliners.autopilot_settings.engage_hint";
            graphics.drawString(font, Component.translatable(hint), textX, top + LINE_Y, PanelArt.GREY, false);
        } else if (assembles) {
            String hint = assembling ? "screen.skyliners.autopilot_settings.assembling" : "screen.skyliners.autopilot_settings.assemble_hint";
            graphics.drawString(font, Component.translatable(hint), textX, top + LINE_Y, assembling ? PanelArt.AMBER : PanelArt.GREY, false);
        }
    }

    /** A note for setting a share of the way from the least to the most, higher the more. */
    private static void note(double share) {
        float pitch = Mth.lerp((float) Mth.clamp(share, 0, 1), AutopilotBlockEntity.NOTE_LOW, AutopilotBlockEntity.NOTE_HIGH);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), pitch, SET_VOLUME));
    }

    /** The whoosh of choosing a way to fly. */
    private static void whoosh() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SkylinersRegistry.PAPER_PLANE_WHOOSH.get(), 1F, SET_VOLUME));
    }

    /** A line from one point to another, so many pixels wide, drawn in small squares along it. */
    private static void line(GuiGraphics graphics, double x0, double y0, double x1, double y1, int width, int colour) {
        int steps = (int) Math.ceil(Math.hypot(x1 - x0, y1 - y0) * 2) + 1;
        int half = width / 2;
        for (int k = 0; k <= steps; k++) {
            int x = (int) Math.round(Mth.lerp((double) k / steps, x0, x1));
            int y = (int) Math.round(Mth.lerp((double) k / steps, y0, y1));
            graphics.fill(x - half, y - half, x - half + width, y - half + width, colour);
        }
    }

    /** A dotted line from one point to another, a dot every few pixels. */
    private static void dotted(GuiGraphics graphics, double x0, double y0, double x1, double y1, int colour) {
        int dots = (int) (Math.hypot(x1 - x0, y1 - y0) / 3);
        for (int k = 0; k <= dots; k++) {
            int x = (int) Math.round(Mth.lerp((double) k / Math.max(1, dots), x0, x1));
            int y = (int) Math.round(Mth.lerp((double) k / Math.max(1, dots), y0, y1));
            graphics.fill(x, y, x + 1, y + 1, colour);
        }
    }

    /**
     * One setting's number, as typed into its box: kept to its range, a bearing going round, and what it was last set
     * to while what is typed cannot be read.
     */
    private final class Typed {
        private final String key;
        private final int colour;
        private final int min;
        private final int max;
        private final boolean bearing;
        private int value;
        @Nullable
        private ValueBox box;

        Typed(String key, int colour, int value, int min, int max, boolean bearing) {
            this.key = key;
            this.colour = colour;
            this.min = min;
            this.max = max;
            this.bearing = bearing;
            this.value = fit(value);
        }

        /** Puts its box at a place, keeping what was typed into it before. */
        void place(int x, int y) {
            value = value();
            ValueBox typed = addRenderableWidget(new ValueBox(font, x, y, BOX_WIDTH, BOX_HEIGHT, name(), colour));
            typed.setMaxLength(MAX_LENGTH);
            typed.setFilter(text -> NUMBER.matcher(text).matches());
            typed.setValue(format(value));
            typed.setResponder(text -> typed.setTextColor(readable(text) ? TYPED : UNREADABLE));
            typed.setTooltip(Tooltip.create(tooltip()));
            box = typed;
        }

        /** What it is set to: what is typed, brought into range, or what it was last set to if that cannot be read. */
        int value() {
            if (box == null) {
                return value;
            }
            try {
                return fit(Integer.parseInt(box.getValue()));
            } catch (NumberFormatException e) {
                return value;
            }
        }

        /** Sets it, showing the number in its box. */
        void show(int set) {
            value = fit(set);
            if (box != null) {
                box.setValue(format(value));
            }
        }

        int min() {
            return min;
        }

        int max() {
            return max;
        }

        /** The share of the way from its least to its most a value is. */
        double share(double set) {
            return max > min ? Mth.clamp((set - min) / (max - min), 0, 1) : 0;
        }

        private boolean readable(String text) {
            try {
                int typed = Integer.parseInt(text);
                return typed >= min && typed <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private int fit(int set) {
            return bearing ? Math.floorMod(set, max + 1) : Mth.clamp(set, min, max);
        }

        String format(int set) {
            return bearing ? String.format(Locale.ROOT, "%03d", set) : Integer.toString(set);
        }

        Component name() {
            return Component.translatable("screen.skyliners.autopilot_settings." + key);
        }

        /** Its name and its range, shown over its box. */
        Component tooltip() {
            return CommonComponents.optionNameValue(name(), Component.translatable("screen.skyliners.autopilot_settings.range." + key, format(min), format(max)));
        }

        void setVisible(boolean visible) {
            if (box != null) {
                box.visible = visible;
            }
        }
    }

    /**
     * A control set by where it is clicked or dragged to, as the flight deck's are touched: a step at a time by the
     * arrow keys or the scroll wheel, sounding a note higher the more it sets. Pointed at, it shows a ghost of what a
     * click there would set.
     */
    private abstract class Gauge extends AbstractWidget {
        private final Typed setting;
        private final int step;

        Gauge(int x, int y, int width, int height, Typed setting, int step) {
            super(x, y, width, height, setting.name());
            this.setting = setting;
            this.step = step;
        }

        /** What a click at a place on it sets, before it is rounded to its step. */
        abstract double valueAt(double mouseX, double mouseY);

        /** What a click at a place on it sets, rounded to its step and kept to its range. */
        int stepped(double mouseX, double mouseY) {
            return Mth.clamp((int) Math.round(valueAt(mouseX, mouseY) / step) * step, setting.min(), setting.max());
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            set(stepped(mouseX, mouseY));
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            set(stepped(mouseX, mouseY));
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (scrollY != 0) {
                set(setting.value() + (scrollY > 0 ? step : -step));
            }
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            boolean more = keyCode == InputConstants.KEY_UP || keyCode == InputConstants.KEY_RIGHT;
            boolean less = keyCode == InputConstants.KEY_DOWN || keyCode == InputConstants.KEY_LEFT;
            if (more || less) {
                set(setting.value() + (more ? step : -step));
                return true;
            }
            return false;
        }

        private void set(int value) {
            if (value != setting.value()) {
                setting.show(value);
                changed();
            }
        }

        /** Set to something new: a note, higher the more it sets. */
        void changed() {
            note(setting.share(setting.value()));
        }

        @Override
        public void playDownSound(SoundManager sounds) {
        }

        /** The value a ghost shows, where the pointer is on it; null while it is not pointed at. */
        @Nullable
        Integer ghost(int mouseX, int mouseY) {
            return isHovered() ? stepped(mouseX, mouseY) : null;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            setMessage(CommonComponents.optionNameValue(setting.name(), Component.literal(setting.format(setting.value()))));
            defaultButtonNarrationText(output);
        }
    }

    /**
     * The speed: a speedometer, its band green, amber and red from slow to fast, ticked and numbered round it; the
     * needle at the speed now, the magenta bug on its rim at the speed set, and a ghost of one where it is pointed at.
     */
    private final class SpeedDial extends Gauge {
        SpeedDial(int x, int y) {
            super(x, y, SPEEDOMETER_WIDTH, GAUGE_HEIGHT, speed, AutopilotBlockEntity.SPEED_STEP);
        }

        private int middleX() {
            return getX() + width / 2;
        }

        private int middleY() {
            return getY() + DIAL_MIDDLE;
        }

        /** A step between the airliner's dial's marks, as much smaller as this dial's speeds are, in round numbers. */
        private int step(int airliner) {
            double wanted = airliner * (speed.max() - speed.min()) / (double) (AutopilotBlockEntity.MAX_SPEED - AutopilotBlockEntity.MIN_SPEED);
            for (int nice : NICE_STEPS) {
                if (nice >= wanted) {
                    return nice;
                }
            }
            return airliner;
        }

        /** Where a speed lies round the dial, in radians anticlockwise from the right, as on the flight deck's. */
        private double angle(double set) {
            return new Dial(0, 0, 1).angle(speed.share(set));
        }

        @Override
        double valueAt(double mouseX, double mouseY) {
            double share = new Dial(middleX(), -middleY(), DIAL_RADIUS).share(mouseX, -mouseY);
            return Mth.lerp(share, speed.min(), speed.max());
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            PanelArt.drawTile(graphics, getX(), getY(), getRight(), getBottom(), PanelArt.AMBER, isHovered() ? Look.HOT : Look.IDLE, false);
            PanelArt.drawDisc(graphics, middleX(), middleY(), DIAL_RADIUS + 2, GLASS);
            int amber = limits.amberFrom();
            int red = limits.redFrom();
            band(graphics, speed.min(), amber, PanelArt.GREEN);
            band(graphics, amber, red, PanelArt.AMBER);
            band(graphics, red, speed.max(), PanelArt.RED);
            int tick = step(AutopilotDeskArt.SPEED_TICK);
            int numbered = step(AutopilotDeskArt.SPEED_NUMBER);
            for (int each = speed.min(); each <= speed.max(); each += tick) {
                int length = each % numbered == 0 ? LONG_TICK : SHORT_TICK;
                spoke(graphics, each, DIAL_RADIUS - BAND - length, DIAL_RADIUS - BAND, 1, PanelArt.WHITE);
            }
            for (int each = speed.min(); each <= speed.max(); each += numbered) {
                String number = String.valueOf(each);
                double a = angle(each);
                int x = (int) Math.round(middleX() + Math.cos(a) * (DIAL_RADIUS - BAND - SPEED_NUMBERS_IN)) - font.width(number) / 2;
                int y = (int) Math.round(middleY() - Math.sin(a) * (DIAL_RADIUS - BAND - SPEED_NUMBERS_IN)) - PanelArt.GLYPH_HEIGHT / 2;
                graphics.drawString(font, number, x, y, PanelArt.GREY, false);
            }
            bug(graphics, speed.value(), PanelArt.MAGENTA);
            Integer ghost = ghost(mouseX, mouseY);
            if (ghost != null) {
                bug(graphics, ghost, AutopilotDeskArt.GHOST & PanelArt.MAGENTA);
            }
            SableClient.Attitude attitude = attitude(partialTick);
            if (attitude != null) {
                spoke(graphics, Mth.clamp(attitude.airspeed(), speed.min(), speed.max()), 0, DIAL_RADIUS - BAND - 2, 2, PanelArt.WHITE);
            }
            PanelArt.drawDisc(graphics, middleX(), middleY(), 3, PanelArt.GREY);
            // Pointed at, its number shows in white what a click there would set.
            String set = String.valueOf(ghost != null ? ghost : speed.value());
            graphics.drawString(font, set, middleX() - font.width(set) / 2, getBottom() - SPEED_NUMBER_UP, ghost != null ? PanelArt.WHITE : PanelArt.MAGENTA, false);
        }

        private void band(GuiGraphics graphics, int from, int to, int colour) {
            for (double each = from; each < to; each += 0.5) {
                spoke(graphics, each, DIAL_RADIUS - BAND, DIAL_RADIUS, 2, colour);
            }
        }

        private void spoke(GuiGraphics graphics, double set, double from, double to, int width, int colour) {
            double a = angle(set);
            line(graphics, middleX() + Math.cos(a) * from, middleY() - Math.sin(a) * from, middleX() + Math.cos(a) * to, middleY() - Math.sin(a) * to, width,
                colour);
        }

        /** The bug: a small triangle outside the rim, pointing in. */
        private void bug(GuiGraphics graphics, double set, int colour) {
            for (int out = 0; out < BUG; out++) {
                double a = angle(set);
                double spread = out * BUG_SPREAD;
                line(graphics, middleX() + Math.cos(a - spread) * (DIAL_RADIUS + 2 + out), middleY() - Math.sin(a - spread) * (DIAL_RADIUS + 2 + out),
                    middleX() + Math.cos(a + spread) * (DIAL_RADIUS + 2 + out), middleY() - Math.sin(a + spread) * (DIAL_RADIUS + 2 + out), 1, colour);
            }
        }
    }

    /**
     * The steering fan, seen from above: the airliner at its foot nose up, as on the moving map; its rim cyan while it
     * holds a heading, ticked every so many degrees, north faintly on it; holding a heading, the magenta bug where it
     * is set to fly and a path from the nose to it; and a ghost of the bug where it is pointed at. Clicked, it holds
     * the heading clicked, straight on. A rocket, which holds no heading, is drawn on it from the side instead, with
     * her way up from beside her, over her and back down where she rose (see {@link #roundTrip}); it takes no clicks
     * then.
     */
    private final class SteeringFan extends Gauge {
        SteeringFan(int x, int y, int width) {
            super(x, y, width, FAN_HEIGHT, heading, AutopilotBlockEntity.HEADING_STEP);
        }

        private int footX() {
            return getX() + width / 2;
        }

        private int footY() {
            return getBottom() - 1;
        }

        /** How far right of the nose a compass bearing lies, in degrees, kept to the fan's half. */
        private double turnTo(double bearing) {
            return Mth.clamp(Mth.wrapDegrees(bearing - bearingNow(1)), -90, 90);
        }

        @Override
        double valueAt(double mouseX, double mouseY) {
            double turn = Math.toDegrees(Math.atan2(mouseX - footX(), Math.max(0, footY() - mouseY)));
            return Math.round(bearingNow(1)) + Mth.clamp(turn, -90, 90);
        }

        @Override
        int stepped(double mouseX, double mouseY) {
            return Math.floorMod((int) Math.round(valueAt(mouseX, mouseY) / AutopilotBlockEntity.HEADING_STEP) * AutopilotBlockEntity.HEADING_STEP, 360);
        }

        /** Clicked, it whooshes, as a turn chosen does; dragged round, it only moves its bug. */
        @Override
        public void onClick(double mouseX, double mouseY) {
            super.onClick(mouseX, mouseY);
            lateral = Lateral.HEADING;
            whoosh();
        }

        /** Set to a heading, it holds it, straight on, whatever way it was set to fly. */
        @Override
        void changed() {
            lateral = Lateral.HEADING;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean steering = lateral == Lateral.HEADING;
            for (int dy = 0; dy <= FAN_RADIUS; dy++) {
                int half = (int) Math.floor(Math.sqrt(FAN_RADIUS * FAN_RADIUS - dy * dy));
                graphics.fill(footX() - half, footY() - dy, footX() + half + 1, footY() - dy + 1, GLASS);
            }
            int rim = steering ? PanelArt.CYAN : AutopilotDeskArt.RIM_IDLE;
            for (double turn = -90; turn <= 90; turn += 1) {
                mark(graphics, turn, FAN_RADIUS - 1, FAN_RADIUS, 2, rim);
            }
            if (rocket()) {
                roundTrip(graphics, steering);
                return;
            }
            for (int turn = -90; turn <= 90; turn += AutopilotDeskArt.FAN_TICK) {
                mark(graphics, turn, FAN_RADIUS - (turn % AutopilotDeskArt.FAN_LONG_TICK == 0 ? FAN_LONG : FAN_SHORT), FAN_RADIUS, 1, rim);
            }
            double north = Mth.wrapDegrees(-bearingNow(partialTick));
            if (Math.abs(north) <= 90) {
                mark(graphics, north, FAN_RADIUS - NORTH_FROM, FAN_RADIUS - NORTH_TO, 2, AutopilotDeskArt.NORTH);
            }
            if (steering) {
                double turn = turnTo(heading.value());
                path(graphics, turn);
                bug(graphics, turn, PanelArt.MAGENTA);
            }
            Integer ghost = ghost(mouseX, mouseY);
            if (ghost != null) {
                bug(graphics, turnTo(ghost), AutopilotDeskArt.GHOST & PanelArt.MAGENTA);
            }
            pictures().fromAbove().draw(graphics, footX() - Icon.SIZE / 2, footY() - Icon.SIZE, 1, true);
            String set = String.format(Locale.ROOT, "%03d°", ghost != null ? ghost : heading.value());
            int colour = ghost != null ? PanelArt.WHITE : steering ? PanelArt.CYAN : PanelArt.GREY;
            graphics.drawString(font, set, getRight() - font.width(set) - 4, getY() + 4, colour, false);
        }

        /**
         * A rocket seen from the side on her launch pad at the fan's foot, and her way up from beside her, over her
         * and back down the other side, magenta while she is set to come back where she rose and grey otherwise; and
         * RETURN in the corner where an airliner's heading is, cyan while set and grey otherwise.
         */
        private void roundTrip(GuiGraphics graphics, boolean chosen) {
            int colour = chosen ? PanelArt.MAGENTA : AutopilotDeskArt.RIM_IDLE;
            int pad = footY() + 1 - PAD_HEIGHT;
            graphics.fill(footX() - PAD_HALF_WIDTH, pad, footX() + PAD_HALF_WIDTH + 1, footY() + 1, PICTURE_FAINT);
            int ground = pad - 1;
            int top = footY() - TRIP_TOP;
            int left = footX() - TRIP_HALF_WIDTH;
            int right = footX() + TRIP_HALF_WIDTH;
            line(graphics, left, ground, left, top, 2, colour);
            line(graphics, right, top, right, ground, 2, colour);
            for (int k = 0; k < TRIP_PIECES; k++) {
                double a0 = Math.PI * k / TRIP_PIECES;
                double a1 = Math.PI * (k + 1) / TRIP_PIECES;
                line(graphics, footX() - Math.cos(a0) * TRIP_HALF_WIDTH, top - Math.sin(a0) * TRIP_HALF_WIDTH,
                    footX() - Math.cos(a1) * TRIP_HALF_WIDTH, top - Math.sin(a1) * TRIP_HALF_WIDTH, 2, colour);
            }
            arrowhead(graphics, left, (ground + top) / 2 - TRIP_ARROW, -1, colour);
            arrowhead(graphics, right, (ground + top) / 2 + TRIP_ARROW, 1, colour);
            pictures().fromTheSide().draw(graphics, footX() - Icon.SIZE / 2, pad - Icon.SIZE, 1, true);
            String back = Component.translatable("screen.skyliners.autopilot.mode.rocket_back").getString();
            graphics.drawString(font, back, getRight() - font.width(back) - 4, getY() + 4, chosen ? PanelArt.CYAN : PanelArt.GREY, false);
        }

        /** An arrowhead on a line running up or down, its tip where given, pointing up (-1) or down (1). */
        private void arrowhead(GuiGraphics graphics, int x, int y, int way, int colour) {
            line(graphics, x, y, x - TRIP_ARROW, y - way * TRIP_ARROW, 2, colour);
            line(graphics, x, y, x + TRIP_ARROW, y - way * TRIP_ARROW, 2, colour);
        }

        /** The path from the airliner's nose round to the bug, so many degrees right of the nose: a magenta curve. */
        private void path(GuiGraphics graphics, double turn) {
            double r = FAN_RADIUS - FAN_BUG;
            double endX = footX() + Math.sin(Math.toRadians(turn)) * r;
            double endY = footY() - Math.cos(Math.toRadians(turn)) * r;
            double bendY = footY() - FAN_RADIUS * PATH_BEND;
            for (int k = 0; k < PATH_PIECES; k++) {
                double t0 = (double) k / PATH_PIECES;
                double t1 = (double) (k + 1) / PATH_PIECES;
                line(graphics, CockpitKeys.curve(footX(), footX(), endX, t0), CockpitKeys.curve(footY() - NOSE, bendY, endY, t0),
                    CockpitKeys.curve(footX(), footX(), endX, t1), CockpitKeys.curve(footY() - NOSE, bendY, endY, t1), 2, PanelArt.MAGENTA);
            }
        }

        /** A line out from the fan's foot, so many degrees right of the nose, between two distances. */
        private void mark(GuiGraphics graphics, double turn, double from, double to, int width, int colour) {
            double a = Math.toRadians(turn);
            line(graphics, footX() + Math.sin(a) * from, footY() - Math.cos(a) * from, footX() + Math.sin(a) * to, footY() - Math.cos(a) * to, width, colour);
        }

        private void bug(GuiGraphics graphics, double turn, int colour) {
            double a = Math.toRadians(turn);
            PanelArt.drawDisc(graphics, (int) Math.round(footX() + Math.sin(a) * FAN_RADIUS), (int) Math.round(footY() - Math.cos(a) * FAN_RADIUS), FAN_BUG,
                colour);
        }

    }

    /**
     * The height: the sky from the side, darkening upward over the earth and its grass, and for a rocket, which climbs
     * so high, black space over it from the edge of space up; heights ticked and numbered up its side; the airliner in
     * green at the height set, faintly at the height now, a dotted climb or descent between, and a ghost of it where the
     * sky is pointed at.
     */
    private final class SkyColumn extends Gauge {
        SkyColumn(int x, int y) {
            super(x, y, SKY_WIDTH, GAUGE_HEIGHT, altitude, AutopilotBlockEntity.ALTITUDE_STEP);
        }

        private int ground() {
            return getBottom() - SKY_FRAME - GROUND;
        }

        private int lowest() {
            return ground() - SKY_ROOM;
        }

        private int highest() {
            return getY() + SKY_FRAME + SKY_ROOM;
        }

        /** How far down the panel a height lies in the sky. */
        private int yOf(double set) {
            return (int) Math.round(Mth.lerp(altitude.share(set), lowest(), highest()));
        }

        @Override
        double valueAt(double mouseX, double mouseY) {
            double share = Mth.clamp((lowest() - mouseY) / (double) (lowest() - highest()), 0, 1);
            return Mth.lerp(share, altitude.min(), altitude.max());
        }

        /**
         * How many blocks of height come between the sky's numbers: the flight deck's step, or as many times more as
         * keeps them {@link #NUMBERS_APART}, on a sky reaching so high they would crowd each other.
         */
        private int numberedEvery() {
            double pixels = (lowest() - highest()) / (double) Math.max(1, altitude.max() - altitude.min());
            for (int times : NUMBERS_SPREAD) {
                if (AutopilotDeskArt.HEIGHT_NUMBER * times * pixels >= NUMBERS_APART) {
                    return AutopilotDeskArt.HEIGHT_NUMBER * times;
                }
            }
            return AutopilotDeskArt.HEIGHT_NUMBER * NUMBERS_SPREAD[NUMBERS_SPREAD.length - 1];
        }

        /** The sky, the earth under it and its grass, and space over it, if it reaches the edge of space. */
        private void drawSky(GuiGraphics graphics, int x0, int x1) {
            int top = getY() + SKY_FRAME;
            int space = altitude.max() > EdgeOfSpace.HEIGHT ? yOf(EdgeOfSpace.HEIGHT) : top;
            graphics.fillGradient(x0, space, x1, ground(), AutopilotDeskArt.SKY_HIGH, AutopilotDeskArt.SKY_LOW);
            if (space > top) {
                drawSpace(graphics, x0, top, x1, space);
            }
            graphics.fill(x0, ground(), x1, getBottom() - SKY_FRAME, AutopilotDeskArt.EARTH);
            graphics.fill(x0, ground(), x1, ground() + 1, AutopilotDeskArt.GRASS);
        }

        /**
         * Space, from its top down to the edge of space: a picture needing no words of where space begins, black and
         * starry over the glow of the air along its foot.
         */
        private void drawSpace(GuiGraphics graphics, int x0, int top, int x1, int edge) {
            graphics.fill(x0, top, x1, edge, SPACE);
            for (int star = 0; star < STARS.length; star++) {
                int x = x0 + Math.round(STARS[star][0] * (x1 - x0 - 1));
                int y = top + Math.round(STARS[star][1] * Math.max(0, edge - top - 2));
                graphics.fill(x, y, x + 1, y + 1, star % 2 == 0 ? STAR : FAINT_STAR);
            }
            graphics.fill(x0, edge - 1, x1, edge, SPACE_GLOW);
            graphics.fillGradient(x0, edge, x1, edge + SPACE_GLOW_DEPTH, SPACE_GLOW_FADING, SPACE_GLOW_FADING & 0xFFFFFF);
        }

        /** The heights up the sky's side: a tick every so many blocks, the numbered ones longer, and their numbers. */
        private void drawScale(GuiGraphics graphics, int x1) {
            int numbered = numberedEvery();
            int tick = numbered * AutopilotDeskArt.HEIGHT_TICK / AutopilotDeskArt.HEIGHT_NUMBER;
            int first = (int) Math.ceil(altitude.min() / (double) tick) * tick;
            for (int each = first; each <= altitude.max(); each += tick) {
                int length = each % numbered == 0 ? HEIGHT_LONG : HEIGHT_SHORT;
                graphics.fill(x1 - length, yOf(each), x1, yOf(each) + 1, AutopilotDeskArt.HEIGHT_TICKS);
            }
            int firstNumber = (int) Math.ceil(altitude.min() / (double) numbered) * numbered;
            for (int each = firstNumber; each <= altitude.max(); each += numbered) {
                String number = String.valueOf(each);
                int y = yOf(each) - PanelArt.GLYPH_HEIGHT / 2;
                graphics.drawString(font, number, x1 - HEIGHT_NUMBER_GAP - font.width(number), y, AutopilotDeskArt.HEIGHT_NUMBERS, false);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            PanelArt.drawTile(graphics, getX(), getY(), getRight(), getBottom(), PanelArt.GREEN, isHovered() ? Look.HOT : Look.IDLE, false);
            int x0 = getX() + SKY_FRAME;
            int x1 = getRight() - SKY_FRAME;
            drawSky(graphics, x0, x1);
            drawScale(graphics, x1);
            int planeX = x0 + SKY_AIRLINER_X;
            int set = yOf(altitude.value());
            for (int dash = x0; dash < x1 - 6; dash += 3) {
                graphics.fill(dash, set, dash + 2, set + 1, PanelArt.GREEN);
            }
            AutopilotBlockEntity autopilot = autopilot();
            Level level = autopilot != null ? autopilot.getLevel() : null;
            Icon craft = pictures().fromTheSide();
            if (attitude(partialTick) != null && level != null) {
                int now = yOf(SableClient.toWorld(level, Vec3.atCenterOf(pos)).y);
                if (Math.abs(now - set) > CLIMB_DRAWN) {
                    dotted(graphics, planeX + Icon.SIZE / 2.0, now, planeX + Icon.SIZE / 2.0, set, PanelArt.WHITE);
                }
                craft.draw(graphics, planeX, now - Icon.SIZE / 2, 1, PICTURE_FAINT);
            }
            craft.draw(graphics, planeX, set - Icon.SIZE / 2, 1, PanelArt.GREEN);
            Integer ghost = ghost(mouseX, mouseY);
            if (ghost != null) {
                craft.draw(graphics, planeX, yOf(ghost) - Icon.SIZE / 2, 1, PanelArt.shade(PanelArt.GREEN, SKY_GHOST));
            }
            String number = String.valueOf(ghost != null ? ghost : altitude.value());
            graphics.drawString(font, number, x0 + 3, getY() + SKY_FRAME + 3, ghost != null ? PanelArt.WHITE : PanelArt.GREEN, true);
        }
    }

    /**
     * One of the ways to fly, along the bottom of the steering: its picture, lit cyan for straight on or magenta for a
     * circle while chosen. Pressed, it is chosen, to be engaged or applied; straight on holds the heading set.
     */
    private final class TurnTile extends PanelArt.PanelButton {
        private final Turn turn;

        TurnTile(int x, int y, int width, Turn turn) {
            super(x, y, width, TURN_HEIGHT, turn.label());
            this.turn = turn;
            showLabel();
        }

        /**
         * Chosen: a circle that way, that tightly; or straight on the way the airliner goes now, as on the flight deck.
         */
        @Override
        public void onPress() {
            if (turn == Turn.STRAIGHT) {
                heading.show((int) Math.round(bearingNow(1)));
            }
            lateral = turn.button.way;
            if (turn != Turn.STRAIGHT) {
                circle = turn.button.size;
            }
        }

        @Override
        public void playDownSound(SoundManager sounds) {
            whoosh();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Look look = look();
            boolean chosen = turn.button.flies(lateral, circle);
            int colour = turn == Turn.STRAIGHT ? PanelArt.CYAN : PanelArt.MAGENTA;
            PanelArt.drawTile(graphics, getX(), getY(), getRight(), getBottom(), colour, chosen && look == Look.IDLE ? Look.HOT : look, false);
            int sink = look == Look.PRESSED ? 1 : 0;
            turn.picture.apply(pictures()).draw(graphics, getX() + (width - Icon.SIZE) / 2, getY() + (height - Icon.SIZE) / 2 + sink, 1, true);
        }
    }

    /**
     * The pin: chosen, it flies to the pin dropped on the map, which takes the steering's place; pressed again, it
     * holds the heading set instead. Lit while chosen.
     */
    private final class PinTile extends PanelArt.PanelButton {
        PinTile(int x, int y) {
            super(x, y, STRIP_TILE, STRIP_HEIGHT, Component.translatable("screen.skyliners.autopilot_settings.mode.destination"));
            showLabel();
        }

        @Override
        public void onPress() {
            lateral = lateral == Lateral.DESTINATION ? Lateral.HEADING : Lateral.DESTINATION;
            refresh();
        }

        @Override
        public void playDownSound(SoundManager sounds) {
            whoosh();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Look look = look();
            PanelArt.drawTile(graphics, getX(), getY(), getRight(), getBottom(), PanelArt.MAGENTA, look, lateral == Lateral.DESTINATION);
            int sink = look == Look.PRESSED ? 1 : 0;
            PIN.draw(graphics, getX() + (width - Icon.SIZE) / 2, getY() + (height - Icon.SIZE) / 2 + sink, 1, true);
        }
    }

    /**
     * How tightly it circles the pin, beside the pin while it flies to one: the tight or the gentle turn to the right,
     * the way it goes round the pin, pressed to swap them. The map's ring shows the circle's size.
     */
    private final class PinCircle extends PanelArt.PanelButton {
        PinCircle(int x, int y) {
            super(x, y, STRIP_TILE, STRIP_HEIGHT, label(circle));
            showLabel();
        }

        private static Component label(CircleSize size) {
            return (size == CircleSize.TIGHT ? Turn.TIGHT_RIGHT : Turn.GENTLE_RIGHT).label();
        }

        @Override
        public void onPress() {
            circle = circle == CircleSize.TIGHT ? CircleSize.GENTLE : CircleSize.TIGHT;
            setMessage(label(circle));
            showLabel();
        }

        @Override
        public void playDownSound(SoundManager sounds) {
            whoosh();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Look look = look();
            PanelArt.drawTile(graphics, getX(), getY(), getRight(), getBottom(), PanelArt.MAGENTA, look, false);
            int sink = look == Look.PRESSED ? 1 : 0;
            Icon icon = circle == CircleSize.TIGHT ? pictures().tightRight() : pictures().gentleRight();
            icon.draw(graphics, getX() + (width - Icon.SIZE) / 2, getY() + (height - Icon.SIZE) / 2 + sink, 1, true);
        }
    }

    /**
     * Whether, flying to the pin, it lands near it: a tile with an airliner touching down on a runway by a pin, edged
     * in the landing's amber, lit green while it is on. Pointed at, it says what it does and whether it is on. It is
     * there only while it flies to a pin, and pressed it only changes the settings, to be engaged or applied.
     */
    private final class LandToggle extends PanelArt.PanelButton {
        LandToggle(int x, int y) {
            super(x, y, STRIP_TILE, STRIP_HEIGHT, label(landAtPin));
            showLabel();
        }

        private static Component label(boolean on) {
            return Component.translatable(on ? "screen.skyliners.autopilot_settings.land_at_pin.on" : "screen.skyliners.autopilot_settings.land_at_pin.off");
        }

        @Override
        public void onPress() {
            landAtPin = !landAtPin;
            setMessage(label(landAtPin));
            showLabel();
        }

        @Override
        public void playDownSound(SoundManager sounds) {
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.LEVER_CLICK, 1.4F, 0.5F));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Look look = look();
            PanelArt.drawTile(graphics, getX(), getY(), getRight(), getBottom(), PanelArt.AMBER, look, landAtPin);
            int sink = look == Look.PRESSED ? 1 : 0;
            pictures().landAtPin().draw(graphics, getX() + (width - Icon.SIZE) / 2, getY() + (height - Icon.SIZE) / 2 + sink, 1, true);
        }
    }
}
