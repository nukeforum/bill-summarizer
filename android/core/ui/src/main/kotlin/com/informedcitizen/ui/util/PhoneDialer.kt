package com.informedcitizen.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri

private const val TAG = "PhoneDialer"

/**
 * Launches the system dialer pre-filled with [phone]. Uses ACTION_DIAL so
 * the user explicitly taps "call" — no CALL_PHONE permission, no surprise
 * outbound calls. Accepts any human-friendly formatting; the dialer
 * normalizes the digits.
 */
fun dialPhone(context: Context, phone: String) {
    val uri = "tel:${phone.trim()}".toUri()
    try {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No dialer available to handle $uri", e)
    }
}
