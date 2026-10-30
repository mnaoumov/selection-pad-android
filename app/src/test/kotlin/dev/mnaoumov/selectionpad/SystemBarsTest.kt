package dev.mnaoumov.selectionpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fit checks, with the `handset` rig profile's numbers: a 2772 px window, an 84 px status bar
 * and an 84 px gesture bar. The failing shapes are the ones the disclosure had before the fix.
 */
class SystemBarsTest {

  @Test
  fun aFirstLineUnderTheBarIsNotClear() {
    // Before the fix the title sat at y 96 in a window whose action bar ended at 228.
    assertFalse(SystemBars.startsClear(top = 96, barTop = 228))
    assertTrue(SystemBars.startsClear(top = 180, barTop = 84))
  }

  @Test
  fun aLastViewIsPlacedWithTheContentScrolledToItsEnd() {
    // A viewport from y 84 to 2688 (2604 px) over 3600 px of content scrolls 996 px, so a view
    // ending at 3600 ends at 84 + 3600 - 996 = 2688, the gesture bar's top.
    assertEquals(2688, SystemBars.bottomAtEnd(bottom = 3600, contentHeight = 3600, viewportTop = 84, viewportHeight = 2604))
    assertTrue(SystemBars.endsClear(bottom = 2688, windowHeight = 2772, barBottom = 84))
  }

  @Test
  fun aViewportThatFillsTheWindowEndsUnderTheGestureBar() {
    // The shape before the fix: the viewport ran to the window's bottom.
    val bottom = SystemBars.bottomAtEnd(bottom = 3600, contentHeight = 3600, viewportTop = 0, viewportHeight = 2772)
    assertEquals(2772, bottom)
    assertFalse(SystemBars.endsClear(bottom = bottom, windowHeight = 2772, barBottom = 84))
  }

  @Test
  fun contentShorterThanTheViewportDoesNotScroll() {
    assertEquals(2084, SystemBars.bottomAtEnd(bottom = 2000, contentHeight = 2000, viewportTop = 84, viewportHeight = 2604))
  }
}
