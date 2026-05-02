package com.informedcitizen.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import com.informedcitizen.data.model.Bill
import com.informedcitizen.ui.util.displayName
import com.informedcitizen.ui.util.formatBillRef
import com.informedcitizen.ui.util.openInCustomTab

object LlmShareHelper {

    private const val BODY_CAP_CHARS = 50_000

    fun buildPrompt(bill: Bill, body: String?): String {
        val cleanBody = body?.takeIf { it.isNotBlank() }
            ?.let { capBody(it, bill.congressGovUrl) }
            ?: "(No bill text included; see Congress.gov: ${bill.congressGovUrl})"

        return """
            Summarize this U.S. Congressional bill in plain English for a general audience. Cover:
            1. What the bill does in 2-3 sentences
            2. Who it affects
            3. Key provisions (3-5 bullets)
            4. Any controversial or notable elements

            Bill: ${formatBillRef(bill.type, bill.number)} — ${bill.title}
            Status: ${bill.outcome.displayName()} on ${bill.latestAction.date}
            Sponsor: ${bill.sponsor.name} (${bill.sponsor.party}-${bill.sponsor.state})

            $cleanBody
        """.trimIndent()
    }

    fun shareTo(context: Context, target: LlmTarget, payload: String) {
        if (isPackageInstalled(context, target.packageName) && tryDirectShare(context, target.packageName, payload)) {
            return
        }
        copyToClipboard(context, payload)
        Toast.makeText(
            context,
            "Prompt copied — opening ${target.displayName} in browser",
            Toast.LENGTH_SHORT,
        ).show()
        openInCustomTab(context, target.webUrl)
    }

    fun shareToOther(context: Context, payload: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        val chooser = Intent.createChooser(send, "Share to…").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun tryDirectShare(context: Context, packageName: String, payload: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage(packageName)
            putExtra(Intent.EXTRA_TEXT, payload)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Bill summary prompt", text))
    }

    private fun capBody(body: String, congressGovUrl: String): String =
        if (body.length <= BODY_CAP_CHARS) {
            body
        } else {
            body.substring(0, BODY_CAP_CHARS) +
                "\n\n[Bill text truncated. Full text: $congressGovUrl]"
        }
}
