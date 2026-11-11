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
  fun `the pad steps aside for Settings unless the user asked for it there`() {
    assertTrue(LockGate.settingsAside(settingsInFront = true, shownOverSettings = false))
    assertFalse(LockGate.settingsAside(settingsInFront = true, shownOverSettings = true))
    assertFalse(LockGate.settingsAside(settingsInFront = false, shownOverSettings = false))
    assertTrue("com.android.settings" in LockGate.SETTINGS_PACKAGES)
  }

  @Test
  fun `a pad stepped aside for a system dialog hides, and returns only when the user still wants it`() {
    assertEquals(Action.HIDE, LockGate.action(padVisible = true, locked = false, padShowing = true, aside = true))
    assertEquals(Action.NONE, LockGate.action(padVisible = true, locked = false, padShowing = false, aside = true))
    assertEquals(Action.SHOW, LockGate.action(padVisible = true, locked = false, padShowing = false, aside = false))
    assertEquals(Action.NONE, LockGate.action(padVisible = false, locked = false, padShowing = false, aside = false))
  }

  @Test
  fun theScreenOffOrTheKeyguardCountsAsLocked() {
    assertTrue(LockGate.locked(interactive = false, keyguardLocked = false))
    assertTrue(LockGate.locked(interactive = true, keyguardLocked = true))
    assertFalse(LockGate.locked(interactive = true, keyguardLocked = false))
  }
}
