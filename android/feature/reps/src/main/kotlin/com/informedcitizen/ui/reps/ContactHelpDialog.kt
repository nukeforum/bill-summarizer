package com.informedcitizen.ui.reps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Phone
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
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.ui.components.ContactMethod

/**
 * Per-rep contact-options explainer. Opened by tapping the "?" button on
 * a [com.informedcitizen.ui.components.MemberRowWithHelp]. Lists the
 * methods this rep actually has, or an empty-state notice if there are none.
 */
@Composable
internal fun ContactHelpDialog(
    member: Member,
    methods: List<ContactMethod>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
        title = { Text("Contact options") },
        text = {
            if (methods.isEmpty()) {
                Text(
                    "We don't have any published contact methods on file for " +
                        "${member.name}. The data comes from the " +
                        "@unitedstates/congress-legislators project; you can " +
                        "also visit congress.gov directly.",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    methods.forEach { HelpRow(it) }
                }
            }
        },
    )
}

@Composable
private fun HelpRow(method: ContactMethod) {
    val (icon: ImageVector, label: String, description: String) = when (method) {
        is ContactMethod.Phone -> Triple(
            Icons.Filled.Phone, "Phone", "Call the DC office",
        )
        is ContactMethod.ContactForm -> Triple(
            Icons.AutoMirrored.Filled.Send,
            "Contact form",
            "Open this representative's official contact webpage",
        )
        is ContactMethod.Website -> Triple(
            Icons.AutoMirrored.Filled.OpenInNew,
            "Website",
            "Open this representative's official site",
        )
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
