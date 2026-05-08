package com.informedcitizen.ui.billslist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.informedcitizen.ui.preview.PreviewWrap

@PreviewLightDark
@Composable
private fun PreviewSessionStatusLine() = PreviewWrap(modifier = Modifier.fillMaxWidth()) {
    SessionStatusLine(
        text = "House in session today; Senate on recess until May 12.",
        onClick = {},
    )
}
