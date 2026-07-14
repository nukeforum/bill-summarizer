package com.informedcitizen.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.ui.preview.PreviewWrap

@PreviewLightDark
@Composable
private fun PreviewBillSearchFieldEmpty() = PreviewWrap(modifier = Modifier) {
    BillSearchField(
        query = "",
        onQueryChange = {},
        modifier = Modifier.padding(16.dp),
    )
}

@PreviewLightDark
@Composable
private fun PreviewBillSearchFieldFilled() = PreviewWrap(modifier = Modifier) {
    BillSearchField(
        query = "education",
        onQueryChange = {},
        modifier = Modifier.padding(16.dp),
    )
}
