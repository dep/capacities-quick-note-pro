package com.dnnypck.capacitiesquicknotepro.ui.notes

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dnnypck.capacitiesquicknotepro.data.notes.NoteDeliveryStatus
import com.dnnypck.capacitiesquicknotepro.data.notes.SavedNote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedNotesScreen(
    viewModel: SavedNotesViewModel,
    onNavigateBack: () -> Unit,
    onRestoreToEditor: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Saved notes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                text = "Notes are stored only on this device. Uninstalling the app, clearing app data, or a device failure can still lose them. Capacities accepting a note does not mean it is visible in your space yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.notes.isEmpty()) {
                Text(
                    text = "No saved notes yet. Anything you send, fail to send, or leave unfinished will show up here.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                state.notes.forEach { note ->
                    SavedNoteCard(
                        note = note,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Capacities note", note.content))
                            viewModel.copied()
                        },
                        onRestore = { onRestoreToEditor(note.id) },
                        onResend = { viewModel.requestResend(note.id) },
                        onDelete = { viewModel.requestDelete(note.id) }
                    )
                }
            }
        }
    }

    state.confirmDeleteId?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Remove this saved note?") },
            text = { Text("This only deletes the copy on this device. It cannot unsay a note Capacities already accepted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            }
        )
    }

    state.confirmResendId?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelResend() },
            title = { Text("Send this note again?") },
            text = { Text("Capacities may already have it. Sending again can create a duplicate.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmResend() }) { Text("Send anyway") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelResend() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SavedNoteCard(
    note: SavedNote,
    onCopy: () -> Unit,
    onRestore: () -> Unit,
    onResend: () -> Unit,
    onDelete: () -> Unit
) {
    val container = when (note.status) {
        NoteDeliveryStatus.ACKNOWLEDGED -> MaterialTheme.colorScheme.surfaceVariant
        NoteDeliveryStatus.UNCERTAIN -> MaterialTheme.colorScheme.tertiaryContainer
        NoteDeliveryStatus.FAILED, NoteDeliveryStatus.QUEUED -> MaterialTheme.colorScheme.errorContainer
        NoteDeliveryStatus.DRAFT -> MaterialTheme.colorScheme.secondaryContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = note.status.label(), style = MaterialTheme.typography.titleSmall)
            val space = note.spaceName?.takeIf { it.isNotBlank() } ?: note.spaceId
            if (space.isNotBlank()) {
                Text(
                    text = "Space: $space",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium
            )
            note.lastError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopy) { Text("Copy") }
                OutlinedButton(onClick = onRestore) { Text("Edit") }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onResend) { Text("Send again") }
                OutlinedButton(onClick = onDelete) { Text("Remove") }
            }
        }
    }
}
