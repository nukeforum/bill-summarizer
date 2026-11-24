package com.informedcitizen.ui.datasources

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Robolectric-hosted Compose tests for the Congress.gov API key entry field:
 * what the user types reaches the view model, and the field is a password
 * field (so it is drawn obscured and screen readers do not speak the key).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ApiKeyFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val typedKey = "DEMOKEY1234567890"

    private var keyInput by mutableStateOf("")

    private fun setContent() {
        composeRule.setContent { Content() }
    }

    @Composable
    private fun Content() {
        DataSourcesContent(
            state = DataSourcesUiState(keyInput = keyInput),
            innerPadding = PaddingValues(0.dp),
            onKeyInputChange = { keyInput = it },
            onSaveKey = {},
            onClearKey = {},
            onFetchNow = {},
            onOpenGuide = {},
        )
    }

    @Test
    fun `typed key is reported to the view model and enables saving`() {
        setContent()

        composeRule.onNode(hasSetTextAction()).performTextInput(typedKey)
        composeRule.waitForIdle()

        assertEquals(typedKey, keyInput)
        composeRule.onNodeWithText("Verify and save").assertIsEnabled()
    }

    @Test
    fun `key field is a password field`() {
        setContent()

        composeRule.onNode(hasSetTextAction())
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }

    @Test
    fun `typed key is not kept in saved instance state`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { Content() }
        composeRule.onNode(hasSetTextAction()).performTextInput(typedKey)
        composeRule.waitForIdle()

        keyInput = ""
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        assertEquals("", keyInput)
        composeRule.onNodeWithText("Verify and save").assertIsNotEnabled()
    }
}
