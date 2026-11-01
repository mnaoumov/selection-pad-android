package dev.mnaoumov.selectionpad

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics for the loop.
 *
 * **This logs offsets, pixels and timings — never text.** That is not a convention to be relaxed
 * later: the whole trust story is that the app cannot leak what it never reads, and a log is the
 * easiest place for that to quietly stop being true. The throwaway spike logged node text
 * deliberately, on the owner's own device, and none of it came here.
 *
 * It exists because the first version of this app shipped with no instrumentation at all, and the
 * first bug report — "it doesn't work that well" — could not be answered without guessing.
 */
object Diag {
  const val TAG = "ASC"

  /** The ring file under `filesDir`: `adb shell run-as dev.mnaoumov.selectionpad cat files/lifecycle.log`. */
  const val LIFECYCLE_FILE = "lifecycle.log"

  /** How many lines the ring file keeps. Lifecycle lines are rare, so this is days of them. */
  const val LIFECYCLE_LINES = 300

  @Volatile
  var enabled = true

  fun log(message: String) {
    if (enabled) Log.i(TAG, message)
  }

  /**
   * Logs [message] and also keeps it in a small ring file, for the service's lifecycle: connects,
   * unbinds, process exits, and the toggles finding the service not running.
   *
   * **logcat cannot hold these.** On the OnePlus 15 (2026-10-31) the service was found enabled but not
   * bound, with its process alive, and the lines from before the unbind had already rotated out of the
   * phone's 256 KiB buffer, so the cause could not be read. The file outlives the buffer and the
   * process alike.
   */
  fun record(context: Context, message: String) {
    log(message)
    runCatching {
      synchronized(this) {
        val file = File(context.filesDir, LIFECYCLE_FILE)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val lines = if (file.exists()) file.readLines() else emptyList()
        file.writeText(trimmed(lines + "$stamp pid=${android.os.Process.myPid()} $message", LIFECYCLE_LINES).joinToString("\n", postfix = "\n"))
      }
    }.onFailure { log("diag: could not write the lifecycle file (${it::class.java.simpleName})") }
  }

  /** The last [max] of [lines]. */
  internal fun trimmed(lines: List<String>, max: Int): List<String> = if (lines.size <= max) lines else lines.takeLast(max)
}
