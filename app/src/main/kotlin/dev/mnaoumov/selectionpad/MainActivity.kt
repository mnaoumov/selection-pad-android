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
    // The docked pad covers the bottom of every screen, this one included, so the last buttons
    // need room to scroll up past it, on top of the gesture bar the pad itself sits above.
    val clearOfPad = (PAD_CLEARANCE_DP * resources.displayMetrics.density).toInt()
    SystemBars.fit(this, scroll, column, Edges(PADDING, PADDING * 2, PADDING, clearOfPad))
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
   * the app's no-permissions property, and Play allows that permission only for apps whose core
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
   * The other two ways to show or hide the pad (owner, 2026-10-30): the Quick Settings tile, on by
   * default, and the system's accessibility button, off by default.
   *
   * **Only the user can put a tile in the panel**, so "on by default" means the tile's component is
   * enabled from install and the app asks SystemUI to offer it ([offerTile]). The switch disables
   * the component, which takes the tile out of the panel.
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
      addView(addTileButton)
      addView(tileSwitch)
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
    Button(this).apply {
      setOnClickListener {
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

  private val addTileButton by lazy {
    Button(this).apply {
      text = getString(R.string.tile_add)
      setOnClickListener { offerTile() }
    }
  }

  private val tileSwitch by lazy {
    Button(this).apply {
      setOnClickListener {
        PadTileService.setEnabled(this@MainActivity, !PadTileService.isEnabled(this@MainActivity))
        showToggles()
      }
    }
  }

  private fun showToggles() {
    val tileEnabled = PadTileService.isEnabled(this)
    tileSwitch.text = getString(if (tileEnabled) R.string.tile_switch_on else R.string.tile_switch_off)
    addTileButton.visibility = if (tileEnabled) View.VISIBLE else View.GONE
    val assigned = buttonAssigned()
    buttonSwitch.text = getString(if (assigned) R.string.button_switch_on else R.string.button_switch_off)
    buttonHint.text = getString(if (assigned) R.string.button_hint_on else R.string.button_hint_off)
    Diag.log("button: launcher reads the shortcut as ${if (assigned) "assigned" else "not assigned"}")
  }

  /**
   * Asks SystemUI to offer the tile (`StatusBarManager.requestAddTileService`, API 33, which is the
   * app's floor). SystemUI shows its own dialog; an already-added tile answers at once with no
   * dialog. The result is logged and, when the tile could not be offered, the toast says where to
   * add it by hand.
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
        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED,
        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_added
        android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> null
        else -> R.string.tile_add_failed
      }
      if (message != null) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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

  override fun onResume() {
    super.onResume()
    // Re-read on every return, so the row goes away once the user has granted the exemption.
    val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    batteryRow.visibility = if (exempt) View.GONE else View.VISIBLE
    val switchedOff = settings.serviceConnectedOnce && !serviceEnabled()
    switchedOffRow.visibility = if (switchedOff) View.VISIBLE else View.GONE
    showToggles()
    /*
     * The tile is offered by itself once, and only after the service has run: a tile for a service
     * that is still off would only open this screen again, and the dialog would cover the
     * disclosure, which has to be read first.
     */
    if (settings.serviceConnectedOnce && !settings.tileOffered && PadTileService.isEnabled(this)) {
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
  }
}
