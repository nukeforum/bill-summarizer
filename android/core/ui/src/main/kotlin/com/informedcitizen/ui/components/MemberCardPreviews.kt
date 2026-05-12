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
        MemberCard(member = sampleSenatorD, onClick = {})
        MemberCard(member = sampleSenatorR, onClick = {})
        MemberCard(member = sampleRepresentative, onClick = {})
    }
}
