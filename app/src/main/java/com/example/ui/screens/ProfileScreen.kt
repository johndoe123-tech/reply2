package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supabase.SupabaseClientProvider
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProfileViewModel
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val auth = remember { SupabaseClientProvider.client.auth }
    val currentUser = remember { auth.currentUserOrNull() }

    val personalMemory by viewModel.personalMemory.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    var aboutMeState by remember(personalMemory?.aboutMe) {
        mutableStateOf(personalMemory?.aboutMe ?: "")
    }
    var systemPromptState by remember(personalMemory?.globalSystemPrompt) {
        mutableStateOf(personalMemory?.globalSystemPrompt ?: "")
    }
    var sharingRulesState by remember(personalMemory?.sharingRules) {
        mutableStateOf(personalMemory?.sharingRules ?: "")
    }

    var showSignOutDialog by remember { mutableStateOf(false) }
    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var infoToastMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val userInitial = remember(currentUser?.email) {
        currentUser?.email?.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Profile & AI Memory", fontWeight = FontWeight.Bold) },
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
            // 1. User Header Badge Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_profile_header_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar Circle with Gradient Accent
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(AuthAccentTeal, AuthAccentCoral)
                                    )
                                )
                                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = userInitial,
                                fontFamily = SoraFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 36.sp,
                                color = Color(0xFF120E1C)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = currentUser?.email ?: "Guest User",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            color = StatusGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(StatusGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Supabase Sync Active",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StatusGreen
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "UID: ${currentUser?.id?.take(12) ?: "Local Session"}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Knowledge Base & AI Sync Stats
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Knowledge Base Statistics",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = { viewModel.triggerCloudRestore() },
                                enabled = !isSyncing,
                                modifier = Modifier.testTag("sync_cloud_button")
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.CloudDownload, contentDescription = "Sync from Cloud")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatBox(
                                label = "Contacts",
                                value = stats.totalContacts.toString(),
                                icon = Icons.Default.People,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Memories",
                                value = stats.totalMemories.toString(),
                                icon = Icons.Default.Psychology,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Relations",
                                value = stats.knownRelations.toString(),
                                icon = Icons.Default.ContactPhone,
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                label = "Auto-Replies",
                                value = stats.autoRepliedCount.toString(),
                                icon = Icons.Default.AutoMode,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 3. Personal Memory & AI Persona Settings
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Personal Persona & Memory",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = aboutMeState,
                            onValueChange = { aboutMeState = it },
                            label = { Text("About Me (User Profile & Bio)") },
                            placeholder = { Text("e.g. Software engineer, loves basketball and coffee.") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_about_me_input"),
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = systemPromptState,
                            onValueChange = { systemPromptState = it },
                            label = { Text("Global System Prompt") },
                            placeholder = { Text("How the AI should represent you when replying...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_system_prompt_input"),
                            minLines = 3,
                            maxLines = 6,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = sharingRulesState,
                            onValueChange = { sharingRulesState = it },
                            label = { Text("Privacy & Sharing Rules") },
                            placeholder = { Text("What information is allowed vs strictly forbidden...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_sharing_rules_input"),
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp)
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
                                .testTag("profile_save_memory_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Persona & Memory")
                        }
                    }
                }
            }

            // 4. Account Actions Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Account Security & Management",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = { showResetPasswordDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_reset_password_button")
                        ) {
                            Icon(Icons.Default.LockReset, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send Password Reset Email")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { showSignOutDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_sign_out_button")
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

    // Sign Out Confirmation Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out of your account?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        scope.launch {
                            try {
                                com.example.data.db.AppDatabase.getDatabase(context).clearUserData()
                                com.example.data.pref.UserPreferencesRepository.getInstance(context).updateLastUserId(null)
                                auth.signOut()
                            } catch (_: Exception) {}
                            onSignOut()
                        }
                    }
                ) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Password Reset Dialog
    if (showResetPasswordDialog) {
        var emailForReset by remember { mutableStateOf(currentUser?.email ?: "") }
        AlertDialog(
            onDismissRequest = { showResetPasswordDialog = false },
            title = { Text("Reset Password", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("We'll send a password reset link to your email address:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = emailForReset,
                        onValueChange = { emailForReset = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetPasswordDialog = false
                        if (emailForReset.isNotBlank()) {
                            scope.launch {
                                try {
                                    auth.resetPasswordForEmail(emailForReset.trim())
                                    snackbarHostState.showSnackbar("Password reset email sent to $emailForReset")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Failed: ${e.localizedMessage}")
                                }
                            }
                        }
                    }
                ) {
                    Text("Send Email", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
