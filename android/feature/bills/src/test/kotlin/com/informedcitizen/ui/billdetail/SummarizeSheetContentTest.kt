package com.informedcitizen.ui.billdetail

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric-hosted Compose tests for the "Summarize with AI" sheet content. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SummarizeSheetContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(hasOfficialSummary: Boolean, onReadOfficialSummary: () -> Unit = {}) {
        composeRule.setContent {
            SummarizeSheetContent(
                fullTextState = FullTextState.Idle,
                hasHtmlText = true,
                hasOfficialSummary = hasOfficialSummary,
                onIncludeFullTextChange = {},
                onShareToTarget = { _, _ -> },
                onShareToOther = {},
                onReadOfficialSummary = onReadOfficialSummary,
            )
        }
    }

    @Test
    fun `own-account disclosure is shown before the hand-off`() {
        setContent(hasOfficialSummary = false)
        composeRule.onNodeWithText(ACCOUNT_DISCLOSURE).assertExists()
    }

    @Test
    fun `official summary path is offered when the bill has one`() {
        var readOfficial = false
        setContent(hasOfficialSummary = true, onReadOfficialSummary = { readOfficial = true })

        composeRule.onNodeWithText(OFFICIAL_SUMMARY_NOTICE).assertExists()
        composeRule.onNodeWithText(READ_OFFICIAL_SUMMARY_LABEL).performClick()
        assertTrue(readOfficial)
    }

    @Test
    fun `official summary path is absent when the bill has none`() {
        setContent(hasOfficialSummary = false)
        composeRule.onNodeWithText(OFFICIAL_SUMMARY_NOTICE).assertDoesNotExist()
        composeRule.onNodeWithText(READ_OFFICIAL_SUMMARY_LABEL).assertDoesNotExist()
    }
}
