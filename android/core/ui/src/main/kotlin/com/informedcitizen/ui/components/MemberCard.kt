package com.informedcitizen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.informedcitizen.data.model.Member
import com.informedcitizen.theme.PartyColors

@Composable
fun MemberCard(
    member: Member,
    onClick: () -> Unit,
    onCallPhone: (String) -> Unit,
    onOpenContactPage: (url: String, isFallback: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                val role = if (member.chamber == "senate") "Senator" else "Representative"
                val districtSuffix = member.district?.takeIf { it > 0 }?.let { "-${it}" } ?: ""
                Text(
                    "$role · ${member.party}-${member.state}$districtSuffix",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            MemberContactEndRegion(
                phone = member.phone,
                contactForm = member.contactForm,
                website = member.website,
                onCallPhone = onCallPhone,
                onOpenContactPage = onOpenContactPage,
            )
        }
    }
}

@Composable
private fun MemberContactEndRegion(
    phone: String?,
    contactForm: String?,
    website: String?,
    onCallPhone: (String) -> Unit,
    onOpenContactPage: (url: String, isFallback: Boolean) -> Unit,
) {
    val phoneValue = phone?.takeIf { it.isNotBlank() }
    val contactFormValue = contactForm?.takeIf { it.isNotBlank() }
    val websiteValue = website?.takeIf { it.isNotBlank() }
    if (phoneValue == null && contactFormValue == null && websiteValue == null) return
    Row(
        modifier = Modifier.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (phoneValue != null) {
            IconButton(onClick = { onCallPhone(phoneValue) }) {
                Icon(
                    Icons.Filled.Phone,
                    contentDescription = "Call $phoneValue",
                )
            }
        }
        if (contactFormValue != null) {
            IconButton(onClick = { onOpenContactPage(contactFormValue, false) }) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Open contact form",
                )
            }
        } else if (websiteValue != null) {
            IconButton(onClick = { onOpenContactPage(websiteValue, true) }) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open official site — no direct contact form on file",
                )
            }
        }
    }
}
