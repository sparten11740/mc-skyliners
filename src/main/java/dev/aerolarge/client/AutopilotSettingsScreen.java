package dev.aerolarge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotBlockEntity.Lateral;
import dev.aerolarge.block.AutopilotBlockEntity.Phase;
import dev.aerolarge.block.AutopilotBlockEntity.Settings;
import dev.aerolarge.block.OpenAutopilotScreen;
import dev.aerolarge.block.SetAutopilot;
import dev.aerolarge.block.UnstickAirliner;
import dev.aerolarge.client.PanelArt.ArrowButton;
import dev.aerolarge.client.PanelArt.CloseButton;
import dev.aerolarge.client.PanelArt.Icon;
import dev.aerolarge.client.PanelArt.IconButton;
import dev.aerolarge.client.PanelArt.Look;
import dev.aerolarge.client.PanelArt.ValueBox;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The autopilot's settings, opened by using the autopilot or its screen on the flight deck: a panel in the flight
 * deck's glass-cockpit colours (see {@link PanelArt}) on which every setting and button has a picture and a colour of
 * its own, so that a player who cannot read yet can fly too. In the title bar, what the autopilot is doing, with a
 * lamp: green while engaged, amber while off, dark until the airliner is assembled. Under it, on glass, how the
 * airliner flies now and what the autopilot is set to fly. Then the settings: hold a heading, circle left or right, or
 * fly to a pin, then the heading, altitude, speed and circle radius, each slid, stepped with the arrows either side of
 * its slider or typed into the box after it, and kept to what the autopilot flies. Along the bottom the big buttons: switched off,
 * engaging flies the settings (taking off first on the ground); engaged, they can be applied, or the autopilot
 * switched off. In the air it can also be told to land; stuck on the ground with its engines running, the airliner can
 * be hopped free in that button's place instead, for as long as it is held. The server checks it all again (see
 * {@link SetAutopilot} and {@link UnstickAirliner}). The game goes on behind it, and it closes only with the close button
 * in its title bar (or once the autopilot is gone), so it can stay open to watch the autopilot fly. Set to fly to a
 * pin, the rows give way to a map to drop the pin on (see {@link DestinationMap}).
 */
public class AutopilotSettingsScreen extends Screen {
    /** The panel's size, the space inside its bezel, and the width of what is on its face. */
    private static final int WIDTH = 328;
    private static final int HEIGHT = 232;
    private static final int PADDING = 8;
    private static final int CONTENT = WIDTH - 2 * (PanelArt.BORDER + PADDING);
    /** From the panel's top: the glass, its lines of how it flies, the first row of settings and the big buttons. */
    private static final int GLASS_Y = 34;
    private static final int GLASS_HEIGHT = 24;
    private static final int NOW_Y = GLASS_Y + 4;
    private static final int SET_Y = GLASS_Y + 14;
    private static final int ROWS_Y = 64;
    private static final int ACTIONS_Y = 182;
    /** Inside the glass: the space before a line of how it flies, and how far along it its values start. */
    private static final int GLASS_INSET = 4;
    private static final int VALUE_X = 50;
    /** A row of settings: how far apart, a control's height, and across it the picture, arrows, slider and box. */
    private static final int ROW = 23;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ICON_GAP = 4;
    private static final int STEP_WIDTH = 14;
    private static final int STEP_GAP = 2;
    private static final int BOX_GAP = 6;
    private static final int BOX_WIDTH = 40;
    private static final int SLIDER_WIDTH = CONTENT - Icon.SIZE - ICON_GAP - 2 * STEP_WIDTH - 2 * STEP_GAP - BOX_GAP - BOX_WIDTH;
    /** The modes' pictures: their width, the space between them, and before the name of the one chosen. */
    private static final int SEGMENT = 32;
    private static final int SEGMENT_GAP = 2;
    private static final int MODE_NAME_GAP = 8;
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
    /** The steps a slider clicks into; typed in, any whole number goes. */
    private static final int HEADING_DETENT = 5;
    private static final int ALTITUDE_DETENT = 5;
    private static final int SPEED_DETENT = 1;
    private static final int RADIUS_DETENT = 10;
    /** What can be typed into a setting: a whole number of up to four digits, perhaps below zero. */
    private static final Pattern NUMBER = Pattern.compile("-?\\d{0,4}");
    private static final int MAX_LENGTH = 5;
    /** What is typed: light, or red while it cannot be read or is out of range (it is brought into range when sent). */
    private static final int TYPED = 0xFFE0E0E0;
    private static final int UNREADABLE = 0xFFFF5555;
    /** The highest compass bearing; bearings go round past it. */
    private static final int LAST_BEARING = 359;

