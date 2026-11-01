package dev.mnaoumov.selectionpad

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The Quick Settings tile that toggles the pad from the notification panel.
 *
 * **It needs no permission.** The manifest guards the service with `BIND_QUICK_SETTINGS_TILE`, which
 * is the permission SystemUI must hold to bind it, not one this app requests (`aapt2 dump
 * permissions` does not list it).
 *
 * **Off while the service is off is INACTIVE with a subtitle, not `STATE_UNAVAILABLE`.** SystemUI
 * does not deliver a tap on an unavailable tile at all, and a tap that does nothing is the one thing
 * this tile must not be: with the service off it opens the launcher screen, whose disclosure is the
 * only way to turn the service on. With the service enabled but not running ([ServiceStatus]) its
 * subtitle says so, and the launcher screen it opens says how to get it running again.
 *
 * The tile reads the pad's state from the connected service, which shares this process. It is not
 * an active tile, so SystemUI asks for its state whenever the panel opens, and the service asks for
 * a refresh ([refresh]) whenever the pad opens or closes by any other way.
 */
class PadTileService : TileService() {

  /*
   * Whether the tile is in the panel is known only here, so the launcher's switch reads what these
   * record. SystemUI binds a tile to listen only while it is in the panel, which also covers a tile
   * added before this tracking existed.
   */
  override fun onTileAdded() {
    super.onTileAdded()
    Diag.log("tile: added to the panel")
    settings().tileInPanel = true
  }

  override fun onTileRemoved() {
    super.onTileRemoved()
    Diag.log("tile: removed from the panel")
    settings().tileInPanel = false
  }

  override fun onStartListening() {
    super.onStartListening()
    settings().tileInPanel = true
    update()
  }

  private fun settings() = PadSettings(getSharedPreferences(PadSettings.FILE, MODE_PRIVATE))

  override fun onClick() {
    super.onClick()
    val showing = AscAccessibilityService.togglePad()
    if (showing != null) {
      Diag.log("tile: ${if (showing) "showed" else "closed"} the pad")
      update()
      return
    }
    // Off or not running, the launcher screen says which and what to do ([ServiceStatus]).
    val state = ServiceStatus.state(this)
    if (state == ServiceStatus.State.NOT_RUNNING) {
      Diag.record(this, "tile: the service is enabled but not running — opening the launcher screen")
    } else {
      Diag.log("tile: the service is off — opening the launcher screen")
    }
    update()
    openLauncher()
  }

  private fun update() {
    val tile = qsTile ?: return
    val showing = AscAccessibilityService.padShowing()
    tile.state = when (showing) {
      true -> Tile.STATE_ACTIVE
      else -> Tile.STATE_INACTIVE
    }
    tile.subtitle = getString(
      when {
        showing == true -> R.string.tile_on
        showing == false -> R.string.tile_off
        ServiceStatus.state(this) == ServiceStatus.State.NOT_RUNNING -> R.string.tile_not_running
        else -> R.string.tile_service_off
      }
    )
    tile.updateTile()
  }

  private fun openLauncher() {
    val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
    } else {
      @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
      startActivityAndCollapse(intent)
    }
  }

  companion object {
    fun component(context: Context) = ComponentName(context, PadTileService::class.java)

    /** Asks SystemUI to re-read the tile, after the pad opened or closed some other way. */
    fun refresh(context: Context) {
      if (!isEnabled(context)) return
      runCatching { requestListeningState(context, component(context)) }
        .onFailure { Diag.log("tile: refresh refused (${it::class.java.simpleName})") }
    }

    /**
     * Whether the tile's component is enabled. It is from install. Turning the launcher's switch off
     * disables it, which is the only way an app can take its tile out of the panel; turning the
     * switch on enables it again before asking SystemUI to add the tile.
     */
    fun isEnabled(context: Context): Boolean =
      context.packageManager.getComponentEnabledSetting(component(context)) !=
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    /** What the launcher's switch shows: the component enabled, and SystemUI's last word that the tile is in the panel. */
    fun inPanel(context: Context, settings: PadSettings): Boolean = isEnabled(context) && settings.tileInPanel

    fun setEnabled(context: Context, enabled: Boolean) {
      val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
      context.packageManager.setComponentEnabledSetting(component(context), state, PackageManager.DONT_KILL_APP)
      Diag.log("tile: component ${if (enabled) "enabled" else "disabled"}")
    }
  }
}
