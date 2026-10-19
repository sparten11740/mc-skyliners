package dev.aerolarge.block;

/** A flight deck control that drives relays from its own setting: the autopilot hands back to it when it lets go. */
public interface Control {
    /** Sets the relays it works to what the control is set to now. */
    void resync();
}
