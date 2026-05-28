package com.informedcitizen.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.informedcitizen.pipeline.model.Member

/**
 * Row containing a [MemberCard] followed by an OUTSIDE help button. The
 * help button always renders; a badge dot appears when the rep has no
 * published contact methods. Dialog visibility is owned by the caller —
 * this composable only emits [onShowHelp].
 */
@Composable
fun MemberRowWithHelp(
    member: Member,
    onClick: () -> Unit,
    onCallPhone: (String) -> Unit,
    onOpenContactForm: (String) -> Unit,
    onOpenWebsite: (String) -> Unit,
    onOpenSocial: (String) -> Unit,
    onShowHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasNoMethods = member.availableContactMethods().isEmpty()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberCard(
            member = member,
            onClick = onClick,
            onCallPhone = onCallPhone,
            onOpenContactForm = onOpenContactForm,
            onOpenWebsite = onOpenWebsite,
            onOpenSocial = onOpenSocial,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onShowHelp,
            modifier = Modifier.padding(start = 4.dp),
        ) {
            BadgedBox(
                badge = { if (hasNoMethods) Badge() },
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = if (hasNoMethods) {
                        "Contact options help — no published methods for this rep"
                    } else {
                        "Contact options help"
                    },
                )
            }
        }
    }
}
