package dev.mnaoumov.selectionpad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PadNotificationTest {

  @Test
  fun postsWhatThePadWants() {
    assertEquals(PadNotification.Content(padShown = true), PadNotification.content(on = true, allowed = true, wanted = true, enabled = true))
    assertEquals(PadNotification.Content(padShown = false), PadNotification.content(on = true, allowed = true, wanted = false, enabled = true))
  }

  @Test
  fun postsNothingWhileTheSwitchIsOff() {
    assertNull(PadNotification.content(on = false, allowed = true, wanted = true, enabled = true))
    assertNull(PadNotification.content(on = false, allowed = true, wanted = null, enabled = true))
  }

  @Test
  fun postsNothingWhenTheSystemDoesNotAllowIt() {
    assertNull(PadNotification.content(on = true, allowed = false, wanted = true, enabled = true))
    assertNull(PadNotification.content(on = true, allowed = false, wanted = null, enabled = true))
  }

  @Test
  fun postsNothingWhileTheServiceIsOff() {
    assertNull(PadNotification.content(on = true, allowed = true, wanted = null, enabled = false))
  }

  @Test
  fun saysNotRunningWhileTheServiceIsEnabledButNotRunning() {
    assertEquals(PadNotification.Content(padShown = null), PadNotification.content(on = true, allowed = true, wanted = null, enabled = true))
  }
}
