package dev.mnaoumov.selectionpad

/**
 * What `⇄ swap` does with the edge that moves from now on, kept free of Android types so it can be
 * tested on its own. See `SelectionDriver.activeEdge` for the measurement behind it.
 */
internal object EdgeSwap {

  sealed interface Plan {
    /** The announcing node holds both edges, so the announcement already says where the new edge is. */
    data object WholeSelectionHere : Plan

    /** Nothing says where the new edge is, so every press refuses until it is announced again. */
    data object Refuse : Plan

    /**
     * Read the old anchor's node with this range, in the frame Chrome gives a node that does not hold
     * the anchor: `offset..length` for a START, `0..offset` for an END.
     */
    data class StandIn(val from: Int, val to: Int) : Plan
  }

  /**
   * [newEdge] is the edge that moves after the swap. [anchorOffset] is where the old anchor is in its
   * node, whose text is [anchorNodeLength] long, or null where the pad does not know.
   */
  fun plan(newEdge: Edge, wholeSelectionHere: Boolean, anchorOffset: Int?, anchorNodeLength: Int): Plan {
    if (wholeSelectionHere) return Plan.WholeSelectionHere
    if (anchorOffset == null || anchorNodeLength <= 0 || anchorOffset !in 0..anchorNodeLength) return Plan.Refuse
    val (from, to) = if (newEdge == Edge.START) anchorOffset to anchorNodeLength else 0 to anchorOffset
    // An empty range reads as no selection: a START on its node's last caret, or an END on its first.
    if (from == to) return Plan.Refuse
    return Plan.StandIn(from, to)
  }
}
