package com.dnnypck.capacitiesquicknotepro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.rememberNavController
import com.dnnypck.capacitiesquicknotepro.ui.navigation.AppNavigation
import com.dnnypck.capacitiesquicknotepro.ui.theme.CapacitiesQuickNoteTheme
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager
import com.dnnypck.capacitiesquicknotepro.util.ViewModelFactory

class MainActivity : ComponentActivity() {
    private lateinit var viewModelFactory: ViewModelFactory
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferencesManager = PreferencesManager(applicationContext)
        preferencesManager.migrateFromLegacySpaceId()
        viewModelFactory = ViewModelFactory(preferencesManager)

        handleIntent(intent)

        enableEdgeToEdge()
        setContent {
            CapacitiesQuickNoteTheme {
                val navController = rememberNavController()
                AppNavigation(
                    navController = navController,
                    viewModelFactory = viewModelFactory,
                    sharedText = sharedText.value
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                sharedText.value = text
            }
        }
    }
}
