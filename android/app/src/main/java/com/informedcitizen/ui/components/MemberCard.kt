package com.informedcitizen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.informedcitizen.data.model.Member

private fun partyColor(party: String): Color = when (party) {
    "D" -> Color(0xFF2962FF)
    "R" -> Color(0xFFD32F2F)
    else -> Color(0xFF6D6D6D)
}

@Composable
fun MemberCard(member: Member, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.width(8.dp).height(80.dp).background(partyColor(member.party)),
            ) {}
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                val role = if (member.chamber == "senate") "Senator" else "Representative"
                val districtSuffix = member.district?.takeIf { it > 0 }?.let { "-${it}" } ?: ""
                Text(
                    "$role · ${member.party}-${member.state}$districtSuffix",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
