package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.db.ActivityLogEntry
import com.example.data.db.Message
import com.example.ui.theme.StatusAmber
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs by viewModel.userPreferences.collectAsState()
    val activityFeed by viewModel.recentActivity.collectAsState()
    val systemLogs by viewModel.activityLogs.collectAsState()
    val isPermissionGranted by viewModel.isNotificationAccessGranted.collectAsState()
    val simulationResult by viewModel.simulatingState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Diagnostic Activity Logs, 1 = Messages Feed
    var logFilter by remember { mutableStateOf("ALL") } // ALL, ERRORS, AUTO_REPLIED

    val filteredLogs = remember(systemLogs, logFilter) {
        when (logFilter) {
            "ERRORS" -> systemLogs.filter { it.eventType == "ERROR" }
            "AUTO_REPLIED" -> systemLogs.filter { it.eventType == "AUTO_REPLIED" }
            else -> systemLogs
        }
    }

    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.checkNotificationAccess()
    }

    val hasNotifAccess = isNotificationListenerEnabled(context)
    val hasPostNotif = isPostNotificationsPermissionGranted(context)
    val isSetupIncomplete = !hasNotifAccess || !hasPostNotif

    var testContactName by remember { mutableStateOf("David") }
    var testMessageText by remember { mutableStateOf("Hey, where are you right now?") }

    val isRunning = prefs?.autoReplyEnabled == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AutoReply",
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToProfile,
                        modifier = Modifier.testTag("home_profile_button")
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "User Profile")
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Service Control Header Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRunning) MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (isRunning) StatusGreen else StatusRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isRunning) "AutoReply Running" else "AutoReply Paused",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Switch(
                                checked = isRunning,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                                postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        }
                                    }
                                    viewModel.toggleAutoReply(enable)
                                },
                                modifier = Modifier.testTag("start_stop_switch")
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isRunning)
                                "Background service active. Listening to WhatsApp notifications and replying via local Ollama."
                            else "Service stopped. Incoming WhatsApp messages will not be auto-replied.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 2. Setup Incomplete Banner Card if required permissions are missing
            if (isSetupIncomplete) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSetup() }
                            .testTag("setup_incomplete_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Setup incomplete — tap to finish",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Required notification listener or posting permissions are missing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // 3. Status Card (Active Model, Ollama URL, Connection status)
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ollama System Status",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Server Endpoint", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = prefs?.ollamaUrl ?: "Not Configured",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Active Model", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = prefs?.selectedModel ?: "None",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Status: ${prefs?.connectionStatus ?: "Unknown"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 4. In-App Simulator Section for testing
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AutoReply In-App Simulator",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Test decision engine & Ollama generation directly without waiting for real WhatsApp notifications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = testContactName,
                            onValueChange = { testContactName = it },
                            label = { Text("Sender Name") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sim_sender_name_input"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = testMessageText,
                            onValueChange = { testMessageText = it },
                            label = { Text("Test Message Text") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sim_message_text_input"),
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                viewModel.triggerSimulationTest(testContactName, testMessageText)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("run_simulation_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Simulate Incoming WhatsApp Message")
                        }

                        AnimatedVisibility(visible = simulationResult != null) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Simulation Result:",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                            IconButton(
                                                onClick = { viewModel.clearSimulationState() },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Close")
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = simulationResult ?: "",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Activity Log & Diagnostic Feed Header & Tabs
            item {
                Column {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Text(
                                    "System Diagnostics (${systemLogs.size})",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            icon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                            modifier = Modifier.testTag("tab_diagnostics")
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Text(
                                    "Messages Feed (${activityFeed.size})",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            icon = { Icon(Icons.Default.Chat, contentDescription = null) },
                            modifier = Modifier.testTag("tab_messages")
                        )
                    }

                    if (selectedTab == 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = logFilter == "ALL",
                                    onClick = { logFilter = "ALL" },
                                    label = { Text("All (${systemLogs.size})") },
                                    modifier = Modifier.testTag("filter_all")
                                )
                                FilterChip(
                                    selected = logFilter == "ERRORS",
                                    onClick = { logFilter = "ERRORS" },
                                    label = { Text("Errors (${systemLogs.count { it.eventType == "ERROR" }})") },
                                    leadingIcon = if (logFilter == "ERRORS") {
                                        { Icon(Icons.Default.Error, contentDescription = null, tint = StatusRed) }
                                    } else null,
                                    modifier = Modifier.testTag("filter_errors")
                                )
                                FilterChip(
                                    selected = logFilter == "AUTO_REPLIED",
                                    onClick = { logFilter = "AUTO_REPLIED" },
                                    label = { Text("Replies (${systemLogs.count { it.eventType == "AUTO_REPLIED" }})") },
                                    modifier = Modifier.testTag("filter_replies")
                                )
                            }

                            if (systemLogs.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearActivityLogs() },
                                    modifier = Modifier.testTag("clear_logs_button")
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Logs", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                // Render System Diagnostic Logs
                if (filteredLogs.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircleOutline,
                                        contentDescription = null,
                                        tint = StatusGreen,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (logFilter == "ERRORS") "No errors logged! Everything is operating normally."
                                        else "No system activity logs recorded yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(filteredLogs) { logEntry ->
                        ActivityLogEntryItem(entry = logEntry)
                    }
                }
            } else {
                // Render Messages Activity Log items
                if (activityFeed.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Inbox,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No recent auto-reply activity recorded yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(activityFeed) { msg ->
                        ActivityFeedItem(msg = msg)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
fun ActivityFeedItem(msg: Message) {
    val dateFormat = remember { SimpleDateFormat("HH:mm, MMM d", Locale.getDefault()) }
    val formattedTime = remember(msg.timestamp) { dateFormat.format(Date(msg.timestamp)) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = if (msg.sender == "me") Icons.Default.Send else Icons.Default.MarkUnreadChatAlt,
                contentDescription = null,
                tint = if (msg.wasAutoReplied) StatusGreen else StatusAmber,
                modifier = Modifier
                    .size(24.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = msg.contactId,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = if (msg.wasAutoReplied) "Auto-Replied"
                                else if (msg.sender == "me") "Sent" else "Incoming",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (msg.wasAutoReplied) StatusGreen.copy(alpha = 0.15f)
                            else StatusAmber.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityLogEntryItem(entry: ActivityLogEntry) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss, MMM d", Locale.getDefault()) }
    val formattedTime = remember(entry.timestamp) { dateFormat.format(Date(entry.timestamp)) }

    val (badgeColor, badgeText, icon) = when (entry.eventType) {
        "ERROR" -> Triple(StatusRed, "ERROR", Icons.Default.ErrorOutline)
        "AUTO_REPLIED" -> Triple(StatusGreen, "AUTO-REPLIED", Icons.Default.CheckCircle)
        "NOTIFIED_USER" -> Triple(StatusAmber, "ALERT", Icons.Default.NotificationsActive)
        "MESSAGE_RECEIVED" -> Triple(MaterialTheme.colorScheme.tertiary, "RECEIVED", Icons.Default.Inbox)
        "LISTENER_CONNECTED" -> Triple(MaterialTheme.colorScheme.primary, "CONNECTED", Icons.Default.Power)
        "LISTENER_DISCONNECTED" -> Triple(StatusRed, "DISCONNECTED", Icons.Default.PowerOff)
        "HEALTH_CHECK" -> Triple(MaterialTheme.colorScheme.secondary, "HEALTH", Icons.Default.HealthAndSafety)
        else -> Triple(MaterialTheme.colorScheme.outline, entry.eventType, Icons.Default.Info)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (entry.eventType == "ERROR") MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("activity_log_item_${entry.id}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (!entry.contactId.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.contactId,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.eventType == "ERROR") MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
