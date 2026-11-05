package dev.mnaoumov.selectionpad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

/**
 * The ongoing notification that shows or closes the pad from the notification list, beside the
 * Quick Settings tile (owner, 2026-10-30).
 *
 * **Off by default, because it is the one thing in the app that needs a permission.** Android 13+
 * asks the user for `POST_NOTIFICATIONS`, and the app asks only when the user turns the
 * notification on in the launcher screen. Nothing in it leaves the phone: it is drawn by the
 * system from what the app posts locally.
 *
 * It is posted while the service is enabled and the user has it on. While the service is enabled but
 * not running ([ServiceStatus]) it says so, and its button opens the launcher screen, which says how
 * to get it running again. Otherwise it follows the same wish
 * the tile shows ([AscAccessibilityService.padShowing]), so a lock does not flip it. It is
 * `VISIBILITY_SECRET`, because the pad itself never shows on the lock screen.
 *
 * Since Android 14 the user can swipe an ongoing notification away. That is left alone: the next
 * time the pad opens or closes, it is posted again, and the launcher's switch is the way to turn it
 * off for good.
 */
object PadNotification {

  /**
   * What to post: whether the pad is wanted on screen, or null for "enabled but not running"
   * ([ServiceStatus]), which says so. A null [Content] means post nothing.
   */
  data class Content(val padShown: Boolean?)

  /**
   * Whether to post, and what. [on] is the user's switch, [allowed] whether the system lets the app
   * post at all (the permission, and the app's notifications not blocked in Settings), [wanted] the
   * pad's wished state, null while the service is not running, and [enabled] whether the service is
   * in the system's enabled list.
   */
  fun content(on: Boolean, allowed: Boolean, wanted: Boolean?, enabled: Boolean): Content? = when {
    !on || !allowed -> null
    wanted != null -> Content(wanted)
    enabled -> Content(null)
    else -> null
  }

  /** Where a tap sends the user: the toggle broadcast, or the launcher screen. */
  enum class Target { TOGGLE, OPEN_APP }

  /** One button: its label and where it goes. */
  data class Button(val label: Int, val target: Target)

  /**
   * Where the body tap and the buttons go (owner's phone test, 2026-11-04: a tap on the body opened
   * the app, while it should just show or close the pad). While the service runs, the body toggles
   * the pad through the same broadcast as the first button, and a second button opens the launcher
   * screen, which is the way into the settings. While it is not running nothing can toggle, so the
   * body and the one button both open the launcher screen, which says how to get it running again.
   */
  data class Wiring(val body: Target, val buttons: List<Button>)

  fun wiring(padShown: Boolean?): Wiring = when (padShown) {
    null -> Wiring(Target.OPEN_APP, listOf(Button(R.string.notification_what_to_do, Target.OPEN_APP)))
    else -> Wiring(
      Target.TOGGLE,
      listOf(
        Button(if (padShown) R.string.notification_close else R.string.show_pad, Target.TOGGLE),
        Button(R.string.notification_settings, Target.OPEN_APP),
      ),
    )
  }

  /** Posts, updates or cancels the notification to match the switch and the pad. */
  fun refresh(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val settings = PadSettings(context.getSharedPreferences(PadSettings.FILE, Context.MODE_PRIVATE))
    val content = content(
      settings.notificationOn,
      manager.areNotificationsEnabled(),
      AscAccessibilityService.padShowing(),
      ServiceStatus.enabled(context),
    )
    if (content == null) {
      manager.cancel(ID)
      return
    }
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
        setShowBadge(false)
        setSound(null, null)
        enableVibration(false)
      }
    )
    val openApp = PendingIntent.getActivity(
      context,
      0,
      Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      PendingIntent.FLAG_IMMUTABLE,
    )
    val toggle = PendingIntent.getBroadcast(
      context,
      0,
      Intent(context, PadNotificationReceiver::class.java).setAction(ACTION_TOGGLE),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val icon = Icon.createWithResource(context, R.drawable.ic_tile)
    val wiring = wiring(content.padShown)
    fun intentFor(target: Target) = when (target) {
      Target.TOGGLE -> toggle
      Target.OPEN_APP -> openApp
    }
    val builder = Notification.Builder(context, CHANNEL)
      .setSmallIcon(icon)
      .setContentTitle(context.getString(R.string.app_name))
      .setContentText(
        context.getString(
          when (content.padShown) {
            true -> R.string.notification_shown
            false -> R.string.notification_hidden
            null -> R.string.notification_not_running
          }
        )
      )
      .setContentIntent(intentFor(wiring.body))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setCategory(Notification.CATEGORY_STATUS)
      .setVisibility(Notification.VISIBILITY_SECRET)
    for (button in wiring.buttons) {
      builder.addAction(Notification.Action.Builder(icon, context.getString(button.label), intentFor(button.target)).build())
    }
    manager.notify(ID, builder.build())
    Diag.log("notification: posted (${when (content.padShown) { true -> "pad shown"; false -> "pad hidden"; null -> "service not running" }})")
  }

  private const val CHANNEL = "pad"
  private const val ID = 1
  const val ACTION_TOGGLE = "dev.mnaoumov.selectionpad.TOGGLE_PAD"
}

/**
 * The notification's body tap and its toggle button. Not exported: only the notification's own `PendingIntent`, which
 * names this class, can reach it.
 */
class PadNotificationReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != PadNotification.ACTION_TOGGLE) return
    val showing = AscAccessibilityService.togglePad()
    if (showing != null) {
      // togglePad refreshes it.
      Diag.log("notification: ${if (showing) "showed the pad" else "closed the pad"}")
      return
    }
    /*
     * The service went between the post and the tap. Re-posting turns this into the not-running
     * form, whose button opens the launcher screen, or cancels it when the service is off.
     */
    Diag.record(context, "notification: the button found no service running")
    PadNotification.refresh(context)
  }
}
