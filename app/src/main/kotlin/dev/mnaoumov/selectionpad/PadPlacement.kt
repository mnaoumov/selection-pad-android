package dev.mnaoumov.selectionpad

/** Which edge a docked pad sits on. */
enum class DockEdge { BOTTOM, TOP }

/**
 * Where the pad's window goes, worked out without a window so it can be tested.
 *
 * The owner's complaint (2026-10-30, the phone test): the docked pad covers the bottom of the screen,
 * and the only way to reach what is under it was to close the pad. Three ways out now, all kept
 * across a restart ([PadSettings]): float it and drag it, flip a docked pad to the top, or minimise
 * it to a small handle. A minimised pad is one small window and nothing else, so every touch beside
 * it reaches the app below (`FLAG_NOT_TOUCH_MODAL`).
 */
object PadPlacement {

  /** The window's vertical anchor. */
  enum class Anchor { TOP, BOTTOM }

  data class Placement(
    /** Full width (a docked pad) or as wide as its content (a floating pad, or any handle). */
    val fullWidth: Boolean,
    val anchor: Anchor,
    /** At the screen's right edge rather than its left: where a minimised docked pad's handle sits. */
    val alignEnd: Boolean,
    /** Placed by the floating position the user dragged it to. */
    val atFloatingPosition: Boolean,
  )

  fun of(mode: PadMode, edge: DockEdge, minimised: Boolean): Placement = when (mode) {
    PadMode.FLOATING -> Placement(
      fullWidth = false,
      anchor = Anchor.TOP,
      alignEnd = false,
      atFloatingPosition = true,
    )
    PadMode.DOCKED -> Placement(
      fullWidth = !minimised,
      anchor = if (edge == DockEdge.TOP) Anchor.TOP else Anchor.BOTTOM,
      // The handle goes to the corner, out of the way of a line's start, which is where reading begins.
      alignEnd = minimised,
      atFloatingPosition = false,
    )
  }

  /**
   * How much room, in dp, the launcher screen leaves at its top and bottom so its first and last
   * lines can scroll clear of the pad. Only a full pad docked at the top needs it at the top. Every
   * other shape keeps it at the bottom, where a floating pad starts and a minimised handle sits.
   */
  fun clearanceDp(mode: PadMode, edge: DockEdge, minimised: Boolean, docked: Int): Pair<Int, Int> =
    if (mode == PadMode.DOCKED && edge == DockEdge.TOP && !minimised) docked to 0 else 0 to docked
}
