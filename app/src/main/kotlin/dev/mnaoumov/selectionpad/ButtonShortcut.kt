package dev.mnaoumov.selectionpad

import android.content.ComponentName

/**
 * Whether the user has given this service an accessibility shortcut, which is the only thing that
 * shows the system's accessibility button.
 *
 * **The app cannot turn the button on or off. Only the user's shortcut can.** For a service
 * targeting API 30+, the static config is the only place `FLAG_REQUEST_ACCESSIBILITY_BUTTON` counts
 * (`setServiceInfo` keeps its static value), and the flag decides only what a click does. Measured
 * on a OnePlus 15 on 2026-10-30: the flag was set and `accessibility_button_mode` was 1 (floating),
 * but `accessibility_button_targets` was empty (`dumpsys accessibility`: `button target:{null}`)
 * because a reinstall had cleared it, and no button appeared. Android shows one only once the user
 * picks the shortcut in Settings → Accessibility → Selection Pad, and no app can write that setting.
 * So the launcher reads the assignment back rather than keeping a switch of its own.
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
