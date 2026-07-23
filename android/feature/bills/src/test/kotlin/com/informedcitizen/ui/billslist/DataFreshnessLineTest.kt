package com.informedcitizen.ui.billslist

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/** Robolectric-hosted Compose tests for the bills-list freshness indicator. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DataFreshnessLineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-07-22T12:00:00Z")

    @Test
    fun `fresh data renders the relative label without a warning`() {
        composeRule.setContent {
            MaterialTheme {
                DataFreshnessLine(generatedAt = now.minus(Duration.ofHours(3)), now = now)
            }
        }
        composeRule.onNodeWithText("Updated 3 hours ago").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Updated 3 hours ago").assertIsDisplayed()
    }

    @Test
    fun `stale data prepends a warning to the semantic text`() {
        composeRule.setContent {
            MaterialTheme {
                DataFreshnessLine(generatedAt = now.minus(Duration.ofDays(5)), now = now)
            }
        }
        composeRule.onNodeWithText("Updated 5 days ago").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Warning: Updated 5 days ago").assertIsDisplayed()
    }
}
