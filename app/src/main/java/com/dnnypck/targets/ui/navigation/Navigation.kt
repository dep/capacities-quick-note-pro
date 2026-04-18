package com.dnnypck.capacitiesquicknotepro.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dnnypck.capacitiesquicknotepro.ui.main.MainScreen
import com.dnnypck.capacitiesquicknotepro.ui.settings.SettingsScreen
import com.dnnypck.capacitiesquicknotepro.util.ViewModelFactory

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModelFactory: ViewModelFactory,
    sharedText: String? = null
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                viewModel = viewModel(factory = viewModelFactory),
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                sharedText = sharedText
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = viewModel(factory = viewModelFactory),
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
