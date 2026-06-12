package com.mimo.remote.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mimo.remote.ui.home.HomeScreen
import com.mimo.remote.ui.session.SessionScreen
import com.mimo.remote.ui.memory.MemoryScreen
import com.mimo.remote.ui.task.TaskScreen
import com.mimo.remote.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val SESSION = "session"
    const val MEMORY = "memory"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
}

@Composable
fun MainNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToSession = { navController.navigate(Routes.SESSION) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SESSION) {
            SessionScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToMemory = { navController.navigate(Routes.MEMORY) },
                onNavigateToTasks = { navController.navigate(Routes.TASKS) }
            )
        }
        composable(Routes.MEMORY) {
            MemoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TASKS) {
            TaskScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
