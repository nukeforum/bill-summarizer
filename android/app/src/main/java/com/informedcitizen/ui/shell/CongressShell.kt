package com.informedcitizen.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

enum class ShellTab { Bills, Reps }

@Composable
fun CongressShell(
    billsContent: @Composable (Modifier) -> Unit,
    repsContent: @Composable (Modifier) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(ShellTab.Bills) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selected == ShellTab.Bills,
                    onClick = { selected = ShellTab.Bills },
                    icon = { Icon(Icons.Filled.Description, contentDescription = null) },
                    label = { Text("Bills") },
                )
                NavigationBarItem(
                    selected = selected == ShellTab.Reps,
                    onClick = { selected = ShellTab.Reps },
                    icon = { Icon(Icons.Filled.AccountBalance, contentDescription = null) },
                    label = { Text("Reps") },
                )
            }
        },
    ) { padding ->
        when (selected) {
            ShellTab.Bills -> billsContent(Modifier.padding(padding))
            ShellTab.Reps -> repsContent(Modifier.padding(padding))
        }
    }
}
