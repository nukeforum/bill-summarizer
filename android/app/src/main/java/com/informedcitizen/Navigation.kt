package com.informedcitizen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.informedcitizen.ui.billdetail.BillDetailScreen
import com.informedcitizen.ui.billslist.BillsListScreen
import com.informedcitizen.ui.calendar.SessionCalendarScreen
import com.informedcitizen.ui.datasources.DataSourcesScreen
import com.informedcitizen.ui.reps.MemberDetailScreen
import com.informedcitizen.ui.reps.RepsTab
import com.informedcitizen.ui.settings.SettingsScreen
import com.informedcitizen.ui.shell.CongressShell

@Composable
fun MainNavigation(
    pendingDeepLinkAction: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(Root)

    LaunchedEffect(pendingDeepLinkAction) {
        if (pendingDeepLinkAction == MainActivity.ACTION_OPEN_AI_SETTINGS) {
            if (backStack.lastOrNull() != Settings) {
                backStack.add(Settings)
            }
            onDeepLinkConsumed()
        }
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Root> {
                CongressShell(
                    billsContent = { mod ->
                        BillsListScreen(
                            onBillClick = { bill -> backStack.add(BillDetail(bill.id)) },
                            onSettingsClick = { backStack.add(Settings) },
                            modifier = mod,
                            onCalendarClick = { backStack.add(CongressCalendar) },
                        )
                    },
                    repsContent = { mod ->
                        RepsTab(
                            onMemberClick = { backStack.add(MemberDetail(it)) },
                            onSettingsClick = { backStack.add(Settings) },
                            modifier = mod,
                        )
                    },
                )
            }
            entry<BillDetail> { key ->
                BillDetailScreen(
                    billId = key.billId,
                    onBack = { backStack.removeLastOrNull() },
                    modifier = Modifier,
                )
            }
            entry<MemberDetail> { key ->
                MemberDetailScreen(
                    bioguideId = key.bioguideId,
                    onBack = { backStack.removeLastOrNull() },
                    onBillClick = { billId -> backStack.add(BillDetail(billId)) },
                    modifier = Modifier,
                )
            }
            entry<Settings> {
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() },
                    onCalendarClick = { backStack.add(CongressCalendar) },
                    onDataSourcesClick = { backStack.add(DataSources) },
                )
            }
            entry<CongressCalendar> {
                SessionCalendarScreen(onBack = { backStack.removeLastOrNull() })
            }
            entry<DataSources> {
                DataSourcesScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
