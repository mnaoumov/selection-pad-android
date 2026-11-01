package dev.mnaoumov.selectionpad

import dev.mnaoumov.selectionpad.ServiceStatus.State
import org.junit.Assert.assertEquals
import org.junit.Test

/** Enabled is not running (OnePlus 15, 2026-10-31): the three states the toggles tell apart. */
class ServiceStatusTest {

  @Test
  fun aConnectedInstanceIsRunning() {
    assertEquals(State.RUNNING, ServiceStatus.state(enabled = true, connected = true))
  }

  @Test
  fun enabledWithNoInstanceIsNotRunning() {
    assertEquals(State.NOT_RUNNING, ServiceStatus.state(enabled = true, connected = false))
  }

  @Test
  fun notEnabledIsOff() {
    assertEquals(State.OFF, ServiceStatus.state(enabled = false, connected = false))
  }

  @Test
  fun aConnectionCountsEvenBeforeTheListIsReadBack() {
    assertEquals(State.RUNNING, ServiceStatus.state(enabled = false, connected = true))
  }

  @Test
  fun theLifecycleFileKeepsItsLastLines() {
    assertEquals(listOf("b", "c"), Diag.trimmed(listOf("a", "b", "c"), 2))
    assertEquals(listOf("a"), Diag.trimmed(listOf("a"), 2))
  }
}
