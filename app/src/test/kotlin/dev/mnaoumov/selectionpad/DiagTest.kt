package dev.mnaoumov.selectionpad

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagTest {

  @Test
  fun theShareTextNamesTheBuildThenListsEveryLine() {
    val text = Diag.shareText("1.0.0", 36, listOf("a pid=1 service: created", "b pid=1 service: connected"))
    assertEquals(
      "Selection Pad 1.0.0, Android SDK 36\nlifecycle.log: 2 line(s)\na pid=1 service: created\nb pid=1 service: connected\n",
      text,
    )
  }

  @Test
  fun anEmptyRingStillSaysSo() {
    assertEquals("Selection Pad ?, Android SDK 37\nlifecycle.log: 0 line(s)\n", Diag.shareText(null, 37, emptyList()))
  }

  @Test
  fun theRingKeepsItsLastLines() {
    assertEquals(listOf("c", "d"), Diag.trimmed(listOf("a", "b", "c", "d"), 2))
  }
}
