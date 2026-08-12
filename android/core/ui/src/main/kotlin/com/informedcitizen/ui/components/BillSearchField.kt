package com.informedcitizen.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Always-visible search box for the bill lists. Deliberately a plain text
 * field (not a collapsing SearchBar behind an icon) so the entry point stays
 * obvious to non-technical users.
 */
@Composable
fun BillSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search bills by topic or keyword",
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // System back while this field owns focus must do exactly one thing:
    // drop focus (which dismisses the IME) and leave the query + the list's
    // scroll position untouched. We can't lean on the platform's implicit
    // "IME consumes back before the app sees it" behavior for that — it is
    // not guaranteed to fire ahead of the screen's own back-stack handling
    // on every OS version/timing (predictive back included), and when it
    // loses that race the event falls through to the nav back stack instead,
    // popping/pushing an unrelated screen and making the query look lost
    // (#109). Owning back explicitly here, scoped to focus, makes the
    // behavior deterministic and keeps it out of the nav back-stack path
    // entirely. Compose dispatches to the most-recently-registered enabled
    // BackHandler first, so this wins over any handler further up the tree
    // (e.g. the nav graph's) while the field is focused.
    BackHandler(enabled = isFocused) {
        // force = true: a text field can otherwise "capture" focus (e.g. an
        // active selection/IME session) and decline a plain clearFocus(),
        // which would leave the field focused and the IME up — exactly the
        // failure this handler exists to prevent.
        focusManager.clearFocus(force = true)
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        placeholder = { Text(placeholder) },
    )
}
