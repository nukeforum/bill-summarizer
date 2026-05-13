package com.informedcitizen.feature.aititles

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes

/**
 * App-level hooks the AI-titles feature needs but can't own itself: an
 * Intent that opens the in-app AI Settings surface (target activity lives
 * in `:app`) and the small-icon drawable for the foreground-service
 * notification. Provided via Hilt from `:app`'s AppAiTitlesHostModule.
 */
interface AiTitlesHost {
    fun openAiSettingsIntent(context: Context): Intent

    @get:DrawableRes
    val notificationSmallIconResId: Int
}