    private static final Icon AIRLINER = Icon.of("autopilot/airliner");
    private static final Icon TAKE_OFF = Icon.of("autopilot/take_off");
    private static final Icon ENGAGE = Icon.of("autopilot/engage");
    private static final Icon APPLY = Icon.of("autopilot/apply");
    private static final Icon DISENGAGE = Icon.of("autopilot/disengage");
    private static final Icon LAND = Icon.of("autopilot/land");
    private static final Icon UNSTUCK = Icon.of("autopilot/unstuck");
    private static final Icon STRAIGHT = Icon.of("autopilot/hold_heading");
    private static final Icon CIRCLE_LEFT = Icon.of("autopilot/orbit_left");
    private static final Icon CIRCLE_RIGHT = Icon.of("autopilot/orbit_right");
    private static final Icon PIN = Icon.of("autopilot/destination");
    private static final Lateral[] MODES = Lateral.values();
    private static final Component MODE = Component.translatable("screen.aerolarge.autopilot_settings.mode");

    private final BlockPos pos;
    /** Whether engaging takes off, so the button says so. */
    private final boolean takeoff;
    private Lateral lateral;
    private final Setting heading;
    private final Setting altitude;
    private final Setting speed;
    private final Setting radius;
    /** The panel's top left corner on the screen. */
    private int left;
    private int top;
    private ModeSelector mode;
    private IconButton engage;
    private IconButton apply;
    private IconButton disengage;
    private IconButton land;
    private IconButton unstuck;
    /** Whether Unstuck is held down, keeping the control jets firing. */
    private boolean hopping;
    /** The map the pin is dropped on, in the rows' place while the mode flies to it. */
    private final DestinationMap map;

    private AutopilotSettingsScreen(OpenAutopilotScreen opened) {
        super(Component.translatable("screen.aerolarge.autopilot_settings.title"));
        Settings settings = opened.settings();
        pos = opened.pos();
        takeoff = opened.takeoff();
        lateral = settings.lateral();
        heading = new Setting("heading", PanelArt.CYAN, settings.heading(), 0, LAST_BEARING, AutopilotBlockEntity.HEADING_STEP, HEADING_DETENT, true);
        altitude = new Setting("altitude", PanelArt.GREEN, settings.altitude(), opened.minAltitude(), opened.maxAltitude(), AutopilotBlockEntity.ALTITUDE_STEP, ALTITUDE_DETENT, false);
        speed = new Setting("speed", PanelArt.AMBER, settings.speed(), AutopilotBlockEntity.MIN_SPEED, AutopilotBlockEntity.MAX_SPEED, AutopilotBlockEntity.SPEED_STEP, SPEED_DETENT, false);
        radius = new Setting("radius", PanelArt.MAGENTA, settings.radius(), AutopilotBlockEntity.MIN_RADIUS, AutopilotBlockEntity.MAX_RADIUS, AutopilotBlockEntity.RADIUS_STEP, RADIUS_DETENT, false);
        BlockPos pin = lateral == Lateral.DESTINATION ? settings.destination() : null;
        map = new DestinationMap(CONTENT, rowY(rows().size()) - rowY(1) + CONTROL_HEIGHT, pos, this::autopilot, pin, radius::value);
    }

    /** Opens the settings as the server sent them, over whatever else is open. */
    public static void open(OpenAutopilotScreen opened) {
        Minecraft.getInstance().setScreen(new AutopilotSettingsScreen(opened));
    }

    /** The rows of settings under the mode, top to bottom. */
    private List<Setting> rows() {
        return List.of(heading, altitude, speed, radius);
    }

    private int rowY(int row) {
        return top + ROWS_Y + row * ROW;
    }

