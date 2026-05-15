package com.informedcitizen.ui.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.informedcitizen.ui.settings.SettingsLinkRow
import com.informedcitizen.ui.settings.SettingsSection
import com.informedcitizen.ui.settings.SettingsSectionHeader
import com.informedcitizen.ui.util.openInCustomTab
import javax.inject.Inject
import javax.inject.Singleton

private const val CONGRESS_GOV_URL = "https://www.congress.gov/"
private const val PRIVACY_POLICY_URL =
    "https://nukeforum.github.io/bill-summarizer/privacy.html"
private const val GITHUB_REPO_URL =
    "https://github.com/nukeforum/bill-summarizer"

@Singleton
class AboutSettingsSection @Inject constructor() : SettingsSection {
    override val order = 100

    @Composable
    override fun Content() {
        val context = LocalContext.current
        SettingsSectionHeader("About")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            Text(
                text = "Informed Citizen is an independent, third-party app. " +
                    "It is not affiliated with, endorsed by, or operated by " +
                    "the U.S. Government, the U.S. Congress, the Library of " +
                    "Congress, any federal or state agency, or any political " +
                    "party, candidate, or campaign.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Bill data comes from public records published by the " +
                    "U.S. Congress on Congress.gov (operated by the Library " +
                    "of Congress), regenerated daily by an open-source " +
                    "GitHub Actions workflow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        SettingsLinkRow(
            label = "Data source: Congress.gov",
            supporting = "The official U.S. Congress site, operated by the Library of Congress.",
            onClick = { openInCustomTab(context, CONGRESS_GOV_URL) },
        )
        SettingsLinkRow(
            label = "Privacy policy",
            supporting = "What we collect, what we don't, and what the share-to-AI feature does.",
            onClick = { openInCustomTab(context, PRIVACY_POLICY_URL) },
        )
        SettingsLinkRow(
            label = "Source code on GitHub",
            supporting = "App, data pipeline, and Apache-2.0 license — auditable end-to-end.",
            onClick = { openInCustomTab(context, GITHUB_REPO_URL) },
        )
    }
}
