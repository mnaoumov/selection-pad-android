package dev.mnaoumov.selectionpad

import dev.mnaoumov.selectionpad.LockGate.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lock-screen rule (owner, 2026-10-30): no pad over the keyguard, and only the user's own pad back after it. */
class LockGateTest {

  @Test
  fun aShowingPadHidesWhenTheDeviceLocks() {
    assertEquals(Action.HIDE, LockGate.action(padVisible = true, locked = true, padShowing = true))
  }

  @Test
  fun aPadTheUserHadShowingComesBackAtUnlock() {
    assertEquals(Action.SHOW, LockGate.action(padVisible = true, locked = false, padShowing = false))
  }

  @Test
  fun aPadTheUserClosedStaysClosedThroughALock() {
    assertEquals(Action.NONE, LockGate.action(padVisible = false, locked = true, padShowing = false))
    assertEquals(Action.NONE, LockGate.action(padVisible = false, locked = false, padShowing = false))
  }

  @Test
  fun aShowAskedForWhileLockedWaitsForTheUnlock() {
    assertEquals(Action.NONE, LockGate.action(padVisible = true, locked = true, padShowing = false))
  }

  @Test
  fun aPadAlreadyRightIsLeftAlone() {
    assertEquals(Action.NONE, LockGate.action(padVisible = true, locked = false, padShowing = true))
  }

  @Test
  fun theScreenOffOrTheKeyguardCountsAsLocked() {
    assertTrue(LockGate.locked(interactive = false, keyguardLocked = false))
    assertTrue(LockGate.locked(interactive = true, keyguardLocked = true))
    assertFalse(LockGate.locked(interactive = true, keyguardLocked = false))
  }
}
