package dev.mnaoumov.selectionpad

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ScrollView

/** Four edges in pixels, so the inset arithmetic can be tested without a window. */
data class Edges(val left: Int, val top: Int, val right: Int, val bottom: Int)


/**
 * Keeps a scrolling screen out from under the system bars.
 *
 * **Android 15 draws every app edge to edge once it targets API 35, and this app targets 36.** The
 * window then covers the status bar, the gesture bar and any cutout, and the framework action bar
 * no longer pushes the content down. Found on a OnePlus 15 (2026-10-30): the launcher's disclosure
 * opened with its title and first lines behind the "Selection Pad" action bar, and nothing could
 * scroll them back out. On the `handset` rig profile the action bar was at y 135..228 and the
 * disclosure's title at 96..199.
 *
 * So each screen drops the action bar (its content carries its own title) and pads its
 * `ScrollView` by the bars and the cutout. A `ScrollView` clips to its padding, so no line is ever
 * drawn under a bar: the text scrolls in the space between them, and stops clear of both ends.
 */
object SystemBars {

  /** Whether a view whose top is at [top] in the window is below a top bar [barTop] high. */
  fun startsClear(top: Int, barTop: Int): Boolean = top >= barTop

  /**
   * Where a view ending at [bottom] of the content ends in the window once scrolled to the end:
   * the content is [contentHeight] tall, shown in a viewport [viewportHeight] tall whose top is at
   * [viewportTop] in the window.
   */
  fun bottomAtEnd(bottom: Int, contentHeight: Int, viewportTop: Int, viewportHeight: Int): Int =
    viewportTop + bottom - (contentHeight - viewportHeight).coerceAtLeast(0)

  /** Whether a view ending at [bottom] in a window [windowHeight] tall is above a bottom bar [barBottom] high. */
  fun endsClear(bottom: Int, windowHeight: Int, barBottom: Int): Boolean = bottom <= windowHeight - barBottom

  /** Call before `setContentView`: the framework action bar would sit over the content. */
  fun beforeContent(activity: Activity) {
    activity.requestWindowFeature(Window.FEATURE_NO_TITLE)
  }

  /** Pads [content] by [base], and [scroll] by the bars and the cutout, every time the insets change. */
  fun fit(activity: Activity, scroll: ScrollView, content: View, base: Edges) {
    content.setPadding(base.left, base.top, base.right, base.bottom)
    scroll.clipToPadding = true
    scroll.setOnApplyWindowInsetsListener { _, insets ->
      val bars = barsOf(insets)
      scroll.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      insets
    }
    // With the action bar gone the status bar sits on the window's own background, so its icons
    // have to follow that background's lightness or they vanish into it.
    val night = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
      Configuration.UI_MODE_NIGHT_YES
    val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
      WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
    // The controller lives on the decor view, which `PhoneWindow.getInsetsController` reads without
    // creating it, so before `setContentView` it throws. Asking for `decorView` creates it.
    activity.window.decorView.windowInsetsController
      ?.setSystemBarsAppearance(if (night) 0 else light, light)
  }

  /** The bars' insets as last applied to [view], or none before the first layout. */
  fun barsOf(view: View): Edges {
    val insets = view.rootWindowInsets ?: return Edges(0, 0, 0, 0)
    return barsOf(insets)
  }

  private fun barsOf(insets: WindowInsets): Edges {
    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
    return Edges(bars.left, bars.top, bars.right, bars.bottom)
  }
}
