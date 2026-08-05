package com.example.ui.screens

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
import com.example.data.db.KnownRelation
import com.example.ui.viewmodel.KnownRelationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnownRelationsScreen(
    viewModel: KnownRelationsViewModel
) {
    val relations by viewModel.relations.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var inputPhone by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("") }
    var inputLabel by remember { mutableStateOf("Friend") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family & Friend Directory", fontWeight = FontWeight.Bold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("add_known_relation_fab")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add Relation")
            }
        }
    ) { innerPadding ->
        if (relations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ContactPhone,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Directory Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add phone numbers with custom relationship labels (e.g. Mom, Boss, Friend) so the AI recognizes them instantly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(relations) { relation ->
                    RelationCard(
                        relation = relation,
                        onDelete = { viewModel.deleteRelation(relation.phoneNumber) }
                    )
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Known Relation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Contact Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("relation_name_input")
                    )
                    OutlinedTextField(
                        value = inputPhone,
                        onValueChange = { inputPhone = it },
                        label = { Text("Phone Number / WhatsApp ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("relation_phone_input")
                    )
                    OutlinedTextField(
                        value = inputLabel,
                        onValueChange = { inputLabel = it },
                        label = { Text("Relationship (e.g., Mom, Friend, Work)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("relation_label_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputPhone.isNotBlank() && inputName.isNotBlank()) {
                            viewModel.addOrUpdateRelation(inputPhone, inputName, inputLabel)
                            inputPhone = ""
                            inputName = ""
                            inputLabel = "Friend"
                            showAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_relation_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RelationCard(relation: KnownRelation, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
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
                    text = relation.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = relation.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                SuggestionChip(
                    onClick = {},
                    label = { Text(relation.relationshipLabel) }
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
