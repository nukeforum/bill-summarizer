package com.informedcitizen.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

private const val TAG = "CustomTabs"

fun openInCustomTab(context: Context, url: String) {
    val uri = url.toUri()
    val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    try {
        customTabsIntent.launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        // No browser supports Custom Tabs (or none is installed). Fall back to
        // a plain ACTION_VIEW intent; if that also fails, swallow with a
        // warning so we don't crash the user out of the app for a tap.
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e2: ActivityNotFoundException) {
            Log.w(TAG, "No browser available to open $url", e2)
        }
    }
}
