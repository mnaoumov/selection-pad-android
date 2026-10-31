package dev.mnaoumov.selectionpad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonShortcutTest {

  private val pkg = "dev.mnaoumov.selectionpad"
  private val cls = "dev.mnaoumov.selectionpad.AscAccessibilityService"

  @Test
  fun anEmptyOrMissingTargetListIsNotAssigned() {
    assertFalse(ButtonShortcut.assigned(pkg, cls, listOf(null, "", null)))
  }

  @Test
  fun theShortFormIsAssigned() {
    assertTrue(ButtonShortcut.assigned(pkg, cls, listOf("$pkg/.AscAccessibilityService")))
  }

  @Test
  fun theFullFormAmongOtherTargetsIsAssigned() {
    val list = "com.x8bit.bitwarden/com.x8bit.bitwarden.Autofill:$pkg/$cls"
    assertTrue(ButtonShortcut.assigned(pkg, cls, listOf(null, list)))
  }

  @Test
  fun anotherServiceOfTheSamePackageOrAnotherPackageIsNot() {
    assertFalse(ButtonShortcut.assigned(pkg, cls, listOf("$pkg/.Other", "other.pkg/.AscAccessibilityService")))
  }
}
