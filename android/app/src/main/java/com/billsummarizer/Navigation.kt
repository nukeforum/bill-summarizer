package com.billsummarizer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.billsummarizer.ui.billslist.BillsListScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(BillsList)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<BillsList> {
                BillsListScreen(
                    onBillClick = { /* navigation to BillDetailScreen lands in milestone 4 */ },
                    modifier = Modifier,
                )
            }
        },
    )
}
