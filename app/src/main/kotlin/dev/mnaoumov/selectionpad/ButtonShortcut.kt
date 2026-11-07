package dev.mnaoumov.selectionpad

import android.content.ComponentName

/**
 * Whether the user has given this service an accessibility shortcut (the floating button, the
 * gesture or the volume keys).
 *
 * The service does not request the accessibility button: with `FLAG_REQUEST_ACCESSIBILITY_BUTTON`
 * in its static config, Android ties the service's enabled state to its shortcut, so the floating
 * button was forced on and removing it switched the service off (see
 * `accessibility_service_config.xml`). Without the flag a shortcut turns the whole service off and
 * on, which a sideloaded build may not get back under ECM. So the launcher reads the assignment back
 * and, when there is one, warns and offers the page to remove it. No app can write that setting.
 */
object ButtonShortcut {
  /**
   * The `Settings.Secure` keys whose value lists the shortcut targets, as colon-separated flattened
   * component names. The constants are `@hide`, so they are spelled out. The gesture key is the
   * two-finger swipe on gesture navigation, absent on older platforms, which reads as empty. The
   * last is the volume-keys shortcut, which delivers the same click to a service that requests the
   * button.
   */
  val TARGET_KEYS = listOf(
    "accessibility_button_targets",
    "accessibility_gesture_targets",
    "accessibility_shortcut_target_service",
  )

  /**
   * Whether any of [targetLists] names the service [packageName]/[className], in the short
   * (`pkg/.Cls`) or the full form. Parsed by hand rather than by `ComponentName`, so a JVM test can
   * run it.
   */
  fun assigned(packageName: String, className: String, targetLists: List<String?>): Boolean =
    targetLists.filterNotNull().flatMap { it.split(':') }.any { entry ->
      val parts = entry.trim().split('/', limit = 2)
      if (parts.size != 2 || parts[0] != packageName) return@any false
      val cls = if (parts[1].startsWith('.')) packageName + parts[1] else parts[1]
      cls == className
    }

  fun assigned(service: ComponentName, targetLists: List<String?>): Boolean =
    assigned(service.packageName, service.className, targetLists)
}
