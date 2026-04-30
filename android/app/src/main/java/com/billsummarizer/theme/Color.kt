package com.billsummarizer.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

object PartyColors {
    val Democrat = Color(0xFF1F5FBF)
    val Republican = Color(0xFFC0392B)
    val Independent = Color(0xFF7B1FA2)
    val Unknown = Color(0xFF6B6B6B)

    fun forParty(code: String): Color = when (code.uppercase()) {
        "D" -> Democrat
        "R" -> Republican
        "I", "ID" -> Independent
        else -> Unknown
    }
}
