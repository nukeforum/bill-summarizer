package com.billsummarizer.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Solarized — https://ethanschoonover.com/solarized/
val SolarizedBase03 = Color(0xFF002B36)
val SolarizedBase02 = Color(0xFF073642)
val SolarizedBase01 = Color(0xFF586E75)
val SolarizedBase00 = Color(0xFF657B83)
val SolarizedBase0 = Color(0xFF839496)
val SolarizedBase1 = Color(0xFF93A1A1)
val SolarizedBase2 = Color(0xFFEEE8D5)
val SolarizedBase3 = Color(0xFFFDF6E3)
val SolarizedYellow = Color(0xFFB58900)
val SolarizedOrange = Color(0xFFCB4B16)
val SolarizedRed = Color(0xFFDC322F)
val SolarizedMagenta = Color(0xFFD33682)
val SolarizedViolet = Color(0xFF6C71C4)
val SolarizedBlue = Color(0xFF268BD2)
val SolarizedCyan = Color(0xFF2AA198)
val SolarizedGreen = Color(0xFF859900)

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
