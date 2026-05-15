package com.informedcitizen.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.informedcitizen.pipeline.model.MemberLegislationItem

@Composable
fun MemberLegislationRow(
    item: MemberLegislationItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
    ) {
        Text("${item.type.uppercase()} ${item.number}", style = MaterialTheme.typography.labelMedium)
        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
        Text(
            "Latest action ${item.latestAction.date}: ${item.latestAction.text}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
