package dev.mnaoumov.selectionpad

/**
 * Whether the pad may be on screen, given what the user asked for and whether the device is locked,
 * kept free of Android types so it can be tested on its own.
 *
 * **An accessibility overlay draws over the lock screen**, so a pad left showing at screen-off is
 * the first thing anyone sees on the keyguard (owner, 2026-10-30). The pad therefore hides while the
 * device is locked, and comes back at unlock only when the user had it showing. The user's choice
 * ([PadSettings.padVisible]) is never touched by a lock, so a pad the user closed stays closed, and a
 * show asked for while locked (the tile can be tapped from the lock screen) waits for the unlock.
 */
internal object LockGate {

  enum class Action { SHOW, HIDE, NONE }

  /** What to do to a pad that [padShowing] now, so that it shows exactly when [padVisible] and not [locked]. */
  fun action(padVisible: Boolean, locked: Boolean, padShowing: Boolean): Action {
    val wanted = padVisible && !locked
    return when {
      wanted && !padShowing -> Action.SHOW
      !wanted && padShowing -> Action.HIDE
      else -> Action.NONE
    }
  }

  /**
   * Whether the device counts as locked: the screen is off, or the keyguard is up. A screen that is
   * on with no keyguard (no lock set, or a trusted device) is unlocked even before `USER_PRESENT`.
   */
  fun locked(interactive: Boolean, keyguardLocked: Boolean): Boolean = !interactive || keyguardLocked
}
