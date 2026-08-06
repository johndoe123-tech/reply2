package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.viewmodel.SettingsViewModel

import com.example.data.supabase.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val prefs by viewModel.userPreferences.collectAsState()
    val personalMemory by viewModel.personalMemory.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val isLoadingModels by viewModel.isLoadingModels.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var urlState by remember(prefs?.ollamaUrl) {
        mutableStateOf(prefs?.ollamaUrl ?: "http://192.168.1.5:11434")
    }
    var aboutMeState by remember(personalMemory?.aboutMe) {
        mutableStateOf(personalMemory?.aboutMe ?: "")
    }
    var systemPromptState by remember(personalMemory?.globalSystemPrompt) {
        mutableStateOf(personalMemory?.globalSystemPrompt ?: "")
    }
    var sharingRulesState by remember(personalMemory?.sharingRules) {
        mutableStateOf(personalMemory?.sharingRules ?: "")
    }

    var dropdownExpanded by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App & Ollama Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Ollama Server Configuration
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ollama Local Server",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = urlState,
                                onValueChange = { urlState = it },
                                label = { Text("Ollama Base URL") },
                                placeholder = { Text("http://192.168.1.5:11434") },
                                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ollama_url_input"),
                                singleLine = true
                            )

                            Button(
                                onClick = { viewModel.updateOllamaUrl(urlState) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("save_ollama_url_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "If Ollama runs on your PC, use your PC's local network IP (e.g. 192.168.1.X:11434), not 127.0.0.1 — 127.0.0.1 on your phone refers to the phone itself.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.fetchModels(urlState) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("fetch_models_button"),
                                enabled = !isLoadingModels
                            ) {
                                if (isLoadingModels) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fetch Models")
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.testConnection(urlState) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_connection_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Ping")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Model Picker Dropdown
                        Text("Active Model", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = prefs?.selectedModel ?: "Select model",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("model_picker_dropdown")
                            )
                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                if (availableModels.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("llama3 (default)") },
                                        onClick = {
                                            viewModel.selectModel("llama3")
                                            dropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("mistral") },
                                        onClick = {
                                            viewModel.selectModel("mistral")
                                            dropdownExpanded = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("gemma2") },
                                        onClick = {
                                            viewModel.selectModel("gemma2")
                                            dropdownExpanded = false
                                        }
                                    )
                                } else {
                                    availableModels.forEach { modelInfo ->
                                        DropdownMenuItem(
                                            text = { Text(modelInfo.name) },
                                            onClick = {
                                                viewModel.selectModel(modelInfo.name)
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Personal Memory & System Prompt Editors
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Global System Prompt & Personal Memory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = systemPromptState,
                            onValueChange = { systemPromptState = it },
                            label = { Text("Global System Prompt") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("global_system_prompt_input"),
                            minLines = 3,
                            maxLines = 6
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = aboutMeState,
                            onValueChange = { aboutMeState = it },
                            label = { Text("About Me (Personality & Profile)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("about_me_input"),
                            minLines = 3,
                            maxLines = 5
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = sharingRulesState,
                            onValueChange = { sharingRulesState = it },
                            label = { Text("Sharing Rules (Allowed vs Private Info)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sharing_rules_input"),
                            minLines = 3,
                            maxLines = 5
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.savePersonalMemory(
                                    aboutMe = aboutMeState,
                                    systemPrompt = systemPromptState,
                                    sharingRules = sharingRulesState
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_personal_memory_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Prompts & Memory")
                        }
                    }
                }
            }

            // 3. Global Auto-Reply Switch
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
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
                                text = "Global Auto-Reply Master Switch",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "When toggled off, no messages will be auto-replied under any circumstance.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs?.autoReplyEnabled == true,
                            onCheckedChange = { viewModel.updateAutoReplyEnabled(it) },
                            modifier = Modifier.testTag("global_auto_reply_switch")
                        )
                    }
                }
            }

            // 4. Supabase Account & Cloud Sync
            item {
                val scope = rememberCoroutineScope()
                val auth = remember { SupabaseClientProvider.client.auth }
                val currentUser = remember { auth.currentUserOrNull() }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Account & Supabase Cloud Sync",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Signed in as: ${currentUser?.email ?: "Not logged in"}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.forceFullSync() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("force_full_sync_button")
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Force Full Sync to Cloud")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    auth.signOut()
                                    onNavigateBack()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
