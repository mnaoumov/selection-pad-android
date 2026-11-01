package dev.mnaoumov.selectionpad

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Whether the service is running, switched on but not running, or off, so the tile, the
 * notification and the launcher screen can tell the user which.
 *
 * **Enabled is not running.** Measured on the OnePlus 15 on 2026-10-31: `enabled_accessibility_services`
 * listed the service and `dumpsys accessibility` showed it under Enabled services but not under Bound
 * services, with the app's process alive and nothing crashed. The tile, the notification and the
 * launcher's Show button all found no service and fell back to opening the app with no word of why,
 * which read as the tile opening the app instead of the pad. What unbound it is not known yet;
 * [Diag.record] keeps the service's lifecycle in a file so the next occurrence can be read.
 *
 * **The app cannot get itself bound again.** Measured on the rig on 2026-11-01: flipping one of the
 * app's own components off and on, the accessibility service's included, left an unbound service
 * unbound and an inert bound one untouched. A component left disabled would make Android drop the
 * service from the enabled list, which on an ECM-restricted install the user cannot turn back on. So
 * the toggles say what is wrong and what fixes it: turning the service off and on in Accessibility
 * settings, or restarting the phone, both of which bind every enabled service.
 */
internal object ServiceStatus {

  enum class State {
    /** A connected instance exists. */
    RUNNING,

    /** In the enabled list, but no instance is connected. */
    NOT_RUNNING,

    /** Not in the enabled list: the user has not turned it on, or something turned it off. */
    OFF,
  }

  fun state(enabled: Boolean, connected: Boolean): State = when {
    connected -> State.RUNNING
    enabled -> State.NOT_RUNNING
    else -> State.OFF
  }

  fun state(context: Context): State = state(enabled(context), AscAccessibilityService.running())

  /** Whether the service is in the system's enabled list, which is what a ROM edits when it revokes it. */
  fun enabled(context: Context): Boolean {
    val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
      ?: return false
    val ours = ComponentName(context, AscAccessibilityService::class.java)
    return list.split(':').any { ComponentName.unflattenFromString(it) == ours }
  }
}
