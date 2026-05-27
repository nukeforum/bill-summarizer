package com.informedcitizen.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.informedcitizen.ui.preview.PreviewWrap
import com.informedcitizen.ui.preview.sampleMember

@PreviewLightDark
@Composable
private fun PreviewMemberRowWithHelp() = PreviewWrap(modifier = Modifier) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 3 methods — no badge
        MemberRowWithHelp(
            member = sampleMember(name = "3 methods"),
            onClick = {}, onCallPhone = {}, onOpenContactForm = {}, onOpenWebsite = {},
            onShowHelp = {},
        )
        // 1 method (phone) — no badge
        MemberRowWithHelp(
            member = sampleMember(name = "Phone only", contactForm = null, website = null),
            onClick = {}, onCallPhone = {}, onOpenContactForm = {}, onOpenWebsite = {},
            onShowHelp = {},
        )
        // 0 methods — badge dot
        MemberRowWithHelp(
            member = sampleMember(name = "No methods", phone = null, contactForm = null, website = null),
            onClick = {}, onCallPhone = {}, onOpenContactForm = {}, onOpenWebsite = {},
            onShowHelp = {},
        )
    }
}
