package com.informedcitizen.ui.components

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Regression coverage for #109: a persona typed a multi-word search, pressed
 * system back to dismiss the keyboard, and instead of staying on their
 * results the app surfaced an unrelated screen with the query gone.
 *
 * The bug was that [BillSearchField] had no opinion about system back at
 * all, so once focused it depended on the platform reliably letting the IME
 * consume the very first back press before anything else saw it — a race
 * that is not guaranteed to resolve the same way on every OS version/timing.
 * When it lost that race, the event fell through to whatever back handler
 * sat above it in the tree (a screen's own nav back-stack handling here),
 * which could pop/push an unrelated destination and made the in-progress
 * query look lost.
 *
 * This test hosts [BillSearchField] under a stand-in outer [BackHandler] —
 * playing the role of a screen's nav back-stack handler — and asserts that
 * while the field is focused, system back is fully consumed by the field
 * (query untouched) and never reaches that outer handler. A second test
 * covers the complementary contract: an unfocused field never intercepts
 * back at all, so the fix doesn't overreach once the field isn't in play.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BillSearchFieldBackPressTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `system back on a focused search field is consumed by the field, not the outer nav back handler`() {
        var outerBackInvocations = 0
        var query = "healthcare reform"
        var fieldFocused = false

        composeRule.setContent {
            // Stands in for a screen's own nav back-stack handling — e.g.
            // NavDisplay's `onBack = { backStack.removeLastOrNull() }` —
            // registered higher in the tree, exactly like the real app.
            BackHandler(enabled = true) { outerBackInvocations++ }

            Column {
                BillSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    // Piggybacks on the same focus-change stream BillSearchField
                    // wires internally, so this observes real compose focus
                    // state rather than the (less reliable under Robolectric)
                    // merged semantics tree.
                    modifier = Modifier.onFocusChanged { fieldFocused = it.isFocused },
                )
            }
        }

        // Focus the field the way a user does: tap into it.
        composeRule.onNodeWithText(query).performClick()
        composeRule.waitForIdle()
        assertTrue("field should be focused after a tap", fieldFocused)

        // System back while focused must be fully owned by the field: the
        // query must survive and the outer/nav handler must never see this
        // event — that leak is exactly how #109 landed on an unrelated
        // screen with the query looking lost.
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals("query must survive a keyboard-dismiss back press", "healthcare reform", query)
        assertEquals(
            "the outer (nav) back handler must not see a back press that only dismissed the keyboard",
            0,
            outerBackInvocations,
        )
    }

    @Test
    fun `an unfocused search field never intercepts back`() {
        var outerBackInvocations = 0

        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            BackHandler(enabled = true) { outerBackInvocations++ }
            Column {
                BillSearchField(query = query, onQueryChange = { query = it })
            }
        }

        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(1, outerBackInvocations)
    }
}
