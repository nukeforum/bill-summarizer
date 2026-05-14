package com.informedcitizen.ui.reps

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * One-shot interstitial shown the first time the user taps the website-fallback
 * icon on a `MemberCard`. Explains why we're opening a homepage instead of a
 * direct contact form, then proceeds to launch the URL when the user taps Ok.
 * Dismissing without confirming (back press / outside tap) leaves the
 * preference unset so the dialog will show again next time.
 */
@Composable
internal fun WebsiteFallbackDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Ok") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("No direct contact form") },
        text = {
            Text(
                "This representative doesn't have a direct contact form on file. " +
                    "We'll open their official site, which usually has a Contact " +
                    "page you can use to reach their office.",
            )
        },
    )
}
