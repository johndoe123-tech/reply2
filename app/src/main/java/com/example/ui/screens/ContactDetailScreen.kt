package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.db.Message
import com.example.ui.theme.StatusGreen
import com.example.ui.viewmodel.ContactDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    viewModel: ContactDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val contact by viewModel.contact.collectAsState()
    val memory by viewModel.contactMemory.collectAsState()
    val profile by viewModel.behaviorProfile.collectAsState()
    val messages by viewModel.messages.collectAsState()

    var labelState by remember(contact?.relationshipLabel) {
        mutableStateOf(contact?.relationshipLabel ?: "Friend")
    }
    var summaryState by remember(memory?.summary) {
        mutableStateOf(memory?.summary ?: "")
    }
    var factsState by remember(memory?.importantFacts) {
        mutableStateOf(memory?.importantFacts ?: "")
    }
    var toneState by remember(profile?.toneFormalCasual) {
        mutableStateOf(profile?.toneFormalCasual ?: "casual")
    }
    var humorState by remember(profile?.humorLevel) {
        mutableStateOf(profile?.humorLevel ?: "medium")
    }
    var languageState by remember(profile?.preferredLanguage) {
        mutableStateOf(profile?.preferredLanguage ?: "English")
    }
    var doNotRespondState by remember(contact?.doNotRespond) {
        mutableStateOf(contact?.doNotRespond ?: false)
    }
    var genderState by remember(contact?.gender) {
        mutableStateOf(contact?.gender)
    }
    var allowOtherLanguagesState by remember(contact?.allowOtherLanguages) {
        mutableStateOf(contact?.allowOtherLanguages ?: true)
    }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearChatConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact?.displayName ?: viewModel.contactId, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = MaterialTheme.colorScheme.error)
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
            // 1. Contact Identity & Relationship Label Editor
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Relationship Label & Person Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = labelState,
                            onValueChange = { labelState = it },
                            label = { Text("Relationship (e.g., Mom, Friend, Work, Spouse)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_relationship_input"),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = toneState,
                                onValueChange = { toneState = it },
                                label = { Text("Tone (casual/formal)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = humorState,
                                onValueChange = { humorState = it },
                                label = { Text("Humor Level") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = languageState,
                            onValueChange = { languageState = it },
                            label = { Text("Preferred Language") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Allow Other Languages Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Allow Other Languages",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (allowOtherLanguagesState)
                                        "AI can respond in whatever language this contact uses."
                                    else
                                        "ENFORCE ENGLISH ONLY: If contact speaks another language, AI tells them to speak in English only.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (allowOtherLanguagesState) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                                )
                            }
                            Switch(
                                checked = allowOtherLanguagesState,
                                onCheckedChange = { allowOtherLanguagesState = it },
                                modifier = Modifier.testTag("allow_other_languages_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        // Do Not Respond Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Do Not Auto-Respond",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Always notify me for manual reply, bypass AI engine.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = doNotRespondState,
                                onCheckedChange = { doNotRespondState = it },
                                modifier = Modifier.testTag("contact_do_not_respond_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Gender Selection
                        Text(
                            text = "Gender Profile",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = genderState == null,
                                onClick = { genderState = null },
                                label = { Text("Unset") }
                            )
                            FilterChip(
                                selected = genderState == "male",
                                onClick = { genderState = "male" },
                                label = { Text("Male") }
                            )
                            FilterChip(
                                selected = genderState == "female",
                                onClick = { genderState = "female" },
                                label = { Text("Female") }
                            )
                        }
                    }
                }
            }

            // 2. Memory Summary & Important Facts Editor
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Isolated Memory & Learned Facts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = summaryState,
                            onValueChange = { summaryState = it },
                            label = { Text("Memory Summary") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_memory_summary_input"),
                            minLines = 3,
                            maxLines = 5
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = factsState,
                            onValueChange = { factsState = it },
                            label = { Text("Important Facts & Notes") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_important_facts_input"),
                            minLines = 3,
                            maxLines = 5
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.saveContactDetails(
                                    relationshipLabel = labelState,
                                    summary = summaryState,
                                    importantFacts = factsState,
                                    tone = toneState,
                                    humor = humorState,
                                    language = languageState,
                                    doNotRespond = doNotRespondState,
                                    gender = genderState,
                                    allowOtherLanguages = allowOtherLanguagesState
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_contact_details_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Memory & Profile")
                        }
                    }
                }
            }

            // 3. Contact Conversation Message Log
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Isolated Conversation History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${messages.size} msgs recorded",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (messages.isNotEmpty()) {
                        Button(
                            onClick = { showClearChatConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("clear_chat_button")
                        ) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Chat", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "No stored message history for this contact.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(messages) { msg ->
                    MessageLogBubble(msg = msg, contactName = contact?.displayName ?: viewModel.contactId)
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showClearChatConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirmDialog = false },
            title = { Text("Clear Chat History Only?") },
            text = {
                Text("This will clear the message transcript for ${contact?.displayName ?: viewModel.contactId}.\n\nIMPORTANT: Contact facts, notes, learned memories, and profile settings in the database WILL NOT be deleted so the AI does not forget them.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearChatHistoryOnly()
                        showClearChatConfirmDialog = false
                    }
                ) {
                    Text("Clear Chat Log", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("⚠️ WARNING: Delete Contact") },
            text = { Text("Are you sure you want to delete ${contact?.displayName ?: viewModel.contactId}?\n\nThis will permanently erase:\n• All learned facts & memory notes\n• Custom relationship labels & settings\n• All conversation logs & history") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteContactHistory()
                        showDeleteConfirmDialog = false
                        onNavigateBack()
                    }
                ) {
                    Text("Delete Contact Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MessageLogBubble(msg: Message, contactName: String) {
    val isMe = msg.sender == "me"
    val dateFormat = remember { SimpleDateFormat("HH:mm, MMM d", Locale.getDefault()) }
    val timeStr = remember(msg.timestamp) { dateFormat.format(Date(msg.timestamp)) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isMe) 14.dp else 2.dp,
                bottomEnd = if (isMe) 2.dp else 14.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMe) "AutoReply" else contactName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
