package de.englischlernen.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Startet die echte App (ohne Emulator) und klickt durch die wichtigsten Bildschirme.
 * Screenshots landen in app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun waitFor(text: String) = rule.waitUntil(10_000) {
        rule.onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun onboardingHomeAndLesson() {
        waitFor("Expedition beginnen")
        shot("01_onboarding")
        rule.onNodeWithText("Expedition beginnen").performClick()
        rule.onNodeWithText("Weiter").performClick()
        waitFor("Bei A1 starten")
        rule.onNodeWithText("Bei A1 starten").performClick()
        waitFor("Dein nächster Fund")
        shot("02_home")

        rule.onNodeWithText("Expedition").performClick()
        waitFor("Expeditionskarte")
        shot("03_course")
        rule.onNodeWithText("Absoluter Anfänger").performClick()
        waitFor("Begrüßungen")
        shot("04_level")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitFor("Expeditionskarte")

        rule.onNodeWithText("Training").performClick()
        waitFor("Trainingslager")
        shot("05_training")

        rule.onNodeWithText("Finn").performClick()
        waitFor("dein KI-Tutor")
        shot("06_ai")

        rule.onNodeWithText("Fortschritt").performClick()
        waitFor("Schatzkammer")
        shot("07_progress")

        rule.onNodeWithText("Start").performClick()
        waitFor("Los geht's")
        rule.onNodeWithText("Los geht's").performClick()
        waitFor("Wissen")
        shot("08_lesson_info")
        rule.onNodeWithText("Verstanden").performScrollTo().performClick()
        waitFor("Verstanden")
        rule.onNodeWithText("Verstanden").performScrollTo().performClick()
        waitFor("Neue Vokabel")
        shot("09_flashcard")
        repeat(8) {
            rule.onNodeWithText("Weiter").performScrollTo().performClick()
            rule.waitForIdle()
        }
        shot("10_exercise")
    }
}
