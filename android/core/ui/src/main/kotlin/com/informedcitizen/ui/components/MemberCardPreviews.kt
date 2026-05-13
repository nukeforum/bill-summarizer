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
import com.informedcitizen.ui.preview.sampleRepresentative
import com.informedcitizen.ui.preview.sampleSenatorD
import com.informedcitizen.ui.preview.sampleSenatorR

@PreviewLightDark
@Composable
private fun PreviewMemberCards() = PreviewWrap(modifier = Modifier) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Direct contact form (most common in production for senators).
        MemberCard(
            member = sampleSenatorD,
            onClick = {},
            onCallPhone = {},
            onOpenContactPage = {},
        )
        MemberCard(
            member = sampleSenatorR,
            onClick = {},
            onCallPhone = {},
            onOpenContactPage = {},
        )
        // Website fallback (typical for ~58% of House reps).
        MemberCard(
            member = sampleRepresentative,
            onClick = {},
            onCallPhone = {},
            onOpenContactPage = {},
        )
        // Phone only — no contact URL of any kind.
        MemberCard(
            member = sampleMember(
                bioguideId = "Z000099",
                name = "Phone Only Senator",
                contactForm = null,
                website = null,
            ),
            onClick = {},
            onCallPhone = {},
            onOpenContactPage = {},
        )
        // Nothing — collapses to the original layout.
        MemberCard(
            member = sampleMember(
                bioguideId = "Z000100",
                name = "No Contact Data Senator",
                phone = null,
                contactForm = null,
                website = null,
            ),
            onClick = {},
            onCallPhone = {},
            onOpenContactPage = {},
        )
    }
}
