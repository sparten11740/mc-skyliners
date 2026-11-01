package dev.skyliners.airframe;

/**
 * How the fuselage's skin is cut to its curve (see {@link dev.skyliners.block.ShellGeometry}). Fitted cuts each
 * block by the surface where it passes through that block, as closely as one plane can, so neighbouring blocks only
 * roughly meet; snapped cuts every block through corners and the middles of edges only, so neighbours meet exactly
 * in a kit of 45 degree and one-in-two slopes, and cuts the inside of the nose's skin to the curve as well.
 */
public enum ShellStyle {
    FITTED,
    SNAPPED
}
