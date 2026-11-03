package com.dnnypck.capacitiesquicknotepro.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dnnypck.targets.data.model.Space
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                val backupJson = viewModel.getBackupJson()
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(backupJson.toByteArray())
                }
                backupMessage = "Backup saved successfully"
            } catch (e: Exception) {
                backupMessage = "Backup failed: ${e.message}"
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val backupJson = context.contentResolver.openInputStream(it)?.use { inputStream ->
                    inputStream.readBytes().toString(Charsets.UTF_8)
                }
                backupJson?.let { json ->
                    viewModel.restoreFromBackup(json)
                }
            } catch (e: Exception) {
                backupMessage = "Restore failed: ${e.message}"
            }
        }
    }

    LaunchedEffect(backupMessage) {
        backupMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            backupMessage = null
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
        ) {
            if (state.isBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (state.needsOAuthMigration) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "API tokens are no longer supported",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Capacities retired the old public API. Sign in with OAuth to reconnect each space you want to send notes to. Your previous API token will not be used.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "Capacities account",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "Sign in with Capacities. You'll choose a space on their consent screen. Repeat to connect additional spaces.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!state.oauthConfigured) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "OAuth client ID is not set",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "The app developer must register this integration with Capacities and put the issued client ID in local.properties as CAPACITIES_CLIENT_ID. Redirect URI to register:\n${state.redirectUri}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = { viewModel.connect(context) },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.spaces.isEmpty()) "Connect to Capacities" else "Connect another space")
            }

            if (state.spaces.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.requestSignOutAll() },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign out of all spaces")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Connected spaces",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.spaces.isEmpty()) {
                Text(
                    text = "No spaces connected yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.spaces.forEach { space ->
                        SpaceListItem(
                            space = space,
                            onEdit = { viewModel.showEditSpaceDialog(space) },
                            onDelete = { viewModel.requestDisconnect(space) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Backups include OAuth tokens for your connected spaces. Treat the file like a password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        backupLauncher.launch("capacities_backup_$timestamp.json")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Backup Settings")
                }

                OutlinedButton(
                    onClick = {
                        restoreLauncher.launch(arrayOf("application/json"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Restore Settings")
                }
            }
        }

        if (state.showEditDialog) {
            NicknameDialog(
                space = state.editingSpace,
                onDismiss = { viewModel.hideSpaceDialog() },
                onSave = { nickname -> viewModel.saveNickname(nickname) }
            )
        }

        state.showDisconnectConfirm?.let { space ->
            AlertDialog(
                onDismissRequest = { viewModel.cancelDisconnect() },
                title = { Text("Disconnect space?") },
                text = {
                    Text("This signs out of ${space.getDisplayName()} and revokes this app's access on Capacities.")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmDisconnect() }) {
                        Text("Disconnect")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelDisconnect() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (state.showSignOutAllConfirm) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelSignOutAll() },
                title = { Text("Sign out of all spaces?") },
                text = {
                    Text("This disconnects every Capacities space on this device.")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmSignOutAll() }) {
                        Text("Sign out")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelSignOutAll() }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun SpaceListItem(
    space: Space,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = space.nickname ?: "Unnamed Space",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = space.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit nickname")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Disconnect")
                }
            }
        }
    }
}

@Composable
fun NicknameDialog(
    space: Space?,
    onDismiss: () -> Unit,
    onSave: (nickname: String?) -> Unit
) {
    var nickname by remember { mutableStateOf(space?.nickname ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit space nickname") },
        text = {
            Column {
                Text(
                    text = space?.id.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(nickname.ifBlank { null }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
