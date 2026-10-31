package dev.mnaoumov.selectionpad

import dev.mnaoumov.selectionpad.PadPlacement.Anchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadPlacementTest {

  @Test
  fun aDockedPadSpansTheWidthOfItsEdge() {
    val bottom = PadPlacement.of(PadMode.DOCKED, DockEdge.BOTTOM, minimised = false)
    assertTrue(bottom.fullWidth)
    assertEquals(Anchor.BOTTOM, bottom.anchor)
    assertEquals(Anchor.TOP, PadPlacement.of(PadMode.DOCKED, DockEdge.TOP, minimised = false).anchor)
  }

  @Test
  fun aMinimisedDockedPadIsAHandleInTheCornerOfItsEdge() {
    for (edge in DockEdge.entries) {
      val handle = PadPlacement.of(PadMode.DOCKED, edge, minimised = true)
      assertFalse(handle.fullWidth)
      assertTrue(handle.alignEnd)
      assertEquals(if (edge == DockEdge.TOP) Anchor.TOP else Anchor.BOTTOM, handle.anchor)
    }
  }

  @Test
  fun aFloatingPadStaysWhereItWasDraggedWhetherMinimisedOrNot() {
    for (minimised in listOf(false, true)) {
      val placement = PadPlacement.of(PadMode.FLOATING, DockEdge.TOP, minimised)
      assertTrue(placement.atFloatingPosition)
      assertFalse(placement.fullWidth)
    }
  }

  @Test
  fun theLauncherClearsThePadAtTheEdgeItIsDockedOn() {
    assertEquals(0 to 240, PadPlacement.clearanceDp(PadMode.DOCKED, DockEdge.BOTTOM, false, 240))
    assertEquals(240 to 0, PadPlacement.clearanceDp(PadMode.DOCKED, DockEdge.TOP, false, 240))
    assertEquals(0 to 240, PadPlacement.clearanceDp(PadMode.DOCKED, DockEdge.TOP, true, 240))
    assertEquals(0 to 240, PadPlacement.clearanceDp(PadMode.FLOATING, DockEdge.TOP, false, 240))
  }
}
