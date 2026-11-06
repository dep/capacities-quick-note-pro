package com.dnnypck.capacitiesquicknotepro.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteOutbox
import com.dnnypck.capacitiesquicknotepro.data.notes.SavedNote
import com.dnnypck.capacitiesquicknotepro.data.notes.SendCoordinator
import com.dnnypck.capacitiesquicknotepro.data.notes.SharedNoteRuntime
import com.dnnypck.capacitiesquicknotepro.data.repository.CapacitiesRepository
import com.dnnypck.capacitiesquicknotepro.ui.theme.CapacitiesQuickNoteTheme
import com.dnnypck.capacitiesquicknotepro.util.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WidgetNoteActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var repository: CapacitiesRepository
    private lateinit var outbox: NoteOutbox
    private lateinit var sendCoordinator: SendCoordinator
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var sending = false
    private var submitted = false
    private var lastTyped = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        preferencesManager = PreferencesManager(applicationContext)
        repository = CapacitiesRepository(preferencesManager.tokenStore)
        outbox = SharedNoteRuntime.outbox(applicationContext.filesDir)
        sendCoordinator = SendCoordinator(
            outbox,
            SharedNoteRuntime.sendGate,
            repository.asNoteSender()
        )
        outbox.snapshotActiveDraftIfNeeded("")

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            var sendingUi by remember { mutableStateOf(false) }
            CapacitiesQuickNoteTheme {
                WidgetNoteScreen(
                    preferencesManager = preferencesManager,
                    sending = sendingUi,
                    onTextChange = { text ->
                        lastTyped = text
                        if (!submitted) {
                            outbox.saveActiveDraft(text, preferencesManager.getSelectedSpaceId())
                        }
                    },
                    onSend = { noteText ->
                        sendingUi = true
                        sendNote(noteText) { sendingUi = false }
                    },
                    onCancel = { text ->
                        retainIfUnsent(text)
                        finish()
                    }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!submitted && lastTyped.isNotBlank()) {
            outbox.saveActiveDraft(lastTyped, preferencesManager.getSelectedSpaceId())
        }
    }

    private fun sendNote(noteText: String, onFinished: () -> Unit = {}) {
        if (sending) {
            onFinished()
            return
        }
        if (noteText.isBlank()) {
            Toast.makeText(this, "Please enter a note", Toast.LENGTH_SHORT).show()
            onFinished()
            return
        }

        val selectedSpaceId = preferencesManager.getSelectedSpaceId()
        if (selectedSpaceId == null) {
            Toast.makeText(this, "Please select a space first", Toast.LENGTH_SHORT).show()
            onFinished()
            return
        }

        if (!preferencesManager.hasRequiredSettings()) {
            outbox.recordDraft(
                noteText,
                selectedSpaceId,
                preferencesManager.getSpaces().find { it.id == selectedSpaceId }?.getDisplayName(),
                SavedNote.SOURCE_WIDGET
            )
            Toast.makeText(this, "Please connect Capacities in settings. The note is in Saved notes.", Toast.LENGTH_SHORT).show()
            onFinished()
            return
        }

        sending = true
        val spaceName = preferencesManager.getSpaces().find { it.id == selectedSpaceId }?.getDisplayName()
        lifecycleScope.launch {
            try {
                val result = sendCoordinator.submit(
                    content = noteText,
                    spaceId = selectedSpaceId,
                    spaceName = spaceName,
                    source = SavedNote.SOURCE_WIDGET
                )
                Toast.makeText(this@WidgetNoteActivity, result.message, Toast.LENGTH_LONG).show()
                if (result.clearEditor) {
                    submitted = true
                    outbox.clearActiveDraft()
                    val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                    CapacitiesWidget.updateAppWidget(
                        applicationContext,
                        appWidgetManager,
                        appWidgetId
                    )
                    finish()
                }
            } catch (e: Exception) {
                outbox.recordDraft(noteText, selectedSpaceId, spaceName, SavedNote.SOURCE_WIDGET)
                Toast.makeText(
                    this@WidgetNoteActivity,
                    "Couldn't finish sending. Check Saved notes. ${NoteOutbox.sanitizeError(e.message ?: e.javaClass.simpleName)}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                sending = false
                onFinished()
            }
        }
    }

    private fun retainIfUnsent(text: String) {
        if (submitted || text.isBlank()) return
        val spaceId = preferencesManager.getSelectedSpaceId().orEmpty()
        val spaceName = preferencesManager.getSpaces()
            .find { it.id == spaceId }?.getDisplayName()
        outbox.recordDraft(text, spaceId, spaceName, SavedNote.SOURCE_WIDGET)
        outbox.clearActiveDraft()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetNoteScreen(
    preferencesManager: PreferencesManager,
    sending: Boolean = false,
    onTextChange: (String) -> Unit = {},
    onSend: (String) -> Unit,
    onCancel: (String) -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    val spaces = remember { preferencesManager.getSpaces() }
    val selectedSpaceId = remember { preferencesManager.getSelectedSpaceId() }
    val selectedSpace = spaces.find { it.id == selectedSpaceId }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

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
                onValueChange = {
                    noteText = it
                    onTextChange(it)
                },
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
                    onClick = { onCancel(noteText) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = { onSend(noteText) },
                    enabled = !sending,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (sending) "Sending…" else "Send")
                }
            }
        }
    }
}
