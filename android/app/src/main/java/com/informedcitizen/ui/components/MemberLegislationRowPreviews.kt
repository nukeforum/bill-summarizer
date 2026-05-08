package com.informedcitizen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.ui.preview.MaterialPreviewTheme
import com.informedcitizen.ui.preview.sampleLegislationItem

@PreviewLightDark
@Composable
private fun PreviewMemberLegislationRows() = PreviewWrap {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        MemberLegislationRow(item = sampleLegislationItem(), onClick = {})
        MemberLegislationRow(
            item = sampleLegislationItem(
                id = "119-hr-9000",
                type = "hr",
                number = "9000",
                title = "A bill to authorize appropriations for the National Aeronautics and " +
                    "Space Administration for fiscal years 2027 through 2031, and for other " +
                    "purposes.",
            ),
            onClick = {},
        )
        MemberLegislationRow(
            item = sampleLegislationItem(
                id = "119-hres-42",
                type = "hres",
                number = "42",
                title = "Recognizing the contributions of community college educators.",
            ),
            onClick = {},
        )
    }
}

@Composable
private fun PreviewWrap(content: @Composable () -> Unit) {
    MaterialPreviewTheme {
        Surface { content() }
    }
}
