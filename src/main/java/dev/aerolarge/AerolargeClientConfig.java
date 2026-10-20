package dev.aerolarge;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings: what each player feels and sees aboard the airliner, and how much their computer draws for it. */
public final class AerolargeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ModConfigSpec.BooleanValue PILOT_HUD;
    public static final ModConfigSpec.DoubleValue PILOT_HUD_SCALE;
    public static final ModConfigSpec.BooleanValue TAIL_CAMERA_FEED;
    public static final ModConfigSpec.IntValue TAIL_CAMERA_FPS;
    public static final ModConfigSpec.IntValue TAIL_CAMERA_RESOLUTION;
    public static final ModConfigSpec.BooleanValue NIGHT_LIGHTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CAMERA_SHAKE = builder
            .comment("Shake the camera aboard the airliner: rumbling down the runway, thumping onto it, swaying in flight.")
            .define("cameraShake", true);
        PILOT_HUD = builder
            .comment("Show the pilot's head-up display in a pilot helmet's visor on the flight deck of an assembled airliner: the",
                "horizon and the flight path round the crosshair, the speed left of them, the height right of them and the heading",
                "above, with what the autopilot does and the speed, height and heading it is set to.")
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
            .comment("How many times a second, at most, the tail camera's picture is drawn anew.")
            .defineInRange("tailCameraFps", 15, 1, 30);
        TAIL_CAMERA_RESOLUTION = builder
            .comment("How many pixels tall the tail camera's picture is drawn; it is a little over twice as wide.")
            .defineInRange("tailCameraResolution", 144, 72, 360);
        NIGHT_LIGHTS = builder
            .comment("With the cabin lights set for the night, let the lights near you cast a soft blue light round the cabin, as an",
                "airliner's mood lighting does, through Veil (which comes with Sable). While a cabin at night is near, every frame",
                "is drawn with Veil's extra buffers and its lights; turn it off if that costs too much. Never with Iris installed:",
                "the night lights only glow blue then.")
            .define("nightLights", true);
        SPEC = builder.build();
    }

    private AerolargeClientConfig() {
    }
}
