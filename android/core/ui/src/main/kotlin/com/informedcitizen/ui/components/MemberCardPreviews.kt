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
private fun PreviewMemberCards_AllPermutations() = PreviewWrap(modifier = Modifier) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 3 methods: phone + form + website
        Card3(member = sampleMember(name = "Phone + Form + Website"))
        // 2 methods: phone + form
        Card3(member = sampleMember(name = "Phone + Form", website = null))
        // 2 methods: phone + website
        Card3(member = sampleMember(name = "Phone + Website", contactForm = null))
        // 2 methods: form + website
        Card3(member = sampleMember(name = "Form + Website", phone = null))
        // 1 method: phone only
        Card3(member = sampleMember(name = "Phone Only", contactForm = null, website = null))
        // 1 method: form only
        Card3(member = sampleMember(name = "Form Only", phone = null, website = null))
        // 1 method: website only
        Card3(member = sampleMember(name = "Website Only", phone = null, contactForm = null))
        // 0 methods
        Card3(member = sampleMember(name = "No Methods", phone = null, contactForm = null, website = null))
        // 4 methods: phone + form + web + socials
        Card3(member = sampleMember(
            name = "All Four",
            socials = listOf(
                com.informedcitizen.pipeline.model.SocialHandle("twitter", "RepX"),
                com.informedcitizen.pipeline.model.SocialHandle("facebook", "RepX"),
            ),
        ))
    }
}

@Composable
private fun Card3(member: com.informedcitizen.pipeline.model.Member) {
    MemberCard(
        member = member,
        onClick = {},
        onCallPhone = {},
        onOpenContactForm = {},
        onOpenWebsite = {},
        onOpenSocial = {},
    )
}
