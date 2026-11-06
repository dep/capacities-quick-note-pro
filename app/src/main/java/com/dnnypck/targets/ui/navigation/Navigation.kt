package com.dnnypck.capacitiesquicknotepro.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dnnypck.capacitiesquicknotepro.ui.main.MainScreen
import com.dnnypck.capacitiesquicknotepro.ui.main.MainViewModel
import com.dnnypck.capacitiesquicknotepro.ui.notes.SavedNotesScreen
import com.dnnypck.capacitiesquicknotepro.ui.notes.SavedNotesViewModel
import com.dnnypck.capacitiesquicknotepro.ui.settings.SettingsScreen
import com.dnnypck.capacitiesquicknotepro.util.ViewModelFactory

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object Settings : Screen("settings")
    object SavedNotes : Screen("saved_notes")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    viewModelFactory: ViewModelFactory,
    sharedText: String? = null,
    oauthMessage: String? = null,
    onOauthMessageShown: () -> Unit = {}
) {
    val mainViewModel: MainViewModel = viewModel(factory = viewModelFactory)
    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(
                viewModel = mainViewModel,
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToSavedNotes = {
                    navController.navigate(Screen.SavedNotes.route)
                },
                sharedText = sharedText,
                oauthMessage = oauthMessage,
                onOauthMessageShown = onOauthMessageShown
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

        composable(Screen.SavedNotes.route) {
            val savedNotesViewModel: SavedNotesViewModel = viewModel(factory = viewModelFactory)
            SavedNotesScreen(
                viewModel = savedNotesViewModel,
                onNavigateBack = { navController.popBackStack() },
                onRestoreToEditor = { id ->
                    mainViewModel.restoreToEditor(id)
                    navController.popBackStack()
                }
            )
        }
    }
}
