package com.informedcitizen.ui.reps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Static explainer for the contact-option buttons that can appear on a
 * representative's card. Describes every possible method regardless of which
 * ones a given rep publishes, so the guidance is stable across reps. Opened
 * from the "?" action next to the screen title.
 */
@Composable
internal fun ContactHelpDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
        title = { Text("Contact options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpRow(
                    icon = Icons.Filled.Phone,
                    label = "Phone",
                    description = "Call the representative's DC office.",
                )
                HelpRow(
                    icon = Icons.AutoMirrored.Filled.Send,
                    label = "Contact form",
                    description = "Open the representative's official contact webpage.",
                )
                HelpRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = "Website",
                    description = "Open the representative's official site. Shown only " +
                        "when no contact form is published.",
                )
                HelpRow(
                    icon = Icons.Outlined.Public,
                    label = "Socials",
                    description = "Open one of the representative's public social-media profiles.",
                )
                Text(
                    "Not every representative publishes every option, so a card only " +
                        "shows the buttons available for that rep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun HelpRow(icon: ImageVector, label: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