    private static Component modeName(Lateral mode) {
        return Component.translatable("screen.aerolarge.autopilot_settings.mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    /** A mode's picture and colour: straight ahead in the heading's cyan, round a circle in the radius's magenta. */
    private static Icon modeIcon(Lateral mode) {
        return switch (mode) {
            case HEADING -> STRAIGHT;
            case ORBIT_LEFT -> CIRCLE_LEFT;
            case ORBIT_RIGHT -> CIRCLE_RIGHT;
            case DESTINATION -> PIN;
        };
    }

    private static int modeColour(Lateral mode) {
        return mode == Lateral.HEADING ? PanelArt.CYAN : PanelArt.MAGENTA;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int x = left + PanelArt.BORDER + PADDING;
        mode = addRenderableWidget(new ModeSelector(x, rowY(0)));
        List<Setting> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).place(x, rowY(i + 1));
        }
        map.setPosition(x, rowY(1));
        addRenderableWidget(map);

        // Along the bottom in three places: engage across the first two, or apply and disengage in them once engaged;
        // then land, or hop the airliner free. Closing is the close button in the title bar.
        int buttonWidth = (CONTENT - 2 * ACTION_GAP) / 3;
        int buttonY = top + ACTIONS_Y;
        Component engageLabel = Component.translatable(takeoff ? "screen.aerolarge.autopilot_settings.take_off" : "screen.aerolarge.autopilot_settings.engage");
        engage = addRenderableWidget(new IconButton(x, buttonY, 2 * buttonWidth + ACTION_GAP, ACTION_HEIGHT, engageLabel,
            takeoff ? TAKE_OFF : ENGAGE, PanelArt.GREEN, true, () -> send(SetAutopilot.Action.FLY)));
        apply = addRenderableWidget(new IconButton(x, buttonY, buttonWidth, ACTION_HEIGHT, Component.translatable("screen.aerolarge.autopilot_settings.apply"),
            APPLY, PanelArt.GREEN, false, () -> send(SetAutopilot.Action.FLY)));
        disengage = addRenderableWidget(new IconButton(x + buttonWidth + ACTION_GAP, buttonY, buttonWidth, ACTION_HEIGHT,
            Component.translatable("screen.aerolarge.autopilot_settings.disengage"), DISENGAGE, PanelArt.RED, false, () -> send(SetAutopilot.Action.OFF)));
        int lastX = x + 2 * (buttonWidth + ACTION_GAP);
        land = addRenderableWidget(new IconButton(lastX, buttonY, buttonWidth, ACTION_HEIGHT,
            Component.translatable("screen.aerolarge.autopilot_settings.land"), LAND, PanelArt.AMBER, false, () -> send(SetAutopilot.Action.LAND)));
        unstuck = addRenderableWidget(new IconButton(lastX, buttonY, buttonWidth, ACTION_HEIGHT,
            Component.translatable("screen.aerolarge.autopilot_settings.unstuck"), UNSTUCK, PanelArt.CYAN, true, this::unstick));
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

    private static Direction nose(AutopilotBlockEntity autopilot) {
        return autopilot.getBlockState().getValue(AutopilotBlock.FACING);
    }

    /** Shows the buttons for the autopilot as it is now, and the settings the chosen mode flies. */
    private void refresh() {
        AutopilotBlockEntity autopilot = autopilot();
        boolean assembled = autopilot != null && SableClient.attitude(autopilot, nose(autopilot), 1) != null;
        Phase phase = autopilot != null ? autopilot.phase() : Phase.OFF;
        boolean on = phase != Phase.OFF;
        // Set to fly to a pin, it needs one dropped first.
        boolean toPin = lateral == Lateral.DESTINATION;
        boolean pinned = !toPin || map.pin() != null;
        engage.visible = !on;
        engage.active = assembled && pinned;
        apply.visible = on;
        // Once down after landing it only brakes to a stop.
        apply.active = phase != Phase.ROLLOUT && pinned;
        disengage.visible = on;
        // Only in the air, and not while it lands already.
        land.active = assembled && (on ? phase == Phase.CLIMB || phase == Phase.DESCENT || phase == Phase.CRUISE : !takeoff);
        // Stuck on the ground with its engines running, where landing is no use, it can be hopped free instead.
        unstuck.visible = hopping || assembled && autopilot.stuck();
        land.visible = !unstuck.visible;
        heading.setActive(lateral == Lateral.HEADING);
        radius.setActive(lateral != Lateral.HEADING);
        // Flying to a pin, the map takes the rows' place; the height, speed and circle stay as they were set.
        map.visible = toPin;
        for (Setting row : rows()) {
            row.setVisible(!toPin);
        }
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

    /** Sends the settings as they stand: to fly them, to land or with the autopilot switched off. */
    private void send(SetAutopilot.Action action) {
        BlockPos pin = map.pin() != null ? map.pin() : BlockPos.ZERO;
        Settings settings = new Settings(speed.value(), altitude.value(), heading.value(), lateral, radius.value(), pin);
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
        IconButton fly = apply.visible ? apply : engage;
        if (enter && fly.active) {
            send(SetAutopilot.Action.FLY);
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
        PanelArt.drawTitleBar(graphics, font, AIRLINER, title, left, top, left + WIDTH);
        int x = left + PanelArt.BORDER + PADDING;
        PanelArt.drawGlass(graphics, x, top + GLASS_Y, x + CONTENT, top + GLASS_Y + GLASS_HEIGHT);
        for (Setting row : rows()) {
            row.drawIcon(graphics);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // What it is doing, as its screen on the flight deck says it, by a lamp, at the end of the title bar.
        AutopilotBlockEntity autopilot = autopilot();
        Level level = autopilot != null ? autopilot.getLevel() : null;
        SableClient.Attitude attitude = autopilot != null ? SableClient.attitude(autopilot, nose(autopilot), partialTick) : null;
        boolean on = AutopilotScreenRenderer.engaged(autopilot);
        Component state = AutopilotScreenRenderer.state(autopilot, attitude);
        int stateColour = attitude == null ? PanelArt.GREY : on ? PanelArt.GREEN : PanelArt.AMBER;
        int stateX = left + WIDTH - PanelArt.BORDER - CLOSE_INSET - CloseButton.SIZE - STATE_GAP - font.width(state);
        int titleMiddle = top + PanelArt.BORDER + PanelArt.TITLE_HEIGHT / 2;
        graphics.drawString(font, state, stateX, titleMiddle - PanelArt.GLYPH_HEIGHT / 2, stateColour, false);
        float brightness = attitude == null ? LAMP_OUT : on ? Mth.lerp(PanelArt.pulse(LAMP_PERIOD), LAMP_LOW, 1F) : 1F;
        PanelArt.drawLamp(graphics, stateX - LAMP_GAP, titleMiddle, stateColour, brightness);

        // On the glass, how the airliner flies, and what it is set to fly or what engaging does.
        if (attitude != null && level != null) {
            Vec3 here = SableClient.toWorld(level, Vec3.atCenterOf(pos));
            line(graphics, "now", AutopilotScreenRenderer.now(attitude, here), NOW_Y, PanelArt.WHITE);
            if (on) {
                line(graphics, "set", AutopilotScreenRenderer.targets(autopilot), SET_Y, PanelArt.MAGENTA);
            } else {
                String hint = takeoff ? "screen.aerolarge.autopilot_settings.take_off_hint" : "screen.aerolarge.autopilot_settings.engage_hint";
                graphics.drawString(font, Component.translatable(hint), glassTextX(), top + SET_Y, PanelArt.GREY, false);
            }
        }

        // The mode chosen, by name, after its pictures.
        int modeY = rowY(0) + (CONTROL_HEIGHT - PanelArt.GLYPH_HEIGHT) / 2;
        graphics.drawString(font, modeName(lateral), mode.getRight() + MODE_NAME_GAP, modeY, modeColour(lateral), false);
        for (Setting row : rows()) {
            if (row.iconHovered(mouseX, mouseY)) {
                setTooltipForNextRenderPass(row.tooltip());
            }
        }
    }

    /** Where a line on the glass starts. */
    private int glassTextX() {
        return left + PanelArt.BORDER + PADDING + GLASS_INSET;
    }

    /** A line of how it flies: its name, then its values. */
    private void line(GuiGraphics graphics, String key, Component values, int y, int colour) {
        int x = glassTextX();
        graphics.drawString(font, Component.translatable("screen.aerolarge.autopilot_settings." + key), x, top + y, PanelArt.GREY, false);
        graphics.drawString(font, values, x + VALUE_X, top + y, colour, false);
    }

    /**
     * The mode: a picture for each, the one chosen lit, its name as the label. A click picks the picture clicked;
     * pressed from the keyboard or from code, or scrolled, it steps to the next (back with shift), as the cycle button
     * it took the place of did.
     */
    private final class ModeSelector extends PanelArt.PanelButton {
        ModeSelector(int x, int y) {
            super(x, y, MODES.length * (SEGMENT + SEGMENT_GAP) - SEGMENT_GAP, CONTROL_HEIGHT, modeName(lateral));
        }

        @Override
        public void onPress() {
            step(hasShiftDown() ? -1 : 1);
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            choose(MODES[segment(mouseX)]);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (scrollY != 0) {
                step(scrollY > 0 ? -1 : 1);
            }
            return true;
        }

        @Override
        public void playDownSound(SoundManager sounds) {
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.LEVER_CLICK, 1.4F, 0.5F));
        }

        private void step(int by) {
            choose(MODES[Math.floorMod(lateral.ordinal() + by, MODES.length)]);
        }

        private void choose(Lateral chosen) {
            lateral = chosen;
            setMessage(modeName(chosen));
            refresh();
        }

        /** The picture at a place across the selector (the one before, in the gap after it). */
        private int segment(double mouseX) {
            return Mth.clamp((int) ((mouseX - getX()) / (SEGMENT + SEGMENT_GAP)), 0, MODES.length - 1);
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return wrapDefaultNarrationMessage(CommonComponents.optionNameValue(MODE, getMessage()));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int pointedAt = isHovered() ? segment(mouseX) : -1;
            for (int i = 0; i < MODES.length; i++) {
                Lateral each = MODES[i];
                int x0 = getX() + i * (SEGMENT + SEGMENT_GAP);
                Look look = look(i == pointedAt);
                if (look == Look.IDLE && isFocused() && each == lateral) {
                    look = Look.HOT;
                }
                PanelArt.drawTile(graphics, x0, getY(), x0 + SEGMENT, getBottom(), modeColour(each), look, each == lateral);
                int sink = look == Look.PRESSED ? 1 : 0;
                modeIcon(each).draw(graphics, x0 + (SEGMENT - Icon.SIZE) / 2, getY() + (height - Icon.SIZE) / 2 + sink, 1, true);
            }
            if (pointedAt >= 0) {
                AutopilotSettingsScreen.this.setTooltipForNextRenderPass(modeName(MODES[pointedAt]));
            }
        }
    }

    /**
     * One setting: its picture, a slider between arrows that step it down and up, and a box it can be typed into, all
     * kept to its range and to each other; a bearing goes round.
     */
    private final class Setting {
        private final String key;
        private final Icon icon;
        private final int colour;
        private final int min;
        private final int max;
        private final int step;
        private final int detent;
        private final boolean bearing;
        /** What it was last set to, for when what is typed cannot be read. */
        private int value;
        private boolean active = true;
        private boolean visible = true;
        /** Where its picture is. */
        private int iconX;
        private int iconY;
        @Nullable
        private Knob slider;
        @Nullable
        private ValueBox box;
        @Nullable
        private ArrowButton down;
        @Nullable
        private ArrowButton up;

        Setting(String key, int colour, int value, int min, int max, int step, int detent, boolean bearing) {
            this.key = key;
            this.icon = Icon.of("autopilot/" + key);
            this.colour = colour;
            this.min = min;
            this.max = max;
            this.step = step;
            this.detent = detent;
            this.bearing = bearing;
            this.value = fit(value);
        }

        /** Lays its controls out along a row from x, keeping what was typed into them before. */
        void place(int x, int y) {
            value = value();
            iconX = x;
            iconY = y + (CONTROL_HEIGHT - Icon.SIZE) / 2;
            int downX = x + Icon.SIZE + ICON_GAP;
            int sliderX = downX + STEP_WIDTH + STEP_GAP;
            int upX = sliderX + SLIDER_WIDTH + STEP_GAP;
            down = addRenderableWidget(new ArrowButton(downX, y, STEP_WIDTH, CONTROL_HEIGHT, Component.translatable("screen.aerolarge.autopilot_settings.down", step),
                false, colour, () -> show(value() - step)));
            slider = addRenderableWidget(new Knob(this, sliderX, y, position(value)));
            up = addRenderableWidget(new ArrowButton(upX, y, STEP_WIDTH, CONTROL_HEIGHT, Component.translatable("screen.aerolarge.autopilot_settings.up", step),
                true, colour, () -> show(value() + step)));
            ValueBox typed = addRenderableWidget(new ValueBox(font, upX + STEP_WIDTH + BOX_GAP, y, BOX_WIDTH, CONTROL_HEIGHT, name(), colour));
            typed.setMaxLength(MAX_LENGTH);
            typed.setFilter(text -> NUMBER.matcher(text).matches());
            typed.setValue(format(value));
            typed.setResponder(text -> {
                typed.setTextColor(readable(text) ? TYPED : UNREADABLE);
                if (slider != null) {
                    slider.moveTo(position(value()));
                }
            });
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

        private void show(int set) {
            value = fit(set);
            if (box != null) {
                box.setValue(format(value));
            }
        }

        /** Sets it from where its slider is, in the slider's detents. */
        private void slid(double position) {
            int set = Mth.clamp((int) Math.round((min + position * (max - min)) / detent) * detent, min, max);
            if (set != value()) {
                show(set);
            }
        }

        /** Where a value sits along the slider. */
        private double position(int set) {
            return max > min ? (double) (set - min) / (max - min) : 0;
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

        private String format(int set) {
            return bearing ? String.format(Locale.ROOT, "%03d", set) : Integer.toString(set);
        }

        /** Greys the setting out while the mode chosen does not fly it. */
        void setActive(boolean active) {
            this.active = active;
            if (box != null && slider != null && down != null && up != null) {
                box.setEditable(active);
                box.active = active;
                slider.active = active;
                down.active = active;
                up.active = active;
            }
        }

        private Component name() {
            return Component.translatable("screen.aerolarge.autopilot_settings." + key);
        }

        /** Its name and its value, for its slider to be narrated by. */
        private Component describe() {
            return CommonComponents.optionNameValue(name(), Component.literal(format(value())));
        }

        /** Shows the setting's row, or hides it while the map takes its place. */
        void setVisible(boolean visible) {
            this.visible = visible;
            if (box != null && slider != null && down != null && up != null) {
                box.visible = visible;
                slider.visible = visible;
                down.visible = visible;
                up.visible = visible;
            }
        }

        void drawIcon(GuiGraphics graphics) {
            if (visible) {
                icon.draw(graphics, iconX, iconY, 1, active);
            }
        }

        boolean iconHovered(int mouseX, int mouseY) {
            return visible && mouseX >= iconX && mouseX < iconX + Icon.SIZE && mouseY >= iconY && mouseY < iconY + Icon.SIZE;
        }

        /** Its name and its range, shown over its picture. */
        Component tooltip() {
            return CommonComponents.optionNameValue(name(), Component.translatable("screen.aerolarge.autopilot_settings.range." + key, format(min), format(max)));
        }
    }

    /**
     * A setting's slider. Slid, it sets the setting in the steps it clicks into; the arrow keys move it a step at a
     * time, where the slider's own would move it a pixel, too little to reach the next step.
     */
    private static final class Knob extends PanelArt.Slider {
        private final Setting setting;

        Knob(Setting setting, int x, int y, double position) {
            super(x, y, SLIDER_WIDTH, CONTROL_HEIGHT, setting.name(), setting.colour, position);
            this.setting = setting;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(setting.describe());
        }

        @Override
        protected void applyValue() {
            setting.slid(position());
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            // A slider greyed out while it has the keyboard would still take the keys.
            if (!active) {
                return false;
            }
            int before = setting.value();
            boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
            if (handled && (keyCode == InputConstants.KEY_LEFT || keyCode == InputConstants.KEY_RIGHT)) {
                setting.show(before + (keyCode == InputConstants.KEY_LEFT ? -setting.detent : setting.detent));
            }
            return handled;
        }
    }
}
