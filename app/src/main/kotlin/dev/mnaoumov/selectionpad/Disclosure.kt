package dev.mnaoumov.selectionpad

/**
 * Which screen the launcher opens on: the consent step, with the full accessibility disclosure, or
 * the normal screen, with the disclosure one tap away (owner, 2026-11-11).
 *
 * **Play requires the disclosure before the access is granted, not after.** Its prominent-disclosure
 * rule for an accessibility app that is not an `isAccessibilityTool` asks for the text during normal
 * use, in the app, ahead of the grant, with an affirmative choice. So the consent step shows it in
 * full and is never a collapsed link. Nothing asks for it on every later visit, so once the user has
 * agreed, or has the service on (the grant has happened, perhaps through an older build that
 * recorded no consent), the launcher opens on its normal screen.
 */
internal object Disclosure {

  enum class Screen {
    /** The full disclosure, with "I understand" and "No thanks". */
    CONSENT,

    /** The pad's controls, with a link that shows the disclosure on request. */
    MAIN,
  }

  fun screen(consentGiven: Boolean, serviceEnabled: Boolean): Screen =
    if (consentGiven || serviceEnabled) Screen.MAIN else Screen.CONSENT
}
