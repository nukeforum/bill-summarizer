package com.informedcitizen.data.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

internal const val BILL_SUMMARIZATION_WORK_NAME = "summarize-bills"

class StopSummarizationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(BILL_SUMMARIZATION_WORK_NAME)
        }
    }
    companion object {
        const val ACTION_STOP = "com.informedcitizen.action.STOP_SUMMARIZATION"
    }
}
