package dev.aerolarge.autopilot;

/**
 * What an autopilot flies: the airliner, on its wings and jets, or the zeppelin, on its hot air and propellers (see
 * {@link ZeppelinPilot}). Each flies within speeds of its own, in blocks a second: the airliner falls out of the sky
 * below its slowest, while the zeppelin can stop and hover. Its speed dial shows green up to where it cruises
 * comfortably, amber faster and red near its fastest.
 */
public enum Craft {
    AIRLINER(AutopilotBlockEntity.MIN_SPEED, AutopilotBlockEntity.MAX_SPEED, 60, 100),
    ZEPPELIN(0, 25, 20, 24);

    public final int minSpeed;
    public final int maxSpeed;
    /** Where its speed dial turns amber and where it turns red, in blocks a second. */
    public final int amberFrom;
    public final int redFrom;

    Craft(int minSpeed, int maxSpeed, int amberFrom, int redFrom) {
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.amberFrom = amberFrom;
        this.redFrom = redFrom;
    }
}
