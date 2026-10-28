package dev.aerolarge;

import dev.aerolarge.client.BurnerSmoke;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings: what each player feels and sees aboard the airliner, and how much their computer draws for it. */
public final class AerolargeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ModConfigSpec.BooleanValue PILOT_HUD;
    public static final ModConfigSpec.DoubleValue PILOT_HUD_SCALE;
    public static final ModConfigSpec.BooleanValue TAIL_CAMERA_FEED;
    public static final ModConfigSpec.IntValue TAIL_CAMERA_FPS;
    public static final ModConfigSpec.IntValue TAIL_CAMERA_RENDER_DISTANCE;
    public static final ModConfigSpec.IntValue TAIL_CAMERA_RESOLUTION;
    public static final ModConfigSpec.BooleanValue NIGHT_LIGHTS;
    public static final ModConfigSpec.BooleanValue DISCO_LIGHTS;
    public static final ModConfigSpec.BooleanValue FLOODLIGHT_LIGHT;
    public static final ModConfigSpec.BooleanValue CLOUDLESS_CABIN;
    public static final ModConfigSpec.BooleanValue FLYBY_CAMERA;
    public static final ModConfigSpec.BooleanValue HULL_AMBIENCE;
    public static final ModConfigSpec.BooleanValue GREAT_SHADOW;
    public static final ModConfigSpec.BooleanValue MISTY_WINDOWS;
    public static final ModConfigSpec.EnumValue<BurnerSmoke> BURNER_SMOKE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CAMERA_SHAKE = builder
            .comment("Shake the camera aboard the airliner: rumbling down the runway, thumping onto it, swaying in flight.")
            .define("cameraShake", true);
        PILOT_HUD = builder
            .comment("Show the pilot's head-up display in a pilot helmet's visor on the flight deck of an assembled airliner: the",
                "horizon and the flight path round the crosshair, the speed by the left edge, the height by the right edge and the",
                "heading at the top, with what the autopilot does and the speed, height and heading it is set to.")
            .define("pilotHud", true);
        PILOT_HUD_SCALE = builder
            .comment("How big the pilot's head-up display is drawn: 1 is about half the size of the rest of the HUD, and it grows",
                "with the GUI scale as the rest of the HUD does.")
            .defineInRange("pilotHudScale", 1.0, 0.5, 2.0);
        TAIL_CAMERA_FEED = builder
            .comment("Show the live picture from the camera on top of the fin on the flight deck's tail camera display. It draws",
                "the world a second time, small, while a display is in sight; turn it off if that costs too much. It is never",
                "drawn with a shader pack or Fabulous graphics: the display shows its test card then.")
            .define("tailCameraFeed", true);
        TAIL_CAMERA_FPS = builder
            .comment("How many times a second, at most, the tail camera's picture is drawn anew. Each picture draws the world a",
                "second time while the game waits; 10 moves smoothly enough for a camera screen.")
            .defineInRange("tailCameraFps", 10, 1, 30);
        TAIL_CAMERA_RENDER_DISTANCE = builder
            .comment("How far the tail camera's picture reaches, in chunks, before the fog closes in. It never reaches further",
                "than your own render distance: the world is only loaded that far round you. At 12 the whole airliner stands",
                "clear of the fog at the default scale (2); at scale 3 that takes 18, at scale 4 24. The further it reaches, the",
                "more each picture costs: what the camera sees is worked out afresh every time the picture is drawn, while the",
                "game waits, and there is about four times as much to work out at twice the distance. Lower it, or",
                "tailCameraFps, if the picture makes the game stutter.")
            .defineInRange("tailCameraRenderDistance", 12, 2, 32);
        TAIL_CAMERA_RESOLUTION = builder
            .comment("How many pixels tall the tail camera's picture is drawn; it is a little over twice as wide.")
            .defineInRange("tailCameraResolution", 144, 72, 360);
        NIGHT_LIGHTS = builder
            .comment("With the cabin lights set for the night, let the lights near you cast a soft blue light round the cabin, as an",
                "airliner's mood lighting does, through Veil (which comes with Sable). While a cabin at night is near, every frame",
                "is drawn with Veil's extra buffers and its lights; turn it off if that costs too much. Never with Iris installed:",
                "the night lights only glow blue then.")
            .define("nightLights", true);
        DISCO_LIGHTS = builder
            .comment("Let a disco ball that is on cast spots of coloured light that sweep round the room as it turns, through Veil",
                "(which comes with Sable). While a disco ball is near, every frame is drawn with Veil's extra buffers and its",
                "lights; turn it off if that costs too much. Never with Iris installed: the ball then only turns and glints, and",
                "lights the room as a lamp does.")
            .define("discoLights", true);
        FLOODLIGHT_LIGHT = builder
            .comment("Let a zeppelin's lit floodlights cast real light on the land under her, through Veil (which comes with Sable).",
                "While one is near, every frame is drawn with Veil's extra buffers and its lights; turn it off if that costs too",
                "much. Off, or with Iris installed, a pool of light is drawn on the ground under each instead.")
            .define("floodlightLight", true);
        CLOUDLESS_CABIN = builder
            .comment("Keep the clouds out of the airliner's cabin: while you are inside its hull and the cloud layer passes the",
                "airliner, the clouds are not drawn for you, so no slab of cloud cuts through the floor, the walls and the seats.",
                "With Fast or Fancy graphics the windows never show the clouds behind them anyway; with Fabulous graphics the",
                "clouds outside the windows go too while the cloud layer passes. Outside the hull the clouds are always drawn.")
            .define("cloudlessCabin", true);
        FLYBY_CAMERA = builder
            .comment("Among a zeppelin's outside cameras on her passenger screens, the flyby camera, slowly circling the whole ship from",
                "well out and a little above her. Off, her screens look through her bow and belly cameras only.")
            .define("flybyCamera", true);
        HULL_AMBIENCE = builder
            .comment("Aboard a zeppelin, hear her hull: the hollow boom of the wind over her cover, louder the faster she flies, and",
                "now and then a girder groaning somewhere round you.")
            .define("hullAmbience", true);
        GREAT_SHADOW = builder
            .comment("Draw a flying zeppelin's great shadow on the land under her, cast from where the sun is: sharp under her at noon,",
                "thrown long and soft as the sun sinks, gone at night and in rain. Worked out every other tick over the ground she",
                "could shade; turn it off if that costs too much.")
            .define("greatShadow", true);
        MISTY_WINDOWS = builder
            .comment("Aboard a zeppelin flying through cloud or rain, mist her promenade windows over on the inside, to be drawn on by",
                "holding use with an empty hand on a pane. Each player's mist and drawing are their own.")
            .define("mistyWindows", true);
        BURNER_SMOKE = builder
            .comment("How much smoke Create Aeronautics' hot air burners puff, a zeppelin's among them. PARTICLES: as much as the game's",
                "Particles setting allows, as other particles (all at All, two puffs in three at Decreased, none at Minimal). ALL:",
                "every puff, whatever the Particles setting, as Aeronautics puffs them; a full-size zeppelin's burners keep some",
                "1,600 aloft at once, a share of each frame. NONE: no smoke. The burners' flames and embers are drawn either way.")
            .defineEnum("burnerSmoke", BurnerSmoke.PARTICLES);
        SPEC = builder.build();
    }

    private AerolargeClientConfig() {
    }
}
