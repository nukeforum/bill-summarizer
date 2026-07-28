package com.informedcitizen.ui.billdetail

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric-hosted Compose tests for the in-app full-text reader (issue #98).
 * The reader is the reliable path to a bill's source language: it renders the
 * already-fetched GPO text in-app instead of handing the user to congress.gov's
 * Cloudflare-guarded landing page.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FullTextReaderContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: FullTextState,
        onRetry: () -> Unit = {},
        onOpenInBrowser: () -> Unit = {},
    ) {
        composeRule.setContent {
            FullTextReaderContent(
                state = state,
                onRetry = onRetry,
                onOpenInBrowser = onOpenInBrowser,
            )
        }
    }

    @Test
    fun `loaded state renders the bill text and the no-account source note`() {
        setContent(FullTextState.Loaded("A BILL To do a thing. SECTION 1. SHORT TITLE."))
        composeRule.onNodeWithText("A BILL To do a thing. SECTION 1. SHORT TITLE.").assertExists()
        composeRule.onNodeWithText(SOURCE_NOTE).assertExists()
    }

    @Test
    fun `loading state shows the loading label and no browser hand-off`() {
        setContent(FullTextState.Loading)
        composeRule.onNodeWithText(LOADING_LABEL).assertExists()
        composeRule.onNodeWithText(OPEN_IN_BROWSER_LABEL).assertDoesNotExist()
    }

    @Test
    fun `error state offers retry and the browser fallback, and retry fires`() {
        var retried = false
        var openedBrowser = false
        setContent(
            state = FullTextState.Error("network down"),
            onRetry = { retried = true },
            onOpenInBrowser = { openedBrowser = true },
        )

        composeRule.onNodeWithText(OPEN_IN_BROWSER_LABEL).assertExists()
        assertFalse(retried)
        composeRule.onNodeWithText(RETRY_LABEL).performClick()
        assertTrue(retried)
        assertFalse(openedBrowser)
    }
}
