package dev.mnaoumov.selectionpad

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * The launcher entry, and the **prominent disclosure** Play requires of an accessibility app that is
 * not an `isAccessibilityTool`.
 *
 * The requirement is specific and it is why this is a screen rather than a line in a settings menu:
 * the disclosure must appear during normal use, must ask for affirmative consent, and is not
 * satisfied by the privacy policy. It also has to be demonstrable in the review video, so the flow
 * is deliberately linear — read, agree, then and only then the settings link.
 */
class MainActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SystemBars.beforeContent(this)

    column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    titleView = TextView(this).apply {
      text = getString(R.string.disclosure_title)
      textSize = 22f
    }
    column.addView(titleView)

    column.addView(
      TextView(this).apply {
        text = getString(R.string.disclosure_body)
        textSize = 15f
        setPadding(0, PADDING, 0, PADDING)
      }
    )

    column.addView(
      Button(this).apply {
        text = getString(R.string.disclosure_agree)
        setOnClickListener {
          Toast.makeText(this@MainActivity, R.string.consent_recorded, Toast.LENGTH_LONG).show()
          startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
      }
    )

    /*
     * Declining has to be as available as agreeing, or the "consent" is only a speed bump: Play's
     * prominent-disclosure requirement is an affirmative choice, and a choice needs a second
     * option. Declining changes nothing — the service stays off because nothing turned it on.
     */
    column.addView(
      Button(this).apply {
        text = getString(R.string.disclosure_decline)
        setOnClickListener {
          Toast.makeText(this@MainActivity, R.string.disclosure_declined, Toast.LENGTH_LONG).show()
          finish()
        }
      }
    )

    /*
     * The way back from the pad's own close button.
     *
     * Closing the pad hides the overlay and leaves the service connected, so putting it back is a
     * method call rather than a trip through Accessibility settings — which matters because that
     * switch is blocked by Enhanced Confirmation Mode for a sideloaded build.
     */
    column.addView(
      Button(this).apply {
        text = getString(R.string.show_pad)
        setOnClickListener {
          val message =
            if (AscAccessibilityService.showPad()) R.string.pad_shown else R.string.pad_not_running
          Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
      }
    )

    column.addView(togglesRow)
    column.addView(switchedOffRow)
    column.addView(batteryRow)

    scroll = ScrollView(this).apply { addView(column) }
    SystemBars.fit(this, scroll, column, contentEdges())
    setContentView(scroll)
  }

  private lateinit var scroll: ScrollView
  private lateinit var column: LinearLayout
  private lateinit var titleView: TextView

  /**
   * Logs whether the first line starts below the status bar and the last view ends above the
   * gesture bar once scrolled to the end, so the rig can check the disclosure without reading a
   * screenshot. Rectangles and verdicts only, never the text.
   */
  private fun logFit() {
    val bars = SystemBars.barsOf(scroll)
    val last = (column.childCount - 1 downTo 0).map { column.getChildAt(it) }
      .first { it.visibility == View.VISIBLE }
    // The scroll view fills the window, so its padding is where the viewport sits in the window.
    val top = scroll.paddingTop + titleView.top - scroll.scrollY
    val viewport = scroll.height - scroll.paddingTop - scroll.paddingBottom
    val bottom = SystemBars.bottomAtEnd(last.bottom, column.height, scroll.paddingTop, viewport)
    val starts = SystemBars.startsClear(top, bars.top)
    val ends = SystemBars.endsClear(bottom, scroll.height, bars.bottom)
    Diag.log(
      "disclosure fit: first line at y $top against a top bar of ${bars.top} (clear=$starts), " +
        "last view at y $bottom scrolled to the end, in a ${scroll.height} px window with a " +
        "bottom bar of ${bars.bottom} (clear=$ends)"
    )
  }

  /**
   * Asks for the battery-optimization exemption, shown only while the app does not have it.
   *
   * **An optimized app's service is killed and restarted by the ROM, again and again.** Measured on a
   * OnePlus 15 (OxygenOS 16) on 2026-10-16: six kills in 21 minutes, each logged by its battery
   * manager as `o-kill(72) ... with powersavemode`, each followed by the accessibility manager
   * restarting the service. Every kill drops the selection state and any run in progress.
   *
   * **The settings list, not the direct prompt.** `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
   * would be one tap, but it needs the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission, which ends
   * a permission the app otherwise does without, and Play allows that permission only for apps whose core
   * function cannot work otherwise. The settings list needs no permission at all. The OEM link is
   * there because OnePlus and others add a second switch of their own ("Allow background activity")
   * that the platform exemption does not cover.
   */
  private val batteryRow by lazy {
    LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(0, PADDING, 0, 0)
      addView(
        TextView(this@MainActivity).apply {
          text = getString(R.string.battery_body)
          textSize = 15f
        }
      )
      addView(
        Button(this@MainActivity).apply {
          text = getString(R.string.battery_settings)
          setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
          }
        }
      )
      addView(
        Button(this@MainActivity).apply {
          text = getString(R.string.battery_oem_help)
          setOnClickListener {
            val maker = Build.MANUFACTURER.lowercase().replace(Regex("[^a-z0-9]"), "")
            val page = Uri.parse("https://dontkillmyapp.com/$maker")
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, page)) }
              .onFailure { Toast.makeText(this@MainActivity, page.toString(), Toast.LENGTH_LONG).show() }
          }
        }
      )
    }
  }

  /**
   * The other ways to show or hide the pad, each a real switch whose checked state is the real state
   * (owner, 2026-10-31): the Quick Settings tile, the notification and the accessibility button.
   *
   * **Only the user can put a tile in the panel**, so the tile's switch is ONE control over two
   * mechanisms: on asks SystemUI to add the tile ([offerTile]), off disables the tile's component,
   * which is the only way an app can take it out. It used to be an on/off button that only enabled
   * or disabled the component, beside a separate Add button, and turning it back on never put the
   * tile back (phone, 2026-10-31), which read as "Add stopped working".
   */
  private val togglesRow: LinearLayout by lazy {
    LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(0, PADDING, 0, 0)
      addView(
        TextView(this@MainActivity).apply {
          text = getString(R.string.toggles_body)
          textSize = 15f
        }
      )
      addView(tileSwitch)
      addView(notificationSwitch)
      addView(notificationHint)
      addView(buttonSwitch)
      addView(buttonHint)
    }
  }

  /**
   * The accessibility button's state, read from the system, and the way to change it. The app has
   * no switch of its own to offer: the button shows exactly when the user has given the service a
   * shortcut ([ButtonShortcut]), so this opens the page where that is done, either way.
   */
  private val buttonSwitch by lazy {
    toggle(R.string.button_switch).apply {
      setOnClickListener {
        // The tap has flipped the switch; only the user's shortcut can, so put it back until it does.
        showToggles()
        val message = if (buttonAssigned()) R.string.button_remove_shortcut else R.string.button_pick_shortcut
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        openShortcutSettings()
      }
    }
  }

  private val buttonHint by lazy {
    TextView(this).apply {
      textSize = 15f
      setPadding(0, PADDING / 2, 0, 0)
    }
  }

  private fun buttonAssigned(): Boolean {
    val lists = ButtonShortcut.TARGET_KEYS.map { Settings.Secure.getString(contentResolver, it) }
    return ButtonShortcut.assigned(ComponentName(this, AscAccessibilityService::class.java), lists)
  }

  /**
   * Opens Accessibility settings with Selection Pad's row picked out, so the user can tap it and
   * set or remove its shortcut. The direct route, `ACCESSIBILITY_DETAILS_SETTINGS`, is guarded by a
   * permission only the system and the installer hold: on the rig it threw `SecurityException`. The
   * list keys each service's row by its flattened component, which is what the Settings app's
   * `:settings:fragment_args_key` extra highlights.
   */
  private fun openShortcutSettings() {
    val ours = ComponentName(this, AscAccessibilityService::class.java).flattenToString()
    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).putExtra(FRAGMENT_ARG_KEY, ours))
  }

  /**
   * A platform switch row, label on the left. Each is driven by its click, never by its checked
   * state: the tap only asks, and [showToggles] then sets the switch to what is really so.
   */
  private fun toggle(label: Int) = Switch(this).apply {
    text = getString(label)
    textSize = 17f
    setPadding(0, PADDING / 2, 0, PADDING / 2)
  }

  private val tileSwitch by lazy {
    toggle(R.string.tile_switch).apply {
      setOnClickListener {
        if (PadTileService.inPanel(this@MainActivity, settings)) {
          // Disabling the component is the only way an app can take its tile out of the panel.
          settings.tileInPanel = false
          PadTileService.setEnabled(this@MainActivity, false)
          Toast.makeText(this@MainActivity, R.string.tile_removed, Toast.LENGTH_LONG).show()
          showToggles()
        } else {
          if (!PadTileService.isEnabled(this@MainActivity)) PadTileService.setEnabled(this@MainActivity, true)
          // Off until SystemUI answers that the tile is in.
          showToggles()
          offerTile()
        }
      }
    }
  }

  /**
   * The ongoing notification's switch ([PadNotification]), off by default (owner, 2026-10-30).
   *
   * **Turning it on is the only thing that asks for a permission.** Off to on asks for
   * `POST_NOTIFICATIONS` when the app does not hold it, and turns on only when it is granted. After
   * a second refusal Android stops showing the prompt and refuses at once, so from then on the
   * switch opens the app's notification settings instead of asking into silence.
   */
  private val notificationSwitch by lazy {
    toggle(R.string.notification_switch).apply {
      setOnClickListener {
        // The tap has flipped the switch already; it shows what is really so until the flow below settles it.
        showToggles()
        when {
          settings.notificationOn -> setNotification(false)
          notificationsAllowed() -> setNotification(true)
          settings.notificationDenied && !shouldShowRequestPermissionRationale(NOTIFICATIONS) -> {
            Diag.log("notification: the system no longer asks — opening the app's notification settings")
            openNotificationSettings()
          }
          else -> {
            Diag.log("notification: asking for the permission")
            requestPermissions(arrayOf(NOTIFICATIONS), REQUEST_NOTIFICATIONS)
          }
        }
      }
    }
  }

  private val notificationHint by lazy {
    TextView(this).apply {
      textSize = 15f
      setPadding(0, PADDING / 2, 0, 0)
    }
  }

  /** The permission held and the app's notifications not blocked; on 13+ blocking them revokes it. */
  private fun notificationsAllowed(): Boolean =
    checkSelfPermission(NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
      getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()

  private fun setNotification(on: Boolean) {
    settings.notificationOn = on
    if (on) settings.notificationDenied = false
    Diag.log("notification: switch ${if (on) "on" else "off"}")
    PadNotification.refresh(this)
    showToggles()
  }

  private fun openNotificationSettings() {
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode != REQUEST_NOTIFICATIONS) return
    val granted = grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED
    Diag.log("notification: permission ${if (granted) "granted" else "refused"}")
    if (granted) {
      setNotification(true)
    } else {
      settings.notificationDenied = true
      showToggles()
    }
  }

  private fun showNotificationSwitch() {
    val on = settings.notificationOn
    val allowed = notificationsAllowed()
    notificationSwitch.isChecked = on
    val hint = when {
      on && allowed -> null
      on -> R.string.notification_hint_blocked
      settings.notificationDenied && !allowed -> R.string.notification_hint_denied
      else -> R.string.notification_hint_off
    }
    notificationHint.visibility = if (hint == null) View.GONE else View.VISIBLE
    if (hint != null) notificationHint.text = getString(hint)
  }

  private fun showToggles() {
    showNotificationSwitch()
    tileSwitch.isChecked = PadTileService.inPanel(this, settings)
    val assigned = buttonAssigned()
    buttonSwitch.isChecked = assigned
    buttonHint.text = getString(if (assigned) R.string.button_hint_on else R.string.button_hint_off)
    Diag.log("button: launcher reads the shortcut as ${if (assigned) "assigned" else "not assigned"}")
  }

  /**
   * Asks SystemUI to offer the tile (`StatusBarManager.requestAddTileService`, API 33, which is the
   * app's floor). SystemUI shows its own dialog; an already-added tile answers at once with no
   * dialog. The result is logged and, when the tile could not be offered, the toast says where to
   * add it by hand. SystemUI puts a new tile at the END of the panel, on its last page (phone,
   * 2026-10-31), where it is easy to miss, so that toast says so.
   */
  private fun offerTile() {
    val statusBar = getSystemService(android.app.StatusBarManager::class.java)
    statusBar.requestAddTileService(
      PadTileService.component(this),
      getString(R.string.app_name),
      android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_tile),
      mainExecutor,
    ) { result ->
      Diag.log("tile: add request answered $result")
      val message = when (result) {
        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added_at_end
        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_added
        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> null
        else -> R.string.tile_add_failed
      }
      if (result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
        result == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
      ) {
        settings.tileInPanel = true
      }
      if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
      showToggles()
    }
  }

  private val settings by lazy { PadSettings(getSharedPreferences(PadSettings.FILE, MODE_PRIVATE)) }

  /**
   * Says that the service was switched off, shown only when it has run before and is off now.
   *
   * **A ROM can turn the service off without telling the user.** Measured on a OnePlus 15
   * (OxygenOS 16) on 2026-10-17: the pad's menu mask covered the whole screen for two seconds, and
   * the ROM's "Block suspicious app activities" removed the service from the enabled list
   * (`AccessibilityManagerServiceExtImpl: removeEnableService`). The pad simply vanished. The mask can
   * no longer do that, but a protection that judges "abnormal device control" may judge something
   * else next, so the screen says what happened and where the allowlist is. It cannot tell that ROM
   * from the user turning the service off, hence the second button.
   */
  private val switchedOffRow: LinearLayout by lazy {
    LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(0, PADDING, 0, 0)
      addView(
        TextView(this@MainActivity).apply {
          text = getString(R.string.switched_off_body) +
            if (isOplus()) "\n\n" + getString(R.string.switched_off_oplus) else ""
          textSize = 15f
        }
      )
      addView(
        Button(this@MainActivity).apply {
          text = getString(R.string.switched_off_settings)
          setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
      )
      addView(
        Button(this@MainActivity).apply {
          text = getString(R.string.switched_off_by_me)
          setOnClickListener {
            settings.serviceConnectedOnce = false
            switchedOffRow.visibility = View.GONE
          }
        }
      )
    }
  }

  /** OnePlus, OPPO and realme share ColorOS's security centre, which is where the allowlist lives. */
  private fun isOplus(): Boolean =
    Build.MANUFACTURER.lowercase() in setOf("oneplus", "oppo", "realme")

  /** Whether the service is in the system's enabled list, which is what a ROM edits when it revokes it. */
  private fun serviceEnabled(): Boolean {
    val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
      ?: return false
    val ours = ComponentName(this, AscAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
  }

  /**
   * The docked pad covers an edge of every screen, this one included, so the first or last lines
   * need room to scroll past it, on top of the bar the pad itself sits beside. Which edge follows
   * the pad ([PadPlacement.clearanceDp]).
   */
  private fun contentEdges(): Edges {
    val (top, bottom) = PadPlacement.clearanceDp(settings.mode, settings.dockEdge, settings.minimised, PAD_CLEARANCE_DP)
    val density = resources.displayMetrics.density
    return Edges(PADDING, PADDING * 2 + (top * density).toInt(), PADDING, (bottom * density).toInt())
  }

  override fun onResume() {
    super.onResume()
    // The pad may have moved edge since this screen was built.
    contentEdges().let { column.setPadding(it.left, it.top, it.right, it.bottom) }
    // Re-read on every return, so the row goes away once the user has granted the exemption.
    val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    batteryRow.visibility = if (exempt) View.GONE else View.VISIBLE
    val switchedOff = settings.serviceConnectedOnce && !serviceEnabled()
    switchedOffRow.visibility = if (switchedOff) View.VISIBLE else View.GONE
    showToggles()
    // Notifications may have been allowed or blocked in Settings since the screen was last shown.
    PadNotification.refresh(this)
    /*
     * The tile is offered by itself once, and only after the service has run: a tile for a service
     * that is still off would only open this screen again, and the dialog would cover the
     * disclosure, which has to be read first.
     */
    if (settings.serviceConnectedOnce && !settings.tileOffered && PadTileService.isEnabled(this) && !settings.tileInPanel) {
      settings.tileOffered = true
      offerTile()
    }
    column.post { logFit() }
  }

  private companion object {
    const val PADDING = 48

    /** More than the docked pad's height: 152 dp on the rig, three rows of buttons and the padding. */
    const val PAD_CLEARANCE_DP = 240

    /** The Settings app's extra naming the preference to highlight; not in the public SDK. */
    const val FRAGMENT_ARG_KEY = ":settings:fragment_args_key"

    const val NOTIFICATIONS = android.Manifest.permission.POST_NOTIFICATIONS
    const val REQUEST_NOTIFICATIONS = 1
  }
}
