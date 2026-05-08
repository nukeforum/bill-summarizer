package com.informedcitizen.ui.billslist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.informedcitizen.ui.preview.MaterialPreviewTheme

@PreviewLightDark
@Composable
private fun PreviewSessionStatusLine() = PreviewWrap {
    SessionStatusLine(
        text = "House in session today; Senate on recess until May 12.",
        onClick = {},
    )
}

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    MaterialPreviewTheme {
        Surface(modifier = Modifier.fillMaxWidth()) { content() }
    }
}
