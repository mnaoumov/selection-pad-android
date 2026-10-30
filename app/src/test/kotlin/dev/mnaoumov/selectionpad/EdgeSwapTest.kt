package dev.mnaoumov.selectionpad

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The swap's decision, without a node tree. The case that matters is the one the phone found
 * (2026-10-30): the END grown into the next node, then `⇄ swap`, used to refuse every press after it.
 */
class EdgeSwapTest {

  @Test
  fun aSelectionInsideOneNodeNeedsNothingRebuilt() {
    assertEquals(EdgeSwap.Plan.WholeSelectionHere, EdgeSwap.plan(Edge.START, true, 24, 28))
  }

  @Test
  fun aStartLeftBehindInAnotherNodeIsReadThereAtTheAnchor() {
    // `here` (`24..28`) long-pressed, the END stepped into the next block, then `⇄ swap`.
    assertEquals(EdgeSwap.Plan.StandIn(24, 28), EdgeSwap.plan(Edge.START, false, 24, 28))
  }

  @Test
  fun anEndLeftBehindInAnotherNodeIsReadThereAtTheAnchor() {
    // Swapped back: the END is on caret 1 of the 3-character `As `.
    assertEquals(EdgeSwap.Plan.StandIn(0, 1), EdgeSwap.plan(Edge.END, false, 1, 3))
  }

  @Test
  fun anUnknownAnchorStillRefuses() {
    assertEquals(EdgeSwap.Plan.Refuse, EdgeSwap.plan(Edge.START, false, null, 28))
    assertEquals(EdgeSwap.Plan.Refuse, EdgeSwap.plan(Edge.START, false, 24, -1))
    assertEquals(EdgeSwap.Plan.Refuse, EdgeSwap.plan(Edge.START, false, 29, 28))
  }

  @Test
  fun aRangeThatWouldReadAsNoSelectionRefuses() {
    assertEquals(EdgeSwap.Plan.Refuse, EdgeSwap.plan(Edge.START, false, 28, 28))
    assertEquals(EdgeSwap.Plan.Refuse, EdgeSwap.plan(Edge.END, false, 0, 3))
  }
}
