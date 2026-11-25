package com.dnnypck.capacitiesquicknotepro.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.dnnypck.capacitiesquicknotepro.ui.main.components.ContentTextField
import com.dnnypck.capacitiesquicknotepro.ui.main.components.SendButton
import com.dnnypck.targets.data.model.Space

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToSavedNotes: () -> Unit,
    sharedText: String? = null,
    oauthMessage: String? = null,
    onOauthMessageShown: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSpaces()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.persistDraftNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(sharedText) {
        sharedText?.let { text ->
            viewModel.applyIncomingText(text)
        }
    }

    LaunchedEffect(oauthMessage) {
        oauthMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onOauthMessageShown()
        }
    }

    LaunchedEffect(state.message, state.isError) {
        state.message?.let { message ->
            if (!state.isError) {
                snackbarHostState.showSnackbar(message)
                viewModel.clearMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Capacities Quick Note") },
                actions = {
                    TextButton(onClick = onNavigateToSavedNotes) {
                        Text(
                            if (state.unresolvedCount > 0) {
                                "Saved (${state.unresolvedCount})"
                            } else {
                                "Saved notes"
                            }
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
                if (state.unresolvedCount > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "You have ${state.unresolvedCount} note(s) that weren't confirmed with Capacities.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            TextButton(onClick = onNavigateToSavedNotes) {
                                Text("Open Saved notes")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (state.hasCredentials || state.isDemoMode) {
                if (state.needsOAuthMigration && !state.isDemoMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Please sign in with Capacities",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "API tokens no longer work with the Capacities API. Open Settings and tap Connect to Capacities.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            TextButton(onClick = onNavigateToSettings) {
                                Text("Open Settings")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (state.isDemoMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Demo Mode — notes are not saved",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            TextButton(onClick = { viewModel.disableDemoMode() }) {
                                Text(
                                    text = "Exit",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                SpaceSelector(
                    spaces = state.availableSpaces,
                    selectedSpaceId = state.selectedSpaceId,
                    onSpaceSelected = { viewModel.selectSpace(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                ContentTextField(
                    value = state.content,
                    onValueChange = { viewModel.updateContent(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                SendButton(
                    onClick = { viewModel.sendContent() },
                    enabled = state.content.isNotBlank(),
                    isLoading = state.isSending,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                FormattingInstructions(
                    modifier = Modifier.fillMaxWidth()
                )

                // Show detailed error message if present
                state.message?.let { message ->
                    if (state.isError) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                } else {
                    WelcomeCard(
                        onDemoMode = { viewModel.enableDemoMode() },
                        onGoToSettings = onNavigateToSettings,
                        needsOAuthMigration = state.needsOAuthMigration,
                        modifier = Modifier.fillMaxSize()
                    )
            }
        }
    }
}

@Composable
fun WelcomeCard(
    onDemoMode: () -> Unit,
    onGoToSettings: () -> Unit,
    needsOAuthMigration: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome to Capacities Quick Note",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "This app is a companion to Capacities — a powerful, established note-taking app. It lets you quickly capture notes and send them directly to your Capacities workspace.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Don't have Capacities yet? No problem — you can explore this app right now in demo mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDemoMode,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Explore in Demo Mode")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Try all the features without connecting to Capacities. Notes won't be saved.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onGoToSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                if (needsOAuthMigration) {
                    "Reconnect with Capacities"
                } else {
                    "I use Capacities — Set Up Now"
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (needsOAuthMigration) {
                "Capacities retired API tokens. Sign in with OAuth in Settings to keep sending notes."
            } else {
                "You'll sign in with Capacities in Settings and pick a space."
            },
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceSelector(
    spaces: List<Space>,
    selectedSpaceId: String?,
    onSpaceSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedSpace = spaces.find { it.id == selectedSpaceId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedSpace?.getDisplayName() ?: "Select Space",
            onValueChange = {},
            readOnly = true,
            label = { Text("Target Space") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            spaces.forEach { space ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = space.nickname ?: "Unnamed Space",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = space.id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        onSpaceSelected(space.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
fun FormattingInstructions(modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Formatting your notes",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text(
                        text = "You can send formatted notes to Capacities via this extension. You can use all Capacities shortcuts:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val formattingOptions = listOf(
                        "# , ## , ### , and #### for headings",
                        "- for bullets",
                        "1. for numbered lists",
                        "> for quotes",
                        "**text** for bold text",
                        "*text* for italic text",
                        "() for tasks"
                    )

                    formattingOptions.forEach { option ->
                        Text(
                            text = "• $option",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "You can also make #tags and [links](https://google.com)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
