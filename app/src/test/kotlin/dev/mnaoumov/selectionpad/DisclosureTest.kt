package dev.mnaoumov.selectionpad

import org.junit.Assert.assertEquals
import org.junit.Test

class DisclosureTest {

  @Test
  fun aFirstRunOpensOnTheConsentStep() {
    assertEquals(Disclosure.Screen.CONSENT, Disclosure.screen(consentGiven = false, serviceEnabled = false))
  }

  @Test
  fun recordedConsentOpensOnTheNormalScreenEvenWithTheServiceOff() {
    assertEquals(Disclosure.Screen.MAIN, Disclosure.screen(consentGiven = true, serviceEnabled = false))
  }

  @Test
  fun recordedConsentWithTheServiceOnOpensOnTheNormalScreen() {
    assertEquals(Disclosure.Screen.MAIN, Disclosure.screen(consentGiven = true, serviceEnabled = true))
  }

  @Test
  fun aServiceAlreadyOnWithNoRecordedConsentSkipsTheConsentStep() {
    // The grant has happened, through an older build that did not record the consent.
    assertEquals(Disclosure.Screen.MAIN, Disclosure.screen(consentGiven = false, serviceEnabled = true))
  }
}
