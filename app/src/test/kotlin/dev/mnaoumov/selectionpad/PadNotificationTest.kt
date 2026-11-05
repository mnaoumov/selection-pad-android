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
  fun theBodyTogglesThePadWhileItRuns() {
    for (shown in listOf(true, false)) {
      val wiring = PadNotification.wiring(shown)
      assertEquals(PadNotification.Target.TOGGLE, wiring.body)
      assertEquals(PadNotification.Target.TOGGLE, wiring.buttons[0].target)
      assertEquals(
        PadNotification.Button(R.string.notification_settings, PadNotification.Target.OPEN_APP),
        wiring.buttons[1],
      )
      assertEquals(2, wiring.buttons.size)
    }
    assertEquals(R.string.notification_close, PadNotification.wiring(true).buttons[0].label)
    assertEquals(R.string.show_pad, PadNotification.wiring(false).buttons[0].label)
  }

  @Test
  fun everyTapLeadsToHelpWhileTheServiceIsNotRunning() {
    assertEquals(
      PadNotification.Wiring(
        PadNotification.Target.OPEN_APP,
        listOf(PadNotification.Button(R.string.notification_what_to_do, PadNotification.Target.OPEN_APP)),
      ),
      PadNotification.wiring(null),
    )
  }

  @Test
  fun saysNotRunningWhileTheServiceIsEnabledButNotRunning() {
    assertEquals(PadNotification.Content(padShown = null), PadNotification.content(on = true, allowed = true, wanted = null, enabled = true))
  }
}
