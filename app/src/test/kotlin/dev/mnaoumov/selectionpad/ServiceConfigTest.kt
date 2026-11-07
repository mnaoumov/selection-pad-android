package dev.mnaoumov.selectionpad

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the accessibility service's static config against a regression measured on a OnePlus 15
 * (2026-11-04): `flagRequestAccessibilityButton` there makes Android treat the service as always-on
 * (`INVISIBLE_TOGGLE`), enabled only while it has a shortcut. The floating button was then forced
 * on, and removing it switched the whole service off, so neither the tile nor the notification could
 * bring the pad back.
 */
class ServiceConfigTest {
  private fun flags(): List<String> {
    // Gradle runs unit tests with the module directory as the working directory.
    val file = File("src/main/res/xml/accessibility_service_config.xml")
    assertTrue("missing $file (working dir ${File(".").absolutePath})", file.isFile)
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val root = factory.newDocumentBuilder().parse(file).documentElement
    val raw = root.getAttributeNS("http://schemas.android.com/apk/res/android", "accessibilityFlags")
    return raw.split('|').map { it.trim() }.filter { it.isNotEmpty() }
  }

  @Test
  fun `the service never requests the accessibility button`() {
    assertFalse(flags().contains("flagRequestAccessibilityButton"))
  }

  @Test
  fun `the service still retrieves interactive windows`() {
    // The floating toolbar's window, and with it the toolbar mask and the toolbar-aware aim, needs it.
    assertTrue(flags().contains("flagRetrieveInteractiveWindows"))
  }
}
