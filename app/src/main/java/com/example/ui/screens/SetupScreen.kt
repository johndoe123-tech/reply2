package com.example.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.StatusGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onFinishSetup: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var notificationAccessGranted by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var postNotificationsGranted by remember { mutableStateOf(isPostNotificationsPermissionGranted(context)) }
    var batteryOptimizationIgnored by remember { mutableStateOf(isBatteryOptimizationExempt(context)) }

    fun refreshStatuses() {
        notificationAccessGranted = isNotificationListenerEnabled(context)
        postNotificationsGranted = isPostNotificationsPermissionGranted(context)
        batteryOptimizationIgnored = isBatteryOptimizationExempt(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val postNotificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        postNotificationsGranted = isGranted || isPostNotificationsPermissionGranted(context)
    }

    val isRequiredStepsComplete = notificationAccessGranted && postNotificationsGranted

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("AutoReply Setup", fontWeight = FontWeight.Bold)
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
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Welcome to WhatsApp AutoReply!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please complete the required system permissions below to ensure background listener and automated responses function reliably.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // Step 1: Notification Listener Access
            item {
                SetupStepCard(
                    stepNumber = "1",
                    title = "Notification Listener Access",
                    isRequired = true,
                    isGranted = notificationAccessGranted,
                    description = "Required to read incoming WhatsApp messages and trigger quick-reply actions in the background.",
                    buttonText = if (notificationAccessGranted) "Access Granted" else "Enable Notification Access",
                    buttonIcon = Icons.Default.Security,
                    onButtonClick = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        context.startActivity(intent)
                    },
                    testTagPrefix = "setup_step_1"
                )
            }

            // Step 2: Post Notifications Permission
            item {
                SetupStepCard(
                    stepNumber = "2",
                    title = "Post Notifications Permission",
                    isRequired = true,
                    isGranted = postNotificationsGranted,
                    description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        "Required on Android 13+ to post status updates and alerts when messages require manual user reply."
                    } else {
                        "Not required on your Android version (granted automatically)."
                    },
                    buttonText = if (postNotificationsGranted) "Permission Granted" else "Request Notification Permission",
                    buttonIcon = Icons.Default.Notifications,
                    onButtonClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            postNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !postNotificationsGranted,
                    testTagPrefix = "setup_step_2"
                )
            }

            // Step 3: Battery Optimization Exemption
            item {
                SetupStepCard(
                    stepNumber = "3",
                    title = "Battery Optimization Exemption",
                    isRequired = false,
                    isGranted = batteryOptimizationIgnored,
                    description = "Recommended to prevent Android OS from putting the background listener service to sleep.",
                    buttonText = if (batteryOptimizationIgnored) "Exempted" else "Disable Battery Optimization",
                    buttonIcon = Icons.Default.BatteryAlert,
                    onButtonClick = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        }
                    },
                    testTagPrefix = "setup_step_3"
                )
            }

            // Step 4: Background / OEM Autostart Guidance
            item {
                SetupStepCard(
                    stepNumber = "4",
                    title = "Background & Autostart Settings",
                    isRequired = false,
                    isGranted = false,
                    isManualCheck = true,
                    description = "Phone brands like Xiaomi, Samsung, Huawei, Oppo, or Vivo have extra 'Autostart' or 'Protected Apps' settings. Check app settings if service stops unexpectedly.",
                    buttonText = "Open App Settings",
                    buttonIcon = Icons.Default.SettingsApplications,
                    onButtonClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    testTagPrefix = "setup_step_4"
                )
            }

            // Finish Setup Button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        refreshStatuses()
                        if (isRequiredStepsComplete) {
                            onFinishSetup()
                        }
                    },
                    enabled = isRequiredStepsComplete,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("finish_setup_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRequiredStepsComplete) "Finish Setup & Continue" else "Complete Steps 1 & 2 to Proceed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SetupStepCard(
    stepNumber: String,
    title: String,
    isRequired: Boolean,
    isGranted: Boolean,
    isManualCheck: Boolean = false,
    description: String,
    buttonText: String,
    buttonIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onButtonClick: () -> Unit,
    enabled: Boolean = true,
    testTagPrefix: String
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stepNumber,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isManualCheck) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Recommended") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                } else if (isGranted) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Granted") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                } else {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (isRequired) "Required" else "Optional") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isRequired) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onButtonClick,
                enabled = enabled && !isGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_button")
            ) {
                Icon(buttonIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonText)
            }
        }
    }
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val cn = ComponentName.unflattenFromString(name)
            if (cn != null && cn.packageName == pkgName) {
                return true
            }
        }
    }
    return false
}

fun isPostNotificationsPermissionGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun isBatteryOptimizationExempt(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
}
