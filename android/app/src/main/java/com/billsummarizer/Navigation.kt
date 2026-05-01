package com.billsummarizer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.billsummarizer.ui.billdetail.BillDetailScreen
import com.billsummarizer.ui.billslist.BillsListScreen
import com.billsummarizer.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(BillsList)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<BillsList> {
                BillsListScreen(
                    onBillClick = { bill -> backStack.add(BillDetail(bill.id)) },
                    onSettingsClick = { backStack.add(Settings) },
                    modifier = Modifier,
                )
            }
            entry<BillDetail> { key ->
                BillDetailScreen(
                    billId = key.billId,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier,
                )
            }
            entry<Settings> {
                SettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
