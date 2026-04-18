package com.dnnypck.capacitiesquicknotepro.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dnnypck.capacitiesquicknotepro.ui.theme.CapacitiesQuickNoteTheme
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager

class WidgetSpaceSelectionActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED initially
        setResult(Activity.RESULT_CANCELED)

        // Find the widget id from the intent
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If they gave us an invalid widget ID, finish immediately
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val preferencesManager = PreferencesManager(applicationContext)

        setContent {
            CapacitiesQuickNoteTheme {
                SpaceSelectionScreen(
                    preferencesManager = preferencesManager,
                    onSpaceSelected = { spaceId ->
                        preferencesManager.saveSelectedSpaceId(spaceId)

                        // Update the widget
                        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                        CapacitiesWidget.updateAppWidget(
                            applicationContext,
                            appWidgetManager,
                            appWidgetId
                        )

                        // Return success
                        val resultValue = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(Activity.RESULT_OK, resultValue)
                        finish()
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceSelectionScreen(
    preferencesManager: PreferencesManager,
    onSpaceSelected: (String) -> Unit,
    onCancel: () -> Unit
) {
    val spaces = remember { preferencesManager.getSpaces() }
    val selectedSpaceId = remember { preferencesManager.getSelectedSpaceId() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Space") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (spaces.isEmpty()) {
                Text(
                    text = "No spaces configured. Please configure spaces in the app first.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            } else {
                Text(
                    text = "Select a space for the widget:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                spaces.forEach { space ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSpaceSelected(space.id) },
                        colors = if (space.id == selectedSpaceId) {
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            CardDefaults.cardColors()
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = space.nickname ?: "Unnamed Space",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = space.id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
