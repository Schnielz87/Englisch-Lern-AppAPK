package de.englischlernen.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.englischlernen.app.AppContainer
import de.englischlernen.app.data.prefs.Settings
import de.englischlernen.app.ui.components.LocalAppContainer
import de.englischlernen.app.ui.screens.CourseScreen
import de.englischlernen.app.ui.screens.Dashboard
import de.englischlernen.app.ui.screens.DashboardViewModel
import de.englischlernen.app.ui.screens.HomeScreen
import de.englischlernen.app.ui.screens.LevelScreen
import de.englischlernen.app.ui.screens.OnboardingScreen
import de.englischlernen.app.ui.screens.ProgressScreen
import de.englischlernen.app.ui.screens.SettingsScreen
import de.englischlernen.app.ui.screens.TheoryScreen
import de.englischlernen.app.ui.screens.TrainingScreen
import de.englischlernen.app.ui.session.SessionMode
import de.englischlernen.app.ui.session.SessionScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object HomeRoute
@Serializable object CourseRoute
@Serializable object TrainingRoute
@Serializable object ProgressRoute
@Serializable object SettingsRoute
@Serializable data class LevelRoute(val levelId: String)
@Serializable data class TheoryRoute(val lessonId: String)
// Routen enthalten nur einfache Typen (String/Int) – robust gegen Code-Verkleinerung (R8).
@Serializable data class SessionRoute(val mode: String, val arg: String = "")
@Serializable object AiRoute
@Serializable data class AiChatRoute(val mode: String, val scenario: Int = 0)

private data class Tab(val route: Any, val cls: KClass<*>, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, "Start", Icons.Filled.Home),
    Tab(CourseRoute, CourseRoute::class, "Expedition", Icons.Filled.Explore),
    Tab(TrainingRoute, TrainingRoute::class, "Training", Icons.Filled.FitnessCenter),
    Tab(AiRoute, AiRoute::class, "Finn", Icons.Filled.AutoAwesome),
    Tab(ProgressRoute, ProgressRoute::class, "Fortschritt", Icons.Filled.Insights),
)

@Composable
fun AppRoot(container: AppContainer, settings: Settings) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        var pendingPlacement by rememberSaveable { mutableStateOf(false) }
        if (!settings.onboardingDone) {
            OnboardingScreen { placement -> pendingPlacement = placement }
            return@CompositionLocalProvider
        }
        val vm: DashboardViewModel = viewModel { DashboardViewModel(container) }
        val dashboard by vm.state.collectAsStateWithLifecycle()
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val dest = entry?.destination
        val showBar = tabs.any { t -> dest?.hasRoute(t.cls) == true }

        LaunchedEffect(pendingPlacement) {
            if (pendingPlacement) {
                pendingPlacement = false
                nav.navigate(SessionRoute(SessionMode.PLACEMENT.name))
            }
        }

        fun start(mode: SessionMode, arg: String) = nav.navigate(SessionRoute(mode.name, arg))

        Scaffold(bottomBar = {
            if (showBar) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = dest?.hasRoute(t.cls) == true,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        }) { pad ->
            NavHost(nav, startDestination = HomeRoute, modifier = Modifier.padding(if (showBar) pad else androidx.compose.foundation.layout.PaddingValues())) {
                composable<HomeRoute> { WithDashboard(dashboard) { d -> HomeScreen(d, ::start, onOpenCourse = { nav.navigate(CourseRoute) { launchSingleTop = true } }, onSettings = { nav.navigate(SettingsRoute) }) } }
                composable<CourseRoute> { WithDashboard(dashboard) { d -> CourseScreen(d) { nav.navigate(LevelRoute(it)) } } }
                composable<TrainingRoute> { WithDashboard(dashboard) { d -> TrainingScreen(d, ::start) } }
                composable<ProgressRoute> { WithDashboard(dashboard) { d -> ProgressScreen(d) } }
                composable<SettingsRoute> { SettingsScreen(settings, onBack = { nav.popBackStack() }) { start(SessionMode.PLACEMENT, "") } }
                composable<AiRoute> { de.englischlernen.app.ui.ai.AiHubScreen { mode, sc -> nav.navigate(AiChatRoute(mode.name, sc)) } }
                composable<AiChatRoute> { e ->
                    val r = e.toRoute<AiChatRoute>()
                    de.englischlernen.app.ui.ai.AiChatScreen(de.englischlernen.app.ui.ai.AiMode.valueOf(r.mode), r.scenario) { nav.popBackStack() }
                }
                composable<LevelRoute> { e ->
                    val r = e.toRoute<LevelRoute>()
                    WithDashboard(dashboard) { d ->
                        LevelScreen(d, r.levelId, onBack = { nav.popBackStack() }, onStart = ::start, onTheory = { nav.navigate(TheoryRoute(it)) })
                    }
                }
                composable<TheoryRoute> { e -> TheoryScreen(e.toRoute<TheoryRoute>().lessonId) { nav.popBackStack() } }
                composable<SessionRoute> { e ->
                    val r = e.toRoute<SessionRoute>()
                    SessionScreen(
                        SessionMode.valueOf(r.mode), r.arg,
                        onClose = { nav.popBackStack() },
                        onOpenLesson = { id ->
                            nav.navigate(SessionRoute(SessionMode.LESSON.name, id)) {
                                popUpTo<SessionRoute> { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WithDashboard(d: Dashboard?, content: @Composable (Dashboard) -> Unit) {
    if (d == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else content(d)
}
