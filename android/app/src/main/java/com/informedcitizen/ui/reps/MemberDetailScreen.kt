package com.informedcitizen.ui.reps

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MemberDetailScreen(
    bioguideId: String,
    onBack: () -> Unit,
    onBillClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Text("Member $bioguideId (placeholder)", modifier = modifier)
}
