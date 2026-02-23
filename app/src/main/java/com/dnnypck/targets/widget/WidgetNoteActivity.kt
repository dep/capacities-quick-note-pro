package com.dnnypck.capacitiesquicknotepro.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dnnypck.capacitiesquicknotepro.data.network.postToTarget
import com.dnnypck.capacitiesquicknotepro.ui.theme.CapacitiesQuickNoteTheme
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WidgetNoteActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show keyboard automatically
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        preferencesManager = PreferencesManager(applicationContext)

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            CapacitiesQuickNoteTheme {
                WidgetNoteScreen(
                    preferencesManager = preferencesManager,
                    onSend = { noteText ->
                        sendNote(noteText)
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun sendNote(noteText: String) {
        if (noteText.isBlank()) {
            Toast.makeText(this, "Please enter a note", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedSpaceId = preferencesManager.getSelectedSpaceId()
        if (selectedSpaceId == null) {
            Toast.makeText(this, "Please select a space first", Toast.LENGTH_SHORT).show()
            return
        }

        val apiKey = preferencesManager.getApiKey()
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Please configure API key in settings", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "Content-Type" to "application/json"
                )

                val body = """
                    {
                        "spaceId": "$selectedSpaceId",
                        "mdText": "${noteText.replace("\"", "\\\"").replace("\n", "\\n")}",
                        "origin": "commandPalette"
                    }
                """.trimIndent()

                val result = postToTarget(
                    url = "https://api.capacities.io/save-to-daily-note",
                    headers = headers,
                    body = body
                )

                result.fold(
                    onSuccess = {
                        Toast.makeText(
                            this@WidgetNoteActivity,
                            "Note sent successfully!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // Update the widget
                        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                        CapacitiesWidget.updateAppWidget(
                            applicationContext,
                            appWidgetManager,
                            appWidgetId
                        )

                        finish()
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this@WidgetNoteActivity,
                            "Failed: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@WidgetNoteActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetNoteScreen(
    preferencesManager: PreferencesManager,
    onSend: (String) -> Unit,
    onCancel: () -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    val spaces = remember { preferencesManager.getSpaces() }
    val selectedSpaceId = remember { preferencesManager.getSelectedSpaceId() }
    val selectedSpace = spaces.find { it.id == selectedSpaceId }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-focus the text field when the screen opens
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quick Note") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Space: ${selectedSpace?.getDisplayName() ?: "None selected"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Your note") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .focusRequester(focusRequester),
                maxLines = 10
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = { onSend(noteText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Send")
                }
            }
        }
    }
}
