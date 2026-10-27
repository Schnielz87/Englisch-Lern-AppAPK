package de.englischlernen.app

import de.englischlernen.app.ui.AiChatRoute
import de.englischlernen.app.ui.LevelRoute
import de.englischlernen.app.ui.SessionRoute
import de.englischlernen.app.ui.TheoryRoute
import kotlinx.serialization.descriptors.PrimitiveKind
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Navigations-Routen duerfen nur einfache Typen enthalten. Enums o. Ae. werden im
 * Release-Build (R8) umbenannt und lassen die App sonst beim Start abstuerzen.
 */
class RouteSafetyTest {
    @Test
    fun routesUseOnlyPrimitiveArguments() {
        listOf(SessionRoute.serializer(), AiChatRoute.serializer(), LevelRoute.serializer(), TheoryRoute.serializer()).forEach { s ->
            val d = s.descriptor
            for (i in 0 until d.elementsCount) {
                val kind = d.getElementDescriptor(i).kind
                assertTrue("${d.serialName}.${d.getElementName(i)} ist kein einfacher Typ ($kind)", kind is PrimitiveKind)
            }
        }
    }
}
