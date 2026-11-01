package dev.mnaoumov.selectionpad

import android.content.SharedPreferences

/**
 * What the user set on the pad, kept across a restart of the service.
 *
 * **The service is restarted behind the user's back, so nothing the user can see may live only in
 * memory.** Measured 2026-10-16 on the OnePlus 15: OxygenOS's battery manager killed the process six
 * times in 21 minutes (`OplusClearSystemService: K [...] o-kill(72) ... with powersavemode`), and each
 * time the accessibility manager restarted the service 1-80 s later. `onServiceConnected` used to
 * show the pad unconditionally, so a pad the user had closed came back "at random". It reads this
 * instead, and so does every other choice the pad offers: the menu mask, docked or floating, the
 * edge a docked pad sits on, whether it is minimised, and where a floating pad was dragged.
 *
 * None of it is the text, the selection or anything about the app being driven. These are the pad's
 * own settings, in the app's private preferences, and they never leave the device.
 */
class PadSettings(private val preferences: SharedPreferences) {

  /** Whether the pad is on screen. A first run shows it, because that is what turning the service on asks for. */
  var padVisible: Boolean
    get() = preferences.getBoolean(KEY_PAD_VISIBLE, true)
    set(value) = preferences.edit().putBoolean(KEY_PAD_VISIBLE, value).apply()

  /** Whether the target app's selection toolbar is covered ([ToolbarMask]). */
  var menuMasked: Boolean
    get() = preferences.getBoolean(KEY_MENU_MASKED, false)
    set(value) = preferences.edit().putBoolean(KEY_MENU_MASKED, value).apply()

  /**
   * Whether the service has connected since the user last acknowledged it being off.
   *
   * Set on every connect. The launcher screen reads it to tell "never turned on" from "was on and
   * something switched it off", which OxygenOS's "Block suspicious app activities" does without a word
   * to the user (2026-10-17). The user's "I turned it off myself" clears it.
   */
  var serviceConnectedOnce: Boolean
    get() = preferences.getBoolean(KEY_SERVICE_CONNECTED_ONCE, false)
    set(value) = preferences.edit().putBoolean(KEY_SERVICE_CONNECTED_ONCE, value).apply()

  /**
   * Whether the launcher screen has already offered to add the Quick Settings tile by itself. It
   * offers once; the button stays for any later time.
   */
  var tileOffered: Boolean
    get() = preferences.getBoolean(KEY_TILE_OFFERED, false)
    set(value) = preferences.edit().putBoolean(KEY_TILE_OFFERED, value).apply()

  /**
   * Whether the Quick Settings tile is in the panel, as SystemUI last told the tile
   * (`onTileAdded`, `onTileRemoved`, or a bind to listen, which only a tile in the panel gets). Only
   * the user can add a tile, so the app cannot read this anywhere else.
   */
  var tileInPanel: Boolean
    get() = preferences.getBoolean(KEY_TILE_IN_PANEL, false)
    set(value) = preferences.edit().putBoolean(KEY_TILE_IN_PANEL, value).apply()

  /**
   * Whether the user turned on the ongoing notification ([PadNotification]). Off by default (owner,
   * 2026-10-30), so the app asks for no permission until the user turns it on.
   */
  var notificationOn: Boolean
    get() = preferences.getBoolean(KEY_NOTIFICATION_ON, false)
    set(value) = preferences.edit().putBoolean(KEY_NOTIFICATION_ON, value).apply()

  /**
   * Whether the system has refused the notification permission since the user last asked for it.
   * After a second refusal Android stops showing the prompt, so the switch then opens the app's
   * notification settings instead.
   */
  var notificationDenied: Boolean
    get() = preferences.getBoolean(KEY_NOTIFICATION_DENIED, false)
    set(value) = preferences.edit().putBoolean(KEY_NOTIFICATION_DENIED, value).apply()

  /**
   * The timestamp of the newest process exit already written to the lifecycle file, so each connect
   * records only the exits since ([Diag.record]).
   */
  var lastExitRecorded: Long
    get() = preferences.getLong(KEY_LAST_EXIT_RECORDED, 0L)
    set(value) = preferences.edit().putLong(KEY_LAST_EXIT_RECORDED, value).apply()

  /** Docked or floating. An unknown stored name, from some later build, falls back to docked. */
  var mode: PadMode
    get() = PadMode.entries.firstOrNull { it.name == preferences.getString(KEY_MODE, null) } ?: PadMode.DOCKED
    set(value) = preferences.edit().putString(KEY_MODE, value.name).apply()

  /** Which edge a docked pad sits on. An unknown stored name falls back to the bottom. */
  var dockEdge: DockEdge
    get() = DockEdge.entries.firstOrNull { it.name == preferences.getString(KEY_DOCK_EDGE, null) } ?: DockEdge.BOTTOM
    set(value) = preferences.edit().putString(KEY_DOCK_EDGE, value.name).apply()

  /** Whether the pad is shrunk to its small handle. */
  var minimised: Boolean
    get() = preferences.getBoolean(KEY_MINIMISED, false)
    set(value) = preferences.edit().putBoolean(KEY_MINIMISED, value).apply()

  /** Where a floating pad was last dragged, in raw screen pixels, or null before the first drag. */
  var floatingPosition: Pair<Int, Int>?
    get() =
      if (preferences.contains(KEY_FLOATING_X) && preferences.contains(KEY_FLOATING_Y)) {
        preferences.getInt(KEY_FLOATING_X, 0) to preferences.getInt(KEY_FLOATING_Y, 0)
      } else {
        null
      }
    set(value) {
      val editor = preferences.edit()
      if (value == null) {
        editor.remove(KEY_FLOATING_X).remove(KEY_FLOATING_Y)
      } else {
        editor.putInt(KEY_FLOATING_X, value.first).putInt(KEY_FLOATING_Y, value.second)
      }
      editor.apply()
    }

  companion object {
    /** The preferences file, under the app's private data. */
    const val FILE = "pad"

    private const val KEY_PAD_VISIBLE = "padVisible"
    private const val KEY_MENU_MASKED = "menuMasked"
    private const val KEY_SERVICE_CONNECTED_ONCE = "serviceConnectedOnce"
    private const val KEY_TILE_OFFERED = "tileOffered"
    private const val KEY_TILE_IN_PANEL = "tileInPanel"
    private const val KEY_NOTIFICATION_ON ="notificationOn"
    private const val KEY_NOTIFICATION_DENIED = "notificationDenied"
    private const val KEY_LAST_EXIT_RECORDED = "lastExitRecorded"
    private const val KEY_MODE = "mode"
    private const val KEY_DOCK_EDGE = "dockEdge"
    private const val KEY_MINIMISED = "minimised"
    private const val KEY_FLOATING_X = "floatingX"
    private const val KEY_FLOATING_Y = "floatingY"
  }
}
