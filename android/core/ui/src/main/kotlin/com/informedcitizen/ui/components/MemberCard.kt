package com.informedcitizen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.informedcitizen.pipeline.model.Member
import com.informedcitizen.theme.PartyColors

@Composable
fun MemberCard(
    member: Member,
    onClick: () -> Unit,
    onCallPhone: (String) -> Unit,
    onOpenContactForm: (String) -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val methods = member.availableContactMethods()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                onClickLabel = "View details for ${member.name}",
                role = Role.Button,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .width(8.dp)
                    .height(80.dp)
                    .background(PartyColors.forParty(member.party)),
            ) {}
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                val role = if (member.chamber == "senate") "Senator" else "Representative"
                val districtSuffix = member.district?.takeIf { it > 0 }?.let { "-${it}" } ?: ""
                Text(
                    "$role · ${member.party}-${member.state}$districtSuffix",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            SegmentedContactEndRegion(
                methods = methods,
                onCallPhone = onCallPhone,
                onOpenContactForm = onOpenContactForm,
                onOpenWebsite = onOpenWebsite,
            )
        }
    }
}

@Composable
internal fun SegmentedContactEndRegion(
    methods: List<ContactMethod>,
    onCallPhone: (String) -> Unit,
    onOpenContactForm: (String) -> Unit,
    onOpenWebsite: (String) -> Unit,
) {
    if (methods.isEmpty()) return
    Row(
        modifier = Modifier.height(80.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        methods.forEachIndexed { index, method ->
            if (index > 0) VerticalDivider(modifier = Modifier.fillMaxHeight())
            Box(
                modifier = Modifier.width(52.dp).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                when (method) {
                    is ContactMethod.Phone -> IconButton(onClick = { onCallPhone(method.number) }) {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = "Call ${method.number.replace(Regex("[^0-9+]"), " ").trim()}",
                        )
                    }
                    is ContactMethod.ContactForm -> IconButton(onClick = { onOpenContactForm(method.url) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Open contact form",
                        )
                    }
                    is ContactMethod.Website -> IconButton(onClick = { onOpenWebsite(method.url) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open official website",
                        )
                    }
                }
            }
        }
    }
}
