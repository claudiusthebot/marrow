package rocks.talon.marrow.phone

import org.junit.Assert.assertNotNull
import org.junit.Test
import rocks.talon.marrow.phone.ui.icons.MarrowIcons

class MarrowIconsInitTest {
    @Test fun all_icons_initialize() {
        assertNotNull(MarrowIcons.Device)
        assertNotNull(MarrowIcons.System)
        assertNotNull(MarrowIcons.Battery)
        assertNotNull(MarrowIcons.Cpu)
        assertNotNull(MarrowIcons.Memory)
        assertNotNull(MarrowIcons.Storage)
        assertNotNull(MarrowIcons.Display)
        assertNotNull(MarrowIcons.Network)
        assertNotNull(MarrowIcons.Sensors)
        assertNotNull(MarrowIcons.Cameras)
        assertNotNull(MarrowIcons.BuildFlags)
        assertNotNull(MarrowIcons.Software)
        assertNotNull(MarrowIcons.Wordmark)
        assertNotNull(MarrowIcons.Watch)
    }

    @Test fun for_section_returns_non_null_for_every_known_id() {
        // Iterates Sections constants and asserts forSection returns non-null.
        // Pulls the constants reflectively from the shared Sections object so
        // future additions are auto-covered.
        val sections = Class.forName("rocks.talon.marrow.shared.Sections").declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        sections.forEach { f ->
            f.isAccessible = true
            val id = f.get(null) as String
            assertNotNull("forSection($id) returned null", MarrowIcons.forSection(id))
        }
    }
}
