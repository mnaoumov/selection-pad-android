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
 * It is posted only while the service runs and the user has it on, and it follows the same wish
 * the tile shows ([AscAccessibilityService.padShowing]), so a lock does not flip it. It is
 * `VISIBILITY_SECRET`, because the pad itself never shows on the lock screen.
 *
 * Since Android 14 the user can swipe an ongoing notification away. That is left alone: the next
 * time the pad opens or closes, it is posted again, and the launcher's switch is the way to turn it
 * off for good.
 */
object PadNotification {

  /** What to post: whether the pad is wanted on screen. Null means post nothing. */
  data class Content(val padShown: Boolean)

  /**
   * Whether to post, and what. [on] is the user's switch, [allowed] whether the system lets the app
   * post at all (the permission, and the app's notifications not blocked in Settings), and [wanted]
   * the pad's wished state, null while the service is not running.
   */
  fun content(on: Boolean, allowed: Boolean, wanted: Boolean?): Content? =
    if (on && allowed && wanted != null) Content(wanted) else null

  /** Posts, updates or cancels the notification to match the switch and the pad. */
  fun refresh(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val settings = PadSettings(context.getSharedPreferences(PadSettings.FILE, Context.MODE_PRIVATE))
    val content = content(settings.notificationOn, manager.areNotificationsEnabled(), AscAccessibilityService.padShowing())
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
    val notification = Notification.Builder(context, CHANNEL)
      .setSmallIcon(icon)
      .setContentTitle(context.getString(R.string.app_name))
      .setContentText(context.getString(if (content.padShown) R.string.notification_shown else R.string.notification_hidden))
      .setContentIntent(openApp)
      .addAction(
        Notification.Action.Builder(
          icon,
          context.getString(if (content.padShown) R.string.notification_close else R.string.show_pad),
          toggle,
        ).build()
      )
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setCategory(Notification.CATEGORY_STATUS)
      .setVisibility(Notification.VISIBILITY_SECRET)
      .build()
    manager.notify(ID, notification)
    Diag.log("notification: posted (pad ${if (content.padShown) "shown" else "hidden"})")
  }

  private const val CHANNEL = "pad"
  private const val ID = 1
  const val ACTION_TOGGLE = "dev.mnaoumov.selectionpad.TOGGLE_PAD"
}

/**
 * The notification's button. Not exported: only the notification's own `PendingIntent`, which
 * names this class, can reach it.
 */
class PadNotificationReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != PadNotification.ACTION_TOGGLE) return
    val showing = AscAccessibilityService.togglePad()
    Diag.log("notification: ${if (showing == null) "the service is off" else if (showing) "showed the pad" else "closed the pad"}")
    // togglePad refreshes it; with the service off it cancels it.
    if (showing == null) PadNotification.refresh(context)
  }
}
