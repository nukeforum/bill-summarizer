package com.informedcitizen.ui.reps

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun RepsTab(
    onMemberClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text("Reps (placeholder)", modifier = modifier)
}
